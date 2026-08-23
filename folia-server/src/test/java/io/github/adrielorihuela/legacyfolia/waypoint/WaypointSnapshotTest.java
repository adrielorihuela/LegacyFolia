package io.github.adrielorihuela.legacyfolia.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The snapshot is the whole safety argument for the locator bar under regionised threading, so
 * what it promises is pinned down here: that it is immutable once taken, and that the range
 * decisions made from it match the ones upstream makes from a live entity.
 */
class WaypointSnapshotTest {

    /** The receiver, at the same height as the helper below puts the transmitter. */
    private static final Vec3 RECEIVER = new Vec3(0, 64, 0);

    private static WaypointSnapshot at(final double x, final double z, final double transmitRange,
                                       final Set<UUID> riders) {
        return new WaypointSnapshot(UUID.randomUUID(), new Vec3(x, 64, z),
            BlockPos.containing(x, 64, z), new ChunkPos((int) x >> 4, (int) z >> 4),
            null, transmitRange, false, true, riders);
    }

    @Test
    @DisplayName("the rider set is copied, so the owning thread cannot change it afterwards")
    void ridersAreCopiedAtCaptureTime() {
        final Set<UUID> mutable = new HashSet<>();
        final UUID rider = UUID.randomUUID();
        mutable.add(rider);

        final WaypointSnapshot snapshot = at(0, 0, 1000, mutable);
        mutable.clear();
        mutable.add(UUID.randomUUID());

        assertEquals(Set.of(rider), snapshot.riders(),
            "a receiver reading this must not see the owning thread's later edits");
    }

    @Test
    @DisplayName("the copied rider set cannot be modified through the snapshot either")
    void ridersAreUnmodifiable() {
        final WaypointSnapshot snapshot = at(0, 0, 1000, Set.of(UUID.randomUUID()));

        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.riders().add(UUID.randomUUID()));
    }

    @Test
    @DisplayName("range uses the smaller of what one will send and the other will receive")
    void rangeIsTheSmallerOfTheTwo() {
        final WaypointSnapshot shy = at(100, 0, 50, Set.of());

        assertTrue(shy.outOfRange(RECEIVER, 1000),
            "a transmitter unwilling to be seen that far should not be");
        assertTrue(at(100, 0, 1000, Set.of()).outOfRange(RECEIVER, 50),
            "a receiver unwilling to look that far should not see it");
        assertFalse(at(10, 0, 1000, Set.of()).outOfRange(RECEIVER, 1000));
    }

    @Test
    @DisplayName("range is exclusive at the boundary, as upstream's comparison is")
    void theBoundaryIsExclusive() {
        assertTrue(at(100, 0, 100, Set.of()).outOfRange(RECEIVER, 100),
            "exactly at the range counts as out, matching the >= upstream uses");
    }

    @Test
    @DisplayName("the direction-only threshold is 332 blocks")
    void reallyFarIsThreeHundredAndThirtyTwo() {
        assertFalse(at(300, 0, 1000, Set.of()).reallyFarFrom(RECEIVER));
        assertTrue(at(400, 0, 1000, Set.of()).reallyFarFrom(RECEIVER));
        assertFalse(at(332, 0, 1000, Set.of()).reallyFarFrom(RECEIVER),
            "exactly 332 is not yet really far, matching upstream's strict >");
    }

    @Test
    @DisplayName("distance is measured in three dimensions, not along the ground")
    void heightCounts() {
        final WaypointSnapshot high = new WaypointSnapshot(UUID.randomUUID(),
            new Vec3(0, 400, 0), BlockPos.ZERO, new ChunkPos(0, 0), null, 1000, false, true,
            Set.of());

        assertTrue(high.reallyFarFrom(RECEIVER),
            "somebody 400 blocks straight up is as far away as one 400 blocks sideways");
    }
}
