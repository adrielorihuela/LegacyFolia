package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundledTomlParseTestSuite {

    private static final List<String> FILES = List.of(
        "combat.toml", "gameplay.toml", "network.toml", "performance.toml",
        "plugins.toml", "server.toml", "world.toml", "worlds.toml");

    @Test
    void parsesWithNightConfigAndIsAscii() throws Exception {
        for (final String name : FILES) {
            try (InputStream in = BundledTomlParseTestSuite.class.getClassLoader()
                    .getResourceAsStream("legacyfolia/" + name)) {
                assertNotNull(in, name + " is not on the classpath");
                final byte[] bytes = in.readAllBytes();
                for (final byte b : bytes) {
                    assertTrue(b >= 0, name + " contains a non-ASCII byte");
                }
                final CommentedConfig parsed = TomlFormat.instance().createParser().parse(
                    new InputStreamReader(new java.io.ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8));
                assertTrue(parsed.size() > 0, name + " parsed to nothing");
                System.out.println("PARSED " + name + " top-level=" + parsed.size());
            }
        }
    }
}
