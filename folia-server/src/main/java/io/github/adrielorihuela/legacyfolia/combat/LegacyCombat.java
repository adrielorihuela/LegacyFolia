package io.github.adrielorihuela.legacyfolia.combat;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.phys.Vec3;

/**
 * The combat arithmetic 1.8.8 used, taken from its own source.
 *
 * <p>Each method here is the body of a method in {@code EntityLiving} or {@code EntityHuman} as it
 * stood in 1.8.8, with the obfuscated names spelled out and nothing else changed. They are kept
 * apart from the server so that what the game used to do can be checked against fixed numbers,
 * rather than only being observable by two people hitting each other.</p>
 *
 * <p>The reference sources are not shipped with this repository — they are Mojang's, decompiled,
 * and not ours to redistribute. {@code reference/fetch-1.8.8-source.sh} reproduces them from the
 * exact Spigot build these formulas were read from, for anybody who wants to check the fidelity
 * themselves.</p>
 */
public final class LegacyCombat {

    private LegacyCombat() {
    }

    /**
     * The knockback 1.8.8 applied, from {@code EntityLiving.a(Entity, float, double, double)}.
     *
     * <p>Two things differ from what the game does now, and between them they are most of why
     * combat feels different.</p>
     *
     * <p>The first is that the vertical component is applied always. Modern knockback only touches
     * the Y velocity of a target that is on the ground, so hitting somebody already in the air does
     * not lift them; in 1.8 it does, every time, which is what makes a combo hold somebody up.</p>
     *
     * <p>The second is that the horizontal push is a flat 0.4 rather than a strength passed in by
     * the caller, so it does not vary with the weapon or the attack.</p>
     *
     * @param current the target's velocity before the hit
     * @param dx how far the target is from the attacker along x
     * @param dz how far the target is from the attacker along z
     * @param config the strengths to use
     * @return the velocity to set
     */
    public static Vec3 knockback(final Vec3 current, final double dx, final double dz,
                                 final CombatConfig config) {
        final double magnitude = Math.sqrt(dx * dx + dz * dz);
        if (magnitude < 1.0E-5) {
            // 1.8 divided by this without guarding it, which produced a NaN velocity and a
            // rubber-banding player. Standing exactly on top of somebody is rare but reachable,
            // and a divide by zero is not a mechanic worth reproducing.
            return current;
        }

        final double horizontal = config.knockbackHorizontal();
        final double x = current.x / 2.0 - dx / magnitude * horizontal;
        final double z = current.z / 2.0 - dz / magnitude * horizontal;
        double y = current.y / 2.0 + config.knockbackVertical();
        if (y > config.knockbackVerticalLimit()) {
            y = config.knockbackVerticalLimit();
        }
        return new Vec3(x, y, z);
    }

    /**
     * The extra push a sprinting attacker added, from {@code EntityHuman.attack}.
     *
     * <p>1.8 added this on top of the knockback above rather than folding it in, and then halved
     * the attacker's own horizontal velocity and cancelled their sprint. That is the w-tap: a
     * sprint hit throws the target further, and costs the attacker their sprint until they build
     * it again.</p>
     *
     * @param yawDegrees which way the attacker is facing
     * @param level the knockback enchantment level, plus one for sprinting
     * @param config the strength to use
     * @return the velocity to add to the target
     */
    public static Vec3 sprintKnockback(final float yawDegrees, final int level,
                                       final CombatConfig config) {
        final double radians = yawDegrees * Math.PI / 180.0;
        final double strength = level * config.sprintKnockbackStrength();
        return new Vec3(-Math.sin(radians) * strength, 0.1, Math.cos(radians) * strength);
    }

    /** What the attacker's own horizontal velocity is multiplied by after a sprint hit. */
    public static final double SPRINT_ATTACKER_SLOWDOWN = 0.6;

    /**
     * Whether a hit is a critical, by 1.8's conditions.
     *
     * <p>Unchanged in substance since: falling, not on the ground, not on a ladder, not in water,
     * not blinded and not riding anything. It is spelled out here so that the multiplier can be
     * configured without also having to reimplement the test.</p>
     *
     * @param falling whether the attacker has fallen any distance
     * @param onGround whether they are standing on something
     * @param onLadder whether they are on a ladder
     * @param inWater whether they are in water
     * @param blinded whether they have blindness
     * @param riding whether they are riding something
     * @return whether the hit crits
     */
    public static boolean isCritical(final boolean falling, final boolean onGround,
                                     final boolean onLadder, final boolean inWater,
                                     final boolean blinded, final boolean riding) {
        return falling && !onGround && !onLadder && !inWater && !blinded && !riding;
    }

