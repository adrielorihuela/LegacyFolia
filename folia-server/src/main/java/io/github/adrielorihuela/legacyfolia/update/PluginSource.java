package io.github.adrielorihuela.legacyfolia.update;

/**
 * Somewhere plugins are published, and a way to ask it what the current build of one is.
 *
 * <p>Each implementation owns one API and the shape of its answers. What they have in common is
 * only what {@link PluginRelease} carries, which is deliberately the least a download needs: the
 * sources disagree about almost everything else, including whether a checksum exists at all.</p>
 */
public interface PluginSource {

    /** The name of this source, as it appears in the configuration and in the log. */
    String name();

    /**
     * Looks up the current build of a plugin.
     *
     * @param spec the entry from the configuration
     * @return the release to install
     * @throws PluginUpdateException if the source cannot be reached or does not publish it
     */
    PluginRelease resolve(String spec) throws PluginUpdateException;
}
