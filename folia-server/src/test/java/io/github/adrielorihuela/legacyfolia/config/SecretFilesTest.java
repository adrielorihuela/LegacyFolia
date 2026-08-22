package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A secret file is read by shell scripts and copied between machines, so what counts as "the
 * value" has to be exact.
 */
class SecretFilesTest {

    @Test
    @DisplayName("an absolute path is used as given, so a secret can be mounted from elsewhere")
    void absolutePathsAreLeftAlone(@TempDir final Path dir) {
        final Path mounted = dir.resolve("mounted.secret");

        assertEquals(mounted, SecretFiles.path(mounted.toString()));
    }

    @Test
    @DisplayName("a bare name is resolved inside the config directory")
    void bareNamesGoUnderConfig() {
        assertEquals(ServerConfig.DIRECTORY.resolve("forwarding.secret"),
            SecretFiles.path("forwarding.secret"));
    }

    @Test
    @DisplayName("surrounding whitespace and the trailing newline are not part of the secret")
    void readingTrims(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("s");
        Files.writeString(file, "  abc123\r\n", StandardCharsets.UTF_8);

        assertEquals("abc123", SecretFiles.read(file.toString()));
    }

    @Test
    @DisplayName("an empty or whitespace-only file counts as having no secret")
    void blankReadsAsAbsent(@TempDir final Path dir) throws IOException {
        final Path empty = dir.resolve("empty");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        final Path spaces = dir.resolve("spaces");
        Files.writeString(spaces, "  \n\t\n", StandardCharsets.UTF_8);

        assertNull(SecretFiles.read(empty.toString()));
        assertNull(SecretFiles.read(spaces.toString()));
    }

    @Test
    @DisplayName("a missing file reads as absent rather than failing")
    void missingReadsAsAbsent(@TempDir final Path dir) {
        assertNull(SecretFiles.read(dir.resolve("nope").toString()));
    }

    @Test
    @DisplayName("writing always uses a single newline, whatever the platform does")
    void writingUsesUnixLineEndings(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("written");

        assertTrue(SecretFiles.write(file.toString(), "abc123"));

        assertEquals("abc123\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("what is written comes back out unchanged")
    void writeThenReadRoundTrips(@TempDir final Path dir) {
        final Path file = dir.resolve("round-trip");

        SecretFiles.write(file.toString(), "s3cr3t-with-symbols_!@#");

        assertEquals("s3cr3t-with-symbols_!@#", SecretFiles.read(file.toString()));
    }

    @Test
    @DisplayName("creating an absent file leaves it empty, never with a comment in it")
    void createLeavesTheFileEmpty(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("fresh");

        SecretFiles.createIfAbsent(file.toString());

        assertTrue(Files.exists(file));
        assertEquals("", Files.readString(file, StandardCharsets.UTF_8),
            "the whole file is the secret, so anything else in it would become part of the value");
    }

    @Test
    @DisplayName("creating does not overwrite a secret that is already there")
    void createKeepsAnExistingSecret(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("existing");
        Files.writeString(file, "keep-me\n", StandardCharsets.UTF_8);

        SecretFiles.createIfAbsent(file.toString());

        assertEquals("keep-me", SecretFiles.read(file.toString()));
    }
}
