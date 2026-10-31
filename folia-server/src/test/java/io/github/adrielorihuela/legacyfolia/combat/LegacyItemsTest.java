package io.github.adrielorihuela.legacyfolia.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the 1.8 rules that need the game's items and effects: what an item gives and what can be
 * crafted.
 */
@AllFeatures
class LegacyItemsTest {

    private static final CombatConfig ON = CombatConfig.vanilla18(true);

    @AfterEach
    void restore() {
        Combat.use(CombatConfig.off());
    }

    @Test
    @DisplayName("the enchanted golden apple gives 1.8's effects")
    void notchAppleEffects() {
        Combat.use(ON);
        final List<MobEffectInstance> modern = List.of(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
        final List<MobEffectInstance> legacy =
            Combat.consumeEffects(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), modern);

        assertEquals(4, legacy.size());
        assertEquals(MobEffects.ABSORPTION, legacy.get(0).getEffect());
        assertEquals(2400, legacy.get(0).getDuration());
        assertEquals(0, legacy.get(0).getAmplifier(), "Absorption I, not IV");
        assertEquals(MobEffects.REGENERATION, legacy.get(1).getEffect());
        assertEquals(600, legacy.get(1).getDuration());
        assertEquals(4, legacy.get(1).getAmplifier(), "Regeneration V for thirty seconds");
        assertEquals(6000, legacy.get(2).getDuration());
        assertEquals(6000, legacy.get(3).getDuration());

        assertSame(modern, Combat.consumeEffects(new ItemStack(Items.GOLDEN_APPLE), modern),
            "the plain golden apple already matches");
        Combat.use(CombatConfig.off());
        assertSame(modern, Combat.consumeEffects(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), modern));
    }

    @Test
    @DisplayName("shields, the off-hand and totems are refused, each on its own switch")
    void missingItems() {
        Combat.use(ON);
        org.junit.jupiter.api.Assertions.assertTrue(Combat.shieldDisabled(new ItemStack(Items.SHIELD)));
        org.junit.jupiter.api.Assertions.assertFalse(Combat.shieldDisabled(new ItemStack(Items.DIAMOND_SWORD)));
        org.junit.jupiter.api.Assertions.assertTrue(Combat.offhandDisabled());
        org.junit.jupiter.api.Assertions.assertTrue(Combat.totemsDisabled());
        Combat.use(CombatConfigs.with(ON, "disableTotems", false));
        org.junit.jupiter.api.Assertions.assertFalse(Combat.totemsDisabled());
        org.junit.jupiter.api.Assertions.assertTrue(Combat.offhandDisabled());
        Combat.use(CombatConfig.off());
        org.junit.jupiter.api.Assertions.assertFalse(Combat.shieldDisabled(new ItemStack(Items.SHIELD)));
        org.junit.jupiter.api.Assertions.assertFalse(Combat.offhandDisabled());
    }

    @Test
    @DisplayName("eight blocks of gold around an apple make an enchanted golden apple again")
    void notchAppleRecipe() {
        Combat.use(ON);
        final RecipeMap recipes = RecipeMap.createEmpty();
        Combat.addLegacyRecipes(recipes);
        Combat.addLegacyRecipes(recipes);

        final RecipeHolder<?> holder = recipes.byKey.get(ResourceKey.create(Registries.RECIPE,
            Identifier.fromNamespaceAndPath("legacyfolia", "enchanted_golden_apple")));
        assertNotNull(holder, "added once, and a second load does not add it twice");
        assertEquals(1, recipes.byKey.size());

        Combat.use(CombatConfig.off());
        final RecipeMap none = RecipeMap.createEmpty();
        Combat.addLegacyRecipes(none);
        assertEquals(0, none.byKey.size());
    }
}
