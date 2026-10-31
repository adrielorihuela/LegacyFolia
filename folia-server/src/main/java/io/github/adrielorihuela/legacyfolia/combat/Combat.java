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
     * The {@code BLOCKING} damage modifier of a raised sword, from the {@code blocking} function
     * of CraftBukkit 1.8.8's {@code EntityLiving.d(DamageSource, float)}.
     *
     * <p>1.8 applied it only to a player, only while the sword was raised, only to damage above
     * zero and never to damage that ignores armour: falls and ender pearls, fire ticks, drowning,
     * suffocation, starving, the void, magic and harming potions, and wither all went through a
     * raised sword untouched. It ran after the invulnerability window and the hard hat, on what
     * they left, and before armour, so blocking and armour compound. See
     * {@link LegacyCombat#afterBlocking(double, CombatConfig)} for the formula.</p>
     *
     * @param target who is being hit
     * @param source what is hitting them
     * @param damage the damage the modifier works on
     * @return the modifier, negative or zero, or null when a raised sword does not apply and the
     *     game's own blocking should decide
     */
    public static @org.jspecify.annotations.Nullable Double swordBlocking(
        final net.minecraft.world.entity.LivingEntity target,
        final net.minecraft.world.damagesource.DamageSource source, final double damage) {
        final CombatConfig current = config;
        if (!current.enabled() || !current.swordBlocking()
            || !(target instanceof net.minecraft.world.entity.player.Player)
            || !target.isUsingItem() || !target.getUseItem().is(net.minecraft.tags.ItemTags.SWORDS)) {
            return null;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) || damage <= 0.0) {
            return -0.0;
        }
        return -(damage - LegacyCombat.afterBlocking(damage, current));
    }

    /**
     * Whether a hit that does no damage still lands on a player.
     *
     * <p>Vanilla 1.8 dropped a 0 damage hit on a player, and CraftBukkit 1.8.8 took that filter
     * out ({@code if (false && f == 0.0F)} in {@code EntityHuman.damageEntity}), so on the Spigot
     * servers PvP was played on a snowball, an egg or a fishing hook flashed the player red,
     * started the invulnerability window and knocked them back. Paper put the filter back; this
     * takes it out again.</p>
     *
     * @return whether 0 damage hits land
     */
    public static boolean zeroDamageHitsLand() {
        return config.enabled();
    }

    /**
     * The damage a player's armour is worn by, with a 0 damage hit still costing a point.
     *
     * <p>1.8's {@code PlayerInventory.a(float)} divided the damage by four and raised anything
     * below one to one, so a snowball wore every armour piece by a point. The modern
     * {@code doHurtEquipment} does nothing for 0, so 0 becomes the smallest positive damage, which
     * it then rounds up to the same single point.</p>
     *
     * @param damage the damage the armour step was given
     * @return the damage to wear the armour by
     */
    public static float armorWear(final float damage) {
        return config.enabled() && damage <= 0.0F ? Float.MIN_VALUE : damage;
    }

    /**
     * Whether hits follow 1.8's damage window: the raw damage is compared with the last hit's,
     * only the difference reaches the damage modifiers, and the raw figure is remembered.
     *
     * @return whether the old window applies
     */
    public static boolean legacyDamageWindow() {
        return config.enabled();
    }

    /**
     * What a hit inside the invulnerability window does, from CraftBukkit 1.8.8's
     * {@code EntityLiving.damageEntity}: the difference from the last hit, or nothing when
     * {@code damage-overriding} is off or the hit is not stronger.
     *
     * @param damage the raw damage of this hit
     * @param lastDamage the raw damage of the hit that is remembered
     * @return the damage to hand to the modifiers, zero for none
     */
    public static float damageThroughWindow(final float damage, final float lastDamage) {
        return LegacyCombat.damageThroughInvulnerability(damage, lastDamage, config);
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
     * The protection points of every piece of equipment added together, before the roll, from
     * {@code EnchantmentManager.a(ItemStack[], DamageSource)}.
     *
     * <p>All five protection enchantments feed the same sum in 1.8, each only against its own
     * kind of damage: Protection against everything, Fire Protection against fire, Feather
     * Falling against falls (an ender pearl's landing is a fall), Blast Protection against
     * explosions and Projectile Protection against projectiles. None of them counts against
     * damage that ignores invulnerability, which is the void. A player's equipment there is the
     * four armour slots; a mob's also includes what it holds.</p>
     *
     * @param target who is being hit
     * @param source what is hitting them
     * @return the summed points
     */
    public static int protectionPoints(final net.minecraft.world.entity.LivingEntity target,
                                       final net.minecraft.world.damagesource.DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return 0;
        }
        final CombatConfig current = config;
        final boolean fire = source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        final boolean fall = source.is(net.minecraft.tags.DamageTypeTags.IS_FALL);
        final boolean explosion = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        final boolean projectile = source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
        int points = 0;
        for (final net.minecraft.world.entity.EquipmentSlot slot
            : net.minecraft.world.entity.EquipmentSlot.VALUES) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR
                && (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    || target instanceof net.minecraft.world.entity.player.Player)) {
                continue;
            }
            final net.minecraft.world.item.ItemStack piece = target.getItemBySlot(slot);
            if (piece.isEmpty()) {
                continue;
            }
            points += LegacyCombat.protectionPoints(enchantmentLevel(piece,
                net.minecraft.world.item.enchantment.Enchantments.PROTECTION), current);
            if (fire) {
                points += LegacyCombat.specialisedProtectionPoints(enchantmentLevel(piece,
                    net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION),
                    LegacyCombat.FIRE_PROTECTION_FACTOR);
            }
            if (fall) {
                points += LegacyCombat.specialisedProtectionPoints(enchantmentLevel(piece,
                    net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING),
                    LegacyCombat.FEATHER_FALLING_FACTOR);
            }
            if (explosion) {
                points += LegacyCombat.specialisedProtectionPoints(enchantmentLevel(piece,
                    net.minecraft.world.item.enchantment.Enchantments.BLAST_PROTECTION),
                    LegacyCombat.BLAST_PROTECTION_FACTOR);
            }
            if (projectile) {
                points += LegacyCombat.specialisedProtectionPoints(enchantmentLevel(piece,
                    net.minecraft.world.item.enchantment.Enchantments.PROJECTILE_PROTECTION),
                    LegacyCombat.PROJECTILE_PROTECTION_FACTOR);
            }
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
     * The attack damage of a melee hit, worked out the way 1.8 worked it out.
     *
     * <p>Read where the attack damage attribute is read for a melee hit ({@code Player.attack} and
     * {@code Mob.doHurtTarget}), so it follows a reload and leaves the item registry, every stored
     * item and every packet exactly as they are. Two things can differ from the attribute:</p>
     * <ul>
     *   <li><b>Strength and Weakness</b>, for every attacker and whatever is in hand. In 1.8
     *       Strength multiplied the whole figure by {@code 1 + 1.3 * level}, after everything
     *       else, and Weakness took {@code 0.5 * level} off before that. Now Strength adds 3 a
     *       level and Weakness takes 4 off, so a fist under Weakness I no longer hurts at all and a
     *       diamond sword under Strength I does 11 where it did 18.4. The effects' own modifiers
     *       are left out and these put in their place, read from the effects the attacker has.</li>
     *   <li><b>The weapon's modifier</b>, swapped for its 1.8 value only when the weapon is a sword,
     *       axe, pickaxe or shovel of one of the seven materials, the stack carries no attribute
     *       modifiers of its own (1.8 kept an item's {@code AttributeModifiers} tag in place of its
     *       defaults), and the modifier applied to the attacker is that item's own default, so a
     *       broken weapon, one swapped in this tick or a modifier somebody put on the entity under
     *       the same id is left alone.</li>
     * </ul>
     * <p>Every other modifier is kept, and the value is clamped to the attribute's range.</p>
     *
     * @param attacker who is swinging
     * @return the attack damage to use
     */
    public static double attackDamage(final net.minecraft.world.entity.LivingEntity attacker) {
        final double modern = attacker.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        final CombatConfig current = config;
        if (!current.enabled() || !current.legacyWeaponDamage() && !current.legacyStrength()) {
            return modern;
        }
        final net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
            attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return modern;
        }
        final Double weaponModifier = current.legacyWeaponDamage()
            ? legacyWeaponModifier(attacker, instance, current) : null;
        final boolean effects = current.legacyStrength();
        if (weaponModifier == null && !effects) {
            return modern;
        }

        double otherAdditions = 0.0;
        double multipliedBase = 0.0;
        double multipliedTotal = 1.0;
        for (final net.minecraft.world.entity.ai.attributes.AttributeModifier modifier
            : instance.getModifiers()) {
            final net.minecraft.resources.Identifier id = modifier.id();
            if (weaponModifier != null && id.equals(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)
                || effects && (id.equals(STRENGTH_MODIFIER) || id.equals(WEAKNESS_MODIFIER))) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> otherAdditions += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1.0 + modifier.amount();
            }
        }
        final double legacy = LegacyCombat.attackDamageWith(instance.getBaseValue(), otherAdditions,
            weaponModifier == null ? 0.0 : weaponModifier, multipliedBase, multipliedTotal,
            effects ? amplifier(attacker, net.minecraft.world.effect.MobEffects.STRENGTH) : -1,
            effects ? amplifier(attacker, net.minecraft.world.effect.MobEffects.WEAKNESS) : -1);
        return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value()
            .sanitizeValue(legacy);
    }

    private static final net.minecraft.resources.Identifier STRENGTH_MODIFIER =
        net.minecraft.resources.Identifier.withDefaultNamespace("effect.strength");
    private static final net.minecraft.resources.Identifier WEAKNESS_MODIFIER =
        net.minecraft.resources.Identifier.withDefaultNamespace("effect.weakness");

    /** The amplifier of an effect the entity has, or -1 when it has none. */
    static int amplifier(final net.minecraft.world.entity.LivingEntity entity,
                         final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        final net.minecraft.world.effect.MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    /**
     * The 1.8 modifier of the weapon in hand, or null when the weapon keeps its modern one: it is
     * not in the table, it carries modifiers of its own, or the modifier applied to the attacker is
     * not the item's default.
     */
    private static @org.jspecify.annotations.Nullable Double legacyWeaponModifier(
        final net.minecraft.world.entity.LivingEntity attacker,
        final net.minecraft.world.entity.ai.attributes.AttributeInstance instance,
        final CombatConfig current) {
        final net.minecraft.world.item.ItemStack weapon = attacker.getMainHandItem();
        final Weapons.Entry entry = Weapons.TABLE.get(weapon.getItem());
        if (entry == null
            || weapon.hasNonDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS)) {
            return null;
        }
        final net.minecraft.world.entity.ai.attributes.AttributeModifier applied =
            instance.getModifier(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID);
        if (applied == null || !applied.equals(entry.modern())) {
            return null;
        }
        return LegacyCombat.weaponAttackDamage(entry.kind(), entry.materialBonus(), current);
    }

    /**
     * Which items 1.8's weapon rule covers, with their material bonus and their modern modifier.
     *
     * <p>Listed by item rather than read from the item tags, because a datapack can put anything
     * in {@code #swords} and a datapack sword has no 1.8 value to go back to. Built on first use,
     * which is always after the item registry exists. {@link WeaponTooltips} reads the same table,
     * so the tooltip a client is sent and the damage a hit does come from one place.</p>
     */
    static final class Weapons {

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
     * Sends every online player their inventory and open container again, so the weapon tooltips
     * they hold follow a reload that changed them. Each player's resend runs on the thread that
     * owns that player. Nothing to do at start-up, before there is a server or anybody on it.
     */
    private static void resendInventories() {
        final org.bukkit.Server server = org.bukkit.Bukkit.getServer();
        if (server == null) {
            return;
        }
        for (final org.bukkit.entity.Player online : server.getOnlinePlayers()) {
            final org.bukkit.craftbukkit.entity.CraftPlayer craft =
                (org.bukkit.craftbukkit.entity.CraftPlayer) online;
            craft.taskScheduler.schedule((final net.minecraft.server.level.ServerPlayer player) -> {
                player.inventoryMenu.sendAllDataToRemote();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.sendAllDataToRemote();
                }
            }, null, 1L);
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
     * The exhaustion a player pays for being hurt, from {@code DamageSource.getExhaustionCost}.
     *
     * <p>1.8 charged 0.3 for every source except those that ignore armour (falls, burning,
     * drowning, suffocation, starving, the void, magic, wither), which cost nothing. Modern
     * damage types charge 0.1 for nearly everything, a third of what a hit used to drain.</p>
     *
     * @param source what hurt the player
     * @return the exhaustion
     */
    public static float damageExhaustion(final net.minecraft.world.damagesource.DamageSource source) {
        final CombatConfig current = config;
        if (!current.enabled()) {
            return source.getFoodExhaustion();
        }
        return LegacyCombat.damageExhaustion(
            source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR), current);
    }

    /**
     * Wears a helmet down the way a falling anvil or block did in 1.8, from CraftBukkit 1.8.8's
     * {@code EntityLiving.d(DamageSource, float)}.
     *
     * <p>{@code (int) (damage * 4 + random * damage * 2)} durability, on any living thing wearing
     * one, where the damage is what the invulnerability window left and before the hard hat took
     * its quarter. A 20 damage anvil costs 80 to 119 durability; now it costs 5, and a mob's helmet
     * never wears at all.</p>
     *
     * @param entity who was hit
     * @param damage the damage after the window, before the hard hat
     * @return whether the helmet was handled here, false to leave it to the game
     */
    public static boolean wearHelmet(final net.minecraft.world.entity.LivingEntity entity,
                                     final float damage) {
        if (!config.enabled()) {
            return false;
        }
        final net.minecraft.world.item.ItemStack helmet =
            entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        helmet.hurtAndBreak(LegacyCombat.helmetWear(damage, entity.getRandom().nextFloat()), entity,
            net.minecraft.world.entity.EquipmentSlot.HEAD);
        return true;
    }

    /**
     * Whether a hit on a player is scaled by the difficulty, from 1.8's {@code DamageSource.r()}.
     *
     * <p>1.8 looked at the entity that dealt the hit directly: a mob's melee hit scaled, but its
     * arrow or fireball did not, because the arrow is not a living thing. An explosion scaled
     * unless a player set it off. The modern rule looks at the entity behind the hit, so a
     * skeleton's arrows do half again as much on Hard.</p>
     *
     * @param source what is hitting the player
     * @return whether the difficulty changes the damage
     */
    public static boolean scalesWithDifficulty(final net.minecraft.world.damagesource.DamageSource source) {
        if (!config.enabled()) {
            return source.scalesWithDifficulty();
        }
        final boolean always = source.type().scaling()
            == net.minecraft.world.damagesource.DamageScaling.ALWAYS;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            final net.minecraft.world.entity.Entity cause = source.getEntity();
            return cause == null ? always : livingNonPlayer(cause);
        }
        final net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        return direct == null ? always : livingNonPlayer(direct);
    }

    private static boolean livingNonPlayer(final net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity
            && !(entity instanceof net.minecraft.world.entity.player.Player);
    }

    /**
     * A hit on Easy, from 1.8's {@code EntityHuman.damageEntity}: {@code damage / 2 + 1}, with no
     * ceiling, so a 1 damage hit does 1.5. The modern rule never raises a hit.
     *
     * @param damage the damage before the difficulty
     * @return the damage on Easy
     */
    public static float easyDamage(final float damage) {
        return config.enabled() ? damage / 2.0F + 1.0F : Math.min(damage / 2.0F + 1.0F, damage);
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

    /**
     * Puts settings in force without reading the files, for the tests.
     *
     * @param settings the settings to use
     */
    static void use(final CombatConfig settings) {
        config = settings;
    }

    /** Re-reads the settings, at start-up and on reload. */
    public static void reload() {
        final CombatConfig before = config;
        config = CombatConfig.load();
        if (WeaponTooltips.differ(before, config)) {
            resendInventories();
        }
        if (config.enabled()) {
            LOGGER.info("1.8 combat is on: no attack cooldown, no sweep, and knockback that lifts "
                + "an airborne target. Every part of it is switchable under [combat] in "
                + "config/combat.toml.");
        }
    }
}
