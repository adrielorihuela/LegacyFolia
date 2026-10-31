package io.github.adrielorihuela.legacyfolia.waypoint;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jspecify.annotations.Nullable;

/**
 * One level's locator bar, made to work under regionised threading.
 *
 * <p>Upstream's {@code ServerWaypointManager} drives the bar from whichever thread moved something,
 * reaching into every other player in the level to do it. Folia emptied it out, which is why
 * {@code /gamerule locator_bar true} does nothing there. Here each direction runs on the thread that
 * owns what it touches:</p>
 *
 * <ul>
 *   <li>A transmitter publishes a {@link WaypointSnapshot} of itself, on its own thread, when it
 *       moves, is tracked, or has its connections remade.</li>
 *   <li>A receiver runs upstream's connection state machine ({@link WaypointConnections}) against
 *       those snapshots, on its own thread: when it moves, as upstream's {@code updatePlayer} does,
 *       and on its tick for transmitters that changed since, which is upstream's
 *       {@code updateWaypoint} arriving at most a tick later. Every waypoint packet a player gets
 *       is sent from that player's own thread, so their order is never raced.</li>
 * </ul>
 *
 * <p>One instance per {@code ServerLevel}, as upstream: a receiver only ever sees the transmitters
 * of the level it is in, and leaving a level disconnects it from all of them.</p>
 */
public final class RegionisedWaypoints {

    /** What each tracked transmitter last published. Written by its own thread. */
    private final Map<UUID, WaypointSnapshot> transmitters = new ConcurrentHashMap<>();

    /** Every receiver in this level (upstream's {@code players}), and their connections. */
    private final Map<UUID, Receiver> receivers = new ConcurrentHashMap<>();

    /** Transmitters whose tracking or remake was asked for from another thread. */
    private final Set<UUID> pendingTrack = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRemake = ConcurrentHashMap.newKeySet();

    /** The level's clock: every move, track and remake gets the next value. */
    private final AtomicLong versions = new AtomicLong();

    /** Bumped on every change to {@link #transmitters}, so an idle receiver can skip its tick. */
    private final AtomicLong generation = new AtomicLong();

    /** Bumped by {@code breakAllConnections}. */
    private final AtomicLong breaks = new AtomicLong();

    /** Bumped when the gamerule turns the bar on, which upstream answers with updatePlayer. */
    private final AtomicLong refreshes = new AtomicLong();

    /** Set from the gamerule, so the hot paths do not look it up per player. */
    private volatile boolean enabled;

    /** One receiver: its connections, and what it has caught up with. */
    private static final class Receiver {
        private final ServerPlayer player;
        private final WaypointConnections connections = new WaypointConnections();
        private long generation = -1;
        private long breaks;
        private long refreshes;
        /** An updatePlayer that arrived on another thread, done on the receiver's next tick. */
        private volatile boolean moved;

        private Receiver(final ServerPlayer player, final long breaks, final long refreshes) {
            this.player = player;
            this.breaks = breaks;
            this.refreshes = refreshes;
        }
    }

