package io.github.adrielorihuela.legacyfolia.waypoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import org.jspecify.annotations.Nullable;

/**
 * One receiver's locator bar: upstream's connection state machine, run against snapshots.
 *
 * <p>This is {@code ServerWaypointManager.createConnection} / {@code updateConnection} and the
 * three {@code WaypointTransmitter} connection classes, one row of upstream's connection table at
 * a time. Every decision — which of the three kinds of waypoint to send, when a connection counts
 * as broken, when an update is worth sending — is upstream's, with the same comparisons and the
 * same thresholds. The only difference is where the transmitter's state comes from: an immutable
 * {@link WaypointSnapshot} instead of the live entity.</p>
 *
 * <p>Not thread-safe, on purpose: an instance belongs to its receiver and is only touched on the
 * receiver's own thread, which is also the only thread that sends it waypoint packets.</p>
 */
public final class WaypointConnections {

    /** Upstream's threshold for resending an azimuth: half a degree. */
    public static final float ANGLE_THRESHOLD = 0.008726646F;

    /** The receiver, as the state machine needs to see it. Read on the receiver's own thread. */
    public interface Receiver {
        UUID uuid();

        Vec3 position();

        double receiveRange();

        boolean spectator();

        /** Whether a plugin has inverted this transmitter's visibility for the receiver. */
        boolean hides(UUID transmitter);

        /** Whether the receiver's client has this chunk ({@code WaypointTransmitter.isChunkVisible}). */
        boolean chunkVisible(ChunkPos chunk);
    }

    /** Where the packets go. In the server this is {@code ClientboundTrackedWaypointPacket}. */
    public interface Packets {
        void addAzimuth(UUID uuid, Waypoint.@Nullable Icon icon, float angle);

        void addBlock(UUID uuid, Waypoint.@Nullable Icon icon, BlockPos pos);

        void addChunk(UUID uuid, Waypoint.@Nullable Icon icon, ChunkPos chunk);

        void updateAzimuth(UUID uuid, Waypoint.@Nullable Icon icon, float angle);

        void updateBlock(UUID uuid, Waypoint.@Nullable Icon icon, BlockPos pos);

        void updateChunk(UUID uuid, Waypoint.@Nullable Icon icon, ChunkPos chunk);

        void remove(UUID uuid);
    }

    /** What started an evaluation, which decides between upstream's create and update. */
    public enum Trigger {
        /** {@code addPlayer}: a fresh connection to every transmitter. */
        JOINED,
        /** {@code updatePlayer}: the receiver moved; update what exists, create the rest. */
        MOVED,
        /** A tick: only transmitters that changed since the receiver last looked. */
        TICK
    }

    /** The three connection kinds upstream has. */
    public enum Kind {
        AZIMUTH, BLOCK, CHUNK
    }

    /** One live connection: what upstream's connection object holds. */
    private static final class Connection {
        private final Kind kind;
        private final Waypoint.@Nullable Icon icon;
        private float lastAngle;
        private BlockPos lastBlock;
        private ChunkPos lastChunk;

        private Connection(final Kind kind, final Waypoint.@Nullable Icon icon, final float angle,
                           final BlockPos block, final ChunkPos chunk) {
            this.kind = kind;
            this.icon = icon;
            this.lastAngle = angle;
            this.lastBlock = block;
            this.lastChunk = chunk;
        }
    }

    private final Map<UUID, Connection> connections = new HashMap<>();

    /** The snapshot version each transmitter had when this receiver last evaluated it. */
    private final Map<UUID, Long> seen = new HashMap<>();

    /** Which kind of waypoint the client currently has for a transmitter, or {@code null}. */
    public @Nullable Kind shown(final UUID transmitter) {
        final Connection connection = this.connections.get(transmitter);
        return connection == null ? null : connection.kind;
    }

    /** How many waypoints the client currently has from this receiver's connections. */
    public int size() {
        return this.connections.size();
    }

