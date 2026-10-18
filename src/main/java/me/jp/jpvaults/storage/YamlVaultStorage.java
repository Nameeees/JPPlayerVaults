package me.jp.jpvaults.storage;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.model.PlayerVaultData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
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
 * Guarda cada jugador en {@code <carpeta>/<uuid>.yml} con un item por slot no
 * vacio:
 *
 *   vaults:
 *     "3":
 *       items:
 *         "0": <objeto YAML de Bukkit>
 *
 * Ventajas: legible/editable por administradores y compatible entre versiones
 * de Minecraft (Bukkit convierte al cargar los nombres de material que fueron
 * renombrados en 1.13+). La escritura corre en un hilo propio y en orden.
 *
 * Proteccion de datos: si un archivo no se puede leer se conserva una copia
 * .bak y el vault no se abre (mejor eso que mostrar uno vacio y sobrescribir),
 * y un item que falle al deserializarse no impide cargar el resto del vault.
 */
public final class YamlVaultStorage implements VaultStorage {

    private final JPPlayerVaults plugin;
    private final File folder;
    private final ExecutorService writer;

    public YamlVaultStorage(JPPlayerVaults plugin, String folderName) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the player data folder: " + folder);
        }
        this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "JPPlayerVaults-Writer");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public PlayerVaultData load(UUID owner) {
        PlayerVaultData data = new PlayerVaultData(owner);
        File file = fileOf(owner);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getKeys(false).isEmpty() && file.length() > 0L) {
            // Bukkit devuelve una configuracion vacia cuando el YAML esta roto:
            // se guarda una copia y se corta, para no sobrescribir los objetos.
            backupUnreadable(file, "The file could not be parsed");
            throw new StorageException("The player file " + file.getName() + " is not readable YAML");
        }
        data.setLastName(yaml.getString("last-known-name", ""));
        ConfigurationSection vaults = yaml.getConfigurationSection("vaults");
        if (vaults == null) {
            data.markClean();
            return data;
        }
        int brokenItems = 0;
        for (String vaultKey : vaults.getKeys(false)) {
            int number = parseKey(vaultKey);
            ConfigurationSection items = vaults.getConfigurationSection(vaultKey + ".items");
            if (number < 1 || items == null) {
                continue;
            }
            ItemStack[] contents = data.getVaultContents(number);
            for (String slotKey : items.getKeys(false)) {
                int slot = parseKey(slotKey);
                if (slot < 0 || slot >= contents.length) {
                    continue;
                }
                try {
                    ItemStack item = items.getItemStack(slotKey);
                    if (item != null && item.getType() != Material.AIR) {
                        contents[slot] = item;
                    }
                } catch (RuntimeException broken) {
                    brokenItems++;
                    plugin.getLogger().warning("Could not read slot " + slotKey + " of vault #" + number
                            + " for " + owner + ": " + broken.getMessage());
                }
            }
        }
        if (brokenItems > 0) {
            backupUnreadable(file, brokenItems + " item(s) could not be read");
        }
        data.markClean(); // recien cargado: no hay nada pendiente que guardar
        return data;
    }

    @Override
    public void save(PlayerVaultData data, boolean waitForDisk) {
        final long revision = data.getRevision();
        final YamlConfiguration yaml = toYaml(data); // la foto se toma en el hilo del servidor
        final File file = fileOf(data.getOwner());
        final UUID owner = data.getOwner();
        Future<?> writing;
        try {
            writing = writer.submit(() -> {
                if (write(file, yaml, owner)) {
                    data.markCleanIfRevision(revision);
                }
            });
        } catch (RejectedExecutionException alreadyClosed) {
            plugin.getLogger().warning("Vault storage is already closed; the change for " + owner + " was not written.");
            return;
        }
        if (waitForDisk) {
            awaitWrite(writing, owner);
        }
    }

    @Override
    public boolean deleteVault(UUID owner, int vault, boolean waitForDisk) {
        File file = fileOf(owner);
        if (!file.exists()) {
            return true;
        }
        PlayerVaultData data = load(owner); // lanza StorageException si no se puede leer
        data.getVaults().remove(vault);     // al no estar en el mapa, deja de escribirse
        save(data, waitForDisk);
        return true;
    }

    @Override
    public Set<UUID> knownOwners() {
        Set<UUID> owners = new LinkedHashSet<>();
        File[] files = folder.listFiles();
        if (files == null) {
            return owners;
        }
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || !name.endsWith(".yml")) {
                continue;
            }
            try {
                owners.add(UUID.fromString(name.substring(0, name.length() - 4)));
            } catch (IllegalArgumentException notAUuid) {
                // Copias .bak o archivos con otro nombre: se ignoran.
            }
        }
        return owners;
    }

    @Override
    public String describe() {
        return "YAML " + folder.getAbsolutePath();
    }

    /** Ruta de la carpeta de datos, para mensajes y para la migracion. */
    public File getFolder() {
        return folder;
    }

    /** Espera acotada: en un disco normal la escritura tarda milisegundos. */
    private void awaitWrite(Future<?> writing, UUID owner) {
        try {
            writing.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write the vault of " + owner, e.getCause());
        } catch (TimeoutException e) {
            plugin.getLogger().warning("Writing the vault of " + owner
                    + " took more than 5 seconds; it continues in the background.");
        }
    }

    @Override
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Some vault writes were still pending at shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private YamlConfiguration toYaml(PlayerVaultData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("JPPlayerVaults - player data\n"
                + "Generated automatically: do not edit it while the server is running.\n"
                + "vaults.<number>.items.<slot> stores one item per non-empty slot.");
        yaml.set("uuid", data.getOwner().toString());
        yaml.set("last-known-name", data.getLastName());
        for (Map.Entry<Integer, ItemStack[]> entry : data.getVaults().entrySet()) {
            ConfigurationSection items = yaml.createSection("vaults." + entry.getKey() + ".items");
            ItemStack[] contents = entry.getValue();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item != null && item.getType() != Material.AIR) {
                    items.set(String.valueOf(slot), item);
                }
            }
        }
        return yaml;
    }

    /**
     * Escribe en un .tmp y lo mueve encima del archivo real: si el servidor
     * muere a mitad de escritura, el archivo bueno nunca queda a medias.
     */
    private boolean write(File file, YamlConfiguration yaml, UUID owner) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save the vault of " + owner, e);
            deleteQuietly(temp);
            return false;
        }
    }

    /**
     * Copia de seguridad de un archivo que no se pudo leer, para que el
     * siguiente guardado no borre datos que un administrador podria recuperar.
     */
    private void backupUnreadable(File file, String reason) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backup = new File(file.getParentFile(), file.getName() + "." + stamp + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().severe(reason + " in " + file.getName()
                    + ". A backup was kept as " + backup.getName() + " before anything is overwritten.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, reason + " in " + file
                    + " and the backup copy could not be created", e);
        }
    }

    private void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // No hay nada mas que hacer: se reintentara en el proximo guardado.
        }
    }

    private File fileOf(UUID owner) {
        return new File(folder, owner.toString() + ".yml");
    }

    /** Devuelve -1 si la clave no es un numero (archivo editado a mano). */
    private int parseKey(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
