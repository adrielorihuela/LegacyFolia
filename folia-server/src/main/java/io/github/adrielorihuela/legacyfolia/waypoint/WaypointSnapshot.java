package io.github.adrielorihuela.legacyfolia.waypoint;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import org.jspecify.annotations.Nullable;

/**
 * Everything a receiver needs to know about one transmitter, frozen at a moment in time.
 *
 * <p>This is what makes the locator bar work under regionised threading. The bar only shows
 * somebody once they are more than 332 blocks away, which on Folia means they are almost always
 * being ticked by a different thread. Upstream's connections read the transmitter's live entity —
 * its position, its attributes, its passenger list — from whichever thread happens to be updating
 * a receiver, and that is a data race with a real crash in it: iterating a passenger list while
 * its owner's thread restructures it. Folia's answer was to switch the whole feature off.</p>
 *
 * <p>The answer here is that no receiver ever touches a transmitter. Each transmitter writes one of
 * these on its own thread, and receivers read only what is written. Everything is immutable and
 * copied at capture time, so the worst a receiver can see is a value one tick old.</p>
 *
 * <p>The four counters say what happened to the transmitter, so that a receiver catching up on
 * several moves at once still reaches the decision upstream reaches by being told about each:</p>
 *
 * @param uuid who this is, and the key the client tracks the waypoint by
 * @param position where they were, for the angle and the distance
 * @param blockPos the same position as blocks, for a close-range waypoint
 * @param chunkPos the chunk they were in, for a mid-range waypoint
 * @param icon the icon and style to draw, already resolved against their team (a private copy)
 * @param transmitRange how far they are willing to be seen from
 * @param spectator whether they were spectating, which stops them transmitting
 * @param visibleByDefault the Bukkit visibility default, half of a receiver's canSee check
 * @param riders the whole passenger tree, so a receiver riding the transmitter can be excluded
 * @param firstTick whether the entity has not finished its first tick, when upstream connects nobody
 * @param version when this transmitter last moved or was (re)tracked; receivers compare it
 * @param epoch when this transmitter was last tracked or had its connections remade, which upstream
 *     answers with a fresh connection to every receiver
 * @param trackedSince when this transmitter was tracked after not being tracked, which upstream
 *     preceded by disconnecting every receiver
 * @param blockJump the last version at which the block position moved more than one block
 *     (Manhattan), the step that breaks a block connection upstream
 * @param chunkJump the last version at which the chunk moved more than one chunk (chessboard), the
 *     step that breaks a chunk connection upstream
 */
