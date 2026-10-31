package io.github.adrielorihuela.legacyfolia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The lobby has little to compute: whether to forget the saved position, and with
 * {@code lobby-spawn-world = "main-world"} the saved world too. What is worth pinning down is that
 * decision, how the setting's two values are read, and that asking before the configuration exists
 * is answered rather than thrown at. Plenty of the server runs before {@code config/} has been read,
 * and a join path that threw there would be a far worse failure than a player spawning where they
 * logged out.
 */
class LobbyTest {

    @Test
    @DisplayName("asking before the configuration is read answers no, and does not throw")
    void answersBeforeTheConfigurationIsRead() {
        assumeFalse(ServerConfig.isLoaded(), "another test loaded the configuration");

        assertFalse(Lobby.forceWorldSpawn());
        assertFalse(NetherRoof.disabled());
    }

    @Test
    @DisplayName("lobby-spawn-world: only a forced spawn in main-world mode forgets the saved world")
    void spawnWorldDecision() {
        assertTrue(Lobby.sendToMainWorld(true, Lobby.SpawnWorld.MAIN_WORLD));
        assertFalse(Lobby.sendToMainWorld(true, Lobby.SpawnWorld.LOGOUT_WORLD));
        assertFalse(Lobby.sendToMainWorld(false, Lobby.SpawnWorld.MAIN_WORLD), "means nothing while the spawn is not forced");
        assertFalse(Lobby.sendToMainWorld(false, Lobby.SpawnWorld.LOGOUT_WORLD));
    }

    @Test
    @DisplayName("lobby-spawn-world reads its two values, and anything else as the default")
    void spawnWorldValues() {
        assertEquals(Lobby.SpawnWorld.MAIN_WORLD, Lobby.SpawnWorld.parse("main-world"));
        assertEquals(Lobby.SpawnWorld.MAIN_WORLD, Lobby.SpawnWorld.parse(" Main-World "));
        assertEquals(Lobby.SpawnWorld.LOGOUT_WORLD, Lobby.SpawnWorld.parse("logout-world"));
        assertEquals(Lobby.SpawnWorld.LOGOUT_WORLD, Lobby.SpawnWorld.parse("lobby"));
        assertEquals(Lobby.SpawnWorld.LOGOUT_WORLD, Lobby.SpawnWorld.parse(null));
        assertEquals("main-world", Lobby.SpawnWorld.MAIN_WORLD.value());
    }

    @Test
    @DisplayName("before the configuration is read, nobody is sent to another world")
    void spawnWorldBeforeTheConfigurationIsRead() {
        assumeFalse(ServerConfig.isLoaded(), "another test loaded the configuration");
        assertEquals(Lobby.SpawnWorld.LOGOUT_WORLD, Lobby.spawnWorld());
        assertFalse(Lobby.sendToMainWorld());
    }
}
