package io.github.adrielorihuela.legacyfolia.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.bridge.ItemSanitizerFirst;
import io.netty.buffer.Unpooled;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Checks what a client is sent for a weapon, and what the server keeps of what a client sends back.
 *
 * <p>The encoding tests go through the game's own item codecs, so they check the bytes a client
 * would receive, not only the helper that builds them.</p>
 */
@ExtendWith(ItemSanitizerFirst.class)
@AllFeatures
class WeaponTooltipsTest {

    private static final CombatConfig ON = CombatConfig.vanilla18(true);

    /** Any consistent hash will do: the client and the server only have to agree. */
    private static final HashedPatchMap.HashGenerator HASHER = component -> component.value().hashCode();

    @AfterEach
    void restore() {
        Combat.use(CombatConfig.off());
    }

    @Test
    @DisplayName("a plain sword is sent with 1.8's attack damage, the speed modifier untouched")
    void plainWeaponIsSentWithLegacyDamage() {
        Combat.use(ON);
        final ItemAttributeModifiers sent = sentModifiers(new ItemStack(Items.DIAMOND_SWORD));
        assertNotNull(sent, "the component is added");

        final ItemAttributeModifiers defaults =
            Items.DIAMOND_SWORD.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertEquals(defaults.modifiers().size(), sent.modifiers().size());
        final ItemAttributeModifiers.Entry damage = entry(sent, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
        final ItemAttributeModifiers.Entry modernDamage = entry(defaults, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
        // 1 + 7 = 8, which is what the client draws ("8 Attack Damage"): its own 1.0 plus this.
        assertEquals(7.0, damage.modifier().amount());
        assertEquals(modernDamage.modifier().operation(), damage.modifier().operation());
        assertEquals(modernDamage.slot(), damage.slot());
        assertEquals(modernDamage.display(), damage.display());
        assertEquals(entry(defaults, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID),
            entry(sent, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID));
    }

    @Test
    @DisplayName("the figure sent is the one the hit uses, for every kind and material")
    void sentFigureMatchesTheHit() {
        Combat.use(ON);
        final Object[][] cases = {
            {Items.WOODEN_SWORD, 4.0}, {Items.STONE_SWORD, 5.0}, {Items.IRON_SWORD, 6.0},
            {Items.GOLDEN_SWORD, 4.0}, {Items.NETHERITE_SWORD, 8.0}, {Items.COPPER_SWORD, 5.0},
            {Items.WOODEN_AXE, 3.0}, {Items.DIAMOND_AXE, 6.0}, {Items.IRON_PICKAXE, 4.0},
            {Items.DIAMOND_PICKAXE, 5.0}, {Items.STONE_SHOVEL, 2.0}, {Items.NETHERITE_SHOVEL, 5.0},
        };
        for (final Object[] c : cases) {
            final Item item = (Item) c[0];
            final ItemAttributeModifiers sent = sentModifiers(new ItemStack(item));
            assertNotNull(sent, item.toString());
            assertEquals((double) c[1],
                entry(sent, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID).modifier().amount(),
                item.toString());
        }
    }

    @Test
    @DisplayName("with the setting off, or the module off, stacks are sent exactly as they are")
    void nothingIsAddedWhenOff() {
        final DataComponentPatch patch = new ItemStack(Items.DIAMOND_SWORD).getComponentsPatch();
        final Holder<Item> sword = Items.DIAMOND_SWORD.builtInRegistryHolder();

        Combat.use(with(ON, "legacyWeaponDamage", false));
        assertSame(patch, WeaponTooltips.outgoing(sword, patch));
        assertNull(sentModifiers(new ItemStack(Items.DIAMOND_SWORD)));

        Combat.use(CombatConfig.off());
        assertSame(patch, WeaponTooltips.outgoing(sword, patch));
    }

    @Test
    @DisplayName("a weapon with modifiers of its own, or with them removed, is left alone")
    void customModifiersAreLeftAlone() {
        Combat.use(ON);
        final ItemStack custom = new ItemStack(Items.DIAMOND_SWORD);
        final ItemAttributeModifiers plugins = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 20.0,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
        custom.set(DataComponents.ATTRIBUTE_MODIFIERS, plugins);
        assertEquals(plugins, sentModifiers(custom));
        assertFalse(WeaponTooltips.rewritten(custom));

        final ItemStack removed = new ItemStack(Items.DIAMOND_SWORD);
        removed.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        final DataComponentPatch patch = removed.getComponentsPatch();
        assertSame(patch, WeaponTooltips.outgoing(removed.typeHolder(), patch));
    }

    @Test
    @DisplayName("the trident, the mace, hoes and everything else are sent as they are")
    void otherItemsAreLeftAlone() {
        Combat.use(ON);
        for (final Item item : List.of(Items.TRIDENT, Items.MACE, Items.DIAMOND_HOE, Items.STICK,
            Items.BOW)) {
            final ItemStack stack = new ItemStack(item);
            final DataComponentPatch patch = stack.getComponentsPatch();
            assertSame(patch, WeaponTooltips.outgoing(stack.typeHolder(), patch), item.toString());
            assertFalse(WeaponTooltips.rewritten(stack), item.toString());
        }
    }

    @Test
    @DisplayName("the stack on the server keeps its modern components while one is sent")
    void storedStackIsNotChanged() {
        Combat.use(ON);
        final ItemStack stack = new ItemStack(Items.IRON_AXE);
        encode(stack);
        assertFalse(stack.hasNonDefault(DataComponents.ATTRIBUTE_MODIFIERS));
        assertTrue(WeaponTooltips.rewritten(stack));
    }

    @Test
    @DisplayName("a creative client sending back what it was given leaves the server's copy at the default")
    void creativeRoundTripStripsTheComponent() {
        Combat.use(ON);
        final ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));

        // What the client holds: exactly what it was sent.
        final ItemStack client = clientCopy(original);
        assertTrue(client.hasNonDefault(DataComponents.ATTRIBUTE_MODIFIERS));

        // What it sends back in ServerboundSetCreativeModeSlotPacket.
        final RegistryFriendlyByteBuf buf = buffer();
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buf, client);
        final ItemStack received =
            ItemStack.validatedStreamCodec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC).decode(buf);

        assertFalse(received.hasNonDefault(DataComponents.ATTRIBUTE_MODIFIERS));
        assertTrue(ItemStack.matches(original, received), "the name and everything else survive");
    }

    @Test
    @DisplayName("the component is taken back out even after the setting was turned off")
    void stripWorksAfterTurningOff() {
        Combat.use(ON);
        final ItemStack client = clientCopy(new ItemStack(Items.STONE_PICKAXE));
        Combat.use(with(ON, "legacyWeaponDamage", false));

        final DataComponentPatch kept = WeaponTooltips.incoming(client.typeHolder(), client.getComponentsPatch());
        assertTrue(kept.isEmpty());
    }

    @Test
    @DisplayName("modifiers a client sends that are not exactly the 1.8 ones are kept")
    void otherModifiersAreKept() {
        Combat.use(ON);
        final ItemAttributeModifiers almost = WeaponTooltips.withAttackDamage(
            Items.DIAMOND_SWORD.components().get(DataComponents.ATTRIBUTE_MODIFIERS), 7.5);
        final DataComponentPatch patch = DataComponentPatch.builder()
            .set(DataComponents.ATTRIBUTE_MODIFIERS, almost).build();
        assertSame(patch, WeaponTooltips.incoming(Items.DIAMOND_SWORD.builtInRegistryHolder(), patch));

        // The 1.8 sword component on a different item is not that item's, so it stays too.
        final DataComponentPatch onAxe = DataComponentPatch.builder()
            .set(DataComponents.ATTRIBUTE_MODIFIERS,
                WeaponTooltips.legacyModifiers(Items.DIAMOND_SWORD, ON)).build();
        assertSame(onAxe, WeaponTooltips.incoming(Items.DIAMOND_AXE.builtInRegistryHolder(), onAxe));
    }

    @Test
    @DisplayName("a sword a creative client put in a bundle comes back without the component")
    void nestedStacksAreStripped() {
        Combat.use(ON);
        final ItemStack client = clientCopy(new ItemStack(Items.IRON_SWORD));
        final BundleContents contents = new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(client)));

        final RegistryFriendlyByteBuf buf = buffer();
        BundleContents.STREAM_CODEC.encode(buf, contents);
        final BundleContents received = BundleContents.STREAM_CODEC.decode(buf);

        final ItemStackTemplate inside = received.items().get(0);
        assertTrue(inside.components().isEmpty());
    }

    @Test
    @DisplayName("a container click with the stack the client was sent matches the server's stack")
    void hashesMatchWhatTheClientHolds() {
        Combat.use(ON);
        final ItemStack server = new ItemStack(Items.DIAMOND_SWORD);
        final HashedStack fromClient = HashedStack.create(clientCopy(server), HASHER);
        assertTrue(fromClient.matches(server, HASHER));

        // A client still holding the modern stack is out of date, and gets the slot sent again.
        assertFalse(HashedStack.create(server, HASHER).matches(server, HASHER));

        Combat.use(CombatConfig.off());
        assertTrue(HashedStack.create(server, HASHER).matches(server, HASHER));
    }

    @Test
    @DisplayName("a weapon shown in the recipe book is sent with 1.8's damage")
    void recipeDisplayIsRewritten() {
        Combat.use(ON);
        final RegistryFriendlyByteBuf buf = buffer();
        SlotDisplay.ItemStackSlotDisplay.STREAM_CODEC.encode(buf,
            new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.DIAMOND_AXE)));

        Item.STREAM_CODEC.decode(buf);
        buf.readVarInt();
        final DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
        final ItemAttributeModifiers sent = patch.get(Items.DIAMOND_AXE.components(), DataComponents.ATTRIBUTE_MODIFIERS);
        assertEquals(6.0, entry(sent, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID).modifier().amount());
    }

    @Test
    @DisplayName("a reload only resends inventories when the tooltips it sends change")
    void reloadResendsOnlyOnChange() {
        assertFalse(WeaponTooltips.differ(ON, CombatConfig.vanilla18(true)));
        assertTrue(WeaponTooltips.differ(ON, with(ON, "legacyWeaponDamage", false)));
        assertTrue(WeaponTooltips.differ(ON, CombatConfig.off()));
        assertTrue(WeaponTooltips.differ(ON, with(ON, "swordBaseDamage", 5.0)));
        assertFalse(WeaponTooltips.differ(CombatConfig.off(), with(CombatConfig.off(), "swordBaseDamage", 5.0)));
        assertFalse(WeaponTooltips.differ(ON, with(ON, "legacyKnockback", false)));
    }

    /** The attribute modifiers the client ends up with, read straight from the encoded bytes. */
    private static ItemAttributeModifiers sentModifiers(final ItemStack stack) {
        final RegistryFriendlyByteBuf buf = encode(stack);
        buf.readVarInt();
        final Holder<Item> item = Item.STREAM_CODEC.decode(buf);
        final DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
        final Object value = patch.map.get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertSame(stack.typeHolder().value(), item.value());
        return value instanceof ItemAttributeModifiers modifiers ? modifiers : null;
    }

    /** The stack a client builds from what it is sent, without the server's decoding. */
    private static ItemStack clientCopy(final ItemStack stack) {
        final RegistryFriendlyByteBuf buf = encode(stack);
        final int count = buf.readVarInt();
        final Holder<Item> item = Item.STREAM_CODEC.decode(buf);
        return new ItemStack(item, count, DataComponentPatch.STREAM_CODEC.decode(buf));
    }

    private static RegistryFriendlyByteBuf encode(final ItemStack stack) {
        final RegistryFriendlyByteBuf buf = buffer();
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        return buf;
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryHelper.registryAccess());
    }

    private static ItemAttributeModifiers.Entry entry(final ItemAttributeModifiers modifiers,
                                                      final Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                      final net.minecraft.resources.Identifier id) {
        return modifiers.modifiers().stream().filter(e -> e.matches(attribute, id)).findFirst().orElseThrow();
    }

    /** A copy of a configuration with one setting changed. */
    private static CombatConfig with(final CombatConfig config, final String name, final Object value) {
        try {
            final RecordComponent[] components = CombatConfig.class.getRecordComponents();
            final Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                values[i] = components[i].getName().equals(name)
                    ? value : components[i].getAccessor().invoke(config);
            }
            final Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            return CombatConfig.class.getDeclaredConstructor(types).newInstance(values);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
