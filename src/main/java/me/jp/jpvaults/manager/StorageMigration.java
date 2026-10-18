package me.jp.jpvaults.manager;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.model.PlayerVaultData;
import me.jp.jpvaults.storage.VaultStorage;
import me.jp.jpvaults.storage.YamlVaultStorage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Copia los archivos YAML de la carpeta de datos a la base de datos activa
 * ({@code /pv admin migrate}).
 *
 * Va en lotes sobre el hilo del servidor: leer YAML deserializa ItemStacks y
 * eso solo es seguro en el hilo principal; la escritura la hace el backend SQL
 * en su propio hilo, asi que la migracion no bloquea el servidor.
 *
 * Los archivos YAML no se borran nunca: quedan como copia de seguridad hasta
 * que el administrador los elimine a mano.
 */
public final class StorageMigration {

    /** Jugadores copiados por tick; 5/tick son 100 por segundo. */
    private static final int BATCH_PER_TICK = 5;
    private static final int PROGRESS_EVERY = 25;

    private final JPPlayerVaults plugin;
    private final CommandSender initiator;
    private final VaultStorage target;
    private final YamlVaultStorage source;
    private final List<UUID> pending;
    private final boolean overwrite;

    private BukkitTask task;
    private int index;
    private int migrated;
    private int skipped;
    private int failed;
    private int vaults;
    private int items;

    public StorageMigration(JPPlayerVaults plugin, CommandSender initiator, boolean overwrite, String yamlFolder) {
        this.plugin = plugin;
        this.initiator = initiator;
        this.overwrite = overwrite;
        this.target = plugin.getStorage();
        this.source = new YamlVaultStorage(plugin, yamlFolder);
        List<UUID> queue = new ArrayList<>();
        if (overwrite) {
            queue.addAll(source.knownOwners());
        } else {
            java.util.Set<UUID> alreadyStored = target.knownOwners();
            for (UUID owner : source.knownOwners()) {
                if (alreadyStored.contains(owner)) {
                    skipped++;
                } else {
                    queue.add(owner);
                }
            }
        }
        this.pending = queue;
    }

    public int getTotal() {
        return pending.size();
    }

    public int getSkipped() {
        return skipped;
    }

    /** Arranca el proceso por lotes. */
    public void start() {
        if (pending.isEmpty()) {
            finish();
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 1L, 1L);
    }

    private void tick() {
        int processed = 0;
        while (index < pending.size() && processed < BATCH_PER_TICK) {
            UUID owner = pending.get(index++);
            processed++;
            try {
                PlayerVaultData data = source.load(owner);
                if (!data.getVaults().isEmpty()) {
                    vaults += data.getVaults().size();
                    for (ItemStack[] contents : data.getVaults().values()) {
                        items += countItems(contents);
                    }
                }
                target.save(data, false); // el backend escribe en su propio hilo
                migrated++;
            } catch (RuntimeException error) {
                failed++;
                plugin.getLogger().log(Level.WARNING,
                        "Migration: could not copy the data of " + owner + " to the database", error);
            }
        }
        if (processed > 0 && index % PROGRESS_EVERY < BATCH_PER_TICK) {
            plugin.getMessages().send(initiator, "admin.migrate.progress",
                    "done", String.valueOf(index),
                    "total", String.valueOf(pending.size()));
        }
        if (index >= pending.size()) {
            finish();
        }
    }

    private void finish() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        source.shutdown();
        if (initiator instanceof Player && !((Player) initiator).isOnline()) {
            plugin.getLogger().info("Migration finished: " + migrated + " player(s), " + vaults + " vault(s), "
                    + items + " item(s), " + skipped + " skipped, " + failed + " failed.");
            return;
        }
        plugin.getMessages().send(initiator, "admin.migrate.done",
                "migrated", String.valueOf(migrated),
                "vaults", String.valueOf(vaults),
                "items", String.valueOf(items),
                "skipped", String.valueOf(skipped),
                "failed", String.valueOf(failed));
        plugin.getLogger().info("Migration finished: " + migrated + " player(s), " + vaults + " vault(s), "
                + items + " item(s), " + skipped + " skipped, " + failed + " failed"
                + (overwrite ? " (overwrite mode)" : ""));
    }

    private int countItems(ItemStack[] contents) {
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
