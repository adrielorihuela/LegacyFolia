package io.github.adrielorihuela.legacyfolia.world;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;

/**
 * Sending every player to the world spawn when they join, the way a lobby wants.
 *
 * <p>A lobby has one place people are meant to arrive at. The server, left alone, puts a returning
 * player back exactly where they logged out, which on a lobby means they reappear wherever they
 * happened to be standing — halfway up a build, inside a portal room, or in a corner the map was
 * never meant to start people in. This makes every join behave like a first join: the spawn point,
 * spread over the spawn radius, every time.</p>
 *
 * <p><b>Nothing is erased.</b> This does not clear, rewrite or discard any part of the player's
 * saved data. The position stored in the NBT is simply not consulted when deciding where to put
 * them; inventory, ender chest, experience, health, hunger, advancements, statistics and everything
 * else are read and kept exactly as they always were. That is what makes the setting safe to try:
 * turning it off restores the ordinary behaviour on the very next join, because the position it was
 * ignoring is still sitting in the file, untouched.</p>
 *
 * <p>The hook is one line in {@code net.minecraft.server.network.config.PrepareSpawnTask#start()}:
 * when this is on, hand the rest of the method {@link net.minecraft.server.level.ServerPlayer
 * ServerPlayer}{@code .SavedPosition.EMPTY} instead of the position read from the NBT. The server
 * then falls through its own existing path, {@code PlayerSpawnFinder.findSpawn(level,
 * respawnData.pos())}, which already spreads the arrival across the {@code spawnRadius} gamerule
 * and already finds a safe block. No new placement code is added, and none is wanted: the
 * first-join path is the one that has always been exercised.</p>
 */
public final class Lobby {

    /** The setting, in {@code config/world.toml}. */
    private static final String FORCE_WORLD_SPAWN_KEY = "world.always-spawn-at-world-spawn";

    private Lobby() {
    }

    /**
     * Whether joining players should be placed at the world spawn rather than where they left.
     *
     * <p>Read on each join rather than cached, so an operator editing the file and reloading does
     * not have to restart the server to see the change.</p>
     *
     * @return whether the saved position should be ignored when a player joins
     */
    public static boolean forceWorldSpawn() {
        return ServerConfig.isLoaded() && ServerConfig.getBoolean(FORCE_WORLD_SPAWN_KEY, false);
    }
}
