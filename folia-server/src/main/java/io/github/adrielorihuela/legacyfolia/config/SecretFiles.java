package io.github.adrielorihuela.legacyfolia.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Small files under {@code config/} that hold one secret and nothing else.
 *
 * <p>A secret pasted into a configuration file travels with that file. Configurations get quoted in
 * issues, pasted into support channels and photographed in screenshots, and a Velocity forwarding
 * secret sitting three lines below the setting somebody was asked about goes with them. Keeping it
 * in a file of its own means the configuration can be shared as-is, and the one file that must not
 * be shared is obvious from its name.</p>
 *
 * <p>These files hold their value raw, with no comments and no syntax: the entire contents, trimmed,
 * are the secret. That is what makes them trivial to write from a deployment script, and it is why
 * nothing is ever added to them automatically.</p>
 */
public final class SecretFiles {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** The Velocity modern-forwarding secret, matching the file name Velocity itself uses. */
    public static final String FORWARDING_SECRET = "forwarding.secret";

    /** An optional GitHub token, used only to raise the plugin updater's rate limit. */
    public static final String GITHUB_TOKEN = "github.token";

    private SecretFiles() {
    }

    /**
     * Resolves a secret file.
     *
     * <p>A name is taken as relative to {@code config/}; an absolute path is used as given, so a
     * secret can be mounted from somewhere the server does not otherwise write to.</p>
     *
     * @param name the configured file name or path
     * @return where the file lives
     */
    public static Path path(final String name) {
        final Path asGiven = Path.of(name);
        return asGiven.isAbsolute() ? asGiven : ServerConfig.DIRECTORY.resolve(asGiven);
    }

    /**
     * Creates the file empty when it is not there.
     *
     * <p>Created even when nothing needs it yet, because an empty file named {@code
     * forwarding.secret} invites being filled in, whereas a missing one has to be guessed at from
     * documentation.</p>
     *
     * @param name the configured file name or path
     */
    public static void createIfAbsent(final String name) {
        final Path path = path(name);
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            LOGGER.info("Created an empty {}. Put the value in it when you need one.", path);
        } catch (final IOException e) {
            LOGGER.warn("Could not create {}: {}", path, e.getMessage());
        }
    }

    /**
     * Reads a secret.
     *
     * @param name the configured file name or path
     * @return the trimmed contents, or {@code null} if the file is missing, empty or unreadable
     */
    public static @Nullable String read(final String name) {
        final Path path = path(name);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            final String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (final IOException e) {
            LOGGER.warn("Could not read {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Writes a secret, replacing whatever was there.
     *
     * @param name the configured file name or path
     * @param value the secret to store
     * @return whether it was written
     */
    public static boolean write(final String name, final String value) {
        final Path path = path(name);
        try {
            Files.createDirectories(path.getParent());
            // "\n" rather than the platform separator: these files get copied between a Windows
            // machine and a Linux server, and read by shell scripts that would carry the carriage
            // return straight into the value.
            Files.writeString(path, value + "\n", StandardCharsets.UTF_8);
            return true;
        } catch (final IOException e) {
            LOGGER.warn("Could not write {}: {}", path, e.getMessage());
            return false;
        }
    }
}
