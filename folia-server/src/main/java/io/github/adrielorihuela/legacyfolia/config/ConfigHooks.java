package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.bridge.BukkitBridge;
import io.github.adrielorihuela.legacyfolia.config.bridge.EffectiveSettings;
import io.github.adrielorihuela.legacyfolia.config.bridge.PaperBridge;
import io.github.adrielorihuela.legacyfolia.config.bridge.ServerConfigSettings;
import io.github.adrielorihuela.legacyfolia.config.bridge.ServerPropertiesBridge;
import io.github.adrielorihuela.legacyfolia.config.bridge.SpigotBridge;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.migration.LegacyImport;
import io.github.adrielorihuela.legacyfolia.config.properties.PropertyValues;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Where the server calls into the configuration: start-up, reload, the objects Bukkit, Spigot,
 * Paper and vanilla read, the writes commands make, and shutdown.
 *
 * <p>Every patch that touches the configuration calls one method here and nothing else, so the
 * patches stay one line long and the order of things is decided in one readable place:</p>
 * <ol>
 *   <li>{@link #start(Path, Path)}: import an older server's files, read the TOML files, reconcile
 *       {@code server.properties}. Before anything else reads a setting.</li>
 *   <li>The bridge: {@link #serverProperties()}, {@link #bukkit()}, {@link #commands()},
 *       {@link #spigot(Function)}, {@link #paperGlobal()}, {@link #paperWorldDefaults()},
 *       {@link #paperWorld(String)}. Each builds its object from the settings in force, in memory;
 *       none of them reads or writes a YAML file.</li>
 *   <li>{@link #reload()}: the TOML files and {@code server.properties} again, always together.</li>
 *   <li>{@link #vanillaChanged(String, String)}: a vanilla setting changed by a command.</li>
 *   <li>{@link #stop()}: the last write reaches the disk.</li>
 * </ol>
 */
public final class ConfigHooks {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final ServerConfigSettings SOURCE = new ServerConfigSettings();

    private static final String UPDATE_FOLDER = "plugins.api.update-folder";

    private static volatile boolean firstStart;

    private static volatile boolean started;

    private ConfigHooks() {
    }

    // ------------------------------------------------------------------------------------------
    //  Start-up, reload and shutdown
    // ------------------------------------------------------------------------------------------

    /**
     * Imports an older server's configuration if there is one, reads the TOML files and reconciles
     * {@code server.properties}, in that order.
     *
     * @param serverRoot the directory the server runs in
     * @param worldContainer {@code --universe}, or {@code null}
     * @throws IOException if the import or the configuration failed; the server must not start
     */
    public static void start(final Path serverRoot, final @Nullable Path worldContainer) throws IOException {
        final boolean existed = Files.exists(ConfigFile.DIRECTORY.resolve(ServerConfig.SERVER));
        final boolean imported = LegacyImport.runIfNeeded(serverRoot, worldContainer).isPresent();
        firstStart = !existed && !imported;
        ServerConfig.load();
        log(ServerProperties.reconcile());
        started = true;
    }

    /**
     * Whether {@link #start} has run, that is whether this is a running server. Paper's
     * configuration is built from the TOML files only then; without it, as in the tests that use
     * Paper's own files as the reference, Paper reads and writes its files as it always has.
     *
     * @return whether the server started from the TOML files
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * Reads the TOML files and {@code server.properties} again. {@code ServerConfig.load()} drops
     * the server.properties overrides, so the two always go together.
     *
     * @throws IOException if a file could not be read or written
     */
    public static void reload() throws IOException {
        ServerConfig.load();
        log(ServerProperties.reconcile());
        io.github.adrielorihuela.legacyfolia.chunks.AdaptiveDistance.reload();
        io.github.adrielorihuela.legacyfolia.combat.Combat.reload();
    }

    /**
     * {@link #reload()} for callers that cannot throw, such as {@code /spigot reload}.
     */
    public static void reloadOrWarn() {
        try {
            reload();
        } catch (final IOException e) {
            LOGGER.error("Could not read the configuration again; keeping the settings in force: {}", e.getMessage(), e);
        }
    }

    /** Waits for every configuration write to reach the disk. */
    public static void stop() {
        try {
            ServerProperties.flush();
        } catch (final RuntimeException e) {
            LOGGER.error("A configuration change could not be saved before stopping: {}", e.getMessage());
        }
    }

    /** Whether this run created the configuration from nothing, with nothing imported. */
    public static boolean isFirstStart() {
        return firstStart;
    }

    /** Logs what a reconcile did, in a sentence or two rather than a record. */
    public static void log(final ServerProperties.Report report) {
        final ServerProperties.Status status = ServerProperties.status();
        if (report.enabled()) {
            LOGGER.info("server.properties mode is on: config/{} {} {} setting(s) that differ from the defaults{}.",
                ServerProperties.FILE_NAME, report.created() ? "was created and holds" : "holds", status.overrides(),
                status.byFile().isEmpty() ? "" : " " + status.byFile());
        } else {
            LOGGER.info("server.properties mode is off: the TOML files in config/ hold every setting ({} differ from the defaults).",
                status.overrides());
        }
        sentence("Moved from the TOML files into server.properties", report.moved());
        sentence("Set in both places, server.properties won", report.conflicts());
        sentence("Removed from server.properties because they said the default", report.removedDefaults());
        sentence("Not valid, the default is used", report.invalid());
        sentence("Not settings this server knows, kept as they are", report.unknown());
        sentence("Moved from server.properties into the TOML files", report.transferred());
        sentence("Kept in " + ServerProperties.UNUSED_FILE_NAME, report.unused());
    }

    private static void sentence(final String what, final List<String> keys) {
        if (!keys.isEmpty()) {
            LOGGER.info("  {} ({}): {}", what, keys.size(), String.join(", ", keys));
        }
    }

    // ------------------------------------------------------------------------------------------
    //  The bridge
    // ------------------------------------------------------------------------------------------

    /** The settings in force, with server.properties applied. */
    public static EffectiveSettings source() {
        return SOURCE;
    }

    /**
     * What {@code DedicatedServerProperties} is built from, in place of reading server.properties.
     * The RCON password comes from its secret file, generated there on first use.
     */
    public static Properties serverProperties() {
        return ServerPropertiesBridge.build(SOURCE, RconSecret.resolve(ServerPropertiesBridge.rconPasswordFile(SOURCE)));
    }

    /** {@code bukkit.yml}, in memory. */
    public static YamlConfiguration bukkit() {
        return BukkitBridge.bukkit(SOURCE);
    }

    /** {@code commands.yml}, in memory. */
    public static YamlConfiguration commands() {
        return BukkitBridge.commands(SOURCE);
    }

    /**
     * {@code spigot.yml}, in memory.
     *
     * @param worldKeys the dimension key of each world known so far, by its folder name
     * @return the configuration, for {@code SpigotConfig.init}
     */
    public static YamlConfiguration spigot(final Function<String, @Nullable String> worldKeys) {
        return SpigotBridge.build(SOURCE, worldKeys);
    }

    /**
     * {@code spigot.yml} for a server whose worlds are not loaded yet: the three of vanilla.
     *
     * @param levelName {@code level-name}
     * @return the configuration
     */
    public static YamlConfiguration spigot(final String levelName) {
        return SpigotBridge.build(SOURCE, SpigotBridge.vanillaWorldKeys(levelName));
    }

    /**
     * {@code spigot.yml} for a running server, with a section for every world that is loaded, as
     * {@code /spigot reload} and {@code Bukkit.reload()} need it.
     *
     * @return the configuration
     */
    public static YamlConfiguration spigotForLoadedWorlds() {
        final Map<String, String> keys = new java.util.HashMap<>();
        for (final net.minecraft.server.level.ServerLevel level : net.minecraft.server.MinecraftServer.getServer().getAllLevels()) {
            keys.put(level.getWorld().getName(), level.dimension().identifier().toString());
        }
        return SpigotBridge.build(SOURCE, keys::get);
    }

    /**
     * Adds a world's section to Spigot's configuration when it loads, unless it has one: Spigot
     * names worlds by dimension key, the TOML by folder, and only now are both known.
     *
     * @param config {@code SpigotConfig.config}
     * @param worldName the world's folder name
     * @param worldKey its dimension key
     */
    public static void addSpigotWorld(final YamlConfiguration config, final String worldName, final String worldKey) {
        if (config != null && !config.isConfigurationSection("world-settings." + worldKey)) {
            SpigotBridge.addWorld(config, SOURCE, worldName, worldKey);
        }
    }

    /** The node {@code GlobalConfiguration} is built from. */
    public static ConfigurationNode paperGlobal() {
        return PaperBridge.global(SOURCE);
    }

    /** The node Paper's world defaults are built from. */
    public static ConfigurationNode paperWorldDefaults() {
        return PaperBridge.worldDefaults(SOURCE);
    }

    /**
     * The node of one world.
     *
     * @param worldName the world's folder name
     * @return what the world sets differently from the defaults
     */
    public static ConfigurationNode paperWorld(final String worldName) {
        return PaperBridge.world(SOURCE, worldName);
    }

    /**
     * What {@code Bukkit.spigot().getPaperConfig()} returns: the global settings, with the world
     * defaults and each world under {@code __________WORLDS__________}, as Paper assembled it from
     * its files.
     *
     * @param worldNames the loaded worlds
     * @return a new configuration
     */
    public static YamlConfiguration paperLegacyObject(final List<String> worldNames) {
        final YamlConfiguration global = yaml(paperGlobal());
        final org.bukkit.configuration.ConfigurationSection worlds = global.createSection("__________WORLDS__________");
        worlds.set("__defaults__", yaml(paperWorldDefaults()));
        for (final String world : worldNames) {
            worlds.set(world, yaml(paperWorld(world)));
        }
        return global;
    }

    private static YamlConfiguration yaml(final ConfigurationNode node) {
        final YamlConfiguration yaml = new YamlConfiguration();
        if (plain(node) instanceof final Map<?, ?> map) {
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                set(yaml, String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return yaml;
    }

    private static void set(final org.bukkit.configuration.ConfigurationSection section, final String key, final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final org.bukkit.configuration.ConfigurationSection child = section.createSection(key);
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                set(child, String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            section.set(key, value);
        }
    }

    private static @Nullable Object plain(final ConfigurationNode node) {
        if (node.isMap()) {
            final Map<Object, Object> map = new LinkedHashMap<>();
            node.childrenMap().forEach((key, child) -> map.put(key, plain(child)));
            return map;
        }
        if (node.isList()) {
            final List<Object> list = new ArrayList<>();
            for (final ConfigurationNode child : node.childrenList()) {
                list.add(plain(child));
            }
            return list;
        }
        return node.raw();
    }

    /**
     * {@code settings.update-folder} for the plugin loader, which reads it before the TOML files
     * are loaded, in the order the server will settle it: a {@code bukkit.yml} that is still there
     * (about to be imported), then a {@code server.properties} line, then {@code plugins.toml}.
     *
     * @param legacyBukkit the {@code --bukkit-settings} file
     * @return the folder's name
     */
    public static String earlyUpdateFolder(final File legacyBukkit) {
        final String fallback = "update";
        try {
            if (legacyBukkit != null && legacyBukkit.isFile()) {
                return YamlConfiguration.loadConfiguration(legacyBukkit).getString("settings.update-folder", fallback);
            }
            final Path properties = ConfigFile.DIRECTORY.resolve(ServerProperties.FILE_NAME);
            if (Files.isRegularFile(properties)) {
                final Properties loaded = new Properties();
                try (InputStream in = Files.newInputStream(properties)) {
                    loaded.load(in);
                }
                final String line = loaded.getProperty(UPDATE_FOLDER);
                if (line != null) {
                    return line.trim();
                }
            }
            final Path plugins = ConfigFile.DIRECTORY.resolve(ServerConfig.PLUGINS);
            if (Files.isRegularFile(plugins)) {
                final CommentedConfig parsed = CommentedConfig.inMemory();
                try (InputStream in = Files.newInputStream(plugins)) {
                    new TomlParser().parse(in, parsed, ParsingMode.REPLACE);
                }
                if (parsed.get(UPDATE_FOLDER) instanceof final String folder) {
                    return folder.trim();
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn("Could not read {} early; using \"{}\": {}", UPDATE_FOLDER, fallback, e.getMessage());
        }
        return fallback;
    }

    // ------------------------------------------------------------------------------------------
    //  Writes while running
    // ------------------------------------------------------------------------------------------

    /**
     * A vanilla setting a command changed, such as {@code /difficulty} or {@code /whitelist on}:
     * saved where the setting lives, server.properties with the mode on and its TOML file with it
     * off. Safe from any region thread; the file is written on the configuration writer thread.
     *
     * @param vanillaKey the server.properties key vanilla uses
     * @param text the value as vanilla writes it
     */
    public static void vanillaChanged(final String vanillaKey, final String text) {
        if (!ServerConfig.isLoaded()) {
            return;
        }
        final Optional<SettingEntry> entry = SettingsCatalog.get().byServerPropertiesKey(vanillaKey)
            .filter(SettingEntry::isVanilla);
        if (entry.isEmpty()) {
            LOGGER.warn("{} changed to {} while running, but it is not a catalogued setting; the change lasts until the "
                + "server stops.", vanillaKey, text);
            return;
        }
        try {
            ServerProperties.set(entry.get().tomlPath(), PropertyValues.parse(entry.get(), text));
        } catch (final RuntimeException e) {
            LOGGER.warn("Could not save {} = {}: {}", entry.get().tomlPath(), text, e.getMessage());
        }
    }
}
