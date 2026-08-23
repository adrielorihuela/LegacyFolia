package io.github.adrielorihuela.legacyfolia.combat;

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
}
