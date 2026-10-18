package me.jp.jpvaults.storage;

import me.jp.jpvaults.model.PlayerVaultData;

import java.util.Set;
import java.util.UUID;

/**
 * Persistencia de los vaults. Implementaciones: {@link YamlVaultStorage}
 * (un archivo legible por jugador) y {@link SqlVaultStorage} (base de datos por
 * JDBC). Cual de las dos se usa lo decide {@code storage.type} en config.yml.
 */
public interface VaultStorage {

    /**
     * Carga los datos guardados del jugador (vacio si todavia no tiene).
     *
     * @throws StorageException si los datos existen pero no se pueden leer: el
     *         plugin prefiere no abrir el vault antes que mostrar uno vacio y
     *         que el siguiente guardado borre los objetos reales.
     */
    PlayerVaultData load(UUID owner);

    /**
     * Toma una foto de los datos y los escribe en su propio hilo.
     * Con {@code waitForDisk} la llamada espera a que la escritura termine:
     * se usa al cerrar o salir, cuando una muerte del servidor justo despues
     * se convertiria en un duplicado al reabrir el vault.
     *
     * La implementacion marca los datos como limpios solo despues de que la
     * escritura haya tenido exito y solo si no cambiaron mientras tanto.
     */
    void save(PlayerVaultData data, boolean waitForDisk);

    /**
     * Borra un vault concreto del jugador (lo usa {@code /pv admin delete}).
     * Con {@code waitForDisk} devuelve si el borrado llego a disco.
     */
    boolean deleteVault(UUID owner, int vault, boolean waitForDisk);

    /** Jugadores con datos guardados; lo usan la migracion y {@code /pv admin info}. */
    Set<UUID> knownOwners();

    /** Descripcion del destino de los datos, sin credenciales, para logs y mensajes. */
    String describe();

    /** Espera a que terminen las escrituras pendientes (llamar en onDisable). */
    void shutdown();
}
