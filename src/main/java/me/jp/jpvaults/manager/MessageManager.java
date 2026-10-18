package me.jp.jpvaults.manager;

import me.jp.jpvaults.JPPlayerVaults;
import me.jp.jpvaults.util.Components;
import me.jp.jpvaults.util.MessageFormat;
import me.jp.jpvaults.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Todos los textos del plugin viven en messages_<idioma>.yml (nada hardcodeado
 * en el codigo). Aqui se reemplazan los placeholders {asi}, se colorea y se
 * envia. Un mensaje vacio no se envia.
 *
 * Dos caminos de color:
 *  - Legacy con & y &#RRGGBB, que funciona en cualquier version.
 *  - MiniMessage en servidores con Adventure (Paper y forks modernos), con
 *    degradados, hex, click y hover. Se elige con messages-format en config.yml
 *    (auto por defecto: se usa MiniMessage solo si el mensaje trae etiquetas y
 *    el servidor las soporta).
 *
 * El idioma se elige en config.yml (language). El archivo del idioma pedido se
 * genera en la carpeta del plugin a partir del recurso incluido en el jar; si
 * falta una clave, se usa el texto ingles del propio jar para que una
 * traduccion incompleta no deje huecos.
 */
public final class MessageManager {

    /** Idioma de reserva: siempre viene incluido en el jar. */
    public static final String DEFAULT_LANGUAGE = "en";

    /** Version del formato de messages_<idioma>.yml que entiende esta build. */
    private static final int CONFIG_VERSION = 1;

