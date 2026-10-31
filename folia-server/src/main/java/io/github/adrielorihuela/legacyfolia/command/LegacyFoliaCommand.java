package io.github.adrielorihuela.legacyfolia.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.adrielorihuela.legacyfolia.config.ServerProperties;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.command.CommandSender;

/**
 * {@code /legacyfolia properties on|off|status}: the server.properties mode, from the console.
 *
 * <p>Only the console and operators of level 4 may use it, as with {@code /stop}: turning the mode
 * off rewrites the TOML files and deletes {@code server.properties}.</p>
 *
 * <p><b>Threads.</b> Under Folia a command runs on the global region thread when the console sends
 * it and on the player's region thread when a player does. Neither may wait on the disk, and
 * {@code on} and {@code off} read and write several files, so the work runs on a thread of its own
 * and the answer is sent from there when it is done. {@link ServerProperties} serialises the
 * change with every other configuration write, so two of these at once are applied one after the
 * other.</p>
 */
public final class LegacyFoliaCommand {

    /** What {@code /help} says about the command. */
    public static final String DESCRIPTION = "LegacyFolia's own commands: /legacyfolia properties on|off|status";

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "LegacyFolia command");
        thread.setDaemon(true);
        return thread;
    });

    private enum Action { ON, OFF, STATUS }

    private LegacyFoliaCommand() {
    }

    /**
     * The command's tree.
     *
     * @return {@code legacyfolia}
     */
    public static LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("legacyfolia")
            .requires(LegacyFoliaCommand::allowed)
            .executes(LegacyFoliaCommand::usage)
            .then(Commands.literal("properties")
                .executes(LegacyFoliaCommand::usage)
                .then(Commands.literal("on").executes(context -> run(context, Action.ON)))
                .then(Commands.literal("off").executes(context -> run(context, Action.OFF)))
                .then(Commands.literal("status").executes(context -> run(context, Action.STATUS))))
            .build();
    }

    /** The console, or an operator of level 4. */
    private static boolean allowed(final CommandSourceStack source) {
        return source instanceof final net.minecraft.commands.CommandSourceStack vanilla
            && net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_OWNERS).test(vanilla);
    }

    private static int usage(final CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(MM.deserialize(
            "<gray>Usage: <white>/legacyfolia properties <on|off|status></white></gray>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int run(final CommandContext<CommandSourceStack> context, final Action action) {
        final CommandSender sender = context.getSource().getSender();
        CompletableFuture.supplyAsync(() -> switch (action) {
            case ON -> {
                try {
                    yield describe(ServerProperties.enable(), ServerProperties.status());
                } catch (final java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            case OFF -> {
                try {
                    yield describe(ServerProperties.disable(), ServerProperties.status());
                } catch (final java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            case STATUS -> describe(ServerProperties.status());
        }, WORKER).whenComplete((message, failure) -> {
            if (failure != null) {
                final Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                LOGGER.error("/legacyfolia properties {} failed", action.name().toLowerCase(java.util.Locale.ROOT), cause);
                sender.sendMessage(MM.deserialize("<red>Could not change the server.properties mode: <msg>. Nothing was "
                    + "deleted; the console has the details.</red>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("msg", String.valueOf(cause.getMessage()))));
            } else {
                sender.sendMessage(MM.deserialize(message));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * What turning the mode on or off did, as MiniMessage.
     *
     * @param report what the change did
     * @param status the state afterwards
     * @return the message
     */
    public static String describe(final ServerProperties.Report report, final ServerProperties.Status status) {
        final StringBuilder out = new StringBuilder();
        if (report.enabled()) {
            out.append("<green>The server.properties mode is on.</green> <gray>config/").append(ServerProperties.FILE_NAME)
                .append(" holds ").append(status.overrides()).append(" setting(s) that differ from the defaults")
                .append(byFile(status.byFile())).append(", and it wins over the TOML files.</gray>");
            list(out, "Moved there from the TOML files", report.moved());
            list(out, "Set in both, server.properties kept", report.conflicts());
        } else {
            out.append("<yellow>The server.properties mode is off.</yellow> <gray>The TOML files in config/ hold every setting");
            if (!report.transferred().isEmpty()) {
                out.append("; ").append(report.transferred().size()).append(" setting(s) were moved into them from ")
                    .append(ServerProperties.FILE_NAME).append(", which was deleted");
            }
            out.append(".</gray>");
            list(out, "Moved into the TOML files", report.transferred());
            list(out, "Kept in " + ServerProperties.UNUSED_FILE_NAME + " (not settings, or not valid)", report.unused());
        }
        list(out, "Not valid, using the default", report.invalid());
        list(out, "Not settings this server knows", report.unknown());
        return out.toString();
    }

    /**
     * The state of the mode, as MiniMessage.
     *
     * @param status the state
     * @return the message
     */
    public static String describe(final ServerProperties.Status status) {
        final StringBuilder out = new StringBuilder("<gray>server.properties mode: </gray>");
        out.append(status.enabled() ? "<green>on</green>" : "<yellow>off</yellow>");
        out.append("<gray>. File: <white>config/").append(ServerProperties.FILE_NAME).append("</white> (")
            .append(status.fileExists() ? "present" : "absent").append(").</gray>");
        if (status.enabled()) {
            out.append("\n<gray>It changes <white>").append(status.overrides()).append("</white> setting(s) from the defaults")
                .append(byFile(status.byFile())).append(".</gray>");
            if (status.invalid() > 0 || status.unknown() > 0) {
                out.append("\n<yellow>").append(status.invalid()).append(" line(s) not valid and ").append(status.unknown())
                    .append(" not settings; the console log names them.</yellow>");
            }
        } else {
            out.append("\n<gray>The TOML files change <white>").append(status.overrides())
                .append("</white> setting(s) from the defaults").append(byFile(status.byFile()))
                .append("; turning the mode on would move them to server.properties.</gray>");
        }
        return out.toString();
    }

    private static String byFile(final Map<String, Integer> byFile) {
        if (byFile.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(" (");
        boolean first = true;
        for (final Map.Entry<String, Integer> entry : byFile.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(MM.escapeTags(entry.getKey())).append(' ').append(entry.getValue());
            first = false;
        }
        return out.append(')').toString();
    }

    private static void list(final StringBuilder out, final String what, final List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        out.append("\n<gray>").append(what).append(" (").append(keys.size()).append("): <white>");
        final int shown = Math.min(keys.size(), 10);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(MM.escapeTags(keys.get(i)));
        }
        if (keys.size() > shown) {
            out.append(", and ").append(keys.size() - shown).append(" more");
        }
        out.append("</white></gray>");
    }
}
