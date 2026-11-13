package io.github.adrielorihuela.legacyfolia.config.players;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.properties.AtomicFiles;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlLiterals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code config/players.toml}: the whitelist, the operators and the two ban lists as tables of
 * entries, one {@code [[whitelist]]}, {@code [[ops]]}, {@code [[banned-players]]} or
 * {@code [[banned-ips]]} per person or address.
 *
 * <p>The file is written as text rather than rendered from a parsed tree, because it is meant to
 * be edited by hand: everything before the first entry (the explanation a new file starts with,
 * and whatever the operator adds there) is kept as it is, and so is the comment block directly
 * above an entry that is still on the list. Only the entries that were added, removed or changed
 * are written anew. A list is saved whole, as vanilla saves its JSON, so this class compares what
 * it is given with what the file holds and leaves untouched text untouched.</p>
 *
 * <p>An entry is a flat map of the same fields vanilla's JSON has, with the keys in kebab case
 * ({@code bypasses-player-limit} for {@code bypassesPlayerLimit}). Values are strings, integers or
 * booleans, which is all the four lists ever hold.</p>
 */
final class PlayersFile {

    /** The section names, in the order a new file lists them. */
    static final List<String> SECTIONS = List.of("whitelist", "ops", "banned-players", "banned-ips");

    /** What a new file starts with. ASCII only. */
    static final String HEADER = """
        # LegacyFolia players.toml
        #
        # The whitelist, the operators and the two ban lists. The commands (/whitelist, /op, /ban,
        # /ban-ip and their pardons) and plugins edit this file for you, and the server rereads it
        # on /whitelist reload and on /reload. Edit it by hand only while the server is stopped, or
        # follow it with /whitelist reload: a running server writes its own copy back.
        #
        # One table per entry. Examples:
        #
        #   [[whitelist]]
        #   uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
        #   name = "Notch"
        #
        #   [[ops]]
        #   uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
        #   name = "Notch"
        #   level = 4
        #   bypasses-player-limit = false
        #
        #   [[banned-players]]
        #   uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
        #   name = "Notch"
        #   created = "2026-01-01 12:00:00 +0000"
        #   source = "Server"
        #   expires = "forever"
        #   reason = "Banned by an operator."
        #
        #   [[banned-ips]]
        #   ip = "203.0.113.7"
        #   created = "2026-01-01 12:00:00 +0000"
        #   source = "Server"
        #   expires = "forever"
        #   reason = "Banned by an operator."

        """;

    private static final Pattern ENTRY_HEADER = Pattern.compile("^\\s*\\[\\[\\s*([A-Za-z0-9_-]+)\\s*\\]\\]\\s*(#.*)?$");
    /** A plain table, {@code [name]}: the operator's own, kept as it is. */
    private static final Pattern TABLE_HEADER = Pattern.compile("^\\s*\\[[^\\[\\]]+\\]\\s*(#.*)?$");

    /** The section name under which text that is not an entry is kept. */
    private static final String RAW = "\u0000raw";

    private final Path path;

    PlayersFile(final Path path) {
        this.path = path;
    }

    /** One entry as it stands in the text: its section, its fields and the lines it takes. */
    private record Block(String section, Map<String, Object> values, String text) {
    }

    /** The file split into the text before the first entry and the entries. */
    private record Parsed(String preamble, List<Block> blocks) {
    }

    /**
     * The entries of one list.
     *
     * @param section one of {@link #SECTIONS}
     * @return the entries in file order, each a map of JSON-style field names to values; empty when
     *     the file does not exist
     * @throws IOException if the file could not be read or is not TOML
     */
    List<Map<String, Object>> read(final String section) throws IOException {
        final List<Map<String, Object>> entries = new ArrayList<>();
        for (final Block block : parse().blocks()) {
            if (block.section().equals(section)) {
                entries.add(block.values());
            }
        }
        return entries;
    }

