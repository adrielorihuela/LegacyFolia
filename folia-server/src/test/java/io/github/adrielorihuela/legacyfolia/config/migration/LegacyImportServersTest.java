package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.BACKUP;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.copy;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.override;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.report;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.run;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.toml;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whole servers, as vanilla, Spigot, Paper and Folia leave them, imported end to end.
 */
class LegacyImportServersTest {

    @Test
    @DisplayName("vanilla: server.properties and eula.txt alone")
    void vanilla(@TempDir final Path root) throws IOException {
        copy("vanilla", root);

        final Optional<LegacyImport.Result> result = run(root);

        assertTrue(result.isPresent());
        assertEquals(50, toml(root, "server.toml", "server.max-players"));
        assertEquals("normal", toml(root, "server.toml", "server.difficulty"), "vanilla's numeric difficulty 2");
        assertEquals("Survival été server", toml(root, "server.toml", "server.motd"));
        assertEquals(16, toml(root, "server.toml", "server.spawn-protection-radius"), "the server ran with vanilla's 16");
        assertEquals(true, toml(root, "server.toml", "server.access.white-list"));
        assertEquals(true, toml(root, "server.toml", "server.eula"), "eula=true becomes server.eula");
        assertEquals(60, toml(root, "server.toml", "server.pause-when-empty-seconds"), "only Paper forces -1");
        assertEquals(List.of("vanilla", "minecart_improvements"),
            toml(root, "server.toml", "server.data-packs.initial-enabled-packs"));
        assertEquals("-4172144997902289642", toml(root, "world.toml", "world.generation.level-seed"));
        assertEquals(false, toml(root, "network.toml", "network.rcon.enabled"));
        assertEquals(12, toml(root, "performance.toml", "chunks.view-distance"));
        assertEquals("0f3a4ea9c2b0d5a1e1a7dcd0f5f7a0bb3c4e5d6f", toml(root, "server.toml", "server.resource-pack.sha1"),
            "resource-pack-sha1 is empty, so vanilla's fallback to resource-pack-hash applies");

        // The RCON password goes to its secret file and never into a TOML file or the report.
        assertEquals("hunter2\n", Files.readString(root.resolve("config/rcon.secret")));
        final String report = report(root);
        assertFalse(report.contains("hunter2"));
        assertFalse(report.contains("Xq0b8w2YlTqzJ4r1"), "the management secret is reported but not shown");
        assertTrue(report.contains("management-server-secret = (hidden)"));

        // Nothing landed in the files no vanilla setting belongs to.
        assertFalse(Files.exists(root.resolve("config/combat.toml")));
        assertFalse(Files.exists(root.resolve("config/gameplay.toml")));

        assertEquals(List.of("eula.txt", "import-report.txt", "server.properties"), tree(root.resolve(BACKUP)));
        assertFalse(Files.exists(root.resolve("server.properties")));
        assertFalse(Files.exists(root.resolve("eula.txt")));
        assertFalse(report.contains("online-mode"), "a value equal to the default is not imported");
    }