    public RegionisedWaypoints(final boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether the locator bar is on for this level. */
    public boolean enabled() {
        return this.enabled;
    }

    /**
     * Follows the gamerule. Turning it on makes every receiver re-evaluate on its next tick, which
     * is what the {@code updatePlayer} upstream calls for each player does.
     */
    public void setEnabled(final boolean enabled) {
        final boolean was = this.enabled;
        this.enabled = enabled;
        if (enabled && !was) {
            this.refreshes.incrementAndGet();
        }
    }

    // ---------------------------------------------------------------- transmitters

    /** {@code trackWaypoint}. Captured on the transmitter's thread, or on its next tick. */
    public void track(final LivingEntity source) {
        if (!TickThread.isTickThreadFor(source)) {
            this.pendingTrack.add(source.getUUID());
            return;
        }
        publish(source, WaypointSnapshot.Change.TRACKED);
    }

    /** {@code updateWaypoint}: only for a tracked transmitter, on its own thread (a move). */
    public void moved(final LivingEntity source) {
        if (this.transmitters.containsKey(source.getUUID())) {
            publish(source, WaypointSnapshot.Change.MOVED);
        }
    }

    /** {@code untrackWaypoint}: receivers drop it on their next tick. Safe from any thread. */
    public void untrack(final UUID uuid) {
        this.pendingTrack.remove(uuid);
        if (this.transmitters.remove(uuid) != null) {
            this.generation.incrementAndGet();
        }
    }

    /** {@code remakeConnections}, which the scoreboard calls from whatever thread it is on. */
    public void remake(final LivingEntity source) {
        if (!this.enabled) {
            return;
        }
        if (!TickThread.isTickThreadFor(source)) {
            this.pendingRemake.add(source.getUUID());
            return;
        }
        if (this.transmitters.containsKey(source.getUUID())) {
            publish(source, WaypointSnapshot.Change.REMADE);
        }
    }

    /** Whether a transmitter is tracked in this level, for tests of the wiring and for /waypoint. */
    public boolean tracks(final UUID uuid) {
        return this.transmitters.containsKey(uuid);
    }

    private void publish(final LivingEntity source, final WaypointSnapshot.Change change) {
        final WaypointSnapshot.State state = WaypointSnapshot.State.of(source);
        this.transmitters.compute(source.getUUID(), (uuid, previous) ->
            WaypointSnapshot.next(previous, state, change, this.versions.incrementAndGet()));
        this.generation.incrementAndGet();
    }

    // ---------------------------------------------------------------- receivers

    /** {@code addPlayer}'s receiver half: a fresh connection to every transmitter. */
    public void addReceiver(final ServerPlayer player) {
        final Receiver existing = this.receivers.get(player.getUUID());
        final Receiver receiver;
        if (existing != null && existing.player == player) {
            // Added twice (its receive range changed but stayed above zero): upstream simply
            // creates every connection again over the old ones.
            receiver = existing;
        } else {
            receiver = new Receiver(player, this.breaks.get(), this.refreshes.get());
            final Receiver previous = this.receivers.put(player.getUUID(), receiver);
            if (previous != null) {
                // A stale entry for an older ServerPlayer with this uuid: clear its client's bar.
                previous.connections.disconnectAll(packets(previous.player));
            }
        }
        if (!this.enabled) {
            return;
        }
        if (TickThread.isTickThreadFor(player)) {
            run(receiver, WaypointConnections.Trigger.JOINED);
        } else {
            receiver.moved = true;
        }
    }

    /** {@code updatePlayer}: the receiver moved. */
    public void receiverMoved(final ServerPlayer player) {
        if (!this.enabled) {
            return;
        }
        final Receiver receiver = this.receivers.get(player.getUUID());
        if (receiver == null || receiver.player != player) {
            return;
        }
        if (TickThread.isTickThreadFor(player)) {
            run(receiver, WaypointConnections.Trigger.MOVED);
        } else {
            receiver.moved = true;
        }
    }

    /**
     * {@code removePlayer}'s receiver half: every waypoint on this player's bar is removed, which
     * is what keeps somebody from another dimension off it after a dimension change.
     */
    public void removeReceiver(final ServerPlayer player) {
        final Receiver receiver = this.receivers.get(player.getUUID());
        if (receiver != null && receiver.player == player) {
            this.receivers.remove(player.getUUID(), receiver);
            receiver.connections.disconnectAll(packets(player));
        }
    }

    /** {@code breakAllConnections}: each receiver disconnects everything on its next tick. */
    public void breakAll() {
        this.breaks.incrementAndGet();
    }

    // ---------------------------------------------------------------- the tick

    /**
     * Called once per tick for every living entity in this level, on its own thread.
     *
     * <p>As a transmitter it carries out what was asked of it from other threads, and republishes
     * quietly if anything a receiver reads about it changed without a move (gamemode, mounts,
     * Bukkit visibility, its first tick ending). As a receiver it catches up.</p>
     */
    public void tick(final LivingEntity entity) {
        final UUID uuid = entity.getUUID();
        if (!this.pendingTrack.isEmpty() && this.pendingTrack.remove(uuid)) {
            publish(entity, WaypointSnapshot.Change.TRACKED);
        }
        if (!this.pendingRemake.isEmpty() && this.pendingRemake.remove(uuid)
            && this.transmitters.containsKey(uuid)) {
            publish(entity, WaypointSnapshot.Change.REMADE);
        }
        final WaypointSnapshot snapshot = this.transmitters.get(uuid);
        if (snapshot != null) {
            final WaypointSnapshot.State state = WaypointSnapshot.State.of(entity);
            if (!state.sameAs(snapshot)) {
                this.transmitters.computeIfPresent(uuid, (key, previous) ->
                    WaypointSnapshot.next(previous, state, WaypointSnapshot.Change.QUIET, 0L));
            }
        }

        if (entity instanceof ServerPlayer player) {
            final Receiver receiver = this.receivers.get(uuid);
            if (receiver != null && receiver.player == player) {
                tickReceiver(receiver);
            }
        }
    }

    private void tickReceiver(final Receiver receiver) {
        final long breaks = this.breaks.get();
        if (receiver.breaks != breaks) {
            receiver.breaks = breaks;
            receiver.connections.disconnectAll(packets(receiver.player));
        }
        if (!this.enabled) {
            return;
        }
        final long refreshes = this.refreshes.get();
        if (receiver.refreshes != refreshes || receiver.moved) {
            receiver.refreshes = refreshes;
            receiver.moved = false;
            run(receiver, WaypointConnections.Trigger.MOVED);
        } else if (receiver.generation != this.generation.get()) {
            run(receiver, WaypointConnections.Trigger.TICK);
        }
    }

    private void run(final Receiver receiver, final WaypointConnections.Trigger trigger) {
        receiver.generation = this.generation.get();
        receiver.connections.evaluate(new LiveReceiver(receiver.player), this.transmitters,
            trigger, packets(receiver.player));
    }

    /** The receiver as the state machine sees it, read on the receiver's own thread. */
    private record LiveReceiver(ServerPlayer player) implements WaypointConnections.Receiver {
        @Override
        public UUID uuid() {
            return this.player.getUUID();
        }

        @Override
        public Vec3 position() {
            return this.player.position();
        }

        @Override
        public double receiveRange() {
            return this.player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE);
        }

        @Override
        public boolean spectator() {
            return this.player.isSpectator();
        }

        @Override
        public boolean hides(final UUID transmitter) {
            return ((org.bukkit.craftbukkit.entity.CraftPlayer) this.player.getBukkitEntity())
                .isHiddenEntity(transmitter);
        }

        @Override
        public boolean chunkVisible(final ChunkPos chunk) {
            return WaypointTransmitter.isChunkVisible(chunk, this.player);
        }
    }

