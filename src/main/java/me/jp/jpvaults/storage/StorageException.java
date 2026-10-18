package me.jp.jpvaults.storage;

/**
 * La persistencia no pudo leer los datos del jugador. El plugin prefiere
 * fallar a mostrar un vault vacio: si se mostrara vacio, el siguiente guardado
 * sobrescribiria los objetos reales.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
