package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.Warning;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code bukkit.yml} and {@code commands.yml} built from the TOML files against the templates
 * {@code CraftServer} writes for a fresh server, which are the files a fresh server has.
 */
class BukkitBridgeTest {

    private static YamlConfiguration template(final String name) {
        final InputStream in = BukkitBridgeTest.class.getClassLoader().getResourceAsStream("configurations/" + name);
        assertNotNull(in, name + " template");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the default TOML files give the bukkit.yml of a fresh server")
    void bukkitDefaults() {
        Leaves.assertSame(Leaves.of(template("bukkit.yml")), Leaves.of(BukkitBridge.bukkit(TomlSettings.bundled())), Set.of(), "bukkit.yml");
    }

    @Test
    @DisplayName("the default TOML files give the commands.yml of a fresh server")
    void commandsDefaults() {
        Leaves.assertSame(Leaves.of(template("commands.yml")), Leaves.of(BukkitBridge.commands(TomlSettings.bundled())), Set.of(), "commands.yml");
    }

    @Test
    @DisplayName("generators, biome providers, aliases and the three-state deprecation warning")
    void tablesAndSpecialTypes() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [server.console]
            deprecated-verbose = true
            [world.generators]
            world = "Terra:overworld"
            skyblock = "VoidGen"
            [world.biome-providers]
            world = "Terra:biomes"
            islands = "OceanOnly"
            [plugins.commands.aliases]
            icanhasbukkit = ["version $1-"]
            spawnme = ["spawn", "heal"]
            [performance.spawning.spawn-limits]
            monsters = 30
            """);
        final YamlConfiguration bukkit = BukkitBridge.bukkit(settings);
        assertEquals("Terra:overworld", bukkit.getString("worlds.world.generator"));
        assertEquals("VoidGen", bukkit.getString("worlds.skyblock.generator"));
        assertEquals("Terra:biomes", bukkit.getString("worlds.world.biome-provider"), "beside the world's generator");
        assertEquals("Terra:overworld", bukkit.getString("worlds.world.generator"), "and without replacing it");
        assertEquals("OceanOnly", bukkit.getString("worlds.islands.biome-provider"));
        assertEquals(null, bukkit.getString("worlds.islands.generator"));
        assertEquals(Warning.WarningState.ON, Warning.WarningState.value(bukkit.getString("settings.deprecated-verbose")));
        assertEquals(Warning.WarningState.DEFAULT, Warning.WarningState.value(BukkitBridge.bukkit(TomlSettings.bundled()).getString("settings.deprecated-verbose")));
        assertEquals(30, bukkit.getInt("spawn-limits.monsters"));
        assertEquals("now-in-commands.yml", bukkit.getString("aliases"));

        final YamlConfiguration commands = BukkitBridge.commands(settings);
        assertEquals(List.of("spawn", "heal"), commands.getStringList("aliases.spawnme"));
    }

    @Test
    @DisplayName("an operator who removed every alias keeps none")
    void emptyAliases() {
        final YamlConfiguration commands = BukkitBridge.commands(TomlSettings.bundledWith("""
            [plugins.commands]
            aliases = {}
            """));
        assertTrue(commands.contains("aliases"), "the section is there, so CraftServer does not add its default");
        assertTrue(commands.getConfigurationSection("aliases").getKeys(false).isEmpty());
    }

    @Test
    @DisplayName("a per-world spawn limit does not leak into the server-wide bukkit.yml")
    void perWorldStaysOut() {
        final YamlConfiguration bukkit = BukkitBridge.bukkit(TomlSettings.bundledWith("""
            [worlds.world_nether]
            "performance.spawning.spawn-limits.monsters" = 5
            """));
        assertEquals(70, bukkit.getInt("spawn-limits.monsters"));
        assertFalse(bukkit.contains("worlds"));
    }
}
