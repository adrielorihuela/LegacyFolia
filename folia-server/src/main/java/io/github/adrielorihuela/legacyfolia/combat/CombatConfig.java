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
 * @param restoreVelocityAfterKnockback whether the target's own motion is put back after the
 *     velocity packet is sent, as 1.8 did
 * @param legacyCriticals whether a critical is judged by 1.8's conditions
 * @param criticalMultiplier what a critical multiplies damage by, 1.5 in 1.8
 * @param invulnerabilityTicks how long after a hit another cannot land, 20 in 1.8
 * @param damageOverriding whether a stronger hit lands during those ticks for the difference
 * @param swordBlocking whether right-clicking a sword blocks
 * @param swordBlockingReduction the factor in 1.8's {@code (1 + damage) * 0.5}, so 0.5
 * @param disableShields whether shields stop working, as they did not exist in 1.8
 * @param disableOffhand whether the off-hand slot is refused, as it did not exist in 1.8
 * @param legacyArmor whether armour reduces damage by 1.8's flat linear formula
 * @param armorDamageDivisor the constant damage is scaled against, 25 in 1.8
 * @param armorPointsCap the most armour points that can count, 20 in 1.8
 * @param legacyProtection whether protection is 1.8's capped, randomised roll
 * @param protectionFactor what the per-piece protection figure is scaled by, 0.75 in 1.8
 * @param protectionPointsCap the ceiling on summed protection points before the roll, 25 in 1.8
 * @param protectionRollCap the ceiling on the rolled result, 20 in 1.8
 * @param legacySharpness whether sharpness is a flat bonus added after the critical multiplier
 * @param sharpnessPerLevel the damage each level of sharpness adds, 1.25 in 1.8
 * @param legacySwordDamage whether swords use 1.8's base damage
 * @param swordBaseDamage what a sword's tier bonus is added to, 4.0 in 1.8 against 3.0 now
 * @param fireAspectBeforeDamage whether fire aspect ignites before the hit is resolved
 * @param fireAspectSeconds how long fire aspect sets the target alight for, 1 in 1.8
 * @param legacyRegeneration whether health regeneration uses 1.8's single slow track
 * @param regenIntervalTicks how many ticks between regenerated hearts, 80 in 1.8
 * @param regenFoodThreshold the food level regeneration needs, 18 in 1.8
 * @param saturatedFastRegen whether the modern saturation-driven fast track exists
 * @param attackExhaustion the exhaustion a landed hit costs, 0.3 in 1.8
 * @param regenExhaustion the exhaustion regenerating a heart costs, 3.0 in 1.8
 * @param sprintJumpExhaustion the exhaustion a sprinting jump costs, 0.8 in 1.8
 * @param jumpExhaustion the exhaustion an ordinary jump costs, 0.2 in 1.8
 * @param legacyInstantPotions whether instant health and harming use 1.8's shifts
 * @param instantDamageBase what harming shifts left by the amplifier, 6 in 1.8
 * @param instantHealBase what healing shifts left by the amplifier, 4 in 1.8
 * @param splashRadius the radius over which a splash potion weakens to nothing, 4.0 in 1.8
 */
