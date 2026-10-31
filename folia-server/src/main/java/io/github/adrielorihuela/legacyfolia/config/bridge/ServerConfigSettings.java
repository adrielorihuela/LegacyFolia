package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The settings the running server uses: the loaded {@code config/*.toml} with the
 * server.properties mode's overrides applied, read through {@link ServerConfig}.
 *
 * <p>Answers exactly as {@link TomlSettings} does over the same files, tables included: a server
 * with its own generators, biome providers, command aliases, forced statistics or per-world
 * despawn table gets them, and {@link #worlds()} lists every world that has a section of its own,
 * from {@code worlds.toml} or from {@code worlds.<world>.<path>} lines in server.properties.</p>
 *
 * <p>It is live. Every answer reflects {@code ServerConfig} as it stands at the moment of the
 * call: after a reload, a reconcile or a write such as {@code /difficulty}, the next read sees the
 * new value. The flattened view behind it is rebuilt only when {@link ServerConfig#generation()}
 * moves, so reading it in a loop costs a hash lookup.</p>
 */
public final class ServerConfigSettings implements EffectiveSettings {

    private record Snapshot(long generation, TomlSettings settings) {
    }

    private volatile @Nullable Snapshot snapshot;

    private TomlSettings current() {
        if (!ServerConfig.isLoaded()) {
            throw new IllegalStateException("the configuration has not been read yet");
        }
        final long generation = ServerConfig.generation();
        final Snapshot cached = this.snapshot;
        if (cached != null && cached.generation() == generation) {
            return cached.settings();
        }
        final TomlSettings built = TomlSettings.of(ServerConfig.documents(), ServerConfig.overriddenGlobal(),
            ServerConfig.overriddenWorlds());
        this.snapshot = new Snapshot(generation, built);
        return built;
    }

    @Override
    public @Nullable Object get(final String path) {
        return this.current().get(path);
    }

    @Override
    public @Nullable Object get(final String path, final String world) {
        return this.current().get(path, world);
    }

    @Override
    public Set<String> worlds() {
        return this.current().worlds();
    }
}
