package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.BACKUP;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.run;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.tree;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The backup holds configuration files and nothing else: a world's data is never moved into it. */
class LegacyImportOnlyConfigTest {

    private static void world(final Path root, final String folder) throws IOException {
        write(root, folder + "/level.dat", "nbt");
        write(root, folder + "/session.lock", "lock");
        write(root, folder + "/players/data/a.dat", "player");
        write(root, folder + "/region/r.0.0.mca", "region");
    }

    private static void assertOnlyConfiguration(final Path root) throws IOException {
        for (final String file : tree(root.resolve(BACKUP))) {
            assertTrue(file.endsWith(".yml") || file.endsWith(".txt") || file.endsWith(".properties"),
                "not a configuration file: " + file);
        }
    }

    @Test
    @DisplayName("a level folder's data stays where it is when its paper-world.yml is imported")
    void levelData(@TempDir final Path root) throws IOException {
        write(root, "config/bukkit.yml", "settings: {}\n");
        world(root, "world");
        write(root, "world/dimensions/minecraft/overworld/paper-world.yml", "_version: 31\n");
        run(root);
        assertOnlyConfiguration(root);
        assertTrue(Files.exists(root.resolve("world/level.dat")));
        assertTrue(Files.exists(root.resolve("world/players/data/a.dat")));
    }

    @Test
    @DisplayName("a world container that is config/ itself does not carry the world into the backup")
    void containerIsConfig(@TempDir final Path root) throws IOException {
        write(root, "config/bukkit.yml", "settings: {}\n");
        world(root, "config");
        write(root, "config/paper-world-defaults.yml", "_version: 31\n");
        LegacyImport.run(root, Path.of("config"), ImportFixtures.WHEN, LegacyImport.Checkpoint.NONE);
        assertOnlyConfiguration(root);
        assertTrue(Files.exists(root.resolve("config/level.dat")));
        assertEquals(List.of(), tree(root.resolve(BACKUP)).stream().filter(f -> f.endsWith(".dat")).toList());
    }
}
