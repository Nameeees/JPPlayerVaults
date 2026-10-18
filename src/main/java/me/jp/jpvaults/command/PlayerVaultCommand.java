package me.jp.jpvaults.command;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.manager.MessageManager;
import me.jp.jpvaults.manager.StorageMigration;
import me.jp.jpvaults.manager.VaultManager;
import me.jp.jpvaults.storage.StorageException;
import me.jp.jpvaults.storage.StorageType;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.lang.reflect.Method;

/**
 * Comando /pv: abre el vault del jugador, gestiona /pv reload y los
 * subcomandos de administrador:
 *
 * <pre>
 * /pv admin open &lt;jugador&gt; &lt;n&gt;      abre el vault de otro jugador (online u offline)
 * /pv admin info [jugador]         estado del plugin o de un jugador
 * /pv admin delete &lt;jugador&gt; &lt;n&gt; confirm
 * /pv admin migrate [force]        copia los archivos YAML a la base de datos
 * </pre>
 */
public final class PlayerVaultCommand implements CommandExecutor, TabCompleter {

    /** Permite usar /pv. Por defecto lo tiene todo el mundo. */
    public static final String PERMISSION_USE = "jpvaults.use";
    /** Permite recargar el plugin en caliente. */
    public static final String PERMISSION_RELOAD = "jpvaults.reload";
    /** Permite los subcomandos /pv admin. */
    public static final String PERMISSION_ADMIN = "jpvaults.admin";

    private static final String[] ADMIN_SUBCOMMANDS = {"open", "info", "delete", "migrate"};

    private final JPPlayerVaults plugin;
    /** Bukkit.getOfflinePlayerIfCached, resuelto una sola vez (Paper). */
    private Method offlineLookup;
    private boolean offlineLookupResolved;

    public PlayerVaultCommand(JPPlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager messages = plugin.getMessages();
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reload(sender);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }

