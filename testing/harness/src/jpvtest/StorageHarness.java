package jpvtest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only plugin: exercises the real JPPlayerVaults code inside a running
 * server.
 *
 * Covered here:
 *  - YAML storage round-trip, deletes, corrupt files and known owners.
 *  - SQL storage (JDBC) against an in-memory H2 database, same checks, with a
 *    driver instance the harness provides (exactly what the plugin does with
 *    the driver that ships with the server).
 *  - /pv and /pv admin command paths, their permissions and their messages,
 *    through dynamic proxies of CommandSender and Player.
 *  - The real client path (open, click, close, reload from disk) is covered by
 *    testing/bot/vault-test.js.
 *
 * Not part of the release jar.
 */
public final class StorageHarness extends JavaPlugin {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
    private static final UUID BROKEN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ef");

    private int checks;
    private int failures;

    private final List<String> messages = new ArrayList<>();
    private Inventory opened;
    private UUID testId;

    @Override
    public void onEnable() {
        try {
            Plugin plugin = plugin();
            testYamlStorage(plugin);
            testSqlStorage(plugin);
            testSenderCommands(plugin);
            testPlayerCommands(plugin);
        } catch (Throwable problem) {
            failures++;
            getLogger().severe("HARNESS EXCEPTION " + problem.getClass().getName() + ": " + problem.getMessage());
            problem.printStackTrace();
        }
        getLogger().info("HARNESS RESULT checks=" + checks + " failures=" + failures);
        getLogger().info("HARNESS " + (failures == 0 ? "PASS" : "FAIL"));
        getServer().getScheduler().runTaskLater(this, Bukkit::shutdown, 40L);
    }

    // ------------------------------------------------------------ YAML backend

    private void testYamlStorage(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> pluginClass = jp.getClass();
        Class<?> storageClass = loader.loadClass("me.jp.jpvaults.storage.YamlVaultStorage");
        Class<?> dataClass = loader.loadClass("me.jp.jpvaults.model.PlayerVaultData");

        Object storage = storageClass.getConstructor(pluginClass, String.class).newInstance(jp, "harness-data");
        roundTrip("YAML", storage, storageClass, dataClass, OWNER_ID);

        File file = new File(jp.getDataFolder(), "harness-data" + File.separator + OWNER_ID + ".yml");
        check("YAML: player file written", file.isFile());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        check("YAML: file keeps the player uuid", OWNER_ID.toString().equals(yaml.getString("uuid")));

        // A file that cannot be parsed is backed up and refuses to load, so the
        // next save cannot overwrite the real items.
        File folder = new File(jp.getDataFolder(), "harness-data");
        File brokenFile = new File(folder, BROKEN_ID + ".yml");
        Files.write(brokenFile.toPath(), "{{{ this is not valid yaml".getBytes(StandardCharsets.UTF_8));
        Object loaded = null;
        try {
            loaded = storageClass.getMethod("load", UUID.class).invoke(storage, BROKEN_ID);
        } catch (InvocationTargetException wrapped) {
            check("YAML: corrupt file refuses to load",
                    wrapped.getCause().getClass().getName().endsWith("StorageException"));
        }
        if (loaded != null) {
            check("YAML: corrupt file refuses to load", false);
        }
        File[] backups = folder.listFiles((dir, name) -> name.startsWith(BROKEN_ID + ".") && name.endsWith(".bak"));
        check("YAML: corrupt file is backed up", backups != null && backups.length >= 1);

        storageClass.getMethod("shutdown").invoke(storage);
    }

    // ------------------------------------------------------------- SQL backend

    private void testSqlStorage(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> pluginClass = jp.getClass();
        Class<?> settingsClass = loader.loadClass("me.jp.jpvaults.storage.SqlSettings");
        Class<?> storageClass = loader.loadClass("me.jp.jpvaults.storage.SqlVaultStorage");
        Class<?> dataClass = loader.loadClass("me.jp.jpvaults.model.PlayerVaultData");

        Driver driver = new org.h2.Driver();
        Object settings = settingsClass
                .getConstructor(String.class, String.class, String.class, String.class, String.class, int.class, long.class)
                .newInstance("", "jdbc:h2:mem:jpvaults_harness;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", "test_", 2, 5000L);
        Object storage = storageClass.getConstructor(pluginClass, settingsClass, Driver.class)
                .newInstance(jp, settings, driver);
        check("SQL: storage starts against the JDBC driver", storage != null);

        String description = String.valueOf(storageClass.getMethod("describe").invoke(storage));
        check("SQL: describes the target", description.contains("jpvaults_harness"));

        roundTrip("SQL", storage, storageClass, dataClass, UUID.fromString("00000000-0000-0000-0000-000000000011"));
        storageClass.getMethod("shutdown").invoke(storage);
    }

