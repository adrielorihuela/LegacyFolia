package io.github.adrielorihuela.legacyfolia.config.properties;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Changes single values in a TOML file without rewriting the rest of it.
 *
 * <p>The configuration files are mostly comments: a header, an explanation above every key, and in
 * {@code worlds.toml} a page of commented-out examples. night-config keeps the comments it can attach
 * to a key and loses the others when it saves, and it re-indents what is left. So nothing writes
 * these files through night-config: every write, from the server.properties mode, from
 * {@code ConfigFile} when a release adds keys or a setting is changed, and from the importer, goes
 * through this class, which edits the text. It finds the line that holds a key and replaces the
 * value on it, or inserts a new key or table with its comment copied from the template, and every
 * other byte of the file stays where it was.</p>
 *
 * <p>It understands enough TOML to find keys reliably: table headers, bare, quoted and dotted keys,
 * strings of all four kinds, and arrays and inline tables spanning lines. It is only ever given
 * files night-config has already parsed, and every edit is parsed again before it is accepted, so a
 * mistake here is an exception rather than a damaged file.</p>
 *
 * <p>Paths are given as a list of exact segments followed by a dotted rest, because the two halves
 * behave differently: {@code worlds.<world>} names a table by exact segments (a world called
 * {@code a.b} is one segment), while the setting under it may be written either as one quoted key or
 * as nested tables, and both must be found.</p>
 */
