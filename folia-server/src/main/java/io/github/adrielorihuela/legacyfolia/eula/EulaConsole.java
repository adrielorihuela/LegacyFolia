package io.github.adrielorihuela.legacyfolia.eula;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.config.ServerProperties;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.eula.EulaPrompt.Decision;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Puts the EULA question on the console and writes down the answer.
 *
 * <p>The answer is {@code eula} in {@code config/server.toml}, which replaced {@code eula.txt}. It
 * is written there whatever the server.properties mode says: an agreement never goes into
 * {@code server.properties}, a file people copy from one server to another.</p>
 *
 * <p>This is the half that touches a real terminal; {@link EulaPrompt} is the half that decides
 * what an answer means. They are separate because one of them can be tested and the other cannot.
 * </p>
 */
public final class EulaConsole {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String EULA_URL = "https://aka.ms/MinecraftEULA";

    private static final String TIMEOUT_KEY = "server.eula-prompt-timeout-seconds";

    /** Where the agreement lives, for the messages. */
    private static final String WHERE = "eula = true in config/server.toml";
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private EulaConsole() {
    }

    /**
     * Asks whether the operator agrees, and records it if they do.
     *
     * <p>Returns false without asking anything when there is no console to ask at. A server started
     * by systemd, by a container or with {@code --noconsole} has nobody watching, and a prompt there
     * would be an invisible hang rather than a question — so it stops with the same message
     * upstream prints.</p>
     *
     * @return whether the server may continue
     */
    public static boolean askAndRecord() {
        if (!interactive()) {
            LOGGER.info("You need to agree to the EULA ({}) in order to run the server. "
                + "Set {} to agree.", EULA_URL, WHERE);
            return false;
        }

        final Decision decision;
        try {
            decision = ask();
        } catch (final Exception e) {
            // Deliberately broad: a terminal fails to open in more ways than IOException, and none
            // of them are a reason to stop a server from starting.
            //
            // Said differently from the no-console case on purpose. These two used to print the
            // same line, which is exactly how the bug that made this branch fire every time went
            // unnoticed: there was no way to tell "nobody is watching" from "the question broke".
            LOGGER.warn("Could not ask about the EULA at the console: {}", e.toString());
            LOGGER.warn("Agree by hand instead: set {}.", WHERE);
            LOGGER.debug("Could not ask at the console", e);
            return false;
        }

        switch (decision) {
            case ACCEPTED -> {
                if (!record()) {
                    LOGGER.error("Could not write config/server.toml, so your agreement was not saved. "
                        + "Set {} by hand.", WHERE);
                    return false;
                }
                LOGGER.info("Agreed. config/server.toml now says eula = true; you will not be asked again.");
                return true;
            }
            case TIMED_OUT -> {
                LOGGER.info("Nobody answered, so nothing was agreed to. Start the server again, or "
                    + "set {} by hand.", WHERE);
                return false;
            }
            default -> {
                LOGGER.info("Not agreed, so the server will not start. config/server.toml still says "
                    + "eula = false.");
                return false;
            }
        }
    }

    /**
     * Whether there is somebody at a console to be asked.
     *
     * <p>{@code --noconsole} says so outright. Otherwise the question is whether standard input is
     * a real terminal: under systemd, in a container or behind a pipe it is not, and asking there
     * would be an invisible prompt that looks exactly like the server hanging.</p>
     */
    private static boolean interactive() {
        if (!org.bukkit.craftbukkit.Main.useConsole) {
            return false;
        }
        final java.io.Console console = System.console();
        // isTerminal, not a null check: since Java 22 a Console is handed out even when the
        // streams are redirected, and only this distinguishes the two.
        return console != null && console.isTerminal();
    }

    /**
     * Prints the question and waits for one key.
     *
     * <p>Raw mode is entered so that a single key is enough and no line has to be submitted, and
     * the terminal's previous attributes are always put back, including when the wait is cut
     * short.</p>
     */
    private static Decision ask() throws IOException {
        // The console appender has already built the system terminal by the time this runs --
        // PaperBootstrap logs two lines before Main.main, which initialises it. Asking JLine for a
        // second one throws "A system terminal is already running", so the existing one is borrowed
        // instead, and deliberately not closed: closing it would take the server console with it.
        final Terminal existing = net.minecrell.terminalconsole.TerminalConsoleAppender.getTerminal();
        if (existing != null) {
            return ask(existing, timeoutMillis());
        }

        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(false).build()) {
            return ask(terminal, timeoutMillis());
        }
    }

    /**
     * Asks on a given terminal.
     *
     * <p>Separate from {@link #ask()} so the question can be exercised over a pair of streams,
     * which is the only way to reach this code without a real terminal attached.</p>
     *
     * @param terminal where to ask
     * @param timeoutMillis how long to wait, or zero to wait indefinitely
     * @return what was decided
     * @throws IOException if the terminal could not be read
     */
    static Decision ask(final Terminal terminal, final long timeoutMillis) throws IOException {
        final Attributes original = terminal.enterRawMode();
        try {
            final OutputStream out = terminal.output();
            out.write(banner().getBytes(StandardCharsets.UTF_8));
            out.flush();

            final Decision decision =
                EulaPrompt.awaitDecision(terminal.reader()::read, timeoutMillis);

            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return decision;
        } finally {
            terminal.setAttributes(original);
        }
    }

    private static String banner() {
        final String newline = System.lineSeparator();
        return newline
            + "  You need to agree to the Minecraft EULA before this server will start." + newline
            + "  " + EULA_URL + newline
            + newline
            + "  Press ENTER to agree, or ESC to stop the server. "
            + "(Agreeing writes eula = true to config/server.toml.)" + newline;
    }

    /** How long to wait, from the unified configuration, or the default before it is loaded. */
    private static long timeoutMillis() {
        final int seconds = ServerConfig.isLoaded()
            ? ServerConfig.getInt(TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS)
            : DEFAULT_TIMEOUT_SECONDS;
        return seconds <= 0 ? 0 : seconds * 1000L;
    }

    /**
     * Writes the agreement to {@code server.eula}, waiting until it is on disk so that a crash right
     * after does not ask again.
     */
    private static boolean record() {
        try {
            ServerProperties.set(SettingEntry.EULA, true).join();
            return true;
        } catch (final RuntimeException e) {
            LOGGER.debug("Could not write the EULA agreement", e);
            return false;
        }
    }
}
