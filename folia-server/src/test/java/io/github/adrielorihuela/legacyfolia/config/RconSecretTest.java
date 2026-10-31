package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The RCON password file: generated when needed, kept when written, never logged. */
class RconSecretTest {

    /** Everything the LegacyFolia logger says during a test, formatted and with its arguments. */
    private static final class Capture extends AbstractAppender {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        Capture() {
            super("rcon-secret-test", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            this.lines.add(event.getMessage().getFormattedMessage());
            final Object[] parameters = event.getMessage().getParameters();
            if (parameters != null) {
                for (final Object parameter : parameters) {
                    this.lines.add(String.valueOf(parameter));
                }
            }
        }
    }

    private final Capture capture = new Capture();

    private Logger logger;

    private Level previousLevel;

    @BeforeEach
    void listen() {
        this.logger = ((LoggerContext) LogManager.getContext(false)).getLogger("LegacyFolia");
        this.previousLevel = this.logger.getLevel();
        this.capture.start();
        this.logger.addAppender(this.capture);
        this.logger.setLevel(Level.ALL);
    }

    @AfterEach
    void stopListening() {
        this.logger.removeAppender(this.capture);
        this.logger.setLevel(this.previousLevel);
        this.capture.stop();
    }

    @Test
    @DisplayName("a missing file gets 32 random letters and digits, written to it")
    void generatesWhenMissing(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("rcon.secret");
        final String password = RconSecret.resolve(file.toString());
        assertEquals(RconSecret.LENGTH, password.length());
        assertTrue(password.matches("[A-Za-z0-9]{32}"), password);
        assertEquals(password + "\n", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(password, RconSecret.resolve(file.toString()), "the second start reads the same one");
    }

    @Test
    @DisplayName("the password is never in the log; where it is, is")
    void neverLogged(@TempDir final Path dir) {
        final Path file = dir.resolve("rcon.secret");
        final String password = RconSecret.resolve(file.toString());
        assertFalse(this.capture.lines.isEmpty(), "the location is reported");
        assertTrue(this.capture.lines.stream().anyMatch(line -> line.contains(file.toString())), this.capture.lines.toString());
        for (final String line : this.capture.lines) {
            assertFalse(line.contains(password), "the log gave the password away: " + line);
        }
    }

    @Test
    @DisplayName("a password the operator wrote is kept, untouched")
    void keepsTheOperatorsOwn(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("rcon.secret");
        Files.writeString(file, "  correct horse battery staple \r\n", StandardCharsets.UTF_8);
        assertEquals("correct horse battery staple", RconSecret.resolve(file.toString()));
        assertEquals("  correct horse battery staple \r\n", Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(this.capture.lines.isEmpty(), "nothing to say about a password that was already there");
    }

    @Test
    @DisplayName("an empty or blank file is regenerated, which is also how a password is rotated")
    void emptyIsRegenerated(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("rcon.secret");
        Files.writeString(file, " \n", StandardCharsets.UTF_8);
        final String password = RconSecret.resolve(file.toString());
        assertEquals(RconSecret.LENGTH, password.length());
        assertEquals(password + "\n", Files.readString(file, StandardCharsets.UTF_8));

        Files.delete(file);
        final String rotated = RconSecret.resolve(file.toString());
        assertNotEquals(password, rotated);
        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("two generated passwords differ")
    void random() {
        assertNotEquals(RconSecret.generate(), RconSecret.generate());
    }
}
