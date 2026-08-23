package io.github.adrielorihuela.legacyfolia.chunks;

import io.papermc.paper.FeatureHooks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Gives each player only as many chunks as they are actually using.
 *
 * <p>A server's view distance has to be set for the player who wants the most, which means everyone
 * else is sent and ticked chunks they never look at. Two things are wasted there: chunks beyond
 * what a client asked for, which it throws away on arrival, and chunks belonging to somebody who is
 * not at the keyboard at all.</p>
 *
 * <p>Both are handed back here. A client that asks for 8 is given 8 however high the server's own
 * limit is, and a player the activity tests decide is away is dropped by the configured reduction
 * until they come back. The permission {@value #UNLIMITED_VIEW} lets a player past the server's
 * view distance rather than being capped by it.</p>
 *
 * <p>Every player's state is theirs alone and is only touched from their own thread. The packets
 * that feed the activity tests are handled there, and so is the check that reads them, so nothing
 * here needs to reach across a region.</p>
 */
public final class AdaptiveDistance {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Lets a player see further than the server's own view distance. */
    public static final String UNLIMITED_VIEW = "legacyfolia.chunkscope.unlimited";

    private static volatile AdaptiveDistanceConfig config = AdaptiveDistanceConfig.disabled();

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** What one player is currently being given, and why. */
    private static final class State {
        private final PlayerActivity activity = new PlayerActivity();
        private int applied = Distances.NOTHING_APPLIED;
        private long nextCheckAt;
        private @Nullable String reason;
    }

    private AdaptiveDistance() {
    }

    /** Re-reads the configuration, at start-up and on reload. */
    public static void reload() {
        config = AdaptiveDistanceConfig.load();
        if (config.enabled()) {
            LOGGER.info("Adaptive view distance is on: players who are not playing lose {} chunks "
                + "of view and {} of simulation after {}s.", config.viewReduction(),
                config.simulationReduction(), config.afkSeconds());
        }
    }

    /** Whether anything should be adjusted at all. */
    public static boolean enabled() {
        return config.enabled();
    }

    /**
     * The activity record for a player, so the packet handlers can feed it.
     *
     * @param player the player
     * @return their activity, or {@code null} when the feature is off
     */
    public static @Nullable PlayerActivity activityOf(final ServerPlayer player) {
        if (!config.enabled()) {
            return null;
        }
        return STATES.computeIfAbsent(player.getUUID(), key -> new State()).activity;
    }

    /** Forgets a player who has disconnected. */
    public static void forget(final UUID uuid) {
        STATES.remove(uuid);
    }

    /**
     * Notes something only a person does on purpose: breaking a block, using an item, clicking in
     * a container, attacking. Resets the away clock outright.
     *
     * @param player who did it
     */
    public static void onDeliberateAction(final ServerPlayer player) {
        final PlayerActivity activity = activityOf(player);
        if (activity != null) {
            activity.recordDeliberateAction(System.currentTimeMillis());
        }
    }

    /**
     * Notes that the player moved, which is believed only if it looks like a person moved.
     *
     * @param player who moved
     */
    public static void onMovement(final ServerPlayer player) {
        final PlayerActivity activity = activityOf(player);
        if (activity != null) {
            activity.recordMovement(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), System.currentTimeMillis());
        }
    }

    /**
     * Notes an arm swing, for the auto-clicker test.
     *
     * @param player who swung
     */
    public static void onSwing(final ServerPlayer player) {
        final PlayerActivity activity = activityOf(player);
        if (activity != null) {
            activity.recordSwing(System.currentTimeMillis());
        }
    }

    /**
     * Notes a line of chat, which counts unless it is the same line again.
     *
     * @param player who said it
     * @param message what was said
     */
    public static void onChat(final ServerPlayer player, final String message) {
        final PlayerActivity activity = activityOf(player);
        if (activity != null) {
            activity.recordChatFromAnyThread(message, System.currentTimeMillis());
        }
    }

    /**
     * Re-examines a player if enough time has passed, and applies anything that changed.
     *
     * <p>Called from the player's own tick, so it runs on their region's thread and touches nothing
     * belonging to anybody else. The interval is per player rather than global, which spreads the
     * work out instead of doing everyone at once.</p>
     *
     * @param player the player to look at
     */
    public static void tick(final ServerPlayer player) {
        final AdaptiveDistanceConfig current = config;
        if (!current.enabled() || player.connection == null) {
            return;
        }
        final State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        final long now = System.currentTimeMillis();
        if (now < state.nextCheckAt) {
            return;
        }
        state.nextCheckAt = now + current.checkSeconds() * 1000L;
        evaluate(player, state, current, now);
    }

    /**
     * Re-examines a player right now, whatever the interval says.
     *
     * <p>For the moments where waiting would be visible: joining, changing world, or the client
     * telling us it has changed its render distance.</p>
     *
     * @param player the player to look at
     */
    public static void evaluateNow(final ServerPlayer player) {
        final AdaptiveDistanceConfig current = config;
        if (!current.enabled() || player.connection == null) {
            return;
        }
        final State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        evaluate(player, state, current, System.currentTimeMillis());
    }

    private static void evaluate(final ServerPlayer player, final State state,
                                 final AdaptiveDistanceConfig current, final long now) {
        final int clientView = player.requestedViewDistance();
        if (clientView <= 0) {
            // The client has not said yet. Guessing here would send a burst of chunks that the
            // next packet immediately corrects.
            return;
        }

        final net.minecraft.server.level.ServerLevel level = player.level();
        final int serverView = level.getWorld().getViewDistance();
        final int serverSimulation = level.getWorld().getSimulationDistance();
        final boolean unlimited = player.getBukkitEntity().hasPermission(UNLIMITED_VIEW);

        state.reason = state.activity.notPlayingReason(current, now);
        final int target = DistanceSolver.solvePacked(clientView, serverView, serverSimulation,
            unlimited, state.reason != null, current);

        if (target == state.applied) {
            return;
        }
        state.applied = target;

        final int view = Distances.viewOf(target);
        final int simulation = Distances.simulationOf(target);
        FeatureHooks.setViewDistance(player, view);
        FeatureHooks.setSendViewDistance(player, view);
        FeatureHooks.setSimulationDistance(player, simulation);

        if (current.debug()) {
            LOGGER.info("{}: view {}, simulation {} (client asked for {}{})",
                player.getScoreboardName(), view, simulation, clientView,
                state.reason == null ? "" : ", " + state.reason);
        }
    }
}