    /**
     * Whether a hit lands while the target is still invulnerable from the last one.
     *
     * <p>1.8 let a stronger hit through during the invulnerable window, for the difference between
     * the two rather than its full value. Without that, holding down attack against somebody who
     * has just been tapped for one heart does nothing at all until the window passes, which is not
     * how that version played.</p>
     *
     * @param damage the damage this hit would do
     * @param lastDamage the damage of the hit that started the window
     * @param config whether overriding is enabled
     * @return how much damage to actually apply, or zero for none
     */
    public static float damageThroughInvulnerability(final float damage, final float lastDamage,
                                                     final CombatConfig config) {
        if (!config.damageOverriding() || damage <= lastDamage) {
            return 0.0F;
        }
        return damage - lastDamage;
    }

    /**
     * The damage left after armour, from {@code EntityLiving.applyArmorModifier}.
     *
     * <p>1.8 scaled the hit by {@code (25 - points) / 25} and nothing else. It is linear, it tops
     * out at eighty per cent off with a full twenty points, and — the part that matters — it is
     * completely independent of how big the hit was.</p>
     *
     * <p>Modern armour ({@code CombatRules.getDamageAfterAbsorb}) subtracts a term derived from the
     * damage and the armour's toughness from the effective armour, so a large hit punches through
     * armour that a small hit does not. That is why a diamond-armoured player survives a hail of
     * weak hits far better on 1.8 than on a modern server, and why one huge hit is comparably
     * worse there: the old curve does not care about the size of the blow at all.</p>
     *
     * @param damage the damage arriving at the armour step
     * @param armorPoints the target's armour points
     * @param config the divisor and cap to use
     * @return the damage that gets through
     */
    public static float afterArmor(final float damage, final int armorPoints,
                                   final CombatConfig config) {
        final int points = Math.clamp(armorPoints, 0, config.armorPointsCap());
        final float divisor = (float) config.armorDamageDivisor();
        return damage * (divisor - (float) points) / divisor;
    }

    /**
     * The protection points one piece of armour is worth, from {@code EnchantmentProtection.a}.
     *
     * <p>1.8 computed {@code (6 + level * level) / 3} and then scaled it by 0.75 for plain
     * Protection, rounding up. A piece is worth 2, 3, 4 or 6 points for levels one to four — note
     * that the step from III to IV is twice the step from II to III.</p>
     *
     * @param level the level of Protection on the piece
     * @param config the factor to scale by
     * @return the enchantment protection factor for that piece
     */
    public static int protectionPoints(final int level, final CombatConfig config) {
        if (level <= 0) {
            return 0;
        }
        final float base = (float) (6 + level * level) / 3.0F;
        return (int) Math.ceil(base * (float) config.protectionFactor());
    }

    /**
     * The protection actually rolled for one hit, from {@code EnchantmentManager.a}.
     *
     * <p>This is the part people are surprised by: in 1.8 protection was <em>random on every
     * hit</em>. The summed points are capped at 25, and then the game rolls
     * {@code ((points + 1) >> 1) + nextInt((points >> 1) + 1)} — half the points guaranteed plus up
     * to half again — and caps the result at 20. A full set of Protection IV is 24 points, which
     * rolls anywhere from 12 to 20, so the same armour takes between forty-eight and eighty per
     * cent off depending on the roll. Modern protection is a flat, deterministic figure.</p>
     *
     * <p>The randomness is passed in rather than taken from a static field so the roll can be
     * pinned to a fixed number in a test. The operator is a {@code nextInt(bound)}: given a
     * positive bound it returns a value in {@code [0, bound)}.</p>
     *
     * @param summedPoints the protection points of every worn piece added together
     * @param random a {@code nextInt(bound)}
     * @param config the caps to use
     * @return the protection points this particular hit is reduced by
     */
    public static int rollProtection(final int summedPoints, final IntUnaryOperator random,
                                     final CombatConfig config) {
        final int points = Math.clamp(summedPoints, 0, config.protectionPointsCap());
        final int rolled = ((points + 1) >> 1) + random.applyAsInt((points >> 1) + 1);
        return Math.min(rolled, config.protectionRollCap());
    }

    /**
     * The damage left after a protection roll, from {@code EntityLiving.applyMagicModifier}.
     *
     * <p>The same {@code (25 - points) / 25} curve as armour, applied a second time to the already
     * reduced figure. 1.8's order is armour first, then resistance, then this.</p>
     *
     * @param damage the damage arriving at the enchantment step
     * @param rolledPoints what {@link #rollProtection} returned
     * @param config the divisor and cap to use
     * @return the damage that gets through
     */
    public static float afterProtection(final float damage, final int rolledPoints,
                                        final CombatConfig config) {
        if (rolledPoints <= 0) {
            return damage;
        }
        final int points = Math.min(rolledPoints, config.protectionRollCap());
        final float divisor = (float) config.armorDamageDivisor();
        return damage * (divisor - (float) points) / divisor;
    }

