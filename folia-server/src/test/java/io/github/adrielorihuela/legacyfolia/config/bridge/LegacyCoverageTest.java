package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.adrielorihuela.legacyfolia.config.catalog.LegacyExclusions;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacyRef;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingKind;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.papermc.paper.configuration.PaperConfigurations;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.key.Key;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.spigotmc.SpigotConfig;
import org.spigotmc.SpigotWorldConfig;

/**
 * Every key the older servers read has a place: a TOML key in the catalogue, or a deliberate
 * exclusion in {@link LegacyExclusions} with its reason.
 *
 * <p>The keys are not listed by hand. They are what each server writes for a fresh install, from
 * its own code: the {@code server.properties} vanilla fills in from an empty file, the
 * {@code spigot.yml} Spigot writes with a world loaded, the {@code paper-global.yml} and
 * {@code paper-world-defaults.yml} Paper writes, and Bukkit's bundled {@code bukkit.yml} and
 * {@code commands.yml}. A Paper update that adds a key makes this test fail until the key is given
 * a place, which is the point: otherwise a migrated server would lose its value silently.</p>
 */
@ExtendWith(ItemSanitizerFirst.class)
@AllFeatures
class LegacyCoverageTest {

    /** What describes a file rather than configuring anything. */
    private static final Set<String> METADATA = Set.of("_version", "config-version");

    /**
     * Keys Bukkit reads that its bundled template does not write: the world container, read in
     * {@code Main}, and the per-world generator and biome provider, read by {@code CraftServer} for
     * whichever world asks. A world name stands in for every world.
     */
    private static final List<String> BUKKIT_UNTEMPLATED = List.of(
        "settings.world-container", "worlds.<world>.generator", "worlds.<world>.biome-provider");

    private static final Path FORWARDING_SECRET = Path.of("config", "forwarding.secret");

    private static boolean secretExisted;

    private static RegistryAccess access;

    @BeforeAll
    static void setUp() {
        secretExisted = Files.exists(FORWARDING_SECRET);
        access = RegistryHelper.registryAccess();
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!secretExisted) {
            Files.deleteIfExists(FORWARDING_SECRET);
            final Path parent = FORWARDING_SECRET.getParent();
            if (Files.isDirectory(parent)) {
                try (var children = Files.list(parent)) {
                    if (children.findAny().isEmpty()) {
                        Files.delete(parent);
                    }
                }
            }
        }
    }

    private static YamlConfiguration template(final String name) {
        final InputStream in = LegacyCoverageTest.class.getClassLoader().getResourceAsStream("configurations/" + name);
        assertNotNull(in, name + " template");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Whether the catalogue holds a key, directly or as part of a table it holds whole. */
    private static boolean catalogued(final LegacySource source, final String path) {
        final SettingsCatalog catalog = SettingsCatalog.get();
        if (catalog.byLegacy(source, path).isPresent()) {
            return true;
        }
        for (final SettingEntry entry : catalog.all()) {
            if (entry.kind() != SettingKind.TABLE) {
                continue;
            }
            for (final LegacyRef ref : entry.allLegacy()) {
                if (ref.source() == source && path.startsWith(ref.path() + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void check(final LegacySource source, final Iterable<String> paths, final List<String> missing) {
        for (final String path : paths) {
            // bukkit.yml's aliases is only a pointer to commands.yml, which the catalogue holds.
            if (METADATA.contains(path) || source == LegacySource.BUKKIT && "aliases".equals(path)) {
                continue;
            }
            if (!catalogued(source, path) && LegacyExclusions.reason(source, path).isEmpty()) {
                missing.add(source + ":" + path);
            }
        }
    }

    @Test
    @DisplayName("every key vanilla, Bukkit, Spigot and Paper read is catalogued or deliberately excluded")
    void everyLegacyKeyHasAPlace(@TempDir final Path dir) throws Exception {
        final List<String> missing = new ArrayList<>();

        check(LegacySource.SERVER_PROPERTIES,
            new TreeSet<>(new DedicatedServerProperties(new Properties(), null).properties.stringPropertyNames()), missing);

        final Set<String> bukkit = new TreeSet<>(Leaves.of(template("bukkit.yml")).keySet());
        bukkit.addAll(BUKKIT_UNTEMPLATED);
        check(LegacySource.BUKKIT, bukkit, missing);
        check(LegacySource.COMMANDS, Leaves.of(template("commands.yml")).keySet(), missing);

        SpigotConfig.init(dir.resolve("spigot.yml").toFile());
        new SpigotWorldConfig("world", Key.key("minecraft:overworld"));
        final List<String> spigot = new ArrayList<>();
        final List<String> spigotWorld = new ArrayList<>();
        for (final String path : Leaves.of(SpigotConfig.config).keySet()) {
            if (path.startsWith("world-settings.default.")) {
                spigotWorld.add(path);
            } else if (!path.startsWith("world-settings.")) {
                spigot.add(path);
            }
        }
        check(LegacySource.SPIGOT, spigot, missing);
        check(LegacySource.SPIGOT_WORLD, spigotWorld, missing);

        final Path paper = dir.resolve("paper");
        new PaperConfigurations(paper).initializeGlobalConfiguration(access);
        new PaperConfigurations(paper).initializeWorldDefaultsConfiguration(access);
        check(LegacySource.PAPER_GLOBAL, Leaves.of(paper.resolve("paper-global.yml").toFile()).keySet(), missing);
        check(LegacySource.PAPER_WORLD, Leaves.of(paper.resolve("paper-world-defaults.yml").toFile()).keySet(), missing);

        if (!missing.isEmpty()) {
            fail(missing.size() + " legacy key(s) have no TOML key and no exclusion; a migrated server would lose "
                + "their values. Catalogue them, or add them to LegacyExclusions and SETTINGS-MAP 8:\n  "
                + String.join("\n  ", missing));
        }
    }

    @Test
    @DisplayName("an exclusion never names a key the catalogue holds")
    void exclusionsAreNotCatalogued() {
        for (final Map.Entry<LegacyRef, String> excluded : LegacyExclusions.all().entrySet()) {
            final LegacyRef ref = excluded.getKey();
            assertTrue(SettingsCatalog.get().byLegacy(ref.source(), ref.path()).isEmpty(),
                ref + " is excluded and catalogued at once");
        }
    }
}
