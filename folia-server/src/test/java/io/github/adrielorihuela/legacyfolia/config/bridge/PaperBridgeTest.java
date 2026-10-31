package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.configuration.PaperConfigurations;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.DespawnRange;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRules;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.spigotmc.SpigotConfig;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The Paper nodes built from the TOML files, run through Paper's own loading — version check,
 * transformations, object mapping, post-processing — and compared with what Paper writes for a
 * fresh server.
 *
 * <p>Paper's loaders only read files, so the nodes are saved to temporary ones; on the server the
 * hook copies them into a node made by Paper's loader instead, and nothing is written.</p>
 */
@ExtendWith(ItemSanitizerFirst.class)
@AllFeatures
class PaperBridgeTest {

    /** Paper's global post-processing creates this beside the working directory; see cleanUp. */
    private static final Path FORWARDING_SECRET = Path.of("config", "forwarding.secret");

    private static boolean secretExisted;

    private static RegistryAccess access;

    @BeforeAll
    static void setUp(@TempDir final Path dir) throws Exception {
        secretExisted = Files.exists(FORWARDING_SECRET);
        access = RegistryHelper.registryAccess();
        // The world defaults are mapped against a SpigotWorldConfig, which reads SpigotConfig.
        SpigotConfig.init(dir.resolve("spigot.yml").toFile());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!secretExisted) {
            Files.deleteIfExists(FORWARDING_SECRET);
            final Path parent = FORWARDING_SECRET.getParent();
            try (var children = Files.list(parent)) {
                if (children.findAny().isEmpty()) {
                    Files.delete(parent);
                }
            } catch (final IOException ignored) {
                // not ours to remove if anything else is in it
            }
        }
    }

    private static void save(final ConfigurationNode node, final Path file) throws IOException {
        Files.createDirectories(file.getParent());
        YamlConfigurationLoader.builder().path(file).nodeStyle(NodeStyle.BLOCK).build().save(node);
    }

    private static File global(final Path dir) throws Exception {
        new PaperConfigurations(dir).initializeGlobalConfiguration(access);
        return dir.resolve("paper-global.yml").toFile();
    }

    private static File worldDefaults(final Path dir) throws Exception {
        new PaperConfigurations(dir).initializeWorldDefaultsConfiguration(access);
        return dir.resolve("paper-world-defaults.yml").toFile();
    }

    private static WorldConfiguration world(final Path configDir, final Path worldDir, final String key) {
        final SpigotWorldConfig spigot = new SpigotWorldConfig("world", net.kyori.adventure.key.Key.key(key));
        return new PaperConfigurations(configDir).createWorldConfig(PaperConfigurations.createWorldContextMap(
            worldDir, Identifier.parse(key), spigot, access, new GameRules(FeatureFlags.REGISTRY.allFlags())));
    }

    @Test
    @DisplayName("the default TOML files give the paper-global.yml of a fresh server")
    void globalDefaults(@TempDir final Path stockDir, @TempDir final Path bridgedDir) throws Exception {
        final Map<String, Object> stock = Leaves.of(global(stockDir));
        save(PaperBridge.global(TomlSettings.bundled()), bridgedDir.resolve("paper-global.yml"));
        final Map<String, Object> bridged = Leaves.of(global(bridgedDir));
        Leaves.assertSame(stock, bridged, Set.of(), "paper-global.yml");
    }

    @Test
    @DisplayName("the default TOML files give the paper-world-defaults.yml of a fresh server")
    void worldDefaultsMatch(@TempDir final Path stockDir, @TempDir final Path bridgedDir) throws Exception {
        final Map<String, Object> stock = Leaves.of(worldDefaults(stockDir));
        save(PaperBridge.worldDefaults(TomlSettings.bundled()), bridgedDir.resolve("paper-world-defaults.yml"));
        final Map<String, Object> bridged = Leaves.of(worldDefaults(bridgedDir));
        Leaves.assertSame(stock, bridged, Set.of(), "paper-world-defaults.yml");
    }

    @Test
    @DisplayName("every catalogued Paper path is one Paper writes")
    void everyPathIsPapers(@TempDir final Path dir) throws Exception {
        final Map<String, Object> global = Leaves.of(global(dir));
        final Map<String, Object> defaults = Leaves.of(worldDefaults(dir));
        for (final SettingEntry entry : SettingsCatalog.get().fromSource(LegacySource.PAPER_GLOBAL, false)) {
            assertTrue(hasPath(global, entry.legacyPath()), entry.tomlPath() + " -> " + entry.legacyPath() + " is not in paper-global.yml");
        }
        for (final String path : PaperBridge.worldPaths()) {
            assertTrue(hasPath(defaults, path), path + " is not in paper-world-defaults.yml");
        }
    }

    private static boolean hasPath(final Map<String, Object> leaves, final String path) {
        return leaves.containsKey(path) || leaves.keySet().stream().anyMatch(leaf -> leaf.startsWith(path + "."));
    }

    @Test
    @DisplayName("the nodes carry Paper's own version, so no upgrade transformation touches them")
    void versions() {
        assertEquals(PaperBridge.version(GlobalConfiguration.class), PaperBridge.global(TomlSettings.bundled()).node("_version").getInt());
        assertEquals(PaperBridge.version(WorldConfiguration.class), PaperBridge.worldDefaults(TomlSettings.bundled()).node("_version").getInt());
    }

    @Test
    @DisplayName("a world's override is in that world's node only, and Paper reads it")
    void perWorldOverride(@TempDir final Path configDir, @TempDir final Path worldDir, @TempDir final Path otherWorldDir) throws Exception {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [worlds.world_nether]
            "gameplay.environment.disable-thunder" = true
            "performance.spawning.spawn-limits.monsters" = 30
            "performance.spawning.despawn-ranges.monster-hard" = 100
            "chunks.delay-chunk-unloads-by" = 100
            "performance.spawning.despawn-ranges.shape" = "CYLINDER"
            """);
        final ConfigurationNode nether = PaperBridge.world(settings, "world_nether");
        assertTrue(nether.node("environment", "disable-thunder").getBoolean());
        assertEquals(30, nether.node("entities", "spawning", "spawn-limits", "monster").getInt());
        assertEquals(100, nether.node("entities", "spawning", "despawn-ranges", "monster", "hard").getInt());
        assertEquals("default", nether.node("entities", "spawning", "despawn-ranges", "monster", "soft").getString(),
            "Paper needs both ends of the range");
        assertEquals("5s", nether.node("chunks", "delay-chunk-unloads-by").getString());
        assertEquals(1, PaperBridge.world(settings, "world").childrenMap().size(), "the overworld has only its version");

        final ConfigurationNode defaults = PaperBridge.worldDefaults(settings);
        assertEquals(-1, defaults.node("entities", "spawning", "spawn-limits", "monster").getInt(), "still deferring to bukkit.yml");
        assertFalse(defaults.node("environment", "disable-thunder").getBoolean());

        save(defaults, configDir.resolve("paper-world-defaults.yml"));
        save(nether, worldDir.resolve("paper-world.yml"));
        final WorldConfiguration loaded = world(configDir, worldDir, "minecraft:the_nether");
        assertTrue(loaded.environment.disableThunder);
        assertEquals(30, loaded.entities.spawning.spawnLimits.getInt(MobCategory.MONSTER));
        assertEquals(-1, loaded.entities.spawning.spawnLimits.getInt(MobCategory.CREATURE));
        assertEquals(100, loaded.chunks.delayChunkUnloadsBy.ticks());
        assertEquals(DespawnRange.Shape.CYLINDER, loaded.entities.spawning.despawnRangeShape);
        final DespawnRange hard = loaded.entities.spawning.despawnRanges.get(MobCategory.MONSTER).hard();
        assertTrue(hard.shouldDespawn(DespawnRange.Shape.CYLINDER, 101 * 101, 0, 0, 0), "the world's hard range, 100");
        assertFalse(hard.shouldDespawn(DespawnRange.Shape.CYLINDER, 99 * 99, 0, 0, 0), "the world's hard range, 100");

        final WorldConfiguration overworld = world(configDir, otherWorldDir, "minecraft:overworld");
        assertFalse(overworld.environment.disableThunder);
        assertEquals(-1, overworld.entities.spawning.spawnLimits.getInt(MobCategory.MONSTER));
    }

    @Test
    @DisplayName("every despawn shape the catalogue allows is one Paper has")
    void despawnShapes() {
        for (final String shape : SettingsCatalog.get().require("performance.spawning.despawn-ranges.shape").values()) {
            DespawnRange.Shape.valueOf(shape);
        }
    }

    @Test
    @DisplayName("a shared spawn limit different from bukkit's goes to the world defaults")
    void sharedSpawnLimit() {
        final ConfigurationNode defaults = PaperBridge.worldDefaults(TomlSettings.bundledWith("""
            [worlds.default]
            "performance.spawning.spawn-limits.monsters" = 40
            """));
        assertEquals(40, defaults.node("entities", "spawning", "spawn-limits", "monster").getInt());
    }

    @Test
    @DisplayName("player saving follows the autosave interval unless it is set apart from it")
    void playerSave() {
        assertEquals(-1, PaperBridge.global(TomlSettings.bundled()).node("player-auto-save", "rate").getInt());
        assertEquals(1200, PaperBridge.global(TomlSettings.bundledWith("""
            [server.saving]
            player-save-interval-ticks = 1200
            """)).node("player-auto-save", "rate").getInt());
    }

    @Test
    @DisplayName("messages reach Paper as components: MiniMessage, and translate: as a translatable")
    void messages(@TempDir final Path dir) throws Exception {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [server.messages]
            no-permission = "<green>Ask an admin"
            kick-flying-player = "translate:multiplayer.disconnect.invalid_player_movement"
            """);
        final ConfigurationNode node = PaperBridge.global(settings);
        assertEquals("<green>Ask an admin", node.node("messages", "no-permission").getString());
        assertEquals("<lang:multiplayer.disconnect.invalid_player_movement>", node.node("messages", "kick", "flying-player").getString());

        save(node, dir.resolve("paper-global.yml"));
        final GlobalConfiguration loaded = new PaperConfigurations(dir).initializeGlobalConfiguration(access);
        assertEquals(Component.text("Ask an admin", NamedTextColor.GREEN), loaded.messages.noPermission);
        assertEquals(Component.translatable("multiplayer.disconnect.invalid_player_movement"), loaded.messages.kick.flyingPlayer);
    }

    @Test
    @DisplayName("the Velocity secret never goes into the node")
    void noSecret() {
        assertTrue(PaperBridge.global(TomlSettings.bundled()).node("proxies", "velocity", "secret").virtual());
    }
}
