package me.jp.jpvaults.command;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.manager.MessageManager;
import me.jp.jpvaults.manager.VaultManager;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comando /pv: abre el vault del jugador y gestiona /pv reload.
 * Registrado en plugin.yml (comando + alias) y en onEnable (executor y tab).
 */
public final class PlayerVaultCommand implements CommandExecutor, TabCompleter {

    /** Permite usar /pv. Por defecto lo tiene todo el mundo. */
    public static final String PERMISSION_USE = "jpvaults.use";
    /** Permite recargar el plugin en caliente. */
    public static final String PERMISSION_RELOAD = "jpvaults.reload";

    private final JPPlayerVaults plugin;

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

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
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

        vaults.openVault(player, number);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        if (sender instanceof Player && sender.hasPermission(PERMISSION_USE)) {
            int available = plugin.getVaultManager().availableVaults((Player) sender);
            for (int number = 1; number <= available; number++) {
                String option = String.valueOf(number);
                if (option.startsWith(typed)) {
                    suggestions.add(option);
                }
            }
        }
        if (sender.hasPermission(PERMISSION_USE) && "help".startsWith(typed)) {
            suggestions.add("help");
        }
        if (sender.hasPermission(PERMISSION_RELOAD) && "reload".startsWith(typed)) {
            suggestions.add("reload");
        }
        return suggestions;
    }

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

    /** Devuelve el numero o -1 si el texto no es un numero valido. */
    private int parseNumber(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
