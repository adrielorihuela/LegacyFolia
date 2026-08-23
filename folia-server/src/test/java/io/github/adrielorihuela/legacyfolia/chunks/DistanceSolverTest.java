package io.github.adrielorihuela.legacyfolia.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules that decide how many chunks a player is given. Getting these wrong either wastes what
 * the feature exists to save or shows somebody less world than they asked for, so each is pinned.
 */
class DistanceSolverTest {

    private static AdaptiveDistanceConfig config(final int viewReduction,
                                                 final int simulationReduction, final int min,
                                                 final int max) {
        return new AdaptiveDistanceConfig(true, viewReduction, simulationReduction, min, max,
            300, 10, true, true, false);
    }

    private static final AdaptiveDistanceConfig DEFAULT = config(4, 4, 2, 32);

    @Test
    @DisplayName("a client asking for less than the server allows is given what it asked for")
    void theClientIsNotGivenMoreThanItWants() {
        final Distances given = DistanceSolver.solve(8, 16, 16, false, false, DEFAULT);

        assertEquals(8, given.view(), "chunks past what a client asked for are thrown away on "
            + "arrival, so sending them is pure waste");
    }

    @Test
    @DisplayName("a client asking for more than the server allows is capped")
    void theServerCapHolds() {
        assertEquals(10, DistanceSolver.solve(32, 10, 10, false, false, DEFAULT).view());
    }

    @Test
    @DisplayName("the unlimited permission lifts the server cap but not the invariant")
    void unlimitedSkipsTheCap() {
        final Distances given = DistanceSolver.solve(24, 10, 10, true, false, DEFAULT);

        assertEquals(24, given.view());
        assertTrue(given.simulation() <= given.view(),
            "simulating past what is visible is the waste this exists to remove");
    }

    @Test
    @DisplayName("simulation never exceeds view, whatever the numbers say")
    void simulationNeverExceedsView() {
        for (int clientView = 2; clientView <= 32; clientView++) {
            for (int serverView = 2; serverView <= 32; serverView += 5) {
                for (int serverSimulation = 2; serverSimulation <= 32; serverSimulation += 5) {
                    for (final boolean reduced : new boolean[] {false, true}) {
                        final Distances given = DistanceSolver.solve(clientView, serverView,
                            serverSimulation, false, reduced, DEFAULT);
                        assertTrue(given.simulation() <= given.view(),
                            "view " + given.view() + " simulation " + given.simulation()
                                + " for client " + clientView + " server " + serverView + "/"
                                + serverSimulation + " reduced=" + reduced);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a player who is away loses the configured reductions")
    void beingAwayCostsTheReduction() {
        final Distances playing = DistanceSolver.solve(16, 16, 16, false, false, DEFAULT);
        final Distances away = DistanceSolver.solve(16, 16, 16, false, true, DEFAULT);

        assertEquals(playing.view() - 4, away.view());
        assertEquals(playing.simulation() - 4, away.simulation());
    }

    @Test
    @DisplayName("the reduction cannot push anybody below the floor")
    void theFloorHolds() {
        final Distances away = DistanceSolver.solve(4, 4, 4, false, true, config(10, 10, 3, 32));

        assertEquals(3, away.view(), "a huge reduction should stop at the floor, not go negative");
        assertEquals(3, away.simulation());
    }

    @Test
    @DisplayName("clamping up to the floor cannot leave simulation above view")
    void clampingCannotBreakTheInvariant() {
        // A floor of 8 with a client that wants 4: both get clamped up, and the invariant has to
        // survive that. This is the case the re-assertion after clamping exists for.
        final Distances given = DistanceSolver.solve(4, 32, 32, false, false, config(0, 0, 8, 32));

        assertTrue(given.simulation() <= given.view());
    }

    @Test
    @DisplayName("packing and unpacking round-trips every value in range")
    void packingRoundTrips() {
        for (int view = 2; view <= 32; view++) {
            for (int simulation = 2; simulation <= view; simulation++) {
                final int packed = new Distances(view, simulation).packed();
                assertEquals(view, Distances.viewOf(packed));
                assertEquals(simulation, Distances.simulationOf(packed));
                assertTrue(packed != Distances.NOTHING_APPLIED,
                    "a real result must never look like 'nothing applied yet'");
            }
        }
    }

    @Test
    @DisplayName("nonsensical bounds are clamped rather than refused")
    void boundsAreClamped() {
        final AdaptiveDistanceConfig silly =
            new AdaptiveDistanceConfig(true, -5, -5, 99, 1, 0, 0, true, true, false);

        assertEquals(32, silly.minDistance(), "clamped to Minecraft's own ceiling");
        assertEquals(32, silly.maxDistance(), "a max below the min is raised to it");
        assertEquals(0, silly.viewReduction(), "a negative reduction would be an increase");
        assertTrue(silly.afkSeconds() >= 1 && silly.checkSeconds() >= 1);
    }
}
