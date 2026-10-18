package me.jp.jpvaults.util;

import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Puente opcional con Adventure (el sistema de texto de las versiones modernas).
 *
 * Todo se resuelve por reflexion y una sola vez: si el servidor no trae Adventure
 * (1.8 - 1.15, o Spigot sin Paper) {@link #available()} devuelve false y el
 * plugin sigue funcionando con los colores legacy de siempre. Si lo trae, los
 * mensajes pueden usar MiniMessage y los titulos de inventario se crean con
 * Component, que es la via que las versiones nuevas esperan.
 */
public final class Components {

    private static boolean initialised;
    private static boolean available;
    private static boolean miniMessageAvailable;

    private static Class<?> componentClass;
    private static Object miniMessage;
    private static Method miniDeserialize;
    private static Object legacySerializer;
    private static Method legacyDeserialize;
    private static Method legacySerialize;
    private static Object plainSerializer;
    private static Method plainSerialize;
    private static Method audienceSendMessage;
    private static Method createInventoryWithComponent;
    private static Method metaDisplayName;
    private static Method metaLore;

    private Components() {
    }

    /** True si el servidor expone Adventure (Paper y forks modernos). */
    public static boolean available() {
        init();
        return available;
    }

    /** True si ademas esta MiniMessage (<red>, <gradient:...>, <click:...>). */
    public static boolean miniMessageAvailable() {
        init();
        return miniMessageAvailable;
    }

    /** Component a partir de texto MiniMessage, o null si no se puede. */
    public static Object fromMiniMessage(String text) {
        init();
        if (!miniMessageAvailable || miniMessage == null || miniDeserialize == null || text == null) {
            return null;
        }
        try {
            Class<?>[] parameters = miniDeserialize.getParameterTypes();
            Object[] arguments;
            if (parameters.length == 1) {
                arguments = new Object[]{text};
            } else {
                // deserialize(String, TagResolver...): sin resolvers extra.
                arguments = new Object[]{text, Array.newInstance(parameters[1].getComponentType(), 0)};
            }
            return miniDeserialize.invoke(miniMessage, arguments);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /**
     * MiniMessage no siempre expone deserialize(String): en Adventure 5 la
     * firma es varargs (String, TagResolver...) y getMethod no la encuentra
     * pidiendo solo String. Se busca a mano, prefiriendo la de un argumento.
     */
    private static Method findMiniDeserialize(Class<?> miniClass) {
        Method varargs = null;
        for (Method method : miniClass.getMethods()) {
            if (!"deserialize".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == String.class) {
                return method;
            }
            if (parameters.length == 2 && parameters[0] == String.class && parameters[1].isArray()) {
                varargs = method;
            }
        }
        return varargs;
    }

    /** Component a partir de texto con colores legacy (& y §), o null. */
    public static Object fromLegacy(String text) {
        init();
        if (!available || legacySerializer == null || legacyDeserialize == null || text == null) {
            return null;
        }
        try {
            return legacyDeserialize.invoke(legacySerializer, text);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /** Texto plano de un Component (para medir el titulo, por ejemplo). */
    public static String toPlain(Object component) {
        init();
        if (!available || plainSerializer == null || plainSerialize == null || component == null) {
            return "";
        }
        try {
            return String.valueOf(plainSerialize.invoke(plainSerializer, component));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return "";
        }
    }

    /** Envia el Component a quien sea que lo soporte; false si hay que usar texto. */
    public static boolean send(CommandSender to, Object component) {
        init();
        if (!available || component == null) {
            return false;
        }
        try {
            audienceSendMessage.invoke(to, component);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    /** Inventario con titulo Component; null si el servidor no lo soporta. */
    public static Inventory createInventory(InventoryHolder holder, int size, Object component) {
        init();
        if (createInventoryWithComponent == null || component == null) {
            return null;
        }
        try {
            return (Inventory) createInventoryWithComponent.invoke(null, holder, size, component);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /** Pone el nombre del item como Component; false si no se puede. */
    public static boolean applyDisplayName(ItemMeta meta, Object component) {
        init();
        if (metaDisplayName == null || component == null) {
            return false;
        }
        try {
            metaDisplayName.invoke(meta, component);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    /** Pone el lore del item como Component; false si no se puede. */
    public static boolean applyLore(ItemMeta meta, List<Object> components) {
        init();
        if (metaLore == null || components == null || components.isEmpty()) {
            return false;
        }
        try {
            metaLore.invoke(meta, components);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    /** Nombre del sistema de texto detectado, para logs e informes. */
    public static String describe() {
        init();
        if (!available) {
            return "legacy only";
        }
        return miniMessageAvailable ? "Adventure + MiniMessage" : "Adventure";
    }

    private static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> audienceClass = Class.forName("net.kyori.adventure.audience.Audience");
            audienceSendMessage = audienceClass.getMethod("sendMessage", componentClass);
            available = true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            available = false;
            return;
        }

        // Legacy (& y §) <-> Component. Ojo: los metodos se resuelven desde los
        // INTERFACES publicas, nunca desde la clase de implementacion (no es
        // publica y invocarla da IllegalAccessException). Y legacySection()
        // devuelve el serializador ya construido en Adventure 5, mientras que en
        // Adventure 4 devolvia un Builder: se contemplan los dos casos.
        try {
            Class<?> legacyClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            legacyDeserialize = legacyClass.getMethod("deserialize", String.class);
            legacySerialize = legacyClass.getMethod("serialize", componentClass);
            legacySerializer = finishBuilder(legacyClass.getMethod("legacySection").invoke(null),
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer$Builder");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            legacySerializer = null;
            legacyDeserialize = null;
            legacySerialize = null;
        }

        try {
            Class<?> plainClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            plainSerialize = plainClass.getMethod("serialize", componentClass);
            plainSerializer = finishBuilder(plainClass.getMethod("plainText").invoke(null),
                    "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer$Builder");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plainSerializer = null;
            plainSerialize = null;
        }

        try {
            Class<?> miniClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            miniDeserialize = findMiniDeserialize(miniClass);
            miniMessage = miniClass.getMethod("miniMessage").invoke(null);
            miniMessageAvailable = miniDeserialize != null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            miniMessage = null;
            miniDeserialize = null;
            miniMessageAvailable = false;
        }

        try {
            createInventoryWithComponent = Class.forName("org.bukkit.Bukkit")
                    .getMethod("createInventory", InventoryHolder.class, int.class, componentClass);
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            createInventoryWithComponent = null;
        }

        try {
            metaDisplayName = ItemMeta.class.getMethod("displayName", componentClass);
        } catch (NoSuchMethodException missing) {
            metaDisplayName = null;
        }

        try {
            metaLore = ItemMeta.class.getMethod("lore", List.class);
        } catch (NoSuchMethodException missing) {
            metaLore = null;
        }
    }

    /**
     * En Adventure 4 las fabricas devolvian un Builder y habia que llamar a
     * build(); en Adventure 5 devuelven el serializador ya listo. Si el objeto
     * es un Builder se construye, y si no se usa tal cual.
     */
    private static Object finishBuilder(Object candidate, String builderClassName) throws ReflectiveOperationException {
        if (candidate == null) {
            return null;
        }
        Class<?> builderClass;
        try {
            builderClass = Class.forName(builderClassName);
        } catch (ClassNotFoundException oldAdventure) {
            return candidate;
        }
        if (!builderClass.isInstance(candidate)) {
            return candidate;
        }
        return builderClass.getMethod("build").invoke(candidate);
    }

    /** Item clonado con el nombre aplicado, o el mismo si no se pudo. */
    public static ItemStack withDisplayName(ItemStack item, Object component) {
        if (item == null || component == null) {
            return item;
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null && applyDisplayName(meta, component)) {
            copy.setItemMeta(meta);
        }
        return copy;
    }
}
