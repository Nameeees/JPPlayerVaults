package me.jp.jpvaults.manager;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.menu.VaultHolder;
import me.jp.jpvaults.model.PlayerVaultData;
import me.jp.jpvaults.storage.VaultStorage;
import me.jp.jpvaults.util.Text;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nucleo del plugin: cache de datos por jugador, cuantos vaults otorga el
 * rango y ciclo abrir/cerrar/guardar.
 *
 * Todo el acceso ocurre en el hilo del servidor; la escritura a disco la hace
 * VaultStorage en su propio hilo.
 */
public final class VaultManager {

    /** Los rangos otorgan jpvaults.vaults.<n>; el plugin usa el numero mas alto. */
    public static final String VAULT_PERMISSION_PREFIX = "jpvaults.vaults.";

    /**
     * En 1.8-1.12 un titulo de inventario de mas de 32 caracteres rompe el
     * cliente: se recorta para que una configuracion larga no kickee a nadie.
     */
    private static final int MAX_TITLE_LENGTH = 32;

    private final JPPlayerVaults plugin;
    private final VaultStorage storage;
    private final Map<UUID, PlayerVaultData> cache = new HashMap<>();
    private final Map<UUID, VaultHolder> open = new HashMap<>();
    private boolean titleLengthWarned;
    private boolean shuttingDown;

