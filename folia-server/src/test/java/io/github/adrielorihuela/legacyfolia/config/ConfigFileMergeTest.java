package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.adrielorihuela.legacyfolia.config.properties.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The merge is the one part of loading that can destroy somebody's work, so it is pinned down
 * here: it may only ever add, and what it adds is the template's own text, comment included.
 */
class ConfigFileMergeTest {

    private static final String TEMPLATE = """
        # ---------------------------------------------------------------------------
        #  Dimensions
        # ---------------------------------------------------------------------------
        #  Which worlds exist.
        [dimensions]

        # The Nether.
        nether = true

        # The End.
        end = true
        """;

    private static Object value(final String text, final String path) {
        return ConfigFile.parse(text).get(path);
    }

    @Test
    @DisplayName("an empty file gains every setting the template has, with the template's text")
    void fillsAnEmptyFile() {
        final ConfigFile.Merged merged = ConfigFile.merge("", TEMPLATE);

        // A wholly missing section is copied in one go, so it is reported as the one path added
        // rather than as each of the keys inside it.
        assertIterableEquals(List.of("dimensions"), merged.added());
        assertEquals(TEMPLATE, merged.text());
    }

    @Test
    @DisplayName("a value the administrator changed is never reset to the default")
    void keepsChangedValues() {
        final String file = """
            [dimensions]
            nether = false
            end = false
            """;

        final ConfigFile.Merged merged = ConfigFile.merge(file, TEMPLATE);

        assertTrue(merged.added().isEmpty(), "nothing was missing, so nothing should have been added");
        assertEquals(file, merged.text());
    }

    @Test
    @DisplayName("a setting added by a new release appears with its comment, after the key before it")
    void addsNewSettingsOnUpgrade() {
        final String file = """
            [dimensions]

            # The Nether, as I like it.
            nether = false
            """;

        final ConfigFile.Merged merged = ConfigFile.merge(file, TEMPLATE);

        assertIterableEquals(List.of("dimensions.end"), merged.added());
        assertEquals(file + "\n# The End.\nend = true\n", merged.text());
        assertEquals(Boolean.FALSE, value(merged.text(), "dimensions.nether"), "the existing value must survive the upgrade");
        assertEquals(Boolean.TRUE, value(merged.text(), "dimensions.end"));
    }

    @Test
    @DisplayName("a key the template does not know about is left in place")
    void keepsUnknownKeys() {
        final String file = """
            [dimensions]
            from-a-newer-build = "keep me"
            """;

        final ConfigFile.Merged merged = ConfigFile.merge(file, TEMPLATE);

        assertEquals("keep me", value(merged.text(), "dimensions.from-a-newer-build"));
        assertTrue(isSubsequence(file, merged.text()), "nothing of the file is lost or reordered");
    }

    @Test
    @DisplayName("merging twice changes nothing the second time")
    void isIdempotent() {
        final ConfigFile.Merged first = ConfigFile.merge("", TEMPLATE);
        final ConfigFile.Merged second = ConfigFile.merge(first.text(), TEMPLATE);

        assertTrue(second.added().isEmpty(), "a second merge should find nothing left to add");
        assertEquals(first.text(), second.text());
    }

    @Test
    @DisplayName("a section replaced by a scalar is rebuilt rather than merged into")
    void replacesAScalarStandingWhereASectionBelongs() {
        final ConfigFile.Merged merged = ConfigFile.merge("dimensions = \"not a section\"\n", TEMPLATE);

        assertIterableEquals(List.of("dimensions"), merged.added());
        assertTrue(ConfigFile.parse(merged.text()).get("dimensions") instanceof CommentedConfig);
        assertFalse(merged.text().contains("not a section"));
    }

    @Test
    @DisplayName("a table of data, such as the command aliases, is never topped up from the template")
    void leavesDataTablesAlone() {
        final String template = """
            [plugins.commands.aliases]
            icanhasbukkit = ["version $1-"]
            """;
        final String file = """
            [plugins.commands.aliases]
            hi = ["say hi"]
            """;

        final ConfigFile.Merged merged = ConfigFile.merge(file, template);

        assertTrue(merged.added().isEmpty(), "the operator removed the default alias; it must stay removed");
        assertEquals(file, merged.text());
    }