    /**
     * The damage left after a raised sword, from the {@code blocking} modifier in
     * {@code EntityLiving.d(DamageSource, float)}.
     *
     * <p>1.8's formula is {@code (1.0F + damage) * 0.5F}, not {@code damage * 0.5F}. The extra half
     * a heart matters: a seven-damage hit blocks down to 4.0, not 3.5, and a one-damage hit is not
     * reduced at all. Treating blocking as a plain halving makes it look stronger than it was
     * against large hits and weaker against small ones.</p>
     *
     * <p>Where it sits also matters. 1.8 ran the damage modifiers in this order: hard hat,
     * <strong>blocking</strong>, armour, resistance, magic (protection), absorption. Blocking is
     * therefore applied to the raw hit and armour works on whatever blocking left, so the two
     * compound rather than being alternatives.</p>
     *
     * @param damage the raw damage of the hit
     * @param config the factor to use, 0.5 in 1.8
     * @return the damage that gets through
     */
    public static float afterBlocking(final float damage, final CombatConfig config) {
        if (damage <= 0.0F) {
            return damage;
        }
        return (1.0F + damage) * (float) config.swordBlockingReduction();
    }

    /**
     * The damage a sharpness enchantment adds, from {@code EnchantmentWeaponDamage.a}.
     *
     * <p>Flat: 1.25 per level, so Sharpness V is a straight +6.25 whatever the weapon. What makes
     * it behave differently from the modern enchantment is not the number but where it lands. 1.8
     * multiplied the weapon damage by 1.5 for a critical and <em>then</em> added this on top, so a
     * critical never amplified sharpness. Modern combat folds sharpness into the figure the
     * critical multiplies, and uses {@code 0.5 * level + 0.5}, which is far less at high levels.</p>
     *
     * @param level the sharpness level
     * @param config the per-level bonus
     * @return the damage to add after the critical multiplier
     */
    public static float sharpnessBonus(final int level, final CombatConfig config) {
        return level <= 0 ? 0.0F : (float) ((double) level * config.sharpnessPerLevel());
    }

    /**
     * A hit's damage put together the way {@code EntityHuman.attack} did it.
     *
     * <p>Spelled out because the order is the whole point: the critical multiplier applies to the
     * weapon damage alone, and the enchantment bonus is added afterwards.</p>
     *
     * @param weaponDamage the attack damage attribute
     * @param critical whether the hit crits
     * @param sharpnessLevel the sharpness level on the weapon
     * @param config the multiplier and per-level bonus
     * @return the damage to hand to the damage pipeline
     */
    public static float attackDamage(final float weaponDamage, final boolean critical,
                                     final int sharpnessLevel, final CombatConfig config) {
        float damage = weaponDamage;
        if (critical && damage > 0.0F) {
            damage *= (float) config.criticalMultiplier();
        }
        return damage + sharpnessBonus(sharpnessLevel, config);
    }

    /** The base modern swords are built on, for comparison with {@code swordBaseDamage}. */
    public static final double MODERN_SWORD_BASE_DAMAGE = 3.0;

    /**
     * What a sword's attack damage modifier is, from {@code ItemSword}'s constructor.
     *
     * <p>1.8 gave every sword {@code 4.0F + material bonus}; modern swords are built on a base of
     * 3.0 with the identical tier bonuses (wood 0, stone 1, iron 2, diamond 3, gold 0). So every
     * sword carries exactly one point more on 1.8 than it does now — a diamond sword's modifier is
     * 7.0 there against 6.0 today — on top of the player's own base attack damage of 1.0. The tiers
     * themselves are unchanged, so only the base differs. Netherite has no 1.8 counterpart and
     * simply follows the same rule.</p>
     *
     * @param tierBonus the material's attack damage bonus
     * @param config the base to add it to
     * @return the attack damage modifier the sword should carry
     */
    public static double swordAttackDamage(final double tierBonus, final CombatConfig config) {
        return config.swordBaseDamage() + tierBonus;
    }

    /**
     * How long fire aspect sets a target alight for, from {@code EntityHuman.attack}.
     *
     * <p>One second, and — the part worth restoring — 1.8 lit the target <em>before</em> calling
     * {@code damageEntity}. Two things follow. A hit that is entirely blocked, or that lands inside
     * the invulnerability window and does nothing at all, still sets the target on fire. And the
     * burning starts before the hit resolves rather than after. Modern combat ignites once the
     * damage is confirmed, and for four seconds per level rather than one flat second.</p>
     *
     * @param config the duration to use
     * @return the duration in seconds
     */
    public static int fireAspectSeconds(final CombatConfig config) {
        return config.fireAspectSeconds();
    }

