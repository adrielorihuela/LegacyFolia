package io.github.adrielorihuela.legacyfolia.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.ServerProperties;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What {@code /legacyfolia properties} says, which is MiniMessage that has to parse cleanly. */
class LegacyFoliaCommandTest {

    private static String plain(final String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    @Test
    @DisplayName("status shows the mode, the file and the count by file")
    void status() {
        final Map<String, Integer> byFile = new LinkedHashMap<>();
        byFile.put("server.toml", 2);
        byFile.put("worlds.toml", 1);
        final String text = plain(LegacyFoliaCommand.describe(
            new ServerProperties.Status(true, Path.of("config", "server.properties"), true, 3, byFile, 1, 0)));
        assertTrue(text.contains("server.properties mode: on"), text);
        assertTrue(text.contains("It changes 3 setting(s) from the defaults (server.toml 2, worlds.toml 1)"), text);
        assertTrue(text.contains("1 line(s) not valid"), text);
        assertFalse(text.contains("<"), text);
    }

    @Test
    @DisplayName("turning the mode off says what moved, and keys are shown literally")
    void off() {
        final ServerProperties.Report report = new ServerProperties.Report(false, false, List.of(), List.of(), List.of(),
            List.of(), List.of("<bold>odd-key"), List.of("motd", "max-players"), List.of());
        final String text = plain(LegacyFoliaCommand.describe(report,
            new ServerProperties.Status(false, Path.of("config", "server.properties"), false, 2, Map.of("server.toml", 2), 0, 0)));
        assertTrue(text.contains("The server.properties mode is off."), text);
        assertTrue(text.contains("2 setting(s) were moved into them from server.properties, which was deleted"), text);
        assertTrue(text.contains("Moved into the TOML files (2): motd, max-players"), text);
        assertTrue(text.contains("<bold>odd-key"), text);
    }
}
