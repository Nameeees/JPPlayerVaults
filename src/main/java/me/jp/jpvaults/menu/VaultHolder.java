package me.jp.jpvaults.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Identifica un inventario de vault abierto: de quien es, que numero es y si
 * lo esta viendo un administrador. Asi los listeners distinguen los cofres del
 * plugin de cualquier otro inventario, sin mirar el titulo ni materiales.
 */
public final class VaultHolder implements InventoryHolder {

    private final UUID owner;
    private final int number;
    private final boolean adminView;
    private Inventory inventory; // se asigna justo despues de crear el inventario

    public VaultHolder(UUID owner, int number) {
        this(owner, number, false);
    }

    public VaultHolder(UUID owner, int number, boolean adminView) {
        this.owner = owner;
        this.number = number;
        this.adminView = adminView;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getNumber() {
        return number;
    }

    /** True si lo abrio un administrador con /pv admin open. */
    public boolean isAdminView() {
        return adminView;
    }

    /** El inventario real; se usa para leer el contenido al cerrar el vault. */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
