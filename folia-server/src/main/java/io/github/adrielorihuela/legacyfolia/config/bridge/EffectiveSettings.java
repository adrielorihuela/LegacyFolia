package io.github.adrielorihuela.legacyfolia.config.bridge;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where the bridge reads the values it hands to Spigot, Bukkit, Paper and vanilla.
 *
 * <p>The bridge does not care whether a value came from a TOML file, from {@code server.properties}
 * laid over the TOML files, or from a test. It asks for a setting by its catalogued TOML path and
 * gets back the raw value an operator wrote: a {@code Boolean}, a number, a {@code String}, a
 * {@code List}, or a table (a {@code Map} or a night-config table). Checking and converting that
 * value is the bridge's job, not the source's; a source only has to say what is written.</p>
 *
 * <p>Per-world values follow the chain {@code ServerConfig.forWorld} follows: the world's own
 * section, then {@code [worlds.default]}, then the global section. Asking for the world named
 * {@value io.github.adrielorihuela.legacyfolia.config.WorldConfig#DEFAULT} gives the middle layer
 * alone over the global one, which is what Spigot's {@code world-settings.default} and Paper's
 * {@code paper-world-defaults.yml} stand for.</p>
 */
public interface EffectiveSettings {

    /**
     * The server-wide value of a setting: its global section, with no world's override applied.
     *
     * @param path the catalogued TOML path, such as {@code server.motd}
     * @return the raw value, or {@code null} when nothing is written, in which case the catalogued
     *     default applies
     */
    @Nullable Object get(String path);

    /**
     * The value of a setting as one world sees it.
     *
     * @param path the catalogued TOML path
     * @param world the world's name as the server knows it, such as {@code world_nether}, or
     *     {@code default} for the layer every world shares
     * @return the raw value, or {@code null} when nothing is written at any layer
     */
    @Nullable Object get(String path, String world);

    /**
     * The worlds that have a section of their own, so that the bridge knows which per-world objects
     * to fill. {@code default} is not one of them.
     *
     * @return the world names, possibly empty
     */
    Set<String> worlds();
}
