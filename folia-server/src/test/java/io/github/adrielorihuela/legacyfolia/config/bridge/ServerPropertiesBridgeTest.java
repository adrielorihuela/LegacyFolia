package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The properties built from the bundled TOML files against the ones the server loads from a fresh
 * {@code server.properties}: what {@code DedicatedServerProperties} keeps after reading an empty
 * file, which is exactly what it writes out as the default file.
 */
@ExtendWith(ItemSanitizerFirst.class)
@AllFeatures
class ServerPropertiesBridgeTest {

    /**
     * Where the fork means to differ from the server it is replacing (decision 4). The spawn
     * protection radius and the whitelist are not here: the fork already patched their defaults
     * into {@code DedicatedServerProperties}, so the server this replaces agrees with the TOML.
     */
    private static final Map<String, List<String>> FORK_DIFFERENCES = Map.of(
        // key -> [what the TOML gives, what a fresh server.properties gives]
        "enable-rcon", List.of("true", "false"),
        "rcon.ip", List.of("127.0.0.1", "<absent>"),
        "rcon.password", List.of("generated", ""));

    /** Random on every start, by vanilla's design, so there is nothing to compare. */
    private static final Set<String> NONDETERMINISTIC = Set.of("management-server-secret");

    private static Properties loaded(final Properties input) {
        return new DedicatedServerProperties(input, null).properties;
    }

    @Test
    @DisplayName("the default TOML files give the same server.properties a fresh server has, but for RCON")
    void defaultsMatchAFreshServer() {
        final Properties stock = loaded(new Properties());
        final Properties bridged = loaded(ServerPropertiesBridge.build(TomlSettings.bundled(), "generated"));

        final Set<String> keys = new TreeSet<>();
        stock.stringPropertyNames().forEach(keys::add);
        bridged.stringPropertyNames().forEach(keys::add);
        for (final String key : keys) {
            if (NONDETERMINISTIC.contains(key)) {
                continue;
            }
            final String stockValue = stock.getProperty(key, "<absent>");
            final String bridgedValue = bridged.getProperty(key, "<absent>");
            final List<String> expected = FORK_DIFFERENCES.get(key);
            if (expected != null) {
                assertEquals(expected.get(0), bridgedValue, key + " from the TOML");
                assertEquals(expected.get(1), stockValue, key + " in a fresh server.properties");
            } else {
                assertEquals(stockValue, bridgedValue, key);
            }
        }
    }

    @Test
    @DisplayName("every key written is one vanilla reads")
    void everyKeyIsVanillas() {
        final Set<String> known = loaded(new Properties()).stringPropertyNames();
        for (final String key : ServerPropertiesBridge.build(TomlSettings.bundled(), "x").stringPropertyNames()) {
            assertTrue(known.contains(key) || "rcon.ip".equals(key), key + " is not a vanilla property");
        }
    }

    @Test
    @DisplayName("permission levels, enums, lists and booleans arrive in the form Settings parses")
    void specialTypes() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [server]
            difficulty = "hard"
            gamemode = "creative"
            hardcore = true
            [server.access]
            op-permission-level = 3
            function-permission-level = 4
            [server.data-packs]
            initial-enabled-packs = ["vanilla", "bundle"]
            initial-disabled-packs = ["minecart_improvements"]
            """);
        final Properties properties = ServerPropertiesBridge.build(settings, "x");
        assertEquals("3", properties.getProperty("op-permission-level"));
        assertEquals("vanilla,bundle", properties.getProperty("initial-enabled-packs"));
        assertEquals("true", properties.getProperty("hardcore"));

        final DedicatedServerProperties server = new DedicatedServerProperties(properties, null);
        assertSame(LevelBasedPermissionSet.ADMIN, server.opPermissions.get());
        assertSame(LevelBasedPermissionSet.OWNER, server.functionPermissions);
        assertSame(Difficulty.HARD, server.difficulty.get());
        assertSame(GameType.CREATIVE, server.gameMode.get());
        assertTrue(server.hardcore);
        assertEquals(List.of("vanilla", "bundle"), server.initialDataPackConfiguration.getEnabled());
        assertEquals(List.of("minecart_improvements"), server.initialDataPackConfiguration.getDisabled());
    }

    @Test
    @DisplayName("an empty rcon.ip is left out, so vanilla falls back to server-ip")
    void emptyRconIpIsAbsent() {
        final TomlSettings settings = TomlSettings.bundledWith("""
            [network]
            ip = "10.0.0.5"
            [network.rcon]
            ip = ""
            """);
        final Properties properties = ServerPropertiesBridge.build(settings, "x");
        assertNull(properties.getProperty("rcon.ip"));
        assertEquals("10.0.0.5", new DedicatedServerProperties(properties, null).rconIp);
    }

    @Test
    @DisplayName("RCON is on, on loopback, with the password it is given")
    void rcon() {
        final DedicatedServerProperties server = new DedicatedServerProperties(
            ServerPropertiesBridge.build(TomlSettings.bundled(), "s3cret"), null);
        assertTrue(server.enableRcon);
        assertEquals("127.0.0.1", server.rconIp);
        assertEquals("s3cret", server.rconPassword);
        assertEquals("rcon.secret", ServerPropertiesBridge.rconPasswordFile(TomlSettings.bundled()));
    }

    @Test
    @DisplayName("a value the setting cannot take falls back to its default instead of reaching vanilla")
    void invalidValueFallsBack() {
        final Properties properties = ServerPropertiesBridge.build(TomlSettings.bundledWith("""
            [server.access]
            op-permission-level = 7
            [server]
            max-players = "many"
            """), "x");
        assertEquals("4", properties.getProperty("op-permission-level"));
        assertEquals("20", properties.getProperty("max-players"));
        assertFalse(properties.getProperty("motd").isEmpty());
    }
}
