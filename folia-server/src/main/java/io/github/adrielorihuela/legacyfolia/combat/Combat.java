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

    /**
     * Whether this item can be raised to block, which in 1.8 meant a sword and nothing else.
     *
     * @param stack the item in hand
     * @return whether right-clicking it should raise it
     */
    public static boolean canBlockWith(final net.minecraft.world.item.ItemStack stack) {
        final CombatConfig current = config;
        return current.enabled() && current.swordBlocking()
            && stack.is(net.minecraft.tags.ItemTags.SWORDS);
    }

    /**
     * How much of an incoming hit a raised sword takes off.
     *
     * <p>Zero when the target is not blocking, so the caller multiplies unconditionally rather than
     * branching on the hot path.</p>
     *
     * @param target who is being hit
     * @return the fraction of the damage that still lands
     */
    public static float blockingMultiplier(final net.minecraft.world.entity.LivingEntity target) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.swordBlocking() || !target.isUsingItem()) {
            return 1.0F;
        }
        return target.getUseItem().is(net.minecraft.tags.ItemTags.SWORDS)
            ? (float) (1.0 - current.swordBlockingReduction()) : 1.0F;
    }

    /**
     * How long a living entity is invulnerable after a hit, in ticks.
     *
     * <p>Read for every entity as it is created, so it stays a field read. Twenty either way: the
     * window and the rule that a stronger hit still lands during it are both unchanged since 1.8,
     * and this only exists so a server can lengthen or shorten it.</p>
     *
     * @return the number of ticks
     */
    public static int invulnerableDuration() {
        final CombatConfig current = config;
        return current.enabled() ? current.invulnerabilityTicks() : 20;
    }

    /** Re-reads the settings, at start-up and on reload. */
    public static void reload() {
        config = CombatConfig.load();
        if (config.enabled()) {
            LOGGER.info("1.8 combat is on: no attack cooldown, no sweep, and knockback that lifts "
                + "an airborne target. Every part of it is switchable under [combat] in "
                + "config/combat.toml.");
        }
    }
}
