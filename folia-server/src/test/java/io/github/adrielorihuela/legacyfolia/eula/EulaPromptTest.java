package io.github.adrielorihuela.legacyfolia.eula;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.adrielorihuela.legacyfolia.eula.EulaPrompt.Decision;
import io.github.adrielorihuela.legacyfolia.eula.EulaPrompt.Keys;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Accepting here means agreeing to a legal document on somebody's behalf, so the only thing that
 * may ever return {@link Decision#ACCEPTED} is a deliberate press of Enter.
 */
class EulaPromptTest {

    /** Replays a fixed sequence of keypresses, then behaves as if the input ended. */
    private static Keys typing(final int... keys) {
        final Deque<Integer> queue = new ArrayDeque<>();
        for (final int key : keys) {
            queue.add(key);
        }
        return timeout -> queue.isEmpty() ? Keys.END_OF_INPUT : queue.poll();
    }

    @ParameterizedTest(name = "pressing {0} accepts")
    @ValueSource(ints = {'\r', '\n'})
    @DisplayName("Enter accepts, whichever byte the terminal sends for it")
    void enterAccepts(final int enter) throws IOException {
        assertEquals(Decision.ACCEPTED, EulaPrompt.awaitDecision(typing(enter), 1000));
    }

    @ParameterizedTest(name = "code {0} declines")
    @ValueSource(ints = {27, 3, 4, -1})
    @DisplayName("Escape, Ctrl-C, Ctrl-D and the end of input all decline")
    void refusalsDecline(final int key) throws IOException {
        assertEquals(Decision.DECLINED, EulaPrompt.awaitDecision(typing(key), 1000));
    }

    @Test
    @DisplayName("nothing arriving before the deadline is not agreement")
    void silenceTimesOut() throws IOException {
        assertEquals(Decision.TIMED_OUT,
            EulaPrompt.awaitDecision(timeout -> Keys.NOTHING, 1000));
    }

    @Test
    @DisplayName("keys that mean nothing here are ignored rather than answering")
    void unrelatedKeysAreIgnored() throws IOException {
        assertEquals(Decision.ACCEPTED,
            EulaPrompt.awaitDecision(typing('y', 'e', 's', ' ', '\t', '\r'), 1000));
    }

    @Test
    @DisplayName("a keyboard being leant on cannot extend the wait past the deadline")
    void ignoredKeysDoNotResetTheDeadline() throws IOException {
        // Answers instantly and forever with a key that means nothing, so the only way out is the
        // deadline. Without an absolute deadline this would spin until the test timed out.
        final Decision decision = EulaPrompt.awaitDecision(timeout -> 'x', 50);

        assertEquals(Decision.TIMED_OUT, decision);
    }

    @Test
    @DisplayName("a refusal is taken even when it arrives after other keys")
    void refusalAfterNoiseStillDeclines() throws IOException {
        assertEquals(Decision.DECLINED, EulaPrompt.awaitDecision(typing('a', 'b', 27), 1000));
    }

    @Test
    @DisplayName("waiting indefinitely is allowed, and still only Enter accepts")
    void noDeadlineStillNeedsEnter() throws IOException {
        assertEquals(Decision.DECLINED, EulaPrompt.awaitDecision(typing('q', 27), 0));
    }

    @Test
    @DisplayName("the shrinking deadline is passed down, never a stale or negative one")
    void remainingTimeIsPassedToTheReader() throws IOException {
        final List<Long> seen = new java.util.ArrayList<>();
        EulaPrompt.awaitDecision(timeout -> {
            seen.add(timeout);
            return seen.size() < 3 ? 'x' : '\r';
        }, 5000);

        assertEquals(3, seen.size());
        for (final long timeout : seen) {
            org.junit.jupiter.api.Assertions.assertTrue(timeout > 0 && timeout <= 5000,
                "each wait should be what is left of the deadline, but was " + timeout);
        }
        org.junit.jupiter.api.Assertions.assertTrue(seen.get(2) <= seen.get(0),
            "the deadline should shrink, not reset");
    }
}
