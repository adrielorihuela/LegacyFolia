package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.override;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.report;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.run;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.toml;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.ConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each collision of SETTINGS-MAP section 3, resolved as the server that owned both files did.
 */
class LegacyImportCollisionsTest {

    @Test
    @DisplayName("3.1 dimensions: imported on a fresh setup, the TOML kept where it was already in charge")
    void dimensions(@TempDir final Path fresh, @TempDir final Path upgraded) throws IOException {
        write(fresh, "config/paper-global.yml", "misc:\n  enable-nether: false\n");
        write(fresh, "bukkit.yml", "settings:\n  allow-end: false\n");
        run(fresh).orElseThrow();
        assertEquals(false, toml(fresh, "world.toml", "dimensions.nether"));
        assertEquals(false, toml(fresh, "world.toml", "dimensions.end"));

        // A LegacyFolia server whose world.toml already exists: Dimensions has been reading the
        // TOML there since v0.3, so the YAML values were not what the server ran with.
        ConfigFile.open(upgraded.resolve("config"), "world.toml");
        write(upgraded, "config/paper-global.yml", "misc:\n  enable-nether: false\n");
        run(upgraded).orElseThrow();
        assertEquals(true, toml(upgraded, "world.toml", "dimensions.nether"));
        assertTrue(report(upgraded).contains("dimensions.nether = true kept over"));
    }

    @Test
    @DisplayName("3.2 BungeeCord: Spigot's switch and Paper's online mode, each to its own key")
    void bungeeCord(@TempDir final Path root) throws IOException {
        write(root, "spigot.yml", "settings:\n  bungeecord: true\n");
        write(root, "config/paper-global.yml", "proxies:\n  bungee-cord:\n    online-mode: false\n");
        run(root).orElseThrow();
        assertEquals(true, toml(root, "network.toml", "network.bungeecord.enabled"));
        assertEquals(false, toml(root, "network.toml", "network.bungeecord.online-mode"));
    }