    public VaultManager(JPPlayerVaults plugin, VaultStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Cuantos vaults puede usar el jugador segun su rango. */
    public int availableVaults(Player player) {
        for (int granted = plugin.getMaxAmount(); granted >= 1; granted--) {
            if (player.hasPermission(VAULT_PERMISSION_PREFIX + granted)) {
                return granted;
            }
        }
        return plugin.getDefaultAmount();
    }

    public boolean hasOpenVault(Player player) {
        return open.containsKey(player.getUniqueId());
    }

    /**
     * Cierra las vistas de vault que hayan sobrevivido a una sesion anterior
     * de esta clase (plugin recargado sin ejecutar onDisable). Sin esto, esa
     * ventana "zombi" seguiria mostrando los objetos y podria vaciarse otra
     * vez ademas de los que ya estan guardados en disco.
     *
     * Se compara por nombre de clase a proposito: la clase antigua vive en
     * otro classloader y un instanceof fallaria.
     */
    public void closeStaleVaultViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder != null && VaultHolder.class.getName().equals(holder.getClass().getName())) {
                player.closeInventory();
                plugin.getLogger().info("Closed a vault left open by a previous plugin session for "
                        + player.getName() + " (anti-dupe guard).");
            }
        }
    }

    /** Abre el vault indicado (el comando ya valido el rango del jugador). */
    public void openVault(Player player, int number) {
        flushOpenVault(player);
        PlayerVaultData data = data(player);
        int size = plugin.getVaultSize();
        MessageManager messages = plugin.getMessages();

        String title = messages.get("vault-title", "number", String.valueOf(number));
        String safeTitle = Text.limitVisible(title, MAX_TITLE_LENGTH);
        if (!safeTitle.equals(title) && !titleLengthWarned) {
            titleLengthWarned = true;
            plugin.getLogger().warning("vault-title is longer than " + MAX_TITLE_LENGTH
                    + " visible characters and was shortened for compatibility with 1.8-1.12 clients.");
        }

        VaultHolder holder = new VaultHolder(player.getUniqueId(), number);
        Inventory inventory = Bukkit.createInventory(holder, size, safeTitle);
        holder.setInventory(inventory);
        inventory.setContents(viewOf(data, number, size));

        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        plugin.playUiSound(player, UiSound.OPEN);
        messages.send(player, "command.opened", "number", String.valueOf(number));
    }

    /** Guarda el vault que se acaba de cerrar, esperando al disco. */
    public void handleClose(Player player, VaultHolder holder) {
        UUID id = player.getUniqueId();
        PlayerVaultData data = cache.get(id);
        if (data != null) {
            data.setVaultContents(holder.getNumber(), holder.getInventory().getContents());
            if (shuttingDown) {
                // storage.shutdown() espera a que termine la cola: no hace falta
                // bloquear el hilo del servidor 5 segundos por cada jugador.
                save(data);
            } else {
                saveNow(data);
            }
        }
        open.remove(id, holder);
    }

    /** Guarda lo que quedara abierto cuando el jugador sale del servidor. */
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        VaultHolder holder = open.remove(id);
        PlayerVaultData data = cache.remove(id);
        if (data == null) {
            return;
        }
        if (holder != null) {
            data.setVaultContents(holder.getNumber(), holder.getInventory().getContents());
        }
        if (data.isDirty()) {
            saveNow(data);
        }
    }

    /** Guardado periodico de los vaults abiertos (storage.autosave-seconds). */
    public void autosave() {
        for (VaultHolder holder : open.values()) {
            PlayerVaultData data = cache.get(holder.getOwner());
            if (data != null) {
                data.setVaultContents(holder.getNumber(), holder.getInventory().getContents());
                save(data);
            }
        }
    }

    /**
     * Apagado del plugin: cierra las vistas abiertas (para que no queden GUIs
     * zombi tras un /reload), guarda todo y espera a que terminen las
     * escrituras pendientes.
     */
    public void shutdown() {
        shuttingDown = true;
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof VaultHolder) {
                player.closeInventory(); // dispara InventoryCloseEvent -> guarda
            }
        }
        // Vistas sin evento de cierre (jugador ya desconectado, etc.).
        for (Map.Entry<UUID, VaultHolder> entry : open.entrySet()) {
            PlayerVaultData data = cache.get(entry.getKey());
            if (data != null) {
                data.setVaultContents(entry.getValue().getNumber(), entry.getValue().getInventory().getContents());
            }
        }
        open.clear();
        for (PlayerVaultData data : cache.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
        cache.clear();
        storage.shutdown();
    }

    /**
     * Guarda el vault que ya estuviera abierto antes de mostrar otro. El
     * cierre del inventario anterior casi siempre lo guarda, pero esto
     * garantiza que cambiar de vault nunca pierda cambios sin importar el
     * orden de los eventos entre versiones.
     */
    private void flushOpenVault(Player player) {
        VaultHolder previous = open.get(player.getUniqueId());
        PlayerVaultData data = cache.get(player.getUniqueId());
        if (previous != null && data != null) {
            data.setVaultContents(previous.getNumber(), previous.getInventory().getContents());
            saveNow(data);
        }
    }

    /** Datos en cache del jugador; se cargan de disco la primera vez. */
    private PlayerVaultData data(Player player) {
        UUID id = player.getUniqueId();
        PlayerVaultData data = cache.get(id);
        if (data == null) {
            data = storage.load(id);
            cache.put(id, data);
        }
        data.setLastName(player.getName()); // identifica el archivo para los administradores
        return data;
    }

    /** Copia de la vista que se mostrara (si vaults.size < 54, conserva el resto). */
    private ItemStack[] viewOf(PlayerVaultData data, int number, int size) {
        ItemStack[] stored = data.getVaultContents(number);
        ItemStack[] view = new ItemStack[size];
        System.arraycopy(stored, 0, view, 0, Math.min(size, stored.length));
        return view;
    }

    /**
     * Guarda sin bloquear el hilo del servidor (autoguardado y apagado). Los
     * datos se marcan limpios desde el hilo de escritura solo si el archivo se
     * escribio y nadie los cambio entretanto.
     */
    private void save(PlayerVaultData data) {
        storage.save(data, false);
    }

    /**
     * Guarda y espera a que el archivo este escrito. Se usa al cerrar y al
     * salir: si el servidor muere justo despues, el contenido ya esta en disco
     * y nadie puede duplicarlo reabriendo el vault.
     */
    private void saveNow(PlayerVaultData data) {
        storage.save(data, true);
    }
}
