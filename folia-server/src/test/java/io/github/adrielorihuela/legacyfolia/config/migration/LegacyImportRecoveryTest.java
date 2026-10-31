package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.BACKUP;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.WHEN;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.copy;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.run;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.tree;
import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The backup, running twice, and running again after being cut short.
 */
class LegacyImportRecoveryTest {

    /** Thrown by a checkpoint to stand in for the process dying there. */
    private static final class Killed extends IOException {
        Killed(final String step) {
            super("killed at " + step);
        }
    }

    private static Map<String, String> configFiles(final Path root) throws IOException {
        final Map<String, String> contents = new LinkedHashMap<>();
        for (final String name : tree(root.resolve("config"))) {
            if (!name.startsWith("legacy-backup/")) {
                contents.put(name, Files.readString(root.resolve("config").resolve(name), StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    @Test
    @DisplayName("the backup keeps every original at its relative path, with an ASCII report beside them")
    void backupStructure(@TempDir final Path root) throws IOException {
        copy("paper", root);
        final List<String> before = tree(root);

        final LegacyImport.Result result = run(root).orElseThrow();

        assertEquals(root.resolve(BACKUP), result.backupDirectory());
        final List<String> backedUp = tree(result.backupDirectory());
        for (final String original : before) {
            assertTrue(backedUp.contains(original), original + " is in the backup");
            assertFalse(Files.exists(root.resolve(original)), original + " left its old place");
            assertEquals(Files.readString(Path.of(toUri("legacyfolia-import/paper/" + original))),
                Files.readString(result.backupDirectory().resolve(original)), original + " is unchanged");
        }
        assertFalse(Files.exists(result.backupDirectory().resolve(LegacyImport.MARKER)));
        final byte[] report = Files.readAllBytes(result.report());
        for (final byte b : report) {
            assertTrue(b >= 0 && (b == '\n' || b >= 0x20 && b < 0x7f), "the report is plain ASCII");
        }
        assertTrue(new String(report, StandardCharsets.US_ASCII).contains("Files imported"));
    }

    private static java.net.URI toUri(final String resource) {
        try {
            return ImportFixtures.class.getClassLoader().getResource(resource).toURI();
        } catch (final java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a second run with nothing left to import does nothing")
    void idempotent(@TempDir final Path root) throws IOException {
        copy("spigot", root);
        run(root).orElseThrow();
        final Map<String, String> after = configFiles(root);
        final List<String> backups = tree(root.resolve("config/legacy-backup"));

        assertTrue(run(root).isEmpty());
        assertTrue(LegacyImport.runIfNeeded(root, null).isEmpty());

        assertEquals(after, configFiles(root), "no TOML file changed");
        assertEquals(backups, tree(root.resolve("config/legacy-backup")), "no second backup");
    }

    @Test
    @DisplayName("importing the same files again writes nothing new")
    void sameFilesTwice(@TempDir final Path root) throws IOException {
        copy("spigot", root);
        run(root).orElseThrow();
        final Map<String, String> after = configFiles(root);

        copy("spigot", root);
        final LegacyImport.Result again = LegacyImport.run(root, null, WHEN.plusMinutes(1), LegacyImport.Checkpoint.NONE).orElseThrow();

        assertEquals(0, again.written(), "every value already matched");
        assertEquals(withoutSecrets(after), withoutSecrets(configFiles(root)));
        assertEquals(2, Files.list(root.resolve("config/legacy-backup")).count());
    }

    private static Map<String, String> withoutSecrets(final Map<String, String> files) {
        final Map<String, String> copy = new LinkedHashMap<>(files);
        copy.keySet().removeIf(name -> name.endsWith(".secret"));
        return copy;
    }

    @ParameterizedTest(name = "killed at {0}")
    @ValueSource(strings = {"started", "moved:config/paper-global.yml", "moved:world/paper-world.yml",
        "saved:performance.toml", "saved:worlds.toml"})
    @DisplayName("an import cut short is finished by the next run, losing and doubling nothing")
    void interrupted(final String step, @TempDir final Path control, @TempDir final Path root) throws IOException {
        copy("paper", control);
        run(control).orElseThrow();

        copy("paper", root);
        final List<String> originals = tree(root);
        assertThrows(Killed.class, () -> LegacyImport.run(root, null, WHEN, reached -> {
            if (reached.equals(step)) {
                throw new Killed(reached);
            }
        }));
        assertTrue(Files.exists(root.resolve(BACKUP).resolve(LegacyImport.MARKER)), "the marker says it is unfinished");
        for (final String original : originals) {
            assertTrue(Files.exists(root.resolve(original)) || Files.exists(root.resolve(BACKUP).resolve(original)),
                original + " is in one place or the other");
        }

        // A minute later, as the next start would be.
        final LegacyImport.Result result = LegacyImport.run(root, null, WHEN.plusMinutes(1), LegacyImport.Checkpoint.NONE).orElseThrow();

        assertEquals(root.resolve(BACKUP), result.backupDirectory(), "the unfinished backup is resumed, not doubled");
        assertEquals(List.of("2026-10-30_12-00-00"), Files.list(root.resolve("config/legacy-backup"))
            .map(path -> path.getFileName().toString()).toList());
        assertEquals(tree(control.resolve(BACKUP)), tree(root.resolve(BACKUP)));
        assertEquals(configFiles(control), configFiles(root), "the same TOML files as an uninterrupted import");
        assertTrue(run(root).isEmpty(), "and then there is nothing left to do");
    }

    @Test
    @DisplayName("a file that cannot be parsed stops the import before anything moves")
    void unparsable(@TempDir final Path root) throws IOException {
        write(root, "bukkit.yml", "settings:\n  allow-end: [unclosed\n");
        write(root, "server.properties", "#Minecraft server properties\nmotd=hi\n");

        final IOException error = assertThrows(IOException.class, () -> run(root));

        assertTrue(error.getMessage().contains("bukkit.yml"));
        assertTrue(Files.exists(root.resolve("bukkit.yml")));
        assertTrue(Files.exists(root.resolve("server.properties")));
        assertFalse(Files.exists(root.resolve("config/legacy-backup")));
    }

    @Test
    @DisplayName("LegacyFolia's own server.properties is not legacy and is left alone")
    void ownServerProperties(@TempDir final Path root) throws IOException {
        write(root, "config/server.properties", """
            # LegacyFolia server.properties mode: only the settings that differ from the default.
            # Delete a line to go back to the default.
            motd=My server
            worlds.world_nether.chunks.view-distance=6
            """);

        assertTrue(run(root).isEmpty());
        assertTrue(Files.exists(root.resolve("config/server.properties")));
    }
}
