package io.github.adrielorihuela.legacyfolia.combat;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;

/**
 * The {@code [combat]} settings: how much of 1.8's combat to put back.
 *
 * <p>Every default here is the value 1.8.8 actually used, taken from its own source rather than
 * from memory, so turning the module on and changing nothing gives the combat that version had.
 * Each mechanic is separately switchable, because a server may want the old knockback without the
 * old attack speed, or the reverse.</p>
 *
 * @param enabled whether any of this applies at all
 * @param noAttackCooldown whether a hit always does full damage, as it did before 1.9
 * @param noSweepAttack whether the sweeping arc that hits everything nearby is removed
 * @param legacyKnockback whether knockback uses 1.8's formula, which lifts an airborne target
 * @param knockbackHorizontal the horizontal strength, 0.4 in 1.8
 * @param knockbackVertical the vertical strength, 0.4 in 1.8
 * @param knockbackVerticalLimit the ceiling on upward knockback, 0.4 in 1.8
 * @param sprintKnockback whether sprinting adds a level of knockback and is then cancelled
 * @param sprintKnockbackStrength the extra push per level, 0.5 in 1.8
 * @param legacyCriticals whether a critical is judged by 1.8's conditions
 * @param criticalMultiplier what a critical multiplies damage by, 1.5 in 1.8
 * @param invulnerabilityTicks how long after a hit another cannot land, 20 in 1.8
 * @param damageOverriding whether a stronger hit lands during those ticks for the difference
 * @param swordBlocking whether right-clicking a sword blocks
 * @param swordBlockingReduction the fraction of damage blocking removes, 0.5 in 1.8
 * @param disableShields whether shields stop working, as they did not exist in 1.8
 * @param disableOffhand whether the off-hand slot is refused, as it did not exist in 1.8
 */
public record CombatConfig(boolean enabled, boolean noAttackCooldown, boolean noSweepAttack,
                           boolean legacyKnockback, double knockbackHorizontal,
                           double knockbackVertical, double knockbackVerticalLimit,
                           boolean sprintKnockback, double sprintKnockbackStrength,
                           boolean legacyCriticals, double criticalMultiplier,
                           int invulnerabilityTicks, boolean damageOverriding,
                           boolean swordBlocking, double swordBlockingReduction,
                           boolean disableShields, boolean disableOffhand) {

    private static final String PREFIX = "combat.";

    /** Exactly what 1.8.8 did, which is what the module is for. */
    public static CombatConfig vanilla18(final boolean enabled) {
        return new CombatConfig(enabled, true, true, true, 0.4, 0.4, 0.4, true, 0.5, true, 1.5,
            20, true, true, 0.5, true, true);
    }

    /** The module off, for before the configuration exists. */
    public static CombatConfig off() {
        return vanilla18(false);
    }

    /**
     * Reads the settings from {@code config/combat.toml}.
     *
     * @return the configuration
     */
    public static CombatConfig load() {
        if (!ServerConfig.isLoaded()) {
            return off();
        }
        final CombatConfig defaults = vanilla18(true);
        return new CombatConfig(
            ServerConfig.getBoolean(PREFIX + "enabled", defaults.enabled()),
            ServerConfig.getBoolean(PREFIX + "no-attack-cooldown", defaults.noAttackCooldown()),
            ServerConfig.getBoolean(PREFIX + "no-sweep-attack", defaults.noSweepAttack()),
            ServerConfig.getBoolean(PREFIX + "legacy-knockback", defaults.legacyKnockback()),
            ServerConfig.getDouble(PREFIX + "knockback-horizontal", defaults.knockbackHorizontal()),
            ServerConfig.getDouble(PREFIX + "knockback-vertical", defaults.knockbackVertical()),
            ServerConfig.getDouble(PREFIX + "knockback-vertical-limit", defaults.knockbackVerticalLimit()),
            ServerConfig.getBoolean(PREFIX + "sprint-knockback", defaults.sprintKnockback()),
            ServerConfig.getDouble(PREFIX + "sprint-knockback-strength", defaults.sprintKnockbackStrength()),
            ServerConfig.getBoolean(PREFIX + "legacy-criticals", defaults.legacyCriticals()),
            ServerConfig.getDouble(PREFIX + "critical-multiplier", defaults.criticalMultiplier()),
            ServerConfig.getInt(PREFIX + "invulnerability-ticks", defaults.invulnerabilityTicks()),
            ServerConfig.getBoolean(PREFIX + "damage-overriding", defaults.damageOverriding()),
            ServerConfig.getBoolean(PREFIX + "sword-blocking", defaults.swordBlocking()),
            ServerConfig.getDouble(PREFIX + "sword-blocking-reduction", defaults.swordBlockingReduction()),
            ServerConfig.getBoolean(PREFIX + "disable-shields", defaults.disableShields()),
            ServerConfig.getBoolean(PREFIX + "disable-offhand", defaults.disableOffhand()));
    }
}
