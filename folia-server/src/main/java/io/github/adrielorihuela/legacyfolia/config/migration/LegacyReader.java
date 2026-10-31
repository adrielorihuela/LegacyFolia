package io.github.adrielorihuela.legacyfolia.config.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads a legacy file into a tree of plain Java values.
 *
 * <p>A properties file becomes a flat map of strings, a YAML file the nested maps SnakeYAML builds.
 * Nothing is interpreted here; which value means what is the planner's business. A file that
 * cannot be parsed is reported with its name and the import stops before anything has been moved,
 * because importing half of a server's configuration would apply a setup nobody chose.</p>
 */
final class LegacyReader {

    private LegacyReader() {
    }

    /**
     * Reads one legacy file.
     *
     * @param file the file
     * @return its contents: for {@code server.properties} and {@code eula.txt} a map from key to
     *     string, sorted by key; for the YAML files the root mapping, empty for an empty file
     * @throws IOException if it could not be read or parsed
     */
    static Map<String, Object> read(final LegacyFile file) throws IOException {
        return switch (file.kind()) {
            case SERVER_PROPERTIES, EULA -> properties(file.path());
            default -> yaml(file);
        };
    }

    static Map<String, Object> properties(final Path path) throws IOException {
        // As vanilla's Settings.loadFromFile reads it: UTF-8, which is what it writes, and
        // ISO-8859-1 for a file an older version or a text editor wrote, which UTF-8 cannot decode.
        // Reading it any other way would mangle a motd with accents.
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)));
        } catch (final CharacterCodingException e) {
            properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                properties.load(reader);
            }
        }
        final Map<String, Object> map = new TreeMap<>();
        for (final String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return map;
    }

    private static Map<String, Object> yaml(final LegacyFile file) throws IOException {
        final LoaderOptions options = new LoaderOptions();
        final Yaml yaml = new Yaml(new SafeConstructor(options));
        final Object loaded;
        try (Reader reader = Files.newBufferedReader(file.path(), StandardCharsets.UTF_8)) {
            loaded = yaml.load(reader);
        } catch (final YAMLException e) {
            throw new IOException(file.relative() + " is not valid YAML: " + e.getMessage(), e);
        }
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof final Map<?, ?> map)) {
            throw new IOException(file.relative() + " does not hold a YAML mapping at its root");
        }
        return stringKeys(map);
    }

    /** YAML allows any key; the legacy files only use strings, but a number key must not break the walk. */
    static Map<String, Object> stringKeys(final Map<?, ?> map) {
        final Map<String, Object> copy = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final Object value = entry.getValue() instanceof final Map<?, ?> nested ? stringKeys(nested) : entry.getValue();
            copy.put(String.valueOf(entry.getKey()), value);
        }
        return copy;
    }
}
