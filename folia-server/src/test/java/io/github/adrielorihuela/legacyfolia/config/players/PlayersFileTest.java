package io.github.adrielorihuela.legacyfolia.config.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** config/players.toml: entries in and out, and the operator's text left alone. */
class PlayersFileTest {

    private static Map<String, Object> op(final String uuid, final String name, final long level, final boolean bypass) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", uuid);
        map.put("name", name);
        map.put("level", level);
        map.put("bypassesPlayerLimit", bypass);
        return map;
    }

    @Test
    @DisplayName("an op written as TOML reads back with the same fields, kebab case in the file")
    void roundTrip(@TempDir final Path dir) throws IOException {
        final PlayersFile file = new PlayersFile(dir.resolve("players.toml"));
        assertTrue(file.write("ops", List.of(op("u-1", "Notch", 4, true))));
        final String text = Files.readString(dir.resolve("players.toml"));
        assertTrue(text.contains("[[ops]]"));
        assertTrue(text.contains("bypasses-player-limit = true"));
        assertEquals(List.of(op("u-1", "Notch", 4, true)), file.read("ops"));
        assertEquals(List.of(), file.read("whitelist"));
    }

    @Test
    @DisplayName("saving the same list again does not rewrite the file")
    void unchanged(@TempDir final Path dir) throws IOException {
        final PlayersFile file = new PlayersFile(dir.resolve("players.toml"));
        file.write("ops", List.of(op("u-1", "Notch", 4, false)));
        assertFalse(file.write("ops", List.of(op("u-1", "Notch", 4, false))));
    }

    @Test
    @DisplayName("comments above a kept entry and the file's opening text survive a save")
    void commentsKept(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("players.toml");
        Files.writeString(path, "# mine\n\n# the boss\n[[ops]]\nuuid = \"u-1\"\nname = \"Notch\"\nlevel = 4\n"
            + "bypasses-player-limit = false\n");
        final PlayersFile file = new PlayersFile(path);
        file.write("ops", List.of(op("u-1", "Notch", 4, false), op("u-2", "Alex", 2, false)));
        final String text = Files.readString(path);
        assertTrue(text.startsWith("# mine"));
        assertTrue(text.contains("# the boss\n[[ops]]"));
        assertEquals(2, file.read("ops").size());
    }

    @Test
    @DisplayName("removing an entry drops it, and the other lists are untouched")
    void removal(@TempDir final Path dir) throws IOException {
        final PlayersFile file = new PlayersFile(dir.resolve("players.toml"));
        file.write("whitelist", List.of(Map.of("uuid", "w-1", "name", "Steve")));
        file.write("ops", List.of(op("u-1", "Notch", 4, false)));
        file.write("ops", List.of());
        assertEquals(List.of(), file.read("ops"));
        assertEquals(1, file.read("whitelist").size());
    }

    @Test
    @DisplayName("a table of the operator's own after an entry survives saves of any list")
    void unknownTableKept(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("players.toml");
        Files.writeString(path, "[[ops]]\nuuid = \"u-1\"\nname = \"Notch\"\nlevel = 4\nbypasses-player-limit = false\n\n"
            + "# my notes\n[notes]\nx = 1\n\n[[banned-ips]]\nip = \"9.9.9.9\"\n");
        final PlayersFile file = new PlayersFile(path);
        file.write("banned-ips", List.of(Map.of("ip", "9.9.9.9"), Map.of("ip", "1.2.3.4")));
        file.write("ops", List.of());
        final String text = Files.readString(path);
        assertTrue(text.contains("# my notes\n[notes]\nx = 1"), text);
        assertEquals(2, file.read("banned-ips").size());
        assertEquals(List.of(), file.read("ops"));
    }

    @Test
    @DisplayName("a file that is not TOML is reported, not overwritten")
    void invalid(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("players.toml");
        Files.writeString(path, "[[ops]]\nuuid = = broken\n");
        final PlayersFile file = new PlayersFile(path);
        assertThrows(IOException.class, () -> file.read("ops"));
        assertThrows(IOException.class, () -> file.write("ops", List.of()));
        assertEquals("[[ops]]\nuuid = = broken\n", Files.readString(path));
    }
}
