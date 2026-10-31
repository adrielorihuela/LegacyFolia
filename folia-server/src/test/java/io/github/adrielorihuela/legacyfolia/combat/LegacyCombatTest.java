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
        final CombatConfig noOverride = CombatConfigs.with(VANILLA, "damageOverriding", false);

        assertEquals(0.0F, LegacyCombat.damageThroughInvulnerability(20.0F, 1.0F, noOverride),
            0.0001F);
    }

    @Test
    @DisplayName("armour takes a flat fraction off, whatever the size of the hit")
    void armourIsLinearAndDamageIndependent() {
        // (25 - 20) / 25 = 0.2, so full diamond leaves a fifth of the hit. Modern armour would
        // leave more of the big hit and less of the small one; 1.8's curve does not look at it.
        assertEquals(2.0F, LegacyCombat.afterArmor(10.0F, 20, VANILLA), 0.0001F);
        assertEquals(20.0F, LegacyCombat.afterArmor(100.0F, 20, VANILLA), 0.0001F);
        assertEquals(0.2F, LegacyCombat.afterArmor(1.0F, 20, VANILLA), 0.0001F);
    }

    @Test
    @DisplayName("armour reduction is proportional to the points worn")
    void armourScalesWithPoints() {
        assertEquals(10.0F, LegacyCombat.afterArmor(10.0F, 0, VANILLA), 0.0001F, "no armour");
        assertEquals(6.0F, LegacyCombat.afterArmor(10.0F, 10, VANILLA), 0.0001F, "half a set");
        assertEquals(2.0F, LegacyCombat.afterArmor(10.0F, 25, VANILLA), 0.0001F,
            "capped at twenty points, so eighty per cent off is the floor");
    }

    @Test
    @DisplayName("a piece of protection is worth 1, 2, 3 or 5 points, rounded down as 1.8 did")
    void protectionPointsPerPiece() {
        // floor((6 + level^2) / 3 * 0.75): 1.75, 2.5, 3.75 and 5.5 round down.
        assertEquals(0, LegacyCombat.protectionPoints(0, VANILLA));
        assertEquals(1, LegacyCombat.protectionPoints(1, VANILLA));
        assertEquals(2, LegacyCombat.protectionPoints(2, VANILLA));
        assertEquals(3, LegacyCombat.protectionPoints(3, VANILLA));
        assertEquals(5, LegacyCombat.protectionPoints(4, VANILLA),
            "the step from III to IV is twice the step from II to III");
    }

    @Test
    @DisplayName("the specialised protections count against their own damage with their own factor")
    void specialisedProtectionPoints() {
        assertEquals(0, LegacyCombat.specialisedProtectionPoints(0,
            LegacyCombat.FEATHER_FALLING_FACTOR));
        // Feather Falling: 2.33, 3.33, 5 and 7.33 times 2.5, rounded down.
        assertEquals(5, LegacyCombat.specialisedProtectionPoints(1,
            LegacyCombat.FEATHER_FALLING_FACTOR));
        assertEquals(8, LegacyCombat.specialisedProtectionPoints(2,
            LegacyCombat.FEATHER_FALLING_FACTOR));
        assertEquals(12, LegacyCombat.specialisedProtectionPoints(3,
            LegacyCombat.FEATHER_FALLING_FACTOR));
        assertEquals(18, LegacyCombat.specialisedProtectionPoints(4,
            LegacyCombat.FEATHER_FALLING_FACTOR));
        // Fire Protection IV: 7.33 * 1.25 = 9.17.
        assertEquals(9, LegacyCombat.specialisedProtectionPoints(4,
            LegacyCombat.FIRE_PROTECTION_FACTOR));
        // Blast and Projectile Protection: 1.5.
        assertEquals(3, LegacyCombat.specialisedProtectionPoints(1,
            LegacyCombat.BLAST_PROTECTION_FACTOR));
        assertEquals(11, LegacyCombat.specialisedProtectionPoints(4,
            LegacyCombat.PROJECTILE_PROTECTION_FACTOR));
        // Feather Falling IV boots and full Protection IV against a fall: 18 + 20, capped at 25.
        assertEquals(LegacyCombat.rollProtection(25, bound -> 0, VANILLA),
            LegacyCombat.rollProtection(18 + 4 * LegacyCombat.protectionPoints(4, VANILLA),
                bound -> 0, VANILLA));
    }

    @Test
    @DisplayName("protection is rolled fresh on every hit, so the same armour varies")
    void protectionIsRandomPerHit() {
        // A full set of Protection IV is 4 x 5 = 20 points.
        final int points = 4 * LegacyCombat.protectionPoints(4, VANILLA);
        assertEquals(20, points);

        // ((20 + 1) >> 1) = 10 guaranteed, plus nextInt((20 >> 1) + 1) = nextInt(11), so 10 to 20.
        assertEquals(10, LegacyCombat.rollProtection(points, bound -> 0, VANILLA), "worst roll");
        assertEquals(20, LegacyCombat.rollProtection(points, bound -> bound - 1, VANILLA),
            "best roll");
        assertEquals(16, LegacyCombat.rollProtection(points, bound -> 6, VANILLA), "a middling one");
        // 24 points, as a mix of pieces can reach, rolls 12 to 24 and is capped at twenty.
        assertEquals(20, LegacyCombat.rollProtection(24, bound -> bound - 1, VANILLA));
    }

    @Test
    @DisplayName("the roll asks for a bound of half the points plus one")
    void protectionRollBound() {
        final int[] seen = new int[1];
        LegacyCombat.rollProtection(24, bound -> {
            seen[0] = bound;
            return 0;
        }, VANILLA);

        assertEquals(13, seen[0]);
    }

    @Test
    @DisplayName("summed protection points are capped at 25 before the roll")
    void protectionPointsAreCapped() {
        assertEquals(LegacyCombat.rollProtection(25, bound -> 0, VANILLA),
            LegacyCombat.rollProtection(400, bound -> 0, VANILLA));
    }

    @Test
    @DisplayName("the rolled protection takes the same (25 - points) / 25 off")
    void protectionUsesTheArmourCurve() {
        assertEquals(10.0F, LegacyCombat.afterProtection(10.0F, 0, VANILLA), 0.0001F);
        assertEquals(5.2F, LegacyCombat.afterProtection(10.0F, 12, VANILLA), 0.0001F,
            "the worst roll off a full set of Protection IV");
        assertEquals(2.0F, LegacyCombat.afterProtection(10.0F, 20, VANILLA), 0.0001F,
            "the best one");
    }

    @Test
    @DisplayName("blocking is (1 + damage) * 0.5, so a hit of seven leaves four")
    void blockingAddsTheHalfHeart() {
        // The difference from a plain halving is exactly half a point, and it is the whole reason
        // this has its own method rather than being a multiplier.
        assertEquals(4.0F, LegacyCombat.afterBlocking(7.0F, VANILLA), 0.0001F);
        assertEquals(1.0F, LegacyCombat.afterBlocking(1.0F, VANILLA), 0.0001F,
            "a one-damage hit is not reduced at all");
        assertEquals(0.0F, LegacyCombat.afterBlocking(0.0F, VANILLA), 0.0001F,
            "1.8 required the damage to be above zero, so blocking never invents half a heart");
    }

    @Test
    @DisplayName("blocking works on what the window and the hard hat left, in double")
    void blockingAfterTheWindow() {
        // A 7 then an 8 inside the window: 1.8 blocks the difference, (1 + 1) * 0.5 = 1. Blocking
        // the raw hits first would have given 4.5 - 4 = 0.5.
        final float through = LegacyCombat.damageThroughInvulnerability(8.0F, 7.0F, VANILLA);
        assertEquals(1.0F, through, 0.0001F);
        assertEquals(1.0, LegacyCombat.afterBlocking((double) through, VANILLA), EPSILON);
        // An anvil of 12 on a helmeted, blocking player: hard hat first, 9, then (1 + 9) * 0.5.
        assertEquals(5.0, LegacyCombat.afterBlocking(12.0 * 0.75, VANILLA), EPSILON);
        // The modifier is computed in double, as CraftBukkit's was.
        assertEquals((1.0 + 0.1) * 0.5, LegacyCombat.afterBlocking(0.1, VANILLA), 0.0);
    }

    @Test
    @DisplayName("sharpness is a flat 1.25 a level")
    void sharpnessIsFlat() {
        assertEquals(0.0F, LegacyCombat.sharpnessBonus(0, VANILLA), 0.0001F);
        assertEquals(1.25F, LegacyCombat.sharpnessBonus(1, VANILLA), 0.0001F);
        assertEquals(6.25F, LegacyCombat.sharpnessBonus(5, VANILLA), 0.0001F,
            "against 3.0 for the modern enchantment at the same level");
    }

    @Test
    @DisplayName("a critical multiplies the weapon damage but not the sharpness on top of it")
    void criticalsDoNotAmplifySharpness() {
        // A seven-damage weapon, Sharpness V, critical: 7 * 1.5 = 10.5, then + 6.25.
        assertEquals(16.75F, LegacyCombat.attackDamage(7.0F, true, 5, VANILLA), 0.0001F);
        assertEquals(13.25F, LegacyCombat.attackDamage(7.0F, false, 5, VANILLA), 0.0001F);

        // Folding sharpness in before the multiplier, as modern combat does, would give 19.875.
        assertEquals(19.875F, (7.0F + 6.25F) * 1.5F, 0.0001F);
    }

    /** A player's own attack damage, 1.0 in 1.8 ({@code EntityHuman}) and now. */
    private static final double FIST = 1.0;

    @Test
    @DisplayName("each 1.8 weapon hits for what ItemSword and ItemTool gave it")
    void weaponsHitFor18Damage() {
        // Material bonuses from EnumToolMaterial: wood 0, stone 1, iron 2, diamond (EMERALD) 3,
        // gold 0. Totals as 1.8 computed them: the player's 1.0 plus the item's modifier.
        final double[] bonus = {0.0, 1.0, 2.0, 0.0, 3.0};
        final double[] sword = {5.0, 6.0, 7.0, 5.0, 8.0};
        final double[] axe = {4.0, 5.0, 6.0, 4.0, 7.0};
        final double[] pickaxe = {3.0, 4.0, 5.0, 3.0, 6.0};
        final double[] shovel = {2.0, 3.0, 4.0, 2.0, 5.0};
        final String[] material = {"wood", "stone", "iron", "gold", "diamond"};
        for (int i = 0; i < bonus.length; i++) {
            assertEquals(sword[i], FIST + LegacyCombat.weaponAttackDamage(
                LegacyCombat.WeaponKind.SWORD, bonus[i], VANILLA), EPSILON, material[i] + " sword");
            assertEquals(axe[i], FIST + LegacyCombat.weaponAttackDamage(
                LegacyCombat.WeaponKind.AXE, bonus[i], VANILLA), EPSILON, material[i] + " axe");
            assertEquals(pickaxe[i], FIST + LegacyCombat.weaponAttackDamage(
                LegacyCombat.WeaponKind.PICKAXE, bonus[i], VANILLA), EPSILON, material[i] + " pickaxe");
            assertEquals(shovel[i], FIST + LegacyCombat.weaponAttackDamage(
                LegacyCombat.WeaponKind.SHOVEL, bonus[i], VANILLA), EPSILON, material[i] + " shovel");
        }
    }

    @Test
    @DisplayName("the axe hit for less than the sword in 1.8, the reverse of now")
    void axesWereWeakerThanSwords() {
        // 26.3: a diamond sword is 1 + 3 + 3 = 7 and a diamond axe 1 + 5 + 3 = 9.
        final double sword = FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.SWORD, 3.0, VANILLA);
        final double axe = FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.AXE, 3.0, VANILLA);
        assertEquals(8.0, sword, EPSILON, "one more than the modern 7");
        assertEquals(7.0, axe, EPSILON, "two less than the modern 9");
        assertTrue(axe < sword);
    }

    @Test
    @DisplayName("copper and netherite follow 1.8's rule with their modern material bonus")
    void newMaterialsFollowTheRule() {
        assertEquals(6.0, FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.SWORD, 1.0, VANILLA), EPSILON, "copper sword, as stone");
        assertEquals(9.0, FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.SWORD, 4.0, VANILLA), EPSILON, "netherite sword");
        assertEquals(8.0, FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.AXE, 4.0, VANILLA), EPSILON, "netherite axe");
        assertEquals(7.0, FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.PICKAXE, 4.0, VANILLA), EPSILON, "netherite pickaxe");
        assertEquals(6.0, FIST + LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.SHOVEL, 4.0, VANILLA), EPSILON, "netherite shovel");
    }

    @Test
    @DisplayName("the attribute is worked out again with the other modifiers kept")
    void otherModifiersAreKept() {
        // Diamond sword, nothing else: 1 + 7.
        assertEquals(8.0, LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.0, 1.0), EPSILON);
        // Strength I adds 3 now: 1 + 3 + 7.
        assertEquals(11.0, LegacyCombat.attackDamageWith(1.0, 3.0, 7.0, 0.0, 1.0), EPSILON);
        // A plugin's +50% of the base and x2 of the total apply to the new figure, not the old.
        assertEquals((1.0 + 7.0) * 1.5 * 2.0,
            LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.5, 2.0), EPSILON);
    }

    @Test
    @DisplayName("Strength multiplies the whole figure and Weakness takes half a point a level")
    void strengthAndWeaknessAsIn18() {
        // Diamond sword (1 + 7) under Strength I: 8 * 2.3. Now it would be 1 + 7 + 3 = 11.
        assertEquals(8.0 * 2.3, LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.0, 1.0, 0, -1),
            EPSILON);
        // Strength II is 1 + 2.6.
        assertEquals(8.0 * 3.6, LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.0, 1.0, 1, -1),
            EPSILON);
        // A fist under Strength I.
        assertEquals(2.3, LegacyCombat.attackDamageWith(1.0, 0.0, 0.0, 0.0, 1.0, 0, -1), EPSILON);
        // A fist under Weakness I still hurts: 1 - 0.5. Now it is 1 - 4, clamped to nothing.
        assertEquals(0.5, LegacyCombat.attackDamageWith(1.0, 0.0, 0.0, 0.0, 1.0, -1, 0), EPSILON);
        // Weakness II on a diamond sword: 8 - 1.
        assertEquals(7.0, LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.0, 1.0, -1, 1), EPSILON);
        // Both: the subtraction comes first and the multiplier last, after a plugin's x2.
        assertEquals((8.0 - 0.5) * 2.0 * 2.3,
            LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.0, 2.0, 0, 0), EPSILON);
        // No effects is the plain recomputation.
        assertEquals(LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.5, 2.0),
            LegacyCombat.attackDamageWith(1.0, 0.0, 7.0, 0.5, 2.0, -1, -1), EPSILON);
        assertTrue(VANILLA.legacyStrength());
    }

    @Test
    @DisplayName("each base can be changed on its own")
    void basesAreConfigurable() {
        final CombatConfig modernSwords = CombatConfigs.with(VANILLA, "swordBaseDamage", 3.0);
        assertEquals(6.0, LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.SWORD, 3.0, modernSwords), EPSILON);
        assertEquals(6.0, LegacyCombat.weaponAttackDamage(
            LegacyCombat.WeaponKind.AXE, 3.0, modernSwords), EPSILON, "the axe is untouched");
    }

    @Test
    @DisplayName("instant potions shift rather than multiply, so level two is exactly double")
    void instantPotionsShift() {
        assertEquals(6, LegacyCombat.instantDamage(0, 1.0, VANILLA));
        assertEquals(12, LegacyCombat.instantDamage(1, 1.0, VANILLA));
        assertEquals(4, LegacyCombat.instantHeal(0, 1.0, VANILLA));
        assertEquals(8, LegacyCombat.instantHeal(1, 1.0, VANILLA));
    }

    @Test
    @DisplayName("a weakened splash rounds to the nearest whole point")
    void instantPotionsRound() {
        // 0.5 * 6 = 3.0, and (int) (3.0 + 0.5) = 3.
        assertEquals(3, LegacyCombat.instantDamage(0, 0.5, VANILLA));
        // 0.6 * 6 = 3.6, and (int) (3.6 + 0.5) = 4.
        assertEquals(4, LegacyCombat.instantDamage(0, 0.6, VANILLA));
    }

    @Test
    @DisplayName("a splash weakens linearly with distance and reaches nothing at four blocks")
    void splashFalloff() {
        assertEquals(1.0, LegacyCombat.splashIntensity(0.0, VANILLA), EPSILON, "at the impact");
        assertEquals(0.5, LegacyCombat.splashIntensity(4.0, VANILLA), EPSILON,
            "two blocks away, given as the squared distance");
        assertEquals(0.0, LegacyCombat.splashIntensity(16.0, VANILLA), EPSILON, "four blocks");
        assertEquals(0.0, LegacyCombat.splashIntensity(400.0, VANILLA), EPSILON, "well outside");
    }

    @Test
    @DisplayName("regeneration needs food at 18, and the fast saturation track does not exist")
    void regenerationIsTheSlowTrackOnly() {
        assertTrue(LegacyCombat.regenerates(18, true, true, VANILLA));
        assertFalse(LegacyCombat.regenerates(17, true, true, VANILLA), "below the threshold");
        assertFalse(LegacyCombat.regenerates(20, false, true, VANILLA), "already at full health");
        assertFalse(LegacyCombat.regenerates(20, true, false, VANILLA), "the game rule is off");

        assertFalse(LegacyCombat.fastRegenerationAllowed(VANILLA),
            "the saturation-driven track every ten ticks was added after 1.8");
    }

    @Test
    @DisplayName("a sprinting jump costs four times a standing one")
    void jumpExhaustion() {
        assertEquals(0.8F, LegacyCombat.jumpExhaustion(true, VANILLA), 0.0001F);
        assertEquals(0.2F, LegacyCombat.jumpExhaustion(false, VANILLA), 0.0001F);
    }

    @Test
    @DisplayName("fire aspect burns for a second, and the velocity restore is on")
    void fireAspectAndVelocity() {
        assertEquals(1, LegacyCombat.fireAspectSeconds(VANILLA),
            "one flat second, against four a level in modern combat");
        assertTrue(VANILLA.fireAspectBeforeDamage(), "1.8 ignited before resolving the hit");
        assertTrue(LegacyCombat.restoresVelocity(VANILLA));
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

        assertEquals(25.0, config.armorDamageDivisor(), EPSILON);
        assertEquals(20, config.armorPointsCap());
        assertEquals(0.75, config.protectionFactor(), EPSILON);
        assertEquals(25, config.protectionPointsCap());
        assertEquals(20, config.protectionRollCap());
        assertEquals(1.25, config.sharpnessPerLevel(), EPSILON);
        assertEquals(4.0, config.swordBaseDamage(), EPSILON);
        assertEquals(3.0, config.axeBaseDamage(), EPSILON);
        assertEquals(2.0, config.pickaxeBaseDamage(), EPSILON);
        assertEquals(1.0, config.shovelBaseDamage(), EPSILON);
        assertTrue(config.legacyWeaponDamage());
        assertEquals(1, config.fireAspectSeconds());
        assertEquals(80, config.regenIntervalTicks());
        assertEquals(18, config.regenFoodThreshold());
        assertFalse(config.saturatedFastRegen());
        assertEquals(0.3, config.attackExhaustion(), EPSILON);
        assertEquals(3.0, config.regenExhaustion(), EPSILON);
        assertEquals(0.8, config.sprintJumpExhaustion(), EPSILON);
        assertEquals(0.2, config.jumpExhaustion(), EPSILON);
        assertEquals(6, config.instantDamageBase());
        assertEquals(4, config.instantHealBase());
        assertEquals(4.0, config.splashRadius(), EPSILON);
    }
}
