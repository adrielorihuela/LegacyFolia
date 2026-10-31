package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacyRef;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * The {@code spigot.yml} that {@code SpigotConfig} and every {@code SpigotWorldConfig} read, built
 * in memory from the settings.
 *
 * <p>The result is meant to be assigned to {@code SpigotConfig.config} in place of loading the
 * file, before {@code readConfig} runs. From there Spigot does what it always does: it reads each
 * value with its own default, and resolves each world as {@code world-settings.<key>} over
 * {@code world-settings.default}. Every key Spigot reads is in the catalogue, so the defaults it
 * adds only ever repeat what is already there. {@code Bukkit.spigot().getConfig()} returns this
 * same object.</p>
 *
 * <p>How the layers map:</p>
 * <ul>
 *   <li>Spigot's global settings take the server-wide value.</li>
 *   <li>{@code world-settings.default} takes {@code [worlds.default]} over the global value, and
 *       holds every per-world setting, as the file Spigot writes does.</li>
 *   <li>{@code world-settings.<key>} holds only what a world sets differently from that default.
 *       The key is the world's dimension key ({@code minecraft:overworld}), which is how Spigot
 *       names worlds since config version 13; the TOML names them by folder
 *       ({@code world}), so the caller says which is which.</li>
 *   <li>{@code view-distance} and {@code simulation-distance} keep Spigot's {@code "default"},
 *       which means "the server's", unless a world or {@code [worlds.default]} sets a different
 *       number; the server's own value goes to {@code server.properties}.</li>
 * </ul>
 */
public final class SpigotBridge {

    /** The config version {@code SpigotConfig} writes; a lower one would trigger its migrations. */
    static final int CONFIG_VERSION = 13;

    private static final String WORLD_SETTINGS = "world-settings.";

    private static final String DEFAULT_PREFIX = WORLD_SETTINGS + WorldConfig.DEFAULT + ".";

    /** Spigot's word for "the server's own value", used by the two distances. */
    private static final String SERVER_VALUE = "default";

    private SpigotBridge() {
    }

    /**
     * Builds the configuration.
     *
     * @param source the settings
     * @param worldKeys the dimension key of a world, by its TOML name; {@code null} for a world that
     *     is not known yet, whose section {@link #addWorld} adds when it loads
     * @return a new configuration, never saved anywhere
     */
    public static YamlConfiguration build(final EffectiveSettings source, final Function<String, @Nullable String> worldKeys) {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", CONFIG_VERSION);
        for (final SettingEntry entry : SettingsCatalog.get().all()) {
            if (entry.legacySource() == LegacySource.SPIGOT) {
                setValue(config, entry.legacyPath(), entry, BridgeValues.global(source, entry));
            } else if (entry.legacySource() == LegacySource.SPIGOT_WORLD) {
                final Object value = entry.perWorld()
                    ? BridgeValues.world(source, entry, WorldConfig.DEFAULT)
                    : BridgeValues.global(source, entry);
                setValue(config, entry.legacyPath(), entry, value);
            }
            for (final LegacyRef also : entry.alsoFrom()) {
                if (also.source() == LegacySource.SPIGOT && !isLegacyLocation(also)) {
                    setValue(config, also.path(), entry, BridgeValues.global(source, entry));
                } else if (also.source() == LegacySource.SPIGOT_WORLD) {
                    // A server-wide setting Spigot can override per world: the two distances.
                    final Object server = BridgeValues.global(source, entry);
                    final Object shared = BridgeValues.world(source, entry, WorldConfig.DEFAULT);
                    config.set(also.path(), server.equals(shared) ? SERVER_VALUE : BridgeValues.yaml(entry, shared));
                }
            }
        }
        for (final String world : source.worlds()) {
            final String key = worldKeys.apply(world);
            if (key != null) {
                addWorld(config, source, world, key);
            }
        }
        return config;
    }

    /**
     * Adds one world's section: every per-world setting the world sets differently from
     * {@code world-settings.default}. Nothing is written for a world that differs in nothing.
     *
     * @param config the configuration {@link #build} made
     * @param source the settings
     * @param world the world's TOML name, such as {@code world_nether}
     * @param worldKey its dimension key, such as {@code minecraft:the_nether}
     */
    public static void addWorld(final YamlConfiguration config, final EffectiveSettings source, final String world, final String worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        final String prefix = WORLD_SETTINGS + worldKey + ".";
        for (final SettingEntry entry : SettingsCatalog.get().perWorld()) {
            final String path = spigotWorldPath(entry);
            if (path == null) {
                continue;
            }
            final Object own = BridgeValues.world(source, entry, world);
            final Object shared = BridgeValues.world(source, entry, WorldConfig.DEFAULT);
            if (!BridgeValues.yaml(entry, own).equals(BridgeValues.yaml(entry, shared))) {
                config.set(prefix + path.substring(DEFAULT_PREFIX.length()), BridgeValues.yaml(entry, own));
            }
        }
    }

    /**
     * The usual dimension keys of a server's three worlds, named the way Paper names their folders:
     * the level name, {@code _nether} and {@code _the_end}.
     *
     * @param levelName {@code level-name}
     * @return the key of each of the three, and {@code null} for any other name
     */
    public static Function<String, @Nullable String> vanillaWorldKeys(final String levelName) {
        final Map<String, String> keys = Map.of(
            levelName, "minecraft:overworld",
            levelName + "_nether", "minecraft:the_nether",
            levelName + "_the_end", "minecraft:the_end");
        return keys::get;
    }

    /** Where a per-world setting lives under {@code world-settings.default}, if Spigot reads it. */
    private static @Nullable String spigotWorldPath(final SettingEntry entry) {
        if (entry.legacySource() == LegacySource.SPIGOT_WORLD) {
            return entry.legacyPath();
        }
        for (final LegacyRef also : entry.alsoFrom()) {
            if (also.source() == LegacySource.SPIGOT_WORLD) {
                return also.path();
            }
        }
        return null;
    }

    /**
     * Whether a secondary location is one Spigot itself moves away from, such as the root
     * {@code replace-commands} it relocates under {@code commands.} on load. Writing it would only
     * have Spigot move it again, and show a plugin a key that no longer means anything.
     */
    private static boolean isLegacyLocation(final LegacyRef ref) {
        return "replace-commands".equals(ref.path());
    }

    private static void setValue(final YamlConfiguration config, final String path, final SettingEntry entry, final Object value) {
        final Object yaml = BridgeValues.yaml(entry, value);
        if (yaml instanceof final Map<?, ?> table) {
            config.createSection(path, table);
        } else {
            config.set(path, yaml);
        }
    }
}
