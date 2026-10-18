package me.jp.jpvaults.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colores validos en todas las versiones: codigos legacy con & siempre y,
 * ademas, &#RRGGBB (visible a partir de 1.16; las versiones anteriores lo
 * muestran como texto, por eso messages.yml avisa).
 *
 * En servidores con Adventure (Paper y forks modernos) los mensajes pueden
 * usar ademas MiniMessage (etiquetas como la de color rojo, hex, degradados,
 * click y hover). El parseo real lo hace {@link Components}; aqui solo se
 * detecta si un texto parece MiniMessage.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final char COLOR_CHAR = '\u00A7';

    /**
     * Tags que MiniMessage reconoce de verdad. Deliberadamente estricto: asi
     * textos como los placeholders de la ayuda (jugador, numero) no se
     * confunden con etiquetas de color; y si alguna se colara, el parseo
     * fallaria y se usaria el texto legacy, sin romper nada.
     */
    private static final Pattern MINI_TAG = Pattern.compile(
            "</?(?:[a-z_]+(?::[^<>{}\\s]+)?|#[0-9a-fA-F]{6})>");

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

    /** True si el texto parece usar etiquetas de MiniMessage. */
    public static boolean hasMiniMessageTags(String text) {
        return text != null && MINI_TAG.matcher(text).find();
    }

    /**
     * True si el texto trae codigos legacy (&a, &l, &#RRGGBB). Se usa para no
     * confundir un mensaje legacy con uno de MiniMessage: en 'auto' un texto
     * con & se interpreta siempre como legacy, y asi un placeholder como
     * {@code <number>} en la ayuda no puede romper el color.
     */
    public static boolean hasLegacyCodes(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != '&') {
                continue;
            }
            char next = Character.toLowerCase(text.charAt(index + 1));
            if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')
                    || (next >= 'k' && next <= 'o') || next == 'r' || next == '#') {
                return true;
            }
        }
        return false;
    }

    /**
     * Quita las etiquetas de MiniMessage: se usa cuando el servidor no tiene
     * Adventure y el administrador configuro MiniMessage igualmente, para que
     * el jugador lea el texto sin etiquetas raras en vez de nada.
     */
    public static String stripMiniMessageTags(String text) {
        if (text == null) {
            return "";
        }
        return MINI_TAG.matcher(text).replaceAll("");
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
