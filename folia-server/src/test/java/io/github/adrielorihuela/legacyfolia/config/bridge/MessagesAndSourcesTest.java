package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Messages in the form each legacy object keeps them, and the two sources the bridge reads from.
 */
class MessagesAndSourcesTest {

    private static final TomlSettings MESSAGES = TomlSettings.bundledWith("""
        [server.messages]
        whitelist = "<red>You are not on the <bold>list</bold>"
        unknown-command = "translate:commands.help.failed"
        shutdown = "<gold>Back soon"
        restart = "Plain text stays plain"
        """);

    @Test
    @DisplayName("Spigot's messages are MiniMessage rendered to section-sign colours")
    void spigotMessages() {
        final YamlConfiguration spigot = SpigotBridge.build(MESSAGES, name -> null);
        assertEquals("§cYou are not on the §llist", spigot.getString("messages.whitelist"));
        assertEquals("Plain text stays plain", spigot.getString("messages.restart"));
        assertEquals("Outdated client! Please use {0}", spigot.getString("messages.outdated-client"), "placeholders survive");
    }

    @Test
    @DisplayName("a translate: key cannot stay translatable in a plain string, so Spigot gets it rendered in the server's language")
    void translateInALegacyString() {
        final String rendered = SpigotBridge.build(MESSAGES, name -> null).getString("messages.unknown-command");
        assertEquals(LegacyComponentSerializer.legacySection().serialize(Component.translatable("commands.help.failed")), rendered);
        assertEquals("Unknown command or insufficient permissions", rendered);
    }

    @Test
    @DisplayName("Bukkit's shutdown message is a legacy string too, which Server#shutdownMessage parses back")
    void bukkitShutdownMessage() {
        assertEquals("§6Back soon", BukkitBridge.bukkit(MESSAGES).getString("settings.shutdown-message"));
    }

    @Test
    @DisplayName("the TOML source resolves world, then [worlds.default], then the global section")
    void tomlResolution() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [gameplay.hunger]
            sprint-multiplier = 0.2
            [worlds.default]
            "gameplay.hunger.sprint-multiplier" = 0.3
            [worlds.world_nether.gameplay.hunger]
            sprint-multiplier = 0.4
            """);
        assertEquals(0.2, settings.get("gameplay.hunger.sprint-multiplier"));
        assertEquals(0.3, settings.get("gameplay.hunger.sprint-multiplier", "default"));
        assertEquals(0.3, settings.get("gameplay.hunger.sprint-multiplier", "world"));
        assertEquals(0.4, settings.get("gameplay.hunger.sprint-multiplier", "world_nether"));
        assertEquals(Set.of("world_nether"), settings.worlds());
    }

    @Test
    @DisplayName("a setting whose value is a table is kept whole, not flattened")
    void tablesStayWhole() {
        final TomlSettings settings = TomlSettings.bundled();
        assertEquals(Map.of("icanhasbukkit", List.of("version $1-")), settings.get("plugins.commands.aliases"));
        assertEquals(Map.of("minecraft:cobblestone", 300), settings.get("gameplay.items.alt-item-despawn-rate-items", "world"));
        assertTrue(settings.worlds().isEmpty());
    }

    @Test
    @DisplayName("the ServerConfig adapter refuses to answer before the files are read")
    void serverConfigAdapterNeedsTheFiles() {
        if (!ServerConfig.isLoaded()) {
            assertThrows(IllegalStateException.class, () -> new ServerConfigSettings().get("server.motd"));
        }
    }
}
