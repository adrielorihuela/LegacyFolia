package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How legacy files are found, and how an older server's server.properties is told apart from the
 * server.properties mode's own.
 */
class LegacyFilesTest {

    @Test
    @DisplayName("a header that mentions LegacyFolia makes the file the mode's own, wherever it is")
    void ownHeader(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "# LegacyFolia: settings that differ from the default\nrcon.password=x\n");
        write(root, "bukkit.yml", "settings: {}\n");
        assertFalse(LegacyFiles.isLegacyServerProperties(root.resolve("server.properties"), true));
    }

    @Test
    @DisplayName("vanilla's header, a neighbouring bukkit.yml or spigot.yml, or a key the mode never writes make it legacy")
    void legacySigns(@TempDir final Path root) throws IOException {
        write(root, "a/server.properties", "#Minecraft server properties\nmotd=x\n");
        assertTrue(LegacyFiles.isLegacyServerProperties(root.resolve("a/server.properties"), false));

        write(root, "b/server.properties", "motd=x\n");
        assertTrue(LegacyFiles.isLegacyServerProperties(root.resolve("b/server.properties"), true));

        write(root, "c/server.properties", "motd=x\nrcon.password=secret\n");
        assertTrue(LegacyFiles.isLegacyServerProperties(root.resolve("c/server.properties"), false),
            "rcon.password is a secret and never a key of the mode's file");
    }

    @Test
    @DisplayName("without a header, a file with only the mode's keys and no YAML beside it is left alone")
    void ambiguousIsLeftAlone(@TempDir final Path root) throws IOException {
        write(root, "server.properties", "motd=x\ngameplay.hunger.jump-walk-exhaustion=0.1\n"
            + "worlds.world_nether.chunks.view-distance=6\n");
        assertFalse(LegacyFiles.isLegacyServerProperties(root.resolve("server.properties"), false));
        assertTrue(LegacyFiles.isModeKey("worlds.my.world.chunks.view-distance"), "a world name may contain dots");
        assertFalse(LegacyFiles.isModeKey("worlds.world.network.server-port"), "not a per-world setting");
    }

    @Test
    @DisplayName("worlds are found in both the CraftBukkit and the Paper 26 layouts, and named as Paper names them")
    void worlds(@TempDir final Path root) throws IOException {
        write(root, "world_nether/paper-world.yml", "_version: 31\n");
        write(root, "survival/paper-world.yml", "_version: 31\n");
        write(root, "survival/dimensions/minecraft/the_end/paper-world.yml", "_version: 31\n");
        write(root, "survival/dimensions/myplugin/mining/paper-world.yml", "_version: 31\n");
        write(root, "config/paper-world.yml", "_version: 31\n");

        final List<String> worlds = LegacyFiles.paperWorldFiles(root, root).stream()
            .map(file -> file.world() + "=" + file.relative()).toList();

        assertEquals(List.of(
            "survival=survival/paper-world.yml",
            "survival_the_end=survival/dimensions/minecraft/the_end/paper-world.yml",
            "survival_myplugin_mining=survival/dimensions/myplugin/mining/paper-world.yml",
            "world_nether=world_nether/paper-world.yml"), worlds);
    }

    @Test
    @DisplayName("nothing to import is an empty result, and whitelist, ops and bans are never touched")
    void nothing(@TempDir final Path root) throws IOException {
        write(root, "whitelist.json", "[]");
        write(root, "config/ops.json", "[]");
        write(root, "config/banned-players.json", "[]");
        assertTrue(LegacyFiles.find(root, root).isEmpty());
        assertTrue(ImportFixtures.run(root).isEmpty());
    }
}
