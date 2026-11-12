package io.github.adrielorihuela.legacyfolia.config.migration;

import static io.github.adrielorihuela.legacyfolia.config.migration.ImportFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** server.properties lives in the server root; an earlier release's copy in config/ is moved there. */
class AdoptOwnPropertiesTest {

    private static final String OWN = "# LegacyFolia server.properties\nmotd=x\n";

    @Test
    @DisplayName("the mode's own file in config/ moves to the root")
    void movesOwn(@TempDir final Path root) throws IOException {
        write(root, "config/server.properties", OWN);
        assertTrue(LegacyImport.adoptOwnProperties(root));
        assertFalse(Files.exists(root.resolve("config/server.properties")));
        assertEquals(OWN, Files.readString(root.resolve("server.properties")));
    }

    @Test
    @DisplayName("a legacy file in config/ is the importer's and stays")
    void leavesLegacy(@TempDir final Path root) throws IOException {
        write(root, "config/server.properties", "#Minecraft server properties\nmotd=x\n");
        assertFalse(LegacyImport.adoptOwnProperties(root));
        assertTrue(Files.exists(root.resolve("config/server.properties")));
        assertFalse(Files.exists(root.resolve("server.properties")));
    }

    @Test
    @DisplayName("a root file that already exists is never overwritten")
    void keepsRoot(@TempDir final Path root) throws IOException {
        write(root, "config/server.properties", OWN);
        write(root, "server.properties", "# LegacyFolia server.properties\nmotd=root\n");
        assertFalse(LegacyImport.adoptOwnProperties(root));
        assertEquals("# LegacyFolia server.properties\nmotd=root\n", Files.readString(root.resolve("server.properties")));
        assertTrue(Files.exists(root.resolve("config/server.properties")));
    }

    @Test
    @DisplayName("nothing to do without a file in config/")
    void nothing(@TempDir final Path root) throws IOException {
        assertFalse(LegacyImport.adoptOwnProperties(root));
    }
}