    /** Solo letras, numeros, guion y guion bajo: evita rutas como ../config. */
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z0-9_-]{2,16}");

    private final JPPlayerVaults plugin;
    private final File folder;
    private final Set<String> missingKeysWarned = new HashSet<>();

    private FileConfiguration config;
    private FileConfiguration fallback;
    private String language = DEFAULT_LANGUAGE;
    private MessageFormat messageFormat = MessageFormat.AUTO;
    private boolean warnedMiniMessage;
    private boolean warnedNoAdventure;

    public MessageManager(JPPlayerVaults plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder();
    }

    /**
     * Carga (o recarga) el idioma y el formato de color pedidos. Nunca lanza:
     * si el archivo no se puede leer se usan los textos incluidos en el jar.
     */
    public void load(String requestedLanguage, MessageFormat requestedFormat) {
        String normalized = normalize(requestedLanguage);
        if (requestedLanguage != null && !requestedLanguage.trim().equalsIgnoreCase(normalized)) {
            plugin.getLogger().warning("Invalid language '" + requestedLanguage + "' in config.yml; using '"
                    + normalized + "'.");
        }
        this.language = normalized;
        this.messageFormat = requestedFormat == null ? MessageFormat.AUTO : requestedFormat;
        this.warnedMiniMessage = false;
        this.warnedNoAdventure = false;
        this.fallback = loadBundled(DEFAULT_LANGUAGE);
        this.missingKeysWarned.clear();
        this.config = loadConfigured(normalized);
        warnAboutMissingKeys();
    }

    /** Idioma activo, ya validado. */
    public String getLanguage() {
        return language;
    }

    /** Formato de color configurado. */
    public MessageFormat getMessageFormat() {
        return messageFormat;
    }

    /** Resumen del sistema de texto, para /pv admin info. */
    public String describeText() {
        return messageFormat.getKey() + " (" + Components.describe() + ")";
    }

    /** Texto coloreado con los reemplazos aplicados; "" si la clave no existe. */
    public String get(String key, String... replacements) {
        String raw = raw(key);
        if (raw == null) {
            return "";
        }
        String text = applyPlaceholders(raw, replacements);
        if (usesMiniMessage(raw)) {
            if (!Components.miniMessageAvailable()) {
                warnNoAdventureOnce();
                return Text.color(Text.stripMiniMessageTags(text));
            }
            // En MiniMessage el texto va tal cual: las etiquetas son el color.
            return text;
        }
        return Text.color(text);
    }

    /**
     * Component listo para enviar, o null si este mensaje va por el camino
     * legacy (formato legacy, sin etiquetas, o servidor sin Adventure).
     */
    public Object component(String key, String... replacements) {
        String raw = raw(key);
        if (raw == null || !usesMiniMessage(raw)) {
            return null;
        }
        if (!Components.miniMessageAvailable()) {
            warnNoAdventureOnce();
            return null;
        }
        Object component = Components.fromMiniMessage(applyPlaceholders(raw, replacements));
        if (component == null) {
            warnBadMiniMessageOnce(key);
        }
        return component;
    }

    /** Lista de lineas coloreadas (ayuda, etc.); vacia si la clave no existe. */
    public List<String> getList(String key, String... replacements) {
        List<String> raw = readList(config, key);
        if (raw == null) {
            raw = readList(fallback, key);
        }
        if (raw == null) {
            warnMissingKey(key);
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(formatLine(line, replacements));
        }
        return lines;
    }

    /**
     * Lista de Components, pero solo si alguna linea usa MiniMessage: una lista
     * legacy se envia como texto de siempre (mismo resultado y sin trabajo de
     * mas). En una lista mixta, las lineas legacy se convierten a Component para
     * que el orden y el envio sean consistentes.
     */
    public List<Object> componentList(String key, String... replacements) {
        List<String> raw = readList(config, key);
        if (raw == null) {
            raw = readList(fallback, key);
        }
        if (raw == null || !Components.miniMessageAvailable()) {
            return Collections.emptyList();
        }
        boolean anyMiniMessage = false;
        for (String line : raw) {
            if (usesMiniMessage(line)) {
                anyMiniMessage = true;
                break;
            }
        }
        if (!anyMiniMessage) {
            return Collections.emptyList();
        }
        List<Object> components = new ArrayList<>(raw.size());
        for (String line : raw) {
            if (!usesMiniMessage(line)) {
                Object legacy = Components.fromLegacy(Text.color(applyPlaceholders(line, replacements)));
                if (legacy == null) {
                    return Collections.emptyList();
                }
                components.add(legacy);
                continue;
            }
            Object component = Components.fromMiniMessage(applyPlaceholders(line, replacements));
            if (component == null) {
                return Collections.emptyList();
            }
            components.add(component);
        }
        return components;
    }

    public void send(CommandSender to, String key, String... replacements) {
        Object component = component(key, replacements);
        if (component != null && Components.send(to, component)) {
            return;
        }
        String message = get(key, replacements);
        if (!Text.isBlank(message)) {
            to.sendMessage(message);
        }
    }

    public void sendList(CommandSender to, String key, String... replacements) {
        List<Object> components = componentList(key, replacements);
        if (!components.isEmpty()) {
            boolean allSent = true;
            for (Object component : components) {
                if (!Components.send(to, component)) {
                    allSent = false;
                    break;
                }
            }
            if (allSent) {
                return;
            }
        }
        for (String line : getList(key, replacements)) {
            to.sendMessage(line);
        }
    }

    /**
     * Normaliza el codigo de idioma de config.yml: minusculas, sin espacios y
     * solo caracteres seguros. Cualquier cosa rara cae a ingles.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return DEFAULT_LANGUAGE;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return LANGUAGE_CODE.matcher(value).matches() ? value : DEFAULT_LANGUAGE;
    }

    // ------------------------------------------------------------- internals

    /** Texto crudo de la clave (activo y, si falta, el ingles del jar). */
    private String raw(String key) {
        String value = config == null ? null : config.getString(key);
        if (value == null && fallback != null) {
            value = fallback.getString(key);
        }
        if (value == null) {
            warnMissingKey(key);
            return null;
        }
        return value;
    }

    /** True si este texto debe pasar por MiniMessage. */
    private boolean usesMiniMessage(String raw) {
        if (messageFormat == MessageFormat.LEGACY) {
            return false;
        }
        if (messageFormat == MessageFormat.MINIMESSAGE) {
            return true;
        }
        // auto: MiniMessage solo si trae etiquetas y NO usa codigos legacy, de
        // modo que un texto con & (y sus placeholders) siga siendo legacy.
        return Text.hasMiniMessageTags(raw) && !Text.hasLegacyCodes(raw);
    }

    private String formatLine(String rawLine, String... replacements) {
        String text = applyPlaceholders(rawLine, replacements);
        if (usesMiniMessage(rawLine)) {
            if (!Components.miniMessageAvailable()) {
                warnNoAdventureOnce();
                return Text.color(Text.stripMiniMessageTags(text));
            }
            return text;
        }
        return Text.color(text);
    }

    /** {prefix} + pares clave/valor. Los placeholders van RAW en el YAML. */
    private String applyPlaceholders(String raw, String... replacements) {
        String text = raw.replace("{prefix}", prefix());
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace("{" + replacements[index] + "}", replacements[index + 1]);
        }
        return text;
    }

    /** Prefijo del idioma activo; si falta, el del ingles. */
    private String prefix() {
        String active = config == null ? null : config.getString("prefix");
        if (active == null && fallback != null) {
            active = fallback.getString("prefix");
        }
        return active == null ? "" : active;
    }

    private void warnNoAdventureOnce() {
        if (!warnedNoAdventure) {
            warnedNoAdventure = true;
            plugin.getLogger().warning("messages-format is set to MiniMessage but this server has no Adventure; "
                    + "sending the texts as plain legacy colours. Set messages-format: legacy to silence this.");
        }
    }

    private void warnBadMiniMessageOnce(String key) {
        if (!warnedMiniMessage) {
            warnedMiniMessage = true;
            plugin.getLogger().warning("Could not parse '" + key + "' as MiniMessage; falling back to legacy. "
                    + "Check the tags of that message in messages_" + language + ".yml.");
        }
    }

    /** Carga el archivo del idioma, creandolo desde el jar si hace falta. */
    private FileConfiguration loadConfigured(String code) {
        File file = new File(folder, "messages_" + code + ".yml");
        if (!file.exists()) {
            String resource = "messages_" + code + ".yml";
            if (plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            } else if (!DEFAULT_LANGUAGE.equals(code)) {
                plugin.getLogger().warning("Language '" + code + "' is not included in this build; using '"
                        + DEFAULT_LANGUAGE + "'. Add your own messages_" + code + ".yml to use it.");
                this.language = DEFAULT_LANGUAGE;
                return loadConfigured(DEFAULT_LANGUAGE);
            }
        }
        if (!file.exists()) {
            plugin.getLogger().severe("Bundled messages_" + code + ".yml is missing; built-in defaults will be used.");
            return fallback;
        }
        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (loaded.getKeys(false).isEmpty()) {
            plugin.getLogger().severe("messages_" + code + ".yml is empty or unreadable; built-in defaults will be used.");
            return fallback;
        }
        int fileVersion = loaded.getInt("config-version", CONFIG_VERSION);
        if (fileVersion > CONFIG_VERSION) {
            plugin.getLogger().warning("messages_" + code + ".yml comes from a newer plugin version (config-version "
                    + fileVersion + "). Some keys may not be understood by this build.");
        }
        return loaded;
    }

    /** Copia en memoria del messages_en.yml incluido en el jar. */
    private FileConfiguration loadBundled(String code) {
        String resource = "messages_" + code + ".yml";
        InputStream stream = plugin.getResource(resource);
        if (stream == null) {
            plugin.getLogger().severe("Bundled " + resource + " is missing from the jar; messages will be empty.");
            return new YamlConfiguration();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the bundled " + resource, e);
            return new YamlConfiguration();
        }
    }

    /** Avisa una sola vez por arranque de las claves que falten en la traduccion. */
    private void warnAboutMissingKeys() {
        if (config == null || fallback == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String key : fallback.getKeys(true)) {
            if (!fallback.isConfigurationSection(key) && !config.contains(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        StringBuilder sample = new StringBuilder();
        for (int index = 0; index < missing.size() && index < 10; index++) {
            if (index > 0) {
                sample.append(", ");
            }
            sample.append(missing.get(index));
        }
        if (missing.size() > 10) {
            sample.append(", ...");
        }
        plugin.getLogger().warning("messages_" + language + ".yml is missing " + missing.size()
                + " key(s); English text is used for them (" + sample + ").");
    }

    private void warnMissingKey(String key) {
        if (missingKeysWarned.add(key)) {
            plugin.getLogger().warning("Missing message key '" + key + "' in messages_" + language + ".yml.");
        }
    }

    private List<String> readList(FileConfiguration source, String key) {
        if (source == null) {
            return null;
        }
        if (source.isList(key)) {
            return source.getStringList(key);
        }
        // Un admin puede escribir el mensaje como una sola linea.
        if (source.isString(key)) {
            return Collections.singletonList(source.getString(key));
        }
        return null;
    }
}
