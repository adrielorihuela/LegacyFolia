package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
 *
 * <p>Most of what the server settles is decided once for the whole server, and the methods here
 * answer for that. More than half of the settings inherited from Spigot and Paper, however, are
 * decided per world, and for those a caller asks {@link #forWorld(String)} for that world's view
 * instead. The view resolves {@code [worlds.<name>]} over {@code [worlds.default]} over the global
 * section, which is the same chain Spigot and Paper each implement separately today; see
 * {@link WorldConfig} for why it is flattened rather than walked. Views are cached per world and
 * dropped whole whenever the files are read again.</p>
 *
 * <p><b>server.properties.</b> With the server.properties mode on, what that file says wins over
 * the TOML files. {@link ServerProperties} works out the effective values when it reconciles the
 * two and hands them to this class as overrides; every getter here, and every world view, answers
 * from those overrides first. Callers do not change and do not need to know the mode exists.</p>
 */
public final class ServerConfig {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    public static final String SERVER = "server.toml";
    public static final String WORLD = "world.toml";
    public static final String NETWORK = "network.toml";
    public static final String PERFORMANCE = "performance.toml";
    public static final String COMBAT = "combat.toml";
    public static final String PLUGINS = "plugins.toml";
    public static final String WORLDS = "worlds.toml";
    public static final String GAMEPLAY = "gameplay.toml";

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
        "plugins", PLUGINS,
        "worlds", WORLDS,
        "gameplay", GAMEPLAY);

    /**
     * The sections a world may override.
     *
     * <p>These are the files whose contents are flattened to make a world's view, so a path a
     * caller passes to a {@link WorldConfig} has to start in one of them. Everything else —
     * a port, a proxy secret, the message shown to a kicked player — is the server's, not a
     * world's, and asking for it per world would be a category error rather than a lookup that
     * happens to miss.</p>
     */
    private static final List<String> PER_WORLD_FILES = List.of(GAMEPLAY, PERFORMANCE, WORLD);

    /** Loaded in this order, which is the order they are reported in. */
    private static final List<String> FILES =
        List.of(SERVER, WORLD, NETWORK, PERFORMANCE, COMBAT, PLUGINS, WORLDS, GAMEPLAY);

    private static volatile Map<String, ConfigFile> files;

    private static volatile Path directory = ConfigFile.DIRECTORY;

    /**
     * The effective values {@link ServerProperties} worked out, or {@code null} before it has run.
     *
     * <p>Replaced whole, never modified, so a reader on any region thread sees one consistent set.</p>
     */
    private static volatile Overrides overrides;

    /**
     * Values that answer before the files do.
     *
     * @param global every catalogued setting's effective server-wide value, by TOML path
     * @param perWorldGlobal the subset of {@code global} that lives in a per-world file, which is
     *     what a world view layers over the files
     * @param worlds the whole {@code [worlds]} layer, by world name and then by dotted path; it
     *     replaces what {@code worlds.toml} says rather than adding to it
     */
    record Overrides(Map<String, Object> global, Map<String, Object> perWorldGlobal,
                     Map<String, Map<String, Object>> worlds) {

        static Overrides of(final Map<String, Object> global, final Map<String, Map<String, Object>> worlds) {
            final Map<String, Object> perWorld = new HashMap<>();
            for (final Map.Entry<String, Object> entry : global.entrySet()) {
                if (isPerWorldFile(fileNameFor(entry.getKey()))) {
                    perWorld.put(entry.getKey(), entry.getValue());
                }
            }
            final Map<String, Map<String, Object>> copy = new HashMap<>();
            worlds.forEach((world, values) -> {
                if (!values.isEmpty()) {
                    copy.put(world, Map.copyOf(values));
                }
            });
            return new Overrides(Map.copyOf(global), Map.copyOf(perWorld), Map.copyOf(copy));
        }
    }

    private static final WorldConfigCache WORLD_VIEWS = new WorldConfigCache(ServerConfig::resolve);

    private ServerConfig() {
    }

    /**
     * The eight files, in the order they are loaded.
     *
     * @return their names within {@code config/}
     */
    public static List<String> fileNames() {
        return FILES;
    }

    /**
     * The file that owns a dotted path, without needing the configuration to be loaded.
     *
     * @param path the dotted path, such as {@code chunks.view-distance}
     * @return the file's name, such as {@code performance.toml}
     * @throws IllegalArgumentException if no file owns the path's first segment
     */
    public static String fileNameFor(final String path) {
        final int dot = path.indexOf('.');
        final String section = dot == -1 ? path : path.substring(0, dot);
        final String fileName = SECTIONS.get(section);
        if (fileName == null) {
            throw new IllegalArgumentException("no configuration file owns the section '" + section
                + "'. Add it to ServerConfig.SECTIONS.");
        }
        return fileName;
    }

    /**
     * Whether a file's settings may be overridden per world in {@code worlds.toml}.
     *
     * @param fileName the file's name
     * @return whether {@link #forWorld(String)} consults it
     */
    public static boolean isPerWorldFile(final String fileName) {
        return PER_WORLD_FILES.contains(fileName);
    }

    /** Whether the configuration has been read, for code that runs on both sides of start-up. */
    public static boolean isLoaded() {
        return files != null;
    }

    /**
     * Forgets the configuration, as if it had never been read. Only tests have a use for this: it
     * keeps one test's temporary configuration from leaking into the next.
     */
    static void unload() {
        files = null;
        overrides = null;
        directory = ConfigFile.DIRECTORY;
        WORLD_VIEWS.invalidate();
    }

    /** The directory the files were read from: {@code config/} on a real server. */
    public static Path directory() {
        return directory;
    }

    /**
     * One loaded file, for {@link ServerProperties}, which compares and rewrites them.
     *
     * @param fileName the file's name
     * @return the file as it was last read
     */
    static ConfigFile file(final String fileName) {
        final Map<String, ConfigFile> loaded = files;
        if (loaded == null) {
            throw new IllegalStateException("the configuration has not been read yet");
        }
        final ConfigFile file = loaded.get(fileName);
        if (file == null) {
            throw new IllegalArgumentException("no configuration file is called " + fileName);
        }
        return file;
    }

    /**
     * Installs the effective values, dropping every cached world view.
     *
     * @param value the overrides, or {@code null} to answer from the files alone
     */
    static void setOverrides(final Overrides value) {
        overrides = value;
        WORLD_VIEWS.invalidate();
    }

    /** The effective values in force, or {@code null}. */
    static Overrides overrides() {
        return overrides;
    }

    /** The overriding value of a path, or {@code null} when the file answers. */
    private static Object overridden(final String path) {
        final Overrides current = overrides;
        return current == null ? null : current.global().get(path);
    }

    /**
     * Reads every configuration file, creating the ones that are not there.
     *
     * @throws IOException if a file could not be read or created
     */
    public static void load() throws IOException {
        load(ConfigFile.DIRECTORY);
    }

    /**
     * Reads every configuration file from a given directory, creating the ones that are not there.
     *
     * <p>Any server.properties overrides are dropped: they were worked out from the files as they
     * were, and {@link ServerProperties#reconcile()} has to run again to work them out anew.</p>
     *
     * @param configDirectory the directory, {@code config/} on a real server
     * @throws IOException if a file could not be read or created
     */
    public static void load(final Path configDirectory) throws IOException {
        final Map<String, ConfigFile> loaded = new LinkedHashMap<>();
        for (final String fileName : FILES) {
            loaded.put(fileName, ConfigFile.open(configDirectory, fileName));
        }
        files = loaded;
        directory = configDirectory;
        overrides = null;
        WORLD_VIEWS.invalidate();

        final long created = loaded.values().stream().filter(ConfigFile::wasCreated).count();
        if (created == FILES.size()) {
            LOGGER.info("Created the configuration in config/. Every setting in it is commented; "
                + "start there.");
        } else if (created > 0) {
            LOGGER.info("Created {} new configuration file(s) in config/.", created);
        }
    }

    /**
     * The configuration as one world sees it.
     *
     * <p>Cheap to call and cheap to hold on to: the view for a world is built once and reused, so
     * a tick loop may ask for it every tick, and a caller that keeps one only has to remember that
     * it is a snapshot of the last load.</p>
     *
     * @param worldName the world's name, as the server knows it
     * @return that world's view, built if this is the first time it has been asked for
     */
    public static WorldConfig forWorld(final String worldName) {
        return WORLD_VIEWS.get(worldName);
    }

    /** Builds one world's view out of the files that were loaded. */
    private static WorldConfig resolve(final String worldName) {
        final Map<String, ConfigFile> loaded = files;
        if (loaded == null) {
            throw new IllegalStateException("the configuration has not been read yet");
        }
        final List<UnmodifiableConfig> globals = new ArrayList<>(PER_WORLD_FILES.size());
        for (final String fileName : PER_WORLD_FILES) {
            globals.add(loaded.get(fileName).raw());
        }
        final Overrides current = overrides;
        if (current != null) {
            return WorldConfig.resolve(worldName, globals, current.perWorldGlobal(), current.worlds());
        }
        final Object worlds = loaded.get(WORLDS).raw().get(List.of("worlds"));
        return WorldConfig.resolve(worldName, globals,
            worlds instanceof final UnmodifiableConfig section ? section : null);
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
        return loaded.get(fileNameFor(path));
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
        final Object value = overridden(path);
        return value != null ? ConfigFile.asBoolean(value, fallback) : fileFor(path).getBoolean(path, fallback);
    }

    /**
     * An integer setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public static int getInt(final String path, final int fallback) {
        final Object value = overridden(path);
        return value != null ? ConfigFile.asInt(value, fallback) : fileFor(path).getInt(path, fallback);
    }

    /**
     * A floating point setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value
     */
    public static double getDouble(final String path, final double fallback) {
        final Object value = overridden(path);
        return value != null ? ConfigFile.asDouble(value, fallback) : fileFor(path).getDouble(path, fallback);
    }

    /**
     * A string setting.
     *
     * @param path the dotted path
     * @param fallback what to use when the key is absent
     * @return the configured value, trimmed
     */
    public static String getString(final String path, final String fallback) {
        final Object value = overridden(path);
        return value != null ? ConfigFile.asString(value, fallback) : fileFor(path).getString(path, fallback);
    }

    /**
     * A list-of-strings setting, with blank entries dropped.
     *
     * @param path the dotted path
     * @return the configured values, never {@code null}
     */
    public static List<String> getStringList(final String path) {
        final Object value = overridden(path);
        return value != null ? ConfigFile.asStringList(value) : fileFor(path).getStringList(path);
    }

    /**
     * Sets a value and writes it to its file, changing only the text of that value: every comment
     * and every other line stays as it was ({@link ConfigFile#set}).
     *
     * @param path the dotted path
     * @param value the value to store
     */
    public static void set(final String path, final Object value) {
        final ConfigFile file = fileFor(path);
        file.set(path, value);
        final Overrides current = overrides;
        if (current != null && current.global().containsKey(path)) {
            final Map<String, Object> global = new HashMap<>(current.global());
            global.put(path, value);
            setOverrides(Overrides.of(global, current.worlds()));
        }
    }
}
