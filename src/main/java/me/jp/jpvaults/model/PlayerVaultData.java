package me.jp.jpvaults.model;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Datos en memoria de los vaults de un jugador.
 *
 * Cada vault guarda SIEMPRE {@link #SIZE} slots aunque la vista configurada
 * sea mas pequena (vaults.size): asi bajar el tamano temporalmente nunca
 * borra objetos, solo los oculta hasta volver a subirlo.
 *
 * El contador {@link #revision} permite que el guardado asincrono marque los
 * datos como limpios solo si nadie los ha vuelto a tocar mientras el archivo
 * se estaba escribiendo; un fallo de disco no pierde el "hay cambios sin
 * guardar" que necesita el guardado al salir.
 */
public final class PlayerVaultData {

    /** 54 slots = 6 filas: el cofre mas grande que Minecraft permite. */
    public static final int SIZE = 54;

    private final UUID owner;
    private final Map<Integer, ItemStack[]> vaults = new LinkedHashMap<>();
    private String lastName = "";
    private volatile boolean dirty;
    private volatile long revision;

    public PlayerVaultData(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Ultimo nombre conocido, para que los administradores identifiquen el archivo. */
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        String value = lastName == null ? "" : lastName;
        if (!value.equals(this.lastName)) {
            this.lastName = value;
            touch();
        }
    }

    /** True si hay cambios sin escribir a disco. */
    public boolean isDirty() {
        return dirty;
    }

    /** Numero de cambios desde que se creo el objeto. */
    public long getRevision() {
        return revision;
    }

    public void markClean() {
        this.dirty = false;
    }

    /**
     * Marca limpio solo si no hubo cambios desde la revision indicada. Lo llama
     * el hilo de escritura al terminar con exito, para no perder cambios que
     * hayan llegado mientras se guardaba.
     */
    public void markCleanIfRevision(long expectedRevision) {
        if (this.revision == expectedRevision) {
            this.dirty = false;
        }
    }

    /** Contenido completo del vault (nunca null; se crea vacio la primera vez). */
    public ItemStack[] getVaultContents(int number) {
        ItemStack[] contents = vaults.get(number);
        if (contents == null) {
            contents = new ItemStack[SIZE];
            vaults.put(number, contents);
        }
        return contents;
    }

    /** Guarda lo que se ve en el inventario; las filas ocultas se conservan. */
    public void setVaultContents(int number, ItemStack[] view) {
        ItemStack[] contents = getVaultContents(number);
        int limit = Math.min(view.length, contents.length);
        for (int slot = 0; slot < limit; slot++) {
            contents[slot] = view[slot];
        }
        touch();
    }

    /** Todos los vaults con datos (lo recorre el guardado). */
    public Map<Integer, ItemStack[]> getVaults() {
        return vaults;
    }

    private void touch() {
        this.revision++;
        this.dirty = true;
    }
}
