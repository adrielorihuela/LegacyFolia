package io.github.adrielorihuela.legacyfolia.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * Makes a client's tooltip show the damage {@link Combat#attackDamage} actually deals.
 *
 * <p>A modern client draws "N Attack Damage" from its own copy of the item's default components,
 * and the server only sends the components a stack has changed, so a plain diamond sword reads 7
 * while it hits for 8. Changing the items themselves would bake 1.8's figure into every saved
 * world and inventory, survive turning the setting off, and make every weapon look custom to a
 * plugin. So nothing stored changes: the figure is rewritten on the way out, in the packet
 * encoder, and taken back out on the way in.</p>
 *
 * <ul>
 *   <li>{@link #outgoing}: a sword, axe, pickaxe or shovel {@link Combat#attackDamage} would count
 *       at its 1.8 value, sent with an {@code attribute_modifiers} component that is the item's own
 *       default with the attack damage modifier's amount set to 1.8's. Same id, slot, operation
 *       and display, so the client draws it exactly as vanilla draws the default, and the attack
 *       speed modifier is untouched. A stack that carries attribute modifiers of its own (a
 *       plugin's, a command's) is sent as it is, which is also what the hit does with it.</li>
 *   <li>{@link #incoming}: a creative-mode client sends whole stacks back, the component it was
 *       given included. When the component is exactly what {@link #outgoing} adds for that item,
 *       it is removed again, so the server's copy goes back to the item's default. Compared
 *       against the configured bases whether or not the setting is on, so a stack sent just
 *       before the setting was turned off is still recognised.</li>
 * </ul>
 */
public final class WeaponTooltips {

    /** The figures worked out for one configuration, rebuilt when the configuration changes. */
    private record Cache(CombatConfig config, Map<Item, ItemAttributeModifiers> byItem) {
    }

    private static volatile Cache cache = new Cache(CombatConfig.off(), Map.of());

    private WeaponTooltips() {
    }

    /**
     * The components to send for a stack, with 1.8's attack damage in place of the default's
     * when the setting is on.
     *
     * @param item the stack's item
     * @param patch the stack's own changes to the item's defaults
     * @return what to encode; {@code patch} itself when there is nothing to change
     */
    public static DataComponentPatch outgoing(final Holder<Item> item, final DataComponentPatch patch) {
        final CombatConfig current = Combat.config();
        if (!current.enabled() || !current.legacyWeaponDamage()
            || patch.map.containsKey(DataComponents.ATTRIBUTE_MODIFIERS)) {
            return patch;
        }
        final ItemAttributeModifiers legacy = legacyModifiers(item.value(), current);
        if (legacy == null) {
            return patch;
        }
        final DataComponentPatch.Builder builder = DataComponentPatch.builder();
        builder.copy(patch);
        builder.set(DataComponents.ATTRIBUTE_MODIFIERS, legacy);
        return builder.build();
    }

    /**
     * {@link #outgoing(Holder, DataComponentPatch)} for a stack shown in a recipe.
     *
     * @param template the stack
     * @return what to encode
     */
    public static ItemStackTemplate outgoing(final ItemStackTemplate template) {
        final DataComponentPatch patch = outgoing(template.item(), template.components());
        return patch == template.components()
            ? template : new ItemStackTemplate(template.item(), template.count(), patch);
    }

    /**
     * The components to keep from a stack a client sent, without the component
     * {@link #outgoing} gave it.
     *
     * @param item the stack's item
     * @param patch the components the client sent
     * @return what to keep; {@code patch} itself when there is nothing to remove
     */
    public static DataComponentPatch incoming(final Holder<Item> item, final DataComponentPatch patch) {
        final Object sent = patch.map.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (!(sent instanceof ItemAttributeModifiers)) {
            return patch;
        }
        final ItemAttributeModifiers legacy = legacyModifiers(item.value(), Combat.config());
        if (legacy == null || !legacy.equals(sent)) {
            return patch;
        }
        return patch.forget(type -> type == DataComponents.ATTRIBUTE_MODIFIERS);
    }

    /**
     * Whether a client is sent this stack with components the server's copy does not have, so
     * that a client which made the stack itself (creative mode) needs it sent back to it.
     *
     * @param stack the server's stack
     * @return whether the stack is rewritten on the way out
     */
    public static boolean rewritten(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final DataComponentPatch patch = stack.getComponentsPatch();
        return outgoing(stack.typeHolder(), patch) != patch;
    }

    /**
     * Whether two configurations would send different tooltips.
     *
     * @param before the previous settings
     * @param after the new ones
     * @return whether a client holding stacks sent under {@code before} is out of date
     */
    static boolean differ(final CombatConfig before, final CombatConfig after) {
        final boolean wasOn = before.enabled() && before.legacyWeaponDamage();
        final boolean isOn = after.enabled() && after.legacyWeaponDamage();
        if (wasOn != isOn) {
            return true;
        }
        return isOn && (before.swordBaseDamage() != after.swordBaseDamage()
            || before.axeBaseDamage() != after.axeBaseDamage()
            || before.pickaxeBaseDamage() != after.pickaxeBaseDamage()
            || before.shovelBaseDamage() != after.shovelBaseDamage());
    }

    /**
     * The {@code attribute_modifiers} component that shows 1.8's damage for an item.
     *
     * @param item the item
     * @param config the bases to use
     * @return the component, or {@code null} when the item is not one of the covered weapons or
     *     its default already says the same
     */
    static @Nullable ItemAttributeModifiers legacyModifiers(final Item item, final CombatConfig config) {
        Cache current = cache;
        if (current.config() != config) {
            current = new Cache(config, build(config));
            cache = current;
        }
        return current.byItem().get(item);
    }

    /** Every covered weapon's component for one configuration: 28 entries, built once. */
    private static Map<Item, ItemAttributeModifiers> build(final CombatConfig config) {
        final Map<Item, ItemAttributeModifiers> byItem = new IdentityHashMap<>();
        for (final Map.Entry<Item, Combat.Weapons.Entry> weapon : Combat.Weapons.TABLE.entrySet()) {
            final ItemAttributeModifiers defaults =
                weapon.getKey().components().get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (defaults == null) {
                continue;
            }
            final Combat.Weapons.Entry entry = weapon.getValue();
            final ItemAttributeModifiers legacy = withAttackDamage(defaults,
                LegacyCombat.weaponAttackDamage(entry.kind(), entry.materialBonus(), config));
            if (legacy != null) {
                byItem.put(weapon.getKey(), legacy);
            }
        }
        return Collections.unmodifiableMap(byItem);
    }

    /**
     * An item's default modifiers with the amount of its base attack damage modifier replaced.
     *
     * @param defaults the item's default component
     * @param amount the new amount
     * @return the new component, or {@code null} when there is no such modifier or it already has
     *     that amount (the client would drop a component equal to the default, so sending one would
     *     only make the stack look changed)
     */
    static @Nullable ItemAttributeModifiers withAttackDamage(final ItemAttributeModifiers defaults,
                                                             final double amount) {
        final List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(defaults.modifiers().size());
        boolean replaced = false;
        for (final ItemAttributeModifiers.Entry entry : defaults.modifiers()) {
            if (!replaced && entry.matches(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID)) {
                final AttributeModifier modifier = entry.modifier();
                entries.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                    new AttributeModifier(modifier.id(), amount, modifier.operation()),
                    entry.slot(), entry.display()));
                replaced = true;
            } else {
                entries.add(entry);
            }
        }
        if (!replaced) {
            return null;
        }
        final ItemAttributeModifiers result = new ItemAttributeModifiers(List.copyOf(entries));
        return result.equals(defaults) ? null : result;
    }
}
