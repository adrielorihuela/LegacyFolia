package io.github.adrielorihuela.legacyfolia.world;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Which of the three vanilla dimensions the server creates at all.
 *
 * <p>A dimension that is turned off is never loaded: no world folder is read or written, no chunks
 * of it sit in memory, nothing in it ticks, and no region threads are spent on it. On a lobby or a
 * minigame server that never sends anybody to the Nether, that is the cheapest saving there is —
 * an empty server starts in about half the time with both of them off.</p>
 *
 * <p>The server could already do this, which is exactly the problem this fork exists to fix: the
 * Nether switch is {@code misc.enable-nether} in {@code config/paper-global.yml} and the End switch
 * is {@code settings.allow-end} in {@code bukkit.yml} — two files, two formats, two names, for one
 * decision. So this adds no third mechanism and no second code path. It presents both as one
 * {@code [dimensions]} section and answers on their behalf from
 * {@link org.bukkit.Server#getAllowNether()} and {@link org.bukkit.Server#getAllowEnd()}, which are
 * what upstream's world loader already consults and what plugins already ask. Everything downstream
 * — skipping the load, and the portals that then find no destination — is code the server already
 * had, already exercised by those settings.</p>
 *
 * <p>Only the vanilla Nether and End have switches. A dimension a datapack adds is always loaded:
 * it was added on purpose, and there is no sensible default for something we know nothing about.</p>
 */
public final class Dimensions {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String OVERWORLD_KEY = "dimensions.overworld";
    private static final String NETHER_KEY = "dimensions.nether";
    private static final String END_KEY = "dimensions.end";

    private static final Object LOCK = new Object();

    /**
     * Each dimension is settled on its own, from its own inherited setting.
     *
     * <p>Null until settled. They cannot share one resolution step: the two inherited values arrive
     * through different calls, from different files, and whichever was asked first would otherwise
     * decide the other one from a default it never actually read.</p>
     */
    private static volatile Boolean nether;
    private static volatile Boolean end;

    private static volatile boolean warnedAboutOverworld;

    private Dimensions() {
    }

    /**
     * Whether the Nether is one this server has.
     *
     * @param inherited what {@code misc.enable-nether} says
     * @return whether the Nether is enabled
     */
    public static boolean netherEnabled(final boolean inherited) {
        final Boolean settled = nether;
        return settled != null ? settled : settle(NETHER_KEY, inherited, "The Nether");
    }

    /**
     * Whether the End is one this server has.
     *
     * @param inherited what {@code settings.allow-end} says
     * @return whether the End is enabled
     */
    public static boolean endEnabled(final boolean inherited) {
        final Boolean settled = end;
        return settled != null ? settled : settle(END_KEY, inherited, "The End");
    }

    /**
     * Settles one dimension, once.
     *
     * <p>On a server whose {@code config/server.config} has just been created the direction is
     * reversed: whatever the old setting was is written into the new file, so upgrading a server
     * that turned the Nether off years ago never quietly switches it back on.</p>
     *
     * @param key the key in the unified file
     * @param inherited the value of the setting this one takes over from
     * @param label how to name the dimension in the log
     * @return whether the dimension is enabled
     */
    private static boolean settle(final String key, final boolean inherited, final String label) {
        synchronized (LOCK) {
            final Boolean alreadySettled = NETHER_KEY.equals(key) ? nether : end;
            if (alreadySettled != null) {
                return alreadySettled;
            }

            // Anything that asks before start-up has read the file gets the inherited answer, and
            // nothing is remembered, so the real answer is still settled once the file is there.
            if (!ServerConfig.isLoaded()) {
                return inherited;
            }

            final ServerConfig config = ServerConfig.get();
            final boolean enabled;

            if (ServerConfig.wasCreated()) {
                enabled = inherited;
                if (config.getBoolean(key, true) != inherited) {
                    config.set(key, inherited);
                    config.save();
                    LOGGER.info("{} keeps the setting it already had — config/{} now says {} = {}.",
                        label, ServerConfig.FILE_NAME, key, inherited);
                }
            } else {
                enabled = config.getBoolean(key, inherited);
                if (!enabled) {
                    LOGGER.info("{} is disabled in config/{}, so its world will not be loaded.",
                        label, ServerConfig.FILE_NAME);
                }
                warnIfOverworldDisabled(config);
            }

            if (NETHER_KEY.equals(key)) {
                nether = enabled;
            } else {
                end = enabled;
            }
            return enabled;
        }
    }

    private static void warnIfOverworldDisabled(final ServerConfig config) {
        if (warnedAboutOverworld || config.getBoolean(OVERWORLD_KEY, true)) {
            return;
        }
        warnedAboutOverworld = true;
        LOGGER.warn("config/{}: dimensions.overworld cannot be turned off — the server has nowhere "
            + "to put players without it. Carrying on with it enabled.", ServerConfig.FILE_NAME);
    }

    /**
     * Whether a dimension is one this server has.
     *
     * <p>For everything that runs after start-up: portals, respawns and teleports all need to know
     * that the far side is not there. By then both switches have long been settled.</p>
     *
     * @param dimension the level key
     * @return whether that dimension was loaded
     */
    public static boolean exists(final ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return nether == null || nether;
        }
        if (Level.END.equals(dimension)) {
            return end == null || end;
        }
        return true;
    }
}
