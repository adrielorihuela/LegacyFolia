package io.github.adrielorihuela.legacyfolia.config.migration;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One file left behind by the server this one replaces.
 *
 * @param kind what the file is
 * @param path where it is now
 * @param relative where it is relative to the server root, with forward slashes; it keeps this
 *     path inside the backup, so {@code world_nether/paper-world.yml} is backed up as
 *     {@code config/legacy-backup/<date>/world_nether/paper-world.yml}
 * @param world the world a {@code paper-world.yml} belongs to, as the server names it; {@code null}
 *     for every other kind
 */
record LegacyFile(Kind kind, Path path, String relative, @Nullable String world) {

    LegacyFile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(relative, "relative");
        if ((kind == Kind.PAPER_WORLD) != (world != null)) {
            throw new IllegalArgumentException("only a paper-world.yml belongs to a world: " + relative);
        }
    }

    /** The same file, somewhere else: used once it has been moved into the backup. */
    LegacyFile at(final Path newPath) {
        return new LegacyFile(this.kind, newPath, this.relative, this.world);
    }

    /** The kinds of file an older server leaves behind. */
    enum Kind {

        /** {@code server.properties}, vanilla's. */
        SERVER_PROPERTIES("server.properties"),

        /** {@code bukkit.yml}. */
        BUKKIT("bukkit.yml"),

        /** {@code spigot.yml}, global settings and {@code world-settings} both. */
        SPIGOT("spigot.yml"),

        /** {@code commands.yml}. */
        COMMANDS("commands.yml"),

        /** {@code config/paper-global.yml}. */
        PAPER_GLOBAL("paper-global.yml"),

        /** {@code config/paper-world-defaults.yml}. */
        PAPER_WORLD_DEFAULTS("paper-world-defaults.yml"),

        /** {@code <world>/paper-world.yml}. */
        PAPER_WORLD("paper-world.yml"),

        /** {@code eula.txt}. */
        EULA("eula.txt");

        private final String fileName;

        Kind(final String fileName) {
            this.fileName = fileName;
        }

        /** The file's name on disk. */
        String fileName() {
            return this.fileName;
        }
    }
}