    /**
     * Replaces the entries of one list.
     *
     * @param section one of {@link #SECTIONS}
     * @param entries the list as it is now, each a map of JSON-style field names to values
     * @return whether the file was written; false when it already said this
     * @throws IOException if the file could not be read, is not TOML, or could not be written
     */
    boolean write(final String section, final List<Map<String, Object>> entries) throws IOException {
        final Parsed parsed = parse();
        final Map<String, List<Block>> bySection = new LinkedHashMap<>();
        for (final String name : SECTIONS) {
            bySection.put(name, new ArrayList<>());
        }
        for (final Block block : parsed.blocks()) {
            bySection.computeIfAbsent(block.section(), ignored -> new ArrayList<>()).add(block);
        }

        // The blocks of this section that still say what the list says keep their text, with their
        // comments; the rest of the list is written anew, in the list's order.
        final List<Block> old = new ArrayList<>(bySection.get(section));
        final List<Block> next = new ArrayList<>();
        for (final Map<String, Object> entry : entries) {
            final Map<String, Object> normal = normalise(entry);
            Block kept = null;
            for (final Block candidate : old) {
                if (candidate.values().equals(normal)) {
                    kept = candidate;
                    break;
                }
            }
            if (kept != null) {
                old.remove(kept);
                next.add(kept);
            } else {
                next.add(new Block(section, normal, render(section, normal)));
            }
        }
        bySection.put(section, next);

        final List<String> chunks = new ArrayList<>();
        final String preamble = parsed.preamble().stripTrailing();
        if (!preamble.isEmpty()) {
            chunks.add(preamble);
        }
        for (final List<Block> blocks : bySection.values()) {
            for (final Block block : blocks) {
                chunks.add(block.text().strip());
            }
        }
        final String rendered = String.join("\n\n", chunks) + "\n";
        if (Files.exists(this.path) && AtomicFiles.read(this.path).equals(rendered)) {
            return false;
        }
        AtomicFiles.write(this.path, rendered);
        return true;
    }

    private Parsed parse() throws IOException {
        if (!Files.isRegularFile(this.path)) {
            return new Parsed(HEADER, List.of());
        }
        final String text = AtomicFiles.read(this.path);
        final List<String> lines = text.lines().toList();

        // Where each entry starts, pulled back over the comment lines directly above it.
        final List<Integer> starts = new ArrayList<>();
        final List<Integer> headers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (ENTRY_HEADER.matcher(lines.get(i)).matches() || TABLE_HEADER.matcher(lines.get(i)).matches()) {
                int start = i;
                while (start > 0 && lines.get(start - 1).strip().startsWith("#")) {
                    start--;
                }
                starts.add(start);
                headers.add(i);
            }
        }
        final int firstStart = starts.isEmpty() ? lines.size() : starts.get(0);
        final String preamble = String.join("\n", lines.subList(0, firstStart)) + (firstStart > 0 ? "\n" : "");

        final List<Block> blocks = new ArrayList<>();
        for (int n = 0; n < starts.size(); n++) {
            final int header = headers.get(n);
            final int end = n + 1 < starts.size() ? starts.get(n + 1) : lines.size();
            final String blockText = String.join("\n", lines.subList(starts.get(n), end));
            final Matcher matcher = ENTRY_HEADER.matcher(lines.get(header));
            if (matcher.matches()) {
                final String section = matcher.group(1);
                blocks.add(new Block(section, values(blockText, section), blockText));
            } else {
                // A table of the operator's own, with whatever follows it up to the next header.
                blocks.add(new Block(RAW, Map.of(), blockText));
            }
        }
        return new Parsed(preamble, blocks);
    }

    private Map<String, Object> values(final String blockText, final String section) throws IOException {
        final CommentedConfig config = CommentedConfig.inMemory();
        try {
            new TomlParser().parse(blockText, config, ParsingMode.REPLACE);
        } catch (final RuntimeException e) {
            throw new IOException(this.path + ": the [[" + section + "]] entry could not be read: " + e.getMessage(), e);
        }
        final Object list = config.get(List.of(section));
        final Map<String, Object> values = new LinkedHashMap<>();
        if (list instanceof final List<?> tables && !tables.isEmpty() && tables.get(0) instanceof final Config table) {
            for (final Map.Entry<String, Object> field : table.valueMap().entrySet()) {
                values.put(camel(field.getKey()), field.getValue() instanceof final Integer integer
                    ? Long.valueOf(integer) : field.getValue());
            }
        }
        return values;
    }

    /** Integers as longs, keys as given, so an entry read from the file equals one built from JSON. */
    private static Map<String, Object> normalise(final Map<String, Object> entry) {
        final Map<String, Object> normal = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> field : entry.entrySet()) {
            final Object value = field.getValue();
            normal.put(field.getKey(), value instanceof final Integer integer ? Long.valueOf(integer) : value);
        }
        return normal;
    }

    private static String render(final String section, final Map<String, Object> entry) {
        final StringBuilder text = new StringBuilder("[[").append(section).append("]]\n");
        for (final Map.Entry<String, Object> field : entry.entrySet()) {
            text.append(kebab(field.getKey())).append(" = ").append(TomlLiterals.render(field.getValue())).append('\n');
        }
        return text.toString();
    }

    /** {@code bypassesPlayerLimit} as {@code bypasses-player-limit}. */
    static String kebab(final String key) {
        final StringBuilder out = new StringBuilder();
        for (final char c : key.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('-').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code bypasses-player-limit} as {@code bypassesPlayerLimit}. */
    static String camel(final String key) {
        final StringBuilder out = new StringBuilder();
        boolean up = false;
        for (final char c : key.toCharArray()) {
            if (c == '-') {
                up = true;
            } else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return out.toString();
    }
}
