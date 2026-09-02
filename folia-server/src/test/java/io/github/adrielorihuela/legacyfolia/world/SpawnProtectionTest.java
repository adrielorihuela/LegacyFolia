package io.github.adrielorihuela.legacyfolia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks the spawn protection radius that is actually enforced for a configured value. */
class SpawnProtectionTest {

    @Test
    @DisplayName("the default is off, unlike vanilla's 16")
    void defaultIsOff() {
        assertEquals(0, SpawnProtection.DEFAULT_RADIUS);
        if (!io.github.adrielorihuela.legacyfolia.config.ServerConfig.isLoaded()) {
            assertEquals(0, SpawnProtection.radius(), "before the configuration is read, too");
        }
    }

    @Test
    @DisplayName("a configured radius is enforced as written")
    void configuredRadiusIsKept() {
        assertEquals(16, SpawnProtection.effectiveRadius(16));
        assertEquals(0, SpawnProtection.effectiveRadius(0));
    }

    @Test
    @DisplayName("a negative radius is a typo, and is read as off rather than reaching the check")
    void negativeRadiusIsOff() {
        assertEquals(0, SpawnProtection.effectiveRadius(-1));
        assertEquals(0, SpawnProtection.effectiveRadius(Integer.MIN_VALUE));
    }
}
