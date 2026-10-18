package me.jp.jpvaults.util;

/** Como se interpretan los colores de los mensajes. */
public enum MessageFormat {

    /** Legacy con & en cualquier version; es el que entienden todos los servidores. */
    LEGACY("legacy"),
    /** MiniMessage (<red>, <gradient:...>, <click:...>); requiere Adventure. */
    MINIMESSAGE("minimessage"),
    /** Legacy, salvo que el mensaje traiga etiquetas de MiniMessage y el servidor las soporte. */
    AUTO("auto");

    private final String key;

    MessageFormat(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** Devuelve null si el texto no corresponde a ningun formato conocido. */
    public static MessageFormat fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (MessageFormat format : values()) {
            if (format.key.equals(value)) {
                return format;
            }
        }
        return null;
    }
}
