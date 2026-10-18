package jpvtest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only plugin: exercises the real JPPlayerVaults code inside a running
 * server (storage round-trip, command flow, permissions, language switch,
 * corrupt-file backup) and then shuts the server down.
 *
 * Not part of the release jar.
 */
public final class StorageHarness extends JavaPlugin {

    private int checks;
    private int failures;

    private final List<String> messages = new ArrayList<>();
    private Inventory opened;
    private UUID testId;

    @Override
    public void onEnable() {
        try {
            testStorage();
            testCommands();
            testCorruptBackup();
        } catch (Throwable problem) {
            failures++;
            getLogger().severe("HARNESS EXCEPTION " + problem.getClass().getName() + ": " + problem.getMessage());
            problem.printStackTrace();
        }
        getLogger().info("HARNESS RESULT checks=" + checks + " failures=" + failures);
        getLogger().info("HARNESS " + (failures == 0 ? "PASS" : "FAIL"));
        getServer().getScheduler().runTaskLater(this, Bukkit::shutdown, 40L);
    }

    // ---------------------------------------------------------------- storage

    private void testStorage() throws Exception {
        Plugin jp = plugin();
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> pluginClass = jp.getClass();
        Class<?> storageClass = loader.loadClass("me.jp.jpvaults.storage.YamlVaultStorage");
        Class<?> dataClass = loader.loadClass("me.jp.jpvaults.model.PlayerVaultData");

        Object storage = storageClass.getConstructor(pluginClass, String.class).newInstance(jp, "harness-data");
        UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        Object data = dataClass.getConstructor(UUID.class).newInstance(owner);

        Method setContents = dataClass.getMethod("setVaultContents", int.class, ItemStack[].class);
        Method getContents = dataClass.getMethod("getVaultContents", int.class);
        Method isDirty = dataClass.getMethod("isDirty");
        Method save = storageClass.getMethod("save", dataClass, boolean.class);
        Method load = storageClass.getMethod("load", UUID.class);

        ItemStack[] expected = new ItemStack[54];
        expected[0] = new ItemStack(Material.DIAMOND, 17);
        ItemStack book = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName("Libro \u00f1and\u00fa");
        meta.setLore(Arrays.asList("L\u00ednea uno", "L\u00ednea dos"));
        book.setItemMeta(meta);
        expected[7] = book;
        expected[53] = new ItemStack(Material.ARROW, 64);

        setContents.invoke(data, 2, expected);
        save.invoke(storage, data, true);
        check("data is clean after a successful save", !((Boolean) isDirty.invoke(data)).booleanValue());

        Object loaded = load.invoke(storage, owner);
        ItemStack[] actual = (ItemStack[]) getContents.invoke(loaded, 2);
        check("all 54 slots round-trip", Arrays.equals(expected, actual));
        check("stack amount preserved", actual[0] != null && actual[0].getAmount() == 17);
        check("unicode display name preserved", actual[7] != null && actual[7].getItemMeta() != null
                && "Libro \u00f1and\u00fa".equals(actual[7].getItemMeta().getDisplayName()));
        check("lore preserved", actual[7] != null && actual[7].getItemMeta().getLore() != null
                && "L\u00ednea uno".equals(actual[7].getItemMeta().getLore().get(0)));
        check("last slot preserved", actual[53] != null && actual[53].getType() == Material.ARROW);
        check("empty slot stays empty", actual[1] == null);

        expected[0] = null;
        expected[7] = null;
        expected[53] = null;
        setContents.invoke(data, 2, expected);
        check("data is dirty after a change", ((Boolean) isDirty.invoke(data)).booleanValue());
        save.invoke(storage, data, true);

        Object reloaded = load.invoke(storage, owner);
        ItemStack[] cleared = (ItemStack[]) getContents.invoke(reloaded, 2);
        check("removed slots disappear after save", cleared[0] == null && cleared[53] == null);

        ItemStack[] other = new ItemStack[54];
        other[0] = new ItemStack(Material.STONE, 3);
        setContents.invoke(data, 5, other);
        save.invoke(storage, data, true);
        Object again = load.invoke(storage, owner);
        check("vaults stay independent", ((ItemStack[]) getContents.invoke(again, 5))[0].getType() == Material.STONE
                && ((ItemStack[]) getContents.invoke(again, 2))[0] == null);

        File file = new File(jp.getDataFolder(), "harness-data" + File.separator + owner + ".yml");
        check("player file written", file.isFile());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        check("file keeps the player uuid", owner.toString().equals(yaml.getString("uuid")));

        storageClass.getMethod("shutdown").invoke(storage);
    }