    /** Same expectations for every backend: this is the storage contract. */
    private void roundTrip(String label, Object storage, Class<?> storageClass, Class<?> dataClass, UUID owner)
            throws Exception {
        Method setContents = dataClass.getMethod("setVaultContents", int.class, ItemStack[].class);
        Method getContents = dataClass.getMethod("getVaultContents", int.class);
        Method isDirty = dataClass.getMethod("isDirty");
        Method save = storageClass.getMethod("save", dataClass, boolean.class);
        Method load = storageClass.getMethod("load", UUID.class);
        Method delete = storageClass.getMethod("deleteVault", UUID.class, int.class, boolean.class);

        Object data = dataClass.getConstructor(UUID.class).newInstance(owner);
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
        check(label + ": data is clean after a successful save", !((Boolean) isDirty.invoke(data)).booleanValue());

        Object loaded = load.invoke(storage, owner);
        ItemStack[] actual = (ItemStack[]) getContents.invoke(loaded, 2);
        check(label + ": all 54 slots round-trip", Arrays.equals(expected, actual));
        check(label + ": stack amount preserved", actual[0] != null && actual[0].getAmount() == 17);
        check(label + ": unicode display name preserved", actual[7] != null && actual[7].getItemMeta() != null
                && "Libro \u00f1and\u00fa".equals(actual[7].getItemMeta().getDisplayName()));
        check(label + ": lore preserved", actual[7] != null && actual[7].getItemMeta().getLore() != null
                && "L\u00ednea uno".equals(actual[7].getItemMeta().getLore().get(0)));
        check(label + ": last slot preserved", actual[53] != null && actual[53].getType() == Material.ARROW);
        check(label + ": empty slot stays empty", actual[1] == null);

        ItemStack[] other = new ItemStack[54];
        other[0] = new ItemStack(Material.STONE, 3);
        setContents.invoke(data, 5, other);
        check(label + ": data is dirty after a change", ((Boolean) isDirty.invoke(data)).booleanValue());
        save.invoke(storage, data, true);

        Object again = load.invoke(storage, owner);
        check(label + ": vaults stay independent",
                ((ItemStack[]) getContents.invoke(again, 5))[0].getType() == Material.STONE
                        && ((ItemStack[]) getContents.invoke(again, 2))[0] != null);

        check(label + ": deleteVault reports success",
                ((Boolean) delete.invoke(storage, owner, 2, true)).booleanValue());
        Object afterDelete = load.invoke(storage, owner);
        check(label + ": deleted vault comes back empty",
                ((ItemStack[]) getContents.invoke(afterDelete, 2))[0] == null);
        check(label + ": other vault survives the delete",
                ((ItemStack[]) getContents.invoke(afterDelete, 5))[0] != null);

        Set<?> owners = (Set<?>) storageClass.getMethod("knownOwners").invoke(storage);
        check(label + ": owner is listed by knownOwners", owners.contains(owner));
    }

    // ------------------------------------------- command senders (every version)

