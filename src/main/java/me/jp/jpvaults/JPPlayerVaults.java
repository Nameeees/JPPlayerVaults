package me.jp.jpvaults;

import me.jp.jpvaults.command.PlayerVaultCommand;
import me.jp.jpvaults.listener.VaultListener;
import me.jp.jpvaults.manager.MessageManager;
import me.jp.jpvaults.manager.VaultManager;
import me.jp.jpvaults.storage.SqlSettings;
import me.jp.jpvaults.storage.SqlVaultStorage;
import me.jp.jpvaults.storage.StorageType;
import me.jp.jpvaults.storage.VaultStorage;
import me.jp.jpvaults.storage.YamlVaultStorage;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPPlayerVaults — vaults privados por jugador.
 *
 * Una sola build, sin NMS ni dependencias, compatible con Minecraft 1.8 hasta
 * la ultima version: se compila contra la API de Spigot 1.8.8 y solo se usan
 * metodos estables en todas las versiones; lo especifico de versiones nuevas
 * (como la tecla F de 1.9+) se registra por reflexion si existe.
 *
 * Los datos se guardan en YAML (por defecto) o en una base de datos por JDBC
 * (storage.type: mysql). El driver JDBC lo pone el servidor o lo elige el
 * administrador; el plugin solo usa java.sql.
 */
public final class JPPlayerVaults extends JavaPlugin {

    /** Version de los archivos de configuracion que entiende esta build. */
    private static final int CONFIG_VERSION = 1;

    /** Drivers que se prueban cuando storage.mysql.driver-class esta vacio. */
    private static final String[] DEFAULT_DRIVERS = {
            "com.mysql.cj.jdbc.Driver",
            "com.mysql.jdbc.Driver",
            "org.mariadb.jdbc.Driver"
    };

    private MessageManager messages;
    private VaultManager vaultManager;
    private VaultStorage storage;
    private BukkitTask autosaveTask;

    // Valores de config.yml (se releen en /pv reload).
    private String language;
    private int vaultSize;
    private int defaultAmount;
    private int maxAmount;
    private String storageFolder;
    private int autosaveSeconds;
    private boolean soundsEnabled;
    private float soundVolume;
    private float soundPitch;
    private StorageType configuredStorage = StorageType.YAML;
    private SqlSettings sqlSettings;

    // Estado real: puede ser YAML aunque la config pida MySQL si la base de
    // datos no responde (el plugin sigue funcionando y avisa por consola).
    private StorageType activeStorage = StorageType.YAML;
    private String activeStorageSignature;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        applyConfig();
        messages = new MessageManager(this);
        messages.load(language);

        storage = createStorage();
        vaultManager = new VaultManager(this, storage);
        activeStorageSignature = storageSignature();