        if (!(sender instanceof Player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        Player player = (Player) sender;

        // jpvaults.use permite a los administradores cerrar el acceso al
        // comando (por ejemplo en un lobby) sin tocar los rangos de vaults.
        if (!player.hasPermission(PERMISSION_USE)) {
            messages.send(player, "command.no-permission");
            plugin.playUiSound(player, UiSound.DENY);
            return true;
        }

        if (args.length == 0) {
            // Sin argumentos: menu selector (si esta activado) o la ayuda.
            if (plugin.isPickerEnabled() && plugin.getVaultManager().availableVaults(player) > 0) {
                try {
                    plugin.getVaultManager().openPicker(player);
                } catch (StorageException failure) {
                    messages.send(player, "command.storage-error");
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not open the vault picker of " + player.getName(), failure);
                }
                return true;
            }
            messages.sendList(player, "command.help",
                    "available", String.valueOf(plugin.getVaultManager().availableVaults(player)));
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            messages.sendList(player, "command.help",
                    "available", String.valueOf(plugin.getVaultManager().availableVaults(player)));
            return true;
        }

        int number = parseNumber(args[0]);
        if (number < 1) {
            messages.send(player, "command.invalid-number", "input", args[0]);
            plugin.playUiSound(player, UiSound.DENY);
            return true;
        }

        VaultManager vaults = plugin.getVaultManager();
        int available = vaults.availableVaults(player);
        if (available < 1) {
            messages.send(player, "command.no-vaults");
            plugin.playUiSound(player, UiSound.DENY);
            return true;
        }
        if (number > available) {
            messages.send(player, "command.locked",
                    "number", String.valueOf(number),
                    "available", String.valueOf(available));
            plugin.playUiSound(player, UiSound.DENY);
            return true;
        }

        try {
            vaults.openVault(player, number);
        } catch (StorageException failure) {
            messages.send(player, "command.storage-error");
            plugin.getLogger().log(Level.SEVERE,
                    "Could not open vault #" + number + " of " + player.getName(), failure);
        }
        return true;
    }

    // ------------------------------------------------------------- /pv admin

    private void admin(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessages();
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            messages.send(sender, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.sendList(sender, "admin.help");
            return;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (subcommand.equals("info")) {
            adminInfo(sender, args);
        } else if (subcommand.equals("open") || subcommand.equals("view")) {
            adminOpen(sender, args);
        } else if (subcommand.equals("delete")) {
            adminDelete(sender, args);
        } else if (subcommand.equals("migrate")) {
            adminMigrate(sender, args);
        } else {
            messages.send(sender, "admin.unknown", "input", args[1]);
            messages.sendList(sender, "admin.help");
        }
    }

    private void adminOpen(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessages();
        if (!(sender instanceof Player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 4) {
            messages.send(sender, "admin.open-usage");
            return;
        }
        Player viewer = (Player) sender;
        Target target = resolve(args[2]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[2]);
            return;
        }
        int number = parseNumber(args[3]);
        if (number < 1 || number > plugin.getMaxAmount()) {
            messages.send(sender, "admin.invalid-number", "input", args[3]);
            return;
        }

        VaultManager manager = plugin.getVaultManager();
        UUID otherViewer = manager.findViewer(target.uuid, number, viewer.getUniqueId());
        if (otherViewer != null) {
            Player online = Bukkit.getPlayer(otherViewer);
            messages.send(sender, "admin.vault-open",
                    "player", online == null ? otherViewer.toString() : online.getName());
            return;
        }

        try {
            manager.openVaultFor(viewer, target.uuid, target.name, number, true);
        } catch (StorageException failure) {
            messages.send(sender, "command.storage-error");
            plugin.getLogger().log(Level.SEVERE,
                    "Could not open vault #" + number + " of " + target.name + " for " + viewer.getName(), failure);
            return;
        }
        messages.send(sender, "admin.opened",
                "player", target.name,
                "number", String.valueOf(number));
        plugin.getLogger().info(viewer.getName() + " opened vault #" + number + " of " + target.name
                + (target.online ? " (admin view)" : " (admin view, offline player)"));
    }

    private void adminInfo(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessages();
        VaultManager manager = plugin.getVaultManager();

        if (args.length < 3) {
            messages.send(sender, "admin.info.header");
            messages.send(sender, "admin.info.storage",
                    "type", plugin.getStorageType().getKey(),
                    "target", plugin.getStorageDescription());
            messages.send(sender, "admin.info.players",
                    "players", String.valueOf(manager.getStorage().knownOwners().size()));
            messages.send(sender, "admin.info.settings",
                    "size", String.valueOf(plugin.getVaultSize()),
                    "default", String.valueOf(plugin.getDefaultAmount()),
                    "max", String.valueOf(plugin.getMaxAmount()),
                    "autosave", String.valueOf(plugin.getAutosaveSeconds()));
            return;
        }

        Target target = resolve(args[2]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[2]);
            return;
        }
        try {
            messages.send(sender, "admin.info.player-header", "player", target.name);
            messages.send(sender, "admin.info.player-id",
                    "uuid", target.uuid.toString(),
                    "state", target.online ? "online" : "offline");
            int used = manager.vaultsWithData(target.uuid);
            int items = manager.countItems(target.uuid);
            messages.send(sender, "admin.info.player-vaults",
                    "used", String.valueOf(used),
                    "items", String.valueOf(items));
            if (target.online) {
                Player online = Bukkit.getPlayer(target.uuid);
                messages.send(sender, "admin.info.player-rank",
                        "available", String.valueOf(manager.availableVaults(online)));
            }
            messages.send(sender, "admin.info.player-open", "open", describeOpenVaults(target.uuid));
        } finally {
            manager.releaseIfIdle(target.uuid);
        }
    }

    private void adminDelete(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessages();
        if (args.length < 5 || !args[4].equalsIgnoreCase("confirm")) {
            messages.send(sender, "admin.delete-usage");
            return;
        }
        Target target = resolve(args[2]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[2]);
            return;
        }
        int number = parseNumber(args[3]);
        if (number < 1 || number > plugin.getMaxAmount()) {
            messages.send(sender, "admin.invalid-number", "input", args[3]);
            return;
        }
        if (!plugin.getVaultManager().deleteVault(target.uuid, number)) {
            messages.send(sender, "admin.delete-failed");
            return;
        }
        messages.send(sender, "admin.delete-done",
                "player", target.name,
                "number", String.valueOf(number));
        plugin.getLogger().info(sender.getName() + " deleted vault #" + number + " of " + target.name
                + " (storage: " + plugin.getStorageType().getKey() + ")");
    }

    private void adminMigrate(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessages();
        if (plugin.getStorageType() != StorageType.MYSQL) {
            messages.send(sender, "admin.migrate.not-sql", "type", plugin.getStorageType().getKey());
            return;
        }
        boolean overwrite = args.length >= 3
                && (args[2].equalsIgnoreCase("force") || args[2].equalsIgnoreCase("overwrite"));
        StorageMigration migration = new StorageMigration(plugin, sender, overwrite, plugin.getStorageFolderName());
        messages.send(sender, "admin.migrate.start",
                "total", String.valueOf(migration.getTotal()),
                "skipped", String.valueOf(migration.getSkipped()));
        migration.start();
    }

    // ---------------------------------------------------------------- helpers

