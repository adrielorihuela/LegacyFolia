package io.github.adrielorihuela.legacyfolia.waypoint;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.WaypointTransmitter;

/**
 * The locator bar, made to work under regionised threading.
 *
 * <p>Upstream drives this from whichever thread moved something, reaching across into other
 * entities to do it. Folia gutted the four methods that did so, which is why {@code
 * /gamerule locator_bar true} does nothing on it. Here the two directions are separated, and each
 * runs where it already belongs:</p>
 *
 * <ul>
 *   <li>A transmitter that moves writes a {@link WaypointSnapshot} of itself. That is its own
 *       state, on its own thread, and it touches nobody else.</li>
 *   <li>A receiver that moves rebuilds its own connections from those snapshots. That is its own
 *       state, on its own thread, and it touches no other entity.</li>
 * </ul>
 *
 * <p>So nothing is ever read across a region boundary, the packets go out on the receiver's own
 * connection, and the cost of the whole thing is one map write per moving transmitter.</p>
 */
public final class RegionisedWaypoints {

    /** What each transmitter last published. Written by its own thread, read by every receiver. */
    private final Map<UUID, WaypointSnapshot> transmitters = new ConcurrentHashMap<>();

    /**
     * What each receiver is currently showing, and who they are.
     *
     * <p>The player is held alongside the markers because a transmitter going away has to tell
     * every receiver so, and that means reaching their connection from the transmitter's thread.
     * Sending a packet is safe from anywhere; nothing else about the player is touched here.</p>
     */
    private final Map<UUID, Receiver> shown = new ConcurrentHashMap<>();

    /** One receiver and the markers currently on their locator bar. */
    private record Receiver(ServerPlayer player, Map<UUID, Shown> markers) {
    }

    /** Set from the gamerule, so the hot paths do not look it up per player. */
    private volatile boolean enabled;