public record CombatConfig(boolean enabled, boolean noAttackCooldown, boolean noSweepAttack,
                           boolean legacyKnockback, double knockbackHorizontal,
                           double knockbackVertical, double knockbackVerticalLimit,
                           boolean sprintKnockback, double sprintKnockbackStrength,
                           boolean restoreVelocityAfterKnockback,
                           boolean legacyCriticals, double criticalMultiplier,
                           int invulnerabilityTicks, boolean damageOverriding,
                           boolean swordBlocking, double swordBlockingReduction,
                           boolean disableShields, boolean disableOffhand,
                           boolean legacyArmor, double armorDamageDivisor, int armorPointsCap,
                           boolean legacyProtection, double protectionFactor,
                           int protectionPointsCap, int protectionRollCap,
                           boolean legacySharpness, double sharpnessPerLevel,
                           boolean legacySwordDamage, double swordBaseDamage,
                           boolean fireAspectBeforeDamage, int fireAspectSeconds,
                           boolean legacyRegeneration, int regenIntervalTicks,
                           int regenFoodThreshold, boolean saturatedFastRegen,
                           double attackExhaustion, double regenExhaustion,
                           double sprintJumpExhaustion, double jumpExhaustion,
                           boolean legacyInstantPotions, int instantDamageBase,
                           int instantHealBase, double splashRadius) {

    private static final String PREFIX = "combat.";

    /** Exactly what 1.8.8 did, which is what the module is for. */
    public static CombatConfig vanilla18(final boolean enabled) {
        return new CombatConfig(enabled, true, true, true, 0.4, 0.4, 0.4, true, 0.5, true,
            true, 1.5, 20, true, true, 0.5, true, true,
            true, 25.0, 20,
            true, 0.75, 25, 20,
            true, 1.25,
            true, 4.0,
            true, 1,
            true, 80, 18, false,
            0.3, 3.0, 0.8, 0.2,
            true, 6, 4, 4.0);
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
            ServerConfig.getBoolean(PREFIX + "restore-velocity-after-knockback", defaults.restoreVelocityAfterKnockback()),
            ServerConfig.getBoolean(PREFIX + "legacy-criticals", defaults.legacyCriticals()),
            ServerConfig.getDouble(PREFIX + "critical-multiplier", defaults.criticalMultiplier()),
            ServerConfig.getInt(PREFIX + "invulnerability-ticks", defaults.invulnerabilityTicks()),
            ServerConfig.getBoolean(PREFIX + "damage-overriding", defaults.damageOverriding()),
            ServerConfig.getBoolean(PREFIX + "sword-blocking", defaults.swordBlocking()),
            ServerConfig.getDouble(PREFIX + "sword-blocking-reduction", defaults.swordBlockingReduction()),
            ServerConfig.getBoolean(PREFIX + "disable-shields", defaults.disableShields()),
            ServerConfig.getBoolean(PREFIX + "disable-offhand", defaults.disableOffhand()),
            ServerConfig.getBoolean(PREFIX + "legacy-armor", defaults.legacyArmor()),
            ServerConfig.getDouble(PREFIX + "armor-damage-divisor", defaults.armorDamageDivisor()),
            ServerConfig.getInt(PREFIX + "armor-points-cap", defaults.armorPointsCap()),
            ServerConfig.getBoolean(PREFIX + "legacy-protection", defaults.legacyProtection()),
            ServerConfig.getDouble(PREFIX + "protection-factor", defaults.protectionFactor()),
            ServerConfig.getInt(PREFIX + "protection-points-cap", defaults.protectionPointsCap()),
            ServerConfig.getInt(PREFIX + "protection-roll-cap", defaults.protectionRollCap()),
            ServerConfig.getBoolean(PREFIX + "legacy-sharpness", defaults.legacySharpness()),
            ServerConfig.getDouble(PREFIX + "sharpness-per-level", defaults.sharpnessPerLevel()),
            ServerConfig.getBoolean(PREFIX + "legacy-sword-damage", defaults.legacySwordDamage()),
            ServerConfig.getDouble(PREFIX + "sword-base-damage", defaults.swordBaseDamage()),
            ServerConfig.getBoolean(PREFIX + "fire-aspect-before-damage", defaults.fireAspectBeforeDamage()),
            ServerConfig.getInt(PREFIX + "fire-aspect-seconds", defaults.fireAspectSeconds()),
            ServerConfig.getBoolean(PREFIX + "legacy-regeneration", defaults.legacyRegeneration()),
            ServerConfig.getInt(PREFIX + "regen-interval-ticks", defaults.regenIntervalTicks()),
            ServerConfig.getInt(PREFIX + "regen-food-threshold", defaults.regenFoodThreshold()),
            ServerConfig.getBoolean(PREFIX + "saturated-fast-regen", defaults.saturatedFastRegen()),
            ServerConfig.getDouble(PREFIX + "attack-exhaustion", defaults.attackExhaustion()),
            ServerConfig.getDouble(PREFIX + "regen-exhaustion", defaults.regenExhaustion()),
            ServerConfig.getDouble(PREFIX + "sprint-jump-exhaustion", defaults.sprintJumpExhaustion()),
            ServerConfig.getDouble(PREFIX + "jump-exhaustion", defaults.jumpExhaustion()),
            ServerConfig.getBoolean(PREFIX + "legacy-instant-potions", defaults.legacyInstantPotions()),
            ServerConfig.getInt(PREFIX + "instant-damage-base", defaults.instantDamageBase()),
            ServerConfig.getInt(PREFIX + "instant-heal-base", defaults.instantHealBase()),
            ServerConfig.getDouble(PREFIX + "splash-radius", defaults.splashRadius()));
    }
}
