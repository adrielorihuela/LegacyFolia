package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One TOML file under {@code config/}, and everything that keeping it readable involves.
 *
 * <p>Each file is created from a template that carries a comment on every key. On upgrade, keys the
 * template has gained are merged in with their comments while keys the administrator has set are
 * left alone, so a new release explains its new options without discarding anybody's edits.</p>
 *
 * <p>{@link ServerConfig} is what the rest of the server talks to; this is the piece it is built
 * out of. Which file a setting lives in is decided there, not here.</p>
 */
public final class ConfigFile {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Where the configuration lives. */
    public static final Path DIRECTORY = Path.of("config");

    private final String fileName;

    private final CommentedFileConfig config;

    /** Whether this run is the one that created the file, for settings that migrate on first use. */
    private final boolean created;

    ConfigFile(final String fileName, final CommentedFileConfig config, final boolean created) {
        this.fileName = fileName;
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
        Files.createDirectories(DIRECTORY);
        final Path path = DIRECTORY.resolve(fileName);

        final boolean created = !Files.exists(path);
        if (created) {
            try (InputStream in = openTemplate(fileName)) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // The format is named rather than inferred: night-config picks a parser from the file
        // extension, and it should not matter what these are called.
        final CommentedFileConfig config = CommentedFileConfig.builder(path, TomlFormat.instance())
            .preserveInsertionOrder()
            .sync()
            .build();
        try {
            config.load();
        } catch (final ParsingException e) {
            throw new IOException("config/" + fileName + " is not valid TOML: " + e.getMessage(), e);
        }

        final List<String> added = merge(config, readTemplate(fileName));
        if (!added.isEmpty()) {
            config.save();
            LOGGER.info("Added {} new setting(s) to config/{}: {}",
                added.size(), fileName, String.join(", ", added));
        }

        return new ConfigFile(fileName, config, created);
    }

    /**
     * Sets a value, leaving its comment in place.
     *
     * @param path the dotted path
     * @param value the value to store
     */
    public void set(final String path, final Object value) {
        this.config.set(path, value);
    }

    /** Writes the file back out, comments and all. */
    public void save() {
        this.config.save();
    }

    private static CommentedConfig readTemplate(final String fileName) throws IOException {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        try (InputStream in = openTemplate(fileName)) {
            new TomlParser().parse(in, parsed, ParsingMode.REPLACE);
        }
        return parsed;
    }

    private static InputStream openTemplate(final String fileName) throws IOException {
        final String resource = "legacyfolia/" + fileName;
        final InputStream in = ConfigFile.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("the built-in " + resource + " is missing from the jar");
        }
        return in;
    }

    /**
     * Copies keys the file does not have from the template, along with their comments.
     *
     * <p>Only missing keys are written. A value somebody set is never replaced by the default, and
     * a key the template no longer has is never deleted — an unknown key is far more likely to come
     * from a newer build than to be worth throwing away.</p>
     *
     * @param target the loaded configuration
     * @param source the template
     * @return the dotted paths that were added
     */
    static List<String> merge(final CommentedConfig target, final CommentedConfig source) {
        final List<String> added = new ArrayList<>();
        merge(target, source, "", added);
        return added;
    }

    private static void merge(final CommentedConfig target, final CommentedConfig source,
                              final String prefix, final List<String> added) {
        for (final CommentedConfig.Entry entry : source.entrySet()) {
            final String key = entry.getKey();
            final List<String> at = List.of(key);
            final String path = prefix.isEmpty() ? key : prefix + "." + key;
            final Object value = entry.getValue();

            if (value instanceof final CommentedConfig section) {
                if (!(target.get(at) instanceof final CommentedConfig existing)) {
                    target.set(at, section);
                    target.setComment(at, entry.getComment());
                    added.add(path);
                    continue;
                }
                if (target.getComment(at) == null) {
                    target.setComment(at, entry.getComment());
                }
                merge(existing, section, path, added);
                continue;
            }

            if (!target.contains(at)) {
                target.set(at, value);
                target.setComment(at, entry.getComment());
                added.add(path);
            }
        }
    }

    /**
     * A boolean setting.
     *
     * @param path the dotted path, such as {@code dimensions.nether}
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public boolean getBoolean(final String path, final boolean fallback) {
        return this.config.get(path) instanceof final Boolean bool ? bool : fallback;
    }

    /**
     * An integer setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public int getInt(final String path, final int fallback) {
        return this.config.get(path) instanceof final Number number ? number.intValue() : fallback;
    }

    /**
     * A floating point setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public double getDouble(final String path, final double fallback) {
        return this.config.get(path) instanceof final Number number ? number.doubleValue() : fallback;
    }

    /**
     * A string setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value, trimmed
     */
    public String getString(final String path, final String fallback) {
        return this.config.get(path) instanceof final String string ? string.trim() : fallback;
    }

    /**
     * A list-of-strings setting, with blank entries dropped.
     *
     * @param path the dotted path
     * @return the configured values, never {@code null}
     */
    public List<String> getStringList(final String path) {
        if (!(this.config.get(path) instanceof final List<?> list)) {
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