    public RegionisedWaypoints(final boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether the locator bar is on for this level. */
    public boolean enabled() {
        return this.enabled;
    }

    /**
     * Turns the locator bar on or off, following the gamerule.
     *
     * @param enabled whether it is on
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    /**
     * Publishes where a transmitter is now.
     *
     * <p>Called on the transmitter's own thread, from the same places upstream called
     * {@code trackWaypoint} and {@code updateWaypoint}.</p>
     *
     * @param source the entity transmitting a waypoint
     */
    public void publish(final LivingEntity source) {
        if (!this.enabled) {
            return;
        }
        // Vanilla decides this with an attribute, not a flag: crouching, wearing a head or a carved
        // pumpkin, and invisibility each apply a -100% modifier to WAYPOINT_TRANSMIT_RANGE. Asking
        // the question upstream already answers is what keeps those rules working here, rather than
        // reimplementing a list of them that would drift.
        if (!source.isTransmittingWaypoint()) {
            unpublish(source.getUUID());
            return;
        }
        this.transmitters.put(source.getUUID(), WaypointSnapshot.of(source));
    }

    /**
     * Stops publishing a transmitter, and tells everyone showing it to drop it.
     *
     * @param uuid the transmitter that has gone
     */
    public void unpublish(final UUID uuid) {
        this.transmitters.remove(uuid);

        // The removal has to be pushed, not left for the receivers to notice. refresh() only runs
        // when a receiver moves, so a receiver standing still would never reconcile; and clearing
        // the marker without sending this is what left it frozen on the client forever, whether
        // the transmitter had crouched, put a head on, changed dimension or disconnected.
        for (final Receiver receiver : this.shown.values()) {
            if (receiver.markers().remove(uuid) != null && receiver.player().connection != null) {
                receiver.player().connection.send(
                    ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
            }
        }
    }

    /**
     * Rebuilds what one receiver is being shown.
     *
     * <p>Called on the receiver's own thread, from the same places upstream called
     * {@code updatePlayer}. Reads only immutable snapshots and the receiver's own state, so it
     * crosses no region boundary.</p>
     *
     * @param receiver the player whose locator bar to update
     */
    public void refresh(final ServerPlayer receiver) {
        if (!this.enabled || receiver.connection == null) {
            return;
        }

        // Replaced rather than reused, because a player who reconnects is a different object with
        // a different connection and the old one would send into a closed channel.
        final Map<UUID, Shown> connections = this.shown
            .compute(receiver.getUUID(), (key, existing) -> existing != null
                && existing.player() == receiver ? existing
                : new Receiver(receiver, new ConcurrentHashMap<>()))
            .markers();
        final Vec3 at = receiver.position();
        final double receiveRange = receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE);
        final boolean spectating = receiver.isSpectator();

        for (final WaypointSnapshot snapshot : this.transmitters.values()) {
            if (snapshot.uuid().equals(receiver.getUUID())) {
                continue;
            }
            if (ignores(snapshot, receiver, at, receiveRange, spectating)) {
                hide(receiver, connections, snapshot.uuid());
                continue;
            }
            show(receiver, connections, snapshot, at);
        }

        // Anything still shown that nobody is publishing any more.
        for (final UUID uuid : connections.keySet()) {
            if (!this.transmitters.containsKey(uuid)) {
                hide(receiver, connections, uuid);
            }
        }
    }

    /**
     * Whether a receiver should be shown nothing for this transmitter.
     *
     * <p>The same three questions upstream asks, answered from the snapshot rather than from the
     * transmitter's live entity: is it hidden from this player, is one of them riding the other,
     * and is it further away than either of them is willing to reach.</p>
     */
    private static boolean ignores(final WaypointSnapshot snapshot, final ServerPlayer receiver,
                                   final Vec3 at, final double receiveRange,
                                   final boolean spectating) {
        if (!canSee(receiver, snapshot)) {
            return true;
        }
        if (spectating) {
            // A spectator sees everybody, which is upstream's rule too.
            return false;
        }
        if (snapshot.spectator() || snapshot.riders().contains(receiver.getUUID())) {
            return true;
        }
        return snapshot.outOfRange(at, receiveRange);
    }

    /**
     * The receiver's half of Bukkit's visibility check, done by UUID.
     *
     * <p>{@code CraftPlayer#canSee} is {@code isVisibleByDefault() ^ hidden.containsKey(uuid)}. The
     * first half belongs to the transmitter and travels in the snapshot; the second is a lookup in
     * the receiver's own map. So a plugin hiding a player still hides them from the locator bar,
     * without anybody reaching across a region to ask.</p>
     */
    private static boolean canSee(final ServerPlayer receiver, final WaypointSnapshot snapshot) {
        final org.bukkit.craftbukkit.entity.CraftPlayer bukkit =
            (org.bukkit.craftbukkit.entity.CraftPlayer) receiver.getBukkitEntity();
        return snapshot.visibleByDefault() ^ bukkit.isHiddenEntity(snapshot.uuid());
    }

    /**
     * Half a degree, upstream's threshold for resending a direction.
     *
     * <p>Without it a player running in a circle a thousand blocks away would cost every receiver
     * a packet per tick for a change nobody could see.</p>
     */
    private static final float ANGLE_THRESHOLD = 0.008726646F;

    private static void show(final ServerPlayer receiver, final Map<UUID, Shown> connections,
                             final WaypointSnapshot snapshot, final Vec3 at) {
        final UUID uuid = snapshot.uuid();
        final Shown current = connections.get(uuid);
        final Kind kind = kindFor(receiver, snapshot, at);
        // A change of kind is a different packet, so it is always sent afresh.
        final boolean isNew = current == null || current.kind() != kind;

        switch (kind) {
            case AZIMUTH -> {
                final Vec3 direction = at.subtract(snapshot.position()).rotateClockwise90();
                final float angle =
                    (float) net.minecraft.util.Mth.atan2(direction.z(), direction.x());
                if (isNew) {
                    receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(
                        uuid, snapshot.icon(), angle));
                } else if (Math.abs(angle - current.angle()) > ANGLE_THRESHOLD) {
                    receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(
                        uuid, snapshot.icon(), angle));
                } else {
                    return;
                }
                connections.put(uuid, new Shown(kind, angle, null));
            }
            case CHUNK -> {
                final String where = snapshot.chunkPos().toString();
                if (!isNew && where.equals(current.where())) {
                    return;
                }
                receiver.connection.send(isNew
                    ? ClientboundTrackedWaypointPacket.addWaypointChunk(uuid, snapshot.icon(),
                        snapshot.chunkPos())
                    : ClientboundTrackedWaypointPacket.updateWaypointChunk(uuid, snapshot.icon(),
                        snapshot.chunkPos()));
                connections.put(uuid, new Shown(kind, 0F, where));
            }
            default -> {
                final String where = snapshot.blockPos().toString();
                if (!isNew && where.equals(current.where())) {
                    return;
                }
                receiver.connection.send(isNew
                    ? ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, snapshot.icon(),
                        snapshot.blockPos())
                    : ClientboundTrackedWaypointPacket.updateWaypointPosition(uuid, snapshot.icon(),
                        snapshot.blockPos()));
                connections.put(uuid, new Shown(kind, 0F, where));
            }
        }
    }

    /** Which of the three kinds of waypoint the client should be given for this pair. */
    private static Kind kindFor(final ServerPlayer receiver, final WaypointSnapshot snapshot,
                                final Vec3 at) {
        if (snapshot.reallyFarFrom(at)) {
            return Kind.AZIMUTH;
        }
        return WaypointTransmitter.isChunkVisible(snapshot.chunkPos(), receiver)
            ? Kind.BLOCK : Kind.CHUNK;
    }
    private static void hide(final ServerPlayer receiver, final Map<UUID, Shown> connections,
                             final UUID uuid) {
        if (connections.remove(uuid) != null && receiver.connection != null) {
            receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
        }
    }

    /** Forgets a receiver entirely, when they disconnect or change level. */
    public void removeReceiver(final UUID uuid) {
        this.shown.remove(uuid);
    }

    /** Drops everything, for a gamerule change or a shutdown. */
    public void clear() {
        this.shown.clear();
        this.transmitters.clear();
    }

    /** Which kind of waypoint a receiver is currently being shown for a transmitter. */
    private enum Kind {
        AZIMUTH, CHUNK, BLOCK
    }

    /** What a receiver was last told about one transmitter, to avoid repeating it. */
    private record Shown(Kind kind, float angle, String where) {
    }
}
