package io.github.adrielorihuela.legacyfolia.config.migration;

import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What an import is going to write, worked out before anything is written.
 *
 * <p>Values are the effective values the older server ran with, already resolved across files and
 * converted to the TOML's spelling. Whether each one is actually written is decided when it is
 * applied, against what the TOML files hold at that moment, so that applying the same plan twice
 * writes nothing the second time.</p>
 */
final class ImportPlan {

    /**
     * One value to write.
     *
     * @param entry the setting
     * @param value the TOML value
     * @param from where it came from, for the report: file and path, or a description of how it
     *     was resolved
     */
    record Planned(SettingEntry entry, Object value, String from) {
    }

    /** Values for the global sections of the eight files, by TOML path. */
    final Map<String, Planned> global = new LinkedHashMap<>();

    /**
     * Overrides for {@code worlds.toml}, by world and then by TOML path. {@code default} is
     * {@code [worlds.default]}.
     */
    final Map<String, Map<String, Planned>> worlds = new TreeMap<>((a, b) -> {
        if (a.equals(b)) {
            return 0;
        }
        if (WorldConfig.DEFAULT.equals(a)) {
            return -1;
        }
        if (WorldConfig.DEFAULT.equals(b)) {
            return 1;
        }
        return a.compareTo(b);
    });

    /** Secrets to move into their files, by the TOML path of the setting that names the file. */
    final Map<String, Planned> secrets = new LinkedHashMap<>();

    /** How each collision between files was resolved. */
    final List<String> collisions = new ArrayList<>();

    /** Legacy keys with no place in the TOML files. */
    final List<String> unplaced = new ArrayList<>();

    /** Legacy values the setting cannot take, left at the default. */
    final List<String> invalid = new ArrayList<>();

    /** Keys that are file metadata, not settings. */
    final List<String> ignored = new ArrayList<>();

    void global(final SettingEntry entry, final Object value, final String from) {
        this.global.put(entry.tomlPath(), new Planned(entry, value, from));
    }

    void world(final String world, final SettingEntry entry, final Object value, final String from) {
        this.worlds.computeIfAbsent(world, ignored -> new LinkedHashMap<>())
            .put(entry.tomlPath(), new Planned(entry, value, from));
    }
}
