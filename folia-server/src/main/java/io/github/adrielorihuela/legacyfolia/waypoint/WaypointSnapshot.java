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
 * these on its own thread as it moves, and receivers read only what is written. Everything is
 * immutable and copied at capture time, so the worst a receiver can see is a value one tick old,
 * which for a direction indicator is not a difference anybody can perceive.</p>
 *
 * @param uuid who this is, and the key the client tracks the waypoint by
 * @param position where they were, for the angle and the distance
 * @param blockPos the same position as blocks, for a close-range waypoint
 * @param chunkPos the chunk they were in, for a mid-range waypoint
 * @param icon the icon and style to draw, already resolved against their team
 * @param transmitRange how far they are willing to be seen from
 * @param spectator whether they were spectating, which stops them transmitting
 * @param visibleByDefault the Bukkit visibility default, half of a receiver's canSee check
 * @param riders the whole passenger tree, so a receiver riding the transmitter can be excluded
 */
public record WaypointSnapshot(UUID uuid, Vec3 position, BlockPos blockPos, ChunkPos chunkPos,
                               Waypoint.Icon icon, double transmitRange, boolean spectator,
                               boolean visibleByDefault, Set<UUID> riders) {

    public WaypointSnapshot {
        riders = Set.copyOf(riders);
    }

    /**
     * Captures a transmitter, on its own thread.
     *
     * <p>Must be called from the thread that ticks {@code source}: everything it reads belongs to
     * that thread, and this is the one place any of it is read.</p>
     *
     * @param source the entity transmitting a waypoint
     * @return the frozen snapshot
     */
    public static WaypointSnapshot of(final LivingEntity source) {
        return new WaypointSnapshot(
            source.getUUID(),
            source.position(),
            source.blockPosition(),
            source.chunkPosition(),
            source.waypointIcon().cloneAndAssignStyle(source),
            source.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE),
            source.isSpectator(),
            source.getBukkitEntity().isVisibleByDefault(),
            ridersOf(source));
    }

    /**
     * Every entity riding this one, however deep.
     *
     * <p>Read here, once, on the owning thread. Upstream asks {@code hasIndirectPassenger} from the
     * receiver's thread instead, which walks a list the owning thread may be restructuring at that
     * moment.</p>
     */
    private static Set<UUID> ridersOf(final Entity source) {
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
     * Whether this transmitter is too far from, or hidden from, a receiver at the given position.
     *
     * @param receiverPosition where the receiver is
     * @param receiveRange how far the receiver is willing to see
     * @return whether nothing should be shown
     */
    public boolean outOfRange(final Vec3 receiverPosition, final double receiveRange) {
        final double range = Math.min(this.transmitRange, receiveRange);
        return this.position.distanceToSqr(receiverPosition) >= range * range;
    }

    /** Whether the receiver is more than 332 blocks away, where only a direction can be shown. */
    public boolean reallyFarFrom(final Vec3 receiverPosition) {
        final double reallyFar = 332.0;
        return this.position.distanceToSqr(receiverPosition) > reallyFar * reallyFar;
    }
}
