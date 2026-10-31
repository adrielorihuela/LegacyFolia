package io.github.adrielorihuela.legacyfolia.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Upstream's locator bar connection state machine, run against snapshots, checked packet by
 * packet against what {@code ServerWaypointManager} and the three {@code WaypointTransmitter}
 * connection classes would send for the same sequence of events.
 */
class WaypointConnectionsTest {

    private static final double PLAYER_RANGE = 6.0E7;

    private long clock;

    /** Records packets as short strings, in order. */
    private static final class Recorder implements WaypointConnections.Packets {
        private final List<String> sent = new ArrayList<>();

        @Override
        public void addAzimuth(final UUID uuid, final Waypoint.Icon icon, final float angle) {
            this.sent.add("add azimuth " + uuid);
        }

        @Override
        public void addBlock(final UUID uuid, final Waypoint.Icon icon, final BlockPos pos) {
            this.sent.add("add block " + uuid + " " + pos.toShortString());
        }

        @Override
        public void addChunk(final UUID uuid, final Waypoint.Icon icon, final ChunkPos chunk) {
            this.sent.add("add chunk " + uuid + " " + chunk);
        }

        @Override
        public void updateAzimuth(final UUID uuid, final Waypoint.Icon icon, final float angle) {
            this.sent.add("update azimuth " + uuid);
        }

        @Override
        public void updateBlock(final UUID uuid, final Waypoint.Icon icon, final BlockPos pos) {
            this.sent.add("update block " + uuid + " " + pos.toShortString());
        }

        @Override
        public void updateChunk(final UUID uuid, final Waypoint.Icon icon, final ChunkPos chunk) {
            this.sent.add("update chunk " + uuid + " " + chunk);
        }

        @Override
        public void remove(final UUID uuid) {
            this.sent.add("remove " + uuid);
        }

        private List<String> take() {
            final List<String> copy = List.copyOf(this.sent);
            this.sent.clear();
            return copy;
        }
    }

    /** A receiver whose state the test sets directly. */
    private static final class TestReceiver implements WaypointConnections.Receiver {
        private final UUID uuid = UUID.randomUUID();
        private Vec3 position = new Vec3(0.5, 64, 0.5);
        private double receiveRange = PLAYER_RANGE;
        private boolean spectator;
        private final Set<UUID> hidden = new HashSet<>();
        private Predicate<ChunkPos> chunkVisible = chunk -> false;

        @Override
        public UUID uuid() {
            return this.uuid;
        }

        @Override
        public Vec3 position() {
            return this.position;
        }

        @Override
        public double receiveRange() {
            return this.receiveRange;
        }

        @Override
        public boolean spectator() {
            return this.spectator;
        }

        @Override
        public boolean hides(final UUID transmitter) {
            return this.hidden.contains(transmitter);
        }

        @Override
        public boolean chunkVisible(final ChunkPos chunk) {
            return this.chunkVisible.test(chunk);
        }
    }

    /** One level's transmitters, published the way RegionisedWaypoints publishes them. */
    private final class Level {
        private final Map<UUID, WaypointSnapshot> transmitters = new LinkedHashMap<>();

        private WaypointSnapshot.State state(final UUID uuid, final double x, final double z,
                                             final double range, final boolean spectator,
                                             final Set<UUID> riders, final boolean firstTick) {
            final Vec3 at = new Vec3(x, 64, z);
            return new WaypointSnapshot.State(uuid, at, BlockPos.containing(at),
                ChunkPos.containing(BlockPos.containing(at)), null, range, spectator, true,
                riders, firstTick);
        }

        private void publish(final UUID uuid, final double x, final double z,
                             final WaypointSnapshot.Change change) {
            publish(state(uuid, x, z, PLAYER_RANGE, false, Set.of(), false), change);
        }

        private void publish(final WaypointSnapshot.State state,
                             final WaypointSnapshot.Change change) {
            this.transmitters.put(state.uuid(), WaypointSnapshot.next(
                this.transmitters.get(state.uuid()), state, change, ++WaypointConnectionsTest.this.clock));
        }