    /**
     * Brings this receiver up to date with the level's transmitters.
     *
     * @param receiver the receiver, read on its own thread
     * @param transmitters every tracked transmitter in the receiver's level
     * @param trigger what started this
     * @param packets where to send the result
     */
    public void evaluate(final Receiver receiver, final Map<UUID, WaypointSnapshot> transmitters,
                         final Trigger trigger, final Packets packets) {
        // Untracked transmitters first: upstream's untrackWaypoint disconnects them from everyone.
        final Iterator<Map.Entry<UUID, Connection>> shown = this.connections.entrySet().iterator();
        while (shown.hasNext()) {
            final UUID uuid = shown.next().getKey();
            if (!transmitters.containsKey(uuid)) {
                shown.remove();
                packets.remove(uuid);
            }
        }
        this.seen.keySet().retainAll(transmitters.keySet());

        for (final WaypointSnapshot snapshot : transmitters.values()) {
            final UUID uuid = snapshot.uuid();
            if (uuid.equals(receiver.uuid())) {
                continue;
            }
            final long seenVersion = this.seen.getOrDefault(uuid, Long.MIN_VALUE);
            if (trigger == Trigger.TICK && seenVersion == snapshot.version()) {
                continue;
            }
            this.seen.put(uuid, snapshot.version());

            if (snapshot.trackedSince() > seenVersion && this.connections.containsKey(uuid)) {
                // Untracked and tracked again since we last looked: upstream disconnected us in
                // between, and the client was told.
                this.connections.remove(uuid);
                packets.remove(uuid);
            }
            final Connection existing = this.connections.get(uuid);
            if (trigger == Trigger.JOINED || snapshot.epoch() > seenVersion || existing == null) {
                create(receiver, snapshot, packets);
            } else {
                update(receiver, snapshot, existing, seenVersion, packets);
            }
        }
    }

    /**
     * Disconnects everything, telling the client: upstream's {@code breakAllConnections}, and the
     * receiver's half of {@code removePlayer}.
     */
    public void disconnectAll(final Packets packets) {
        for (final UUID uuid : this.connections.keySet()) {
            packets.remove(uuid);
        }
        this.connections.clear();
        this.seen.clear();
    }

    /** {@code ServerWaypointManager.createConnection}. */
    private void create(final Receiver receiver, final WaypointSnapshot snapshot,
                        final Packets packets) {
        final Connection connection = make(receiver, snapshot);
        if (connection != null) {
            this.connections.put(snapshot.uuid(), connection);
            connect(snapshot.uuid(), connection, packets);
        } else if (this.connections.remove(snapshot.uuid()) != null) {
            packets.remove(snapshot.uuid());
        }
    }

    /** {@code ServerWaypointManager.updateConnection}. */
    private void update(final Receiver receiver, final WaypointSnapshot snapshot,
                        final Connection connection, final long seenVersion,
                        final Packets packets) {
        if (!isBroken(receiver, snapshot, connection, seenVersion)) {
            refresh(receiver, snapshot, connection, packets);
            return;
        }
        final Connection replacement = make(receiver, snapshot);
        if (replacement != null) {
            connect(snapshot.uuid(), replacement, packets);
            this.connections.put(snapshot.uuid(), replacement);
        } else {
            packets.remove(snapshot.uuid());
            this.connections.remove(snapshot.uuid());
        }
    }

    /** {@code LivingEntity.makeWaypointConnectionWith}. */
    private static @Nullable Connection make(final Receiver receiver,
                                             final WaypointSnapshot snapshot) {
        if (snapshot.firstTick() || ignores(snapshot, receiver)) {
            return null;
        }
        final Waypoint.Icon icon = snapshot.icon();
        if (snapshot.reallyFarFrom(receiver.position())) {
            return new Connection(Kind.AZIMUTH, icon, angle(receiver, snapshot), snapshot.blockPos(),
                snapshot.chunkPos());
        }
        return !receiver.chunkVisible(snapshot.chunkPos())
            ? new Connection(Kind.CHUNK, icon, 0F, snapshot.blockPos(), snapshot.chunkPos())
            : new Connection(Kind.BLOCK, icon, 0F, snapshot.blockPos(), snapshot.chunkPos());
    }

