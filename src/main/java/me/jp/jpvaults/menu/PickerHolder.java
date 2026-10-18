package me.jp.jpvaults.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * El menu que se abre con {@code /pv} sin argumentos: un icono por vault
 * disponible. No guarda datos de items, solo que jugador lo abrio y cuantos
 * vaults se listaron, para traducir el slot pulsado al numero de vault.
 */
public final class PickerHolder implements InventoryHolder {

    private final UUID owner;
    private final int available;
    private Inventory inventory;

    public PickerHolder(UUID owner, int available) {
        this.owner = owner;
        this.available = available;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Cuantos vaults se listaron (los iconos ocupan los slots 0..available-1). */
    public int getAvailable() {
        return available;
    }

    /** Numero de vault que corresponde a un slot, o -1 si ahi no hay icono. */
    public int vaultAt(int slot) {
        return slot >= 0 && slot < available ? slot + 1 : -1;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
