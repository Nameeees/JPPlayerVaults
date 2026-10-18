package me.jp.jpvaults.storage;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.model.PlayerVaultData;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Guarda los vaults en una base de datos por JDBC (MySQL/MariaDB o cualquier
 * motor que hable el mismo SQL):
 *
 * <pre>
 * &lt;prefijo&gt;players (uuid PK, last_name, updated_at)
 * &lt;prefijo&gt;vaults  (uuid, vault, data BLOB, updated_at, PK (uuid, vault))
 * </pre>
 *
 * Un vault entero ocupa una fila: se serializa con {@link VaultCodec} y se
 * escribe en una transaccion (borrar + insertar), asi que un fallo a mitad no
 * deja el vault a medias.
 *
 * El driver se recibe ya instanciado: asi funciona con el driver que trae el
 * servidor, con uno propio y con {@code DriverManager} fuera del camino (que
 * rechaza drivers cargados por el classloader de otro plugin).
 *
 * Las lecturas ocurren en el hilo del servidor y las escrituras en un unico
 * hilo propio, en orden, igual que el backend YAML.
 */
public final class SqlVaultStorage implements VaultStorage {

    /** Espera maxima cuando una llamada pide confirmacion de escritura. */
    private static final long WRITE_TIMEOUT_SECONDS = 5L;

    private static final String DEFAULT_BLOB = "MEDIUMBLOB";
    private static final String FALLBACK_BLOB = "BLOB";

    private final JPPlayerVaults plugin;
    private final SqlSettings settings;
    private final Driver driver;
    private final ExecutorService writer;
    private final Deque<Connection> pool = new ArrayDeque<>();
    private final String playersTable;
    private final String vaultsTable;
    private final String blobType;
    private final boolean preferNbt;

    private boolean closed;

    /** Usa NBT para los items si el servidor lo ofrece (Paper 1.20.5+). */
    public SqlVaultStorage(JPPlayerVaults plugin, SqlSettings settings, Driver driver) throws SQLException {
        this(plugin, settings, driver, VaultCodec.nbtAvailable());
    }

