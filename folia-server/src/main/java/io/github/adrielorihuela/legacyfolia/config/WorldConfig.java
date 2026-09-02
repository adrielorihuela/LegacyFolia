package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The configuration as one world sees it: the global value for every setting, with that world's
 * overrides already applied.
 *
 * <p>More than half of the settings this fork inherited are per-world. Spigot resolves them as
 * {@code world-settings.<world>} falling back to {@code world-settings.default}, and Paper as
 * {@code <world>/paper-world.yml} falling back to {@code paper-world-defaults.yml}. Both chains end
 * at a default written in code. This class is that chain, expressed once:</p>
 *
 * <ol>
 *   <li>{@code [worlds.<name>]} — what this world says,</li>
 *   <li>{@code [worlds.default]} — what every world says,</li>
 *   <li>the global section the path names, in whichever file owns it,</li>
 *   <li>the fallback the caller passes, which is the value the code would have used anyway.</li>
 * </ol>
 *
 * <p><b>Why the layers are flattened at construction.</b> Several of these settings are read inside
 * tick loops — an activation range is consulted once per entity per tick. Walking four nested maps
 * on every one of those reads would make the resolution itself the cost. So the whole chain is
 * collapsed, once, into a single flat map from dotted path to value: the global sections first, then
 * {@code [worlds.default]} over them, then {@code [worlds.<name>]} over that. A read is then one
 * hash lookup and a type check, with no allocation, no file access and no traversal. The price is
 * that a view is a snapshot: it stops being true the moment the files are re-read, which is why
 * {@link WorldConfigCache} throws every view away on reload rather than trying to patch them.</p>
 *
 * <p>Keys may be written either way round. {@code [worlds.default]} accepts a nested table, and it
 * accepts a quoted dotted key naming the path in full; both flatten to the same thing, so an
 * operator overriding one setting does not have to reproduce four levels of table headers.</p>
 *
 * <p>A world literally named {@code default} cannot have its own section, because that name is
 * taken by the layer that applies to every world. The server has no such world, and Spigot has the
 * same limitation.</p>
 */
public final class WorldConfig {

    /** The section standing for every world, matching Spigot's {@code world-settings.default}. */
    public static final String DEFAULT = "default";

    private final String world;

    private final Map<String, Object> values;

    WorldConfig(final String world, final Map<String, Object> values) {
        this.world = world;
        this.values = values;
    }

    /**
     * Builds the view for one world.
     *
     * @param world the world's name, as the server knows it
     * @param globals the global sections that per-world settings may override, each a whole file's
     *     contents so that the paths flattened out of them are the paths callers ask for
     * @param worlds the {@code [worlds]} table, or {@code null} when the file has none
     * @return the resolved view
     */
    public static WorldConfig resolve(final String world, final List<UnmodifiableConfig> globals,
                                      final UnmodifiableConfig worlds) {
        final Map<String, Object> resolved = new HashMap<>();
        for (final UnmodifiableConfig global : globals) {
            flattenInto(global, "", resolved);
        }
        if (worlds != null) {
            overlay(worlds, DEFAULT, resolved);
            if (!DEFAULT.equals(world)) {
                overlay(worlds, world, resolved);
            }
        }
        return new WorldConfig(world, Map.copyOf(resolved));
    }

    private static void overlay(final UnmodifiableConfig worlds, final String name,
                                final Map<String, Object> resolved) {
        if (worlds.get(List.of(name)) instanceof final UnmodifiableConfig section) {
            flattenInto(section, "", resolved);
        }
    }

    /**
     * Copies a tree of tables into a flat map of dotted path to value, later entries winning.
     *
     * <p>A key that already contains dots is one key, not several: TOML's quoted keys are how an
     * operator names a deep path without writing out the tables above it.</p>
     */
    static void flattenInto(final UnmodifiableConfig config, final String prefix,
                            final Map<String, Object> into) {
        for (final UnmodifiableConfig.Entry entry : config.entrySet()) {
            final String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof final UnmodifiableConfig section) {
                flattenInto(section, path, into);
            } else {
                into.put(path, entry.getValue());
            }
        }
    }

    /** The world this view was resolved for. */
    public String world() {
        return this.world;
    }

    /** How many settings resolved to a value, for tests and for the debug logging. */
    public int size() {
        return this.values.size();
    }

    /**
     * A boolean setting.
     *
     * @param path the dotted path, such as {@code gameplay.hunger.sprint}
     * @param fallback what to use when neither the world nor the global section says
     * @return the resolved value
     */
    public boolean getBoolean(final String path, final boolean fallback) {
        return this.values.get(path) instanceof final Boolean bool ? bool : fallback;
    }

    /**
     * An integer setting.
     *
     * @param path the dotted path
     * @param fallback what to use when neither the world nor the global section says
     * @return the resolved value
     */
    public int getInt(final String path, final int fallback) {
        return this.values.get(path) instanceof final Number number ? number.intValue() : fallback;
    }

    /**
     * A floating point setting.
     *
     * @param path the dotted path
     * @param fallback what to use when neither the world nor the global section says
     * @return the resolved value
     */
    public double getDouble(final String path, final double fallback) {
        return this.values.get(path) instanceof final Number number ? number.doubleValue() : fallback;
    }

    /**
     * A string setting.
     *
     * @param path the dotted path
     * @param fallback what to use when neither the world nor the global section says
     * @return the resolved value, trimmed
     */
    public String getString(final String path, final String fallback) {
        return this.values.get(path) instanceof final String string ? string.trim() : fallback;
    }

    /**
     * A list-of-strings setting, with blank entries dropped.
     *
     * @param path the dotted path
     * @return the resolved values, never {@code null}
     */
    public List<String> getStringList(final String path) {
        if (!(this.values.get(path) instanceof final List<?> list)) {
            return List.of();
        }
        final List<String> strings = new ArrayList<>(list.size());
        for (final Object element : list) {
            if (element instanceof final String string && !string.isBlank()) {
                strings.add(string.trim());
            }
        }
        return List.copyOf(strings);
    }
}
