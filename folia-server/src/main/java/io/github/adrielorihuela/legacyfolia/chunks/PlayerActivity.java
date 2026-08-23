package io.github.adrielorihuela.legacyfolia.chunks;

import org.jspecify.annotations.Nullable;

/**
 * Whether one player is really playing, or just leaving the game running.
 *
 * <p>A timer alone is not enough, because the point of an anti-AFK macro is to defeat one. So a
 * deliberate action — breaking a block, opening a chest, typing — resets the clock outright, and
 * movement is only believed when it looks like a person moved. Walking a four-block circuit,
 * turning at a perfectly even rate, or swinging with no jitter between clicks all keep a player
 * technically active while nobody is at the keyboard, and all three are recognised here.</p>
 *
 * <p>Everything in here belongs to one player, and all of it but one method is only ever touched
 * from that player's own thread: the packets that feed it are handled there, and the check that
 * reads it runs there. That is what lets it use plain fields and a fixed ring rather than anything
 * synchronised. The exception is chat, which Paper handles on a netty thread, so that path is
 * confined to two volatile fields and is marked as such.
 */
public final class PlayerActivity {

    /** Fifteen seconds of history at four samples a second. */
    private static final int SAMPLES = 60;
    private static final long SAMPLE_INTERVAL_MILLIS = 250L;

    /** How small a box the whole history has to fit in to count as standing still. */
    private static final double CONFINEMENT_BLOCKS = 4.0;

    /** How few distinct blocks the whole history may touch before it looks like a circuit. */
    private static final int LOOP_DISTINCT_BLOCKS = 8;

    /** How little the turn rate may vary before it stops looking like a hand. */
    private static final double ROTATION_VARIATION_DEGREES = 0.75;

    /** How little the gap between swings may vary before it stops looking like a finger. */
    private static final double SWING_JITTER_MILLIS = 12.0;

    private static final int SWINGS = 16;

    /** One moment of where the player was and which way they faced. */
    private record Sample(double x, double y, double z, float yaw, float pitch, long at) {
    }

    private final Sample[] samples = new Sample[SAMPLES];
    private int written;
    private int cursor;
    private long lastSampleAt;

    private final long[] swings = new long[SWINGS];
    private int swingsWritten;
    private int swingCursor;

    // Volatile because chat arrives on a netty thread rather than the player's own. A single long
    // write is safe from anywhere; nothing else in here is, which is why chat only touches this.
    private volatile long lastHardActionAt = System.currentTimeMillis();
    private volatile @Nullable String lastChat;

    /** Set by a client that tells us it is minimised or paused, if one ever does. */
    private boolean focused = true;
    private boolean paused;

    /**
     * Records something only a person does on purpose.
     *
     * <p>Breaking or placing a block, using an item, opening a container, typing a command. These
     * reset the clock outright, with no heuristic applied: there is no macro question to ask.</p>
     *
     * @param now the current time in milliseconds
     */
    public void recordDeliberateAction(final long now) {
        this.lastHardActionAt = now;
        // A real action also clears the movement history, so a macro cannot bank a suspicious
        // pattern and then have it held against the player once they come back.
        this.written = 0;
        this.cursor = 0;
        this.swingsWritten = 0;
        this.swingCursor = 0;
    }

    /**
     * Records a line of chat, which counts as deliberate unless it is the same line again.
     *
     * <p>Safe to call from any thread, and the only method here that is. Chat is handled on a netty
     * thread rather than the player's own, so this touches nothing but the two volatile fields:
     * clearing the movement history from over there would race with the thread writing it.</p>
     *
     * @param message what was said
     * @param now the current time in milliseconds
     */
    public void recordChatFromAnyThread(final String message, final long now) {
        if (message.equals(this.lastChat)) {
            return;
        }
        this.lastChat = message;
        this.lastHardActionAt = now;
    }

    /**
     * Records where the player is, at most four times a second.
     *
     * @param x the position
     * @param y the position
     * @param z the position
     * @param yaw where they are facing
     * @param pitch where they are facing
     * @param now the current time in milliseconds
     */
    public void recordMovement(final double x, final double y, final double z, final float yaw,
                               final float pitch, final long now) {
        if (now - this.lastSampleAt < SAMPLE_INTERVAL_MILLIS) {
            return;
        }
        this.lastSampleAt = now;
        this.samples[this.cursor] = new Sample(x, y, z, yaw, pitch, now);
        this.cursor = (this.cursor + 1) % SAMPLES;
        if (this.written < SAMPLES) {
            this.written++;
        }
    }

    /**
     * Records an arm swing, for the auto-clicker test.
     *
     * @param now the current time in milliseconds
     */
    public void recordSwing(final long now) {
        this.swings[this.swingCursor] = now;
        this.swingCursor = (this.swingCursor + 1) % SWINGS;
        if (this.swingsWritten < SWINGS) {
            this.swingsWritten++;
        }
    }