        private void untrack(final UUID uuid) {
            this.transmitters.remove(uuid);
        }
    }

    private final Recorder packets = new Recorder();

    private List<String> run(final WaypointConnections connections, final TestReceiver receiver,
                             final Level level, final WaypointConnections.Trigger trigger) {
        connections.evaluate(receiver, level.transmitters, trigger, this.packets);
        return this.packets.take();
    }

    @Test
    @DisplayName("only transmitters of the receiver's own level are shown, and a dimension change swaps them")
    void dimensionsAreIsolated() {
        final Level overworld = new Level();
        final Level nether = new Level();
        final UUID inOverworld = UUID.randomUUID();
        final UUID inNether = UUID.randomUUID();
        overworld.publish(inOverworld, 1000, 0, WaypointSnapshot.Change.TRACKED);
        nether.publish(inNether, 1000, 0, WaypointSnapshot.Change.TRACKED);

        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        assertEquals(List.of("add azimuth " + inOverworld),
            run(bar, receiver, overworld, WaypointConnections.Trigger.JOINED));

        // removePlayer in the old level, then addPlayer in the new one.
        bar.disconnectAll(this.packets);
        assertEquals(List.of("remove " + inOverworld), this.packets.take());
        final WaypointConnections netherBar = new WaypointConnections();
        assertEquals(List.of("add azimuth " + inNether),
            run(netherBar, receiver, nether, WaypointConnections.Trigger.JOINED));
        assertNull(netherBar.shown(inOverworld));
    }

    @Test
    @DisplayName("azimuth past 332 blocks, chunk when the chunk is not loaded, block when it is")
    void theThreeKinds() {
        final Level level = new Level();
        final UUID far = UUID.randomUUID();
        final UUID unloaded = UUID.randomUUID();
        final UUID loaded = UUID.randomUUID();
        level.publish(far, 400, 0, WaypointSnapshot.Change.TRACKED);
        level.publish(unloaded, 200, 0, WaypointSnapshot.Change.TRACKED);
        level.publish(loaded, 20, 0, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        receiver.chunkVisible = chunk -> Math.abs(chunk.x()) <= 2;

        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);
        assertEquals(WaypointConnections.Kind.AZIMUTH, bar.shown(far));
        assertEquals(WaypointConnections.Kind.CHUNK, bar.shown(unloaded));
        assertEquals(WaypointConnections.Kind.BLOCK, bar.shown(loaded));
    }

    @Test
    @DisplayName("332 is compared in float, as Entity.distanceTo does")
    void reallyFarIsFloat() {
        final WaypointSnapshot.State at = new Level().state(UUID.randomUUID(), 332.0000001, 0,
            PLAYER_RANGE, false, Set.of(), false);
        final WaypointSnapshot snapshot = WaypointSnapshot.next(null, at,
            WaypointSnapshot.Change.TRACKED, 1);
        // In double this is past 332; in float it rounds to exactly 332, which is not > 332.
        assertEquals(false, snapshot.reallyFarFrom(new Vec3(0, 64, 0)));
        assertTrue(WaypointSnapshot.next(null, new Level().state(UUID.randomUUID(), 332.1, 0,
            PLAYER_RANGE, false, Set.of(), false), WaypointSnapshot.Change.TRACKED, 1)
            .reallyFarFrom(new Vec3(0, 64, 0)));
    }

