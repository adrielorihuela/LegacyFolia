package io.github.adrielorihuela.legacyfolia.world;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;

/**
 * How large the untouchable square around the world spawn is, and why it is nothing by default.
 *
 * <p>Vanilla, and {@code DedicatedServerProperties} after it, defaults this to 16: a 33x33 area
 * around spawn that no one but an operator may build in or break. It is there for a public server
 * with no plugins, and it is a surprise everywhere else. The usual first symptom is a new server
 * where nobody can break the first block they walk up to, and the usual fix is an operator finding
 * out that the setting exists at all.</p>
 *
 * <p>Every server that cares about protecting an area already does it properly — a claim plugin, a
 * region plugin, a lobby with building disabled outright — and those all have to work around this
 * one first. So the default here is 0, off. Anyone who wants vanilla's behaviour sets it back to
 * 16, and the setting behaves exactly as it always did: it is the same radius the server already
 * enforces, only its default changed.</p>
 *
 * <p>The hook is where {@code DedicatedServerProperties} reads {@code spawn-protection}; the value
 * this returns is used in its place.</p>
 */
public final class SpawnProtection {

    /** The setting, in {@code config/server.toml}. */
    private static final String RADIUS_KEY = "server.spawn-protection-radius";

    /** Off, unlike vanilla's 16. */
    public static final int DEFAULT_RADIUS = 0;

    private SpawnProtection() {
    }

    /**
     * The configured spawn protection radius, in blocks.
     *
     * @return the radius to enforce, never negative
     */
    public static int radius() {
        if (!ServerConfig.isLoaded()) {
            return DEFAULT_RADIUS;
        }
        return effectiveRadius(ServerConfig.getInt(RADIUS_KEY, DEFAULT_RADIUS));
    }

    /**
     * The radius actually enforced for a configured value.
     *
     * <p>A negative number is not a smaller protection than none, it is a typo; it is read as off
     * rather than allowed to reach the distance check as a negative.</p>
     *
     * @param configured the value read from the file
     * @return the radius to enforce, never negative
     */
    public static int effectiveRadius(final int configured) {
        return Math.max(0, configured);
    }
}
