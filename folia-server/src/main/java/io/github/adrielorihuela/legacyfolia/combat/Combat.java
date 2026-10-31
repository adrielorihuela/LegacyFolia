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
     * @param vanilla the game's own window, returned untouched when the combat settings are off
     * @return the number of ticks
     */
    public static int invulnerableDuration(final int vanilla) {
        final CombatConfig current = config;
        return current.enabled() ? current.invulnerabilityTicks() : vanilla;
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
     * The attack damage of a melee hit, with the weapon in hand counted at what it was worth in
     * 1.8.
     *
     * <p>Read where the attack damage attribute is read for a melee hit, so it follows a reload and
     * leaves the item registry, every stored item and every packet exactly as they are. The
     * weapon's modifier is swapped only when all of this holds; otherwise the attribute comes back
     * untouched:</p>
     * <ul>
     *   <li>the setting is on;</li>
     *   <li>the weapon is a sword, axe, pickaxe or shovel of one of the seven materials;</li>
     *   <li>the stack carries no attribute modifiers of its own. A weapon a plugin, a command or a
     *       loot table gave modifiers to keeps them, as 1.8 kept an item's
     *       {@code AttributeModifiers} tag in place of the item's defaults;</li>
     *   <li>the modifier actually applied to the attacker is that item's own default, so a broken
     *       weapon, one swapped in this tick, or a modifier somebody put on the entity under the
     *       same id is left alone.</li>
     * </ul>
     * <p>Every other modifier, Strength and Weakness included, is kept, and the value is worked out
     * the way the attribute works it out and clamped to the attribute's range.</p>
     *
     * @param attacker who is swinging
     * @return the attack damage to use
     */
    public static double attackDamage(final net.minecraft.world.entity.LivingEntity attacker) {
        final double modern = attacker.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        final CombatConfig current = config;
        if (!current.enabled() || !current.legacyWeaponDamage()) {
            return modern;
        }
        final net.minecraft.world.item.ItemStack weapon = attacker.getMainHandItem();
        final Weapons.Entry entry = Weapons.TABLE.get(weapon.getItem());
        if (entry == null
            || weapon.hasNonDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS)) {
            return modern;
        }
        final net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
            attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return modern;
        }
        final net.minecraft.world.entity.ai.attributes.AttributeModifier applied =
            instance.getModifier(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID);
        if (applied == null || !applied.equals(entry.modern())) {
            return modern;
        }

        double otherAdditions = 0.0;
        double multipliedBase = 0.0;
        double multipliedTotal = 1.0;
        for (final net.minecraft.world.entity.ai.attributes.AttributeModifier modifier
            : instance.getModifiers()) {
            if (modifier.id().equals(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> otherAdditions += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1.0 + modifier.amount();
            }
        }
        final double legacy = LegacyCombat.attackDamageWith(instance.getBaseValue(), otherAdditions,
            LegacyCombat.weaponAttackDamage(entry.kind(), entry.materialBonus(), current),
            multipliedBase, multipliedTotal);
        return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value()
            .sanitizeValue(legacy);
    }

    /**
     * Which items 1.8's weapon rule covers, with their material bonus and their modern modifier.
     *
     * <p>Listed by item rather than read from the item tags, because a datapack can put anything
     * in {@code #swords} and a datapack sword has no 1.8 value to go back to. Built on first use,
     * which is always after the item registry exists.</p>
     */
    private static final class Weapons {

        record Entry(LegacyCombat.WeaponKind kind, double materialBonus,
                     net.minecraft.world.entity.ai.attributes.@org.jspecify.annotations.Nullable AttributeModifier modern) {
        }

        static final java.util.Map<net.minecraft.world.item.Item, Entry> TABLE = build();

        private Weapons() {
        }

        private static java.util.Map<net.minecraft.world.item.Item, Entry> build() {
            final java.util.Map<net.minecraft.world.item.Item, Entry> table =
                new java.util.IdentityHashMap<>();
            put(table, net.minecraft.world.item.ToolMaterial.WOOD,
                net.minecraft.world.item.Items.WOODEN_SWORD, net.minecraft.world.item.Items.WOODEN_AXE,
                net.minecraft.world.item.Items.WOODEN_PICKAXE, net.minecraft.world.item.Items.WOODEN_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.STONE,
                net.minecraft.world.item.Items.STONE_SWORD, net.minecraft.world.item.Items.STONE_AXE,
                net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.STONE_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.COPPER,
                net.minecraft.world.item.Items.COPPER_SWORD, net.minecraft.world.item.Items.COPPER_AXE,
                net.minecraft.world.item.Items.COPPER_PICKAXE, net.minecraft.world.item.Items.COPPER_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.IRON,
                net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_AXE,
                net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.IRON_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.GOLD,
                net.minecraft.world.item.Items.GOLDEN_SWORD, net.minecraft.world.item.Items.GOLDEN_AXE,
                net.minecraft.world.item.Items.GOLDEN_PICKAXE, net.minecraft.world.item.Items.GOLDEN_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.DIAMOND,
                net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.DIAMOND_AXE,
                net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.DIAMOND_SHOVEL);
            put(table, net.minecraft.world.item.ToolMaterial.NETHERITE,
                net.minecraft.world.item.Items.NETHERITE_SWORD, net.minecraft.world.item.Items.NETHERITE_AXE,
                net.minecraft.world.item.Items.NETHERITE_PICKAXE, net.minecraft.world.item.Items.NETHERITE_SHOVEL);
            return java.util.Collections.unmodifiableMap(table);
        }

        private static void put(final java.util.Map<net.minecraft.world.item.Item, Entry> table,
                                final net.minecraft.world.item.ToolMaterial material,
                                final net.minecraft.world.item.Item sword,
                                final net.minecraft.world.item.Item axe,
                                final net.minecraft.world.item.Item pickaxe,
                                final net.minecraft.world.item.Item shovel) {
            final double bonus = material.attackDamageBonus();
            table.put(sword, new Entry(LegacyCombat.WeaponKind.SWORD, bonus, defaultModifier(sword)));
            table.put(axe, new Entry(LegacyCombat.WeaponKind.AXE, bonus, defaultModifier(axe)));
            table.put(pickaxe, new Entry(LegacyCombat.WeaponKind.PICKAXE, bonus, defaultModifier(pickaxe)));
            table.put(shovel, new Entry(LegacyCombat.WeaponKind.SHOVEL, bonus, defaultModifier(shovel)));
        }

        /** The attack damage modifier the item's own default components give it. */
        private static net.minecraft.world.entity.ai.attributes.@org.jspecify.annotations.Nullable AttributeModifier defaultModifier(
            final net.minecraft.world.item.Item item) {
            final net.minecraft.world.item.component.ItemAttributeModifiers modifiers =
                item.components().get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            if (modifiers == null) {
                return null;
            }
            for (final net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry
                : modifiers.modifiers()) {
                if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    && entry.modifier().id().equals(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)) {
                    return entry.modifier();
                }
            }
            return null;
        }
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
