package io.github.adrielorihuela.legacyfolia.config.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalog is only worth anything while it agrees with the TOML files, so these tests are the
 * contract between the two: nothing in one that is missing from the other, the same defaults, and
 * no legacy path claimed twice.
 */
class SettingsCatalogTest {

    /** Every leaf of the eight TOML files: dotted path to value, and to the file it was read from. */
    private static final Map<String, Object> TOML_VALUES = new LinkedHashMap<>();

    private static final Map<String, String> TOML_FILES = new HashMap<>();

    private static SettingsCatalog catalog;

    @BeforeAll
    static void readEverything() throws Exception {
        catalog = SettingsCatalog.get();
        final Set<String> tables = new java.util.HashSet<>();
        for (final SettingEntry entry : catalog.all()) {
            if (entry.kind() == SettingKind.TABLE) {
                tables.add(entry.tomlPath());
            }
        }
        for (final String file : ServerConfig.fileNames()) {
            final CommentedConfig parsed = CommentedConfig.inMemory();
            try (InputStream in = SettingsCatalogTest.class.getClassLoader().getResourceAsStream("legacyfolia/" + file)) {
                assertNotNull(in, file + " is not on the classpath");
                new TomlParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8), parsed, ParsingMode.REPLACE);
            }
            if (ServerConfig.WORLDS.equals(file)) {
                // worlds.toml holds overrides of keys owned elsewhere, never keys of its own.
                assertEquals(Set.of("worlds"), parsed.valueMap().keySet(),
                    "worlds.toml should hold nothing but the [worlds] table");
                continue;
            }
            collect(parsed, "", file, tables);
        }
    }

    private static void collect(final UnmodifiableConfig config, final String prefix, final String file,
                                final Set<String> tables) {
        for (final UnmodifiableConfig.Entry entry : config.entrySet()) {
            final String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            final Object value = entry.getValue();
            // A table that is a setting in its own right (an item id mapped to ticks) is one value;
            // so is an empty table, which would otherwise vanish from the comparison unnoticed.
            if (value instanceof final UnmodifiableConfig section && !tables.contains(path) && !section.isEmpty()) {
                collect(section, path, file, tables);
            } else {
                TOML_VALUES.put(path, SettingsCatalog.plain(value));
                TOML_FILES.put(path, file);
            }
        }
    }

    @Test
    @DisplayName("every key in the TOML files has a catalog entry")
    void everyTomlKeyIsCatalogued() {
        final List<String> missing = TOML_VALUES.keySet().stream()
            .filter(path -> catalog.byPath(path).isEmpty()).toList();
        assertTrue(missing.isEmpty(), "keys with no row in catalog.tsv: " + missing);
    }

    @Test
    @DisplayName("every catalog entry names a key that exists, in the file it claims")
    void everyEntryExistsInItsFile() {
        final List<String> orphans = new ArrayList<>();
        for (final SettingEntry entry : catalog.all()) {
            final String file = TOML_FILES.get(entry.tomlPath());
            if (file == null) {
                orphans.add(entry.tomlPath() + " (no such key)");
            } else if (!file.equals(entry.tomlFile())) {
                orphans.add(entry.tomlPath() + " (in " + file + ", catalogued as " + entry.tomlFile() + ")");
            }
        }
        assertTrue(orphans.isEmpty(), "catalog rows that do not match a TOML key: " + orphans);
        assertEquals(TOML_VALUES.size(), catalog.size(), "the catalog and the files differ in size");
    }

    @Test
    @DisplayName("every default in the catalog is the default the TOML ships with")
    void defaultsMatchTheToml() {
        final List<String> wrong = new ArrayList<>();
        for (final SettingEntry entry : catalog.all()) {
            final Object inToml = TOML_VALUES.get(entry.tomlPath());
            if (inToml != null && !inToml.equals(entry.defaultValue())) {
                wrong.add(entry.tomlPath() + ": catalog " + entry.defaultValue() + ", TOML " + inToml);
            }
        }
        assertTrue(wrong.isEmpty(), "defaults that disagree: " + wrong);
    }

    @Test
    @DisplayName("no legacy path is claimed by two settings")
    void legacyPathsAreUnique() {
        final Map<String, String> claimed = new HashMap<>();
        final List<String> duplicates = new ArrayList<>();
        for (final SettingEntry entry : catalog.all()) {
            for (final LegacyRef ref : entry.allLegacy()) {
                // Keyed by file, not by source: Spigot's global and per-world settings share
                // spigot.yml, and the same path there is the same YAML key whichever class reads it.
                final String key = ref.source().fileName() + ":" + ref.path();
                final String previous = claimed.put(key, entry.tomlPath());
                if (previous != null) {
                    duplicates.add(key + " (" + previous + " and " + entry.tomlPath() + ")");
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "legacy paths used twice: " + duplicates);
    }

    @Test
    @DisplayName("every default is a legal value of its kind")
    void defaultsAreLegalValues() {
        for (final SettingEntry entry : catalog.all()) {
            assertDoesNotThrow(() -> SettingReaders.convert(entry, entry.defaultValue()),
                () -> entry.tomlPath() + " has a default its own kind refuses");
        }
    }

    @Test
    @DisplayName("per-world settings live in files a world can override, and come from per-world sources")
    void perWorldIsConsistent() {
        for (final SettingEntry entry : catalog.all()) {
            if (entry.perWorld()) {
                assertTrue(ServerConfig.isPerWorldFile(entry.tomlFile()),
                    entry.tomlPath() + " is per world but " + entry.tomlFile() + " is never consulted per world");
                assertTrue(entry.allLegacy().stream().anyMatch(ref -> ref.source().perWorld()),
                    entry.tomlPath() + " is per world but none of its legacy sources is");
            } else {
                assertFalse(entry.legacySource().perWorld() && entry.alsoFrom().isEmpty()
                        && !entry.tomlPath().equals("world.generation.below-zero-generation-in-existing-chunks"),
                    entry.tomlPath() + " comes only from a per-world source but is not marked per world");
            }
        }
    }

    @Test
    @DisplayName("server.properties keys are unique, and secrets never get one")
    void serverPropertiesKeys() {
        final Map<String, String> seen = new HashMap<>();
        for (final SettingEntry entry : catalog.all()) {
            final String key = entry.serverPropertiesKey();
            if (entry.secret() || SettingEntry.USE_SERVER_PROPERTIES.equals(entry.tomlPath())) {
                assertNull(key, entry.tomlPath() + " must never appear in server.properties");
                continue;
            }
            assertNotNull(key);
            assertNull(seen.put(key, entry.tomlPath()), "two settings map to the server.properties key " + key);
            assertEquals(entry, catalog.byServerPropertiesKey(key).orElseThrow());
        }
        assertEquals("motd", catalog.require("server.motd").serverPropertiesKey());
        assertEquals("gameplay.hunger.jump-walk-exhaustion",
            catalog.require("gameplay.hunger.jump-walk-exhaustion").serverPropertiesKey());
        assertEquals("worlds.world_nether.chunks.view-distance",
            catalog.require("chunks.view-distance").serverPropertiesKey("world_nether"));
        assertThrows(IllegalStateException.class, () -> catalog.require("server.motd").serverPropertiesKey("world"));
    }

    @Test
    @DisplayName("the secrets and the decisions are catalogued the way the user decided")
    void decisions() {
        assertTrue(catalog.require("network.rcon.password-file").secret());
        assertTrue(catalog.require("network.velocity.secret-file").secret());
        assertTrue(catalog.require("plugins.auto-update.github-token-file").secret());
        assertEquals(false, catalog.require("server.access.white-list").defaultValue());
        assertEquals(true, catalog.require("network.rcon.enabled").defaultValue());
        assertEquals("127.0.0.1", catalog.require("network.rcon.ip").defaultValue());
        assertEquals(true, catalog.require("server.use-server-properties").defaultValue());
        assertEquals(LegacySource.EULA_TXT, catalog.require("server.eula").legacySource());
        assertEquals(List.of("logout-world", "main-world"), catalog.require("world.lobby-spawn-world").values());
    }

    @Test
    @DisplayName("lookups by legacy path and by vanilla name find the setting that absorbed it")
    void lookups() {
        assertEquals("gameplay.hunger.jump-walk-exhaustion", catalog.byLegacy(LegacySource.SPIGOT_WORLD,
            "world-settings.default.hunger.jump-walk-exhaustion").orElseThrow().tomlPath());
        assertEquals("network.velocity.online-mode",
            catalog.byLegacy(LegacySource.PAPER_GLOBAL, "proxies.velocity.online-mode").orElseThrow().tomlPath());
        // A secondary location finds the same setting as the main one.
        assertEquals("performance.spawning.spawn-limits.monsters",
            catalog.byLegacy(LegacySource.PAPER_WORLD, "entities.spawning.spawn-limits.monster").orElseThrow().tomlPath());
        assertEquals("network.port", catalog.byVanillaName("server-port").orElseThrow().tomlPath());
        assertTrue(catalog.byVanillaName("announce-player-achievements").isEmpty());
        assertFalse(catalog.inFile(ServerConfig.COMBAT).isEmpty());
        assertTrue(catalog.inFile(ServerConfig.COMBAT).stream().allMatch(SettingEntry::isForkOnly));
        assertTrue(catalog.require("gameplay.items.trident-despawn-rate").notes().contains("arrow-despawn-rate"));
        System.out.println("CATALOG keys=" + catalog.size() + " perWorld=" + catalog.perWorld().size());
    }

    @Test
    @DisplayName("a malformed catalog is refused with the line it is on")
    void malformedRowsAreRefused() {
        final String header = "path\tkind\tper-world\tsource\tlegacy-path\talso-from\tvanilla\tmutable\tsecret\tvalues\tdefault\tnotes\n";
        final String good = "server.motd\tSTRING\tfalse\tSERVER_PROPERTIES\tmotd\t-\tmotd\ttrue\tfalse\t-\t\"x\"\t-\n";
        assertDoesNotThrow(() -> SettingsCatalog.parse(new StringReader(header + good)));

        final IllegalArgumentException columns = assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + "server.motd\tSTRING\n")));
        assertTrue(columns.getMessage().contains("line 2"), columns.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + good.replace("STRING", "STRNG"))));
        assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + good.replace("\"x\"", "x y"))));
        assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + good + good)), "a key listed twice");
        assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + good.replace("SERVER_PROPERTIES\tmotd", "NONE\tmotd"))),
            "a fork-only key with a legacy path");
        assertThrows(IllegalArgumentException.class,
            () -> SettingsCatalog.parse(new StringReader(header + good.replace("\tSTRING\t", "\tENUM\t"))),
            "an enum with no values");
    }
}