        PluginCommand command = getCommand("pv");
        if (command == null) {
            // Sin el comando en plugin.yml no hay plugin utilizable: mejor
            // desactivarse que quedar a medias con una NullPointerException.
            getLogger().severe("The 'pv' command is missing from plugin.yml; disabling JPPlayerVaults.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PlayerVaultCommand executor = new PlayerVaultCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        VaultListener listener = new VaultListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        registerSwapHandGuard(listener);
        vaultManager.closeStaleVaultViews(); // sin GUIs zombi de un enable anterior

        startAutosave();
        getLogger().info("Enabled: " + vaultSize + " slots per vault, up to " + maxAmount
                + " vaults per rank, language '" + messages.getLanguage() + "', storage: " + storage.describe() + ".");
    }

    @Override
    public void onDisable() {
        if (vaultManager != null) {
            // Cierra las vistas abiertas (anti-dupe tras /reload), guarda todo
            // y espera a que terminen las escrituras pendientes.
            vaultManager.shutdown();
            getLogger().info("Disabled: open vaults were saved and written to disk.");
        }
    }

    /** Recarga config.yml y los mensajes en caliente (/pv reload). */
    public void reloadPlugin() {
        reloadConfig();
        applyConfig();
        messages.load(language);
        startAutosave();
        if (configuredStorage != activeStorage) {
            getLogger().warning("storage.type is now '" + configuredStorage.getKey() + "' but the plugin is using '"
                    + activeStorage.getKey() + "'; restart the server to switch backends.");
        } else if (!storageSignature().equals(activeStorageSignature)) {
            getLogger().warning("storage settings changed; restart the server for them to take effect.");
        }
    }

    /** Reproduce un sonido de interfaz respetando config.yml. */
    public void playUiSound(Player player, UiSound sound) {
        if (soundsEnabled) {
            sound.play(player, soundVolume, soundPitch);
        }
    }

    public MessageManager getMessages() {
        return messages;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public VaultStorage getStorage() {
        return storage;
    }

    /** Backend que se esta usando de verdad (YAML si el SQL no respondio). */
    public StorageType getStorageType() {
        return activeStorage;
    }

    /** Backend pedido en config.yml, aunque no se haya podido abrir. */
    public StorageType getConfiguredStorageType() {
        return configuredStorage;
    }

    /** Texto con el destino de los datos, sin credenciales. */
    public String getStorageDescription() {
        return storage == null ? "-" : storage.describe();
    }

    public SqlSettings getSqlSettings() {
        return sqlSettings;
    }

    public String getStorageFolderName() {
        return storageFolder;
    }

    public int getVaultSize() {
        return vaultSize;
    }

    public int getDefaultAmount() {
        return defaultAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getAutosaveSeconds() {
        return autosaveSeconds;
    }

    // ------------------------------------------------------------- storage

    /**
     * Abre el backend configurado. Si MySQL esta pedido pero no responde, se
     * sigue con YAML: el servidor no se queda sin vaults y el administrador
     * puede migrar los datos despues con /pv admin migrate.
     */
    private VaultStorage createStorage() {
        if (configuredStorage == StorageType.MYSQL) {
            try {
                Driver driver = loadDriver(sqlSettings);
                SqlVaultStorage sql = new SqlVaultStorage(this, sqlSettings, driver);
                activeStorage = StorageType.MYSQL;
                return sql;
            } catch (ReflectiveOperationException | SQLException | RuntimeException problem) {
                getLogger().severe("Could not open the database: " + problem.getMessage());
                getLogger().severe("Falling back to YAML storage in '" + storageFolder + "'. Fix storage.mysql and "
                        + "restart; then run '/pv admin migrate' to copy those files into the database.");
            }
        }
        activeStorage = StorageType.YAML;
        return new YamlVaultStorage(this, storageFolder);
    }

    /** Carga el driver JDBC pedido o, si no hay, el primero de los conocidos. */
    private Driver loadDriver(SqlSettings settings) throws ReflectiveOperationException, SQLException {
        List<String> candidates = new ArrayList<>();
        if (!settings.getDriverClass().isEmpty()) {
            candidates.add(settings.getDriverClass());
        } else {
            Collections.addAll(candidates, DEFAULT_DRIVERS);
        }
        ReflectiveOperationException lastFailure = null;
        for (String name : candidates) {
            try {
                Class<?> type = Class.forName(name);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof Driver) {
                    getLogger().info("Using JDBC driver " + name + ".");
                    return (Driver) instance;
                }
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                     | IllegalAccessException | java.lang.reflect.InvocationTargetException missing) {
                lastFailure = missing;
            }
        }
        String tried = settings.getDriverClass().isEmpty()
                ? String.join(", ", candidates)
                : settings.getDriverClass();
        throw new SQLException("No JDBC driver available (tried " + tried + "). "
                + "Set storage.mysql.driver-class to a driver present on the server.");
    }

    private String storageSignature() {
        return configuredStorage.getKey() + "|" + storageFolder + "|"
                + (sqlSettings == null ? "" : sqlSettings.describe());
    }

    // -------------------------------------------------------------- config

    /** Lee config.yml, valida los valores y avisa de los que se corrigen. */
    private void applyConfig() {
        int fileVersion = getConfig().getInt("config-version", CONFIG_VERSION);
        if (fileVersion > CONFIG_VERSION) {
            getLogger().warning("config.yml comes from a newer plugin version (config-version " + fileVersion
                    + "). Check the values: this build may not understand every option.");
        }

        String configuredLanguage = getConfig().getString("language", MessageManager.DEFAULT_LANGUAGE);
        this.language = configuredLanguage == null ? MessageManager.DEFAULT_LANGUAGE : configuredLanguage;

        int rawSize = getConfig().getInt("vaults.size", 54);
        int size = Math.max(9, Math.min(54, rawSize));
        if (size % 9 != 0) {
            size -= size % 9;
        }
        if (size != rawSize) {
            getLogger().warning("vaults.size=" + rawSize + " is not valid (multiple of 9 between 9 and 54). Using " + size + ".");
        }
        this.vaultSize = size;

        this.defaultAmount = Math.max(0, getConfig().getInt("vaults.default-amount", 1));

        int rawMax = getConfig().getInt("vaults.max-amount", 10);
        int max = Math.max(1, Math.min(100, rawMax));
        if (max != rawMax) {
            getLogger().warning("vaults.max-amount=" + rawMax + " is out of range (1-100). Using " + max + ".");
        }
        this.maxAmount = max;
        if (this.defaultAmount > max) {
            getLogger().warning("vaults.default-amount (" + this.defaultAmount
                    + ") is higher than vaults.max-amount (" + max + "); rank permissions will be capped at " + max + ".");
        }

        String rawType = getConfig().getString("storage.type", StorageType.YAML.getKey());
        StorageType parsed = StorageType.fromKey(rawType);
        if (parsed == null) {
            getLogger().warning("storage.type='" + rawType + "' is not valid (yaml or mysql). Using yaml.");
            parsed = StorageType.YAML;
        }
        this.configuredStorage = parsed;

        String folder = getConfig().getString("storage.folder", "data");
        this.storageFolder = (folder == null || folder.trim().isEmpty()) ? "data" : folder.trim();

        this.autosaveSeconds = Math.max(0, getConfig().getInt("storage.autosave-seconds", 300));
        this.sqlSettings = readSqlSettings();

        this.soundsEnabled = getConfig().getBoolean("sounds.enabled", true);
        this.soundVolume = clampSound("sounds.volume", getConfig().getDouble("sounds.volume", 0.7D), 0.0D, 1.0D);
        this.soundPitch = clampSound("sounds.pitch", getConfig().getDouble("sounds.pitch", 1.4D), 0.5D, 2.0D);
    }

    /** Lee la seccion storage.mysql y monta la URL JDBC. */
    private SqlSettings readSqlSettings() {
        String host = valueOr(getConfig().getString("storage.mysql.host", "127.0.0.1"), "127.0.0.1");
        int port = getConfig().getInt("storage.mysql.port", 3306);
        String database = valueOr(getConfig().getString("storage.mysql.database", "jpvaults"), "jpvaults");
        String username = getConfig().getString("storage.mysql.username", "root");
        String password = getConfig().getString("storage.mysql.password", "");
        String driver = getConfig().getString("storage.mysql.driver-class", "");
        String explicitUrl = getConfig().getString("storage.mysql.jdbc-url", "");
        String properties = getConfig().getString("storage.mysql.properties", "useSSL=false&characterEncoding=utf8");
        int poolSize = getConfig().getInt("storage.mysql.pool-size", 4);
        long timeout = getConfig().getLong("storage.mysql.connection-timeout-ms", 5000L);

        String prefix = getConfig().getString("storage.mysql.table-prefix", "jpvaults_");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,16}")) {
            getLogger().warning("storage.mysql.table-prefix='" + prefix + "' is not valid; using 'jpvaults_'.");
            prefix = "jpvaults_";
        }

        String jdbcUrl = explicitUrl == null ? "" : explicitUrl.trim();
        if (jdbcUrl.isEmpty()) {
            StringBuilder builder = new StringBuilder("jdbc:mysql://").append(host).append(':').append(port)
                    .append('/').append(database);
            if (properties != null && !properties.trim().isEmpty()) {
                builder.append('?').append(properties.trim());
            }
            jdbcUrl = builder.toString();
        }
        return new SqlSettings(driver, jdbcUrl, username, password, prefix, poolSize, timeout);
    }

