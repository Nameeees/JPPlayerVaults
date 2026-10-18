package me.jp.jpvaults.manager;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.menu.VaultHolder;
import me.jp.jpvaults.model.PlayerVaultData;
import me.jp.jpvaults.storage.VaultStorage;
import me.jp.jpvaults.util.Text;
import me.jp.jpvaults.util.UiSound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * Todo el acceso ocurre en el hilo del servidor; la escritura la hace
 * VaultStorage en su propio hilo.
 *
 * Un mismo vault solo puede estar abierto por una persona a la vez: si un
 * administrador lo abre con /pv admin open, el dueño no puede tenerlo abierto
 * al mismo tiempo (ni al reves), y el guardado siempre va a los datos del
 * dueño, no a los de quien lo esta mirando.
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
    /** Quien esta viendo que vault: uuid del que mira -> vault abierto. */
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

    /** Almacenamiento activo (YAML o SQL), para informes y migraciones. */
    public VaultStorage getStorage() {
        return storage;
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

    /** Abre el vault propio del jugador (el comando ya valido su rango). */
    public void openVault(Player player, int number) {
        openVaultFor(player, player.getUniqueId(), player.getName(), number, false);
    }

    /**
     * Abre el vault de un dueño concreto para quien lo esta mirando. Con
     * {@code adminView} el guardado sigue yendo a los datos del dueño.
     */
    public void openVaultFor(Player viewer, UUID owner, String ownerName, int number, boolean adminView) {
        if (!adminView) {
            flushOpenVault(viewer);
        }
        PlayerVaultData data = data(owner, ownerName);
        int size = plugin.getVaultSize();
        MessageManager messages = plugin.getMessages();

        String title = messages.get(adminView ? "admin.vault-title" : "vault-title",
                "number", String.valueOf(number), "player", ownerName == null ? "" : ownerName);
        String safeTitle = Text.limitVisible(title, MAX_TITLE_LENGTH);
        if (!safeTitle.equals(title) && !titleLengthWarned) {
            titleLengthWarned = true;
            plugin.getLogger().warning("The inventory title is longer than " + MAX_TITLE_LENGTH
                    + " visible characters and was shortened for compatibility with 1.8-1.12 clients.");
        }

        VaultHolder holder = new VaultHolder(owner, number, adminView);
        Inventory inventory = Bukkit.createInventory(holder, size, safeTitle);
        holder.setInventory(inventory);
        inventory.setContents(viewOf(data, number, size));

        open.put(viewer.getUniqueId(), holder);
        viewer.openInventory(inventory);
        plugin.playUiSound(viewer, UiSound.OPEN);
        if (!adminView) {
            // Las vistas de administrador tienen su propio mensaje en el comando.
            messages.send(viewer, "command.opened", "number", String.valueOf(number));
        }
    }

    /** Guarda el vault que se acaba de cerrar, esperando al disco. */
    public void handleClose(Player viewer, VaultHolder holder) {
        PlayerVaultData data = cache.get(holder.getOwner());
        if (data != null) {
            data.setVaultContents(holder.getNumber(), holder.getInventory().getContents());
            saveOrWait(data);
        }
        open.remove(viewer.getUniqueId(), holder);
        if (holder.isAdminView()) {
            releaseIfIdle(holder.getOwner());
        }
    }

    /** Guarda lo que quedara abierto cuando el jugador sale del servidor. */
    public void handleQuit(Player player) {
        UUID viewerId = player.getUniqueId();
        VaultHolder holder = open.remove(viewerId);
        if (holder != null) {
            PlayerVaultData viewed = cache.get(holder.getOwner());
            if (viewed != null) {
                viewed.setVaultContents(holder.getNumber(), holder.getInventory().getContents());
                if (viewed.isDirty()) {
                    saveOrWait(viewed);
                }
            }
            if (holder.isAdminView()) {
                releaseIfIdle(holder.getOwner());
            }
        }
        PlayerVaultData own = cache.remove(viewerId);
        if (own != null && own.isDirty()) {
            saveOrWait(own);
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
        for (UUID viewerId : new ArrayList<>(open.keySet())) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof VaultHolder) {
                player.closeInventory(); // dispara InventoryCloseEvent -> guarda
            }
        }
        // Vistas sin evento de cierre (jugador ya desconectado, etc.).
        for (Map.Entry<UUID, VaultHolder> entry : open.entrySet()) {
            PlayerVaultData data = cache.get(entry.getValue().getOwner());
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

    // ------------------------------------------------------------ admin tools

    /** True si ese vault concreto esta abierto por alguien. */
    public boolean isVaultOpen(UUID owner, int number) {
        return findViewer(owner, number, null) != null;
    }

    /** Quien tiene abierto ese vault, o null. */
    public UUID findViewer(UUID owner, int number, UUID exceptViewer) {
        for (Map.Entry<UUID, VaultHolder> entry : open.entrySet()) {
            VaultHolder holder = entry.getValue();
            if (holder.getOwner().equals(owner) && holder.getNumber() == number
                    && (exceptViewer == null || !entry.getKey().equals(exceptViewer))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Borra el contenido de un vault. Se niega si alguien lo tiene abierto,
     * para que su cierre no lo vuelva a escribir.
     */
    public boolean deleteVault(UUID owner, int number) {
        if (isVaultOpen(owner, number)) {
            return false;
        }
        PlayerVaultData data = cache.get(owner);
        if (data != null) {
            data.getVaults().remove(number); // al no estar en el mapa, deja de escribirse
        }
        return storage.deleteVault(owner, number, true);
    }

    /** Cuantos vaults del jugador tienen algun objeto. */
    public int vaultsWithData(UUID owner) {
        PlayerVaultData data = data(owner, null);
        int used = 0;
        for (ItemStack[] contents : data.getVaults().values()) {
            if (countItems(contents) > 0) {
                used++;
            }
        }
        return used;
    }

    /** Numero total de objetos guardados en los vaults del jugador. */
    public int countItems(UUID owner) {
        PlayerVaultData data = data(owner, null);
        int total = 0;
        for (ItemStack[] contents : data.getVaults().values()) {
            total += countItems(contents);
        }
        return total;
    }

    /** Ultimo nombre conocido del jugador (cadena vacia si no hay datos). */
    public String lastKnownName(UUID owner) {
        return data(owner, null).getLastName();
    }

    /**
     * Suelta los datos del jugador de la cache si nadie los tiene abiertos,
     * guardando antes si habia cambios. Lo usan las vistas de administrador.
     */
    public void releaseIfIdle(UUID owner) {
        if (isOwnerOpen(owner)) {
            return;
        }
        PlayerVaultData data = cache.get(owner);
        if (data == null) {
            return;
        }
        if (data.isDirty()) {
            saveOrWait(data);
        }
        cache.remove(owner);
    }

    /** True si algun visor tiene abierto un vault de ese dueño. */
    public boolean isOwnerOpen(UUID owner) {
        for (VaultHolder holder : open.values()) {
            if (holder.getOwner().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- internals

    /**
     * Guarda el vault que ya estuviera abierto antes de mostrar otro. El
     * cierre del inventario anterior casi siempre lo guarda, pero esto
     * garantiza que cambiar de vault nunca pierda cambios sin importar el
     * orden de los eventos entre versiones.
     */
    private void flushOpenVault(Player viewer) {
        VaultHolder previous = open.get(viewer.getUniqueId());
        PlayerVaultData data = cache.get(viewer.getUniqueId());
        if (previous != null && data != null) {
            data.setVaultContents(previous.getNumber(), previous.getInventory().getContents());
            saveOrWait(data);
        }
    }

    /** Datos en cache del jugador; se cargan del disco la primera vez. */
    private PlayerVaultData data(UUID owner, String knownName) {
        PlayerVaultData data = cache.get(owner);
        if (data == null) {
            data = storage.load(owner);
            cache.put(owner, data);
        }
        if (knownName != null && !knownName.isEmpty()) {
            data.setLastName(knownName); // identifica el archivo para los administradores
        }
        return data;
    }

    /** Copia de la vista que se mostrara (si vaults.size < 54, conserva el resto). */
    private ItemStack[] viewOf(PlayerVaultData data, int number, int size) {
        ItemStack[] stored = data.getVaultContents(number);
        ItemStack[] view = new ItemStack[size];
        System.arraycopy(stored, 0, view, 0, Math.min(size, stored.length));
        return view;
    }

    private int countItems(ItemStack[] contents) {
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                total += item.getAmount();
            }
        }
        return total;
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
     * Guarda y espera a que los datos esten escritos. Se usa al cerrar y al
     * salir: si el servidor muere justo despues, el contenido ya esta guardado
     * y nadie puede duplicarlo reabriendo el vault.
     */
    private void saveNow(PlayerVaultData data) {
        storage.save(data, true);
    }

    /** Durante el apagado la cola de escritura se vacia al final (storage.shutdown). */
    private void saveOrWait(PlayerVaultData data) {
        if (shuttingDown) {
            save(data);
        } else {
            saveNow(data);
        }
    }
}
