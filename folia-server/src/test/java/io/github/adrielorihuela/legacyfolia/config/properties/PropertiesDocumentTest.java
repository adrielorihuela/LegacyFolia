package io.github.adrielorihuela.legacyfolia.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The file belongs to the operator and to their panel. Everything this class writes has to read back
 * through java.util.Properties exactly, and everything it does not touch has to stay as it was.
 */
class PropertiesDocumentTest {

    private static Properties load(final String text) throws Exception {
        final Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }

    @Test
    @DisplayName("reads every form java.util.Properties reads, to the same keys and values")
    void readsLikeProperties() throws Exception {
        final String text = String.join("\n",
            "# a comment",
            "! another comment",
            "   ",
            "plain=value",
            "spaced = value with spaces  ",
            "colon:value",
            "  indented   :   x",
            "whitespace separated",
            "empty=",
            "novalue",
            "esc\\=aped\\ key=a\\=b\\:c\\#d\\!e",
            "tabs=a\\tb\\nc",
            "unicode=Caf\\u00e9 \\u2603",
            "continued=first \\",
            "    second \\",
            "\tthird",
            "even\\\\=backslashes\\\\",
            "dup=1",
            "dup=2",
            "");
        final Properties expected = load(text);
        final PropertiesDocument document = PropertiesDocument.parse(text);

        for (final String key : expected.stringPropertyNames()) {
            assertEquals(expected.getProperty(key), document.get(key).orElseThrow(), key);
        }
        assertEquals(expected.size(), document.entries().stream().map(PropertiesDocument.Entry::key).distinct().count());
        assertEquals("first second third", document.get("continued").orElseThrow());
        assertEquals("Café ☃", document.get("unicode").orElseThrow());
        assertEquals("2", document.get("dup").orElseThrow(), "the last copy of a key counts");
    }

    @Test
    @DisplayName("an untouched file renders byte for byte as it was read")
    void untouchedRoundTrip() {
        final String text = "# header\r\n\r\nb = 2\r\na:1\r\n  # indented comment\r\nlong=x \\\r\n   y\r\n";
        assertEquals(text, PropertiesDocument.parse(text).render());
    }

    @Test
    @DisplayName("changing a value keeps comments, order, and the line's own separator")
    void setKeepsEverythingElse() {
        final String text = "# mine\nzeta = 1\n# about alpha\nalpha : old\nmid=3\n";
        final PropertiesDocument document = PropertiesDocument.parse(text);

        assertTrue(document.set("alpha", "new"));
        assertFalse(document.set("alpha", "new"), "the same value is not a change");

        assertEquals("# mine\nzeta = 1\n# about alpha\nalpha : new\nmid=3\n", document.render());
    }

    @Test
    @DisplayName("a new key goes after the last key of its group, or under a new heading")
    void newKeysJoinTheirGroup() {
        final String text = "# mine\nmotd=hi\nunknown=1\nmax-players=5\n# trailing comment\n";
        final PropertiesDocument document = PropertiesDocument.parse(text);
        final Function<String, String> group = key -> key.startsWith("worlds.") ? "worlds"
            : key.equals("unknown") ? null : "server";

        document.set("pvp", "false", group, name -> name);
        document.set("worlds.w.x", "1", group, name -> "World " + name);

        assertEquals("# mine\nmotd=hi\nunknown=1\nmax-players=5\npvp=false\n# trailing comment\n"
            + "\n# World worlds\nworlds.w.x=1\n", document.render());
    }

    @Test
    @DisplayName("setting a key written twice leaves one copy, where the last one was")
    void setDropsDuplicates() {
        final PropertiesDocument document = PropertiesDocument.parse("a=1\nb=2\na=3\n");
        document.set("a", "4");
        assertEquals("b=2\na=4\n", document.render());
    }

    @Test
    @DisplayName("removing a key removes every copy and nothing else")
    void removeKeepsComments() {
        final PropertiesDocument document = PropertiesDocument.parse("# about a\na=1\nb=2\na=3\n");
        assertTrue(document.remove("a"));
        assertFalse(document.remove("a"));
        assertEquals("# about a\nb=2\n", document.render());
    }

    @Test
    @DisplayName("what it writes, Properties reads back exactly, and it is plain ASCII")
    void escapesLikeProperties() throws Exception {
        final List<String> values = List.of(" leading space", "a=b:c#d!e", "tab\tnewline\nreturn\r",
            "back\\slash", "Ünïcødé ☃ 😀", "trailing space ", "", "=");
        final PropertiesDocument document = PropertiesDocument.create(List.of("# header"));
        int i = 0;
        for (final String value : values) {
            document.set("key " + i++ + " é=:", value);
        }
        final String text = document.render();
        for (final char c : text.toCharArray()) {
            assertTrue(c < 0x80, "non-ASCII written: " + text);
        }

        final Properties read = load(text);
        i = 0;
        for (final String value : values) {
            assertEquals(value, read.getProperty("key " + i++ + " é=:"));
        }
        // And this class reads its own output the same way.
        final PropertiesDocument again = PropertiesDocument.parse(text);
        i = 0;
        for (final String value : values) {
            assertEquals(value, again.get("key " + i++ + " é=:").orElseThrow());
        }
    }

    @Test
    @DisplayName("escaping matches Properties.store character for character")
    void escapingMatchesStore() throws Exception {
        final String key = "a key:=#!é";
        final String value = " v=1:2#3!4\té☃";
        final Properties properties = new Properties();
        properties.setProperty(key, value);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        properties.store(out, null);
        final String stored = out.toString(StandardCharsets.ISO_8859_1).lines()
            .filter(line -> !line.startsWith("#")).findFirst().orElseThrow();

        assertEquals(stored, PropertiesDocument.escapeKey(key) + "=" + PropertiesDocument.escapeValue(value));
    }

    @Test
    @DisplayName("a file that is not UTF-8 is read as ISO-8859-1, as Properties.load(InputStream) would")
    void decodesLatin1() throws Exception {
        final byte[] latin1 = "motd=Café\n".getBytes(StandardCharsets.ISO_8859_1);
        final Properties expected = new Properties();
        expected.load(new ByteArrayInputStream(latin1));

        final PropertiesDocument document = PropertiesDocument.parse(PropertiesDocument.decode(latin1));
        assertEquals(expected.getProperty("motd"), document.get("motd").orElseThrow());
        assertEquals("Café", PropertiesDocument.parse(PropertiesDocument.decode(
            "motd=Café\n".getBytes(StandardCharsets.UTF_8))).get("motd").orElseThrow());
    }

    @Test
    @DisplayName("entries report the physical line they start on")
    void lineNumbers() {
        final PropertiesDocument document = PropertiesDocument.parse("# c\n\na=1 \\\n  2\nb=3\n");
        final List<PropertiesDocument.Entry> entries = document.entries();
        assertEquals(3, entries.get(0).line());
        assertEquals(5, entries.get(1).line());
    }
}
