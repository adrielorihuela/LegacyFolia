package io.github.adrielorihuela.legacyfolia.chunks;

/**
 * A view distance and a simulation distance, in chunks.
 *
 * @param view how far the player is sent chunks
 * @param simulation how far around the player the world ticks
 */
public record Distances(int view, int simulation) {

    /** Packed into one int, so a check that almost always finds nothing changed allocates nothing. */
    public int packed() {
        return (this.view << 8) | this.simulation;
    }

    /** A packed value that can never equal a real result, for "nothing applied yet". */
    public static final int NOTHING_APPLIED = -1;

    /** The view distance out of a packed result. */
    public static int viewOf(final int packed) {
        return packed >>> 8;
    }

    /** The simulation distance out of a packed result. */
    public static int simulationOf(final int packed) {
        return packed & 0xFF;
    }
}
