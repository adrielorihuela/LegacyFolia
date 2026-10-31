package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingKind;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.properties.AtomicFiles;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlLiterals;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlText;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One TOML file under {@code config/}, and everything that keeping it readable involves.
 *
 * <p>Each file is created from a template that carries a comment on every key. On upgrade, keys the
 * template has gained are merged in with their comments while keys the administrator has set are
 * left alone, so a new release explains its new options without discarding anybody's edits.</p>
 *
 * <p>The file is only ever written as text: new keys are inserted and changed values replaced in
 * place, so the comments, the commented-out examples, the blank lines and the way each value is
 * written all stay exactly as they were. Nothing here saves the parsed values back.</p>
 *
 * <p>{@link ServerConfig} is what the rest of the server talks to; this is the piece it is built
 * out of. Which file a setting lives in is decided there, not here.</p>
 */
public final class ConfigFile {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Where the configuration lives. */
    public static final Path DIRECTORY = Path.of("config");

    private final String fileName;

    private final Path path;

    /** The parsed values, replaced whole whenever the file is written, so a reader never sees half a change. */
    private volatile CommentedConfig config;

    /** Whether this run is the one that created the file, for settings that migrate on first use. */
    private final boolean created;

    ConfigFile(final String fileName, final Path path, final CommentedConfig config, final boolean created) {
        this.fileName = fileName;
        this.path = path;
        this.config = config;
        this.created = created;
    }

    /** The file's name within {@link #DIRECTORY}, for messages. */
    public String fileName() {
        return this.fileName;
    }

    /**
     * Whether this run created the file.
     *
     * <p>Settings that used to live elsewhere use this to migrate: on the run that creates the file
     * they copy what the old setting was set to into it, rather than imposing the template's
     * default on a server that had already been configured.</p>
     *
     * @return whether the file did not exist when the server started
     */
    public boolean wasCreated() {
        return this.created;
    }

    /**
     * Reads one file from {@code config/}, creating it from its template when it is not there.
     *
     * <p>A file that cannot be parsed stops the server rather than being replaced: it is somebody's
     * configuration, and quietly starting with defaults would apply a setup nobody chose.</p>
     *
     * @param fileName the file's name within {@link #DIRECTORY}
     * @return the loaded file
     * @throws IOException if it could not be read or created
     */
    public static ConfigFile open(final String fileName) throws IOException {
        return open(DIRECTORY, fileName);
    }

