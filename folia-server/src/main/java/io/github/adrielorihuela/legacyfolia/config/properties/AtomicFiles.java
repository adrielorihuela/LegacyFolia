package io.github.adrielorihuela.legacyfolia.config.properties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces a file in one step, so that nobody — the server after a crash, a panel reading it, an
 * operator with an editor open — ever sees half of it.
 *
 * <p>The new contents go to a temporary file beside the old one, which is then moved over it. A
 * move within one directory is atomic on every file system a server runs on; where it is not, the
 * plain replacing move is the fallback, which is still never a half-written file.</p>
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Writes text as UTF-8, replacing the file if it exists.
     *
     * @param path the file
     * @param text its new contents
     * @throws IOException if it could not be written; the old file is then untouched
     */
    public static void write(final Path path, final String text) throws IOException {
        final Path absolute = path.toAbsolutePath();
        final Path directory = absolute.getParent();
        Files.createDirectories(directory);
        final Path temporary = Files.createTempFile(directory, absolute.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads a file as UTF-8 text.
     *
     * @param path the file
     * @return its contents
     * @throws IOException if it could not be read
     */
    public static String read(final Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
