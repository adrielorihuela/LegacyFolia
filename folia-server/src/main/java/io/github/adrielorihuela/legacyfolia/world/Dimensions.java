package io.github.adrielorihuela.legacyfolia.world;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Which of the three vanilla dimensions the server creates at all.
 *
 * <p>A dimension that is turned off is never loaded: no world folder is read or written, no chunks
 * of it sit in memory, nothing in it ticks, and no region threads are spent on it. For a lobby or
 * a minigame server that never sends anybody to the Nether, that is the cheapest saving available.
 * Anything that would send a player there has to cope with the destination not existing, which is
 * what {@link #exists} is for.</p>
 *
 * <p>Only the three vanilla dimensions have switches. A dimension a datapack adds is always
 * loaded — it was added on purpose, and there is no sensible default for something we know
 * nothing about.</p>
 */
public final class Dimensions {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Set once during start-up, then only read, so the hot paths do not touch the config file. */
    private static volatile boolean nether = true;
    private static volatile boolean end = true;

    private Dimensions() {
    }

    /**
     * Reads the switches from {@code config/server.config}.
     *
     * <p>Called once, before any world is loaded. Turning the Overworld off is refused rather than
     * obeyed: the server has nowhere to put a joining player without it, and every later failure
     * would be a confusing symptom of that one setting.</p>
     */
    public static void load() {
        final ServerConfig config = ServerConfig.get();

        if (!config.getBoolean("dimensions.overworld", true)) {
            LOGGER.warn("config/{}: dimensions.overworld cannot be turned off — the server has "
                + "nowhere to put players without it. Carrying on with it enabled.",
                ServerConfig.FILE_NAME);
        }

        nether = config.getBoolean("dimensions.nether", true);
        end = config.getBoolean("dimensions.end", true);

        if (!nether || !end) {
            LOGGER.info("Dimensions disabled in config/{}: {}. Their worlds will not be loaded.",
                ServerConfig.FILE_NAME,
                !nether && !end ? "the Nether and the End" : (!nether ? "the Nether" : "the End"));
        }
    }

    /**
     * Whether a dimension should be created at start-up.
     *
     * @param stemKey the dimension's registry key
     * @return whether to load it
     */
    public static boolean isEnabled(final ResourceKey<LevelStem> stemKey) {
        if (LevelStem.NETHER.equals(stemKey)) {
            return nether;
        }
        if (LevelStem.END.equals(stemKey)) {
            return end;
        }
        return true;
    }

    /**
     * Whether a dimension is one this server has.
     *
     * <p>The counterpart of {@link #isEnabled} for everything that runs after start-up: portals,
     * respawns and teleports all need to know that the far side is not there. Phrased in terms of
     * the level key rather than the stem key because that is what those callers hold.</p>
     *
     * @param dimension the level key
     * @return whether that dimension was loaded
     */
    public static boolean exists(final ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return nether;
        }
        if (Level.END.equals(dimension)) {
            return end;
        }
        return true;
    }

    /** Whether the Nether was loaded. */
    public static boolean netherEnabled() {
        return nether;
    }

    /** Whether the End was loaded. */
    public static boolean endEnabled() {
        return end;
    }
}