    @Test
    @DisplayName("3.3 view distance: Spigot's <= 0 falls back to server.properties, a positive value wins and is clamped")
    void viewDistance(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "#Minecraft server properties\nview-distance=12\nsimulation-distance=9\n");
        write(root, "spigot.yml", """
            world-settings:
              default:
                view-distance: 0
                simulation-distance: 6
              world_nether:
                view-distance: 64
              world_the_end:
                simulation-distance: default
            """);
        run(root).orElseThrow();
        assertEquals(12, toml(root, "performance.toml", "chunks.view-distance"));
        assertNull(override(root, "default", "chunks.view-distance"), "0 defers to server.properties");
        assertEquals(32, override(root, "world_nether", "chunks.view-distance"), "clamped to 32 as Spigot does");
        assertEquals(9, toml(root, "performance.toml", "chunks.simulation-distance"));
        assertEquals(6, override(root, "default", "chunks.simulation-distance"));
        assertEquals(9, override(root, "world_the_end", "chunks.simulation-distance"), "default cancels the 6");
    }

    @Test
    @DisplayName("3.4 spawn limits: Paper's -1 means Bukkit decides")
    void spawnLimits(@TempDir final Path root) throws IOException {
        write(root, "bukkit.yml", "spawn-limits:\n  monsters: 45\nticks-per:\n  animal-spawns: 200\n");
        write(root, "config/paper-world-defaults.yml", """
            entities:
              spawning:
                spawn-limits:
                  monster: -1
                ticks-per-spawn:
                  creature: 800
            """);
        run(root).orElseThrow();
        assertEquals(45, toml(root, "performance.toml", "performance.spawning.spawn-limits.monsters"));
        assertNull(override(root, "default", "performance.spawning.spawn-limits.monsters"));
        assertEquals(200, toml(root, "performance.toml", "performance.spawning.ticks-per-spawn.animals"));
        assertEquals(800, override(root, "default", "performance.spawning.ticks-per-spawn.animals"));
        assertTrue(report(root).contains("a negative value means Bukkit decides"));
    }

    @Test
    @DisplayName("3.5 arrows: Paper's default follows Spigot's arrow rate, which is imported")
    void arrows(@TempDir final Path root) throws IOException {
        write(root, "spigot.yml", "world-settings:\n  default:\n    arrow-despawn-rate: 400\n");
        write(root, "config/paper-world-defaults.yml",
            "entities:\n  spawning:\n    non-player-arrow-despawn-rate: default\n    creative-arrow-despawn-rate: 20\n");
        run(root).orElseThrow();
        assertEquals(400, toml(root, "gameplay.toml", "gameplay.items.arrow-despawn-rate"));
        assertEquals("default", toml(root, "gameplay.toml", "gameplay.items.non-player-arrow-despawn-rate"));
        assertEquals(20, toml(root, "gameplay.toml", "gameplay.items.creative-arrow-despawn-rate"));
        assertTrue(report(root).contains("falls back to gameplay.items.arrow-despawn-rate = 400"));
    }

    @Test
    @DisplayName("3.6 autosave: chunks from Bukkit unless Paper's world value is >= 0; players' -1 means the chunk interval")
    void autosave(@TempDir final Path root) throws IOException {
        write(root, "bukkit.yml", "ticks-per:\n  autosave: 2400\n");
        write(root, "config/paper-global.yml", "player-auto-save:\n  rate: -1\n");
        write(root, "config/paper-world-defaults.yml", "chunks:\n  auto-save-interval: 9000\n");
        run(root).orElseThrow();
        assertEquals(2400, toml(root, "performance.toml", "chunks.auto-save-interval"));
        assertEquals(9000, override(root, "default", "chunks.auto-save-interval"));
        assertEquals(2400, toml(root, "server.toml", "server.saving.player-save-interval-ticks"));
    }

    @Test
    @DisplayName("3.7 spawn protection: server.properties is what the server ran with")
    void spawnProtection(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "#Minecraft server properties\nspawn-protection=8\n");
        run(root).orElseThrow();
        assertEquals(8, toml(root, "server.toml", "server.spawn-protection-radius"));
    }

    @Test
    @DisplayName("3.8 Velocity secret: an existing forwarding.secret is kept, the YAML one stays in the backup")
    void velocitySecret(@TempDir final Path root) throws IOException {
        write(root, "config/forwarding.secret", "already-there\n");
        write(root, "config/paper-global.yml", "proxies:\n  velocity:\n    secret: from-yaml\n");
        run(root).orElseThrow();
        assertEquals("already-there\n", Files.readString(root.resolve("config/forwarding.secret")));
        assertTrue(report(root).contains("forwarding.secret kept"));
    }

    @Test
    @DisplayName("3.9-3.12 and 3.15: look-alike keys land in their own TOML keys")
    void separateKeys(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "#Minecraft server properties\nmax-tick-time=120000\n");
        write(root, "bukkit.yml", "settings:\n  connection-throttle: 1000\n");
        write(root, "spigot.yml", "settings:\n  timeout-time: 120\n  user-cache-size: 500\n"
            + "world-settings:\n  default:\n    max-tick-time:\n      entity: 25\n");
        write(root, "config/paper-global.yml", """
            collisions:
              enable-player-collisions: false
            watchdog:
              early-warning-every: 10000
            messages:
              kick:
                connection-throttle: <red>Slow down
            """);
        run(root).orElseThrow();
        assertEquals(false, toml(root, "performance.toml", "performance.entities.collisions.enable-player-collisions"));
        assertEquals(1000, toml(root, "network.toml", "network.limits.connection-throttle"));
        assertEquals("<red>Slow down", toml(root, "server.toml", "server.messages.kick-connection-throttle"));
        assertEquals(25, toml(root, "performance.toml", "performance.ticking.entity-budget-millis"));
        assertEquals(120, toml(root, "server.toml", "server.watchdog.timeout-seconds"));
        // The watchdog's max-tick-time has no TOML key in the catalog yet: reported, not lost.
        assertTrue(report(root).contains("server.properties: max-tick-time = \"120000\"  -- the catalog has no key for it"));
    }

    @Test
    @DisplayName("3.13 below-zero generation: world-settings.default is the one global value; a world's own is reported")
    void belowZero(@TempDir final Path root) throws IOException {
        write(root, "spigot.yml", """
            world-settings:
              default:
                below-zero-generation-in-existing-chunks: false
              world:
                below-zero-generation-in-existing-chunks: true
            """);
        run(root).orElseThrow();
        assertEquals(false, toml(root, "world.toml", "world.generation.below-zero-generation-in-existing-chunks"));
        assertTrue(report(root).contains("world-settings.world.below-zero-generation-in-existing-chunks = true"));
    }

    @Test
    @DisplayName("3.14 structure seeds: Spigot's seeds have no TOML key and are reported, never dropped")
    void seeds(@TempDir final Path root) throws IOException {
        write(root, "spigot.yml", "world-settings:\n  default:\n    seed-village: 42\n");
        run(root).orElseThrow();
        assertTrue(report(root).contains("world-settings.default.seed-village = 42  -- the catalog has no key for it"));
    }

    @Test
    @DisplayName("debug is on if either server.properties or spigot.yml turns it on")
    void debug(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "#Minecraft server properties\ndebug=false\n");
        write(root, "spigot.yml", "settings:\n  debug: true\n");
        run(root).orElseThrow();
        assertEquals(true, toml(root, "server.toml", "server.console.debug"));
    }

    @Test
    @DisplayName("a TOML value the older server did not run with is replaced and reported")
    void replacesStaleTomlValues(@TempDir final Path root) throws IOException {
        final ConfigFile gameplay = ConfigFile.open(root.resolve("config"), "gameplay.toml");
        gameplay.set("gameplay.items.item-despawn-rate", 1234);
        gameplay.save();
        write(root, "spigot.yml", "world-settings:\n  default:\n    item-despawn-rate: 6000\n");
        run(root).orElseThrow();
        assertEquals(6000, toml(root, "gameplay.toml", "gameplay.items.item-despawn-rate"));
        assertTrue(report(root).contains("was 1234, now 6000"));
    }

    @Test
    @DisplayName("a value the setting cannot take is reported and the setting keeps its value")
    void invalidValues(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "#Minecraft server properties\nmax-players=lots\n");
        run(root).orElseThrow();
        assertTrue(Files.notExists(root.resolve("config/server.toml")), "nothing valid to write, so nothing written");
        assertTrue(report(root).contains("max-players = \"lots\" -- \"lots\" is not a whole number"), report(root));
    }
}
