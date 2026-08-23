package io.github.adrielorihuela.legacyfolia.chunks;

/**
 * Decides what view and simulation distance a player should be given.
 *
 * <p>Kept free of anything server-shaped so the rules can be checked without one. They are, in
 * order:</p>
 *
 * <ol>
 *   <li>The view distance is the smaller of what the client asked for and what the server allows.
 *       A player with the unlimited permission skips the server cap and gets what they asked for.
 *   <li>The simulation distance is the smaller of the server's value and the client's render
 *       distance. There is no client-provided simulation distance in any version of the game.
 *   <li>The simulation distance is then capped to the view distance, always, including for the
 *       unlimited permission: ticking chunks nobody can see is exactly the waste being removed.
 *   <li>A player who is not really playing has the configured reductions subtracted.
 *   <li>Both are clamped to the configured bounds, and step 3 is re-asserted afterwards, because
 *       clamping either one up can otherwise break it.
 * </ol>
 */
public final class DistanceSolver {

    private DistanceSolver() {
    }

    /**
     * Works out the distances for one player.
     *
     * @param clientView render distance the client asked for, in chunks
     * @param serverView the server's view distance
     * @param serverSimulation the server's simulation distance
     * @param unlimitedView whether the player may exceed the server's view distance
     * @param reduced whether the player is currently considered not to be playing
     * @param config the configured reductions and bounds
     * @return the distances to apply
     */
    public static Distances solve(final int clientView, final int serverView,
                                  final int serverSimulation, final boolean unlimitedView,
                                  final boolean reduced, final AdaptiveDistanceConfig config) {
        final int packed =
            solvePacked(clientView, serverView, serverSimulation, unlimitedView, reduced, config);
        return new Distances(Distances.viewOf(packed), Distances.simulationOf(packed));
    }

    /**
     * The same decision, packed into an int.
     *
     * <p>This is the form the periodic check uses: it runs for every player on every interval and
     * almost always concludes that nothing changed, so it should not allocate to say so.</p>
     *
     * @param clientView render distance the client asked for, in chunks
     * @param serverView the server's view distance
     * @param serverSimulation the server's simulation distance
     * @param unlimitedView whether the player may exceed the server's view distance
     * @param reduced whether the player is currently considered not to be playing
     * @param config the configured reductions and bounds
     * @return the distances, packed
     */
    public static int solvePacked(final int clientView, final int serverView,
                                  final int serverSimulation, final boolean unlimitedView,
                                  final boolean reduced, final AdaptiveDistanceConfig config) {
        int view = unlimitedView ? clientView : Math.min(clientView, serverView);

        int simulation = serverSimulation > 0
            ? Math.min(serverSimulation, clientView)
            : clientView;

        simulation = Math.min(simulation, view);

        if (reduced) {
            view -= config.viewReduction();
            simulation -= config.simulationReduction();
        }

        view = clamp(view, config.minDistance(), config.maxDistance());
        simulation = clamp(simulation, config.minDistance(), config.maxDistance());

        // Re-assert the invariant: clamping simulation up to the minimum could have pushed it past
        // a view distance that was clamped to the same minimum.
        simulation = Math.min(simulation, view);

        return (view << 8) | simulation;
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
