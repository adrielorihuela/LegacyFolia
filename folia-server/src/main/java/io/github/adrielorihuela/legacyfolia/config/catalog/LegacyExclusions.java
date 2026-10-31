package io.github.adrielorihuela.legacyfolia.config.catalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The legacy keys that are deliberately left out of the TOML files, each with the reason.
 *
 * <p>Every key vanilla, Bukkit, Spigot and Paper read is either in the catalogue or here; the
 * catalogue test fails on a key that is in neither, so a key a new upstream adds cannot slip
 * through unnoticed. The importer reports a legacy value at one of these paths as not imported,
 * with the reason given here, rather than as a key nobody knows. {@code docs/SETTINGS-MAP.md}
 * section 8 explains each one at more length.</p>
 *
 * <p>A path also excludes everything under it, for the keys that are whole maps.</p>
 */
public final class LegacyExclusions {

    private static final Map<LegacyRef, String> EXCLUDED = new LinkedHashMap<>();

    static {
        exclude(LegacySource.PAPER_WORLD, "entities.behavior.door-breaking-difficulty",
            "a map of entity type to difficulties that Paper computes at run time from each mob's own rule; "
                + "there is no default to write without inventing one (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_GLOBAL, "anticheat.obfuscation.items.model-overrides",
            "keyed by item model identifiers, not by a stable key; empty by default (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_WORLD, "tick-rates.sensor",
            "a two-dimensional table (entity type and sensor name to ticks) with no flat TOML form (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_WORLD, "tick-rates.behavior",
            "a two-dimensional table (entity type and behaviour name to ticks) with no flat TOML form (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_WORLD, "chunks.entity-per-chunk-save-limit",
            "a map by entity type, every entry -1 (no limit) by default (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_WORLD, "entities.spawning.despawn-time",
            "a map by entity type, every entry disabled by default (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_GLOBAL, "packet-limiter.overrides",
            "keyed by the Java class of a packet, which is not something a configuration file should name (SETTINGS-MAP 8)");
        exclude(LegacySource.SERVER_PROPERTIES, "management-server-secret",
            "a secret, generated anew by the server on every start; never imported (SETTINGS-MAP 8)");
        exclude(LegacySource.SERVER_PROPERTIES, "management-server-tls-keystore-password",
            "a secret with no secret file yet; copy it by hand when the management server needs it (SETTINGS-MAP 8)");
        exclude(LegacySource.PAPER_WORLD, "chunks.prevent-moving-into-unloaded-chunks",
            "Folia forces it to true whatever the file says (SETTINGS-MAP 8)");
        exclude(LegacySource.BUKKIT, "settings.compatibility",
            "dead code: CraftServer.loadCompatibilities() returns before reading it (SETTINGS-MAP 2.6)");
        exclude(LegacySource.PAPER_WORLD, "feature-seeds.features",
            "a map keyed by worldgen/feature holders that Paper fills in itself when "
                + "world.generation.random-feature-seeds is on (SETTINGS-MAP 7 and 8)");
        exclude(LegacySource.SERVER_PROPERTIES, "announce-player-achievements",
            "dead key: vanilla has not read it since 1.12, when it became a game rule");
    }

    private LegacyExclusions() {
    }

    private static void exclude(final LegacySource source, final String path, final String reason) {
        EXCLUDED.put(new LegacyRef(source, path), reason);
    }

    /**
     * Why a legacy key is not in the TOML files, if it is left out on purpose.
     *
     * @param source the file it is read from
     * @param path its path in that file, as the catalogue writes legacy paths
     * @return the reason, or empty for a key that is not excluded
     */
    public static Optional<String> reason(final LegacySource source, final String path) {
        for (final Map.Entry<LegacyRef, String> excluded : EXCLUDED.entrySet()) {
            final LegacyRef ref = excluded.getKey();
            if (sameFile(ref.source(), source) && (path.equals(ref.path()) || path.startsWith(ref.path() + "."))) {
                return Optional.of(excluded.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Every exclusion.
     *
     * @return the excluded locations, with the reason for each, in no meaningful order
     */
    public static Map<LegacyRef, String> all() {
        return Map.copyOf(EXCLUDED);
    }

    /** Spigot's two sources share one file, and a path in it means the same key whichever reads it. */
    private static boolean sameFile(final LegacySource a, final LegacySource b) {
        return a == b || a.fileName() != null && a.fileName().equals(b.fileName());
    }
}
