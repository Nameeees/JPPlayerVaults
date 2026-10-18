package me.jp.jpvaults;

import me.jp.jpvaults.command.PlayerVaultCommand;
import me.jp.jpvaults.listener.VaultListener;
import me.jp.jpvaults.manager.MessageManager;
import me.jp.jpvaults.manager.VaultManager;
import me.jp.jpvaults.storage.YamlVaultStorage;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * JPPlayerVaults — vaults privados por jugador.
 *
 * Una sola build, sin NMS ni dependencias, compatible con Minecraft 1.8 hasta
 * la ultima version: se compila contra la API de Spigot 1.8.8 y solo se usan
 * metodos estables en todas las versiones; lo especifico de versiones nuevas
 * (como la tecla F de 1.9+) se registra por reflexion si existe.
 */
public final class JPPlayerVaults extends JavaPlugin {

    /** Version de los archivos de configuracion que entiende esta build. */
    private static final int CONFIG_VERSION = 1;

    private MessageManager messages;
    private VaultManager vaultManager;
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

    /** Carpeta con la que se abrio el almacenamiento (cambiarla exige reinicio). */
    private String activeStorageFolder;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        applyConfig();
        messages = new MessageManager(this);
        messages.load(language);

        vaultManager = new VaultManager(this, new YamlVaultStorage(this, storageFolder));
        activeStorageFolder = storageFolder;

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
                + " vaults per rank, language '" + messages.getLanguage() + "'.");
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
        if (activeStorageFolder != null && !activeStorageFolder.equals(storageFolder)) {
            getLogger().warning("storage.folder changed from '" + activeStorageFolder + "' to '" + storageFolder
                    + "'; restart the server for the new folder to take effect.");
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

    public int getVaultSize() {
        return vaultSize;
    }

    public int getDefaultAmount() {
        return defaultAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

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

        String folder = getConfig().getString("storage.folder", "data");
        this.storageFolder = (folder == null || folder.trim().isEmpty()) ? "data" : folder.trim();

        this.autosaveSeconds = Math.max(0, getConfig().getInt("storage.autosave-seconds", 300));

        this.soundsEnabled = getConfig().getBoolean("sounds.enabled", true);
        this.soundVolume = clampSound("sounds.volume", getConfig().getDouble("sounds.volume", 0.7D), 0.0D, 1.0D);
        this.soundPitch = clampSound("sounds.pitch", getConfig().getDouble("sounds.pitch", 1.4D), 0.5D, 2.0D);
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
