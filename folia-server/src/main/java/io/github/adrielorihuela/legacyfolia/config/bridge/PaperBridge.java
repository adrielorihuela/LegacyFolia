package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacyRef;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.configuration.WorldConfiguration;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

/**
 * The Configurate nodes Paper builds {@code GlobalConfiguration} and each
 * {@code WorldConfiguration} from, made from the settings rather than loaded from
 * {@code paper-global.yml}, {@code paper-world-defaults.yml} and each world's {@code paper-world.yml}.
 *
 * <p><b>Why nodes, and not an in-memory loader.</b> Paper's loaders are built with a file path
 * and do more than read: they check whether the file exists, create an empty world file as a
 * template, and save back what they loaded. An in-memory loader would still need every one of
 * those steps disabled. A node is the one thing all three code paths share — each loads one, runs
 * its transformations over it, maps it to the configuration class — so swapping where the node
 * comes from is the smallest change that keeps everything after it as it is. The hook copies these
 * values into a node made by Paper's own loader ({@code loader.createNode().from(node)}), so the
 * node carries Paper's serializers, and then skips the save.</p>
 *
 * <p><b>Transformations.</b> Every node carries {@code _version} at the version Paper's classes
 * are at, so the versioned transformations — written to upgrade old files — find nothing to do,
 * as they find nothing in a file Paper wrote itself. The removed-path transformation runs and
 * finds nothing either, because no removed path is catalogued.</p>
 *
 * <p><b>What goes in.</b> Values are in the form Paper's own YAML holds them, so the object mapper
 * reads them with Paper's serializers and nothing else:</p>
 * <ul>
 *   <li>The global node and the world-defaults node hold every catalogued Paper setting, so that
 *       a value equal to the default is still stated, as in the files Paper writes.</li>
 *   <li>A world's node holds only what that world sets differently from the defaults; Paper
 *       merges the defaults under it, as it does with a world's file.</li>
 *   <li>Where the TOML writes a resolved number and Paper writes "use the other setting"
 *       ({@code -1} or {@code "default"}), the resolved number is turned back into Paper's word
 *       whenever it is the value that word would resolve to. That keeps Paper's fallback alive:
 *       the per-world spawn limits stay {@code -1} and follow {@code bukkit.yml}, and
 *       {@code player-auto-save.rate} stays {@code -1} and follows the autosave interval, unless
 *       the TOML actually sets something else.</li>
 *   <li>{@code chunks.delay-chunk-unloads-by} is ticks in the TOML and a duration in Paper.</li>
 *   <li>The Velocity secret is never written; it is read from its own file.</li>
 * </ul>
 */
public final class PaperBridge {

    private static final String PLAYER_SAVE = "server.saving.player-save-interval-ticks";

    private static final String AUTOSAVE = "chunks.auto-save-interval";

    private static final String DELAY_UNLOADS = "chunks.delay-chunk-unloads-by";

    private static final String DESPAWN_RANGES = "performance.spawning.despawn-ranges.";

    private static final String DESPAWN_SHAPE = DESPAWN_RANGES + "shape";

    /** Paper's word, in an integer setting, for "ask the setting this one falls back to". */
    private static final int FALL_BACK = -1;

    private static final String DEFAULT = "default";

    private PaperBridge() {
    }

    /**
     * The node {@code GlobalConfiguration} is built from.
     *
     * @param source the settings
     * @return a new node, never saved anywhere
     */
    public static ConfigurationNode global(final EffectiveSettings source) {
        final ConfigurationNode root = root(version(GlobalConfiguration.class));
        for (final SettingEntry entry : SettingsCatalog.get().fromSource(LegacySource.PAPER_GLOBAL, false)) {
            if (entry.secret()) {
                continue;
            }
            final Object value = BridgeValues.global(source, entry);
            if (PLAYER_SAVE.equals(entry.tomlPath())) {
                final Object autosave = BridgeValues.global(source, SettingsCatalog.get().require(AUTOSAVE));
                set(root, entry.legacyPath(), value.equals(autosave) ? FALL_BACK : value);
                continue;
            }
            set(root, entry.legacyPath(), BridgeValues.paper(entry, value));
        }
        return root;
    }

    /**
     * The node the world defaults are built from: {@code [worlds.default]} over the global values.
     *
     * @param source the settings
     * @return a new node, never saved anywhere
     */
    public static ConfigurationNode worldDefaults(final EffectiveSettings source) {
        final ConfigurationNode root = root(version(WorldConfiguration.class));
        for (final SettingEntry entry : SettingsCatalog.get().all()) {
            final String path = paperWorldPath(entry);
            if (path == null) {
                continue;
            }
            final Object shared = BridgeValues.world(source, entry, WorldConfig.DEFAULT);
            if (entry.legacySource() != LegacySource.PAPER_WORLD) {
                // A Bukkit setting Paper can override per world: stays "ask Bukkit" unless the
                // shared layer actually differs from the server-wide value Bukkit holds.
                final Object server = BridgeValues.global(source, entry);
                set(root, path, shared.equals(server) ? FALL_BACK : shared);
                continue;
            }
            set(root, path, paperValue(entry, shared));
        }
        return root;
    }

