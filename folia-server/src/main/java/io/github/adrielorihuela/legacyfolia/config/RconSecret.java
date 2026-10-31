package io.github.adrielorihuela.legacyfolia.config;

import java.security.SecureRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The RCON password, kept in {@code config/rcon.secret} rather than in a configuration file.
 *
 * <p>RCON is on by default in this fork and listens only on {@code 127.0.0.1}. An RCON with no
 * password does not start at all, and one with a password somebody chose in a hurry is the
 * weakest door on the machine, so the server picks one itself: 32 characters from
 * {@link SecureRandom}, written to the file the first time the server starts. Whoever needs it
 * reads it from there; the log only ever says where it is.</p>
 *
 * <p>The file follows {@link SecretFiles}: the whole content, trimmed, is the password. An operator
 * who writes their own keeps it. A file that is missing or empty gets a new one, which is also how
 * a password is rotated: delete the file and restart.</p>
 */
public final class RconSecret {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** The default file name, as {@code [network.rcon] password-file} ships. */
    public static final String DEFAULT_FILE = "rcon.secret";

    /** How long a generated password is. */
    public static final int LENGTH = 32;

    /**
     * Letters and digits only: nothing a shell, a panel's form or an RCON client could mangle, and
     * still about 190 bits of entropy at this length.
     */
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RconSecret() {
    }

    /**
     * The password, generating and storing one when there is none.
     *
     * @param fileName the configured file: a name relative to {@code config/}, or an absolute path
     * @return the password to give vanilla as {@code rcon.password}; empty only when a new one could
     *     not be stored, in which case vanilla leaves RCON off rather than start it with a password
     *     nobody can read
     */
    public static String resolve(final @Nullable String fileName) {
        final String name = fileName == null || fileName.isBlank() ? DEFAULT_FILE : fileName.trim();
        final String existing = SecretFiles.read(name);
        if (existing != null) {
            return existing;
        }
        final String generated = generate();
        if (!SecretFiles.write(name, generated)) {
            LOGGER.warn("Could not store a new RCON password in {}; RCON stays off until it can be "
                + "written or you put one there yourself.", SecretFiles.path(name));
            return "";
        }
        LOGGER.info("Generated an RCON password in {}. Read it from there; it is never logged.", SecretFiles.path(name));
        return generated;
    }

    /**
     * A new random password.
     *
     * @return {@value #LENGTH} letters and digits
     */
    public static String generate() {
        final StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
