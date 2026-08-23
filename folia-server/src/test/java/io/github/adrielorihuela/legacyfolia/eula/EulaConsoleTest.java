package io.github.adrielorihuela.legacyfolia.eula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.eula.EulaPrompt.Decision;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the question over a pair of streams, which is as close to a real terminal as this can
 * get without one attached.
 */
class EulaConsoleTest {

    /** The Escape key, spelled out because a raw one in the source would be invisible. */
    private static final String ESCAPE = "\033";

    private record Asked(Decision decision, String printed) {
    }

    private static Asked ask(final String typed, final long timeoutMillis) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Terminal terminal = TerminalBuilder.builder()
            .streams(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)), out)
            .dumb(true)
            .build()) {
            final Decision decision = EulaConsole.ask(terminal, timeoutMillis);
            return new Asked(decision, out.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("the question names the EULA and both keys before waiting for one")
    void theQuestionExplainsItself() throws IOException {
        final Asked asked = ask("\r", 1000);

        assertTrue(asked.printed().contains("https://aka.ms/MinecraftEULA"),
            "the operator has to be able to read what they are agreeing to");
        assertTrue(asked.printed().contains("ENTER"), "should say which key agrees");
        assertTrue(asked.printed().contains("ESC"), "should say which key refuses");
    }

    @Test
    @DisplayName("pressing Enter accepts")
    void enterAccepts() throws IOException {
        assertEquals(Decision.ACCEPTED, ask("\r", 1000).decision());
    }

    @Test
    @DisplayName("pressing Escape declines")
    void escapeDeclines() throws IOException {
        assertEquals(Decision.DECLINED, ask(ESCAPE, 1000).decision());
    }

    @Test
    @DisplayName("closed input declines rather than accepting by default")
    void closedInputDeclines() throws IOException {
        assertEquals(Decision.DECLINED, ask("", 1000).decision());
    }

    @Test
    @DisplayName("typing something else and then Escape still declines")
    void noiseThenEscapeDeclines() throws IOException {
        assertEquals(Decision.DECLINED, ask("no" + ESCAPE, 1000).decision());
    }
}