    @Test
    @DisplayName("nobody is connected during their first tick")
    void firstTickConnectsNobody() {
        final Level level = new Level();
        final UUID joining = UUID.randomUUID();
        level.publish(level.state(joining, 50, 0, PLAYER_RANGE, false, Set.of(), true),
            WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();

        assertEquals(List.of(), run(bar, receiver, level, WaypointConnections.Trigger.JOINED));
        // The first tick ends without a move: a quiet republish. Nothing until somebody moves.
        level.publish(level.state(joining, 50, 0, PLAYER_RANGE, false, Set.of(), false),
            WaypointSnapshot.Change.QUIET);
        assertEquals(List.of(), run(bar, receiver, level, WaypointConnections.Trigger.TICK));
        assertEquals(1, run(bar, receiver, level, WaypointConnections.Trigger.MOVED).size());
    }

    @Test
    @DisplayName("crouching, heads and invisibility (transmit range 0) hide from players but not spectators")
    void zeroRangeHidesExceptFromSpectators() {
        final Level level = new Level();
        final UUID hidden = UUID.randomUUID();
        level.publish(level.state(hidden, 50, 0, 0.0, false, Set.of(), false),
            WaypointSnapshot.Change.TRACKED);
        final TestReceiver player = new TestReceiver();
        final TestReceiver spectator = new TestReceiver();
        spectator.spectator = true;

        assertEquals(List.of(), run(new WaypointConnections(), player, level,
            WaypointConnections.Trigger.JOINED));
        assertEquals(1, run(new WaypointConnections(), spectator, level,
            WaypointConnections.Trigger.JOINED).size());
    }

    @Test
    @DisplayName("receive range, spectating transmitters, riders and plugin hiding are all ignored")
    void ignoreRules() {
        final Level level = new Level();
        final TestReceiver receiver = new TestReceiver();
        final UUID spectating = UUID.randomUUID();
        final UUID carrying = UUID.randomUUID();
        final UUID pluginHidden = UUID.randomUUID();
        final UUID beyond = UUID.randomUUID();
        level.publish(level.state(spectating, 50, 0, PLAYER_RANGE, true, Set.of(), false),
            WaypointSnapshot.Change.TRACKED);
        level.publish(level.state(carrying, 0, 0, PLAYER_RANGE, false, Set.of(receiver.uuid), false),
            WaypointSnapshot.Change.TRACKED);
        level.publish(pluginHidden, 50, 0, WaypointSnapshot.Change.TRACKED);
        level.publish(beyond, 150, 0, WaypointSnapshot.Change.TRACKED);
        receiver.hidden.add(pluginHidden);
        receiver.receiveRange = 100;

        assertEquals(List.of(), run(new WaypointConnections(), receiver, level,
            WaypointConnections.Trigger.JOINED));

        // A spectating receiver sees past range and sees spectators, but not past a plugin's hide.
        receiver.spectator = true;
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);
        assertEquals(3, bar.size());
        assertNull(bar.shown(pluginHidden));
    }

    @Test
    @DisplayName("a block waypoint follows one-block steps and is re-added after a jump")
    void blockConnectionBreaksOnAJump() {
        final Level level = new Level();
        final UUID walker = UUID.randomUUID();
        level.publish(walker, 10.5, 0.5, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        receiver.chunkVisible = chunk -> true;
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);

        level.publish(walker, 11.5, 0.5, WaypointSnapshot.Change.MOVED);
        assertEquals(List.of("update block " + walker + " 11, 64, 0"),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));

