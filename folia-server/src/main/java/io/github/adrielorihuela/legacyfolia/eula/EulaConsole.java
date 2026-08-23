package io.github.adrielorihuela.legacyfolia.eula;

import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.eula.EulaPrompt.Decision;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Puts the EULA question on the console and writes down the answer.
 *
 * <p>This is the half that touches a real terminal; {@link EulaPrompt} is the half that decides
 * what an answer means. They are separate because one of them can be tested and the other cannot.
 * </p>
 */
public final class EulaConsole {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String EULA_URL = "https://aka.ms/MinecraftEULA";

    private static final String TIMEOUT_KEY = "server.eula-prompt-timeout-seconds";
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
     * @param eulaFile the {@code eula.txt} to update on agreement
     * @return whether the server may continue
     */
    public static boolean askAndRecord(final Path eulaFile) {
        if (!interactive()) {
            LOGGER.info("You need to agree to the EULA in order to run the server. "
                + "Go to eula.txt for more info.");
            return false;
        }

        final Decision decision;
        try {
            decision = ask();
        } catch (final Exception e) {
            // Deliberately broad. Building a terminal fails in more ways than IOException --
            // JLine throws IllegalStateException when no provider works -- and none of them are
            // a reason to stop a server from starting, which is what an escaping exception did.
            LOGGER.info("You need to agree to the EULA in order to run the server. "
                + "Go to eula.txt for more info.");
            LOGGER.debug("Could not ask at the console", e);
            return false;
        }

        switch (decision) {
            case ACCEPTED -> {
                if (!record(eulaFile)) {
                    LOGGER.error("Could not write {}, so your agreement was not saved. "
                        + "Set eula=true in it by hand.", eulaFile);
                    return false;
                }
                LOGGER.info("Agreed. {} now says eula=true; you will not be asked again.", eulaFile);
                return true;
            }
            case TIMED_OUT -> {
                LOGGER.info("Nobody answered, so nothing was agreed to. Start the server again, or "
                    + "set eula=true in {} by hand.", eulaFile);
                return false;
            }
            default -> {
                LOGGER.info("Not agreed, so the server will not start. {} still says eula=false.",
                    eulaFile);
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
            + "(Agreeing writes eula=true to eula.txt.)" + newline;
    }

    /** How long to wait, from the unified configuration, or the default before it is loaded. */
    private static long timeoutMillis() {
        final int seconds = ServerConfig.isLoaded()
            ? ServerConfig.get().getInt(TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS)
            : DEFAULT_TIMEOUT_SECONDS;
        return seconds <= 0 ? 0 : seconds * 1000L;
    }

    /**
     * Rewrites {@code eula.txt} with the agreement recorded, keeping the comment Mojang's file
     * carries so the file still explains itself.
     */
    private static boolean record(final Path eulaFile) {
        try (OutputStream output = Files.newOutputStream(eulaFile)) {
            final Properties properties = new Properties();
            properties.setProperty("eula", "true");
            properties.store(output, "By setting the value below to TRUE you are indicating your "
                + "agreement to our EULA (" + EULA_URL + "). Agreed to at the server console.");
            return true;
        } catch (final IOException e) {
            LOGGER.debug("Could not write {}", eulaFile, e);
            return false;
        }
    }
}