    @Test
    @DisplayName("Spigot: bukkit.yml, spigot.yml and commands.yml in the root")
    void spigot(@TempDir final Path root) throws IOException {
        copy("spigot", root);

        run(root).orElseThrow();

        assertEquals(50, toml(root, "performance.toml", "performance.spawning.spawn-limits.monsters"));
        assertEquals(3000, toml(root, "performance.toml", "chunks.auto-save-interval"));
        assertEquals(false, toml(root, "world.toml", "dimensions.end"));
        assertEquals(Map.of("world", "TerraformGenerator"), toml(root, "world.toml", "world.generators"));
        assertEquals(Map.of("world", "TerraformBiomes"), toml(root, "world.toml", "world.biome-providers"));
        assertEquals(2048.0, toml(root, "combat.toml", "combat.attribute-limits.max-health"));
        assertEquals(true, toml(root, "network.toml", "network.bungeecord.enabled"));
        assertEquals(true, toml(root, "server.toml", "server.console.debug"), "debug=true in server.properties");
        assertEquals(0, toml(root, "server.toml", "server.status.sample-count"), "SpigotConfig reads -5 as 0");
        assertEquals("<red>You are not on the <bold>whitelist</bold></red><red>!",
            toml(root, "server.toml", "server.messages.whitelist"));
        assertEquals("<red>Server closed for maintenance", toml(root, "server.toml", "server.messages.shutdown"));
        assertEquals(-1, toml(root, "plugins.toml", "plugins.commands.tab-complete"), "a pre-v6 false is -1");
        assertEquals(List.of("/skill", "/home"), toml(root, "network.toml", "network.limits.spam-exclusions"));
        assertEquals(true, toml(root, "network.toml", "network.limits.enable-spam-exclusions"),
            "absent, and deduced from a list that is no longer the factory one");
        assertEquals(List.of("version $1-"), ((Map<?, ?>) toml(root, "plugins.toml", "plugins.commands.aliases")).get("icanhasbukkit"));
        assertEquals(List.of("warp spawn"), ((Map<?, ?>) toml(root, "plugins.toml", "plugins.commands.aliases")).get("spawn"));
        assertEquals(List.of("minecraft:story/disabled"), toml(root, "server.toml", "server.saving.disabled-advancements"));

        // world-settings.default goes to the global sections.
        assertEquals(6, toml(root, "performance.toml", "performance.spawning.mob-spawn-range"));
        assertEquals(24, toml(root, "performance.toml", "performance.entities.activation-range.monsters"));
        assertEquals(150, toml(root, "gameplay.toml", "gameplay.growth.wheat-modifier"));
        assertEquals(100, toml(root, "gameplay.toml", "gameplay.growth.cactus-modifier"), "Spigot reads 0 as 100");
        assertEquals(300, toml(root, "gameplay.toml", "gameplay.items.arrow-despawn-rate"));
        assertEquals(300, toml(root, "gameplay.toml", "gameplay.items.trident-despawn-rate"), "follows the arrow rate");
        assertEquals(10, toml(root, "performance.toml", "chunks.view-distance"));

        // world-settings.<world> goes to [worlds.<world>].
        assertEquals(4, override(root, "world_nether", "chunks.view-distance"));
        assertEquals(16, override(root, "world_nether", "performance.entities.activation-range.monsters"));
        assertEquals(600, override(root, "world_the_end", "gameplay.items.arrow-despawn-rate"));
        assertEquals(600, override(root, "world_the_end", "gameplay.items.trident-despawn-rate"));
        assertNull(override(root, "default", "chunks.view-distance"), "view-distance: default defers to server.properties");

        final String report = report(root);
        assertEquals(999, override(root, "world_nether", "world.structure-seeds.villages"));
        assertEquals(10387312, toml(root, "world.toml", "world.structure-seeds.villages"), "the vanilla salt, unchanged");
        assertFalse(report.contains("the catalog has no key for it"), report);
        assertTrue(report.contains("world-settings.world_nether.below-zero-generation-in-existing-chunks"));
        assertTrue(report.contains("settings.compatibility"));
        assertTrue(report.contains("config-version (file metadata)"));
        assertEquals(List.of("bukkit.yml", "commands.yml", "eula.txt", "import-report.txt", "server.properties", "spigot.yml"),
            tree(root.resolve(BACKUP)));
    }

    @Test
    @DisplayName("Paper: Paper's files in config/ and a paper-world.yml per world")
    void paper(@TempDir final Path root) throws IOException {
        copy("paper", root);

        run(root).orElseThrow();

        // Spawn limits (SETTINGS-MAP 3.4): -1 defers to Bukkit, >= 0 wins.
        assertEquals(10, toml(root, "performance.toml", "performance.spawning.spawn-limits.animals"));
        assertEquals(5, override(root, "default", "performance.spawning.spawn-limits.animals"));
        assertEquals(10, override(root, "world_nether", "performance.spawning.spawn-limits.animals"),
            "the Nether's explicit -1 gives it Bukkit's value back");
        assertEquals(70, toml(root, "performance.toml", "performance.spawning.spawn-limits.monsters"));
        assertNull(override(root, "default", "performance.spawning.spawn-limits.monsters"));
        assertEquals(30, override(root, "world", "performance.spawning.spawn-limits.monsters"));
        assertEquals(1200, override(root, "world_nether", "chunks.auto-save-interval"));
        assertNull(override(root, "default", "chunks.auto-save-interval"));

        // View distance (SETTINGS-MAP 3.3).
        assertEquals(10, toml(root, "performance.toml", "chunks.view-distance"));
        assertEquals(8, override(root, "default", "chunks.view-distance"));
        assertEquals(10, override(root, "world_the_end", "chunks.view-distance"), "default in one world cancels the 8");

        assertEquals(400, toml(root, "performance.toml", "chunks.delay-chunk-unloads-by"), "20s is 400 ticks");
        assertEquals("6h", toml(root, "gameplay.toml", "gameplay.items.lootables-refresh-min"));
        assertEquals("alternate_current", toml(root, "gameplay.toml", "gameplay.redstone.redstone-implementation"));
        assertEquals(2, toml(root, "gameplay.toml", "gameplay.exploits.anti-xray-engine-mode"));
        assertEquals(1, override(root, "world_nether", "gameplay.exploits.anti-xray-engine-mode"));
        assertEquals(Map.of("minecraft:cobblestone", 300, "minecraft:netherrack", 300),
            toml(root, "gameplay.toml", "gameplay.items.alt-item-despawn-rate-items"));

        assertEquals(true, toml(root, "network.toml", "network.velocity.enabled"));
        assertEquals("forwarding.secret", toml(root, "network.toml", "network.velocity.secret-file"));
        assertEquals("velocity-forwarding-s3cret\n", Files.readString(root.resolve("config/forwarding.secret")));
        assertEquals(false, toml(root, "world.toml", "dimensions.nether"));
        assertEquals("hard", toml(root, "server.toml", "server.difficulty"));
        assertEquals("§aPaper survival", toml(root, "server.toml", "server.motd"), "server.motd is a plain string");
        assertEquals("aaaabbbbccccddddeeeeffff0000111122223333", toml(root, "server.toml", "server.resource-pack.sha1"));
        assertEquals(-1, toml(root, "server.toml", "server.pause-when-empty-seconds"), "Paper forces -1; 60 never applied");
        assertEquals(List.of("setblock", "summon"), toml(root, "plugins.toml", "plugins.commands.replace-commands"),
            "the root replace-commands wins, as SpigotConfig moves it under commands.");
        assertEquals(true, toml(root, "server.toml", "server.console.debug"), "settings.debug in spigot.yml");
        assertEquals("translate:multiplayer.disconnect.flying", toml(root, "server.toml", "server.messages.kick-flying-player"));
        assertEquals(true, toml(root, "server.toml", "server.messages.use-display-name-in-quit-message"));
        assertEquals("<red><lang:disconnect.exceeded_packet_rate>", toml(root, "server.toml", "server.messages.kick-packet-limit"));

        final String report = report(root);
        for (final String omitted : List.of("door-breaking-difficulty", "tick-rates.sensor", "entity-per-chunk-save-limit",
            "prevent-moving-into-unloaded-chunks", "packet-limiter.overrides")) {
            assertTrue(report.contains(omitted), omitted + " must be in the report");
        }
        assertTrue(report.contains("player-auto-save.rate = -1 means the chunk autosave interval"));
        assertFalse(report.contains("velocity-forwarding-s3cret"));

        assertEquals(List.of("bukkit.yml", "config/paper-global.yml",
                "config/paper-world-defaults.yml", "import-report.txt", "server.properties", "spigot.yml",
                "world/paper-world.yml", "world_nether/paper-world.yml"),
            tree(root.resolve(BACKUP)));
        assertFalse(Files.exists(root.resolve("world/paper-world.yml")));
    }