    /** {@code ClientboundTrackedWaypointPacket}s on the player's connection. */
    private static WaypointConnections.Packets packets(final ServerPlayer player) {
        return new WaypointConnections.Packets() {
            private void send(final ClientboundTrackedWaypointPacket packet) {
                if (player.connection != null) {
                    player.connection.send(packet);
                }
            }

            @Override
            public void addAzimuth(final UUID uuid, final Waypoint.@Nullable Icon icon, final float angle) {
                send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(uuid, icon, angle));
            }

            @Override
            public void addBlock(final UUID uuid, final Waypoint.@Nullable Icon icon, final BlockPos pos) {
                send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, icon, pos));
            }

            @Override
            public void addChunk(final UUID uuid, final Waypoint.@Nullable Icon icon, final ChunkPos chunk) {
                send(ClientboundTrackedWaypointPacket.addWaypointChunk(uuid, icon, chunk));
            }

            @Override
            public void updateAzimuth(final UUID uuid, final Waypoint.@Nullable Icon icon, final float angle) {
                send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(uuid, icon, angle));
            }

            @Override
            public void updateBlock(final UUID uuid, final Waypoint.@Nullable Icon icon, final BlockPos pos) {
                send(ClientboundTrackedWaypointPacket.updateWaypointPosition(uuid, icon, pos));
            }

            @Override
            public void updateChunk(final UUID uuid, final Waypoint.@Nullable Icon icon, final ChunkPos chunk) {
                send(ClientboundTrackedWaypointPacket.updateWaypointChunk(uuid, icon, chunk));
            }

            @Override
            public void remove(final UUID uuid) {
                send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
            }
        };
    }
}
