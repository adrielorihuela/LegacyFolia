package io.github.adrielorihuela.legacyfolia.config.properties;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A {@code .properties} file that can be changed without being rewritten.
 *
 * <p>{@link java.util.Properties} reads the format well and writes it badly for a file people edit:
 * {@code store()} throws away every comment, puts the keys in hash order and stamps a date at the
 * top. An operator who wrote notes beside their settings, or keeps them in an order that makes sense
 * to them, would lose both the first time the server saved a change. So this class keeps the file as
 * the lines it was made of, and a change touches only the line it is about.</p>
 *
 * <p>Reading follows {@code Properties.load} exactly: {@code #} and {@code !} comments, keys ending
 * at the first unescaped {@code =}, {@code :} or whitespace, lines continued with a trailing
 * backslash, and the escapes {@code \t \n \r \f \\uXXXX}. Writing escapes the way
 * {@code Properties.store(OutputStream, ...)} does, non-ASCII characters included, so whatever this
 * writes {@code Properties} reads back to the same strings, and the file stays plain ASCII.</p>
 *
 * <p>Not thread-safe. The class that owns the file serialises access to it.</p>
 */
public final class PropertiesDocument {

    /** One logical line: a comment, a blank, or an entry, with the physical lines it spans. */
    private static final class Line {

        /** The physical lines, as they were read or as they were last written. */
        final List<String> physical;

        /** {@code null} for a comment or a blank line. */
        final @Nullable String key;

        final @Nullable String value;

        /**
         * Everything before the value on a one-line entry, kept so that changing the value keeps
         * the operator's own spacing and separator; {@code null} for other lines.
         */
        final @Nullable String prefix;

        Line(final List<String> physical, final @Nullable String key, final @Nullable String value,
             final @Nullable String prefix) {
            this.physical = List.copyOf(physical);
            this.key = key;
            this.value = value;
            this.prefix = prefix;
        }
    }

    /**
     * One entry of the file.
     *
     * @param key the key, unescaped
     * @param value the value, unescaped
     * @param line the 1-based physical line the entry starts on
     */
    public record Entry(String key, String value, int line) {
    }

    private final List<Line> lines;

    private final String newline;

    private PropertiesDocument(final List<Line> lines, final String newline) {
        this.lines = lines;
        this.newline = newline;
    }

    /**
     * A new file holding only comments.
     *
     * @param comments the comment lines, each written as it is (so including its {@code #})
     * @return the document
     */
    public static PropertiesDocument create(final List<String> comments) {
        final List<Line> lines = new ArrayList<>();
        for (final String comment : comments) {
            lines.add(new Line(List.of(comment), null, null, null));
        }
        return new PropertiesDocument(lines, System.lineSeparator());
    }

    /**
     * Decodes the bytes of a properties file the way vanilla does: UTF-8 when they are valid UTF-8,
     * ISO-8859-1 otherwise, which is what {@code Properties.load(InputStream)} assumes.
     *
     * @param bytes the file's contents
     * @return the text
     */
    public static String decode(final byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (final CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Reads a file's text.
     *
     * @param text the text
     * @return the document
     */
    public static PropertiesDocument parse(final String text) {
        final String newline = text.contains("\r\n") ? "\r\n" : text.contains("\n") ? "\n" : System.lineSeparator();
        final String[] physical = text.split("\r\n|\r|\n", -1);
        // A file ending in a newline splits into one empty string too many.
        final int count = physical.length > 0 && physical[physical.length - 1].isEmpty()
            ? physical.length - 1 : physical.length;

        final List<Line> lines = new ArrayList<>();
        int i = 0;
        while (i < count) {
            final String first = physical[i];
            final String stripped = stripLeading(first);
            if (stripped.isEmpty() || stripped.charAt(0) == '#' || stripped.charAt(0) == '!') {
                lines.add(new Line(List.of(first), null, null, null));
                i++;
                continue;
            }
            final List<String> spanned = new ArrayList<>();
            final StringBuilder logical = new StringBuilder();
            String current = first;
            while (true) {
                // Properties strips the leading whitespace of every line, continuations included.
                spanned.add(current);
                logical.append(stripLeading(current));
                i++;
                if (!endsContinued(current)) {
                    break;
                }
                logical.setLength(logical.length() - 1);
                if (i >= count) {
                    break;
                }
                current = physical[i];
            }
            lines.add(entryLine(spanned, logical.toString(), first));
        }
        return new PropertiesDocument(lines, newline);
    }

    /** Splits one logical line into key and value, exactly as {@code Properties.load0} does. */
    private static Line entryLine(final List<String> physical, final String logical, final String first) {
        final int length = logical.length();
        int keyLength = 0;
        int valueStart = length;
        boolean hasSeparator = false;
        boolean escaped = false;
        while (keyLength < length) {
            final char c = logical.charAt(keyLength);
            if ((c == '=' || c == ':') && !escaped) {
                valueStart = keyLength + 1;
                hasSeparator = true;
                break;
            } else if (isWhitespace(c) && !escaped) {
                valueStart = keyLength + 1;
                break;
            }
            escaped = c == '\\' && !escaped;
            keyLength++;
        }
        while (valueStart < length) {
            final char c = logical.charAt(valueStart);
            if (!isWhitespace(c)) {
                if (!hasSeparator && (c == '=' || c == ':')) {
                    hasSeparator = true;
                } else {
                    break;
                }
            }
            valueStart++;
        }
        final String key = unescape(logical.substring(0, keyLength));
        final String value = unescape(logical.substring(Math.min(valueStart, length)));
        String prefix = null;
        if (physical.size() == 1) {
            final int leading = first.length() - stripLeading(first).length();
            prefix = first.substring(0, leading + Math.min(valueStart, length));
        }
        return new Line(physical, key, value, prefix);
    }

    private static boolean endsContinued(final String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean isWhitespace(final char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    private static String stripLeading(final String line) {
        int i = 0;
        while (i < line.length() && isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(i);
    }

    /**
     * Undoes the escapes of the format.
     *
     * <p>A malformed {@code \\u} escape makes {@code Properties} throw and lose the whole file. Here
     * it is kept as written instead, so one bad line costs one bad value.</p>
     *
     * @param text the escaped text
     * @return the text
     */
    public static String unescape(final String text) {
        if (text.indexOf('\\') == -1) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            final char next = text.charAt(++i);
            switch (next) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (isHex(text, i + 1, 4)) {
                        out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                        i += 4;
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static boolean isHex(final String text, final int from, final int count) {
        if (from + count > text.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (Character.digit(text.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Escapes a key as {@code Properties.store(OutputStream, ...)} does.
     *
     * @param key the key
     * @return the escaped key
     */
    public static String escapeKey(final String key) {
        return escape(key, true);
    }

    /**
     * Escapes a value as {@code Properties.store(OutputStream, ...)} does: only a leading space is
     * escaped, the separators and comment characters always are, and so is anything outside
     * printable ASCII.
     *
     * @param value the value
     * @return the escaped value
     */
    public static String escapeValue(final String value) {
        return escape(value, false);
    }

    private static String escape(final String text, final boolean escapeSpace) {
        final StringBuilder out = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c > 61 && c < 127) {
                out.append(c == '\\' ? "\\\\" : String.valueOf(c));
                continue;
            }
            switch (c) {
                case ' ' -> out.append(i == 0 || escapeSpace ? "\\ " : " ");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\f' -> out.append("\\f");
                case '=', ':', '#', '!' -> out.append('\\').append(c);
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Every entry, in file order. A key written twice appears twice; the last one is the one that
     * counts, as with {@code Properties}.
     *
     * @return the entries
     */
    public List<Entry> entries() {
        final List<Entry> entries = new ArrayList<>();
        int number = 1;
        for (final Line line : this.lines) {
            if (line.key != null) {
                entries.add(new Entry(line.key, Objects.requireNonNull(line.value), number));
            }
            number += line.physical.size();
        }
        return entries;
    }

    /**
     * The value a key has, the last one when it is written more than once.
     *
     * @param key the key
     * @return its value, if it is there
     */
    public Optional<String> get(final String key) {
        for (int i = this.lines.size() - 1; i >= 0; i--) {
            if (key.equals(this.lines.get(i).key)) {
                return Optional.ofNullable(this.lines.get(i).value);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the key is there at all.
     *
     * @param key the key
     * @return whether it is
     */
    public boolean contains(final String key) {
        return this.get(key).isPresent();
    }

    /**
     * Sets a value at the end of the file.
     *
     * @param key the key
     * @param value the value
     * @return whether the file changed
     */
    public boolean set(final String key, final String value) {
        return this.set(key, value, ignored -> null, ignored -> "");
    }

    /**
     * Sets a value.
     *
     * <p>A key that is already there keeps its place, and on a one-line entry its spacing and
     * separator too; only the value changes, and any earlier copies of it are dropped. A new key goes
     * after the last entry of its group. A group with no entry yet starts at the end of the file,
     * under a comment naming it.</p>
     *
     * @param key the key
     * @param value the value
     * @param groupOf the group a key belongs to, or {@code null} for none
     * @param label the comment line that introduces a group, without the {@code #}
     * @return whether the file changed
     */
    public boolean set(final String key, final String value, final Function<String, @Nullable String> groupOf,
                       final Function<String, String> label) {
        int last = -1;
        for (int i = 0; i < this.lines.size(); i++) {
            if (key.equals(this.lines.get(i).key)) {
                last = i;
            }
        }
        if (last != -1) {
            final Line existing = this.lines.get(last);
            boolean changed = this.removeAll(key, last);
            last = this.indexOfKey(key);
            if (value.equals(existing.value)) {
                return changed;
            }
            final String prefix = existing.prefix != null ? existing.prefix : escapeKey(key) + "=";
            this.lines.set(last, new Line(List.of(prefix + escapeValue(value)), key, value, prefix));
            return true;
        }

        final String prefix = escapeKey(key) + "=";
        final Line line = new Line(List.of(prefix + escapeValue(value)), key, value, prefix);
        final String group = groupOf.apply(key);
        if (group != null) {
            for (int i = this.lines.size() - 1; i >= 0; i--) {
                final String other = this.lines.get(i).key;
                if (other != null && group.equals(groupOf.apply(other))) {
                    this.lines.add(i + 1, line);
                    return true;
                }
            }
        }
        if (!this.lines.isEmpty() && !this.lines.getLast().physical.getLast().isBlank()) {
            this.lines.add(new Line(List.of(""), null, null, null));
        }
        if (group != null) {
            this.lines.add(new Line(List.of("# " + escapeComment(label.apply(group))), null, null, null));
        }
        this.lines.add(line);
        return true;
    }

    private int indexOfKey(final String key) {
        for (int i = 0; i < this.lines.size(); i++) {
            if (key.equals(this.lines.get(i).key)) {
                return i;
            }
        }
        return -1;
    }

    /** Drops every copy of a key except the one at {@code keep}. */
    private boolean removeAll(final String key, final int keep) {
        boolean removed = false;
        for (int i = keep - 1; i >= 0; i--) {
            if (key.equals(this.lines.get(i).key)) {
                this.lines.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Removes a key, every copy of it. Comments stay where they are.
     *
     * @param key the key
     * @return whether it was there
     */
    public boolean remove(final String key) {
        return this.lines.removeIf(line -> key.equals(line.key));
    }

    /** Whether the file has no entries, only comments and blanks. */
    public boolean isEmpty() {
        return this.lines.stream().allMatch(line -> line.key == null);
    }

    /**
     * The file's text, every untouched line exactly as it was read.
     *
     * @return the text, ending in a newline
     */
    public String render() {
        final StringBuilder out = new StringBuilder();
        for (final Line line : this.lines) {
            for (final String physical : line.physical) {
                out.append(physical).append(this.newline);
            }
        }
        return out.toString();
    }

    /** Keeps a generated comment ASCII and on one line, whatever a world happens to be called. */
    private static String escapeComment(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
