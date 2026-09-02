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
     * What is left of an incoming hit after a raised sword.
     *
     * <p>Returns the damage unchanged when the target is not blocking, so the caller assigns
     * unconditionally rather than branching on the hot path. See
     * {@link LegacyCombat#afterBlocking(float, CombatConfig)} for the formula and for the order the
     * modifiers ran in — blocking came before armour, so the two compound.</p>
     *
     * @param target who is being hit
     * @param damage the damage of the hit before blocking
     * @return the damage that gets through
     */
    public static float afterBlocking(final net.minecraft.world.entity.LivingEntity target,
                                      final float damage) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.swordBlocking() || !target.isUsingItem()
            || !target.getUseItem().is(net.minecraft.tags.ItemTags.SWORDS)) {
            return damage;
        }
        return LegacyCombat.afterBlocking(damage, current);
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

    /**
     * Whether armour should use 1.8's flat linear curve rather than the modern one.
     *
     * @return whether the old curve applies
     */
    public static boolean legacyArmor() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyArmor();
    }

    /**
     * Whether protection should be rolled per hit, as 1.8 did.
     *
     * @return whether the old roll applies
     */
    public static boolean legacyProtection() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyProtection();
    }

    /**
     * The protection points of every worn piece added together, before the roll.
     *
     * <p>1.8 counted only plain Protection towards this figure; the specialised protections had
     * their own paths and are left to the modern code, which never runs when this one does.</p>
     *
     * @param target who is being hit
     * @return the summed points
     */
    public static int protectionPoints(final net.minecraft.world.entity.LivingEntity target) {
        final CombatConfig current = config;
        int points = 0;
        for (final net.minecraft.world.entity.EquipmentSlot slot
            : net.minecraft.world.entity.EquipmentSlotGroup.ARMOR) {
            final net.minecraft.world.item.ItemStack piece = target.getItemBySlot(slot);
            if (piece.isEmpty()) {
                continue;
            }
            points += LegacyCombat.protectionPoints(enchantmentLevel(piece,
                net.minecraft.world.item.enchantment.Enchantments.PROTECTION), current);
        }
        return points;
    }

    /**
     * Whether this weapon's damage bonus should be 1.8's flat sharpness.
     *
     * <p>False for a weapon carrying no Sharpness, which leaves Smite, Bane of Arthropods and
     * anything a datapack adds on the modern path. Vanilla's damage enchantments are mutually
     * exclusive, so a weapon that has Sharpness has none of the others and nothing is lost.</p>
     *
     * @param weapon the weapon in hand
     * @return whether the old bonus applies
     */
    public static boolean legacySharpness(final net.minecraft.world.item.ItemStack weapon) {
        final CombatConfig current = config;
        return current.enabled() && current.legacySharpness()
            && enchantmentLevel(weapon, net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)
                > 0;
    }

    /**
     * The damage 1.8's sharpness adds to a hit with this weapon.
     *
     * @param weapon the weapon in hand
     * @return the flat bonus
     */
    public static float sharpnessBonus(final net.minecraft.world.item.ItemStack weapon) {
        final CombatConfig current = config;
        return LegacyCombat.sharpnessBonus(enchantmentLevel(weapon,
            net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), current);
    }

    private static int enchantmentLevel(final net.minecraft.world.item.ItemStack stack,
                                        final net.minecraft.resources.ResourceKey<
                                            net.minecraft.world.item.enchantment.Enchantment> key) {
        final net.minecraft.world.item.enchantment.ItemEnchantments enchantments =
            stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (final net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>
            enchantment : enchantments.keySet()) {
            if (enchantment.is(key)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    /**
     * Whether the modern saturation-driven fast healing track may run.
     *
     * @return whether it may, always true while the module is off
     */
    public static boolean fastRegenerationAllowed() {
        final CombatConfig current = config;
        return !current.enabled() || LegacyCombat.fastRegenerationAllowed(current);
    }

    /**
     * Whether the slow healing track should run this tick.
     *
     * @param foodLevel the player's food level
     * @param hurt whether they are below full health
     * @param naturalRegeneration whether the game rule allows healing at all
     * @return whether a heart is due once the interval elapses
     */
    public static boolean regenerates(final int foodLevel, final boolean hurt,
                                      final boolean naturalRegeneration) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.legacyRegeneration()) {
            return naturalRegeneration && hurt
                && foodLevel >= net.minecraft.world.food.FoodConstants.HEAL_LEVEL;
        }
        return LegacyCombat.regenerates(foodLevel, hurt, naturalRegeneration, current);
    }

    /**
     * How many ticks pass between regenerated hearts on the slow track.
     *
     * @param modern what the server would otherwise use
     * @return the interval in ticks
     */
    public static int regenIntervalTicks(final int modern) {
        final CombatConfig current = config;
        return current.enabled() && current.legacyRegeneration()
            ? current.regenIntervalTicks() : modern;
    }

    /**
     * The exhaustion regenerating a heart costs.
     *
     * @param modern what the server would otherwise use
     * @return the exhaustion
     */
    public static float regenExhaustion(final float modern) {
        final CombatConfig current = config;
        return current.enabled() ? (float) current.regenExhaustion() : modern;
    }

    /**
     * The exhaustion a landed hit costs.
     *
     * @param modern what the server would otherwise use
     * @return the exhaustion
     */
    public static float attackExhaustion(final float modern) {
        final CombatConfig current = config;
        return current.enabled() ? (float) current.attackExhaustion() : modern;
    }

    /**
     * The exhaustion a jump costs, which a sprinting jump multiplies.
     *
     * @param sprinting whether the player was sprinting
     * @param modern what the server would otherwise use
     * @return the exhaustion
     */
    public static float jumpExhaustion(final boolean sprinting, final float modern) {
        final CombatConfig current = config;
        return current.enabled() ? LegacyCombat.jumpExhaustion(sprinting, current) : modern;
    }

    /**
     * The health an instant healing effect restores.
     *
     * <p>The modern game already uses 1.8's {@code 4 << amplifier}; this exists so the base can be
     * changed rather than because the two disagree.</p>
     *
     * @param amplifier the effect amplifier, zero for level one
     * @param intensity how much of a splash landed, 1.0 for a drunk potion
     * @return the health restored
     */
    public static int instantHeal(final int amplifier, final double intensity) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.legacyInstantPotions()) {
            return (int) (intensity * (double) (4 << amplifier) + 0.5D);
        }
        return LegacyCombat.instantHeal(amplifier, intensity, current);
    }

    /**
     * The damage an instant harming effect does.
     *
     * @param amplifier the effect amplifier, zero for level one
     * @param intensity how much of a splash landed, 1.0 for a drunk potion
     * @return the damage dealt
     */
    public static int instantDamage(final int amplifier, final double intensity) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.legacyInstantPotions()) {
            return (int) (intensity * (double) (6 << amplifier) + 0.5D);
        }
        return LegacyCombat.instantDamage(amplifier, intensity, current);
    }

    /**
     * How long this weapon's fire aspect should set a target alight for, before the hit.
     *
     * @param weapon the weapon in hand
     * @return the duration in seconds, or zero when the old behaviour does not apply
     */
    public static int fireAspectSeconds(final net.minecraft.world.item.ItemStack weapon) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.fireAspectBeforeDamage()
            || enchantmentLevel(weapon,
                net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT) <= 0) {
            return 0;
        }
        return LegacyCombat.fireAspectSeconds(current);
    }

    /**
     * Whether the target's own motion should be put back after the velocity packet is sent.
     *
     * @return whether to restore it
     */
    public static boolean restoresVelocity() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyKnockback()
            && LegacyCombat.restoresVelocity(current);
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