    // --------------------------------------------------------------- commands

    private void testCommands() throws Exception {
        Plugin jp = plugin();
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> managerClass = loader.loadClass("me.jp.jpvaults.manager.VaultManager");
        Class<?> holderClass = loader.loadClass("me.jp.jpvaults.menu.VaultHolder");
        Object manager = jp.getClass().getMethod("getVaultManager").invoke(jp);
        Method available = managerClass.getMethod("availableVaults", Player.class);
        Method handleClose = managerClass.getMethod("handleClose", Player.class, holderClass);

        testId = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        Player player;
        try {
            player = fakePlayer(testId, "TestPlayer", "jpvaults.use", "jpvaults.vaults.3");
        } catch (IllegalArgumentException unsupportedApi) {
            // El API 1.8 declara getHealth() con dos tipos de retorno incompatibles
            // en la jerarquia de Player: java.lang.reflect.Proxy no puede crearlo.
            getLogger().warning("CHECK SKIP: this server API cannot proxy Player ("
                    + unsupportedApi.getMessage() + "); command checks run on the modern server only.");
            return;
        }

        check("available vaults comes from the rank permission", ((Integer) available.invoke(manager, player)) == 3);
        check("vault size comes from config", ((Integer) jp.getClass().getMethod("getVaultSize").invoke(jp)) == 54);

        PluginCommand command = Bukkit.getPluginCommand("pv");
        check("/pv command is registered", command != null);
        if (command == null) {
            return;
        }
        CommandExecutor executor = command.getExecutor();
        check("/pv has the plugin executor",
                executor != null && executor.getClass().getName().equals("me.jp.jpvaults.command.PlayerVaultCommand"));

        messages.clear();
        check("/pv 4 is handled", executor.onCommand(player, command, "pv", new String[]{"4"}));
        check("/pv 4 is locked for a 3-vault rank", strip(last()).contains("locked")
                && strip(last()).contains("4") && strip(last()).contains("3"));

        messages.clear();
        opened = null;
        executor.onCommand(player, command, "pv", new String[]{"1"});
        check("/pv 1 opens an inventory", opened != null);
        check("inventory has 54 slots", opened != null && opened.getSize() == 54);
        String title = inventoryTitle(opened);
        if (title.isEmpty()) {
            getLogger().info("CHECK SKIP: inventory title is not exposed by this API version");
        } else {
            check("inventory title shows vault #1", ChatColor.stripColor(title).contains("#1"));
        }
        check("inventory holder is VaultHolder", opened != null
                && holderClass.getName().equals(opened.getHolder().getClass().getName()));

        Inventory inventory = opened;
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 9));
        ItemStack emerald = new ItemStack(Material.EMERALD, 2);
        ItemMeta emeraldMeta = emerald.getItemMeta();
        emeraldMeta.setDisplayName("Esmeralda \u00f1");
        emerald.setItemMeta(emeraldMeta);
        inventory.setItem(53, emerald);
        handleClose.invoke(manager, player, inventory.getHolder());

        File savedFile = new File(jp.getDataFolder(), "data" + File.separator + testId + ".yml");
        check("closing a vault writes the player file", savedFile.isFile());
        YamlConfiguration savedYaml = YamlConfiguration.loadConfiguration(savedFile);
        ItemStack savedDiamond = savedYaml.getItemStack("vaults.1.items.0");
        check("closed vault content is on disk", savedDiamond != null && savedDiamond.getAmount() == 9);
        check("closed vault keeps item meta", savedYaml.getItemStack("vaults.1.items.53") != null
                && "Esmeralda \u00f1".equals(savedYaml.getItemStack("vaults.1.items.53").getItemMeta().getDisplayName()));

        messages.clear();
        opened = null;
        executor.onCommand(player, command, "pv", new String[]{"1"});
        check("reopened vault keeps the items", opened != null && opened.getItem(0) != null
                && opened.getItem(0).getAmount() == 9);
        check("reopened vault keeps the item meta", opened != null && opened.getItem(53) != null
                && "Esmeralda \u00f1".equals(opened.getItem(53).getItemMeta().getDisplayName()));
        handleClose.invoke(manager, player, opened.getHolder());

        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"abc"});
        check("non-numeric argument is rejected", strip(last()).contains("abc"));

        messages.clear();
        executor.onCommand(player, command, "pv", new String[0]);
        check("help lists the available vaults", strip(joined()).contains("Vaults available")
                && strip(joined()).contains("3"));

        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"reload"});
        check("reload is denied without jpvaults.reload", strip(last()).contains("permission"));

        // Long title must be cut to 32 visible characters for 1.8-1.12 clients.
        File messagesFile = new File(jp.getDataFolder(), "messages_en.yml");
        YamlConfiguration messagesYaml = YamlConfiguration.loadConfiguration(messagesFile);
        messagesYaml.set("vault-title", "&aA deliberately long vault title that old clients cannot handle");
        messagesYaml.save(messagesFile);
        jp.getClass().getMethod("reloadPlugin").invoke(jp);
        messages.clear();
        opened = null;
        executor.onCommand(player, command, "pv", new String[]{"1"});
        String longTitle = inventoryTitle(opened);
        if (!longTitle.isEmpty()) {
            check("long inventory title is shortened to 32 chars",
                    ChatColor.stripColor(longTitle).length() <= 32);
        } else {
            // Sin API publica para leer el titulo, se comprueba la funcion que
            // usa el manager (Text.limitVisible) con el texto configurado.
            Object messageManager = jp.getClass().getMethod("getMessages").invoke(jp);
            String rawTitle = (String) messageManager.getClass()
                    .getMethod("get", String.class, String[].class)
                    .invoke(messageManager, "vault-title", new String[]{"number", "1"});
            Class<?> textClass = loader.loadClass("me.jp.jpvaults.util.Text");
            String limited = (String) textClass.getMethod("limitVisible", String.class, int.class)
                    .invoke(null, rawTitle, 32);
            check("long vault title is shortened to 32 chars",
                    ChatColor.stripColor(limited).length() <= 32);
        }
        handleClose.invoke(manager, player, opened.getHolder());

        // Regenerate the packaged English file and switch languages through config.yml.
        messagesFile.delete();
        jp.getClass().getMethod("reloadPlugin").invoke(jp);
        check("deleted messages file is regenerated", messagesFile.isFile());

        File configFile = new File(jp.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set("language", "es");
        config.save(configFile);
        jp.getClass().getMethod("reloadPlugin").invoke(jp);
        Object messageManager = jp.getClass().getMethod("getMessages").invoke(jp);
        String language = (String) messageManager.getClass().getMethod("getLanguage").invoke(messageManager);
        check("language switches to Spanish", "es".equals(language));
        check("Spanish messages file generated", new File(jp.getDataFolder(), "messages_es.yml").isFile());
        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"4"});
        check("Spanish locked message is used", strip(last()).contains("desbloqueado"));

        config.set("language", "en");
        config.save(configFile);
        jp.getClass().getMethod("reloadPlugin").invoke(jp);
        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"4"});
        check("English messages come back", strip(last()).contains("locked"));
    }

    // ---------------------------------------------------------- corrupt data

    private void testCorruptBackup() throws Exception {
        Plugin jp = plugin();
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> pluginClass = jp.getClass();
        Class<?> storageClass = loader.loadClass("me.jp.jpvaults.storage.YamlVaultStorage");

        File folder = new File(jp.getDataFolder(), "harness-data");
        UUID broken = UUID.fromString("00000000-0000-0000-0000-0000000000ef");
        File brokenFile = new File(folder, broken + ".yml");
        Files.write(brokenFile.toPath(), "{{{ this is not valid yaml".getBytes(StandardCharsets.UTF_8));

        Object storage = storageClass.getConstructor(pluginClass, String.class).newInstance(jp, "harness-data");
        Object loaded = storageClass.getMethod("load", UUID.class).invoke(storage, broken);
        check("corrupt data file does not throw", loaded != null);

        File[] backups = folder.listFiles((dir, name) -> name.startsWith(broken + ".") && name.endsWith(".bak"));
        check("corrupt data file is backed up", backups != null && backups.length >= 1);
        storageClass.getMethod("shutdown").invoke(storage);
    }

    // ----------------------------------------------------------------- helpers

    private Plugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("JPPlayerVaults");
        check("JPPlayerVaults is loaded", plugin != null);
        check("JPPlayerVaults is enabled", plugin != null && plugin.isEnabled());
        if (plugin == null) {
            throw new IllegalStateException("JPPlayerVaults is not loaded");
        }
        return plugin;
    }

    private Player fakePlayer(UUID id, String name, String... permissions) {
        Set<String> allowed = new HashSet<>(Arrays.asList(permissions));
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String methodName = method.getName();
                if ("getUniqueId".equals(methodName)) {
                    return id;
                }
                if ("getName".equals(methodName)) {
                    return name;
                }
                if ("hasPermission".equals(methodName)) {
                    return allowed.contains(String.valueOf(args[0]));
                }
                if ("isPermissionSet".equals(methodName)) {
                    return allowed.contains(String.valueOf(args[0]));
                }
                if ("sendMessage".equals(methodName)) {
                    if (args != null && args.length == 1 && args[0] instanceof String) {
                        messages.add((String) args[0]);
                    }
                    return null;
                }
                if ("openInventory".equals(methodName)) {
                    opened = (Inventory) args[0];
                    return null;
                }
                if ("getLocation".equals(methodName)) {
                    return null;
                }
                if ("getServer".equals(methodName)) {
                    return Bukkit.getServer();
                }
                if ("isOnline".equals(methodName)) {
                    return Boolean.TRUE;
                }
                if ("toString".equals(methodName)) {
                    return name;
                }
                if ("hashCode".equals(methodName)) {
                    return id.hashCode();
                }
                if ("equals".equals(methodName)) {
                    return proxy == args[0];
                }
                return defaultValue(method.getReturnType());
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    private void check(String name, boolean condition) {
        checks++;
        if (condition) {
            getLogger().info("CHECK OK: " + name);
        } else {
            failures++;
            getLogger().warning("CHECK FAILED: " + name);
        }
    }

    /** El titulo del inventario desaparecio de Inventory en las APIs modernas. */
    private String inventoryTitle(Inventory inventory) {
        if (inventory == null) {
            return "";
        }
        try {
            Object value = inventory.getClass().getMethod("getTitle").invoke(inventory);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException notExposed) {
            return "";
        }
    }

    private String last() {
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
    }
    private String joined() {
        StringBuilder builder = new StringBuilder();
        for (String message : messages) {
            builder.append(message).append('\n');
        }
        return builder.toString();
    }

    private String strip(String text) {
        return ChatColor.stripColor(text);
    }
}
