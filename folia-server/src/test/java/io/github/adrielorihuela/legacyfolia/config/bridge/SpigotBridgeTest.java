package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.spigotmc.SpigotConfig;
import org.spigotmc.SpigotWorldConfig;

/**
 * The {@code spigot.yml} built from the TOML files against the one Spigot writes on a fresh server,
 * compared on the file Spigot saves and on every value it exposes to plugins.
 *
 * <p>Spigot only reads from a file, so the built configuration is saved to a temporary one for
 * {@code SpigotConfig.init} to load; on the server it is assigned to {@code SpigotConfig.config}
 * instead and never touches the disk.</p>
 */
@ExtendWith(ItemSanitizerFirst.class)
@AllFeatures
class SpigotBridgeTest {

    private static final Key OVERWORLD = Key.key("minecraft:overworld");

    private static final Key NETHER = Key.key("minecraft:the_nether");

    /** Spigot state a plugin can see, by field name. */
    private static Map<String, Object> spigotFields() throws IllegalAccessException {
        final Map<String, Object> values = new TreeMap<>();
        for (final Field field : SpigotConfig.class.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                && !field.getName().equals("config") && !field.getName().equals("commands")) {
                values.put(field.getName(), field.get(null));
            }
        }
        return values;
    }

    private static Map<String, Object> worldFields(final SpigotWorldConfig world) throws IllegalAccessException {
        final Map<String, Object> values = new TreeMap<>();
        for (final Field field : SpigotWorldConfig.class.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                values.put(field.getName(), field.get(world));
            }
        }
        return values;
    }

    private record Loaded(Map<String, Object> file, Map<String, Object> fields, Map<String, Object> overworld, Map<String, Object> nether) {
    }

    /** Runs Spigot over a file, as the server does at start-up, and records what it ends up with. */
    private static Loaded load(final File file) throws IllegalAccessException {
        SpigotConfig.init(file);
        final Map<String, Object> fields = spigotFields();
        final Map<String, Object> overworld = worldFields(new SpigotWorldConfig("world", OVERWORLD));
        final Map<String, Object> nether = worldFields(new SpigotWorldConfig("world_nether", NETHER));
        return new Loaded(Leaves.of(file), fields, overworld, nether);
    }

    private static Loaded bridged(final EffectiveSettings settings, final Path dir) throws IOException, IllegalAccessException {
        final File file = dir.resolve("bridged-spigot.yml").toFile();
        SpigotBridge.build(settings, SpigotBridge.vanillaWorldKeys("world")).save(file);
        return load(file);
    }

    @Test
    @DisplayName("the default TOML files give the spigot.yml, and the Spigot values, of a fresh server")
    void defaultsMatchAFreshServer(@TempDir final Path dir) throws Exception {
        final Loaded stock = load(dir.resolve("stock-spigot.yml").toFile());
        final Loaded bridged = bridged(TomlSettings.bundled(), dir);

        Leaves.assertSame(stock.file(), bridged.file(), Set.of(), "spigot.yml");
        assertEquals(stock.fields(), bridged.fields(), "SpigotConfig");
        assertEquals(stock.overworld(), bridged.overworld(), "SpigotWorldConfig of the overworld");
        assertEquals(stock.nether(), bridged.nether(), "SpigotWorldConfig of the nether");
    }

    @Test
    @DisplayName("every catalogued Spigot path is one Spigot writes")
    void everyPathIsSpigots(@TempDir final Path dir) throws Exception {
        final Map<String, Object> stock = load(dir.resolve("stock-spigot.yml").toFile()).file();
        final YamlConfiguration built = SpigotBridge.build(TomlSettings.bundled(), name -> null);
        for (final String path : Leaves.of(built).keySet()) {
            assertTrue(stock.containsKey(path), path + " is not in the spigot.yml Spigot writes");
        }
    }

    @Test
    @DisplayName("a world's override lands in world-settings.<key> and nowhere else")
    void perWorldOverride(@TempDir final Path dir) throws Exception {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [worlds.world_nether]
            "gameplay.hunger.sprint-multiplier" = 0.5
            "chunks.view-distance" = 6
            "performance.entities.activation-range.monsters" = 16
            """);
        final YamlConfiguration built = SpigotBridge.build(settings, SpigotBridge.vanillaWorldKeys("world"));
        assertEquals(0.5, built.getDouble("world-settings.minecraft:the_nether.hunger.sprint-multiplier"));
        assertEquals(6, built.getInt("world-settings.minecraft:the_nether.view-distance"));
        assertEquals(16, built.getInt("world-settings.minecraft:the_nether.entity-activation-range.monsters"));
        assertEquals(3, built.getConfigurationSection("world-settings.minecraft:the_nether").getValues(true).values().stream()
            .filter(value -> !(value instanceof org.bukkit.configuration.ConfigurationSection)).count(), "only what the world changes");
        assertNull(built.getConfigurationSection("world-settings.minecraft:overworld"));
        assertEquals("default", built.getString("world-settings.default.view-distance"));
        assertEquals(0.1, built.getDouble("world-settings.default.hunger.sprint-multiplier"));

        final Loaded loaded = bridged(settings, dir);
        assertEquals(0.5f, loaded.nether().get("sprintMultiplier"));
        assertEquals(6, loaded.nether().get("viewDistance"));
        assertEquals(16, loaded.nether().get("monsterActivationRange"));
        assertEquals(0.1f, loaded.overworld().get("sprintMultiplier"));
        assertEquals(32, loaded.overworld().get("monsterActivationRange"));
    }

    @Test
    @DisplayName("[worlds.default] goes to world-settings.default, and a world equal to it writes nothing")
    void sharedLayer() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [worlds.default]
            "gameplay.items.item-despawn-rate" = 3000
            "chunks.simulation-distance" = 8
            [worlds.world_nether]
            "gameplay.items.item-despawn-rate" = 3000
            """);
        final YamlConfiguration built = SpigotBridge.build(settings, SpigotBridge.vanillaWorldKeys("world"));
        assertEquals(3000, built.getInt("world-settings.default.item-despawn-rate"));
        assertEquals(8, built.getInt("world-settings.default.simulation-distance"));
        assertFalse(built.contains("world-settings.minecraft:the_nether"));
    }

    @Test
    @DisplayName("a world the caller cannot name yet is added when it loads")
    void addWorldLater() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [worlds.minigames]
            "gameplay.environment.thunder-chance" = 0
            """);
        final YamlConfiguration built = SpigotBridge.build(settings, SpigotBridge.vanillaWorldKeys("world"));
        assertFalse(built.contains("world-settings.minecraft:minigames"));
        SpigotBridge.addWorld(built, settings, "minigames", "minecraft:minigames");
        assertEquals(0, built.getInt("world-settings.minecraft:minigames.thunder-chance", -1));
    }

    @Test
    @DisplayName("server-wide Spigot settings and the tables they hold")
    void globalValues() {
        final YamlConfiguration built = SpigotBridge.build(TomlSettings.bundledWith("""
            [server.console]
            debug = true
            [server.saving]
            forced-stats = { "minecraft:play_time" = 5 }
            [plugins.commands]
            replace-commands = ["summon"]
            """), name -> null);
        assertTrue(built.getBoolean("settings.debug"), "debug feeds spigot's settings.debug as well");
        assertEquals(5, built.getInt("stats.forced-stats.minecraft:play_time"));
        assertEquals(java.util.List.of("summon"), built.getStringList("commands.replace-commands"));
        assertFalse(built.contains("replace-commands"), "the pre-v1 location is never written");
        assertEquals(13, built.getInt("config-version"));
    }
}
