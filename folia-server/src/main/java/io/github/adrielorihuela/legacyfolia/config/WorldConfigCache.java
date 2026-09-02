package io.github.adrielorihuela.legacyfolia.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keeps one {@link WorldConfig} per world, and throws them all away when the files are re-read.
 *
 * <p>Resolving a world's view walks every global section once, which is cheap but not free, and the
 * settings it answers are read from tick loops. So a view is built the first time a world asks for
 * one and reused after that. Worlds arrive and leave while the server runs and are asked for from
 * region threads, so the map is concurrent rather than merely synchronised.</p>
 *
 * <p>Invalidation is wholesale on purpose. A reload can change any layer of the resolution — a
 * global default, {@code [worlds.default]}, one world's overrides — and working out which views a
 * given edit affects would be a second implementation of the resolution, able to disagree with the
 * first. Dropping every view instead costs one rebuild per world and cannot be wrong.</p>
 */
public final class WorldConfigCache {

    private final Function<String, WorldConfig> resolver;

    private final Map<String, WorldConfig> views = new ConcurrentHashMap<>();

    /**
     * @param resolver builds the view for a world that has not been asked for yet
     */
    public WorldConfigCache(final Function<String, WorldConfig> resolver) {
        this.resolver = resolver;
    }

    /**
     * The view for a world, building it if this is the first time it has been asked for.
     *
     * <p>A world nobody configured is not an error: it resolves to {@code [worlds.default]} over
     * the global sections, which is exactly what a world with an empty section would get.</p>
     *
     * @param world the world's name
     * @return its view
     */
    public WorldConfig get(final String world) {
        return this.views.computeIfAbsent(world, this.resolver);
    }

    /** Drops every view, so that the next reader gets one built from the files as they are now. */
    public void invalidate() {
        this.views.clear();
    }

    /** How many views are being held, for tests. */
    public int size() {
        return this.views.size();
    }
}
