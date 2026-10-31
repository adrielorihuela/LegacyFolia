package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The {@code bukkit.yml} and {@code commands.yml} that {@code CraftServer} keeps as
 * {@code configuration} and {@code commandsConfiguration}, built in memory from the settings.
 *
 * <p>Both are meant to take the place of {@code YamlConfiguration.loadConfiguration(file)} in the
 * {@code CraftServer} constructor and in {@code reload()}. The constructor goes on to lay the
 * bundled templates under them as defaults, exactly as it does for a loaded file, so anything the
 * catalogue does not hold still answers with Bukkit's own default.</p>
 *
 * <p>Bukkit's settings are server-wide, so this takes the global values. The per-world spawn
 * limits, spawn intervals and autosave interval, which Bukkit cannot express, are carried by
 * Paper's per-world configuration instead ({@link PaperBridge}), which is how a stock server
 * expresses them too.</p>
 */
public final class BukkitBridge {

    /** What {@code bukkit.yml} holds under {@code aliases} since the aliases moved out. */
    private static final String ALIASES_POINTER = "now-in-commands.yml";

    /** The two per-world tables Bukkit keeps under {@code worlds.<world>}, by TOML path, with the key each one sets. */
    private static final Map<String, String> PER_WORLD_TABLES = Map.of(
        "world.generators", "generator",
        "world.biome-providers", "biome-provider");

    /**
     * Not in Bukkit's template: {@code Main} reads it with {@code "."} as its own fallback, so a
     * stock {@code bukkit.yml} has no such key and a plugin asking for it gets nothing. It is
     * written only when it says something else.
     */
    private static final String WORLD_CONTAINER = "world.storage.world-container";

    private BukkitBridge() {
    }

    /**
     * Builds {@code bukkit.yml}.
     *
     * @param source the settings
     * @return a new configuration, never saved anywhere
     */
    public static YamlConfiguration bukkit(final EffectiveSettings source) {
        final YamlConfiguration config = new YamlConfiguration();
        for (final SettingEntry entry : SettingsCatalog.get().fromSource(LegacySource.BUKKIT, false)) {
            final String worldKey = PER_WORLD_TABLES.get(entry.tomlPath());
            if (worldKey != null) {
                perWorld(config, worldKey, BridgeValues.table(BridgeValues.global(source, entry)));
                continue;
            }
            final Object value = BridgeValues.yaml(entry, BridgeValues.global(source, entry));
            if (WORLD_CONTAINER.equals(entry.tomlPath()) && value.equals(entry.defaultValue())) {
                continue;
            }
            config.set(entry.legacyPath(), value);
        }
        // Kept as the stock file has it: CraftServer moves an old alias section out of bukkit.yml
        // whenever this is not a string, and plugins that read the key find the same pointer.
        config.set("aliases", ALIASES_POINTER);
        return config;
    }

    /**
     * Builds {@code commands.yml}.
     *
     * <p>The alias table is written even when it is empty. {@code CraftServer} only forces its
     * {@code icanhasbukkit} default on a file with no alias section at all, so an operator who
     * removed every alias keeps none, as with the file.</p>
     *
     * @param source the settings
     * @return a new configuration, never saved anywhere
     */
    public static YamlConfiguration commands(final EffectiveSettings source) {
        final YamlConfiguration config = new YamlConfiguration();
        for (final SettingEntry entry : SettingsCatalog.get().fromSource(LegacySource.COMMANDS, false)) {
            final Object value = BridgeValues.yaml(entry, BridgeValues.global(source, entry));
            if (value instanceof final Map<?, ?> table) {
                final ConfigurationSection section = config.createSection(entry.legacyPath());
                for (final Map.Entry<?, ?> alias : table.entrySet()) {
                    // An alias is a list of commands; a single string is accepted as a list of one,
                    // as Bukkit itself accepts it.
                    final Object commands = alias.getValue();
                    section.set(String.valueOf(alias.getKey()), commands instanceof final List<?> list ? list : List.of(String.valueOf(commands)));
                }
            } else {
                config.set(entry.legacyPath(), value);
            }
        }
        return config;
    }

    /**
     * {@code worlds.<world>.generator} or {@code worlds.<world>.biome-provider}, one entry per world
     * that names a plugin. A world that names both keeps both in its one section.
     */
    private static void perWorld(final YamlConfiguration config, final String key, final Map<String, Object> plugins) {
        for (final Map.Entry<String, Object> world : plugins.entrySet()) {
            final String plugin = String.valueOf(world.getValue()).trim();
            if (plugin.isEmpty()) {
                continue;
            }
            ConfigurationSection worlds = config.getConfigurationSection("worlds");
            if (worlds == null) {
                worlds = config.createSection("worlds");
            }
            ConfigurationSection section = worlds.getConfigurationSection(world.getKey());
            if (section == null) {
                section = worlds.createSection(world.getKey());
            }
            section.set(key, plugin);
        }
    }
}
