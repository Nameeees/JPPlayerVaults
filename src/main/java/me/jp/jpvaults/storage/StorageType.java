package me.jp.jpvaults.storage;

/** Donde se guardan los vaults. */
public enum StorageType {

    /** Un archivo YAML por jugador dentro de la carpeta del plugin. */
    YAML("yaml"),
    /** Base de datos por JDBC (MySQL o compatible). */
    MYSQL("mysql");

    private final String key;

    StorageType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** Devuelve null si el texto no corresponde a ningun tipo conocido. */
    public static StorageType fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (StorageType type : values()) {
            if (type.key.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