    public SqlVaultStorage(JPPlayerVaults plugin, SqlSettings settings, Driver driver, boolean preferNbt)
            throws SQLException {
        this.plugin = plugin;
        this.settings = settings;
        this.driver = driver;
        this.preferNbt = preferNbt;
        this.playersTable = settings.getTablePrefix() + "players";
        this.vaultsTable = settings.getTablePrefix() + "vaults";
        this.blobType = createSchema();
        this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "JPPlayerVaults-SQL");
                thread.setDaemon(true);
                return thread;
            }
        });
        plugin.getLogger().info("SQL storage ready: " + settings.describe() + " using " + blobType
                + ", items as " + VaultCodec.preferredFormatName(preferNbt) + ".");
    }

    // ------------------------------------------------------------------ schema

    /** Crea las tablas si faltan y devuelve el tipo de columna BLOB que acepta el motor. */
    private String createSchema() throws SQLException {
        Connection connection = open();
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + playersTable + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "last_name VARCHAR(16),"
                        + "updated_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid))");
            }
            return createVaultsTable(connection, DEFAULT_BLOB);
        } finally {
            closeQuietly(connection);
        }
    }

    private String createVaultsTable(Connection connection, String type) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + vaultsTable + " ("
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "vault INT NOT NULL,"
                    + "data " + type + " NOT NULL,"
                    + "updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (uuid, vault))");
            return type;
        } catch (SQLException unsupported) {
            if (FALLBACK_BLOB.equals(type)) {
                throw unsupported;
            }
            // Motores que no conocen MEDIUMBLOB (por ejemplo H2 fuera del modo MySQL).
            plugin.getLogger().fine("The database does not accept " + type + " (" + unsupported.getMessage()
                    + "); using " + FALLBACK_BLOB + ".");
            return createVaultsTable(connection, FALLBACK_BLOB);
        }
    }

    // -------------------------------------------------------------------- load

    @Override
    public PlayerVaultData load(UUID owner) {
        PlayerVaultData data = new PlayerVaultData(owner);
        Connection connection = null;
        boolean broken = false;
        try {
            connection = borrow();
            String id = owner.toString();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT last_name FROM " + playersTable + " WHERE uuid = ?")) {
                select.setString(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    if (rows.next()) {
                        data.setLastName(rows.getString(1));
                    }
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT vault, data FROM " + vaultsTable + " WHERE uuid = ?")) {
                select.setString(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        int number = rows.getInt(1);
                        if (number < 1) {
                            continue;
                        }
                        byte[] stored = rows.getBytes(2);
                        if (stored == null || stored.length == 0) {
                            continue;
                        }
                        try {
                            ItemStack[] decoded = VaultCodec.decode(stored, PlayerVaultData.SIZE);
                            ItemStack[] contents = data.getVaultContents(number);
                            System.arraycopy(decoded, 0, contents, 0, Math.min(decoded.length, contents.length));
                        } catch (IOException damaged) {
                            plugin.getLogger().warning("Could not read vault #" + number + " of " + owner
                                    + " from the database: " + damaged.getMessage());
                        }
                    }
                }
            }
            data.markClean();
            return data;
        } catch (SQLException error) {
            broken = true;
            // Nunca se devuelve un vault vacio: el siguiente guardado lo
            // sobrescribiria y se perderian los objetos de verdad.
            throw new StorageException("Could not read the vaults of " + owner + " from the database", error);
        } finally {
            giveBack(connection, broken);
        }
    }

    // -------------------------------------------------------------------- save

    @Override
    public void save(PlayerVaultData data, boolean waitForDisk) {
        final long revision = data.getRevision();
        final UUID owner = data.getOwner();
        final String lastName = data.getLastName();
        final Map<Integer, byte[]> encoded = new LinkedHashMap<>();
        try {
            for (Map.Entry<Integer, ItemStack[]> entry : data.getVaults().entrySet()) {
                encoded.put(entry.getKey(), VaultCodec.encode(entry.getValue(), preferNbt));
            }
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not serialize the vaults of " + owner, error);
            return;
        }
        Future<?> writing;
        try {
            writing = writer.submit(() -> {
                if (write(owner, lastName, encoded)) {
                    data.markCleanIfRevision(revision);
                }
            });
        } catch (RejectedExecutionException alreadyClosed) {
            plugin.getLogger().warning("SQL storage is already closed; the change for " + owner + " was not written.");
            return;
        }
        if (waitForDisk) {
            awaitWrite(writing, owner);
        }
    }

    /** Transaccion: datos del jugador + reemplazo completo de sus vaults. */
    private boolean write(UUID owner, String lastName, Map<Integer, byte[]> vaults) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            Connection connection = null;
            boolean broken = false;
            try {
                connection = borrow();
                connection.setAutoCommit(false);
                try {
                    upsertPlayer(connection, owner, lastName);
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM " + vaultsTable + " WHERE uuid = ?")) {
                        delete.setString(1, owner.toString());
                        delete.executeUpdate();
                    }
                    if (!vaults.isEmpty()) {
                        try (PreparedStatement insert = connection.prepareStatement(
                                "INSERT INTO " + vaultsTable + " (uuid, vault, data, updated_at) VALUES (?, ?, ?, ?)")) {
                            long now = System.currentTimeMillis();
                            for (Map.Entry<Integer, byte[]> entry : vaults.entrySet()) {
                                insert.setString(1, owner.toString());
                                insert.setInt(2, entry.getKey());
                                insert.setBytes(3, entry.getValue());
                                insert.setLong(4, now);
                                insert.addBatch();
                            }
                            insert.executeBatch();
                        }
                    }
                    connection.commit();
                    return true;
                } catch (SQLException error) {
                    rollbackQuietly(connection);
                    throw error;
                } finally {
                    restoreAutoCommit(connection);
                }
            } catch (SQLException error) {
                broken = true;
                if (attempt == 2) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not save the vaults of " + owner + " to the database", error);
                }
            } finally {
                giveBack(connection, broken);
            }
        }
        return false;
    }

    private void upsertPlayer(Connection connection, UUID owner, String lastName) throws SQLException {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + playersTable + " SET last_name = ?, updated_at = ? WHERE uuid = ?")) {
                update.setString(1, lastName);
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, owner.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + playersTable + " (uuid, last_name, updated_at) VALUES (?, ?, ?)")) {
                insert.setString(1, owner.toString());
                insert.setString(2, lastName);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
                return;
            } catch (SQLException race) {
                // Otra conexion lo inserto entre el UPDATE y el INSERT: se repite el UPDATE.
                if (attempt == 2) {
                    throw race;
                }
            }
        }
    }

    // ------------------------------------------------------------------ delete

    @Override
    public boolean deleteVault(UUID owner, int vault, boolean waitForDisk) {
        Future<Boolean> deleting;
        try {
            deleting = writer.submit(() -> deleteRow(owner, vault));
        } catch (RejectedExecutionException alreadyClosed) {
            plugin.getLogger().warning("SQL storage is already closed; vault #" + vault + " of " + owner
                    + " was not deleted.");
            return false;
        }
        if (!waitForDisk) {
            return true;
        }
        try {
            return deleting.get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS).booleanValue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException error) {
            plugin.getLogger().log(Level.WARNING, "Could not delete vault #" + vault + " of " + owner, error);
            return false;
        }
    }

    private boolean deleteRow(UUID owner, int vault) {
        Connection connection = null;
        boolean broken = false;
        try {
            connection = borrow();
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + vaultsTable + " WHERE uuid = ? AND vault = ?")) {
                delete.setString(1, owner.toString());
                delete.setInt(2, vault);
                delete.executeUpdate();
            }
            try (PreparedStatement touch = connection.prepareStatement(
                    "UPDATE " + playersTable + " SET updated_at = ? WHERE uuid = ?")) {
                touch.setLong(1, System.currentTimeMillis());
                touch.setString(2, owner.toString());
                touch.executeUpdate();
            }
            return true;
        } catch (SQLException error) {
            broken = true;
            plugin.getLogger().log(Level.WARNING, "Could not delete vault #" + vault + " of " + owner, error);
            return false;
        } finally {
            giveBack(connection, broken);
        }
    }

    // ------------------------------------------------------------------- misc

    @Override
    public Set<UUID> knownOwners() {
        Set<UUID> owners = new LinkedHashSet<>();
        Connection connection = null;
        boolean broken = false;
        try {
            connection = borrow();
            try (PreparedStatement select = connection.prepareStatement("SELECT uuid FROM " + playersTable);
                 ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    try {
                        owners.add(UUID.fromString(rows.getString(1)));
                    } catch (IllegalArgumentException notAUuid) {
                        // Fila escrita a mano: se ignora.
                    }
                }
            }
        } catch (SQLException error) {
            broken = true;
            plugin.getLogger().log(Level.WARNING, "Could not list the players stored in the database", error);
        } finally {
            giveBack(connection, broken);
        }
        return owners;
    }

    @Override
    public String describe() {
        return "SQL " + settings.describe();
    }

    @Override
    public void shutdown() {
        closed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Some database writes were still pending at shutdown.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (pool) {
            while (!pool.isEmpty()) {
                closeQuietly(pool.poll());
            }
        }
    }

    // -------------------------------------------------------------- connection

    private Connection borrow() throws SQLException {
        Connection connection;
        synchronized (pool) {
            connection = pool.poll();
        }
        if (connection == null) {
            return open();
        }
        if (!isUsable(connection)) {
            closeQuietly(connection);
            return open();
        }
        return connection;
    }

    private void giveBack(Connection connection, boolean broken) {
        if (connection == null) {
            return;
        }
        if (broken || closed) {
            closeQuietly(connection);
            return;
        }
        synchronized (pool) {
            if (pool.size() < settings.getPoolSize()) {
                pool.offer(connection);
                return;
            }
        }
        closeQuietly(connection);
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        if (!settings.getUsername().isEmpty()) {
            properties.setProperty("user", settings.getUsername());
        }
        if (!settings.getPassword().isEmpty()) {
            properties.setProperty("password", settings.getPassword());
        }
        if (settings.getConnectionTimeoutMillis() > 0L) {
            properties.setProperty("connectTimeout", String.valueOf(settings.getConnectionTimeoutMillis()));
            properties.setProperty("loginTimeout", String.valueOf(settings.getConnectionTimeoutMillis() / 1000L));
        }
        Connection connection = driver.connect(settings.getJdbcUrl(), properties);
        if (connection == null) {
            throw new SQLException("The driver " + driver.getClass().getName()
                    + " does not accept the URL " + settings.getJdbcUrl());
        }
        connection.setAutoCommit(true);
        return connection;
    }

    private boolean isUsable(Connection connection) {
        try {
            if (connection.isClosed()) {
                return false;
            }
            try {
                return connection.isValid(2);
            } catch (SQLException notSupported) {
                return true;
            }
        } catch (SQLException error) {
            return false;
        }
    }

    private void awaitWrite(Future<?> writing, UUID owner) {
        try {
            writing.get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException error) {
            plugin.getLogger().log(Level.WARNING, "Failed to write the vaults of " + owner, error.getCause());
        } catch (TimeoutException slow) {
            plugin.getLogger().warning("Writing the vaults of " + owner + " took more than "
                    + WRITE_TIMEOUT_SECONDS + " seconds; it continues in the background.");
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // La conexion se descarta igualmente.
        }
    }

    private void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Si falla, la conexion se descartara al devolverla.
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nada que hacer.
        }
    }
}