        // Two one-block steps before the receiver looks: upstream saw two updates, not a break.
        level.publish(walker, 12.5, 0.5, WaypointSnapshot.Change.MOVED);
        level.publish(walker, 13.5, 0.5, WaypointSnapshot.Change.MOVED);
        assertEquals(List.of("update block " + walker + " 13, 64, 0"),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));

        // A teleport of three blocks breaks the connection: a fresh add.
        level.publish(walker, 16.5, 0.5, WaypointSnapshot.Change.MOVED);
        assertEquals(List.of("add block " + walker + " 16, 64, 0"),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));
    }

    @Test
    @DisplayName("a block waypoint stays a block waypoint while it is walked out of range, as upstream's does")
    void blockConnectionIsSticky() {
        final Level level = new Level();
        final UUID walker = UUID.randomUUID();
        level.publish(walker, 330.5, 0.5, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        receiver.chunkVisible = chunk -> true;
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);
        assertEquals(WaypointConnections.Kind.BLOCK, bar.shown(walker));

        for (int x = 331; x < 340; x++) {
            level.publish(walker, x + 0.5, 0.5, WaypointSnapshot.Change.MOVED);
            run(bar, receiver, level, WaypointConnections.Trigger.TICK);
        }
        assertEquals(WaypointConnections.Kind.BLOCK, bar.shown(walker),
            "isBroken for a block connection only looks at the step and the ignore rules");
    }

    @Test
    @DisplayName("a chunk waypoint becomes a block waypoint once its chunk reaches the client")
    void chunkBecomesBlockWhenVisible() {
        final Level level = new Level();
        final UUID other = UUID.randomUUID();
        level.publish(other, 100.5, 0.5, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);
        assertEquals(WaypointConnections.Kind.CHUNK, bar.shown(other));

        receiver.chunkVisible = chunk -> true;
        receiver.position = new Vec3(1.5, 64, 0.5);
        assertEquals(List.of("add block " + other + " 100, 64, 0"),
            run(bar, receiver, level, WaypointConnections.Trigger.MOVED));
    }

    @Test
    @DisplayName("an azimuth is only resent past half a degree")
    void azimuthThreshold() {
        final Level level = new Level();
        final UUID far = UUID.randomUUID();
        level.publish(far, 1000, 0, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);

        receiver.position = new Vec3(0.5, 64, 1.5); // well under half a degree at 1000 blocks
        assertEquals(List.of(), run(bar, receiver, level, WaypointConnections.Trigger.MOVED));
        receiver.position = new Vec3(0.5, 64, 50);
        assertEquals(List.of("update azimuth " + far),
            run(bar, receiver, level, WaypointConnections.Trigger.MOVED));
    }

    @Test
    @DisplayName("untrack removes; untrack and track re-adds after a remove; track alone re-adds")
    void trackingChanges() {
        final Level level = new Level();
        final UUID other = UUID.randomUUID();
        level.publish(other, 1000, 0, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);

        // Team change (remakeConnections) or a second trackWaypoint: a fresh add, no remove.
        level.publish(other, 1000, 0, WaypointSnapshot.Change.REMADE);
        assertEquals(List.of("add azimuth " + other),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));

        // /waypoint modify: untrack then track.
        level.untrack(other);
        level.publish(other, 1000, 0, WaypointSnapshot.Change.TRACKED);
        assertEquals(List.of("remove " + other, "add azimuth " + other),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));

        // Disconnect, death removal or a dimension change of the transmitter.
        level.untrack(other);
        assertEquals(List.of("remove " + other),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));
        assertEquals(0, bar.size());
    }

    @Test
    @DisplayName("a tick with nothing new sends nothing")
    void idleTickIsSilent() {
        final Level level = new Level();
        level.publish(UUID.randomUUID(), 1000, 0, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);

        assertEquals(List.of(), run(bar, receiver, level, WaypointConnections.Trigger.TICK));
    }

    @Test
    @DisplayName("a receiver never gets a waypoint for itself")
    void notSelf() {
        final Level level = new Level();
        final TestReceiver receiver = new TestReceiver();
        level.publish(receiver.uuid, 1000, 0, WaypointSnapshot.Change.TRACKED);

        assertEquals(List.of(), run(new WaypointConnections(), receiver, level,
            WaypointConnections.Trigger.JOINED));
    }

    @Test
    @DisplayName("crouching mid-session removes the marker, standing up brings it back")
    void crouchAndStand() {
        final Level level = new Level();
        final UUID other = UUID.randomUUID();
        level.publish(other, 1000, 0, WaypointSnapshot.Change.TRACKED);
        final TestReceiver receiver = new TestReceiver();
        final WaypointConnections bar = new WaypointConnections();
        run(bar, receiver, level, WaypointConnections.Trigger.JOINED);

        level.untrack(other); // the crouch modifier takes transmit range to 0
        assertEquals(List.of("remove " + other),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));
        level.publish(other, 1000, 0, WaypointSnapshot.Change.TRACKED);
        assertEquals(List.of("add azimuth " + other),
            run(bar, receiver, level, WaypointConnections.Trigger.TICK));
    }
}
