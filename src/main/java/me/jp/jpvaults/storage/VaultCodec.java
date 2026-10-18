package me.jp.jpvaults.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Convierte el contenido de un vault a bytes y viceversa con los flujos de
 * Bukkit. Es el mismo formato de serializacion que usa el guardado YAML
 * (Bukkit convierte los materiales renombrados en 1.13+), pero compacto para
 * guardarlo en una columna BLOB.
 */
public final class VaultCodec {

    /** Limite defensivo: un vault nunca deberia tener mas slots que esto. */
    private static final int MAX_SLOTS = 4096;

    private VaultCodec() {
    }

    public static byte[] encode(ItemStack[] contents) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(256, contents.length * 64));
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(buffer)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
        }
        return buffer.toByteArray();
    }

    public static ItemStack[] decode(byte[] data, int size) throws IOException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
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
}
