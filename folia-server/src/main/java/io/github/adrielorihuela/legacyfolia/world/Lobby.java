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
 *
 * <p><b>Which world.</b> {@code world.lobby-spawn-world} decides whose spawn that is.
 * {@code logout-world}, the default, keeps the world the player left from and uses its spawn, so
 * somebody who logged out in the Nether arrives at the Nether's spawn. {@code main-world} also
 * forgets the world, so the server falls through to the world its spawn is in, the main one, as it
 * does for a first join: a lobby with several worlds wants everybody back in the lobby world. A
 * second line in {@code PrepareSpawnTask#start()} does that.</p>
 */
public final class Lobby {

    /** The setting, in {@code config/world.toml}. */
    private static final String FORCE_WORLD_SPAWN_KEY = "world.always-spawn-at-world-spawn";

    /** Which world's spawn, in {@code config/world.toml}. */
    private static final String SPAWN_WORLD_KEY = "world.lobby-spawn-world";

    /** The two values of {@code world.lobby-spawn-world}. */
    public enum SpawnWorld {
        /** The spawn of the world the player logged out in. */
        LOGOUT_WORLD("logout-world"),
        /** The spawn of the main world, whatever world the player logged out in. */
        MAIN_WORLD("main-world");

        private final String value;

        SpawnWorld(final String value) {
            this.value = value;
        }

        /** The value as written in the TOML file. */
        public String value() {
            return this.value;
        }

        /**
         * Reads a configured value. Anything that is not {@code main-world} is the default,
         * {@code logout-world}: an unknown word must not move every player to another world.
         *
         * @param value the configured text, or {@code null}
         * @return the mode
         */
        public static SpawnWorld parse(final String value) {
            return value != null && MAIN_WORLD.value.equalsIgnoreCase(value.trim()) ? MAIN_WORLD : LOGOUT_WORLD;
        }
    }

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

    /**
     * Which world's spawn a forced spawn uses.
     *
     * @return the configured mode, {@link SpawnWorld#LOGOUT_WORLD} before the configuration is read
     */
    public static SpawnWorld spawnWorld() {
        return ServerConfig.isLoaded()
            ? SpawnWorld.parse(ServerConfig.getString(SPAWN_WORLD_KEY, SpawnWorld.LOGOUT_WORLD.value()))
            : SpawnWorld.LOGOUT_WORLD;
    }

    /**
     * Whether a joining player's saved world should be forgotten too, so that they arrive in the
     * main world.
     *
     * @return whether to send them to the main world's spawn
     */
    public static boolean sendToMainWorld() {
        return sendToMainWorld(forceWorldSpawn(), spawnWorld());
    }

    /**
     * The decision itself: only a forced spawn in {@code main-world} mode changes the world;
     * {@code lobby-spawn-world} means nothing while {@code always-spawn-at-world-spawn} is off.
     *
     * @param force {@code world.always-spawn-at-world-spawn}
     * @param spawnWorld {@code world.lobby-spawn-world}
     * @return whether to forget the saved world
     */
    public static boolean sendToMainWorld(final boolean force, final SpawnWorld spawnWorld) {
        return force && spawnWorld == SpawnWorld.MAIN_WORLD;
    }
}
