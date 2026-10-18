package me.jp.jpvaults.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colores validos en todas las versiones: codigos legacy con & siempre y,
 * ademas, &#RRGGBB (visible a partir de 1.16; las versiones anteriores lo
 * muestran como texto, por eso messages.yml avisa).
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final char COLOR_CHAR = '\u00A7';

    private Text() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', hexToLegacy(text));
    }

    /** True si el texto no tiene contenido visible (vacio o solo colores/espacios). */
    public static boolean isBlank(String text) {
        return text == null || ChatColor.stripColor(text).trim().isEmpty();
    }

    /**
     * Recorta un texto ya coloreado a un maximo de caracteres visibles sin
     * partir los codigos de color. Se usa en el titulo del inventario, que en
     * 1.8-1.12 rompe el cliente si supera 32 caracteres.
     */
    public static String limitVisible(String text, int maxVisible) {
        if (text == null || maxVisible < 0) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        int visible = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == COLOR_CHAR && index + 1 < text.length()) {
                result.append(current).append(text.charAt(++index));
                continue;
            }
            if (visible >= maxVisible) {
                break;
            }
            result.append(current);
            visible++;
        }
        return result.toString();
    }

    /** Convierte &#RRGGBB en la secuencia §x§R§R§G§G§B§B que entienden 1.16+. */
    private static String hexToLegacy(String text) {
        if (text.indexOf("&#") < 0) {
            return text;
        }
        Matcher matcher = HEX.matcher(text);
        StringBuffer result = new StringBuffer(text.length());
        while (matcher.find()) {
            StringBuilder legacy = new StringBuilder(14).append(COLOR_CHAR).append('x');
            for (char digit : matcher.group(1).toCharArray()) {
                legacy.append(COLOR_CHAR).append(digit);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
