package io.github.adrielorihuela.legacyfolia.network;

import io.github.adrielorihuela.legacyfolia.config.SecretFiles;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Where the Velocity modern-forwarding secret comes from.
 *
 * <p>Upstream keeps it as {@code proxies.velocity.secret} in {@code config/paper-global.yml}, which
 * is the file people are most often asked to paste when something is wrong with their setup. Here
 * it lives in {@code config/forwarding.secret} — the same name Velocity uses on the other side of
 * the connection, so both halves of a network are configured the same way and the proxy's own file
 * can simply be copied across.</p>
 *
 * <p>The secret is held here rather than left in the configuration field, so that the YAML key can
 * be emptied on the run that migrates it. Leaving the value in two places would mean the one nobody
 * is looking at is the one that keeps ending up in a screenshot.</p>
 *
 * <p>The file is created empty on first start even though an empty secret is not usable. A file
 * that is there and blank says what to fill in; a file that is not there has to be guessed at.</p>
 */
public final class ForwardingSecret {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** Kept from upstream: containers set this and never touch a file. */
    private static final String ENVIRONMENT_VARIABLE = "PAPER_VELOCITY_SECRET";

    private static final String FILE_KEY = "network.velocity.secret-file";

    /** Written once during configuration loading, read on every proxied login. */
    private static volatile String secret = "";

    private ForwardingSecret() {
    }

    /**
     * The secret to verify forwarded logins with.
     *
     * @return the secret, or an empty string if there is none
     */
    public static String get() {
        return secret;
    }

    /**
     * Settles the secret, in order of how specific the source is.
     *
     * <p>The environment wins, because that is what a container sets and it is deliberately
     * transient. Then the file. Then, only on a server upgrading from a configuration that predates
     * the file, whatever is still in the YAML — which is copied into the file so that the caller can
     * blank the YAML key out.</p>
     *
     * @param fromYaml the value of {@code proxies.velocity.secret}
     * @return whether the YAML key should now be cleared, having been migrated into the file
     */
    public static boolean resolve(final String fromYaml) {
        final String fileName = fileName();
        SecretFiles.createIfAbsent(fileName);

        final String fromEnvironment = System.getenv(ENVIRONMENT_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            secret = fromEnvironment.trim();
            return false;
        }

        final @Nullable String fromFile = SecretFiles.read(fileName);
        if (fromFile != null) {
            secret = fromFile;
            if (!fromYaml.isBlank()) {
                LOGGER.warn("Both {} and proxies.velocity.secret in paper-global.yml have a "
                    + "forwarding secret. Using the file, and clearing the other one.",
                    SecretFiles.path(fileName));
                return true;
            }
            return false;
        }

        if (!fromYaml.isBlank()) {
            secret = fromYaml.trim();
            if (SecretFiles.write(fileName, secret)) {
                LOGGER.info("Moved your Velocity forwarding secret out of paper-global.yml and into "
                    + "{}, so the configuration is safe to share.", SecretFiles.path(fileName));
                return true;
            }
            return false;
        }

        secret = "";
        return false;
    }

    /** The configured file name, or the default when the unified file has not been read yet. */
    private static String fileName() {
        if (!ServerConfig.isLoaded()) {
            return SecretFiles.FORWARDING_SECRET;
        }
        final String configured =
            ServerConfig.getString(FILE_KEY, SecretFiles.FORWARDING_SECRET);
        return configured.isBlank() ? SecretFiles.FORWARDING_SECRET : configured;
    }
}