    /**
     * Reads one file from a given configuration directory, creating it from its template when it
     * is not there.
     *
     * <p>The same as {@link #open(String)} for a directory other than the working directory's
     * {@code config/}. The server always uses {@link #DIRECTORY}; the importer of older servers
     * works on a server root it is handed, and the tests pass a temporary directory so that
     * nothing they do can reach a real configuration.</p>
     *
     * @param directory the configuration directory
     * @param fileName the file's name within it
     * @return the loaded file
     * @throws IOException if it could not be read or created
     */
    public static ConfigFile open(final Path directory, final String fileName) throws IOException {
        Files.createDirectories(directory);
        final Path path = directory.resolve(fileName);

        final boolean created = !Files.exists(path);
        if (created) {
            try (InputStream in = openTemplate(fileName)) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        final String text = AtomicFiles.read(path);
        final CommentedConfig parsed = parse(text, fileName);
        final Merged merged;
        try {
            merged = merge(text, parsed, readTemplate(fileName));
        } catch (final IllegalArgumentException | IllegalStateException e) {
            throw new IOException("config/" + fileName + " could not be brought up to date with its template: "
                + e.getMessage(), e);
        }
        if (merged.added().isEmpty()) {
            return new ConfigFile(fileName, path, parsed, created);
        }
        AtomicFiles.write(path, merged.text());
        LOGGER.info("Added {} new setting(s) to config/{}: {}",
            merged.added().size(), fileName, String.join(", ", merged.added()));
        return new ConfigFile(fileName, path, parse(merged.text(), fileName), created);
    }

    /**
     * Sets a value and writes the file at once, changing only the text of that value.
     *
     * <p>The file is edited as text rather than saved from the parsed values: saving through
     * night-config drops every comment that is not directly above a key, such as the worked
     * examples at the end of {@code worlds.toml}, and re-indents and reflows what is left. The
     * edit is made to the file as it is on disk now, so a change something else wrote since it was
     * read, such as the server.properties mode, is kept.</p>
     *
     * @param path the dotted path
     * @param value the value to store: a boolean, a number, a string, a list or a map
     * @throws UncheckedIOException if the file could not be read or written; it is then untouched
     */
    public void set(final String path, final Object value) {
        this.edit(text -> text.set(List.of(), path, TomlLiterals.render(value), false));
    }

    /**
     * Applies an edit to the file's text and writes it, in one step.
     *
     * @param edit the change, made to the file as it is on disk now
     * @throws UncheckedIOException if the file could not be read or written; it is then untouched
     */
    public synchronized void edit(final UnaryOperator<TomlText> edit) {
        try {
            final TomlText before = this.text();
            final TomlText after = edit.apply(before);
            if (!after.text().equals(before.text())) {
                this.write(after);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("could not write config/" + this.fileName, e);
        }
    }

    /**
     * The file's text as it is on disk now, for an edit made in several steps and written with
     * {@link #write(TomlText)}.
     *
     * @return the parsed outline of the text
     * @throws IOException if it could not be read
     */
    public TomlText text() throws IOException {
        return TomlText.parse(AtomicFiles.read(this.path));
    }

    /**
     * Writes text produced by editing {@link #text()}, and reads the values from it.
     *
     * @param text the edited text
     * @throws IOException if it could not be written; the file is then untouched
     */
    public synchronized void write(final TomlText text) throws IOException {
        final CommentedConfig parsed = parse(text.text(), this.fileName);
        AtomicFiles.write(this.path, text.text());
        this.config = parsed;
    }

    private static CommentedConfig parse(final String text, final String fileName) throws IOException {
        try {
            return parse(text);
        } catch (final ParsingException e) {
            throw new IOException("config/" + fileName + " is not valid TOML: " + e.getMessage(), e);
        }
    }

    /** Parses TOML text keeping its keys in the order the text has them. */
    static CommentedConfig parse(final String text) {
        final CommentedConfig parsed = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        new TomlParser().parse(new StringReader(text), parsed, ParsingMode.REPLACE);
        return parsed;
    }

    private static String readTemplate(final String fileName) throws IOException {
        try (InputStream in = openTemplate(fileName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream openTemplate(final String fileName) throws IOException {
        final String resource = "legacyfolia/" + fileName;
        final InputStream in = ConfigFile.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("the built-in " + resource + " is missing from the jar");
        }
        return in;
    }

    /** The outcome of a merge: the new text, and the dotted paths that were added to it. */
    record Merged(String text, List<String> added) {
    }

    /**
     * Copies the keys a file does not have from its template, each with the comment that explains
     * it, by inserting their text where the template has them.
     *
     * <p>Only missing keys are written. A value somebody set is never replaced by the default, and
     * a key the template no longer has is never deleted — an unknown key is far more likely to come
     * from a newer build than to be worth throwing away. Every byte that was in the file before is
     * still there afterwards, in the same order: new keys and tables are inserted between them,
     * never written over them. The one exception is a value that is not a table standing where the
     * template has a table, which is replaced by the table.</p>
     *
     * @param text the file's text
     * @param template the template's text
     * @return the merged text and the dotted paths added; a wholly missing table is one path
     */
    static Merged merge(final String text, final String template) {
        return merge(text, parse(text), template);
    }

    private static Merged merge(final String text, final CommentedConfig parsed, final String template) {
        final List<String> added = new ArrayList<>();
        final Document document = new Document(TomlText.parse(text), TomlText.parse(template));
        merge(document, parsed, parse(template), List.of(), added);
        return new Merged(document.text.text(), List.copyOf(added));
    }

    /** The file being merged, and the template it is merged from. */
    private static final class Document {

        private TomlText text;

        private final TomlText template;

        Document(final TomlText text, final TomlText template) {
            this.text = text;
            this.template = template;
        }
    }

    private static void merge(final Document document, final UnmodifiableConfig target, final UnmodifiableConfig source,
                              final List<String> prefix, final List<String> added) {
        for (final UnmodifiableConfig.Entry entry : source.entrySet()) {
            final List<String> path = append(prefix, entry.getKey());
            final String dotted = String.join(".", path);
            final Object value = entry.getValue();
            final Object existing = target.get(List.of(entry.getKey()));

            // A table whose keys are data, such as the command aliases, is one setting: the
            // operator's table replaces the template's rather than being topped up from it, or a
            // deleted default alias would come back at every start.
            if (value instanceof final UnmodifiableConfig section && !isDataTable(dotted)) {
                if (existing instanceof final UnmodifiableConfig mine) {
                    merge(document, mine, section, path, added);
                    continue;
                }
                if (existing != null) {
                    // Something that is not a table stands where a table belongs: it goes, and the
                    // table is rebuilt from the template.
                    document.text = document.text.remove(prefix, String.join(".", entry.getKey()));
                }
                add(document, section, path);
                added.add(dotted);
                continue;
            }

            if (existing == null) {
                add(document, value, path);
                added.add(dotted);
            }
        }
    }

    /** Adds one missing key or table, with its comment, from the template. */
    private static void add(final Document document, final Object value, final List<String> path) {
        final TomlText edited = document.text.addFromTemplate(document.template, path);
        if (edited != null) {
            document.text = edited;
            return;
        }
        if (value instanceof final UnmodifiableConfig section && !isDataTable(String.join(".", path))) {
            // A table the template only writes through its sub-tables, such as [performance]
            // above [performance.entities]: each of those is added in turn.
            for (final UnmodifiableConfig.Entry entry : section.entrySet()) {
                add(document, entry.getValue(), append(path, entry.getKey()));
            }
            return;
        }
        // The key's table is written in this file in a form the template's text cannot be slotted
        // into, such as dotted keys under a parent table: the value goes in on its own, without
        // its comment.
        document.text = document.text.set(List.of(), String.join(".", path), TomlLiterals.render(value), false);
    }

    private static List<String> append(final List<String> prefix, final String key) {
        final List<String> path = new ArrayList<>(prefix.size() + 1);
        path.addAll(prefix);
        path.add(key);
        return List.copyOf(path);
    }

    private static boolean isDataTable(final String path) {
        return SettingsCatalog.get().byPath(path).map(entry -> entry.kind() == SettingKind.TABLE).orElse(false);
    }

    /**
     * A boolean setting.
     *
     * @param path the dotted path, such as {@code dimensions.nether}
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public boolean getBoolean(final String path, final boolean fallback) {
        return asBoolean(this.config.get(path), fallback);
    }

    /**
     * An integer setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public int getInt(final String path, final int fallback) {
        return asInt(this.config.get(path), fallback);
    }

    /**
     * A floating point setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public double getDouble(final String path, final double fallback) {
        return asDouble(this.config.get(path), fallback);
    }

    /**
     * A string setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value, trimmed
     */
    public String getString(final String path, final String fallback) {
        return asString(this.config.get(path), fallback);
    }

    /**
     * A list-of-strings setting, with blank entries dropped.
     *
     * @param path the dotted path
     * @return the configured values, never {@code null}
     */
    public List<String> getStringList(final String path) {
        return asStringList(this.config.get(path));
    }

    // The typed readers below are shared with ServerConfig, which answers some paths from the
    // server.properties overrides rather than from a file, and must read them exactly the same way.

    static boolean asBoolean(final Object value, final boolean fallback) {
        return value instanceof final Boolean bool ? bool : fallback;
    }

    static int asInt(final Object value, final int fallback) {
        return value instanceof final Number number ? number.intValue() : fallback;
    }

    static double asDouble(final Object value, final double fallback) {
        return value instanceof final Number number ? number.doubleValue() : fallback;
    }

    static String asString(final Object value, final String fallback) {
        return value instanceof final String string ? string.trim() : fallback;
    }

    static List<String> asStringList(final Object value) {
        if (!(value instanceof final List<?> list)) {
            return List.of();
        }
        final List<String> strings = new ArrayList<>(list.size());
        for (final Object element : list) {
            if (element instanceof final String string && !string.isBlank()) {
                strings.add(string.trim());
            }
        }
        return List.copyOf(strings);
    }

    /**
     * An enum-like setting matched case-insensitively, with dashes standing in for underscores.
     *
     * @param path the dotted path
     * @param type the enum class
     * @param fallback what to use when the key is absent or unrecognised
     * @param <E> the enum type
     * @return the configured constant
     */
    public <E extends Enum<E>> E getEnum(final String path, final Class<E> type, final E fallback) {
        final String name = getString(path, "");
        if (name.isEmpty()) {
            return fallback;
        }
        for (final E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name.replace('-', '_'))) {
                return constant;
            }
        }
        LOGGER.warn("config/{}: {} is not a recognised value for {}, using {}.",
            this.fileName, name, path, fallback.name().toLowerCase(Locale.ROOT));
        return fallback;
    }

    /** The underlying file, for the few callers that need to walk it. */
    public Config raw() {
        return this.config;
    }
}