    @Test
    @DisplayName("Folia: a world container and Paper 26's dimension folders")
    void folia(@TempDir final Path root) throws IOException {
        copy("folia", root);

        run(root).orElseThrow();

        assertEquals("survival", toml(root, "server.toml", "server.level-name"));
        assertEquals(300, toml(root, "server.toml", "server.max-players"));
        assertEquals("worlds", toml(root, "world.toml", "world.storage.world-container"));
        assertEquals(8, toml(root, "performance.toml", "performance.regions.threads"));
        assertEquals("WORK_STEALING", toml(root, "performance.toml", "performance.regions.scheduler"));
        assertEquals("eigencraft", toml(root, "gameplay.toml", "gameplay.redstone.redstone-implementation"));
        assertEquals("alternate_current", override(root, "survival", "gameplay.redstone.redstone-implementation"));
        assertEquals("1d", override(root, "survival_nether", "gameplay.items.lootables-refresh-min"));
        assertEquals(4, override(root, "survival_nether", "performance.spawning.mob-spawn-range"));
        assertFalse(Files.exists(root.resolve("config/forwarding.secret")), "an empty secret is not a secret");

        assertEquals(List.of("bukkit.yml", "config/paper-global.yml", "config/paper-world-defaults.yml",
                "import-report.txt", "server.properties", "spigot.yml",
                "worlds/survival/dimensions/minecraft/the_nether/paper-world.yml", "worlds/survival/paper-world.yml"),
            tree(root.resolve(BACKUP)));
    }

    @Test
    @DisplayName("a LegacyFolia that kept the legacy files in config/: those are imported, root strays only backed up")
    void legacyFoliaConfigFolder(@TempDir final Path root) throws IOException {
        ImportFixtures.write(root, "config/bukkit.yml", "spawn-limits:\n  monsters: 40\n");
        ImportFixtures.write(root, "bukkit.yml", "spawn-limits:\n  monsters: 99\n");
        ImportFixtures.write(root, "config/server.properties", "#Minecraft server properties\nmax-players=33\n");

        run(root).orElseThrow();

        assertEquals(40, toml(root, "performance.toml", "performance.spawning.spawn-limits.monsters"));
        assertEquals(33, toml(root, "server.toml", "server.max-players"));
        assertEquals(List.of("bukkit.yml", "config/bukkit.yml", "config/server.properties", "import-report.txt"),
            tree(root.resolve(BACKUP)));
        assertTrue(report(root).contains("Files backed up but not imported"));
    }

    @Test
    @DisplayName("comments in the TOML files survive the import")
    void keepsComments(@TempDir final Path root) throws IOException {
        copy("paper", root);

        run(root).orElseThrow();

        final String worlds = Files.readString(root.resolve("config/worlds.toml"), StandardCharsets.UTF_8);
        assertTrue(worlds.contains("How a value is chosen"), "the header comment of worlds.toml");
        assertTrue(worlds.contains("Worked examples"), "the comment block at the end, which has no key after it");
        assertTrue(worlds.contains("# [worlds.\"minigame lobby\"]"));
        assertTrue(LegacyImport.trailingComments(worlds).contains("Worked examples"), "and it is still at the end");
        final String performance = Files.readString(root.resolve("config/performance.toml"), StandardCharsets.UTF_8);
        assertTrue(performance.lines().filter(line -> line.strip().startsWith("#")).count() > 100,
            "performance.toml keeps its commented settings");
    }
}
