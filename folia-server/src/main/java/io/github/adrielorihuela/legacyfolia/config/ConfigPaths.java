package io.github.adrielorihuela.legacyfolia.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Moves the files the server keeps beside its jar into {@code config/}.
 *
 * <p>A server directory accumulates: the whitelist, the ban lists, the operator list, the EULA. All
 * of them are configuration, whatever their format, and none of them are anything an operator wants
 * scattered around the folder they keep the jar in. They belong with the rest of the configuration,
 * and after this the only thing left in the root is the jar and the directories the server
 * generates.</p>
 *
 * <p>Anything already sitting in the root is moved rather than ignored. A server that upgrades into
 * this keeps its bans and its whitelist; finding them silently empty because the file moved would be
 * the kind of surprise that loses an operator their moderation history.</p>
 */
public final class ConfigPaths {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private ConfigPaths() {
    }

    /**
     * The path a configuration file should live at, moving it out of the root if it is still there.
     *
     * @param name the file's name
     * @return where it lives now
     */
    public static File resolve(final String name) {
        return resolvePath(name).toFile();
    }

    /**
     * The same, as a {@link Path}.
     *
     * @param name the file's name
     * @return where it lives now
     */
    public static Path resolvePath(final String name) {
        return migrate(Path.of(name), ConfigFile.DIRECTORY.resolve(name));
    }

    /**
     * Moves one file from the root into {@code config/}, once, and says where it ended up.
     *
     * <p>Returns the root path when the move did not happen, rather than the place the file was
     * supposed to go: answering with a path nothing is at would turn a failed move into an empty
     * whitelist or an empty ban list, which is worse than not moving it.</p>
     *
     * <p>If both copies exist the one in {@code config/} wins and the stray is left alone rather
     * than deleted. Two copies of a ban list is something to be told about, not something to
     * resolve by throwing one away.</p>
     */
    private static Path migrate(final Path fromRoot, final Path target) {
        if (!Files.isRegularFile(fromRoot)) {
            return target;
        }
        if (Files.exists(target)) {
            LOGGER.warn("Both {} and {} exist. Using the one in config/; the other is being left "
                + "alone for you to look at.", fromRoot, target);
            return target;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(fromRoot, target, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Moved {} into config/.", fromRoot);
            return target;
        } catch (final IOException e) {
            LOGGER.warn("Could not move {} into config/: {}. Carrying on with the one in the root.",
                fromRoot, e.getMessage());
            return fromRoot;
        }
    }
}
