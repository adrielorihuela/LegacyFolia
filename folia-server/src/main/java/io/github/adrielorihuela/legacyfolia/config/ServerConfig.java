package io.github.adrielorihuela.legacyfolia.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The configuration an administrator is meant to edit, spread over a handful of files in
 * {@code config/} and grouped by what the settings do.
 *
 * <p>Which file a setting lives in follows from what it is for, never from which of the server's
 * ancestors introduced it. Turning the Nether off belongs with the world, not in the Paper file;
 * the proxy secret belongs with the network, not in Spigot's. That is the whole point: an operator
 * looking for a setting should be able to guess where it is.</p>
 *
 * <p>Callers never name a file. They ask for a dotted path — {@code dimensions.nether},
 * {@code combat.enabled} — and the first segment decides which file answers. Moving a section
 * between files is then a one-line change here and nothing at all anywhere else.</p>
 */
public final class ServerConfig {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    public static final String SERVER = "server.toml";
    public static final String WORLD = "world.toml";
    public static final String NETWORK = "network.toml";
    public static final String PERFORMANCE = "performance.toml";
    public static final String COMBAT = "combat.toml";
    public static final String PLUGINS = "plugins.toml";

    /**
     * Which file owns each top-level section.
     *
     * <p>Every section a caller can ask for has to appear here. A path whose first segment is
     * missing is a programming error rather than a configuration one, and is treated as such.</p>
     */
    private static final Map<String, String> SECTIONS = Map.of(
        "server", SERVER,
        "dimensions", WORLD,
        "world", WORLD,
        "network", NETWORK,
        "chunks", PERFORMANCE,
        "performance", PERFORMANCE,
        "combat", COMBAT,
        "plugins", PLUGINS);

    /** Loaded in this order, which is the order they are reported in. */
    private static final List<String> FILES =
        List.of(SERVER, WORLD, NETWORK, PERFORMANCE, COMBAT, PLUGINS);

    private static volatile Map<String, ConfigFile> files;

    private ServerConfig() {
    }

    /** Whether the configuration has been read, for code that runs on both sides of start-up. */
    public static boolean isLoaded() {
        return files != null;
    }

    /**
     * Reads every configuration file, creating the ones that are not there.
     *
     * @throws IOException if a file could not be read or created
     */
    public static void load() throws IOException {
        final Map<String, ConfigFile> loaded = new LinkedHashMap<>();
        for (final String fileName : FILES) {
            loaded.put(fileName, ConfigFile.open(fileName));
        }
        files = loaded;

        final long created = loaded.values().stream().filter(ConfigFile::wasCreated).count();
        if (created == FILES.size()) {
            LOGGER.info("Created the configuration in config/. Every setting in it is commented; "
                + "start there.");
        } else if (created > 0) {
            LOGGER.info("Created {} new configuration file(s) in config/.", created);
        }
    }

    /**
     * The file that owns a dotted path.
     *
     * @param path the dotted path
     * @return the file it lives in
     */
    private static ConfigFile fileFor(final String path) {
        final Map<String, ConfigFile> loaded = files;
        if (loaded == null) {
            throw new IllegalStateException("the configuration has not been read yet");
        }
        final int dot = path.indexOf('.');
        final String section = dot == -1 ? path : path.substring(0, dot);
        final String fileName = SECTIONS.get(section);
        if (fileName == null) {
            throw new IllegalArgumentException("no configuration file owns the section '" + section
                + "'. Add it to ServerConfig.SECTIONS.");
        }
        return loaded.get(fileName);
    }

    /**
     * Whether the file owning a path was created on this run.
     *
     * <p>A setting that used to live elsewhere uses this to migrate: on the run that creates its
     * file it adopts whatever the old setting said, rather than imposing the template's default on
     * a server that was already configured.</p>
     *
     * @param path the dotted path
     * @return whether that file did not exist when the server started
     */
    public static boolean wasCreated(final String path) {
        return fileFor(path).wasCreated();
    }

    /**
     * A boolean setting.
     *
     * @param path the dotted path, such as {@code dimensions.nether}
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public static boolean getBoolean(final String path, final boolean fallback) {
        return fileFor(path).getBoolean(path, fallback);
    }

    /**
     * An integer setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public static int getInt(final String path, final int fallback) {
        return fileFor(path).getInt(path, fallback);
    }

    /**
     * A floating point setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public static double getDouble(final String path, final double fallback) {
        return fileFor(path).getDouble(path, fallback);
    }

    /**
     * A string setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value, trimmed
     */
    public static String getString(final String path, final String fallback) {
        return fileFor(path).getString(path, fallback);
    }

    /**
     * A list-of-strings setting, with blank entries dropped.
     *
     * @param path the dotted path
     * @return the configured values, never {@code null}
     */
    public static List<String> getStringList(final String path) {
        return fileFor(path).getStringList(path);
    }

    /**
     * Sets a value and writes its file back out, comments and all.
     *
     * @param path the dotted path
     * @param value the value to store
     */
    public static void set(final String path, final Object value) {
        final ConfigFile file = fileFor(path);
        file.set(path, value);
        file.save();
    }
}