    /**
     * The node of one world: what it sets differently from the world defaults, and nothing else.
     *
     * @param source the settings
     * @param world the world's TOML name, such as {@code world_nether}
     * @return a new node, holding only {@code _version} for a world that differs in nothing
     */
    public static ConfigurationNode world(final EffectiveSettings source, final String world) {
        final ConfigurationNode root = root(version(WorldConfiguration.class));
        for (final SettingEntry entry : SettingsCatalog.get().perWorld()) {
            final String path = paperWorldPath(entry);
            if (path == null) {
                continue;
            }
            final Object own = BridgeValues.world(source, entry, world);
            final Object shared = BridgeValues.world(source, entry, WorldConfig.DEFAULT);
            if (entry.legacySource() != LegacySource.PAPER_WORLD) {
                if (!own.equals(shared)) {
                    set(root, path, own);
                }
                continue;
            }
            final Object written = paperValue(entry, own);
            if (!written.equals(paperValue(entry, shared))) {
                set(root, path, written);
                if (entry.tomlPath().startsWith(DESPAWN_RANGES) && !DESPAWN_SHAPE.equals(entry.tomlPath())) {
                    // Paper needs both ends of a category's range; the other one is the default's.
                    final SettingEntry other = otherEndOf(entry);
                    final String otherPath = paperWorldPath(other);
                    if (otherPath != null && root.node(split(otherPath)).virtual()) {
                        set(root, otherPath, paperValue(other, BridgeValues.world(source, other, world)));
                    }
                }
            }
        }
        return root;
    }

    /** A world-level value in Paper's form, with the two conversions the TOML needs. */
    private static Object paperValue(final SettingEntry entry, final Object value) {
        if (DELAY_UNLOADS.equals(entry.tomlPath())) {
            return SettingReaders.durationFromTicks(((Number) value).longValue()).value();
        }
        if (entry.tomlPath().startsWith(DESPAWN_RANGES) && !DESPAWN_SHAPE.equals(entry.tomlPath())) {
            // The TOML writes the distance a category uses by default; Paper writes "default".
            final Object resolved = SettingReaders.convert(entry, entry.defaultValue());
            return value.equals(resolved) ? DEFAULT : value;
        }
        final Object written = BridgeValues.paper(entry, value);
        if (written instanceof final Map<?, ?> table) {
            // Registry ids, written the way Paper writes them back: without the default namespace.
            // Paper reads either form, but a "minecraft:" key would stay in the node beside the
            // short one Paper adds when it maps the node, and read as a second entry.
            final Map<String, Object> shortened = new LinkedHashMap<>();
            table.forEach((key, element) -> shortened.put(String.valueOf(key).replaceFirst("^minecraft:", ""), element));
            return shortened;
        }
        return written;
    }

    private static SettingEntry otherEndOf(final SettingEntry entry) {
        final String path = entry.tomlPath();
        final String other = path.endsWith("-hard")
            ? path.substring(0, path.length() - "-hard".length()) + "-soft"
            : path.substring(0, path.length() - "-soft".length()) + "-hard";
        return SettingsCatalog.get().require(other);
    }

    /** Where a setting lives in Paper's world configuration, if it does. */
    private static @Nullable String paperWorldPath(final SettingEntry entry) {
        if (entry.legacySource() == LegacySource.PAPER_WORLD) {
            return entry.legacyPath();
        }
        for (final LegacyRef also : entry.alsoFrom()) {
            if (also.source() == LegacySource.PAPER_WORLD) {
                return also.path();
            }
        }
        return null;
    }

    private static ConfigurationNode root(final int version) {
        final ConfigurationNode root = BasicConfigurationNode.root(ConfigurationOptions.defaults());
        root.node(Configuration.VERSION_FIELD).raw(version);
        return root;
    }

    private static void set(final ConfigurationNode root, final String path, final Object value) {
        // raw, not set: these are already the plain values a YAML file would hold, and raw turns a
        // map or a list into child nodes without asking for a serializer.
        root.node(split(path)).raw(value);
    }

    private static Object[] split(final String path) {
        return path.split("\\.");
    }

    /**
     * The version Paper's configuration classes are at. It is a package-private constant, read
     * once here so that the nodes always carry the version of the Paper they were built for.
     */
    static int version(final Class<?> type) {
        try {
            final Field field = type.getDeclaredField("CURRENT_VERSION");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read " + type.getSimpleName() + ".CURRENT_VERSION", e);
        }
    }

    /** The settings that end up in the world defaults, for the tests that check their paths. */
    static Set<String> worldPaths() {
        final Set<String> paths = new LinkedHashSet<>();
        for (final SettingEntry entry : SettingsCatalog.get().all()) {
            final String path = paperWorldPath(entry);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }
}
