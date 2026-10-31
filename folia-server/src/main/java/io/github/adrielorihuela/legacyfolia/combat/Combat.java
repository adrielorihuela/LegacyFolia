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
     * Whether a melee hit plays the attack sounds added in 1.9 (knockback, critical, strong, weak
     * and no-damage). 1.8 played only the victim's hurt sound.
     *
     * @return whether to play them
     */
    public static boolean attackSounds() {
        return !config.enabled();
    }

    /**
     * Whether a hit awards {@code DAMAGE_DEALT} the way 1.8 did, ten times the damage of the hit
     * against a living target before armour, and sends no damage indicator particles, which 1.8
     * did not have.
     *
     * @return whether the old statistic applies
     */
    public static boolean legacyDamageDealt() {
        return config.enabled();
    }

    /**
     * The direction a hurt player's camera tilts. 1.8 sent only the hurt status, on which its
     * client set the direction to 0, so the tilt never pointed at the attacker.
     *
     * @param modern the direction towards the attacker
     * @return the direction to send
     */
    public static float hurtDirection(final float modern) {
        return config.enabled() ? 0.0F : modern;
    }

    /**
     * Whether an entity is on a ladder for a critical hit, from 1.8's {@code EntityLiving.k_()}:
     * the block at its feet is a ladder or a vine, whether or not it is flying, and it is not a
     * spectator. The modern test counts every climbable block and open trapdoors, and never a
     * flying player.
     *
     * @param entity the attacker
     * @return whether it is on a ladder
     */
    public static boolean onLegacyLadder(final net.minecraft.world.entity.LivingEntity entity) {
        if (entity.isSpectator()) {
            return false;
        }
        final net.minecraft.world.level.block.state.BlockState state = entity.level().getBlockState(
            net.minecraft.core.BlockPos.containing(entity.getX(), entity.getBoundingBox().minY, entity.getZ()));
        return state.is(net.minecraft.world.level.block.Blocks.LADDER)
            || state.is(net.minecraft.world.level.block.Blocks.VINE);
    }

    /**
     * Whether an entity is in water for a critical hit, from 1.8's {@code Entity.W()}: a water
     * block anywhere in its box shrunk by 0.4 at the top and the bottom and by 0.001 all round.
     * Waterlogged blocks did not exist and do not count.
     *
     * @param entity the attacker
     * @return whether it is in water
     */
    public static boolean inLegacyWater(final net.minecraft.world.entity.Entity entity) {
        final net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(0.0, -0.4000000059604645, 0.0)
            .deflate(0.001);
        final int minX = net.minecraft.util.Mth.floor(box.minX);
        final int maxX = net.minecraft.util.Mth.floor(box.maxX + 1.0);
        final int minY = net.minecraft.util.Mth.floor(box.minY);
        final int maxY = net.minecraft.util.Mth.floor(box.maxY + 1.0);
        final int minZ = net.minecraft.util.Mth.floor(box.minZ);
        final int maxZ = net.minecraft.util.Mth.floor(box.maxZ + 1.0);
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (entity.level().getBlockState(pos.set(x, y, z))
                        .is(net.minecraft.world.level.block.Blocks.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether hits need no charge, as before 1.9.
     *
     * @return whether the attack cooldown is gone
     */
    public static boolean noAttackCooldown() {
        final CombatConfig current = config;
        return current.enabled() && current.noAttackCooldown();
    }

    private static final net.minecraft.resources.Identifier NO_ATTACK_COOLDOWN =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("legacyfolia", "no_attack_cooldown");

    /**
     * Gives a player, or takes away, the attack speed that keeps a modern client's charge
     * indicator full, so it draws none with no attack cooldown.
     *
     * <p>The client works out the indicator from the attack speed it is sent, so answering 1 on
     * the server alone left it drawing a charge that meant nothing. A transient modifier is never
     * saved with the player, and the weapons' own attack speed is untouched, so their tooltips
     * read as before. Called every tick; it only changes anything when the setting has.</p>
     *
     * @param player the player
     */
    public static void syncAttackSpeed(final net.minecraft.world.entity.player.Player player) {
        final net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        final boolean wanted = noAttackCooldown();
        if (wanted != speed.hasModifier(NO_ATTACK_COOLDOWN)) {
            if (wanted) {
                speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    NO_ATTACK_COOLDOWN, 1024.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            } else {
                speed.removeModifier(NO_ATTACK_COOLDOWN);
            }
        }
    }

    /**
     * Whether the server checks reach and swings the way 1.8's did.
     *
     * @return whether the old checks apply
     */
    public static boolean legacyReach() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyReach();
    }

    /**
     * Whether a player may hit or use an entity, from 1.8's {@code PlayerConnection.a(
     * PacketPlayInUseEntity)}: feet to feet closer than 6 blocks when the player can see the
     * target and 3 when they cannot, in any game mode.
     *
     * <p>The modern check measures from the eyes to the nearest point of the target's box, against
     * 3 blocks (5 in creative) plus 3 of leniency, and never asks about line of sight.</p>
     *
     * @param player who is reaching
     * @param target what they reach for
     * @return whether it is in reach
     */
    public static boolean withinReach(final net.minecraft.world.entity.player.Player player,
                                      final net.minecraft.world.entity.Entity target) {
        return LegacyCombat.withinReach(player.distanceToSqr(target), player.hasLineOfSight(target));
    }

    /**
     * Whether a swing hits a block, from 1.8's {@code PlayerConnection.a(PacketPlayInArmAnimation)}:
     * a trace of blocks only, 4.5 blocks from the eyes (5 in creative). 1.8 fired
     * {@code LEFT_CLICK_AIR} for any swing that did not, a swing at a player included.
     *
     * @param player who swung
     * @return whether a block is in the way
     */
    public static boolean swingHitsBlock(final net.minecraft.server.level.ServerPlayer player) {
        final net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        final double distance = player.isCreative() ? 5.0 : 4.5;
        final net.minecraft.world.phys.Vec3 end = eye.add(player.getViewVector(1.0F).scale(distance));
        return player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
            == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    /**
     * Whether thrown and shot projectiles fly the way 1.8's did: Gaussian spread, no motion
     * inherited from the thrower, a move before drag and gravity, a 0.3 margin from the first tick,
     * the thrower spared for five ticks, a launch point 0.16 to the side, arrows passing through
     * creative players, Punch ignoring knockback resistance, and pearls with no cooldown that land
     * where they were before their last move.
     *
     * @return whether the old projectiles apply
     */
    public static boolean legacyProjectiles() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyProjectiles();
    }

    /**
     * Whether potions and effects work the way 1.8's did: splash intensity measured from where the
     * potion was to each entity's feet, splash durations cut to three quarters, 1.8's durations
     * for the potions that changed, no hidden effects, Absorption that lasts its full time, and
     * Hunger that costs five times what it does now.
     *
     * @return whether the old potions apply
     */
    public static boolean legacyPotions() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyPotions();
    }

    /**
     * How much of a splash reaches an entity, from 1.8's {@code EntityPotion.a(MovingObjectPosition)}:
     * {@code 1 - sqrt(d) / 4} with {@code d} the squared distance from where the potion was to the
     * entity's feet, and full strength for the entity it struck.
     *
     * @param center where the potion was before its last move
     * @param entity who might be reached
     * @param hit what the potion struck, if anything
     * @return the intensity, zero for none
     */
    public static double splashIntensity(final net.minecraft.world.phys.Vec3 center,
                                         final net.minecraft.world.entity.Entity entity,
                                         final net.minecraft.world.phys.@org.jspecify.annotations.Nullable HitResult hit) {
        final double intensity = LegacyCombat.splashIntensity(center.distanceToSqr(entity.position()), config);
        if (intensity > 0.0 && hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
            && entityHit.getEntity() == entity) {
            return 1.0;
        }
        return intensity;
    }

    /**
     * A potion's effect with 1.8's duration where it differed: Regeneration and Poison extended
     * lasted 2:00 (2400 ticks) rather than 1:30, and Poison II 450 ticks rather than 432. Only the
     * potion's own effect at its own duration is changed; custom effects are left as they are.
     *
     * @param contents the potion
     * @param effect one of its effects
     * @return the effect to apply
     */
    public static net.minecraft.world.effect.MobEffectInstance potionEffect(
        final net.minecraft.world.item.alchemy.PotionContents contents,
        final net.minecraft.world.effect.MobEffectInstance effect) {
        if (!legacyPotions() || contents.potion().isEmpty()) {
            return effect;
        }
        final net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion = contents.potion().get();
        final int legacy;
        if (potion.is(net.minecraft.world.item.alchemy.Potions.LONG_REGENERATION)
            && effect.is(net.minecraft.world.effect.MobEffects.REGENERATION) && effect.getDuration() == 1800) {
            legacy = 2400;
        } else if (potion.is(net.minecraft.world.item.alchemy.Potions.LONG_POISON)
            && effect.is(net.minecraft.world.effect.MobEffects.POISON) && effect.getDuration() == 1800) {
            legacy = 2400;
        } else if (potion.is(net.minecraft.world.item.alchemy.Potions.STRONG_POISON)
            && effect.is(net.minecraft.world.effect.MobEffects.POISON) && effect.getDuration() == 432) {
            legacy = 450;
        } else {
            return effect;
        }
        return new net.minecraft.world.effect.MobEffectInstance(effect.getEffect(), legacy,
            effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    /**
     * The exhaustion the Hunger effect costs each tick, 0.025 a level in 1.8's
     * {@code MobEffectList.tick} against 0.005 now.
     *
     * @param amplifier the effect amplifier
     * @param modern what the server would otherwise use
     * @return the exhaustion
     */
    public static float hungerEffectExhaustion(final int amplifier, final float modern) {
        return legacyPotions() ? LegacyCombat.hungerEffectExhaustion(amplifier) : modern;
    }

    /**
     * Whether explosions hurt and push the way 1.8's did.
     *
     * @return whether the old explosions apply
     */
    public static boolean legacyExplosions() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyExplosions();
    }

    /**
     * Whether an explosion's hit on an entity is one CraftBukkit 1.8.8 refused only because the
     * entity was still invulnerable from a stronger hit, in which case it pushed it anyway
     * ({@code forceExplosionKnockback}, SPIGOT-949). Asked before the hit is dealt.
     *
     * @param entity what the explosion reaches
     * @param damage the damage it would do
     * @return whether to push it even though the hit will not land
     */
    public static boolean explosionHitsCooldown(final net.minecraft.world.entity.Entity entity,
                                                final float damage) {
        return entity instanceof net.minecraft.world.entity.LivingEntity living
            && (float) living.damageCooldownTime > (float) living.invulnerableDuration / 2.0F
            && damage <= living.lastHurt;
    }

    /**
     * How much of an entity an explosion sees, from 1.8's {@code World.a(Vec3D, AxisAlignedBB)}:
     * the same grid of rays as now, but stepped with {@code float} counters, which lands on
     * slightly different points of the box.
     *
     * @param center the explosion's centre
     * @param entity what it reaches
     * @return the fraction of rays that reach the centre
     */
    public static float explosionExposure(final net.minecraft.world.phys.Vec3 center,
                                          final net.minecraft.world.entity.Entity entity) {
        final net.minecraft.world.phys.AABB bb = entity.getBoundingBox();
        final double xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
        final double ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
        final double zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
        final double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        final double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) {
            return 0.0F;
        }
        int hits = 0;
        int count = 0;
        for (float fx = 0.0F; fx <= 1.0F; fx = (float) ((double) fx + xs)) {
            for (float fy = 0.0F; fy <= 1.0F; fy = (float) ((double) fy + ys)) {
                for (float fz = 0.0F; fz <= 1.0F; fz = (float) ((double) fz + zs)) {
                    final double x = bb.minX + (bb.maxX - bb.minX) * (double) fx;
                    final double y = bb.minY + (bb.maxY - bb.minY) * (double) fy;
                    final double z = bb.minZ + (bb.maxZ - bb.minZ) * (double) fz;
                    if (entity.level().clip(new net.minecraft.world.level.ClipContext(
                            new net.minecraft.world.phys.Vec3(x + xOffset, y, z + zOffset), center,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, entity)).getType()
                        == net.minecraft.world.phys.HitResult.Type.MISS) {
                        hits++;
                    }
                    count++;
                }
            }
        }
        return count == 0 ? 0.0F : (float) hits / (float) count;
    }

    /**
     * Whether a fishing rod works the way 1.8's did: the hook is cast like a thrown item and
     * falls and slows as 1.8's did, hurts what it hits for nothing (knocking a player back from
     * the angler) and catches it only if that landed, cannot catch a dropped item, lifts what it
     * reels in, costs 3 durability for an entity, and never pulls a hooked player.
     *
     * @return whether the old rod applies
     */
    public static boolean legacyFishingRod() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyFishingRod();
    }

    /**
     * Where a living thing's projectile starts, from 1.8's {@code EntityProjectile} and
     * {@code EntityArrow} constructors: at its head height less 0.1, moved 0.16 to the side it
     * faces, with a sneaking player's head at 1.54 as 1.8 had it rather than the 1.27 of today's
     * crouch.
     *
     * @param owner who threw or shot it
     * @return the position, or null to keep the game's
     */
    public static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 projectileOrigin(
        final net.minecraft.world.entity.LivingEntity owner) {
        return legacyProjectiles() ? legacyOrigin(owner) : null;
    }

    /**
     * Where a fishing hook is cast from: the same point as {@link #projectileOrigin}, under
     * {@code legacy-fishing-rod}.
     *
     * @param angler who casts
     * @return the position, or null to keep the game's
     */
    public static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 hookOrigin(
        final net.minecraft.world.entity.player.Player angler) {
        return legacyFishingRod() ? legacyOrigin(angler) : null;
    }

    private static net.minecraft.world.phys.Vec3 legacyOrigin(
        final net.minecraft.world.entity.LivingEntity owner) {
        final double eyeHeight = owner instanceof net.minecraft.world.entity.player.Player player
            ? (double) (player.isShiftKeyDown() ? 1.62F - 0.08F : 1.62F)
            : (double) owner.getEyeHeight();
        return LegacyCombat.projectileOrigin(owner.getX(), owner.getY(), owner.getZ(), eyeHeight,
            owner.getYRot());
    }

    /**
     * Takes away the cooldown the modern ender pearl puts on itself, which 1.8 never had. The
     * cooldown is applied and then removed so a modern client, which starts its own when it
     * throws, is told it is over.
     *
     * @param used the stack that was used
     * @param user who used it
     */
    public static void clearPearlCooldown(final net.minecraft.world.item.ItemStack used,
                                          final net.minecraft.world.entity.LivingEntity user) {
        if (legacyProjectiles() && used.is(net.minecraft.world.item.Items.ENDER_PEARL)
            && user instanceof net.minecraft.world.entity.player.Player player) {
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(used));
        }
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
     * anything a datapack adds on the modern path. A weapon that has Sharpness keeps the others
     * too: see {@link #enchantedDamage}.</p>
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
     * The damage the weapon's enchantments add to a hit, with Sharpness at 1.8's flat figure.
     *
     * <p>Every other damage enchantment on the weapon still counts, as it did in 1.8, which summed
     * them all: a weapon a command gave both Sharpness and Smite gets the Smite as well. Mobs
     * holding a sharpened weapon get the 1.8 figure too, as {@code EntityMonster} gave it.</p>
     *
     * @param level the level the hit is in
     * @param weapon the weapon in hand
     * @param target who is hit
     * @param source the damage source of the hit
     * @param damage the damage before the enchantments
     * @return the damage with them
     */
    public static float enchantedDamage(final net.minecraft.server.level.ServerLevel level,
                                        final net.minecraft.world.item.ItemStack weapon,
                                        final net.minecraft.world.entity.Entity target,
                                        final net.minecraft.world.damagesource.DamageSource source,
                                        final float damage) {
        if (!legacySharpness(weapon)) {
            return net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon,
                target, source, damage);
        }
        final net.minecraft.world.item.ItemStack withoutSharpness = weapon.copy();
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(withoutSharpness,
            enchantments -> enchantments.removeIf(enchantment ->
                enchantment.is(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)));
        return net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,
            withoutSharpness, target, source, damage) + sharpnessBonus(weapon);
    }

    /** What a target's Slowness was before a Bane of Arthropods hit, to put back after it. */
    private record BaneHit(net.minecraft.world.entity.LivingEntity target, int level,
                           net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance before) {
    }

    /**
     * Notes a target's Slowness before the post-attack effects of a Bane of Arthropods weapon run.
     *
     * @param target who is hit
     * @param weapon the weapon in hand
     * @return what {@link #baneAfter(Object)} needs, or null when 1.8's rule does not apply
     */
    public static @org.jspecify.annotations.Nullable Object baneBefore(
        final net.minecraft.world.entity.Entity target, final net.minecraft.world.item.ItemStack weapon) {
        if (!config.enabled()
            || !(target instanceof net.minecraft.world.entity.LivingEntity living)
            || !living.is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            return null;
        }
        final int level = enchantmentLevel(weapon,
            net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS);
        if (level <= 0) {
            return null;
        }
        final net.minecraft.world.effect.MobEffectInstance before =
            living.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        return new BaneHit(living, level,
            before == null ? null : new net.minecraft.world.effect.MobEffectInstance(before));
    }

    /**
     * Replaces the Slowness the modern enchantment just gave with 1.8's, from
     * {@code EnchantmentWeaponDamage.a(EntityLiving, Entity)}: Slowness IV for
     * {@code 20 + random(10 * level)} ticks, so 20 to 29 at level I and 20 to 69 at level V, where
     * the modern enchantment gives a flat 30 at level I and 30 to 70 at V.
     *
     * @param before what {@link #baneBefore} returned
     */
    public static void baneAfter(final @org.jspecify.annotations.Nullable Object before) {
        if (!(before instanceof BaneHit hit)) {
            return;
        }
        final net.minecraft.world.entity.LivingEntity target = hit.target();
        if (target.isRemoved() || target.isDeadOrDying()) {
            return;
        }
        target.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        if (hit.before() != null) {
            target.addEffect(hit.before());
        }
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.SLOWNESS,
            LegacyCombat.baneSlownessTicks(hit.level(), target.getRandom()::nextInt), 3));
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
     * How long this weapon's fire aspect sets a target alight for before the hit; see
     * {@link LegacyCombat#fireAspectSeconds}.
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
     * Whether a player hit by another player has their own motion put back on the server after
     * the velocity packet is sent, as CraftBukkit 1.8.8 and Paper both do.
     *
     * <p>Only ever a player target, and only when the packet was sent: a mob keeps the knockback.
     * True unless {@code restore-velocity-after-knockback} turns it off with 1.8 knockback on.</p>
     *
     * @return whether to restore it
     */
    public static boolean restoresVelocity() {
        final CombatConfig current = config;
        return !current.enabled() || !current.legacyKnockback()
            || LegacyCombat.restoresVelocity(current);
    }

    /**
     * Whether knockback follows 1.8.
     *
     * @return whether the old knockback applies
     */
    public static boolean legacyKnockback() {
        final CombatConfig current = config;
        return current.enabled() && current.legacyKnockback();
    }

    /**
     * Which way the base knockback of a hit pushes, from CraftBukkit 1.8.8's
     * {@code EntityLiving.damageEntity}.
     *
     * <p>1.8 pushed away from {@code damageSource.getEntity()}: the attacker for a melee hit, the
     * owner for an arrow, a snowball, a fireball, a fishing hook or a harming potion, and whoever
     * set off an explosion. An arrow nobody shot pushed away from itself; any other hit with no
     * living thing behind it did not push at all. While the two stood on top of each other the
     * direction was re-rolled from {@code Math.random()}, and nothing clamped a far-off source.</p>
     *
     * @param target who was hit
     * @param source what hit them
     * @return the x and z from the target to the source, or null for no knockback
     */
    public static double @org.jspecify.annotations.Nullable [] knockbackDirection(
        final net.minecraft.world.entity.LivingEntity target,
        final net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.entity.Entity from = source.getEntity();
        if (from == null
            && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) {
            from = arrow;
        }
        if (!(from instanceof net.minecraft.world.entity.LivingEntity)
            && !(from instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) {
            return null;
        }
        double xd = from.getX() - target.getX();
        double zd = from.getZ() - target.getZ();
        while (xd * xd + zd * zd < 1.0E-4) {
            xd = (Math.random() - Math.random()) * 0.01;
            zd = (Math.random() - Math.random()) * 0.01;
        }
        return new double[] {xd, zd};
    }

    /**
     * Whether a hit the game tags as giving no knockback gave some in 1.8: an explosion somebody
     * or something living set off, which pushed away from them on top of the blast.
     *
     * @param source what hit
     * @return whether to knock back anyway
     */
    public static boolean knocksBackDespiteTag(final net.minecraft.world.damagesource.DamageSource source) {
        return legacyKnockback() && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
            && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity;
    }

    /**
     * Adds the extra push of a sprint hit or the Knockback enchantment, from 1.8's
     * {@code EntityHuman.attack} and {@code EntityMonster.r}.
     *
     * <p>1.8 added it straight onto the target's motion: no halving, no cap and no knockback
     * resistance. Sending it through the base knockback instead, as the modern game does, gave a
     * sprint hit the same 0.4 as any other.</p>
     *
     * @param attacker who hit
     * @param target who was hit
     * @param amount half a point per level of Knockback plus one for sprinting
     * @return whether it was added here; false leaves it to the game
     */
    public static boolean extraKnockback(final net.minecraft.world.entity.LivingEntity attacker,
                                         final net.minecraft.world.entity.Entity target,
                                         final float amount) {
        if (!legacyKnockback() || !(target instanceof net.minecraft.world.entity.LivingEntity)) {
            return false;
        }
        final net.minecraft.world.phys.Vec3 push = LegacyCombat.extraKnockback(attacker.getYRot(), amount);
        target.push(push.x, push.y, push.z, attacker);
        return true;
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
