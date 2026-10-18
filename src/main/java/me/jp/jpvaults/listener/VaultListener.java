package me.jp.jpvaults.listener;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.menu.PickerHolder;
import me.jp.jpvaults.menu.VaultHolder;
import me.jp.jpvaults.util.Schedulers;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Ciclo de vida y proteccion antidupe del vault abierto.
 *
 * El inventario se comporta como un cofre normal (shift-click, teclas de
 * hotbar, arrastrar...), pero sin vias para perder o duplicar objetos:
 *  - Cerrar la vista guarda en disco antes de soltarla.
 *  - Q / Ctrl+Q no tira nada al suelo desde el vault.
 *  - Los clics fuera de la ventana no sueltan el objeto del cursor.
 *  - F con objeto en el cursor (desincronizacion conocida de 1.16.x) se
 *    cancela; el intercambio de manos de 1.9+ se cancela en onEnable.
 *
 * No se cancela PlayerDropItemEvent de forma global: al cerrar la ventana con
 * el inventario del jugador lleno, Minecraft tira el objeto del cursor al
 * suelo para no perderlo; cancelar ese drop lo borraria. Las teclas Q del
 * GUI ya se bloquean en onClick, que es donde se pueden duplicar objetos.
 */
public final class VaultListener implements Listener {

    private final JPPlayerVaults plugin;
    private Method clickMethod; // getClickType() o getClick(), segun la version
    private boolean clickMethodResolved;

    public VaultListener(JPPlayerVaults plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder();

        // Menu selector: cualquier clic se cancela y el icono pulsado abre su vault.
        if (topHolder instanceof PickerHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
                return;
            }
            final PickerHolder picker = (PickerHolder) topHolder;
            plugin.playUiSound(player, UiSound.SUCCESS);
            // Un tick despues: abrir un inventario dentro del propio evento de
            // clic es fragil en algunas versiones.
            Schedulers.runLaterForEntity(plugin, player,
                    () -> plugin.getVaultManager().openFromPicker(player, picker, slot));
            return;
        }

        if (!(topHolder instanceof VaultHolder)) {
            return;
        }

        // Clic en el hueco oscuro fuera de la ventana: en un cofre vanilla
        // tiraria el objeto del cursor al suelo; aqui se bloquea para no perderlo.
        if (event.getClickedInventory() == null) {
            if (holdsItem(event.getCursor())) {
                event.setCancelled(true);
                plugin.playUiSound(player, UiSound.DENY);
            }
            return;
        }

        // Q / Ctrl+Q: mientras el vault esta abierto no se tira nada al suelo.
        if (isDropKey(event.getAction())) {
            event.setCancelled(true);
            plugin.playUiSound(player, UiSound.DENY);
            return;
        }

        // Tecla F (1.16+): con un objeto en el cursor es la combinacion de los
        // exploits de desincronizacion clasicos; con la mano vacia se permite.
        if (holdsItem(event.getCursor()) && isSwapOffhand(event)) {
            event.setCancelled(true);
            plugin.playUiSound(player, UiSound.DENY);
        }
    }

    /** Las cuatro variantes de "tirar con Q" que existen desde 1.8. */
    private boolean isDropKey(InventoryAction action) {
        return action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR;
    }

    /**
     * El tipo de clic se lee por reflexion porque el metodo cambio de nombre
     * entre versiones: getClick() en la API clasica y getClickType() despues.
     * Si una version no expone ninguno de los dos, simplemente no se filtra.
     */
    private boolean isSwapOffhand(InventoryClickEvent event) {
        if (!clickMethodResolved) {
            clickMethodResolved = true;
            for (String name : new String[]{"getClickType", "getClick"}) {
                try {
                    clickMethod = InventoryClickEvent.class.getMethod(name);
                    break;
                } catch (NoSuchMethodException otherVersion) {
                    // Probamos el otro nombre.
                }
            }
        }
        if (clickMethod == null) {
            return false;
        }
        try {
            return "SWAP_OFFHAND".equals(String.valueOf(clickMethod.invoke(event)));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof VaultHolder) {
            plugin.getVaultManager().handleClose((Player) event.getPlayer(), (VaultHolder) top.getHolder());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getVaultManager().handleQuit(event.getPlayer());
    }

    private boolean holdsItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }
}
