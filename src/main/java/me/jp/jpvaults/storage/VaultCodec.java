package me.jp.jpvaults.storage;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Convierte el contenido de un vault a bytes y viceversa.
 *
 * Dos formatos, y cada blob dice cual usa en su primer byte:
 *
 *  - {@link #FORMAT_BUKKIT} (1): flujos de Bukkit sobre la serializacion de
 *    ItemStack. Funciona en cualquier version y es lo que usaban las versiones
 *    anteriores del plugin, asi que los datos ya guardados se siguen leyendo
 *    (los blobs antiguos no llevan cabecera y se detectan igual).
 *  - {@link #FORMAT_NBT} (2): NBT de Minecraft a traves de
 *    {@code ItemStack.serializeItemsAsBytes}, que solo existe en Paper 1.20.5+.
 *    El propio javadoc de Paper lo recomienda: "NBT is safer for data
 *    migrations as it will use the built-in data converter instead of bukkits
 *    dangerous serialization system". Ademas guarda el orden y los huecos por
 *    diseno, que es justo lo que necesita un vault.
 *
 * Con {@code item-format: auto} se usa NBT cuando el servidor lo ofrece y
 * Bukkit cuando no. Un blob NBT solo lo puede leer un servidor con esa API, por
 * eso el formato se puede forzar a {@code bukkit} antes de mover el mundo de
 * Paper a Spigot.
 */
public final class VaultCodec {

    /** Cabecera de los blobs nuevos: 1 = flujos de Bukkit, 2 = NBT. */
    public static final byte FORMAT_BUKKIT = 1;
    public static final byte FORMAT_NBT = 2;

    /** Primer byte de un ObjectOutputStream de Java (0xAC 0xED): blobs antiguos. */
    private static final byte LEGACY_MAGIC = (byte) 0xAC;

    /** Limite defensivo: un vault nunca deberia tener mas slots que esto. */
    private static final int MAX_SLOTS = 4096;

    private static boolean nbtChecked;
    private static Method serializeItems;
    private static Method deserializeItems;

    private VaultCodec() {
    }

    /** True si este servidor puede guardar items como NBT (Paper 1.20.5+). */
    public static boolean nbtAvailable() {
        if (!nbtChecked) {
            nbtChecked = true;
            try {
                serializeItems = ItemStack.class.getMethod("serializeItemsAsBytes", ItemStack[].class);
                deserializeItems = ItemStack.class.getMethod("deserializeItemsFromBytes", byte[].class);
            } catch (NoSuchMethodException notModern) {
                serializeItems = null;
                deserializeItems = null;
            }
        }
        return serializeItems != null && deserializeItems != null;
    }

    /** Nombre del formato que se usaria con estas preferencias. */
    public static String preferredFormatName(boolean preferNbt) {
        return preferNbt && nbtAvailable() ? "nbt" : "bukkit";
    }

    public static byte[] encode(ItemStack[] contents, boolean preferNbt) throws IOException {
        if (preferNbt && nbtAvailable()) {
            try {
                byte[] payload = (byte[]) serializeItems.invoke(null, (Object) contents);
                return withHeader(FORMAT_NBT, payload);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // Si el NBT falla en un item concreto, no se pierde nada: se
                // guarda con el formato de siempre.
                throw new IOException("Could not encode the vault as NBT: " + failure.getMessage(), failure);
            }
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(256, contents.length * 64));
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(buffer)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
        }
        return withHeader(FORMAT_BUKKIT, buffer.toByteArray());
    }

    public static ItemStack[] decode(byte[] data, int size) throws IOException {
        if (data == null || data.length == 0) {
            return new ItemStack[size];
        }
        byte format = data[0];
        if (format == FORMAT_NBT) {
            return decodeNbt(data, size);
        }
        if (format == FORMAT_BUKKIT) {
            return decodeBukkit(data, 1, size);
        }
        if (format == LEGACY_MAGIC) {
            // Blob de una version anterior del plugin, sin cabecera.
            return decodeBukkit(data, 0, size);
        }
        throw new IOException("Unknown vault storage format: " + format);
    }

    /** True si el blob esta en el formato NBT (lo usa la migracion y los informes). */
    public static boolean isNbt(byte[] data) {
        return data != null && data.length > 0 && data[0] == FORMAT_NBT;
    }

    private static byte[] withHeader(byte format, byte[] payload) {
        byte[] result = new byte[payload.length + 1];
        result[0] = format;
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    private static ItemStack[] decodeNbt(byte[] data, int size) throws IOException {
        if (!nbtAvailable()) {
            throw new IOException("This vault was stored as NBT and this server has no NBT item API "
                    + "(Paper 1.20.5+ is required). Set storage.item-format: bukkit on a Paper server and "
                    + "re-save, or use a server with the API.");
        }
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);
        try {
            ItemStack[] decoded = (ItemStack[]) deserializeItems.invoke(null, (Object) payload);
            return fit(decoded, size);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IOException("Could not decode an NBT vault: " + failure.getMessage(), failure);
        }
    }

    private static ItemStack[] decodeBukkit(byte[] data, int offset, int size) throws IOException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(data, offset, data.length - offset))) {
            int length = in.readInt();
            if (length < 0 || length > MAX_SLOTS) {
                throw new IOException("Invalid slot count in the stored vault: " + length);
            }
            ItemStack[] contents = new ItemStack[size];
            for (int slot = 0; slot < length; slot++) {
                Object value = in.readObject();
                if (slot < size && value instanceof ItemStack) {
                    contents[slot] = (ItemStack) value;
                }
            }
            return contents;
        } catch (ClassNotFoundException unknown) {
            throw new IOException("Unknown class while reading a stored vault", unknown);
        }
    }

    /**
     * Ajusta el contenido leido al tamaño configurado del vault. El NBT guarda
     * los huecos como items vacios (AIR con cantidad 0), asi que se normalizan
     * a null para que el resto del plugin vea lo mismo que con el formato de
     * Bukkit.
     */
    private static ItemStack[] fit(ItemStack[] decoded, int size) {
        ItemStack[] contents = new ItemStack[size];
        if (decoded == null) {
            return contents;
        }
        int limit = Math.min(decoded.length, size);
        for (int slot = 0; slot < limit; slot++) {
            ItemStack item = decoded[slot];
            if (item == null || item.getAmount() <= 0 || item.getType() == Material.AIR) {
                continue;
            }
            contents[slot] = item;
        }
        return contents;
    }
}