    @Test
    @DisplayName("a new table lands where the template has it, banner and all")
    void addsAMissingTableInPlace() {
        final String template = """
            # -- First --
            [first]
            a = 1

            # -- Second --
            #  What the second is for.
            [second]

            # B.
            b = 2

            [second.inner]
            c = 3

            # -- Third --
            [third]
            d = 4
            """;
        final String file = """
            # -- First --
            [first]
            a = 10

            # -- Third --
            [third]
            d = 40
            """;

        final ConfigFile.Merged merged = ConfigFile.merge(file, template);

        assertIterableEquals(List.of("second"), merged.added());
        assertEquals("""
            # -- First --
            [first]
            a = 10

            # -- Second --
            #  What the second is for.
            [second]

            # B.
            b = 2

            [second.inner]
            c = 3

            # -- Third --
            [third]
            d = 40
            """, merged.text());
    }

    /**
     * The whole contract at once, on a real template: a file full of comments, commented-out
     * examples, tabs, odd spacing, arrays over several lines and inline tables gains new keys
     * and has values changed, and every byte of it that was not one of those values is where it was.
     */
    @Test
    @DisplayName("adding keys and changing values leaves every other byte of the file as it was")
    void leavesEverythingElseAlone(@TempDir final Path dir) throws IOException {
        final String template = new String(ConfigFile.class.getClassLoader()
            .getResourceAsStream("legacyfolia/" + ServerConfig.SERVER).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
        // The operator's file: the template with its own touches, and missing two keys a newer
        // release added: the whole of [server.watchdog] and one key of [server.status].
        final String watchdog = template.substring(template.indexOf("# ----------------------------------------------------------------------------\n#  Watchdog"),
            template.indexOf("# ----------------------------------------------------------------------------\n#  Saving"));
        assertTrue(template.contains(watchdog));
        String operator = template.replace(watchdog, "")
            .replace("motd = \"A Minecraft Server\"", "motd   =   \"Mine\"    # my motd")
            .replace("max-players = 20", "max-players = 20\n\t# a tab-indented note of mine\n# disabled = [\n#   \"example\",\n# ]");
        final int status = operator.indexOf("hide-online-players");
        assertTrue(status > 0, "the template has hide-online-players");
        final int statusLineStart = operator.lastIndexOf('\n', status) + 1;
        final int statusLineEnd = operator.indexOf('\n', status) + 1;
        final int statusCommentStart = commentStart(operator, statusLineStart);
        operator = operator.substring(0, statusCommentStart) + operator.substring(statusLineEnd);
        operator = operator + "\n# A closing note of mine, after everything.\n#   with an example = [1, 2]\n";
        assertFalse(ConfigFile.parse(operator).contains("server.watchdog"));
        assertFalse(ConfigFile.parse(operator).contains("server.status.hide-online-players"));

        final Path file = dir.resolve(ServerConfig.SERVER);
        Files.writeString(file, operator);
        final ConfigFile config = ConfigFile.open(dir, ServerConfig.SERVER);
        final String merged = AtomicFiles.read(file);

        // Every line of the operator's file is still there, in order, and only whole blocks of the
        // template's own text were put between them.
        assertTrue(isSubsequence(operator, merged), "the merge removed or changed something of the operator's");
        assertTrue(merged.contains(watchdog.strip()), "the watchdog section is the template's, comments and all");
        assertTrue(merged.indexOf("[server.watchdog]") < merged.indexOf("[server.saving]"), "and where the template has it");
        assertEquals(60, ((Number) config.raw().get("server.watchdog.timeout-seconds")).intValue());
        assertEquals(Boolean.FALSE, config.raw().get("server.status.hide-online-players"));
        assertTrue(merged.endsWith("#   with an example = [1, 2]\n"));

        // Now change values: only the values change.
        config.set("server.motd", "Changed");
        config.set("server.max-players", 50);
        config.set("server.watchdog.restart-script", "./run.sh");
        final String edited = AtomicFiles.read(file);
        final String expected = merged
            .replace("motd   =   \"Mine\"    # my motd", "motd   =   \"Changed\"    # my motd")
            .replace("max-players = 20", "max-players = 50")
            .replace("restart-script = \"./start.sh\"", "restart-script = \"./run.sh\"");
        assertEquals(expected, edited);
        assertEquals("Changed", config.getString("server.motd", ""));
        assertEquals(50, config.getInt("server.max-players", 0));

        // A second start finds nothing to add, and changes nothing.
        ConfigFile.open(dir, ServerConfig.SERVER);
        assertEquals(edited, AtomicFiles.read(file));
    }

    @Test
    @DisplayName("a value set through the server keeps the worked examples at the end of worlds.toml")
    void setKeepsTrailingComments(@TempDir final Path dir) throws IOException {
        final ConfigFile worlds = ConfigFile.open(dir, ServerConfig.WORLDS);
        final String before = AtomicFiles.read(dir.resolve(ServerConfig.WORLDS));
        worlds.set("worlds.default.chunks", 1);
        final String after = AtomicFiles.read(dir.resolve(ServerConfig.WORLDS));
        assertTrue(after.contains("Worked examples"));
        assertEquals(before, after.replace("chunks = 1\n", ""));
        final UnmodifiableConfig parsed = ConfigFile.parse(after);
        assertEquals(1, ((Number) parsed.get("worlds.default.chunks")).intValue());
    }

    /**
     * Every table of every bundled file, taken out with its banner and merged back, comes back
     * exactly as the template has it: same place, same comments, same blank lines. This is what an
     * existing server sees when a release adds a section.
     */
    @Test
    @DisplayName("a table a release adds comes back exactly where and as the template has it")
    void everyBundledTableMergesBackExactly() throws IOException {
        int checked = 0;
        for (final String file : ServerConfig.fileNames()) {
            final String template = new String(ConfigFile.class.getClassLoader()
                .getResourceAsStream("legacyfolia/" + file).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
            final List<Integer> headers = new java.util.ArrayList<>();
            final List<String> names = new java.util.ArrayList<>();
            for (int at = 0; at < template.length(); at = template.indexOf('\n', at) + 1) {
                if (template.startsWith("[", at) && !template.startsWith("[[", at)) {
                    headers.add(at);
                    names.add(template.substring(at + 1, template.indexOf(']', at)));
                }
                if (template.indexOf('\n', at) == -1) {
                    break;
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                final String name = names.get(i);
                final boolean last = i + 1 == headers.size();
                if (!last && names.get(i + 1).startsWith(name + ".")) {
                    continue;
                }
                final int start = commentStart(template, headers.get(i));
                final int end = last ? template.length() : commentStart(template, headers.get(i + 1));
                final String without = template.substring(0, start) + template.substring(end);
                final ConfigFile.Merged merged = ConfigFile.merge(without, template);
                assertFalse(merged.added().isEmpty(), file + " [" + name + "]: nothing was added back");
                assertEquals(template.stripTrailing(), merged.text().stripTrailing(), file + " [" + name + "]");
                checked++;
            }
        }
        assertTrue(checked > 30, "only " + checked + " tables were checked");
    }

    private static int commentStart(final String text, final int lineStart) {
        int start = lineStart;
        while (start > 0) {
            final int previous = text.lastIndexOf('\n', start - 2) + 1;
            if (!text.substring(previous, start - 1).strip().startsWith("#")) {
                return start;
            }
            start = previous;
        }
        return start;
    }

    /** Whether every line of {@code part} appears in {@code whole}, in the same order. */
    private static boolean isSubsequence(final String part, final String whole) {
        final List<String> wholeLines = whole.lines().toList();
        int at = 0;
        for (final String line : part.lines().toList()) {
            while (at < wholeLines.size() && !wholeLines.get(at).equals(line)) {
                at++;
            }
            if (at == wholeLines.size()) {
                return false;
            }
            at++;
        }
        return true;
    }
}
