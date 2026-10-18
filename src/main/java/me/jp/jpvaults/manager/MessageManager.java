package me.jp.jpvaults.manager;

import me.jp.jpvaults.JPPlayerVaults;
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
 * El idioma se elige en config.yml (language). El archivo del idioma pedido se
 * genera en la carpeta del plugin a partir del recurso incluido en el jar; si
 * falta una clave, se usa el texto ingles del propio jar para que una
 * traduccion incompleta no deje huecos. Si el idioma no existe se avisa y se
 * cae a ingles.
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

    public MessageManager(JPPlayerVaults plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder();
    }

    /**
     * Carga (o recarga) el idioma pedido. Nunca lanza: si el archivo no se
     * puede leer se usan los textos incluidos en el jar.
     */
    public void load(String requestedLanguage) {
        String normalized = normalize(requestedLanguage);
        if (requestedLanguage != null && !requestedLanguage.trim().equalsIgnoreCase(normalized)) {
            plugin.getLogger().warning("Invalid language '" + requestedLanguage + "' in config.yml; using '"
                    + normalized + "'.");
        }
        this.language = normalized;
        this.fallback = loadBundled(DEFAULT_LANGUAGE);
        this.missingKeysWarned.clear();
        this.config = loadConfigured(normalized);
        warnAboutMissingKeys();
    }

    /** Idioma activo, ya validado. */
    public String getLanguage() {
        return language;
    }

    /** Texto coloreado con los reemplazos aplicados; "" si la clave no existe. */
    public String get(String key, String... replacements) {
        String raw = config == null ? null : config.getString(key);
        if (raw == null && fallback != null) {
            raw = fallback.getString(key);
        }
        if (raw == null) {
            warnMissingKey(key);
            return "";
        }
        return format(raw, replacements);
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
            lines.add(format(line, replacements));
        }
        return lines;
    }

    public void send(CommandSender to, String key, String... replacements) {
        String message = get(key, replacements);
        if (!Text.isBlank(message)) {
            to.sendMessage(message);
        }
    }

    public void sendList(CommandSender to, String key, String... replacements) {
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

    /** {prefix} + pares clave/valor + color. Los placeholders van RAW en el YAML. */
    private String format(String raw, String... replacements) {
        String text = raw.replace("{prefix}", prefix());
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace("{" + replacements[index] + "}", replacements[index + 1]);
        }
        return Text.color(text);
    }

    /** Prefijo del idioma activo; si falta, el del ingles. */
    private String prefix() {
        String active = config == null ? null : config.getString("prefix");
        if (active == null && fallback != null) {
            active = fallback.getString("prefix");
        }
        return active == null ? "" : active;
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
