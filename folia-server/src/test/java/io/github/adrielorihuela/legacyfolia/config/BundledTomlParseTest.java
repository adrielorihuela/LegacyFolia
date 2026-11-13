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

class BundledTomlParseTest {

    private static final List<String> FILES = List.of(
        "combat.toml", "gameplay.toml", "network.toml", "performance.toml",
        "plugins.toml", "server.toml", "world.toml", "worlds.toml");

    @Test
    void parsesWithNightConfigAndIsAscii() throws Exception {
        for (final String name : FILES) {
            try (InputStream in = BundledTomlParseTest.class.getClassLoader()
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

    @Test
    void everySettingHasACommentAboveIt() throws Exception {
        final java.util.regex.Pattern setting = java.util.regex.Pattern.compile("^[A-Za-z0-9_.\"-]+ *=.*");
        for (final String name : FILES) {
            try (InputStream in = BundledTomlParseTest.class.getClassLoader()
                    .getResourceAsStream("legacyfolia/" + name)) {
                assertNotNull(in, name + " is not on the classpath");
                final List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
                for (int i = 0; i < lines.size(); i++) {
                    if (setting.matcher(lines.get(i)).matches()) {
                        // Documented by its own comment, or by what comes before it when that is a comment, the key
                        // above it (checked in its turn) or the end of an array: blank lines and the [table] header it
                        // opens are skipped, because a section's keys may share the comment block above its header.
                        int above = i - 1;
                        while (above >= 0 && (lines.get(above).isEmpty() || lines.get(above).startsWith("["))) {
                            above--;
                        }
                        final String text = above < 0 ? "" : lines.get(above);
                        assertTrue(text.startsWith("#") || text.equals("]") || setting.matcher(text).matches(),
                            name + " line " + (i + 1) + " has no comment above it: " + lines.get(i));
                    }
                }
            }
        }
    }
}