public record WaypointSnapshot(UUID uuid, Vec3 position, BlockPos blockPos, ChunkPos chunkPos,
                               Waypoint.@Nullable Icon icon, double transmitRange, boolean spectator,
                               boolean visibleByDefault, Set<UUID> riders, boolean firstTick,
                               long version, long epoch, long trackedSince, long blockJump,
                               long chunkJump) {

    /** Upstream's {@code WaypointTransmitter.REALLY_FAR_DISTANCE}. */
    public static final float REALLY_FAR = 332.0F;

    public WaypointSnapshot {
        riders = Set.copyOf(riders);
    }

    /** What changed about a transmitter since the previous snapshot. */
    public enum Change {
        /** Moved: receivers update or create their connection to it. */
        MOVED,
        /** Tracked: receivers create a fresh connection, as upstream's {@code trackWaypoint}. */
        TRACKED,
        /** Remade: like tracked, for a team change ({@code remakeConnections}). */
        REMADE,
        /** Nothing a receiver is told about; only the live state it reads on its own move. */
        QUIET
    }

    /**
     * The facts about a transmitter, read on its own thread.
     *
     * @param uuid who it is
     * @param position where it is
     * @param blockPos the block it is in
     * @param chunkPos the chunk it is in
     * @param icon the icon a connection made now would carry
     * @param transmitRange its {@code waypoint_transmit_range}
     * @param spectator whether it is spectating
     * @param visibleByDefault the Bukkit visibility default
     * @param riders its passenger tree
     * @param firstTick whether it has not finished its first tick
     */
    public record State(UUID uuid, Vec3 position, BlockPos blockPos, ChunkPos chunkPos,
                        Waypoint.@Nullable Icon icon, double transmitRange, boolean spectator,
                        boolean visibleByDefault, Set<UUID> riders, boolean firstTick) {

        public State {
            riders = Set.copyOf(riders);
        }

        /**
         * Reads a transmitter, on its own thread.
         *
         * <p>Must be called from the thread that ticks {@code source}: everything it reads belongs
         * to that thread, and this is the one place any of it is read.</p>
         *
         * @param source the entity transmitting a waypoint
         * @return its state
         */
        public static State of(final LivingEntity source) {
            final Waypoint.Icon icon = new Waypoint.Icon();
            // cloneAndAssignStyle hands back the entity's own mutable icon when there is nothing to
            // override; a private copy keeps other threads off it.
            icon.copyFrom(source.waypointIcon().cloneAndAssignStyle(source));
            return new State(
                source.getUUID(),
                source.position(),
                source.blockPosition(),
                source.chunkPosition(),
                icon,
                source.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE),
                source.isSpectator(),
                source.getBukkitEntity().isVisibleByDefault(),
                ridersOf(source),
                source.legacyfolia$isFirstTick());
        }

        /** Whether anything a receiver reads differs, ignoring the icon (handled by remakes). */
        public boolean sameAs(final WaypointSnapshot snapshot) {
            return this.position.equals(snapshot.position())
                && this.transmitRange == snapshot.transmitRange()
                && this.spectator == snapshot.spectator()
                && this.visibleByDefault == snapshot.visibleByDefault()
                && this.firstTick == snapshot.firstTick()
                && this.riders.equals(snapshot.riders());
        }
    }

    /**
     * The next snapshot of a transmitter.
     *
     * @param previous what it last published while tracked, or {@code null} if it was not tracked
     * @param state its state now
     * @param change what happened
     * @param version a fresh value of the level's counter, used unless the change is quiet
     * @return the snapshot to publish
     */
    public static WaypointSnapshot next(final @Nullable WaypointSnapshot previous, final State state,
                                        final Change change, final long version) {
        if (change == Change.QUIET && previous != null) {
            return new WaypointSnapshot(state.uuid(), state.position(), state.blockPos(),
                state.chunkPos(), state.icon(), state.transmitRange(), state.spectator(),
                state.visibleByDefault(), state.riders(), state.firstTick(), previous.version(),
                previous.epoch(), previous.trackedSince(), previous.blockJump(),
                previous.chunkJump());
        }
        final boolean fresh = previous == null;
        final long epoch = fresh || change == Change.TRACKED || change == Change.REMADE
            ? version : previous.epoch();
        final long trackedSince = fresh ? version : previous.trackedSince();
        final long blockJump = !fresh && previous.blockPos().distManhattan(state.blockPos()) > 1
            ? version : fresh ? Long.MIN_VALUE : previous.blockJump();
        final long chunkJump = !fresh && previous.chunkPos().getChessboardDistance(state.chunkPos()) > 1
            ? version : fresh ? Long.MIN_VALUE : previous.chunkJump();
        return new WaypointSnapshot(state.uuid(), state.position(), state.blockPos(),
            state.chunkPos(), state.icon(), state.transmitRange(), state.spectator(),
            state.visibleByDefault(), state.riders(), state.firstTick(), version, epoch,
            trackedSince, blockJump, chunkJump);
    }

    /**
     * Every entity riding this one, however deep.
     *
     * <p>Read here, once, on the owning thread. Upstream asks {@code hasIndirectPassenger} from the
     * receiver's thread instead, which walks a list the owning thread may be restructuring at that
     * moment.</p>
     */
    static Set<UUID> ridersOf(final Entity source) {
        if (source.getPassengers().isEmpty()) {
            return Set.of();
        }
        final Set<UUID> riders = new java.util.HashSet<>();
        collectRiders(source, riders);
        return riders;
    }

    private static void collectRiders(final Entity entity, final Set<UUID> into) {
        for (final Entity passenger : entity.getPassengers()) {
            if (into.add(passenger.getUUID())) {
                collectRiders(passenger, into);
            }
        }
    }

    /**
     * Whether this transmitter is out of reach of a receiver at the given position.
     *
     * <p>Upstream: {@code source.distanceToSqr(receiver) >= Mth.square(min(transmit, receive))}.</p>
     *
     * @param receiverPosition where the receiver is
     * @param receiveRange how far the receiver is willing to see
     * @return whether nothing should be shown
     */
    public boolean outOfRange(final Vec3 receiverPosition, final double receiveRange) {
        final double range = Math.min(this.transmitRange, receiveRange);
        final double dx = this.position.x - receiverPosition.x;
        final double dy = this.position.y - receiverPosition.y;
        final double dz = this.position.z - receiverPosition.z;
        return dx * dx + dy * dy + dz * dz >= range * range;
    }

    /**
     * Whether the receiver is more than 332 blocks away, where only a direction is shown.
     *
     * <p>Upstream: {@code source.distanceTo(receiver) > 332.0F}, which {@code Entity.distanceTo}
     * works out in {@code float}; done the same way here so the boundary falls on the same
     * block.</p>
     */
    public boolean reallyFarFrom(final Vec3 receiverPosition) {
        final float xd = (float) (this.position.x - receiverPosition.x);
        final float yd = (float) (this.position.y - receiverPosition.y);
        final float zd = (float) (this.position.z - receiverPosition.z);
        return (float) Math.sqrt(xd * xd + yd * yd + zd * zd) > REALLY_FAR;
    }
}