    /**
     * Records what a client told us about its own window.
     *
     * @param focused whether the game window has focus
     * @param paused whether the game is paused
     */
    public void recordClientState(final boolean focused, final boolean paused) {
        this.focused = focused;
        this.paused = paused;
    }

    /**
     * Why this player is not really playing, or null if they are.
     *
     * @param config which tests are enabled and how long counts as away
     * @param now the current time in milliseconds
     * @return a short reason for the log, or {@code null}
     */
    public @Nullable String notPlayingReason(final AdaptiveDistanceConfig config, final long now) {
        if (this.paused) {
            return "the game is paused";
        }
        if (!this.focused) {
            return "the window is not focused";
        }
        if (now - this.lastHardActionAt < config.afkMillis()) {
            return null;
        }
        // Past the timer, so the only thing keeping them active is movement or clicking. Both get
        // examined for whether a person is actually producing them.
        if (config.detectAutoClicker() && isAutoClicker()) {
            return "clicking with no human jitter";
        }
        if (config.detectAntiAfk() && isMechanicalMovement()) {
            return "moving mechanically";
        }
        if (this.written == 0) {
            return "away";
        }
        return null;
    }

    /** Movement that a macro produces and a person does not. */
    private boolean isMechanicalMovement() {
        if (this.written < SAMPLES) {
            return false;
        }
        return isConfined() || isLooping() || isRotationMechanical();
    }

    /** Every recent position fits in a small box: jumping or spinning on the spot. */
    private boolean isConfined() {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < this.written; i++) {
            final Sample sample = at(i);
            minX = Math.min(minX, sample.x());
            maxX = Math.max(maxX, sample.x());
            minZ = Math.min(minZ, sample.z());
            maxZ = Math.max(maxZ, sample.z());
        }
        return maxX - minX <= CONFINEMENT_BLOCKS && maxZ - minZ <= CONFINEMENT_BLOCKS;
    }

    /**
     * The camera turns by a near-identical amount every sample.
     *
     * <p>A person's aim wanders; a spinner does not. A camera that is not moving at all is not
     * mechanical, it is just somebody standing still, which the timer already covers.</p>
     */
    private boolean isRotationMechanical() {
        final int count = this.written - 1;
        if (count < 2) {
            return false;
        }
        final double[] deltas = new double[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            final Sample from = at(i);
            final Sample to = at(i + 1);
            deltas[i] = Math.abs(angleDelta(from.yaw(), to.yaw()))
                + Math.abs(to.pitch() - from.pitch());
            sum += deltas[i];
        }
        final double mean = sum / count;
        if (mean < 1.0) {
            return false;
        }
        double variance = 0;
        for (final double delta : deltas) {
            variance += (delta - mean) * (delta - mean);
        }
        return Math.sqrt(variance / count) < ROTATION_VARIATION_DEGREES;
    }

    private static double angleDelta(final float from, final float to) {
        double delta = (to - from) % 360.0;
        if (delta > 180.0) {
            delta -= 360.0;
        }
        if (delta < -180.0) {
            delta += 360.0;
        }
        return delta;
    }

    /** The whole history only ever touches a handful of blocks: a tight circuit. */
    private boolean isLooping() {
        final long[] visited = new long[this.written];
        int distinct = 0;
        for (int i = 0; i < this.written; i++) {
            final Sample sample = at(i);
            final long key = ((long) (int) Math.floor(sample.x()) << 32)
                ^ ((int) Math.floor(sample.z()) & 0xFFFFFFFFL);
            boolean seen = false;
            for (int j = 0; j < distinct; j++) {
                if (visited[j] == key) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                visited[distinct++] = key;
                if (distinct > LOOP_DISTINCT_BLOCKS) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Swings arriving with almost no jitter, which no hand produces. */
    private boolean isAutoClicker() {
        if (this.swingsWritten < SWINGS) {
            return false;
        }
        final long[] ordered = new long[SWINGS];
        for (int i = 0; i < SWINGS; i++) {
            ordered[i] = this.swings[(this.swingCursor + i) % SWINGS];
        }
        double sum = 0;
        for (int i = 0; i < SWINGS - 1; i++) {
            sum += ordered[i + 1] - ordered[i];
        }
        final double mean = sum / (SWINGS - 1);
        if (mean <= 0) {
            return false;
        }
        double variance = 0;
        for (int i = 0; i < SWINGS - 1; i++) {
            final double gap = ordered[i + 1] - ordered[i];
            variance += (gap - mean) * (gap - mean);
        }
        return Math.sqrt(variance / (SWINGS - 1)) < SWING_JITTER_MILLIS;
    }

    /** The i-th sample in the order they happened. */
    private Sample at(final int chronological) {
        final int oldest = this.written < SAMPLES ? 0 : this.cursor;
        return this.samples[(oldest + chronological) % SAMPLES];
    }
}