    private void reload(CommandSender sender) {
        MessageManager messages = plugin.getMessages();
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            messages.send(sender, "command.no-permission");
            return;
        }
        plugin.reloadPlugin();
        messages.send(sender, "command.reloaded");
        if (sender instanceof Player) {
            plugin.playUiSound((Player) sender, UiSound.SUCCESS);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            if (sender instanceof Player && sender.hasPermission(PERMISSION_USE)) {
                int available = plugin.getVaultManager().availableVaults((Player) sender);
                for (int number = 1; number <= available; number++) {
                    addIfStarts(suggestions, String.valueOf(number), typed);
                }
            }
            if (sender.hasPermission(PERMISSION_USE)) {
                addIfStarts(suggestions, "help", typed);
            }
            if (sender.hasPermission(PERMISSION_RELOAD)) {
                addIfStarts(suggestions, "reload", typed);
            }
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                addIfStarts(suggestions, "admin", typed);
            }
            return suggestions;
        }

        if (!args[0].equalsIgnoreCase("admin") || !sender.hasPermission(PERMISSION_ADMIN)) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            for (String subcommand : ADMIN_SUBCOMMANDS) {
                addIfStarts(suggestions, subcommand, typed);
            }
            return suggestions;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (subcommand.equals("migrate")) {
                addIfStarts(suggestions, "force", typed);
            } else {
                suggestions.addAll(knownPlayerNames(typed));
            }
            return suggestions;
        }
        if (args.length == 4 && (subcommand.equals("open") || subcommand.equals("view")
                || subcommand.equals("delete"))) {
            for (int number = 1; number <= plugin.getMaxAmount(); number++) {
                addIfStarts(suggestions, String.valueOf(number), typed);
            }
            return suggestions;
        }
        if (args.length == 5 && subcommand.equals("delete")) {
            addIfStarts(suggestions, "confirm", typed);
        }
        return suggestions;
    }

    /** Nombres de jugadores para el tab: primero los conectados, luego la cache. */
    private List<String> knownPlayerNames(String typed) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            addIfStarts(names, online.getName(), typed);
        }
        if (typed.length() >= 2 && names.size() < 20) {
            for (OfflinePlayer cached : Bukkit.getOfflinePlayers()) {
                if (names.size() >= 20) {
                    break;
                }
                String name = cached.getName();
                if (name != null) {
                    addIfStarts(names, name, typed);
                }
            }
        }
        return names;
    }

    /**
     * Busca a un jugador por nombre o UUID sin consultar a Mojang: conectados
     * primero y, si no, la cache de jugadores del servidor (los que ya han
     * jugado). En Paper se usa getOfflinePlayerIfCached, que es O(1) y no
     * bloquea; en el resto se recorre la cache, que es lo unico disponible.
     * El UUID sirve para administrar cuentas que ya no estan en la cache.
     */
    private Target resolve(String nameOrId) {
        UUID parsed = parseUuid(nameOrId);
        if (parsed != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(parsed);
            String known = offline.getName();
            return new Target(parsed, (known == null || known.isEmpty()) ? nameOrId : known, offline.isOnline());
        }
        Player online = Bukkit.getPlayerExact(nameOrId);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName(), true);
        }
        OfflinePlayer cachedPlayer = cachedOfflinePlayer(nameOrId);
        if (cachedPlayer != null) {
            String name = cachedPlayer.getName();
            return new Target(cachedPlayer.getUniqueId(), name == null ? nameOrId : name, cachedPlayer.isOnline());
        }
        for (OfflinePlayer cached : Bukkit.getOfflinePlayers()) {
            String cachedName = cached.getName();
            if (cachedName != null && cachedName.equalsIgnoreCase(nameOrId)) {
                return new Target(cached.getUniqueId(), cachedName, false);
            }
        }
        return null;
    }

    /** Bukkit.getOfflinePlayerIfCached por reflexion (Paper 1.16.5+). */
    private OfflinePlayer cachedOfflinePlayer(String name) {
        if (!offlineLookupResolved) {
            offlineLookupResolved = true;
            try {
                offlineLookup = Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
            } catch (NoSuchMethodException notModern) {
                offlineLookup = null;
            }
        }
        if (offlineLookup == null) {
            return null;
        }
        try {
            return (OfflinePlayer) offlineLookup.invoke(null, name);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private String describeOpenVaults(UUID owner) {
        StringBuilder builder = new StringBuilder();
        VaultManager manager = plugin.getVaultManager();
        for (int number = 1; number <= plugin.getMaxAmount(); number++) {
            UUID viewer = manager.findViewer(owner, number, null);
            if (viewer == null) {
                continue;
            }
            Player online = Bukkit.getPlayer(viewer);
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('#').append(number).append(" (").append(online == null ? "?" : online.getName()).append(')');
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private void addIfStarts(List<String> suggestions, String option, String typed) {
        if (option.toLowerCase(Locale.ROOT).startsWith(typed) && !suggestions.contains(option)) {
            suggestions.add(option);
        }
    }

    /** Devuelve el numero o -1 si el texto no es un numero valido. */
    private int parseNumber(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Jugador objetivo de un subcomando de administrador. */
    private static final class Target {
        private final UUID uuid;
        private final String name;
        private final boolean online;

        private Target(UUID uuid, String name, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }
}
