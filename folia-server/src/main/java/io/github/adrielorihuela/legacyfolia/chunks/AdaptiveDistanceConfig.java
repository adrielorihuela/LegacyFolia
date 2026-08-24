package io.github.adrielorihuela.legacyfolia.chunks;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;

/**
 * The {@code [chunks.adaptive-view-distance]} settings, read once and then only looked at.
 *
 * @param enabled whether to adjust anybody's distances at all
 * @param viewReduction chunks to take off the view distance of a player who is not playing
 * @param simulationReduction chunks to take off their simulation distance
 * @param minDistance the floor both are clamped to
 * @param maxDistance the ceiling both are clamped to
 * @param afkSeconds how long without a deliberate action counts as away
 * @param checkSeconds how often to re-examine everybody
 * @param detectAntiAfk whether to treat mechanical movement as being away
 * @param detectAutoClicker whether to treat inhumanly regular clicking as being away
 * @param debug whether to log every change
 */
public record AdaptiveDistanceConfig(boolean enabled, int viewReduction, int simulationReduction,
                                     int minDistance, int maxDistance, int afkSeconds,
                                     int checkSeconds, boolean detectAntiAfk,
                                     boolean detectAutoClicker, boolean debug) {

    /** Minecraft's own bounds, which no configuration may step outside. */
    private static final int LOWEST = 2;
    private static final int HIGHEST = 32;

    private static final String PREFIX = "chunks.adaptive-view-distance.";

    public AdaptiveDistanceConfig {
        // Clamped rather than rejected: a nonsensical bound should cost a sane server, not a
        // refusal to start.
        minDistance = Math.clamp(minDistance, LOWEST, HIGHEST);
        maxDistance = Math.clamp(maxDistance, minDistance, HIGHEST);
        viewReduction = Math.max(0, viewReduction);
        simulationReduction = Math.max(0, simulationReduction);
        afkSeconds = Math.max(1, afkSeconds);
        checkSeconds = Math.max(1, checkSeconds);
    }

    /**
     * Reads the settings from {@code config/performance.toml}.
     *
     * @return the configuration, disabled if the file has not been loaded
     */
    public static AdaptiveDistanceConfig load() {
        if (!ServerConfig.isLoaded()) {
            return disabled();
        }
        return new AdaptiveDistanceConfig(
            ServerConfig.getBoolean(PREFIX + "enabled", false),
            ServerConfig.getInt(PREFIX + "view-reduction", 4),
            ServerConfig.getInt(PREFIX + "simulation-reduction", 4),
            ServerConfig.getInt(PREFIX + "min-distance", 3),
            ServerConfig.getInt(PREFIX + "max-distance", 32),
            ServerConfig.getInt(PREFIX + "afk-seconds", 300),
            ServerConfig.getInt(PREFIX + "check-seconds", 10),
            ServerConfig.getBoolean(PREFIX + "detect-anti-afk-macros", true),
            ServerConfig.getBoolean(PREFIX + "detect-auto-clickers", true),
            ServerConfig.getBoolean(PREFIX + "debug", false));
    }

    /** Everything off, for before the configuration exists. */
    public static AdaptiveDistanceConfig disabled() {
        return new AdaptiveDistanceConfig(false, 4, 4, 3, 32, 300, 10, true, true, false);
    }

    /** How long without a deliberate action counts as away, in milliseconds. */
    public long afkMillis() {
        return this.afkSeconds * 1000L;
    }
}
