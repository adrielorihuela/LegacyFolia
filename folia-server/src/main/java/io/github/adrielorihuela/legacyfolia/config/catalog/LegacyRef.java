package io.github.adrielorihuela.legacyfolia.config.catalog;

import java.util.Objects;

/**
 * One place a setting used to live: a legacy file and the exact path inside it.
 *
 * @param source the file
 * @param path the dotted YAML path, or the property name for {@code server.properties}; empty only
 *     for {@link LegacySource#NONE}
 */
public record LegacyRef(LegacySource source, String path) {

    /** The reference every fork-only setting has. */
    public static final LegacyRef NONE = new LegacyRef(LegacySource.NONE, "");

    public LegacyRef {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        if ((source == LegacySource.NONE) != path.isEmpty()) {
            throw new IllegalArgumentException(source == LegacySource.NONE
                ? "a setting with no legacy source cannot have a legacy path: " + path
                : "a legacy source needs a path: " + source);
        }
    }

    /**
     * Parses {@code SOURCE:path}, the form the catalog's {@code also-from} column uses.
     *
     * @param text the reference
     * @return the parsed reference
     */
    public static LegacyRef parse(final String text) {
        final int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IllegalArgumentException("expected SOURCE:path, got '" + text + "'");
        }
        return new LegacyRef(LegacySource.valueOf(text.substring(0, colon)), text.substring(colon + 1));
    }

    /** Whether this is {@link #NONE}. */
    public boolean isNone() {
        return this.source == LegacySource.NONE;
    }

    @Override
    public String toString() {
        return this.isNone() ? "NONE" : this.source + ":" + this.path;
    }
}