    /**
     * Whether the target's own motion should be put back after the velocity packet is sent.
     *
     * <p>{@code EntityHuman.attack} saved the target's motion before the hit, sent them a velocity
     * packet, and then wrote the saved motion straight back onto the server-side entity. The client
     * moves; the server's copy does not. That deliberate desync is what 1.8 knockback felt like,
     * and its absence is a good part of why modern knockback feels heavier and less floaty.</p>
     *
     * @param config whether the restore is enabled
     * @return whether to put the pre-hit motion back
     */
    public static boolean restoresVelocity(final CombatConfig config) {
        return config.restoreVelocityAfterKnockback();
    }

    /**
     * Whether a player regenerates health, from {@code FoodMetaData.a}.
     *
     * <p>1.8 had one track and one only: food at 18 or more, one heart every 80 ticks, three
     * exhaustion each time. Modern food added a second, much faster track that spends saturation to
     * heal every 10 ticks while the food bar is full. That track did not exist, and it is why a
     * modern player who has just eaten a golden carrot heals back through a fight in a way a 1.8
     * player never could.</p>
     *
     * @param foodLevel the current food level
     * @param hurt whether the player is below full health
     * @param naturalRegeneration whether the game rule allows it
     * @param config the threshold to use
     * @return whether a heart should be regenerated once the interval elapses
     */
    public static boolean regenerates(final int foodLevel, final boolean hurt,
                                      final boolean naturalRegeneration,
                                      final CombatConfig config) {
        return naturalRegeneration && hurt && foodLevel >= config.regenFoodThreshold();
    }

    /**
     * Whether the modern saturation-driven fast regeneration track is allowed to run.
     *
     * @param config whether the fast track is enabled
     * @return whether it may run, false under 1.8 rules
     */
    public static boolean fastRegenerationAllowed(final CombatConfig config) {
        return !config.legacyRegeneration() || config.saturatedFastRegen();
    }

    /**
     * The exhaustion a jump costs, from {@code EntityHuman}'s jump.
     *
     * <p>A sprinting jump costs four times what a standing one does, which is the whole reason
     * bunny-hopping across a map empties a food bar.</p>
     *
     * @param sprinting whether the player was sprinting
     * @param config the costs to use
     * @return the exhaustion to apply
     */
    public static float jumpExhaustion(final boolean sprinting, final CombatConfig config) {
        return (float) (sprinting ? config.sprintJumpExhaustion() : config.jumpExhaustion());
    }

    /**
     * The health an instant potion restores, from {@code MobEffectList.applyInstantEffect}.
     *
     * <p>{@code 4 << amplifier}, scaled by how much of the splash reached the target and rounded to
     * the nearest whole point. Instant Health I heals 4 and II heals 8 — a shift, not a multiply,
     * which is why the second level is exactly double.</p>
     *
     * @param amplifier the effect amplifier, zero for level one
     * @param intensity how much of the potion landed, 1.0 for a drunk potion
     * @param config the base to shift
     * @return the health restored
     */
    public static int instantHeal(final int amplifier, final double intensity,
                                  final CombatConfig config) {
        return (int) (intensity * (double) (config.instantHealBase() << amplifier) + 0.5D);
    }

    /**
     * The damage an instant harming potion does, from {@code MobEffectList.applyInstantEffect}.
     *
     * <p>{@code 6 << amplifier}: Harming I does 6 and II does 12. Half again as much as healing
     * restores, which is what made a harming splash a real weapon in 1.8 rather than a curiosity.
     * Undead swap the two effects round; that test belongs at the server hook, not here.</p>
     *
     * @param amplifier the effect amplifier, zero for level one
     * @param intensity how much of the splash landed
     * @param config the base to shift
     * @return the damage dealt
     */
    public static int instantDamage(final int amplifier, final double intensity,
                                    final CombatConfig config) {
        return (int) (intensity * (double) (config.instantDamageBase() << amplifier) + 0.5D);
    }

    /**
     * How much of a splash potion reaches somebody, from {@code EntityPotion.a}.
     *
     * <p>1.8 wrote this as {@code 1 - sqrt(d) / 4} where {@code d} is the <em>squared</em> distance,
     * which is the same as {@code 1 - distance / 4}; it is spelled the source's way here so the
     * hook can pass the squared distance it already has and cannot get the units wrong. It falls to
     * nothing at four blocks, and at two blocks a potion still lands at half strength. The entity
     * the potion actually struck always takes it at full strength, which is the caller's decision
     * rather than this method's.</p>
     *
     * @param distanceSquared the squared distance from where the potion broke
     * @param config the radius to use
     * @return the fraction that lands, between zero and one
     */
    public static double splashIntensity(final double distanceSquared, final CombatConfig config) {
        final double radius = config.splashRadius();
        if (distanceSquared >= radius * radius) {
            return 0.0D;
        }
        return Math.max(0.0D, 1.0D - Math.sqrt(distanceSquared) / radius);
    }
}