    private String valueOr(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    /** Sonidos fuera de rango los recorta Minecraft igual, pero avisamos. */
    private float clampSound(String key, double value, double min, double max) {
        if (value < min || value > max) {
            double clamped = Math.max(min, Math.min(max, value));
            getLogger().warning(key + "=" + value + " is out of range (" + min + "-" + max + "). Using " + clamped + ".");
            return (float) clamped;
        }
        return (float) value;
    }

    /** (Re)inicia el guardado automatico de los vaults abiertos. */
    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (autosaveSeconds > 0) {
            long ticks = autosaveSeconds * 20L;
            autosaveTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    vaultManager.autosave();
                }
            }, ticks, ticks);
        }
    }

    /**
     * En 1.9+ existe el intercambio de manos (tecla F). Con un vault abierto se
     * cancela para que el objeto del cursor no pueda duplicarse. La clase del
     * evento no existe en 1.8, por eso se registra por reflexion: si el evento
     * esta disponible se protege, y si no, simplemente no se registra.
     */
    @SuppressWarnings("unchecked")
    private void registerSwapHandGuard(VaultListener listener) {
        Class<?> swapClass;
        try {
            swapClass = Class.forName("org.bukkit.event.player.PlayerSwapHandItemsEvent");
        } catch (ClassNotFoundException notModern) {
            return; // 1.8: no existe la tecla F, nada que proteger.
        }
        try {
            getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) swapClass,
                    listener,
                    EventPriority.LOWEST,
                    (registered, event) -> {
                        try {
                            Object player = event.getClass().getMethod("getPlayer").invoke(event);
                            if (player instanceof Player && vaultManager.hasOpenVault((Player) player)) {
                                event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                            }
                        } catch (ReflectiveOperationException ignored) {
                            // Si una version cambia la firma, no se protege: no rompemos nada.
                        }
                    },
                    this,
                    true);
        } catch (Throwable problem) {
            getLogger().warning("Could not register the off-hand swap guard: " + problem.getMessage());
        }
    }
}
