package io.github.adrielorihuela.legacyfolia.update;

import io.github.adrielorihuela.legacyfolia.config.SecretFiles;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code [plugins.auto-update]} and runs the updater over it.
 *
 * <p>The one place start-up has to call, so that the hook in the server is a single line and the
 * decisions about what to update stay here.</p>
 */
public final class PluginUpdates {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String ENABLED = "plugins.auto-update.enabled";
    private static final String TOKEN_FILE = "plugins.auto-update.github-token-file";
    private static final String LUCKPERMS = "plugins.auto-update.luckperms";

    /** The sources that take a list of names, in the order they are tried. */
    private static final String[] LIST_SOURCES = {"hangar", "modrinth", "github"};

    private PluginUpdates() {
    }

    /**
     * Brings the listed plugins up to date, if the configuration asks for it.
     *
     * @param pluginDirectory the directory the plugin loader is about to read
     * @param minecraftVersion the version to ask the sources for builds of
     * @throws PluginUpdateException if a listed plugin is missing and could not be downloaded
     */
    public static void run(final Path pluginDirectory, final String minecraftVersion)
        throws PluginUpdateException {
        if (!ServerConfig.isLoaded()) {
            return;
        }

        // Created before anything else, and whether or not the updater is switched on. It used to
        // sit below the check for that, which meant the file only ever appeared for people who had
        // already turned the feature on -- exactly the ones who did not need to be shown where the
        // token goes.
        final String tokenFile = tokenFileName();
        SecretFiles.createIfAbsent(tokenFile);

        if (!ServerConfig.getBoolean(ENABLED, false)) {
            return;
        }

        final List<PluginUpdater.Wanted> wanted = wanted();
        if (wanted.isEmpty()) {
            return;
        }

        final @Nullable String token = SecretFiles.read(tokenFile);
        if (token == null && wanted.stream().anyMatch(one -> "github".equals(one.source()))) {
            LOGGER.info("No GitHub token in {}. That is fine, but GitHub allows only sixty "
                + "unauthenticated requests an hour per address.", SecretFiles.path(tokenFile));
        }

        new PluginUpdater(pluginDirectory, minecraftVersion, token).updateAll(wanted);
    }

    /** Everything the configuration asks for, flattened in the order the sources are listed. */
    private static List<PluginUpdater.Wanted> wanted() {
        final List<PluginUpdater.Wanted> wanted = new ArrayList<>();

        for (final String source : LIST_SOURCES) {
            for (final String spec : ServerConfig.getStringList("plugins.auto-update." + source)) {
                wanted.add(new PluginUpdater.Wanted(source, spec));
            }
        }

        // LuckPerms is a switch rather than a list: there is one of it, and its own metadata
        // service publishes exactly one build per platform.
        if (ServerConfig.getBoolean(LUCKPERMS, false)) {
            wanted.add(new PluginUpdater.Wanted("luckperms", "luckperms"));
        }

        return wanted;
    }

    private static String tokenFileName() {
        final String configured =
            ServerConfig.getString(TOKEN_FILE, SecretFiles.GITHUB_TOKEN);
        return configured.isBlank() ? SecretFiles.GITHUB_TOKEN : configured;
    }
}
