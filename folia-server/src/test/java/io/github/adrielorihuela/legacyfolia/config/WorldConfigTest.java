package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The resolution chain is what stops a world losing a setting it was tuned with, so every step of
 * it is pinned down here: the world, then every world, then the global file, then the code.
 */
class WorldConfigTest {

    private static final String RANGE = "performance.entities.entity-activation-range.animals";
    private static final String THUNDER = "gameplay.environment.thunder-chance";

    /** The global sections, as they would come out of the files. */
    private static UnmodifiableConfig globals() {
        final Config globals = Config.inMemory();
        globals.set(RANGE, 32);
        globals.set(THUNDER, 100000);
        globals.set("gameplay.items.item-despawn-rate", 6000);
        return globals;
    }

    private static UnmodifiableConfig worlds() {
        final Config worlds = Config.inMemory();
        // Both spellings an operator may use, since the file documents both.
        worlds.set(List.of("default", RANGE), 16);
        worlds.set(List.of("world_nether", THUNDER), 0);
        worlds.set(List.of("world_nether", "gameplay", "items", "item-despawn-rate"), 1200);
        return worlds;
    }

    private static WorldConfig forWorld(final String name) {
        return WorldConfig.resolve(name, List.of(globals()), worlds());
    }

    @Test
    @DisplayName("a world that says nothing falls back to the default section")
    void fallsBackToTheDefaultSection() {
        assertEquals(16, forWorld("world").getInt(RANGE, 99),
            "[worlds.default] should have been applied over the global file");
    }

    @Test
    @DisplayName("a world that says nothing anywhere falls back to the global file")
    void fallsBackToTheGlobalSection() {
        assertEquals(100000, forWorld("world").getInt(THUNDER, 99),
            "no world overrides thunder, so the global value stands");
    }

    @Test
    @DisplayName("a setting nobody configured falls back to the caller's own default")
    void fallsBackToTheCodeDefault() {
        assertEquals(7, forWorld("world").getInt("gameplay.mobs.not-a-setting", 7));
    }

    @Test
    @DisplayName("a world's own value wins over both the default section and the file")
    void theWorldWins() {
        final WorldConfig nether = forWorld("world_nether");

        assertEquals(0, nether.getInt(THUNDER, 99));
        assertEquals(1200, nether.getInt("gameplay.items.item-despawn-rate", 99),
            "a nested table override should resolve the same as a quoted dotted one");
        assertEquals(16, nether.getInt(RANGE, 99),
            "a world overriding one setting must keep the default section's answer to the rest");
    }

    @Test
    @DisplayName("a world with no section of its own is resolved rather than refused")
    void unknownWorldsResolve() {
        final WorldConfig unknown = forWorld("a_world_nobody_configured");

        assertEquals("a_world_nobody_configured", unknown.world());
        assertEquals(16, unknown.getInt(RANGE, 99), "it should see [worlds.default]");
        assertEquals(100000, unknown.getInt(THUNDER, 99), "and the global file behind it");
    }

    @Test
    @DisplayName("a server with no worlds file at all still resolves")
    void resolvesWithoutAWorldsSection() {
        final WorldConfig world = WorldConfig.resolve("world", List.of(globals()), null);

        assertEquals(32, world.getInt(RANGE, 99), "the global value, with nothing over it");
    }

    @Test
    @DisplayName("the types a setting can have all read back")
    void readsEveryType() {
        final Config globals = Config.inMemory();
        globals.set("gameplay.mobs.flag", true);
        globals.set("gameplay.growth.wheat", 1.5);
        globals.set("gameplay.redstone.implementation", "  vanilla  ");
        globals.set("gameplay.exploits.disabled", List.of("one", "  two  ", " "));
        final WorldConfig world = WorldConfig.resolve("world", List.of(globals), null);

        assertTrue(world.getBoolean("gameplay.mobs.flag", false));
        assertEquals(1.5, world.getDouble("gameplay.growth.wheat", 0));
        assertEquals("vanilla", world.getString("gameplay.redstone.implementation", ""));
        assertIterableEquals(List.of("one", "two"),
            world.getStringList("gameplay.exploits.disabled"));
        assertIterableEquals(List.of(), world.getStringList("gameplay.exploits.missing"));
    }

    @Test
    @DisplayName("a setting of the wrong type falls back rather than throwing")
    void wrongTypesFallBack() {
        final Config globals = Config.inMemory();
        globals.set("gameplay.mobs.flag", "not a boolean");
        final WorldConfig world = WorldConfig.resolve("world", List.of(globals), null);

        assertTrue(world.getBoolean("gameplay.mobs.flag", true),
            "somebody's typo should not decide the setting");
    }
}
