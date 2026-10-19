package io.github.adrielorihuela.legacyfolia.config.catalog;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Every key of the eight TOML files, with its type, its default, and where it came from.
 *
 * <p>The TOML files say what a setting is and what it does. They cannot say that
 * {@code gameplay.hunger.jump-walk-exhaustion} was
 * {@code world-settings.default.hunger.jump-walk-exhaustion} in {@code spigot.yml}, that a negative
 * Paper spawn limit means "ask Bukkit", or that {@code server.motd} is called {@code motd} in
 * {@code server.properties}. The hook-up needs all three: the migration reads old files by their
 * paths, the server.properties mode writes keys by their vanilla names, and the in-memory Spigot and
 * Paper objects are filled by path. This catalog is the one place that knowledge is written down.</p>
 *
 * <p><b>Why a data file and not a Java table.</b> The catalog lives in
 * {@code legacyfolia/catalog.tsv}, one row per key, loaded once. Close to five hundred rows of
 * {@code new SettingEntry(...)} would be a file nobody reads; as a table they line up in columns,
 * diff one line per key, and can be checked with grep or awk without compiling. What a Java table
 * would buy, a compile error for a typo, the tests buy instead: {@code SettingsCatalogTest} fails if
 * a TOML key has no row or a row names no key, if a default disagrees with the TOML, if a legacy
 * path is used twice, or if a default is not a legal value of its kind. A malformed row stops the
 * class from loading at all, so a broken catalog cannot reach a running server.</p>
 *
 * <p>{@code worlds.toml} has no rows: it holds overrides of keys that live in the other files, and
 * those overrides are described by the {@link SettingEntry#perWorld()} flag of the key they
 * override.</p>
 */
public final class SettingsCatalog {

    /** The bundled resource. */
    public static final String RESOURCE = "legacyfolia/catalog.tsv";

    private static final int COLUMNS = 12;

    private static volatile @Nullable SettingsCatalog instance;

    private final List<SettingEntry> entries;

    private final Map<String, SettingEntry> byPath;

    private final Map<String, SettingEntry> byServerPropertiesKey;

    private final Map<LegacyRef, SettingEntry> byLegacy;

    private SettingsCatalog(final List<SettingEntry> entries) {
        this.entries = List.copyOf(entries);
        final Map<String, SettingEntry> paths = new LinkedHashMap<>();
        final Map<String, SettingEntry> properties = new HashMap<>();
        final Map<LegacyRef, SettingEntry> legacy = new HashMap<>();
        for (final SettingEntry entry : entries) {
            if (paths.put(entry.tomlPath(), entry) != null) {
                throw new IllegalArgumentException("the catalog lists " + entry.tomlPath() + " twice");
            }
            final String key = entry.serverPropertiesKey();
            if (key != null && properties.put(key, entry) != null) {
                throw new IllegalArgumentException("two settings share the server.properties key " + key);
            }
            for (final LegacyRef ref : entry.allLegacy()) {
                legacy.putIfAbsent(ref, entry);
            }
        }
        this.byPath = Collections.unmodifiableMap(paths);
        this.byServerPropertiesKey = Map.copyOf(properties);
        this.byLegacy = Map.copyOf(legacy);
    }

    /**
     * The catalog bundled with the server, loaded the first time it is asked for.
     *
     * @return the catalog
     * @throws IllegalStateException if the bundled file is missing or malformed, which is a build
     *     error rather than a configuration one
     */
    public static SettingsCatalog get() {
        SettingsCatalog loaded = instance;
        if (loaded == null) {
            synchronized (SettingsCatalog.class) {
                loaded = instance;
                if (loaded == null) {
                    loaded = loadBundled();
                    instance = loaded;
                }
            }
        }
        return loaded;
    }

    private static SettingsCatalog loadBundled() {
        try (InputStream in = SettingsCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the built-in " + RESOURCE + " is missing from the jar");
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException("could not read the built-in " + RESOURCE, e);
        }
    }

    /**
     * Parses a catalog in the format of {@link #RESOURCE}.
     *
     * @param reader the tab separated text
     * @return the catalog
     * @throws IOException if the reader fails
     * @throws IllegalArgumentException if a row is malformed, naming the line
     */
    public static SettingsCatalog parse(final Reader reader) throws IOException {
        final List<String[]> rows = new ArrayList<>();
        final List<Integer> lineNumbers = new ArrayList<>();
        final StringBuilder defaults = new StringBuilder();
        try (BufferedReader lines = new BufferedReader(reader)) {
            String line;
            int number = 0;
            boolean header = false;
            while ((line = lines.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!header) {
                    if (!line.startsWith("path\t")) {
                        throw new IllegalArgumentException("line " + number + ": expected the column header");
                    }
                    header = true;
                    continue;
                }
                final String[] cells = line.split("\t", -1);
                if (cells.length != COLUMNS) {
                    throw new IllegalArgumentException("line " + number + ": expected " + COLUMNS
                        + " columns, found " + cells.length);
                }
                // All the defaults are parsed in one go, as one TOML document, which is both
                // faster than five hundred parser runs and the surest way to read them exactly as
                // the TOML files themselves are read.
                defaults.append('k').append(rows.size()).append(" = ").append(cells[10]).append('\n');
                rows.add(cells);
                lineNumbers.add(number);
            }
        }

        final CommentedConfig parsed = CommentedConfig.inMemory();
        try {
            new TomlParser().parse(new StringReader(defaults.toString()), parsed, ParsingMode.REPLACE);
        } catch (final ParsingException e) {
            throw new IllegalArgumentException("a default in the catalog is not a TOML value: " + e.getMessage(), e);
        }

        final List<SettingEntry> entries = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            final String[] cells = rows.get(i);
            try {
                entries.add(entry(cells, plain(parsed.get(List.of("k" + i)))));
            } catch (final RuntimeException e) {
                throw new IllegalArgumentException("line " + lineNumbers.get(i) + " (" + cells[0] + "): "
                    + e.getMessage(), e);
            }
        }
        return new SettingsCatalog(entries);
    }

    private static SettingEntry entry(final String[] cells, final Object defaultValue) {
        final String path = cells[0];
        final LegacySource source = LegacySource.valueOf(cells[3]);
        final LegacyRef legacy = source == LegacySource.NONE ? LegacyRef.NONE : new LegacyRef(source, required(cells[4], "legacy-path"));
        if (source == LegacySource.NONE && optional(cells[4]) != null) {
            throw new IllegalArgumentException("a setting with source NONE cannot have a legacy path");
        }
        final List<LegacyRef> alsoFrom = new ArrayList<>();
        final String also = optional(cells[5]);
        if (also != null) {
            for (final String ref : also.split(";")) {
                alsoFrom.add(LegacyRef.parse(ref.trim()));
            }
        }
        final String values = optional(cells[9]);
        return new SettingEntry(
            path,
            ServerConfig.fileNameFor(path),
            SettingKind.valueOf(cells[1]),
            defaultValue,
            bool(cells[2], "per-world"),
            legacy,
            alsoFrom,
            optional(cells[6]),
            bool(cells[7], "mutable"),
            bool(cells[8], "secret"),
            values == null ? List.of() : Arrays.asList(values.split(",")),
            optional(cells[11]));
    }

    private static String required(final String cell, final String column) {
        final String value = optional(cell);
        if (value == null) {
            throw new IllegalArgumentException("the " + column + " column is empty");
        }
        return value;
    }

    private static @Nullable String optional(final String cell) {
        final String trimmed = cell.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    private static boolean bool(final String cell, final String column) {
        return switch (cell.trim()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("the " + column + " column must be true or false, not '" + cell + "'");
        };
    }

    /**
     * Turns what night-config parsed into plain Java values: tables become unmodifiable maps in
     * file order, arrays unmodifiable lists. Callers compare these with {@code equals}, so the
     * configuration library's own types must not leak out.
     *
     * @param value a parsed TOML value
     * @return the same value as plain, unmodifiable Java objects
     */
    public static Object plain(final Object value) {
        if (value instanceof final UnmodifiableConfig table) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (final UnmodifiableConfig.Entry entry : table.entrySet()) {
                map.put(entry.getKey(), plain(entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }
        if (value instanceof final List<?> list) {
            final List<Object> copy = new ArrayList<>(list.size());
            for (final Object element : list) {
                copy.add(plain(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /** Every setting, in the order of the TOML files. */
    public List<SettingEntry> all() {
        return this.entries;
    }

    /** How many settings the catalog describes. */
    public int size() {
        return this.entries.size();
    }

    /**
     * One setting by its TOML path.
     *
     * @param tomlPath the dotted path
     * @return the setting, if there is one
     */
    public Optional<SettingEntry> byPath(final String tomlPath) {
        return Optional.ofNullable(this.byPath.get(tomlPath));
    }

    /**
     * One setting by its TOML path, for callers that name a key they know exists.
     *
     * @param tomlPath the dotted path
     * @return the setting
     * @throws IllegalArgumentException if the catalog has no such key, which is a programming error
     */
    public SettingEntry require(final String tomlPath) {
        final SettingEntry entry = this.byPath.get(tomlPath);
        if (entry == null) {
            throw new IllegalArgumentException("the settings catalog has no key " + tomlPath);
        }
        return entry;
    }

    /**
     * The settings of one TOML file.
     *
     * @param tomlFile the file's name, such as {@code gameplay.toml}
     * @return its settings, in file order
     */
    public List<SettingEntry> inFile(final String tomlFile) {
        return this.entries.stream().filter(entry -> entry.tomlFile().equals(tomlFile)).toList();
    }

    /** The settings a world may override. */
    public List<SettingEntry> perWorld() {
        return this.entries.stream().filter(SettingEntry::perWorld).toList();
    }

    /**
     * The settings that came from one legacy file.
     *
     * @param source the file
     * @param includeAlsoFrom whether to count a setting whose secondary location is in that file
     * @return the settings, in file order
     */
    public List<SettingEntry> fromSource(final LegacySource source, final boolean includeAlsoFrom) {
        return this.entries.stream().filter(entry -> entry.legacySource() == source
            || includeAlsoFrom && entry.alsoFrom().stream().anyMatch(ref -> ref.source() == source)).toList();
    }

    /**
     * The setting that a legacy path now lives in, main location or secondary.
     *
     * @param source the legacy file
     * @param path the exact path inside it; for {@link LegacySource#SPIGOT_WORLD} the path under
     *     {@code world-settings.default.}
     * @return the setting, if any key absorbed that path
     */
    public Optional<SettingEntry> byLegacy(final LegacySource source, final String path) {
        return Optional.ofNullable(this.byLegacy.get(new LegacyRef(source, path)));
    }

    /**
     * The setting a {@code server.properties} key stands for: a vanilla name such as {@code motd},
     * or a full TOML path such as {@code gameplay.hunger.jump-walk-exhaustion}. Per-world keys,
     * {@code worlds.<world>.<path>}, are not resolved here; strip the prefix and use
     * {@link #byPath(String)}.
     *
     * @param key the key as it appears in the file
     * @return the setting, if the key names one that may appear there
     */
    public Optional<SettingEntry> byServerPropertiesKey(final String key) {
        return Optional.ofNullable(this.byServerPropertiesKey.get(key));
    }

    /**
     * The setting with a given vanilla {@code server.properties} name.
     *
     * @param vanillaName the property name, such as {@code max-players}
     * @return the setting, if a vanilla key by that name is catalogued
     */
    public Optional<SettingEntry> byVanillaName(final String vanillaName) {
        return this.entries.stream().filter(entry -> vanillaName.equals(entry.vanillaName())).findFirst();
    }
}
