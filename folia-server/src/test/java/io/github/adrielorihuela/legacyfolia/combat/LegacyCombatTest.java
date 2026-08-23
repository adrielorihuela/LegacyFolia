package io.github.adrielorihuela.legacyfolia.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the combat arithmetic against the numbers 1.8.8 produced, rather than against how it
 * feels. Whether it feels right is a question for two people in a server; whether it computes what
 * that version computed is a question with an answer.
 */
class LegacyCombatTest {

    private static final CombatConfig VANILLA = CombatConfig.vanilla18(true);
    private static final double EPSILON = 1.0E-9;

    @Test
    @DisplayName("an airborne target is still lifted, which is the difference that makes combos")
    void airborneTargetsAreStillLifted() {
        // Falling at -0.5, hit from due west. Modern knockback leaves the Y of an airborne target
        // alone; 1.8 halves it and adds 0.4, so a combo can hold somebody off the ground.
        final Vec3 result = LegacyCombat.knockback(new Vec3(0, -0.5, 0), 1, 0, VANILLA);

        assertEquals(-0.5 / 2.0 + 0.4, result.y, EPSILON,
            "the vertical component is applied whether or not the target is on the ground");
        assertTrue(result.y > 0, "which means a falling player is pushed back up");
    }

    @Test
    @DisplayName("upward knockback is capped at 0.4 however fast the target was already rising")
    void upwardKnockbackIsCapped() {
        final Vec3 result = LegacyCombat.knockback(new Vec3(0, 5.0, 0), 1, 0, VANILLA);

        assertEquals(0.4, result.y, EPSILON);
    }

    @Test
    @DisplayName("the horizontal push is a flat 0.4 away from the attacker")
    void horizontalPushIsFlat() {
        final Vec3 result = LegacyCombat.knockback(Vec3.ZERO, 1, 0, VANILLA);

        assertEquals(-0.4, result.x, EPSILON, "pushed away along the axis of the hit");
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    @DisplayName("existing horizontal velocity is halved before the push is added")
    void existingVelocityIsHalved() {
        final Vec3 result = LegacyCombat.knockback(new Vec3(1.0, 0, 2.0), 0, 1, VANILLA);

        assertEquals(0.5, result.x, EPSILON);
        assertEquals(1.0 - 0.4, result.z, EPSILON);
    }

    @Test
    @DisplayName("the push is normalised, so a distant target is not thrown further")
    void pushIsNormalised() {
        final Vec3 near = LegacyCombat.knockback(Vec3.ZERO, 1, 0, VANILLA);
        final Vec3 far = LegacyCombat.knockback(Vec3.ZERO, 100, 0, VANILLA);

        assertEquals(near.x, far.x, EPSILON);
    }

    @Test
    @DisplayName("standing exactly on top of somebody does not produce a NaN velocity")
    void degenerateDirectionIsRefused() {
        final Vec3 current = new Vec3(0.1, 0.2, 0.3);

        // 1.8 divided by zero here and rubber-banded the player. That is a bug, not a mechanic.
        assertSame(current, LegacyCombat.knockback(current, 0, 0, VANILLA));
    }

    @Test
    @DisplayName("a sprint hit pushes along the attacker's facing, at half a block per level")
    void sprintKnockbackFollowsTheAttackersFacing() {
        final Vec3 north = LegacyCombat.sprintKnockback(0F, 1, VANILLA);

        assertEquals(0.0, north.x, EPSILON);
        assertEquals(0.1, north.y, EPSILON, "1.8 added a small fixed lift with the sprint push");
        assertEquals(0.5, north.z, EPSILON);
    }

    @Test
    @DisplayName("knockback enchantment levels add to the sprint push")
    void knockbackLevelsStack() {
        final Vec3 one = LegacyCombat.sprintKnockback(0F, 1, VANILLA);
        final Vec3 three = LegacyCombat.sprintKnockback(0F, 3, VANILLA);

        assertEquals(one.z * 3, three.z, EPSILON);
    }

    @Test
    @DisplayName("a critical needs a fall, no ground, no ladder, no water, no blindness, no mount")
    void criticalConditions() {
        assertTrue(LegacyCombat.isCritical(true, false, false, false, false, false));
        assertFalse(LegacyCombat.isCritical(false, false, false, false, false, false), "not falling");
        assertFalse(LegacyCombat.isCritical(true, true, false, false, false, false), "on the ground");
        assertFalse(LegacyCombat.isCritical(true, false, true, false, false, false), "on a ladder");
        assertFalse(LegacyCombat.isCritical(true, false, false, true, false, false), "in water");
        assertFalse(LegacyCombat.isCritical(true, false, false, false, true, false), "blinded");
        assertFalse(LegacyCombat.isCritical(true, false, false, false, false, true), "riding");
    }

    @Test
    @DisplayName("a stronger hit lands during the invulnerable window, for the difference only")
    void strongerHitsOverride() {
        assertEquals(3.0F, LegacyCombat.damageThroughInvulnerability(8.0F, 5.0F, VANILLA), 0.0001F,
            "without this, tapping somebody for one heart makes them briefly immune to an axe");
    }

    @Test
    @DisplayName("an equal or weaker hit does nothing during the window")
    void weakerHitsDoNothing() {
        assertEquals(0.0F, LegacyCombat.damageThroughInvulnerability(5.0F, 5.0F, VANILLA), 0.0001F);
        assertEquals(0.0F, LegacyCombat.damageThroughInvulnerability(2.0F, 5.0F, VANILLA), 0.0001F);
    }

    @Test
    @DisplayName("turning overriding off makes the window absolute")
    void overridingCanBeTurnedOff() {
        final CombatConfig noOverride = new CombatConfig(true, true, true, true, 0.4, 0.4, 0.4,
            true, 0.5, true, 1.5, 20, false, true, 0.5, true, true);

        assertEquals(0.0F, LegacyCombat.damageThroughInvulnerability(20.0F, 1.0F, noOverride),
            0.0001F);
    }

    @Test
    @DisplayName("the defaults are the values 1.8.8 used")
    void defaultsAre18() {
        final CombatConfig config = CombatConfig.vanilla18(true);

        assertEquals(0.4, config.knockbackHorizontal(), EPSILON);
        assertEquals(0.4, config.knockbackVertical(), EPSILON);
        assertEquals(0.4, config.knockbackVerticalLimit(), EPSILON);
        assertEquals(0.5, config.sprintKnockbackStrength(), EPSILON);
        assertEquals(1.5, config.criticalMultiplier(), EPSILON);
        assertEquals(20, config.invulnerabilityTicks());
        assertEquals(0.5, config.swordBlockingReduction(), EPSILON);
        assertTrue(config.noAttackCooldown());
        assertTrue(config.noSweepAttack());
    }
}
