package io.github.adrielorihuela.legacyfolia.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The lobby setting has nothing to compute — it is one flag — so what is worth pinning down is that
 * asking for it before the configuration exists is answered rather than thrown at. Plenty of the
 * server runs before {@code config/} has been read, and a join path that threw there would be a
 * far worse failure than a player spawning where they logged out.
 */
class LobbyTest {

    @Test
    @DisplayName("asking before the configuration is read answers no, and does not throw")
    void answersBeforeTheConfigurationIsRead() {
        assumeFalse(ServerConfig.isLoaded(), "another test loaded the configuration");

        assertFalse(Lobby.forceWorldSpawn());
        assertFalse(NetherRoof.disabled());
    }
}
