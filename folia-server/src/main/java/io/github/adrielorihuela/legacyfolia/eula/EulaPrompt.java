package io.github.adrielorihuela.legacyfolia.eula;

import java.io.IOException;

/**
 * Asks about the EULA at the console instead of stopping the server to go and edit a file.
 *
 * <p>Upstream prints a line about {@code eula.txt} and exits. That is one round trip through a text
 * editor before a brand new server will start, and it is the single most common reason a first
 * start appears to fail. Here the question is asked where the operator already is: press Enter to
 * agree and the server carries on, press Escape and it stops with the file still saying {@code
 * false}.</p>
 *
 * <p>Agreement is never assumed. No key but Enter accepts, a timeout does not accept, and a server
 * with nobody at the console does not accept — it falls back to upstream's behaviour of stopping,
 * because a service under systemd or in a container has no one to ask and would otherwise hang on a
 * prompt nobody can see.</p>
 */
public final class EulaPrompt {

    /** What the operator decided. */
    public enum Decision {
        /** Enter was pressed: the EULA is accepted and the server continues. */
        ACCEPTED,
        /** Escape, Ctrl-C or end of input: the server stops. */
        DECLINED,
        /** Nobody answered in time: the server stops. */
        TIMED_OUT
    }

    /**
     * A source of single keypresses, so the decision can be exercised without a real terminal.
     */
    @FunctionalInterface
    public interface Keys {
        /** End of input, matching {@link java.io.Reader#read()}. */
        int END_OF_INPUT = -1;

        /** Nothing arrived before the deadline, matching JLine's {@code READ_EXPIRED}. */
        int NOTHING = -2;

        /**
         * Waits for one keypress.
         *
         * @param timeoutMillis how long to wait, or zero to wait indefinitely
         * @return the character read, {@link #END_OF_INPUT} or {@link #NOTHING}
         * @throws IOException if the terminal could not be read
         */
        int read(long timeoutMillis) throws IOException;
    }

    private static final int ESCAPE = 27;
    private static final int CTRL_C = 3;
    private static final int CTRL_D = 4;
    private static final int CARRIAGE_RETURN = '\r';
    private static final int LINE_FEED = '\n';

    private EulaPrompt() {
    }

    /**
     * Reads keypresses until one of them means something.
     *
     * <p>Keys that mean nothing here are ignored rather than treated as a refusal: somebody leaning
     * on the keyboard, or a stray escape sequence from a resized terminal, should not decide this.
     * The deadline is absolute, so ignored keys cannot extend the wait indefinitely.</p>
     *
     * @param keys where keypresses come from
     * @param timeoutMillis how long to wait in total, or zero to wait indefinitely
     * @return what was decided
     * @throws IOException if the terminal could not be read
     */
    public static Decision awaitDecision(final Keys keys, final long timeoutMillis)
        throws IOException {
        final long deadline = timeoutMillis > 0 ? System.nanoTime() + timeoutMillis * 1_000_000L : 0;

        while (true) {
            final long remaining;
            if (deadline == 0) {
                remaining = 0;
            } else {
                remaining = (deadline - System.nanoTime()) / 1_000_000L;
                if (remaining <= 0) {
                    return Decision.TIMED_OUT;
                }
            }

            final int key = keys.read(remaining);
            switch (key) {
                case CARRIAGE_RETURN, LINE_FEED -> {
                    return Decision.ACCEPTED;
                }
                case ESCAPE, CTRL_C, CTRL_D, Keys.END_OF_INPUT -> {
                    return Decision.DECLINED;
                }
                case Keys.NOTHING -> {
                    return Decision.TIMED_OUT;
                }
                default -> {
                    // Anything else is not an answer. Keep waiting, within the same deadline.
                }
            }
        }
    }
}
