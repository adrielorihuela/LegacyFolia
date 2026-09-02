package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A cached view is a snapshot of the files as they were, so the only thing that can go wrong with
 * it is outliving them. That is what these check.
 */
class WorldConfigCacheTest {

    private final AtomicInteger built = new AtomicInteger();

    private final WorldConfigCache cache = new WorldConfigCache(world -> {
        this.built.incrementAndGet();
        return new WorldConfig(world, Map.of());
    });

    @Test
    @DisplayName("a world is resolved once and reused after that")
    void resolvesOnce() {
        final WorldConfig first = this.cache.get("world");

        assertSame(first, this.cache.get("world"));
        assertSame(first, this.cache.get("world"));
        assertEquals(1, this.built.get(), "reads happen in tick loops; they must not rebuild");
    }

    @Test
    @DisplayName("each world gets its own view")
    void resolvesPerWorld() {
        assertEquals("world", this.cache.get("world").world());
        assertEquals("world_nether", this.cache.get("world_nether").world());
        assertEquals(2, this.built.get());
        assertEquals(2, this.cache.size());
    }

    @Test
    @DisplayName("a reload throws every view away")
    void invalidateDropsEverything() {
        final WorldConfig before = this.cache.get("world");
        this.cache.get("world_nether");

        this.cache.invalidate();

        assertEquals(0, this.cache.size());
        assertNotSame(before, this.cache.get("world"),
            "a view built from the old files must not survive the reload");
        assertEquals(3, this.built.get());
    }
}
