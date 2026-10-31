package io.github.adrielorihuela.legacyfolia.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Changing one value must change one value: the comments, the header and the commented-out examples
 * that make the files readable stay byte for byte.
 */
class TomlTextTest {

    private static CommentedConfig parse(final String text) {
        final CommentedConfig config = CommentedConfig.inMemory();
        new TomlParser().parse(new StringReader(text), config, ParsingMode.REPLACE);
        return config;
    }

    private static String bundled(final String name) throws Exception {
        try (InputStream in = TomlTextTest.class.getClassLoader().getResourceAsStream("legacyfolia/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("replacing a value changes nothing but the value")
    void replaceInPlace() {
        final String text = "# header\n\n[server]\n# about motd\nmotd = \"old\" # trailing\nmax = 20\n";
        final TomlText edited = TomlText.parse(text).set(List.of(), "server.motd", "\"new\"", false);
        assertEquals("# header\n\n[server]\n# about motd\nmotd = \"new\" # trailing\nmax = 20\n", edited.text());
    }

    @Test
    @DisplayName("every bundled file survives an edit with every other line intact")
    void bundledFilesKeepTheirComments() throws Exception {
        final String server = bundled("server.toml");
        final TomlText edited = TomlText.parse(server).set(List.of(), "server.motd", "\"Hello\"", false);

        assertEquals("Hello", parse(edited.text()).get("server.motd"));
        final String[] before = server.split("\n", -1);
        final String[] after = edited.text().split("\n", -1);
        assertEquals(before.length, after.length);
        int differing = 0;
        for (int i = 0; i < before.length; i++) {
            if (!before[i].equals(after[i])) {
                differing++;
            }
        }
        assertEquals(1, differing);
    }

    @Test
    @DisplayName("multi-line arrays and inline tables are replaced whole")
    void multiLineValues() {
        final String text = "[a]\nlist = [\n  \"x\", # one\n  \"y\",\n]\nnext = 1\n";
        final TomlText edited = TomlText.parse(text).set(List.of(), "a.list", "[\"z\"]", false);
        assertEquals("[a]\nlist = [\"z\"]\nnext = 1\n", edited.text());
    }

    @Test
    @DisplayName("a missing key goes at the end of its table, before the next one")
    void insertIntoItsTable() {
        final String text = "[a]\nx = 1\n\n# about b\n[b]\ny = 2\n";
        final TomlText edited = TomlText.parse(text).set(List.of(), "a.z", "3", false);
        assertEquals("[a]\nx = 1\nz = 3\n\n# about b\n[b]\ny = 2\n", edited.text());
        assertEquals(3, (int) parse(edited.text()).get("a.z"));
    }

    @Test
    @DisplayName("a world override is found in either of the two documented forms")
    void bothWorldForms() {
        final String quoted = "[worlds.default]\n\n[worlds.world_nether]\n\"chunks.view-distance\" = 4\n";
        final String nested = "[worlds.default]\n\n[worlds.world_nether.chunks]\nview-distance = 4\n";
        for (final String text : List.of(quoted, nested)) {
            final TomlText toml = TomlText.parse(text);
            assertTrue(toml.contains(List.of("worlds", "world_nether"), "chunks.view-distance"), text);
            final TomlText removed = toml.remove(List.of("worlds", "world_nether"), "chunks.view-distance")
                .removeEmptyTables(List.of("worlds"), List.of("worlds", "default"));
            assertFalse(removed.contains(List.of("worlds", "world_nether"), "chunks.view-distance"));
            assertEquals("[worlds.default]\n\n", removed.text());
        }
    }

    @Test
    @DisplayName("a new world override is written as one quoted key under the world's table")
    void newWorldOverride() {
        final String text = "[worlds.default]\n";
        final TomlText edited = TomlText.parse(text)
            .set(List.of("worlds", "my world"), "chunks.view-distance", "6", true)
            .set(List.of("worlds", "my world"), "gameplay.items.item-despawn-rate", "100", true);
        assertEquals("[worlds.default]\n\n[worlds.\"my world\"]\n\"chunks.view-distance\" = 6\n"
            + "\"gameplay.items.item-despawn-rate\" = 100\n", edited.text());
        final CommentedConfig parsed = parse(edited.text());
        assertEquals(6, (int) parsed.get(List.of("worlds", "my world", "chunks.view-distance")));
    }

    @Test
    @DisplayName("a table written under its own header keeps the header; only its body changes")
    void tableFormKept() {
        final String text = "[plugins.commands]\nx = 1\n\n[plugins.commands.aliases]\nfoo = [\"bar\"]\n"
            + "[plugins.commands.aliases.deeper]\nz = 1\n\n[other]\ny = 2\n";
        final TomlText edited = TomlText.parse(text).set(List.of(), "plugins.commands.aliases",
            TomlLiterals.render(Map.of("icanhasbukkit", List.of("version $1-"))), false);
        assertEquals("[plugins.commands]\nx = 1\n\n[plugins.commands.aliases]\nicanhasbukkit = [\"version $1-\"]\n"
            + "[other]\ny = 2\n", edited.text());
    }

    @Test
    @DisplayName("an inline value replaces the same table written as dotted keys")
    void dottedFormReplaced() {
        final String text = "[plugins.commands]\naliases.foo = [\"bar\"]\naliases.baz = []\nx = 1\n";
        final TomlText edited = TomlText.parse(text).set(List.of(), "plugins.commands.aliases", "{}", false);
        assertEquals("[plugins.commands]\nx = 1\naliases = {}\n", edited.text());
        assertTrue(parse(edited.text()).get("plugins.commands.aliases") instanceof com.electronwill.nightconfig.core.UnmodifiableConfig);
    }

    @Test
    @DisplayName("literals round-trip through the TOML parser")
    void literalsRoundTrip() {
        final List<Object> values = List.of("plain", "quote \" backslash \\ tab \t", "Ünïcødé ☃ 😀", 1, 5_000_000_000L,
            0.25, true, List.of("a", "b"), List.of(), Map.of("minecraft:cobblestone", 300));
        for (final Object value : values) {
            final String literal = TomlLiterals.render(value);
            for (final char c : literal.toCharArray()) {
                assertTrue(c < 0x80, literal);
            }
            assertEquals(value, TomlText.parseValue(literal), literal);
        }
    }

    @Test
    @DisplayName("CRLF files stay CRLF")
    void keepsLineEndings() {
        final String text = "[a]\r\nx = 1\r\n";
        assertEquals("[a]\r\nx = 1\r\ny = 2\r\n", TomlText.parse(text).set(List.of(), "a.y", "2", false).text());
    }
}
