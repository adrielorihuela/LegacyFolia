package io.github.adrielorihuela.legacyfolia.config.catalog;

import org.jspecify.annotations.Nullable;

/**
 * Where a setting lived before this fork gave it a TOML key.
 *
 * <p>Two sources can share a file. Spigot's global settings and its per-world settings are both in
 * {@code spigot.yml}; they are told apart because they are read by different classes and resolved
 * differently, but a migration reading the file sees one YAML tree. {@link #fileName()} is the
 * file, and two legacy paths collide only when both their file and their path are equal.</p>
 */
public enum LegacySource {

    /** {@code server.properties}, read by {@code DedicatedServerProperties}. */
    SERVER_PROPERTIES("server.properties", false),

    /** {@code bukkit.yml}, read by {@code CraftServer}. */
    BUKKIT("bukkit.yml", false),

    /** The global part of {@code spigot.yml}, read by {@code SpigotConfig}. */
    SPIGOT("spigot.yml", false),

    /**
     * The per-world part of {@code spigot.yml}, read by {@code SpigotWorldConfig}. Paths are given
     * under {@code world-settings.default.}; a world's own override is the same path under
     * {@code world-settings.<world>.}.
     */
    SPIGOT_WORLD("spigot.yml", true),

    /** {@code commands.yml}, read by {@code CraftServer}. */
    COMMANDS("commands.yml", false),

    /** {@code config/paper-global.yml}, read into {@code GlobalConfiguration}. */
    PAPER_GLOBAL("paper-global.yml", false),

    /**
     * {@code config/paper-world-defaults.yml}, read into {@code WorldConfiguration}. A world's own
     * override is the same path in {@code <world>/paper-world.yml}.
     */
    PAPER_WORLD("paper-world-defaults.yml", true),

    /** {@code eula.txt}. */
    EULA_TXT("eula.txt", false),

    /** No legacy file: the setting is this fork's own. */
    NONE(null, false);

    private final @Nullable String fileName;

    private final boolean perWorld;

    LegacySource(final @Nullable String fileName, final boolean perWorld) {
        this.fileName = fileName;
        this.perWorld = perWorld;
    }

    /**
     * The file this source is read from.
     *
     * @return its name, or {@code null} for {@link #NONE}
     */
    public @Nullable String fileName() {
        return this.fileName;
    }

    /**
     * Whether the source resolves per world.
     *
     * @return whether a world may override the default in its own section or file
     */
    public boolean perWorld() {
        return this.perWorld;
    }
}