    /** Each connection class's {@code connect()}. */
    private static void connect(final UUID uuid, final Connection connection,
                                final Packets packets) {
        switch (connection.kind) {
            case AZIMUTH -> packets.addAzimuth(uuid, connection.icon, connection.lastAngle);
            case BLOCK -> packets.addBlock(uuid, connection.icon, connection.lastBlock);
            case CHUNK -> packets.addChunk(uuid, connection.icon, connection.lastChunk);
        }
    }

    /**
     * Each connection class's {@code isBroken()}.
     *
     * <p>Upstream breaks a block connection when one move takes the transmitter more than one block
     * from where the client last put it, and a chunk connection likewise for one chunk. Upstream
     * hears about every move; a receiver here may catch up on several at once, so the transmitter
     * records the last move that was such a jump and the question becomes whether one happened
     * since this receiver last looked.</p>
     */
    private static boolean isBroken(final Receiver receiver, final WaypointSnapshot snapshot,
                                    final Connection connection, final long seenVersion) {
        return switch (connection.kind) {
            case AZIMUTH -> ignores(snapshot, receiver)
                || receiver.chunkVisible(snapshot.chunkPos())
                || !snapshot.reallyFarFrom(receiver.position());
            case BLOCK -> snapshot.blockJump() > seenVersion
                || ignores(snapshot, receiver);
            case CHUNK -> snapshot.chunkJump() > seenVersion
                || ignores(snapshot, receiver)
                || receiver.chunkVisible(connection.lastChunk);
        };
    }

    /** Each connection class's {@code update()}. */
    private static void refresh(final Receiver receiver, final WaypointSnapshot snapshot,
                                final Connection connection, final Packets packets) {
        switch (connection.kind) {
            case AZIMUTH -> {
                final float angle = angle(receiver, snapshot);
                if (Mth.abs(angle - connection.lastAngle) > ANGLE_THRESHOLD) {
                    packets.updateAzimuth(snapshot.uuid(), connection.icon, angle);
                    connection.lastAngle = angle;
                }
            }
            case BLOCK -> {
                if (snapshot.blockPos().distManhattan(connection.lastBlock) > 0) {
                    packets.updateBlock(snapshot.uuid(), connection.icon, snapshot.blockPos());
                    connection.lastBlock = snapshot.blockPos();
                }
            }
            case CHUNK -> {
                if (snapshot.chunkPos().getChessboardDistance(connection.lastChunk) > 0) {
                    packets.updateChunk(snapshot.uuid(), connection.icon, snapshot.chunkPos());
                    connection.lastChunk = snapshot.chunkPos();
                }
            }
        }
    }

    /**
     * {@code WaypointTransmitter.doesSourceIgnoreReceiver}, with Paper's canSee check first.
     *
     * <p>Paper's check is {@code receiver.canSee(source)}, which is
     * {@code source.isVisibleByDefault() ^ receiver's inverted set contains source}; the first half
     * travels in the snapshot and the second is the receiver's own map.</p>
     */
    static boolean ignores(final WaypointSnapshot snapshot, final Receiver receiver) {
        if (!(snapshot.visibleByDefault() ^ receiver.hides(snapshot.uuid()))) {
            return true;
        }
        if (receiver.spectator()) {
            return false;
        }
        if (!snapshot.spectator() && !snapshot.riders().contains(receiver.uuid())) {
            return snapshot.outOfRange(receiver.position(), receiver.receiveRange());
        }
        return true;
    }

    /** {@code EntityAzimuthConnection}'s angle. */
    static float angle(final Receiver receiver, final WaypointSnapshot snapshot) {
        final Vec3 direction = receiver.position().subtract(snapshot.position()).rotateClockwise90();
        return (float) Mth.atan2(direction.z(), direction.x());
    }
}
