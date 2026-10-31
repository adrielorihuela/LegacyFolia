package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The loaded {@code config/*.toml}, through {@link ServerConfig} and {@link ServerConfig#forWorld}.
 *
 * <p>A thin adapter: {@code ServerConfig} only answers typed questions, so each read asks the
 * question the setting's catalogued kind calls for. Two consequences, both deliberate rather than
 * worth widening {@code ServerConfig} for:</p>
 * <ul>
 *   <li>A setting whose value is a table ({@code plugins.commands.aliases},
 *       {@code server.saving.forced-stats}, {@code world.generators},
 *       {@code gameplay.items.alt-item-despawn-rate-items}) reads as absent, so the bridge uses its
 *       default. {@link TomlSettings} has no such gap.</li>
 *   <li>It cannot list the worlds that have a section, so {@link #worlds()} is empty and the
 *       per-world objects are filled one world at a time, as each world loads.</li>
 * </ul>
 */
public final class ServerConfigSettings implements EffectiveSettings {

    private static final double ABSENT = Double.NaN;

    @Override
    public @Nullable Object get(final String path) {
        if (!ServerConfig.isLoaded()) {
            throw new IllegalStateException("the configuration has not been read yet");
        }
        final SettingEntry entry = SettingsCatalog.get().require(path);
        return switch (entry.kind()) {
            case BOOLEAN -> ServerConfig.getBoolean(path, true) == ServerConfig.getBoolean(path, false)
                ? ServerConfig.getBoolean(path, false) : null;
            case STRING_LIST -> ServerConfig.getStringList(path);
            case TABLE -> null;
            default -> scalar(ServerConfig.getString(path, null), ServerConfig.getDouble(path, ABSENT),
                ServerConfig.getBoolean(path, true) == ServerConfig.getBoolean(path, false) ? ServerConfig.getBoolean(path, false) : null);
        };
    }

    @Override
    public @Nullable Object get(final String path, final String world) {
        final SettingEntry entry = SettingsCatalog.get().require(path);
        if (!ServerConfig.isPerWorldFile(entry.tomlFile())) {
            return this.get(path);
        }
        final WorldConfig view = ServerConfig.forWorld(world);
        return switch (entry.kind()) {
            case BOOLEAN -> view.getBoolean(path, true) == view.getBoolean(path, false) ? view.getBoolean(path, false) : null;
            case STRING_LIST -> view.getStringList(path);
            case TABLE -> null;
            default -> scalar(view.getString(path, null), view.getDouble(path, ABSENT),
                view.getBoolean(path, true) == view.getBoolean(path, false) ? view.getBoolean(path, false) : null);
        };
    }

    /** A value that may be a string, a number or a boolean, from the three typed answers. */
    private static @Nullable Object scalar(final @Nullable String text, final double number, final @Nullable Boolean bool) {
        if (text != null) {
            return text;
        }
        if (!Double.isNaN(number)) {
            return number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE ? (Object) (int) number : (Object) number;
        }
        return bool;
    }

    @Override
    public Set<String> worlds() {
        return Set.of();
    }
}
