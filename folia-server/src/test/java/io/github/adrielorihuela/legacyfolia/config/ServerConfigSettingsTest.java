package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.bridge.BukkitBridge;
import io.github.adrielorihuela.legacyfolia.config.bridge.ServerConfigSettings;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings the running server hands the bridge: the loaded files with the server.properties
 * mode applied, tables and the list of worlds included.
 */
class ServerConfigSettingsTest {

    private static final String ALIASES = "plugins.commands.aliases";
    private static final String GENERATORS = "world.generators";
    private static final String DESPAWN = "gameplay.items.alt-item-despawn-rate-items";

    @TempDir
    Path dir;

    @AfterEach
    void forget() {
        ServerProperties.forget();
        ServerConfig.unload();
    }

    private void edit(final String file, final List<String> table, final String path, final String literal) throws Exception {
        final Path target = this.dir.resolve(file);
        Files.writeString(target, TomlText.parse(Files.readString(target, StandardCharsets.UTF_8))
            .set(table, path, literal, !table.isEmpty()).text(), StandardCharsets.UTF_8);
    }

    private void append(final String file, final String text) throws Exception {
        final Path target = this.dir.resolve(file);
        Files.writeString(target, Files.readString(target, StandardCharsets.UTF_8) + text, StandardCharsets.UTF_8);
    }

    private void start() throws Exception {
        ServerProperties.flush();
        ServerConfig.load(this.dir);
        ServerProperties.reconcile();
    }

    @Test
    @DisplayName("tables, worlds and the server.properties override all reach the bridge")
    void tablesWorldsAndOverrides() throws Exception {
        start();
        final ServerConfigSettings settings = new ServerConfigSettings();
        assertEquals(Map.of("icanhasbukkit", List.of("version $1-")), settings.get(ALIASES));
        assertEquals(Map.of(), settings.get(GENERATORS));
        assertTrue(settings.worlds().isEmpty());

        // Mode off: what the TOML files say, tables whole, and every world that has a section.
        ServerProperties.disable();
        edit(ServerConfig.WORLD, List.of(), GENERATORS, "{ world_nether = \"CleanroomGenerator:.\" }");
        edit(ServerConfig.PLUGINS, List.of(), ALIASES + ".hub", "[\"server lobby\"]");
        edit(ServerConfig.WORLDS, List.of("worlds", "world_nether"), DESPAWN, "{ \"minecraft:netherrack\" = 100 }");
        start();
        assertEquals(Map.of("world_nether", "CleanroomGenerator:."), settings.get(GENERATORS));
        assertEquals(List.of("server lobby"), ((Map<?, ?>) settings.get(ALIASES)).get("hub"));
        assertEquals(Map.of("minecraft:netherrack", 100), settings.get(DESPAWN, "world_nether"));
        assertEquals(Map.of("minecraft:cobblestone", 300), settings.get(DESPAWN, "world"));
        assertEquals(Set.of("world_nether"), settings.worlds());

        final YamlConfiguration bukkit = BukkitBridge.bukkit(settings);
        assertEquals("CleanroomGenerator:.", bukkit.getString("worlds.world_nether.generator"));
        assertEquals(List.of("server lobby"), BukkitBridge.commands(settings).getStringList("aliases.hub"));

        // Mode on: everything moves to server.properties, and the bridge sees the same values,
        // plus a line written there by hand, live.
        ServerProperties.enable();
        assertEquals(Map.of("world_nether", "CleanroomGenerator:."), settings.get(GENERATORS));
        assertEquals(Map.of("minecraft:netherrack", 100), settings.get(DESPAWN, "world_nether"));
        assertEquals(Set.of("world_nether"), settings.worlds());

        append(ServerProperties.FILE_NAME, "motd=From the file\nworlds.world_the_end.chunks.view-distance=4\n");
        start();
        assertEquals("From the file", settings.get("server.motd"));
        assertEquals(4, settings.get("chunks.view-distance", "world_the_end"));
        assertEquals(Set.of("world_nether", "world_the_end"), settings.worlds());

        ServerProperties.set("server.difficulty", "hard").join();
        assertEquals("hard", settings.get("server.difficulty"), "a write is seen at once");
    }
}
