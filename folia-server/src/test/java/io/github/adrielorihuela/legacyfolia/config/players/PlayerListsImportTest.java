package io.github.adrielorihuela.legacyfolia.config.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The JSON lists of an older server are imported into players.toml once, and backed up. */
class PlayerListsImportTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 2, 6, 30, 0);
    private static final String BACKUP = "config/legacy-backup/2026-11-02_06-30-00/";

    private static void write(final Path root, final String relative, final String text) throws IOException {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    @DisplayName("the four files in the root are imported and moved to the backup with their paths")
    void importsRoot(@TempDir final Path root) throws IOException {
        write(root, "whitelist.json", "[{\"uuid\":\"w-1\",\"name\":\"Steve\"}]");
        write(root, "ops.json", "[{\"uuid\":\"u-1\",\"name\":\"Notch\",\"level\":4,\"bypassesPlayerLimit\":true}]");
        write(root, "banned-players.json", "[{\"uuid\":\"b-1\",\"name\":\"Bad\",\"created\":\"c\","
            + "\"source\":\"Server\",\"expires\":\"forever\",\"reason\":\"x\"}]");
        write(root, "banned-ips.json", "[{\"ip\":\"203.0.113.7\",\"created\":\"c\",\"source\":\"s\","
            + "\"expires\":\"forever\",\"reason\":\"r\"}]");

        assertEquals(4, PlayerLists.importLegacy(root, WHEN));

        final PlayersFile toml = new PlayersFile(root.resolve("config/players.toml"));
        assertEquals("Steve", toml.read("whitelist").get(0).get("name"));
        assertEquals(4L, toml.read("ops").get(0).get("level"));
        assertEquals(true, toml.read("ops").get(0).get("bypassesPlayerLimit"));
        assertEquals("203.0.113.7", toml.read("banned-ips").get(0).get("ip"));
        assertEquals("Bad", toml.read("banned-players").get(0).get("name"));
        for (final String name : List.of("whitelist", "ops", "banned-players", "banned-ips")) {
            assertFalse(Files.exists(root.resolve(name + ".json")));
            assertTrue(Files.exists(root.resolve(BACKUP + name + ".json")));
        }
    }

    @Test
    @DisplayName("a file from config/ is imported too, and a second run finds nothing")
    void importsConfigOnce(@TempDir final Path root) throws IOException {
        write(root, "config/whitelist.json", "[{\"uuid\":\"w-1\",\"name\":\"Steve\"}]");
        assertEquals(1, PlayerLists.importLegacy(root, WHEN));
        assertTrue(Files.exists(root.resolve(BACKUP + "config/whitelist.json")));
        assertEquals(0, PlayerLists.importLegacy(root, WHEN.plusMinutes(1)));
    }

    @Test
    @DisplayName("given the folder the import of the other files made, the originals go there and no second folder appears")
    void usesTheSharedBackup(@TempDir final Path root) throws IOException {
        write(root, "whitelist.json", "[{\"uuid\":\"w-1\",\"name\":\"Steve\"}]");
        write(root, "config/legacy-backup/2026-11-02_06-29-59/import-report.txt", "report");
        final Path shared = root.resolve("config/legacy-backup/2026-11-02_06-29-59");

        assertEquals(1, PlayerLists.importLegacy(root, WHEN, shared));

        assertTrue(Files.exists(shared.resolve("whitelist.json")));
        assertTrue(Files.exists(shared.resolve("import-report.txt")));
        assertFalse(Files.exists(root.resolve(BACKUP)), "a second backup folder was made");
        assertFalse(Files.exists(root.resolve("whitelist.json")));
    }

    @Test
    @DisplayName("an entry already in players.toml wins over the same person in the JSON")
    void existingWins(@TempDir final Path root) throws IOException {
        write(root, "config/players.toml", "[[whitelist]]\nuuid = \"w-1\"\nname = \"Renamed\"\n");
        write(root, "whitelist.json", "[{\"uuid\":\"W-1\",\"name\":\"Steve\"},{\"uuid\":\"w-2\",\"name\":\"Alex\"}]");
        PlayerLists.importLegacy(root, WHEN);
        final List<Map<String, Object>> list = new PlayersFile(root.resolve("config/players.toml")).read("whitelist");
        assertEquals(2, list.size());
        assertEquals("Renamed", list.get(0).get("name"));
    }

    @Test
    @DisplayName("a file that is not JSON stays where it is")
    void brokenStays(@TempDir final Path root) throws IOException {
        write(root, "ops.json", "{not json");
        assertEquals(0, PlayerLists.importLegacy(root, WHEN));
        assertTrue(Files.exists(root.resolve("ops.json")));
    }

    @Test
    @DisplayName("nothing is created when there is nothing to import")
    void nothing(@TempDir final Path root) throws IOException {
        assertEquals(0, PlayerLists.importLegacy(root, WHEN));
        assertFalse(Files.exists(root.resolve("config")));
    }
}
