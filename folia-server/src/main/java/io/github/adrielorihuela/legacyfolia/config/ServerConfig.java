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
 * The one file an administrator is meant to edit: {@code config/server.config}.
 *
 * <p>Every setting here is grouped by what it does rather than by which of the server's ancestors
 * introduced it, so that turning the Nether off is not split across three files with three
 * different syntaxes. The settings this fork adds live here and nowhere else; the settings
 * inherited from Bukkit, Spigot and Paper keep their own files, and this one overrides them at
 * start-up. Nothing is removed from those files and nothing stops reading them, so a plugin
 * calling {@code SpigotConfig} or {@code Bukkit.spigot().getConfig()} sees what it always saw.</p>
 *
 * <p>The file is created from a template that carries a comment on every key. On upgrade, keys the
 * template has gained are merged in with their comments while keys the administrator has set are
 * left alone, so a new release explains its new options without discarding anybody's edits.</p>
 */
public final class ServerConfig {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Where the file lives, next to the Paper configuration it overrides. */
    public static final Path DIRECTORY = Path.of("config");

    public static final String FILE_NAME = "server.config";

    private static final String TEMPLATE_RESOURCE = "legacyfolia/server.config";

    private static volatile ServerConfig instance;

    private final CommentedFileConfig config;

    private ServerConfig(final CommentedFileConfig config) {
        this.config = config;
    }

    /**
     * The loaded configuration.
     *
     * @return the configuration
     * @throws IllegalStateException if called before {@link #load()}
     */
    public static ServerConfig get() {
        final ServerConfig loaded = instance;
        if (loaded == null) {
            throw new IllegalStateException("config/" + FILE_NAME + " has not been loaded yet");
        }
        return loaded;
    }

    /** Whether the configuration has been loaded, for code that runs on both sides of start-up. */
    public static boolean isLoaded() {
        return instance != null;
    }

    /**
     * Reads {@code config/server.config}, creating it from the template when it is not there.
     *
     * <p>A file that cannot be parsed stops the server rather than being replaced: it is somebody's
     * configuration, and quietly starting with defaults would apply a setup nobody chose.</p>
     *
     * @return the loaded configuration
     * @throws IOException if the file could not be read or created
     */
    public static ServerConfig load() throws IOException {
        Files.createDirectories(DIRECTORY);
        final Path path = DIRECTORY.resolve(FILE_NAME);

        final boolean created = !Files.exists(path);
        if (created) {
            try (InputStream in = openTemplate()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // The format is named rather than inferred: night-config picks a parser from the file
        // extension, and ".config" means nothing to it.
        final CommentedFileConfig config = CommentedFileConfig.builder(path, TomlFormat.instance())
            .preserveInsertionOrder()
            .sync()
            .build();
        try {
            config.load();
        } catch (final ParsingException e) {
            throw new IOException("config/" + FILE_NAME + " is not valid TOML: " + e.getMessage(), e);
        }

        final List<String> added = merge(config, readTemplate());
        if (!added.isEmpty()) {
            config.save();
            LOGGER.info("Added {} new setting(s) to config/{}: {}",
                added.size(), FILE_NAME, String.join(", ", added));
        }

        if (created) {
            LOGGER.info("Created config/{}. Every setting in it is commented; start there.", FILE_NAME);
        }

        final ServerConfig loaded = new ServerConfig(config);
        instance = loaded;
        return loaded;
    }

    private static CommentedConfig readTemplate() throws IOException {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        try (InputStream in = openTemplate()) {
            new TomlParser().parse(in, parsed, ParsingMode.REPLACE);
        }
        return parsed;
    }

    private static InputStream openTemplate() throws IOException {
        final InputStream in = ServerConfig.class.getClassLoader()
            .getResourceAsStream(TEMPLATE_RESOURCE);
        if (in == null) {
            throw new IOException("the built-in " + TEMPLATE_RESOURCE + " is missing from the jar");
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
            FILE_NAME, name, path, fallback.name().toLowerCase(Locale.ROOT));
        return fallback;
    }

    /** The underlying file, for the few callers that need to walk it. */
    public Config raw() {
        return this.config;
    }
}
