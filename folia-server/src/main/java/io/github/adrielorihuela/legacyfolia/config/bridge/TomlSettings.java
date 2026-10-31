package io.github.adrielorihuela.legacyfolia.config.bridge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingKind;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Settings read straight from parsed TOML documents, with no file access of their own.
 *
 * <p>This is the source the bridge's tests use, built from the bundled templates, which are what a
 * new server writes to {@code config/}; and it is a complete source for production too, given the
 * eight parsed files. It resolves exactly as {@link WorldConfig} does — {@code [worlds.<name>]},
 * then {@code [worlds.default]}, then the global section — with one difference that matters here:
 * a setting whose value is itself a table, such as {@code plugins.commands.aliases}, is kept whole
 * rather than flattened into one path per alias.</p>
 *
 * <p>Documents are applied in order, and a later one wins path by path. That is how a test lays a
 * few changed values over the bundled defaults without copying a whole file.</p>
 */
public final class TomlSettings implements EffectiveSettings {

    private static final String WORLDS = "worlds";

    private final Map<String, Object> global;

    private final Map<String, Map<String, Object>> worlds;

    private TomlSettings(final Map<String, Object> global, final Map<String, Map<String, Object>> worlds) {
        this.global = global;
        this.worlds = worlds;
    }

    /**
     * Settings from parsed documents.
     *
     * @param documents whole TOML files, in the order they apply; the {@code worlds} table of any of
     *     them is read as per-world overrides
     * @return the settings
     */
    public static TomlSettings of(final List<? extends UnmodifiableConfig> documents) {
        final Map<String, Object> global = new HashMap<>();
        final Map<String, Map<String, Object>> worlds = new LinkedHashMap<>();
        for (final UnmodifiableConfig document : documents) {
            for (final UnmodifiableConfig.Entry entry : document.entrySet()) {
                if (WORLDS.equals(entry.getKey()) && entry.getValue() instanceof final UnmodifiableConfig table) {
                    for (final UnmodifiableConfig.Entry world : table.entrySet()) {
                        if (world.getValue() instanceof final UnmodifiableConfig section) {
                            flatten(section, "", worlds.computeIfAbsent(world.getKey(), name -> new HashMap<>()));
                        }
                    }
                } else if (entry.getValue() instanceof final UnmodifiableConfig table && !isTable(entry.getKey())) {
                    flatten(table, entry.getKey(), global);
                } else {
                    global.put(entry.getKey(), SettingsCatalog.plain(entry.getValue()));
                }
            }
        }
        final Map<String, Map<String, Object>> frozen = new LinkedHashMap<>();
        worlds.forEach((name, values) -> frozen.put(name, Map.copyOf(values)));
        return new TomlSettings(Map.copyOf(global), Collections.unmodifiableMap(frozen));
    }

    /**
     * Settings from parsed documents with the server.properties mode's effective values over them.
     *
     * @param documents whole TOML files, as for {@link #of(List)}
     * @param global effective server-wide values by TOML path, each replacing what the documents
     *     say; {@code null} for none
     * @param worlds the whole per-world layer by world name and dotted path, replacing the
     *     documents' {@code [worlds]} tables; {@code null} to keep those
     * @return the settings
     */
    public static TomlSettings of(final List<? extends UnmodifiableConfig> documents,
                                  final @Nullable Map<String, Object> global,
                                  final @Nullable Map<String, Map<String, Object>> worlds) {
        final TomlSettings base = of(documents);
        if (global == null && worlds == null) {
            return base;
        }
        final Map<String, Object> mergedGlobal = new HashMap<>(base.global);
        if (global != null) {
            global.forEach((path, value) -> {
                if (value != null) {
                    mergedGlobal.put(path, SettingsCatalog.plain(value));
                }
            });
        }
        final Map<String, Map<String, Object>> mergedWorlds = new LinkedHashMap<>();
        if (worlds == null) {
            mergedWorlds.putAll(base.worlds);
        } else {
            worlds.forEach((name, values) -> {
                final Map<String, Object> copy = new HashMap<>();
                values.forEach((path, value) -> {
                    if (value != null) {
                        copy.put(path, SettingsCatalog.plain(value));
                    }
                });
                mergedWorlds.put(name, Map.copyOf(copy));
            });
        }
        return new TomlSettings(Map.copyOf(mergedGlobal), Collections.unmodifiableMap(mergedWorlds));
    }

    /**
     * The bundled templates, which hold every setting at the value a new server starts with.
     *
     * @return the defaults, as {@code ServerConfig} would read them from a fresh {@code config/}
     */
    public static TomlSettings bundled() {
        return of(bundledDocuments());
    }

    /**
     * The bundled templates with further documents laid over them.
     *
     * @param overrides TOML text, each one a document such as {@code "[server]\nmotd = \"Hi\""}
     * @return the combined settings
     */
    public static TomlSettings bundledWith(final String... overrides) {
        final List<UnmodifiableConfig> documents = new ArrayList<>(bundledDocuments());
        for (final String text : overrides) {
            documents.add(parse(text));
        }
        return of(documents);
    }

    /**
     * Parses one TOML document.
     *
     * @param text the TOML text
     * @return the parsed document
     */
    public static UnmodifiableConfig parse(final String text) {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        new TomlParser().parse(new StringReader(text), parsed, ParsingMode.REPLACE);
        return parsed;
    }

    private static List<UnmodifiableConfig> bundledDocuments() {
        final List<UnmodifiableConfig> documents = new ArrayList<>();
        for (final String fileName : ServerConfig.fileNames()) {
            final String resource = "legacyfolia/" + fileName;
            try (InputStream in = TomlSettings.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("the built-in " + resource + " is missing from the jar");
                }
                final CommentedConfig parsed = CommentedConfig.inMemory();
                new TomlParser().parse(in, parsed, ParsingMode.REPLACE);
                documents.add(parsed);
            } catch (final IOException e) {
                throw new UncheckedIOException("could not read the built-in " + resource, e);
            }
        }
        return documents;
    }

    /**
     * Copies a tree of tables into dotted paths, stopping at settings whose value is a table.
     *
     * <p>A key that already holds dots is one key naming a deep path, as TOML's quoted keys allow;
     * it is joined as written, which is how {@link WorldConfig} reads it too.</p>
     */
    private static void flatten(final UnmodifiableConfig table, final String prefix, final Map<String, Object> into) {
        for (final UnmodifiableConfig.Entry entry : table.entrySet()) {
            final String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof final UnmodifiableConfig section && !isTable(path)) {
                flatten(section, path, into);
            } else {
                into.put(path, SettingsCatalog.plain(entry.getValue()));
            }
        }
    }

    private static boolean isTable(final String path) {
        return SettingsCatalog.get().byPath(path).map(SettingEntry::kind).orElse(null) == SettingKind.TABLE;
    }

    @Override
    public @Nullable Object get(final String path) {
        return this.global.get(path);
    }

    @Override
    public @Nullable Object get(final String path, final String world) {
        if (!WorldConfig.DEFAULT.equals(world)) {
            final Map<String, Object> own = this.worlds.get(world);
            if (own != null && own.containsKey(path)) {
                return own.get(path);
            }
        }
        final Map<String, Object> shared = this.worlds.get(WorldConfig.DEFAULT);
        if (shared != null && shared.containsKey(path)) {
            return shared.get(path);
        }
        return this.global.get(path);
    }

    @Override
    public Set<String> worlds() {
        final Set<String> names = new LinkedHashSet<>(this.worlds.keySet());
        names.remove(WorldConfig.DEFAULT);
        return Collections.unmodifiableSet(names);
    }
}
