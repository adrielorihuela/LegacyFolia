package io.github.adrielorihuela.legacyfolia.world;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Closing the Nether roof, without lying to the rest of the server about how tall the Nether is.
 *
 * <p>In modern versions the Nether really is 256 blocks tall. Its {@code logicalHeight} of 128 only
 * decides where portals may lead and where chorus fruit may land — it is not a build limit, so
 * bedrock roof at y=127 is reachable and the flat expanse above it is buildable. Servers that do
 * not want it used as a fast-travel plane have to stop placement themselves.</p>
 *
 * <p>This does exactly that and no more: it answers a separate question, "how high may somebody
 * place a block here", which only the placement path asks. The dimension keeps its real height.</p>
 *
 * <p><b>Do not implement this by changing {@link Level#getMaxY()}.</b> That value is the world's
 * actual vertical extent, and the chunk system, world generation, lighting and every heightmap are
 * built from it. Lowering it would mean chunk sections that do not exist where the generator still
 * writes bedrock, heightmaps that disagree with the blocks under them, and saved worlds that no
 * longer round-trip. The hook is a new {@code getBuildMaxY()} on {@link Level}, consulted only
 * where a block is about to be placed; everything else keeps reading {@code getMaxY()}.</p>
 *
 * <p>Turning the setting off restores placement immediately, and blocks already sitting above the
 * roof are left where they are — nothing here removes anything.</p>
 */
public final class NetherRoof {

    /**
     * The Nether's id.
     *
     * <p>Matched as text rather than against {@link Level#NETHER}: initialising {@code Level} pulls
     * in the particle types and the registries behind them, and the arithmetic below should stay
     * checkable without a running server.</p>
     */
    public static final String NETHER_ID = "minecraft:the_nether";

    /** The setting, in {@code config/world.toml}. */
    private static final String DISABLED_KEY = "world.disable-nether-roof";

    private NetherRoof() {
    }

    /**
     * Whether building above the Nether roof is refused.
     *
     * <p>Read on demand rather than cached, so editing the file and reloading is enough.</p>
     *
     * @return whether the Nether build limit should be lowered to the roof
     */
    public static boolean disabled() {
        return ServerConfig.isLoaded() && ServerConfig.getBoolean(DISABLED_KEY, false);
    }

    /**
     * The highest y a block may be placed at in a dimension.
     *
     * <p>Pure arithmetic on the numbers the level already knows, so it can be checked without a
     * server. In the Nether with the setting on this is {@code minY + logicalHeight - 1}, which for
     * the vanilla Nether is {@code 0 + 128 - 1 = 127}: the bedrock roof layer itself, and nothing
     * above it. Everywhere else, and whenever the setting is off, it is the dimension's own
     * {@code maxY}, so no other world is affected.</p>
     *
     * @param dimension the level being built in
     * @param minY the dimension's lowest y
     * @param maxY the dimension's highest y
     * @param logicalHeight the dimension's logical height
     * @param disabled whether the roof is closed, from {@link #disabled()}
     * @return the highest y a block may be placed at
     */
    public static int buildMaxY(final ResourceKey<Level> dimension, final int minY, final int maxY,
                                final int logicalHeight, final boolean disabled) {
        return buildMaxY(dimension.identifier().toString(), minY, maxY, logicalHeight, disabled);
    }

    /**
     * The same answer, from the dimension's id.
     *
     * @param dimensionId the dimension's namespaced id, such as {@code minecraft:the_nether}
     * @param minY the dimension's lowest y
     * @param maxY the dimension's highest y
     * @param logicalHeight the dimension's logical height
     * @param disabled whether the roof is closed, from {@link #disabled()}
     * @return the highest y a block may be placed at
     */
    public static int buildMaxY(final String dimensionId, final int minY, final int maxY,
                                final int logicalHeight, final boolean disabled) {
        if (!disabled || !NETHER_ID.equals(dimensionId)) {
            return maxY;
        }
        final int roof = minY + logicalHeight - 1;
        // A Nether whose logical height a datapack raised past its real height must not end up
        // with a higher build limit than the world actually has.
        return Math.min(roof, maxY);
    }
}
