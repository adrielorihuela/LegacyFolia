package io.github.adrielorihuela.legacyfolia.combat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The one place the combat settings are held, so the hooks scattered through the server are a
 * field read rather than a configuration lookup.
 *
 * <p>These sit on the hottest paths in the game — every hit, every knockback — so what they read
 * has to be a plain volatile field. Reading the configuration file from inside a damage
 * calculation would be a cost paid thousands of times a second for a value that changes twice a
 * day.</p>
 */
public final class Combat {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static volatile CombatConfig config = CombatConfig.off();

    private Combat() {
    }

    /**
     * The current settings.
     *
     * @return the settings, with everything off before the configuration has been read
     */
    public static CombatConfig config() {
        return config;
    }

    /** Re-reads the settings, at start-up and on reload. */
    public static void reload() {
        config = CombatConfig.load();
        if (config.enabled()) {
            LOGGER.info("1.8 combat is on: no attack cooldown, no sweep, and knockback that lifts "
                + "an airborne target. Every part of it is switchable under [combat] in "
                + "config/server.config.");
        }
    }
}