    private void testSenderCommands(Plugin jp) throws Exception {
        PluginCommand command = Bukkit.getPluginCommand("pv");
        check("/pv command is registered", command != null);
        if (command == null) {
            return;
        }
        CommandExecutor executor = command.getExecutor();
        check("/pv has the plugin executor",
                executor != null && executor.getClass().getName().equals("me.jp.jpvaults.command.PlayerVaultCommand"));

        CommandSender admin = fakeSender("CONSOLE", "jpvaults.use", "jpvaults.reload", "jpvaults.admin");

        messages.clear();
        check("/pv admin info is handled",
                executor.onCommand(admin, command, "pv", new String[]{"admin", "info"}));
        check("/pv admin info reports the storage", strip(joined()).contains("Storage"));

        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "info", OWNER_ID.toString()});
        check("/pv admin info <uuid> reports a player", strip(joined()).contains("UUID")
                && strip(joined()).contains("Vaults with items"));

        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "delete", OWNER_ID.toString(), "1"});
        check("/pv admin delete needs the confirm word", strip(joined()).contains("Usage"));

        messages.clear();
        executor.onCommand(admin, command, "pv",
                new String[]{"admin", "delete", OWNER_ID.toString(), "1", "confirm"});
        check("/pv admin delete works from the console", strip(joined()).contains("Deleted"));

        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "open", OWNER_ID.toString(), "1"});
        check("/pv admin open is player-only", strip(joined()).contains("in game"));

        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "migrate"});
        check("/pv admin migrate explains it needs mysql", strip(joined()).contains("mysql"));

        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "nonsense"});
        check("/pv admin rejects unknown subcommands", strip(joined()).contains("nonsense"));

        messages.clear();
        check("/pv reload is handled", executor.onCommand(admin, command, "pv", new String[]{"reload"}));
        check("/pv reload confirms", strip(joined()).contains("reload"));

        CommandSender denied = fakeSender("Denied", "jpvaults.use");
        messages.clear();
        executor.onCommand(denied, command, "pv", new String[]{"admin", "info"});
        check("/pv admin requires jpvaults.admin", strip(joined()).contains("permission"));
    }

    // ------------------------------------------- player commands (modern API)

    private void testPlayerCommands(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> managerClass = loader.loadClass("me.jp.jpvaults.manager.VaultManager");
        Class<?> holderClass = loader.loadClass("me.jp.jpvaults.menu.VaultHolder");
        Object manager = jp.getClass().getMethod("getVaultManager").invoke(jp);
        Method available = managerClass.getMethod("availableVaults", Player.class);
        Method handleClose = managerClass.getMethod("handleClose", Player.class, holderClass);

        testId = PLAYER_ID;
        Player player;
        try {
            player = fakePlayer(testId, "TestPlayer", "jpvaults.use", "jpvaults.admin", "jpvaults.vaults.3");
        } catch (IllegalArgumentException unsupportedApi) {
            // El API 1.8 declara getHealth() con dos tipos de retorno incompatibles
            // en la jerarquia de Player: java.lang.reflect.Proxy no puede crearlo.
            getLogger().warning("CHECK SKIP: this server API cannot proxy Player ("
                    + unsupportedApi.getMessage() + "); player checks run on the modern server only.");
            return;
        }

        check("available vaults comes from the rank permission", ((Integer) available.invoke(manager, player)) == 3);
        check("vault size comes from config", ((Integer) jp.getClass().getMethod("getVaultSize").invoke(jp)) == 54);

        PluginCommand command = Bukkit.getPluginCommand("pv");
        CommandExecutor executor = command.getExecutor();

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

        messages.clear();
        opened = null;
        executor.onCommand(player, command, "pv", new String[]{"1"});
        check("reopened vault keeps the items", opened != null && opened.getItem(0) != null
                && opened.getItem(0).getAmount() == 9);
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

        // Admin view: open the vault of a player and block a second viewer.
        messages.clear();
        opened = null;
        executor.onCommand(player, command, "pv", new String[]{"admin", "open", testId.toString(), "1"});
        check("/pv admin open <uuid> opens the vault of that player", opened != null);
        boolean adminView = opened != null
                && ((Boolean) opened.getHolder().getClass().getMethod("isAdminView").invoke(opened.getHolder()));
        check("admin view reports itself as an admin view", adminView);
        check("admin view announces the owner", strip(joined()).contains("Opened vault"));

        Object adminHolder = opened == null ? null : opened.getHolder();
        Player second = null;
        try {
            second = fakePlayer(UUID.fromString("00000000-0000-0000-0000-0000000000ff"), "SecondAdmin",
                    "jpvaults.use", "jpvaults.admin");
        } catch (IllegalArgumentException unsupportedApi) {
            getLogger().info("CHECK SKIP: could not create a second fake player");
        }
        if (second != null && adminHolder != null) {
            messages.clear();
            executor.onCommand(second, command, "pv", new String[]{"admin", "open", testId.toString(), "1"});
            check("a second viewer is refused while the vault is open", strip(joined()).contains("open right now"));

            messages.clear();
            executor.onCommand(second, command, "pv",
                    new String[]{"admin", "delete", testId.toString(), "1", "confirm"});
            check("deleting an open vault is refused", strip(joined()).contains("could not be deleted"));
        }

        if (adminHolder != null) {
            handleClose.invoke(manager, player, adminHolder);
        }

        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"admin", "info", testId.toString()});
        check("admin info counts the stored vaults", strip(joined()).contains("Vaults with items")
                && strip(joined()).contains("1"));

        CommandSender admin = fakeSender("CONSOLE", "jpvaults.admin");
        messages.clear();
        executor.onCommand(admin, command, "pv",
                new String[]{"admin", "delete", testId.toString(), "1", "confirm"});
        check("admin delete removes the vault", strip(joined()).contains("Deleted"));
        messages.clear();
        executor.onCommand(admin, command, "pv", new String[]{"admin", "info", testId.toString()});
        check("the deleted vault no longer has items", strip(joined()).contains("Vaults with items: 0"));
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
                if ("hasPermission".equals(methodName) || "isPermissionSet".equals(methodName)) {
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

    private CommandSender fakeSender(String name, String... permissions) {
        Set<String> allowed = new HashSet<>(Arrays.asList(permissions));
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String methodName = method.getName();
                if ("getName".equals(methodName)) {
                    return name;
                }
                if ("hasPermission".equals(methodName) || "isPermissionSet".equals(methodName)) {
                    return allowed.contains(String.valueOf(args[0]));
                }
                if ("sendMessage".equals(methodName)) {
                    if (args != null && args.length == 1 && args[0] instanceof String) {
                        messages.add((String) args[0]);
                    }
                    return null;
                }
                if ("getServer".equals(methodName)) {
                    return Bukkit.getServer();
                }
                if ("isOp".equals(methodName)) {
                    return !allowed.isEmpty();
                }
                if ("toString".equals(methodName)) {
                    return name;
                }
                if ("hashCode".equals(methodName)) {
                    return name.hashCode();
                }
                if ("equals".equals(methodName)) {
                    return proxy == args[0];
                }
                return defaultValue(method.getReturnType());
            }
        };
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, handler);
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

    private void check(String name, boolean condition) {
        checks++;
        if (condition) {
            getLogger().info("CHECK OK: " + name);
        } else {
            failures++;
            getLogger().warning("CHECK FAILED: " + name);
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
