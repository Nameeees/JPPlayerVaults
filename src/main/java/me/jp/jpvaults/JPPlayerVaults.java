package me.jp.jpvaults;

import me.jp.jpvaults.command.PlayerVaultCommand;
import me.jp.jpvaults.listener.VaultListener;
import me.jp.jpvaults.manager.MessageManager;
import me.jp.jpvaults.manager.VaultManager;
import me.jp.jpvaults.storage.SqlSettings;
import me.jp.jpvaults.storage.SqlVaultStorage;
import me.jp.jpvaults.storage.StorageType;
import me.jp.jpvaults.storage.VaultCodec;
import me.jp.jpvaults.storage.VaultStorage;
import me.jp.jpvaults.storage.YamlVaultStorage;
import me.jp.jpvaults.util.Components;
import me.jp.jpvaults.util.MessageFormat;
import me.jp.jpvaults.util.Schedulers;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
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
 * metodos estables en todas las versiones. Lo especifico de versiones nuevas se
 * detecta en tiempo de ejecucion:
 *
 *  - la tecla F (1.9+) se registra por reflexion si el evento existe;
 *  - Adventure/MiniMessage se usa si el servidor lo trae, y si no, colores
 *    legacy (ver {@link Components});
 *  - los items se pueden guardar como NBT (Paper 1.20.5+) o con la
 *    serializacion de Bukkit (ver {@link VaultCodec});
 *  - los planificadores de Paper/Folia se usan si existen (ver
 *    {@link Schedulers}).
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
    private Schedulers.Task autosaveTask;
    private URLClassLoader driverLoader;

    // Valores de config.yml (se releen en /pv reload).
    private String language;
    private MessageFormat messageFormat = MessageFormat.AUTO;
    private int vaultSize;
    private int defaultAmount;
    private int maxAmount;
    private int titleMaxLength = 32;
    private boolean pickerEnabled = true;
    private String storageFolder;
    private int autosaveSeconds;
    private boolean soundsEnabled;
    private float soundVolume;
    private float soundPitch;
    private StorageType configuredStorage = StorageType.YAML;
    private SqlSettings sqlSettings;
    private String driverJar = "";
    private boolean preferNbtItems = true;
    private boolean compactYaml = false;

    // Estado real: puede ser YAML aunque la config pida MySQL si la base de
    // datos no responde (el plugin sigue funcionando y avisa por consola).
    private StorageType activeStorage = StorageType.YAML;
    private String activeStorageSignature;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        applyConfig();
        messages = new MessageManager(this);
        messages.load(language, messageFormat);

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
                + " vaults per rank, language '" + messages.getLanguage() + "', text: " + messages.describeText()
                + ", storage: " + storage.describe() + ".");
        if (Schedulers.folia()) {
            getLogger().info("Folia detected: using region schedulers for per-player work.");
        }
    }

    @Override
    public void onDisable() {
        if (vaultManager != null) {
            // Guarda todo y espera a que terminen las escrituras pendientes.
            vaultManager.shutdown();
            getLogger().info("Disabled: open vaults were saved and written to disk.");
        }
        if (driverLoader != null) {
            try {
                driverLoader.close();
            } catch (IOException ignored) {
                // Nada que hacer al apagar.
            }
            driverLoader = null;
        }
    }

    /** Recarga config.yml y los mensajes en caliente (/pv reload). */
    public void reloadPlugin() {
        reloadConfig();
        applyConfig();
        messages.load(language, messageFormat);
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

    /** Limite de caracteres visibles del titulo; 0 = sin limite. */
    public int getTitleMaxLength() {
        return titleMaxLength;
    }

    /** True si /pv sin argumentos abre el menu selector. */
    public boolean isPickerEnabled() {
        return pickerEnabled;
    }

    /** Como se guardan los items, para logs e informes. */
    public String getItemFormatName() {
        if (activeStorage == StorageType.MYSQL) {
            return VaultCodec.preferredFormatName(preferNbtItems);
        }
        return compactYaml ? VaultCodec.preferredFormatName(true) : "readable";
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
                SqlVaultStorage sql = new SqlVaultStorage(this, sqlSettings, driver, preferNbtItems);
                activeStorage = StorageType.MYSQL;
                return sql;
            } catch (ReflectiveOperationException | SQLException | IOException | RuntimeException problem) {
                getLogger().severe("Could not open the database: " + problem.getMessage());
                getLogger().severe("Falling back to YAML storage in '" + storageFolder + "'. Fix storage.mysql and "
                        + "restart; then run '/pv admin migrate' to copy those files into the database.");
            }
        }
        activeStorage = StorageType.YAML;
        return new YamlVaultStorage(this, storageFolder, compactYaml);
    }

    /**
     * Carga el driver JDBC: primero del classpath del servidor (Spigot y Paper
     * traen MySQL Connector/J) y, si no esta, de los .jar que haya en
     * plugins/JPPlayerVaults/lib/ o en la ruta de storage.mysql.driver-jar.
     */
    private Driver loadDriver(SqlSettings settings)
            throws ReflectiveOperationException, SQLException, IOException {
        List<String> candidates = new ArrayList<>();
        if (!settings.getDriverClass().isEmpty()) {
            candidates.add(settings.getDriverClass());
        } else {
            Collections.addAll(candidates, DEFAULT_DRIVERS);
        }

        for (String name : candidates) {
            Driver driver = instantiate(name, getClass().getClassLoader());
            if (driver != null) {
                getLogger().info("Using JDBC driver " + name + " from the server classpath.");
                return driver;
            }
        }

        List<URL> jars = driverJars();
        if (!jars.isEmpty()) {
            driverLoader = new URLClassLoader(jars.toArray(new URL[0]), getClass().getClassLoader());
            for (String name : candidates) {
                Driver driver = instantiate(name, driverLoader);
                if (driver != null) {
                    getLogger().info("Using JDBC driver " + name + " from " + jars.size() + " jar(s) in lib/.");
                    return driver;
                }
            }
        }

        throw new SQLException("No JDBC driver available (tried " + String.join(", ", candidates) + "). "
                + "Spigot and Paper ship MySQL Connector/J; otherwise drop the driver .jar into "
                + "plugins/JPPlayerVaults/lib/ or set storage.mysql.driver-class.");
    }

    private Driver instantiate(String className, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, true, loader);
            Object instance = type.getDeclaredConstructor().newInstance();
            return instance instanceof Driver ? (Driver) instance : null;
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                 | java.lang.reflect.InvocationTargetException | LinkageError | RuntimeException missing) {
            return null;
        }
    }

    /** Jars de la carpeta lib/ mas el de storage.mysql.driver-jar, si existe. */
    private List<URL> driverJars() throws IOException {
        List<URL> jars = new ArrayList<>();
        File folder = new File(getDataFolder(), "lib");
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    jars.add(file.toURI().toURL());
                }
            }
        }
        if (driverJar != null && !driverJar.trim().isEmpty()) {
            File extra = new File(driverJar.trim());
            if (!extra.isAbsolute()) {
                extra = new File(getDataFolder(), driverJar.trim());
            }
            if (extra.isFile()) {
                jars.add(extra.toURI().toURL());
            } else {
                getLogger().warning("storage.mysql.driver-jar points to a file that does not exist: " + extra);
            }
        }
        return jars;
    }

    private String storageSignature() {
        return configuredStorage.getKey() + "|" + storageFolder + "|" + compactYaml + "|" + preferNbtItems + "|"
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

        String rawFormat = getConfig().getString("messages-format", MessageFormat.AUTO.getKey());
        MessageFormat parsedFormat = MessageFormat.fromKey(rawFormat);
        if (parsedFormat == null) {
            getLogger().warning("messages-format='" + rawFormat + "' is not valid (legacy, minimessage or auto). "
                    + "Using auto.");
            parsedFormat = MessageFormat.AUTO;
        }
        this.messageFormat = parsedFormat;

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

        int rawTitle = getConfig().getInt("vaults.title-max-length", 32);
        this.titleMaxLength = Math.max(0, Math.min(256, rawTitle));
        if (this.titleMaxLength != rawTitle) {
            getLogger().warning("vaults.title-max-length=" + rawTitle + " is out of range (0-256). Using "
                    + this.titleMaxLength + ".");
        }

        this.pickerEnabled = getConfig().getBoolean("vaults.picker", true);

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

        String rawItemFormat = getConfig().getString("storage.item-format", "auto");
        if (rawItemFormat == null) {
            rawItemFormat = "auto";
        }
        String itemFormat = rawItemFormat.trim().toLowerCase(java.util.Locale.ROOT);
        if (itemFormat.equals("nbt")) {
            this.preferNbtItems = true;
        } else if (itemFormat.equals("bukkit")) {
            this.preferNbtItems = false;
        } else {
            if (!itemFormat.equals("auto")) {
                getLogger().warning("storage.item-format='" + rawItemFormat + "' is not valid (auto, bukkit or nbt). "
                        + "Using auto.");
            }
            this.preferNbtItems = true; // auto: NBT donde se pueda
        }
        if (this.preferNbtItems && !VaultCodec.nbtAvailable()) {
            if (itemFormat.equals("nbt")) {
                getLogger().warning("storage.item-format: nbt was requested but this server has no NBT item API "
                        + "(Paper 1.20.5+ is required); using the Bukkit format instead.");
            }
            this.preferNbtItems = false;
        }

        String rawYamlFormat = getConfig().getString("storage.yaml-format", "readable");
        String yamlFormat = rawYamlFormat == null ? "readable" : rawYamlFormat.trim().toLowerCase(java.util.Locale.ROOT);
        if (yamlFormat.equals("nbt") || yamlFormat.equals("compact")) {
            this.compactYaml = true;
        } else {
            if (!yamlFormat.equals("readable")) {
                getLogger().warning("storage.yaml-format='" + rawYamlFormat + "' is not valid (readable or nbt). "
                        + "Using readable.");
            }
            this.compactYaml = false;
        }

        String configuredDriverJar = getConfig().getString("storage.mysql.driver-jar", "");
        this.driverJar = configuredDriverJar == null ? "" : configuredDriverJar;

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
            autosaveTask = Schedulers.runGlobalTimer(this, new Runnable() {
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
