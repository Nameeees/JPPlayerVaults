package me.jp.jpvaults.storage;

import me.jp.jpvaults.model.PlayerVaultData;

import java.util.UUID;

/**
 * Persistencia de los vaults. Implementacion por defecto: YamlVaultStorage
 * (un archivo legible por jugador dentro de la carpeta configurada).
 */
public interface VaultStorage {

    /** Carga los datos guardados del jugador (vacio si todavia no tiene). */
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

    /** Espera a que terminen las escrituras pendientes (llamar en onDisable). */
    void shutdown();
}
