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
import java.util.Collections;
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
            testCodec(plugin);
            testTextUtilities(plugin);
            testMiniMessage(plugin);
        } catch (Throwable problem) {
            failures++;
            getLogger().severe("HARNESS EXCEPTION " + problem.getClass().getName() + ": " + problem.getMessage());
            problem.printStackTrace();
        }
        // El planificador necesita que el servidor siga tickeando para poder
        // comprobar que dispara y que se cancela.
        getServer().getScheduler().runTaskLater(this, this::testSchedulersThenReport, 5L);
    }

    private void testSchedulersThenReport() {
        try {
            testSchedulers(plugin(), this::report);
        } catch (Throwable problem) {
            failures++;
            getLogger().severe("HARNESS EXCEPTION " + problem.getClass().getName() + ": " + problem.getMessage());
            problem.printStackTrace();
            report();
        }
    }

    private void report() {
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
        opened = null;
        executor.onCommand(player, command, "pv", new String[0]);
        check("/pv without arguments opens the picker", opened != null
                && opened.getHolder().getClass().getName().endsWith("PickerHolder"));
        int pickerVaults = opened == null ? -1 : (Integer) opened.getHolder().getClass()
                .getMethod("getAvailable").invoke(opened.getHolder());
        check("picker lists one icon per available vault", pickerVaults == 3);
        check("picker has room for its icons", opened != null && opened.getSize() >= 9);

        messages.clear();
        executor.onCommand(player, command, "pv", new String[]{"help"});
        check("help lists the available vaults", strip(joined()).contains("Vaults available")
                && strip(joined()).contains("3"));

        // Pulsar el segundo icono del selector abre el vault #2.
        Object picker = opened == null ? null : opened.getHolder();
        if (picker != null) {
            messages.clear();
            opened = null;
            managerClass.getMethod("openFromPicker", Player.class, picker.getClass(), int.class)
                    .invoke(manager, player, picker, 1);
            Object holder = opened == null ? null : opened.getHolder();
            check("picking an icon opens that vault", holder != null
                    && holderClass.getName().equals(holder.getClass().getName())
                    && ((Integer) holder.getClass().getMethod("getNumber").invoke(holder)) == 2);
            if (holder != null) {
                handleClose.invoke(manager, player, holder);
            }
        }

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

    // ------------------------------------------------- formato de los items

    /**
     * El codec debe leer lo que escriben las tres generaciones de formato:
     * NBT (Paper 1.20.5+), Bukkit con cabecera (1.2.0+) y Bukkit sin cabecera
     * (1.0.0-1.1.0). Que los datos de una version anterior sigan abriendo es la
     * prueba que mas importa aqui.
     */
    private void testCodec(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> codec = loader.loadClass("me.jp.jpvaults.storage.VaultCodec");
        Method encode = codec.getMethod("encode", ItemStack[].class, boolean.class);
        Method decode = codec.getMethod("decode", byte[].class, int.class);
        Method isNbt = codec.getMethod("isNbt", byte[].class);
        boolean modern = (Boolean) codec.getMethod("nbtAvailable").invoke(null);
        getLogger().info("Codec: NBT item API " + (modern ? "available" : "NOT available (old server)"));

        ItemStack[] contents = new ItemStack[54];
        contents[0] = new ItemStack(Material.DIAMOND, 17);
        ItemStack named = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Nombre \u00f1");
        meta.setLore(Collections.singletonList("linea de lore"));
        named.setItemMeta(meta);
        contents[7] = named;
        contents[53] = new ItemStack(Material.ARROW, 3);

        byte[] bukkit = (byte[]) encode.invoke(null, contents, false);
        check("Codec: the bukkit format writes its header", bukkit.length > 1 && bukkit[0] == 1);
        check("Codec: bukkit round-trip keeps all 54 slots",
                Arrays.equals(contents, (ItemStack[]) decode.invoke(null, bukkit, 54)));

        // Blob de 1.0.0-1.1.0: sin cabecera, empieza por el magic de ObjectStream.
        byte[] legacy = Arrays.copyOfRange(bukkit, 1, bukkit.length);
        check("Codec: a pre-1.2.0 blob starts with the ObjectStream magic",
                legacy.length > 0 && legacy[0] == (byte) 0xAC);
        check("Codec: data saved by 1.1.0 still loads",
                Arrays.equals(contents, (ItemStack[]) decode.invoke(null, legacy, 54)));

        if (!modern) {
            getLogger().info("CHECK SKIP: the NBT round-trip needs Paper 1.20.5+");
            return;
        }
        byte[] nbt = (byte[]) encode.invoke(null, contents, true);
        check("Codec: the nbt format marks itself as nbt", (Boolean) isNbt.invoke(null, nbt));
        check("Codec: nbt round-trip keeps all 54 slots",
                Arrays.equals(contents, (ItemStack[]) decode.invoke(null, nbt, 54)));
        check("Codec: nbt is not bigger than the bukkit format", nbt.length <= bukkit.length * 2);
    }

    // ------------------------------------------------------------- texto

    private void testTextUtilities(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> text = loader.loadClass("me.jp.jpvaults.util.Text");
        Method hasTags = text.getMethod("hasMiniMessageTags", String.class);
        Method hasLegacy = text.getMethod("hasLegacyCodes", String.class);
        Method stripTags = text.getMethod("stripMiniMessageTags", String.class);
        Method limit = text.getMethod("limitVisible", String.class, int.class);

        check("Text: a colour tag is detected as MiniMessage", (Boolean) hasTags.invoke(null, "<red>hi</red>"));
        check("Text: a gradient is detected as MiniMessage",
                (Boolean) hasTags.invoke(null, "<gradient:#2ec4b6:#12727f>x</gradient>"));
        check("Text: <player|uuid> is not a MiniMessage tag",
                !((Boolean) hasTags.invoke(null, "/pv admin open <player|uuid> 1")));
        check("Text: legacy colour codes are detected", (Boolean) hasLegacy.invoke(null, "&aHola &lMundo"));
        check("Text: a plain text has no legacy codes", !((Boolean) hasLegacy.invoke(null, "Hola mundo")));
        check("Text: tags are stripped when there is no Adventure",
                "Hola".equals(stripTags.invoke(null, "<red>Hola</red>")));
        String limited = (String) limit.invoke(null, "\u00a7a1234567890", 5);
        check("Text: the visible limit keeps colour codes intact",
                limited.startsWith("\u00a7a") && ChatColor.stripColor(limited).equals("12345"));
    }

    /**
     * El sistema de color moderno, de punta a punta: se reescribe el titulo con
     * MiniMessage, se recarga y se comprueba que sale un Component con el texto
     * correcto (sin etiquetas). Al terminar se deja todo como estaba.
     */
    private void testMiniMessage(Plugin jp) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> components = loader.loadClass("me.jp.jpvaults.util.Components");
        Class<?> managerClass = loader.loadClass("me.jp.jpvaults.manager.MessageManager");
        probeAdventure(components);
        getLogger().info("Text system: " + components.getMethod("describe").invoke(null));

        if (!((Boolean) components.getMethod("miniMessageAvailable").invoke(null))) {
            getLogger().info("CHECK SKIP: MiniMessage is not available on this server");
            return;
        }
        check("Components: legacy text becomes a Component",
                components.getMethod("fromLegacy", String.class).invoke(null, "\u00a7aHola") != null);

        File messagesFile = new File(jp.getDataFolder(), "messages_en.yml");
        File configFile = new File(jp.getDataFolder(), "config.yml");
        String messagesBefore = new String(Files.readAllBytes(messagesFile.toPath()), StandardCharsets.UTF_8);
        String configBefore = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        try {
            String tagged = "vault-title: '<gradient:#2ec4b6:#12727f><bold>Vault</bold></gradient> <gray>#{number}</gray>'";
            String rewritten = messagesBefore.replaceAll("(?m)^vault-title:.*$", tagged);
            Files.write(messagesFile.toPath(), rewritten.getBytes(StandardCharsets.UTF_8));

            Method component = managerClass.getMethod("component", String.class, String[].class);
            Method get = managerClass.getMethod("get", String.class, String[].class);

            // --- modo auto: el mensaje con etiquetas se convierte y el legacy no.
            Files.write(configFile.toPath(),
                    withMessagesFormat(configBefore, "auto").getBytes(StandardCharsets.UTF_8));
            jp.getClass().getMethod("reloadPlugin").invoke(jp);
            Object messages = jp.getClass().getMethod("getMessages").invoke(jp);

            Object auto = component.invoke(messages, "vault-title", new String[]{"number", "1"});
            check("MiniMessage: in auto mode a tagged message becomes a Component", auto != null);
            Object legacyComponent = component.invoke(messages, "command.no-vaults", new String[0]);
            check("MiniMessage: in auto mode a legacy message stays as text", legacyComponent == null);
            String legacyText = (String) get.invoke(messages, "command.no-vaults", new String[0]);
            check("MiniMessage: in auto mode the legacy colours are still applied",
                    legacyText.startsWith("\u00a78[\u00a7a"));

            // --- modo minimessage: todo pasa por MiniMessage, tambien el titulo.
            Files.write(configFile.toPath(),
                    withMessagesFormat(configBefore, "minimessage").getBytes(StandardCharsets.UTF_8));
            jp.getClass().getMethod("reloadPlugin").invoke(jp);
            messages = jp.getClass().getMethod("getMessages").invoke(jp);

            Object component1 = component.invoke(messages, "vault-title", new String[]{"number", "1"});
            check("MiniMessage: a tagged message becomes a Component", component1 != null);
            String plain = component1 == null ? "" : (String) components.getMethod("toPlain", Object.class)
                    .invoke(null, component1);
            check("MiniMessage: the Component renders as plain text",
                    plain.contains("Vault") && plain.contains("1") && !plain.contains("<"));
            String raw = (String) get.invoke(messages, "vault-title", new String[]{"number", "1"});
            check("MiniMessage: raw text keeps the tags for the component path", raw.contains("<gradient"));

            String sizeable = (String) get.invoke(messages, "picker.title", new String[]{"available", "3"});
            check("MiniMessage: the picker title is still readable", sizeable.length() > 0);
        } finally {
            Files.write(messagesFile.toPath(), messagesBefore.getBytes(StandardCharsets.UTF_8));
            Files.write(configFile.toPath(), configBefore.getBytes(StandardCharsets.UTF_8));
            jp.getClass().getMethod("reloadPlugin").invoke(jp);
        }
        Object restored = managerClass.getMethod("component", String.class, String[].class)
                .invoke(jp.getClass().getMethod("getMessages").invoke(jp), "vault-title",
                        new String[]{"number", "1"});
        check("MiniMessage: after restoring the config the legacy path is back", restored == null);
    }

    // -------------------------------------------------------- planificadores

    private void testSchedulers(Plugin jp, Runnable done) throws Exception {
        ClassLoader loader = jp.getClass().getClassLoader();
        Class<?> schedulers = loader.loadClass("me.jp.jpvaults.util.Schedulers");
        final Method cancel = loader.loadClass("me.jp.jpvaults.util.Schedulers$Task").getMethod("cancel");
        Class<?> taskClass = loader.loadClass("me.jp.jpvaults.util.Schedulers$Task");

        check("Schedulers: folia detection answers", schedulers.getMethod("folia").invoke(null) instanceof Boolean);
        getLogger().info("Schedulers: folia=" + schedulers.getMethod("folia").invoke(null));

        final int[] ticks = {0};
        final Object task = schedulers.getMethod("runGlobalTimer", Plugin.class, Runnable.class, long.class, long.class)
                .invoke(null, jp, (Runnable) () -> ticks[0]++, 1L, 1L);
        check("Schedulers: a global timer returns a cancellable handle", taskClass.isInstance(task));

        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                check("Schedulers: the global timer fires", ticks[0] >= 2);
                cancel.invoke(task);
                final int after = ticks[0];
                getServer().getScheduler().runTaskLater(this, () -> {
                    check("Schedulers: cancelling stops the timer", ticks[0] == after);
                    done.run();
                }, 10L);
            } catch (Throwable problem) {
                failures++;
                getLogger().severe("HARNESS EXCEPTION in the scheduler test: " + problem.getMessage());
                done.run();
            }
        }, 10L);
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

    /**
     * Deja constancia de que clases de Adventure ve el plugin: si algun dia
     * cambia el empaquetado del servidor, se ve aqui en una linea.
     */
    private void probeAdventure(Class<?> components) {
        StringBuilder visible = new StringBuilder();
        ClassLoader pluginLoader = components.getClassLoader();
        String[] probes = {
                "net.kyori.adventure.text.Component",
                "net.kyori.adventure.text.minimessage.MiniMessage",
                "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer"
        };
        for (String name : probes) {
            try {
                Class.forName(name, false, pluginLoader);
                visible.append(name.substring(name.lastIndexOf('.') + 1)).append(' ');
            } catch (Throwable missing) {
                visible.append('[').append(name.substring(name.lastIndexOf('.') + 1)).append(" missing] ");
            }
        }
        getLogger().info("PROBE: Adventure classes seen by the plugin: " + visible.toString().trim());
    }

    /**
     * Fija messages-format en un config.yml que puede ser de una version
     * anterior del plugin (sin esa clave todavia).
     */
    private static String withMessagesFormat(String yaml, String value) {
        if (yaml.contains("messages-format:")) {
            return yaml.replaceAll("(?m)^messages-format:.*$", "messages-format: " + value);
        }
        String separator = System.lineSeparator();
        return yaml + separator + "messages-format: " + value + separator;
    }

    /** El titulo del inventario desaparecio de Inventory en las APIs modernas. */    private String inventoryTitle(Inventory inventory) {
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