public final class TomlText {

    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_-]+");

    /** A {@code key = value} statement. */
    private record KeyLine(List<String> path, int header, int lineStart, int valueStart, int valueEnd, int lineEnd) {
    }

    /** A {@code [table]} or {@code [[array]]} header line. */
    private record Header(List<String> path, boolean array, int lineStart, int lineEnd) {
    }

    private final String text;

    private final String newline;

    private final List<KeyLine> keys = new ArrayList<>();

    private final List<Header> headers = new ArrayList<>();

    private TomlText(final String text) {
        this.text = text;
        this.newline = text.contains("\r\n") ? "\r\n" : "\n";
        new Scanner().scan();
    }

    /**
     * Reads a file's text.
     *
     * @param text the text
     * @return the parsed outline of the file
     * @throws IllegalArgumentException if the text is not TOML this class can follow
     */
    public static TomlText parse(final String text) {
        return new TomlText(text);
    }

    /** The file's text, with every edit applied. */
    public String text() {
        return this.text;
    }

    /**
     * Whether a value is written for a path, in any form.
     *
     * @param prefix exact leading segments
     * @param rest the dotted rest
     * @return whether a key or a table stands at that path
     */
    public boolean contains(final List<String> prefix, final String rest) {
        for (final KeyLine key : this.keys) {
            if (matches(key.path, prefix, rest) || under(key.path, prefix, rest)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets a value, replacing it where it is written or adding it where it belongs.
     *
     * <p>Whatever was written under the path in another form, such as a table where the new value
     * is an inline one, is removed first, so the path ends up written exactly once.</p>
     *
     * @param prefix exact leading segments, such as {@code ["worlds", "world_nether"]}, or none
     * @param rest the dotted rest, such as {@code chunks.view-distance}
     * @param literal the value, already written as TOML
     * @param quoteRest whether a new key is written as one quoted key, as {@code worlds.toml} does,
     *     rather than as bare segments under the deepest table that exists
     * @return the edited file
     */
    public TomlText set(final List<String> prefix, final String rest, final String literal, final boolean quoteRest) {
        if (literal.trim().startsWith("{") && this.header(prefix, rest) != null
            && parseValue(literal) instanceof final Map<?, ?> table) {
            return this.setTable(prefix, rest, table);
        }
        TomlText current = this.removeUnder(prefix, rest, false);
        for (final KeyLine key : current.keys) {
            if (matches(key.path, prefix, rest)) {
                if (current.text.substring(key.valueStart, key.valueEnd).equals(literal)) {
                    return current;
                }
                return current.replace(key.valueStart, key.valueEnd, literal);
            }
        }
        return current.insert(prefix, rest, literal, quoteRest);
    }

    /**
     * Removes a value and anything written under it, wherever and however it is written.
     *
     * @param prefix exact leading segments
     * @param rest the dotted rest
     * @return the edited file
     */
    public TomlText remove(final List<String> prefix, final String rest) {
        TomlText current = this.removeUnder(prefix, rest, false);
        for (int i = current.keys.size() - 1; i >= 0; i--) {
            final KeyLine key = current.keys.get(i);
            if (matches(key.path, prefix, rest)) {
                current = current.replace(key.lineStart, key.lineEnd, "");
            }
        }
        return current;
    }

    /**
     * Removes the headers of tables at or below {@code prefix} that no longer hold any value.
     *
     * @param prefix the subtree to tidy, such as {@code ["worlds"]}
     * @param keep a table to keep even when empty, such as {@code ["worlds", "default"]}
     * @return the edited file
     */
    public TomlText removeEmptyTables(final List<String> prefix, final List<String> keep) {
        TomlText current = this;
        boolean again = true;
        while (again) {
            again = false;
            for (int i = current.headers.size() - 1; i >= 0; i--) {
                final Header header = current.headers.get(i);
                if (header.array || header.path.size() <= prefix.size() || !startsWith(header.path, prefix)
                    || header.path.equals(keep)) {
                    continue;
                }
                boolean empty = true;
                for (final KeyLine key : current.keys) {
                    if (startsWith(key.path, header.path)) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    current = current.replace(header.lineStart, header.lineEnd, "");
                    again = true;
                    break;
                }
            }
        }
        return current;
    }

    /**
     * Adds a key or a table that this file lacks, copied from a template together with the comment
     * that explains it, and puts it where the template has it.
     *
     * <p>A key is copied with the comment lines right above it and lands after the nearest key
     * that comes before it in the template and that this file already has under the same table
     * header, or right after the header when there is none. A table is copied with its banner (the
     * comment lines right above its header) and everything under it, down to the next table that
     * is not inside it, and lands before the first table that follows it in the template and that
     * this file has, or at the end. Either way the text is copied byte for byte, only adopting this
     * file's line endings, and nothing else in the file moves.</p>
     *
     * @param template the template's text
     * @param path the key's or the table's path, as exact segments
     * @return the edited file, or {@code null} when the template has no line of its own for the
     *     path (a table that only exists through its sub-tables) or this file has nowhere to put
     *     it (the key's table is written here in another form); the caller decides what to do
     */
    public TomlText addFromTemplate(final TomlText template, final List<String> path) {
        final int tableIndex = template.headerIndex(path);
        if (tableIndex != -1) {
            return this.addTable(template, tableIndex);
        }
        for (final KeyLine key : template.keys) {
            if (key.path.equals(path)) {
                return this.addKey(template, key);
            }
        }
        return null;
    }

    private TomlText addKey(final TomlText template, final KeyLine key) {
        final List<String> table = key.header == -1 ? List.of() : template.headers.get(key.header).path;
        final int here = table.isEmpty() ? -1 : this.headerIndex(table);
        if (!table.isEmpty() && here == -1) {
            return null;
        }
        int at = here == -1 ? 0 : this.headers.get(here).lineEnd;
        // The nearest earlier sibling in the template that this file has, under the same header.
        for (int i = template.keys.indexOf(key) - 1; i >= 0; i--) {
            final KeyLine sibling = template.keys.get(i);
            if (sibling.header != key.header) {
                continue;
            }
            final KeyLine mine = this.key(sibling.path, here);
            if (mine != null) {
                at = mine.lineEnd;
                break;
            }
        }
        final String block = this.adopt(template.text.substring(template.commentStart(key.lineStart), key.lineEnd));
        if (at == 0) {
            return this.replace(0, 0, block + this.newline);
        }
        final String lead = this.text.substring(0, at).endsWith("\n") ? "" : this.newline;
        return this.replace(at, at, lead + this.newline + block);
    }

    private TomlText addTable(final TomlText template, final int index) {
        final Header header = template.headers.get(index);
        int end = template.text.length();
        int following = -1;
        for (int i = index + 1; i < template.headers.size(); i++) {
            final Header next = template.headers.get(i);
            if (!startsWith(next.path, header.path) || next.path.size() == header.path.size()) {
                end = template.commentStart(next.lineStart);
                following = i;
                break;
            }
        }
        final String block = this.adopt(stripTrailingBlankLines(
            template.text.substring(template.commentStart(header.lineStart), end)));
        // Before the first table that follows it in the template and that this file has.
        if (following != -1) {
            for (int i = following; i < template.headers.size(); i++) {
                final int mine = this.headerIndex(template.headers.get(i).path);
                if (mine != -1) {
                    final int at = this.commentStart(this.headers.get(mine).lineStart);
                    return this.replace(at, at, block + this.newline);
                }
            }
        }
        final String normal = this.text.replace("\r\n", "\n");
        final String lead = normal.isEmpty() || normal.endsWith("\n\n") ? ""
            : normal.endsWith("\n") ? this.newline : this.newline + this.newline;
        return this.replace(this.text.length(), this.text.length(), lead + block);
    }

    /** The index of the {@code [table]} header written for exactly this path, or -1. */
    private int headerIndex(final List<String> path) {
        for (int i = 0; i < this.headers.size(); i++) {
            if (!this.headers.get(i).array && this.headers.get(i).path.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private KeyLine key(final List<String> path, final int header) {
        for (final KeyLine key : this.keys) {
            if (key.header == header && key.path.equals(path)) {
                return key;
            }
        }
        return null;
    }

    /** Where the run of comment lines right above a line begins; the line itself if there is none. */
    private int commentStart(final int lineStart) {
        int start = lineStart;
        while (start > 0) {
            // The previous line runs from just after the newline before it up to its own newline.
            int previousEnd = start - 1;
            if (previousEnd > 0 && this.text.charAt(previousEnd - 1) == '\r') {
                previousEnd--;
            }
            final int previousStart = this.text.lastIndexOf('\n', start - 2) + 1;
            if (previousStart > previousEnd || !this.text.substring(previousStart, previousEnd).strip().startsWith("#")) {
                break;
            }
            start = previousStart;
        }
        return start;
    }

    /** A block from another file with this file's line endings, ending in exactly one of them. */
    private String adopt(final String block) {
        final String normal = block.replace("\r\n", "\n");
        final String ended = normal.endsWith("\n") ? normal : normal + "\n";
        return "\n".equals(this.newline) ? ended : ended.replace("\n", this.newline);
    }

    /** A block cut at the end of its last line that is not blank. */
    private static String stripTrailingBlankLines(final String block) {
        int end = block.length();
        while (end > 0 && Character.isWhitespace(block.charAt(end - 1))) {
            end--;
        }
        return block.substring(0, end) + "\n";
    }

    /** The {@code [table]} header written for exactly this path, if the file writes it that way. */
    private Header header(final List<String> prefix, final String rest) {
        for (final Header header : this.headers) {
            if (!header.array && matches(header.path, prefix, rest)) {
                return header;
            }
        }
        return null;
    }

    /**
     * Replaces the body of a table the file writes under its own header, keeping the header where
     * it is, as the templates write {@code [plugins.commands.aliases]} and {@code [world.generators]}.
     */
    private TomlText setTable(final List<String> prefix, final String rest, final Map<?, ?> table) {
        final TomlText current = this.removeUnder(prefix, rest, true);
        final Header header = current.header(prefix, rest);
        final StringBuilder body = new StringBuilder();
        for (final Map.Entry<?, ?> entry : table.entrySet()) {
            body.append(segment(String.valueOf(entry.getKey()))).append(" = ")
                .append(TomlLiterals.render(entry.getValue())).append(this.newline);
        }
        return current.replace(header.lineEnd, header.lineEnd, body.toString());
    }

    private TomlText removeUnder(final List<String> prefix, final String rest, final boolean keepHeader) {
        TomlText current = this;
        boolean again = true;
        while (again) {
            again = false;
            // A table header at the path or under it goes, with its own key lines.
            for (final Header header : current.headers) {
                if (!keepHeader && matches(header.path, prefix, rest) || under(header.path, prefix, rest)) {
                    int end = current.text.length();
                    for (final Header next : current.headers) {
                        if (next.lineStart > header.lineStart) {
                            end = next.lineStart;
                            break;
                        }
                    }
                    current = current.replace(header.lineStart, end, "");
                    again = true;
                    break;
                }
            }
            if (again) {
                continue;
            }
            for (int i = current.keys.size() - 1; i >= 0; i--) {
                final KeyLine key = current.keys.get(i);
                if (under(key.path, prefix, rest)) {
                    current = current.replace(key.lineStart, key.lineEnd, "");
                    again = true;
                    break;
                }
            }
        }
        return current;
    }

    private TomlText insert(final List<String> prefix, final String rest, final String literal, final boolean quoteRest) {
        // The deepest table that the path lies inside.
        Header best = null;
        int bestIndex = -1;
        for (int i = 0; i < this.headers.size(); i++) {
            final Header header = this.headers.get(i);
            if (header.array || header.path.size() < prefix.size() || !startsWith(header.path, prefix)) {
                continue;
            }
            final String headerRest = String.join(".", header.path.subList(prefix.size(), header.path.size()));
            final boolean inside = headerRest.isEmpty() || !quoteRest && rest.startsWith(headerRest + ".");
            if (inside && (best == null || header.path.size() > best.path.size())) {
                best = header;
                bestIndex = i;
            }
        }

        if (best != null) {
            final String headerRest = String.join(".", best.path.subList(prefix.size(), best.path.size()));
            final String relative = headerRest.isEmpty() ? rest : rest.substring(headerRest.length() + 1);
            final String line = key(relative, quoteRest) + " = " + literal + this.newline;
            int at = best.lineEnd;
            for (final KeyLine key : this.keys) {
                if (key.header == bestIndex) {
                    at = Math.max(at, key.lineEnd);
                }
            }
            final String lead = at == this.text.length() && !this.text.isEmpty() && !this.text.endsWith("\n")
                ? this.newline : "";
            return this.replace(at, at, lead + line);
        }

        final List<String> table = new ArrayList<>(prefix);
        String relative = rest;
        if (!quoteRest && prefix.isEmpty()) {
            final int dot = rest.lastIndexOf('.');
            if (dot != -1) {
                table.addAll(List.of(rest.substring(0, dot).split("\\.")));
                relative = rest.substring(dot + 1);
            }
        }
        // A new table goes after the last thing the file says, but before a block of comments that
        // closes the file, such as the worked examples at the end of worlds.toml, which would
        // otherwise end up reading as if they introduced it.
        final int contentEnd = this.contentEnd();
        if (contentEnd > 0 && contentEnd < this.text.length() && !table.isEmpty()) {
            final StringBuilder out = new StringBuilder(this.newline).append('[');
            for (int i = 0; i < table.size(); i++) {
                out.append(i == 0 ? "" : ".").append(segment(table.get(i)));
            }
            out.append(']').append(this.newline);
            out.append(key(relative, quoteRest)).append(" = ").append(literal).append(this.newline);
            return this.replace(contentEnd, contentEnd, out.toString());
        }
        final StringBuilder out = new StringBuilder();
        if (!this.text.isEmpty() && !this.text.endsWith("\n")) {
            out.append(this.newline);
        }
        if (!table.isEmpty()) {
            out.append(this.newline).append('[');
            for (int i = 0; i < table.size(); i++) {
                out.append(i == 0 ? "" : ".").append(segment(table.get(i)));
            }
            out.append(']').append(this.newline);
        }
        out.append(key(relative, quoteRest)).append(" = ").append(literal).append(this.newline);
        return this.replace(this.text.length(), this.text.length(), out.toString());
    }

    /**
     * Where the last key or table header ends, when only comments and blank lines follow it and
     * there is at least one comment among them; otherwise -1.
     */
    private int contentEnd() {
        int end = 0;
        for (final KeyLine key : this.keys) {
            end = Math.max(end, key.lineEnd);
        }
        for (final Header header : this.headers) {
            end = Math.max(end, header.lineEnd);
        }
        if (end == 0 || end >= this.text.length() || this.text.indexOf('#', end) == -1) {
            return -1;
        }
        return end;
    }

    private static String key(final String dotted, final boolean quoted) {
        if (quoted) {
            return TomlLiterals.string(dotted);
        }
        final StringBuilder out = new StringBuilder();
        for (final String part : dotted.split("\\.")) {
            out.append(out.isEmpty() ? "" : ".").append(segment(part));
        }
        return out.toString();
    }

    private static String segment(final String segment) {
        return BARE.matcher(segment).matches() ? segment : TomlLiterals.string(segment);
    }

    private TomlText replace(final int start, final int end, final String with) {
        final String edited = this.text.substring(0, start) + with + this.text.substring(end);
        try {
            new TomlParser().parse(new StringReader(edited), CommentedConfig.inMemory(), ParsingMode.REPLACE);
        } catch (final ParsingException e) {
            throw new IllegalStateException("editing the TOML text produced invalid TOML: " + e.getMessage(), e);
        }
        return new TomlText(edited);
    }

    private static boolean startsWith(final List<String> path, final List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private static boolean matches(final List<String> path, final List<String> prefix, final String rest) {
        return path.size() > prefix.size() && startsWith(path, prefix)
            && String.join(".", path.subList(prefix.size(), path.size())).equals(rest);
    }

    private static boolean under(final List<String> path, final List<String> prefix, final String rest) {
        return path.size() > prefix.size() && startsWith(path, prefix)
            && String.join(".", path.subList(prefix.size(), path.size())).startsWith(rest + ".");
    }

    /**
     * Parses one TOML value written on its own, such as {@code [1, 2]} or {@code { a = 1 }}.
     *
     * @param literal the value as TOML
     * @return it as plain Java values: maps and lists for tables and arrays
     * @throws IllegalArgumentException if it is not a TOML value
     */
    public static Object parseValue(final String literal) {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        try {
            new TomlParser().parse(new StringReader("v = " + literal + "\n"), parsed, ParsingMode.REPLACE);
        } catch (final ParsingException e) {
            throw new IllegalArgumentException(literal + " is not a TOML value: " + e.getMessage(), e);
        }
        if (parsed.size() != 1 || !parsed.contains("v")) {
            throw new IllegalArgumentException(literal + " is not a single TOML value");
        }
        return plain(parsed.get("v"));
    }

    private static Object plain(final Object value) {
        if (value instanceof final UnmodifiableConfig table) {
            final Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (final UnmodifiableConfig.Entry entry : table.entrySet()) {
                map.put(entry.getKey(), plain(entry.getValue()));
            }
            return java.util.Collections.unmodifiableMap(map);
        }
        if (value instanceof final List<?> list) {
            final List<Object> copy = new ArrayList<>(list.size());
            for (final Object element : list) {
                copy.add(plain(element));
            }
            return java.util.Collections.unmodifiableList(copy);
        }
        return value;
    }

    /** Walks the text once, recording every header and every key statement. */
    private final class Scanner {

        private final String s = TomlText.this.text;

        private int pos;

        private List<String> table = List.of();

        private int header = -1;

        void scan() {
            while (this.pos < this.s.length()) {
                final int lineStart = this.pos;
                this.skipBlanks();
                if (this.pos >= this.s.length()) {
                    break;
                }
                final char c = this.s.charAt(this.pos);
                if (c == '\n' || c == '\r') {
                    this.skipNewline();
                } else if (c == '#') {
                    this.skipToLineEnd();
                    this.skipNewline();
                } else if (c == '[') {
                    final boolean array = this.s.startsWith("[[", this.pos);
                    this.pos += array ? 2 : 1;
                    this.skipBlanks();
                    final List<String> path = this.key();
                    this.skipBlanks();
                    this.expect(array ? "]]" : "]");
                    this.skipToLineEnd();
                    this.skipNewline();
                    TomlText.this.headers.add(new Header(path, array, lineStart, this.pos));
                    this.table = path;
                    this.header = TomlText.this.headers.size() - 1;
                } else {
                    final List<String> relative = this.key();
                    this.skipBlanks();
                    this.expect("=");
                    this.skipBlanks();
                    final int valueStart = this.pos;
                    final int valueEnd = this.value();
                    this.skipToLineEnd();
                    this.skipNewline();
                    final List<String> path = new ArrayList<>(this.table);
                    path.addAll(relative);
                    final boolean inArray = this.header >= 0 && TomlText.this.headers.get(this.header).array;
                    if (!inArray) {
                        TomlText.this.keys.add(new KeyLine(List.copyOf(path), this.header, lineStart,
                            valueStart, valueEnd, this.pos));
                    }
                }
            }
        }

        private List<String> key() {
            final List<String> segments = new ArrayList<>();
            while (true) {
                this.skipBlanks();
                if (this.pos >= this.s.length()) {
                    throw this.error("a key");
                }
                final char c = this.s.charAt(this.pos);
                if (c == '"') {
                    final int end = this.basicStringEnd(this.pos);
                    segments.add(unescapeBasic(this.s.substring(this.pos + 1, end - 1)));
                    this.pos = end;
                } else if (c == '\'') {
                    final int end = this.s.indexOf('\'', this.pos + 1);
                    if (end == -1) {
                        throw this.error("the end of a literal key");
                    }
                    segments.add(this.s.substring(this.pos + 1, end));
                    this.pos = end + 1;
                } else {
                    final int start = this.pos;
                    while (this.pos < this.s.length() && isBare(this.s.charAt(this.pos))) {
                        this.pos++;
                    }
                    if (start == this.pos) {
                        throw this.error("a key");
                    }
                    segments.add(this.s.substring(start, this.pos));
                }
                this.skipBlanks();
                if (this.pos < this.s.length() && this.s.charAt(this.pos) == '.') {
                    this.pos++;
                    continue;
                }
                return List.copyOf(segments);
            }
        }

        /** Skips one value and returns where it ends, before any trailing blanks and comment. */
        private int value() {
            int depth = 0;
            int end = this.pos;
            while (this.pos < this.s.length()) {
                final char c = this.s.charAt(this.pos);
                if (this.s.startsWith("\"\"\"", this.pos)) {
                    this.pos = this.multilineEnd("\"\"\"", true);
                    end = this.pos;
                } else if (this.s.startsWith("'''", this.pos)) {
                    this.pos = this.multilineEnd("'''", false);
                    end = this.pos;
                } else if (c == '"') {
                    this.pos = this.basicStringEnd(this.pos);
                    end = this.pos;
                } else if (c == '\'') {
                    final int close = this.s.indexOf('\'', this.pos + 1);
                    if (close == -1) {
                        throw this.error("the end of a literal string");
                    }
                    this.pos = close + 1;
                    end = this.pos;
                } else if (c == '[' || c == '{') {
                    depth++;
                    this.pos++;
                    end = this.pos;
                } else if (c == ']' || c == '}') {
                    depth--;
                    this.pos++;
                    end = this.pos;
                } else if (c == '#') {
                    this.skipToLineEnd();
                    if (depth == 0) {
                        return end;
                    }
                } else if (c == '\n' || c == '\r') {
                    if (depth == 0) {
                        return end;
                    }
                    this.pos++;
                } else if (c == ' ' || c == '\t') {
                    this.pos++;
                } else {
                    this.pos++;
                    end = this.pos;
                }
            }
            return end;
        }

        private int basicStringEnd(final int open) {
            int i = open + 1;
            while (i < this.s.length()) {
                final char c = this.s.charAt(i);
                if (c == '\\') {
                    i += 2;
                } else if (c == '"') {
                    return i + 1;
                } else if (c == '\n') {
                    break;
                } else {
                    i++;
                }
            }
            throw this.error("the end of a string");
        }

        private int multilineEnd(final String quotes, final boolean escapes) {
            int i = this.pos + 3;
            while (i < this.s.length()) {
                if (escapes && this.s.charAt(i) == '\\') {
                    i += 2;
                } else if (this.s.startsWith(quotes, i)) {
                    // Up to two quotes may sit right before the closing three.
                    int close = i + 3;
                    while (close < this.s.length() && close < i + 5 && this.s.charAt(close) == quotes.charAt(0)) {
                        close++;
                    }
                    return close;
                } else {
                    i++;
                }
            }
            throw this.error("the end of a multi-line string");
        }

        private void skipBlanks() {
            while (this.pos < this.s.length() && (this.s.charAt(this.pos) == ' ' || this.s.charAt(this.pos) == '\t')) {
                this.pos++;
            }
        }

        private void skipToLineEnd() {
            while (this.pos < this.s.length() && this.s.charAt(this.pos) != '\n' && this.s.charAt(this.pos) != '\r') {
                this.pos++;
            }
        }

        private void skipNewline() {
            if (this.s.startsWith("\r\n", this.pos)) {
                this.pos += 2;
            } else if (this.pos < this.s.length() && (this.s.charAt(this.pos) == '\n' || this.s.charAt(this.pos) == '\r')) {
                this.pos++;
            }
        }

        private void expect(final String token) {
            if (!this.s.startsWith(token, this.pos)) {
                throw this.error("'" + token + "'");
            }
            this.pos += token.length();
        }

        private IllegalArgumentException error(final String expected) {
            int line = 1;
            for (int i = 0; i < Math.min(this.pos, this.s.length()); i++) {
                if (this.s.charAt(i) == '\n') {
                    line++;
                }
            }
            return new IllegalArgumentException("line " + line + ": expected " + expected);
        }
    }

    private static boolean isBare(final char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-';
    }

    private static String unescapeBasic(final String text) {
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
                case 'b' -> out.append('\b');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'f' -> out.append('\f');
                case 'r' -> out.append('\r');
                case 'u' -> {
                    out.appendCodePoint(Integer.parseInt(text.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'U' -> {
                    out.appendCodePoint(Integer.parseInt(text.substring(i + 1, i + 9), 16));
                    i += 8;
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "TomlText[%d keys, %d tables]", this.keys.size(), this.headers.size());
    }
}
