package io.github.adrielorihuela.legacyfolia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the build limit the Nether roof setting produces, against the real vanilla numbers: the
 * Nether runs from y=0 to y=255 with a logical height of 128, so the roof is y=127.
 */
class NetherRoofTest {

    // The ids the server's own dimension keys carry. Named here rather than taken from Level,
    // whose static setup pulls in registries a bare unit test has no server to build.
    private static final String NETHER = "minecraft:the_nether";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String END = "minecraft:the_end";

    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 255;
    private static final int NETHER_LOGICAL_HEIGHT = 128;

    @Test
    @DisplayName("with the setting on the Nether may only be built up to the roof")
    void netherStopsAtTheRoof() {
        assertEquals(127, NetherRoof.buildMaxY(NETHER, NETHER_MIN_Y, NETHER_MAX_Y,
            NETHER_LOGICAL_HEIGHT, true));
    }

    @Test
    @DisplayName("with the setting off the Nether keeps its real height, roof and all")
    void netherKeepsItsHeightWhenOff() {
        assertEquals(NETHER_MAX_Y, NetherRoof.buildMaxY(NETHER, NETHER_MIN_Y, NETHER_MAX_Y,
            NETHER_LOGICAL_HEIGHT, false));
    }

    @Test
    @DisplayName("the overworld is never affected, on or off")
    void overworldIsUntouched() {
        assertEquals(319, NetherRoof.buildMaxY(OVERWORLD, -64, 319, 384, true));
        assertEquals(319, NetherRoof.buildMaxY(OVERWORLD, -64, 319, 384, false));
    }

    @Test
    @DisplayName("the End is never affected either, even though its logical height is lower")
    void endIsUntouched() {
        assertEquals(255, NetherRoof.buildMaxY(END, 0, 255, 256, true));
    }

    @Test
    @DisplayName("a logical height raised past the real height cannot raise the build limit")
    void roofNeverExceedsTheRealHeight() {
        assertEquals(NETHER_MAX_Y,
            NetherRoof.buildMaxY(NETHER, NETHER_MIN_Y, NETHER_MAX_Y, 1024, true));
    }
}
