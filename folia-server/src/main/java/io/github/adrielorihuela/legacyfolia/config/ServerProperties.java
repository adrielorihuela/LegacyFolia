package io.github.adrielorihuela.legacyfolia.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.properties.AtomicFiles;
import io.github.adrielorihuela.legacyfolia.config.properties.PropertiesDocument;
import io.github.adrielorihuela.legacyfolia.config.properties.PropertyValues;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlLiterals;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlText;
import io.github.adrielorihuela.legacyfolia.config.types.InvalidSettingException;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The server.properties mode: a {@code config/server.properties} that holds only what differs from
 * the defaults, and wins.
 *
 * <p>Panels such as Pterodactyl read and write {@code server.properties}, and operators have been
 * editing it for fifteen years. With the mode on, which is the default, that file is where every
 * change lives: the TOML files stay complete and at their defaults, as a commented reference, and
 * {@code server.properties} lists only the settings somebody changed. Deleting a line puts a setting
 * back to its default; adding one changes it. Vanilla settings keep their vanilla names
 * ({@code motd}, {@code max-players}) so the panels keep working; everything else is written by its
 * TOML path, and a per-world override as {@code worlds.<world>.<path>}.</p>
 *
 * <p>With the mode off the TOML files are the configuration and {@code server.properties} does not
 * exist.</p>
 *
 * <p><b>When the two are compared.</b> {@link #reconcile()} runs at start-up and on reload, right
 * after {@link ServerConfig#load()}, never while the server runs. It moves any value an operator
 * edited into a TOML file over to {@code server.properties} and puts the TOML back to its default;
 * when both say something different, {@code server.properties} wins and the TOML value it discarded
 * is logged. It validates every line, keeps lines it does not understand, and hands the effective
 * values to {@link ServerConfig}, so that {@code ServerConfig.getInt(...)} and
 * {@code ServerConfig.forWorld(...)} answer with the override applied and no caller changes.</p>
 *
 * <p><b>Writes.</b> {@link #set(String, Object)} and its relatives are what commands such as
 * {@code /difficulty} or {@code /whitelist on} call. They may come from any region thread. The new
 * value is visible to every reader as soon as the call returns; the file write happens on one
 * dedicated thread, in the order the calls were made, each one reading the file, changing its own
 * line and replacing the file atomically. A region thread never waits on the disk, two writes never
 * interleave, and an operator's hand edit made in between is not overwritten.</p>
 *
 * <p>Secrets ({@link SettingEntry#secret()}) and the switch itself never go to
 * {@code server.properties}; they always live in their TOML file.</p>
 */
public final class ServerProperties {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** The file's name within the configuration directory. */
    public static final String FILE_NAME = "server.properties";

    /**
     * Where lines that could not be moved into the TOML files go when the mode is turned off:
     * keys nothing recognises and values that are not valid. Nothing is deleted unread.
     */
    public static final String UNUSED_FILE_NAME = "server.properties.unused";

    /** The comment a new server.properties starts with. ASCII only. */
    public static final List<String> HEADER = List.of(
        "# LegacyFolia server.properties",
        "#",
        "# This file holds only the settings that differ from their defaults, and",
        "# what it says wins over the TOML files beside it, which keep the defaults",
        "# as a commented reference. Delete a line to put a setting back to its",
        "# default; add one to change it. Vanilla settings keep their usual names",
        "# (motd, max-players); every other setting uses its TOML path, such as",
        "# gameplay.hunger.jump-walk-exhaustion, and a per-world one",
        "# worlds.<world>.<path>. Read at start-up and on reload.",
        "#",
        "# Turn this off with use-server-properties = false in server.toml, or with",
        "# /legacyfolia properties off: these settings then move into the TOML files.",
        "");

    private static final String WORLDS_PREFIX = "worlds.";

    /** Serialises every change of mode and every write. */
    private static final ReentrantLock LOCK = new ReentrantLock(true);

    /** Unknown keys already reported, so that a reload does not repeat the same warning. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static @Nullable ExecutorService writer;

    /** The mode in force, or {@code null} before the first {@link #reconcile()}. */
    private static volatile @Nullable Boolean enabled;

    private ServerProperties() {
    }

    // ------------------------------------------------------------------------------------------
    //  Results
    // ------------------------------------------------------------------------------------------

    /**
     * What a reconcile, an enable or a disable did. Each list holds {@code server.properties} keys.
     *
     * @param enabled whether the mode is on afterwards
     * @param created whether a new server.properties was created
     * @param moved values edited into a TOML file and moved to server.properties
     * @param conflicts keys set differently in both places; server.properties won
     * @param removedDefaults lines removed from server.properties because they said the default
     * @param invalid lines whose value is not valid, each as {@code key (line N): reason}; the
     *     default is used
     * @param unknown keys that name no setting, kept untouched
     * @param transferred values written back to the TOML files when the mode was turned off
     * @param unused lines kept in {@link #UNUSED_FILE_NAME} when the mode was turned off
     */
    public record Report(boolean enabled, boolean created, List<String> moved, List<String> conflicts,
                         List<String> removedDefaults, List<String> invalid, List<String> unknown,
                         List<String> transferred, List<String> unused) {
    }

    /**
     * The state of the mode, for {@code /legacyfolia properties status}.
     *
     * @param enabled whether the mode is on
     * @param file where server.properties is, or would be
     * @param fileExists whether it exists
     * @param overrides with the mode on, how many valid settings server.properties changes; with it
     *     off, how many settings the TOML files change from the default, which is what turning the
     *     mode on would move
     * @param byFile the same count by the TOML file each setting belongs to ({@code worlds.toml} for
     *     the per-world ones), in file order
     * @param invalid lines whose value is not valid (mode on only)
     * @param unknown lines that name no setting (mode on only)
     */
    public record Status(boolean enabled, Path file, boolean fileExists, int overrides, Map<String, Integer> byFile,
                         int invalid, int unknown) {
    }

    private record Target(SettingEntry entry, @Nullable String world) {
    }

    /** One line of server.properties, read and checked. */
    private record Line(String key, String text, int line, Target target, Object value, @Nullable String error) {

        boolean valid() {
            return this.error == null;
        }
    }

    /** Every line of a server.properties, sorted into what it means. */
    private record Analysis(Map<String, Line> global, Map<String, Map<String, Line>> worlds,
                            Map<String, Integer> unknown, List<String> invalid) {

        @Nullable Line byKey(final String key) {
            for (final Line line : this.global.values()) {
                if (line.key.equals(key)) {
                    return line;
                }
            }
            for (final Map<String, Line> world : this.worlds.values()) {
                for (final Line line : world.values()) {
                    if (line.key.equals(key)) {
                        return line;
                    }
                }
            }
            return null;
        }
    }

    /** Collects what a run did. */
    private static final class Builder {
        boolean created;
        final List<String> moved = new ArrayList<>();
        final List<String> conflicts = new ArrayList<>();
        final List<String> removedDefaults = new ArrayList<>();
        final List<String> invalid = new ArrayList<>();
        final List<String> unknown = new ArrayList<>();
        final List<String> transferred = new ArrayList<>();
        final List<String> unused = new ArrayList<>();

        Report build(final boolean on) {
            return new Report(on, this.created, List.copyOf(this.moved), List.copyOf(this.conflicts),
                List.copyOf(this.removedDefaults), List.copyOf(this.invalid), List.copyOf(this.unknown),
                List.copyOf(this.transferred), List.copyOf(this.unused));
        }
    }

    /** The TOML files being edited in one run, read once and written once. */
    private static final class Tomls {
        private final Path directory;
        private final Map<String, TomlText> texts = new LinkedHashMap<>();
        private final Set<String> changed = new java.util.HashSet<>();

        Tomls(final Path directory) {
            this.directory = directory;
        }

        void edit(final String fileName, final UnaryOperator<TomlText> edit) throws IOException {
            TomlText text = this.texts.get(fileName);
            if (text == null) {
                text = TomlText.parse(AtomicFiles.read(this.directory.resolve(fileName)));
            }
            final TomlText edited = edit.apply(text);
            this.texts.put(fileName, edited);
            if (!edited.text().equals(text.text())) {
                this.changed.add(fileName);
            }
        }

        boolean write() throws IOException {
            for (final String fileName : this.changed) {
                AtomicFiles.write(this.directory.resolve(fileName), this.texts.get(fileName).text());
            }
            return !this.changed.isEmpty();
        }
    }

    // ------------------------------------------------------------------------------------------
    //  Start-up, reload and the command
    // ------------------------------------------------------------------------------------------

    /**
     * Compares server.properties with the TOML files and works out the values the server uses.
     *
     * <p>Call it right after {@link ServerConfig#load()}, at start-up and on every reload. With the
     * mode on it moves edited TOML values into server.properties, resets those TOML values, drops
     * lines that say the default, and reports invalid and unknown lines. With the mode off and a
     * server.properties still there — the switch was turned off by hand — it does what
     * {@link #disable()} does. Either way {@link ServerConfig} answers with the result afterwards.</p>
     *
     * @return what it did
     * @throws IOException if a file could not be read or written; nothing is deleted in that case
     */
    public static Report reconcile() throws IOException {
        LOCK.lock();
        try {
            drainWrites();
            requireLoaded();
            return readSwitch() ? reconcileOn() : reconcileOff(new Builder());
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Turns the mode on, saves the switch, and moves every non-default TOML value into
     * server.properties.
     *
     * @return what it did
     * @throws IOException if a file could not be read or written
     */
    public static Report enable() throws IOException {
        LOCK.lock();
        try {
            drainWrites();
            requireLoaded();
            if (!readSwitch()) {
                final Tomls tomls = new Tomls(ServerConfig.directory());
                tomls.edit(ServerConfig.SERVER, text -> text.set(List.of(), SettingEntry.USE_SERVER_PROPERTIES, "true", false));
                tomls.write();
                ServerConfig.load(ServerConfig.directory());
                LOGGER.info("Turned the server.properties mode on.");
            }
            return reconcileOn();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Turns the mode off: writes what server.properties says into the TOML files, saves the switch,
     * and only then deletes server.properties.
     *
     * <p>Lines that cannot go into a TOML file, because they name no setting or their value is not
     * valid, are kept in {@link #UNUSED_FILE_NAME} rather than lost.</p>
     *
     * @return what it did
     * @throws IOException if a file could not be read or written; server.properties is then kept
     */
    public static Report disable() throws IOException {
        LOCK.lock();
        try {
            drainWrites();
            requireLoaded();
            final Builder report = new Builder();
            final Tomls tomls = new Tomls(ServerConfig.directory());
            if (readSwitch()) {
                tomls.edit(ServerConfig.SERVER, text -> text.set(List.of(), SettingEntry.USE_SERVER_PROPERTIES, "false", false));
            }
            final Report done = reconcileOff(report, tomls);
            LOGGER.info("Turned the server.properties mode off.");
            return done;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Whether the mode is on, as of the last reconcile, enable or disable.
     *
     * @return whether it is
     * @throws IllegalStateException before the first {@link #reconcile()}
     */
    public static boolean isEnabled() {
        final Boolean on = enabled;
        if (on == null) {
            throw new IllegalStateException("ServerProperties.reconcile() has not run yet");
        }
        return on;
    }

    /** Where server.properties is, or would be. */
    public static Path file() {
        return ServerConfig.directory().resolve(FILE_NAME);
    }

    /**
     * How many settings are changed, and where.
     *
     * @return the state
     * @throws IllegalStateException before the first {@link #reconcile()}
     */
    public static Status status() {
        LOCK.lock();
        try {
            final boolean on = isEnabled();
            drainWrites();
            final Path file = file();
            final Map<String, Integer> byFile = new TreeMap<>(ServerProperties::compareFiles);
            if (on) {
                final Analysis analysis = analyze(read(file));
                for (final Line line : analysis.global.values()) {
                    if (line.valid()) {
                        byFile.merge(line.target.entry.tomlFile(), 1, Integer::sum);
                    }
                }
                for (final Map<String, Line> world : analysis.worlds.values()) {
                    for (final Line line : world.values()) {
                        if (line.valid()) {
                            byFile.merge(ServerConfig.WORLDS, 1, Integer::sum);
                        }
                    }
                }
                return new Status(true, file, Files.exists(file), total(byFile), byFile,
                    analysis.invalid.size(), analysis.unknown.size());
            }
            final ServerConfig.Overrides current = ServerConfig.overrides();
            if (current != null) {
                for (final SettingEntry entry : SettingsCatalog.get().all()) {
                    final Object value = current.global().get(entry.tomlPath());
                    if (entry.allowedInServerProperties() && value != null
                        && !PropertyValues.same(entry, value, entry.defaultValue())) {
                        byFile.merge(entry.tomlFile(), 1, Integer::sum);
                    }
                }
                for (final Map<String, Object> world : current.worlds().values()) {
                    for (final String path : world.keySet()) {
                        if (perWorldEntry(path) != null) {
                            byFile.merge(ServerConfig.WORLDS, 1, Integer::sum);
                        }
                    }
                }
            }
            return new Status(false, file, Files.exists(file), total(byFile), byFile, 0, 0);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            LOCK.unlock();
        }
    }

    private static int compareFiles(final String a, final String b) {
        return Integer.compare(ServerConfig.fileNames().indexOf(a), ServerConfig.fileNames().indexOf(b));
    }

    private static int total(final Map<String, Integer> byFile) {
        return byFile.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ------------------------------------------------------------------------------------------
    //  Writes while running
    // ------------------------------------------------------------------------------------------

    /**
     * Changes a setting for the whole server, from any thread.
     *
     * <p>Readers see the new value as soon as this returns. The file — server.properties with the
     * mode on, the setting's TOML file with it off or for a secret — is written on the writer thread;
     * the future completes when it has been. Setting a value back to its default removes its line
     * from server.properties.</p>
     *
     * @param path the TOML path, such as {@code server.difficulty}
     * @param value the value as the TOML file would hold it: a {@code Boolean}, a number, a
     *     {@code String}, a {@code List} or a {@code Map}
     * @return completes once the file is written
     * @throws IllegalArgumentException if no such setting exists, or for the switch itself, which
     *     {@link #enable()} and {@link #disable()} change
     * @throws InvalidSettingException if the value is not valid for the setting
     */
    public static CompletableFuture<Void> set(final String path, final Object value) {
        return write(path, value, null);
    }

    /**
     * Changes a setting for one world, from any thread.
     *
     * @param path the TOML path of a per-world setting
     * @param value the value
     * @param world the world's folder name, or {@code default} for every world
     * @return completes once the file is written
     * @throws IllegalArgumentException if no such setting exists or it is not per world
     * @throws InvalidSettingException if the value is not valid for the setting
     */
    public static CompletableFuture<Void> set(final String path, final Object value, final String world) {
        return write(path, value, world);
    }

    /**
     * Puts a setting back to its default for the whole server.
     *
     * @param path the TOML path
     * @return completes once the file is written
     */
    public static CompletableFuture<Void> reset(final String path) {
        return write(path, null, null);
    }

    /**
     * Removes one world's override of a setting, so the world goes back to what every world uses.
     *
     * @param path the TOML path of a per-world setting
     * @param world the world's folder name
     * @return completes once the file is written
     */
    public static CompletableFuture<Void> reset(final String path, final String world) {
        return write(path, null, world);
    }

    /**
     * Waits until every write so far is on disk. The server calls this before it stops.
     */
    public static void flush() {
        final ExecutorService current;
        synchronized (ServerProperties.class) {
            current = writer;
        }
        if (current != null) {
            CompletableFuture.runAsync(() -> { }, current).join();
        }
    }

    private static CompletableFuture<Void> write(final String path, final @Nullable Object value,
                                                 final @Nullable String world) {
        final SettingEntry entry = SettingsCatalog.get().require(path);
        if (SettingEntry.USE_SERVER_PROPERTIES.equals(path)) {
            throw new IllegalArgumentException("the server.properties switch is changed with enable() or disable()");
        }
        if (world != null && !entry.perWorld()) {
            throw new IllegalArgumentException(path + " is not a per-world setting");
        }
        final Object plain = value == null ? null : SettingsCatalog.plain(value);
        if (plain != null) {
            SettingReaders.convert(entry, plain);
        }

        LOCK.lock();
        try {
            final boolean on = isEnabled();
            final ServerConfig.Overrides current = ServerConfig.overrides();
            final Map<String, Object> global = new HashMap<>(current == null ? Map.of() : current.global());
            final Map<String, Map<String, Object>> worlds = new HashMap<>();
            if (current != null) {
                current.worlds().forEach((name, values) -> worlds.put(name, new HashMap<>(values)));
            }
            if (world == null) {
                global.put(path, plain == null ? entry.defaultValue() : plain);
            } else if (plain == null) {
                worlds.getOrDefault(world, new HashMap<>()).remove(path);
            } else {
                worlds.computeIfAbsent(world, name -> new HashMap<>()).put(path, plain);
            }
            ServerConfig.setOverrides(ServerConfig.Overrides.of(global, worlds));

            final Path directory = ServerConfig.directory();
            if (on && entry.allowedInServerProperties()) {
                final String key = world == null ? entry.serverPropertiesKey() : entry.serverPropertiesKey(world);
                final boolean drop = plain == null || world == null && PropertyValues.same(entry, plain, entry.defaultValue());
                final String text = plain == null ? "" : PropertyValues.render(entry, plain);
                return submit(() -> {
                    final Path file = directory.resolve(FILE_NAME);
                    final boolean exists = Files.exists(file);
                    final PropertiesDocument document = exists ? read(file) : PropertiesDocument.create(HEADER);
                    final boolean changed = drop ? document.remove(key)
                        : document.set(key, text, ServerProperties::groupOf, ServerProperties::groupLabel);
                    if (changed || !exists) {
                        AtomicFiles.write(file, document.render());
                    }
                });
            }
            final String literal = plain == null ? TomlLiterals.render(entry.defaultValue()) : TomlLiterals.render(plain);
            return submit(() -> {
                final Tomls tomls = new Tomls(directory);
                if (world == null) {
                    tomls.edit(entry.tomlFile(), text -> text.set(List.of(), path, literal, false));
                } else if (plain == null) {
                    tomls.edit(ServerConfig.WORLDS, text -> text.remove(List.of("worlds", world), path)
                        .removeEmptyTables(List.of("worlds"), List.of("worlds", WorldConfig.DEFAULT)));
                } else {
                    tomls.edit(ServerConfig.WORLDS, text -> text.set(List.of("worlds", world), path, literal, true));
                }
                tomls.write();
            });
        } finally {
            LOCK.unlock();
        }
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }

    private static CompletableFuture<Void> submit(final IoTask task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (final IOException e) {
                LOGGER.error("Could not save a configuration change: {}", e.getMessage(), e);
                throw new UncheckedIOException(e);
            } catch (final RuntimeException e) {
                LOGGER.error("Could not save a configuration change: {}", e.getMessage(), e);
                throw e;
            }
        }, writer());
    }

    private static synchronized ExecutorService writer() {
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "LegacyFolia config writer");
                thread.setDaemon(true);
                return thread;
            });
        }
        return writer;
    }

    /** Waits for queued writes, so that a reconcile reads files nobody else is about to replace. */
    private static void drainWrites() {
        try {
            flush();
        } catch (final RuntimeException e) {
            // A failed write has been logged already; the reconcile that follows reads the files
            // as they are, which is the best anybody can do.
        }
    }

    // ------------------------------------------------------------------------------------------
    //  The two directions
    // ------------------------------------------------------------------------------------------

    private static Report reconcileOn() throws IOException {
        final Builder report = new Builder();
        final SettingsCatalog catalog = SettingsCatalog.get();
        final Path directory = ServerConfig.directory();
        final Path file = directory.resolve(FILE_NAME);

        report.created = !Files.exists(file);
        final PropertiesDocument document = report.created ? PropertiesDocument.create(HEADER) : read(file);
        final Analysis before = analyze(document);
        boolean documentChanged = report.created;
        final Tomls tomls = new Tomls(directory);

        // A value edited into a TOML file moves to server.properties, and the TOML goes back to
        // its default.
        for (final SettingEntry entry : catalog.all()) {
            if (!entry.allowedInServerProperties()) {
                continue;
            }
            final Object current = ServerConfig.file(entry.tomlFile()).raw().get(entry.tomlPath());
            if (current == null || PropertyValues.same(entry, current, entry.defaultValue())) {
                continue;
            }
            final String key = entry.serverPropertiesKey();
            documentChanged |= moveOrConflict(document, before, report, entry, key, current, entry.tomlFile());
            tomls.edit(entry.tomlFile(), text -> text.set(List.of(), entry.tomlPath(),
                TomlLiterals.render(entry.defaultValue()), false));
        }

        // So does every per-world override in worlds.toml.
        boolean worldsEdited = false;
        for (final Map.Entry<String, Map<String, Object>> world : worldsToml(true).entrySet()) {
            for (final Map.Entry<String, Object> override : world.getValue().entrySet()) {
                final SettingEntry entry = catalog.require(override.getKey());
                final String key = entry.serverPropertiesKey(world.getKey());
                documentChanged |= moveOrConflict(document, before, report, entry, key, override.getValue(),
                    ServerConfig.WORLDS);
                tomls.edit(ServerConfig.WORLDS, text -> text.remove(List.of("worlds", world.getKey()), entry.tomlPath()));
                worldsEdited = true;
            }
        }
        if (worldsEdited) {
            tomls.edit(ServerConfig.WORLDS, text -> text.removeEmptyTables(List.of("worlds"),
                List.of("worlds", WorldConfig.DEFAULT)));
        }

        // A line that says the default changes nothing, and the file is for what does.
        for (final Line line : before.global.values()) {
            if (line.valid() && PropertyValues.same(line.target.entry, line.value, line.target.entry.defaultValue())) {
                document.remove(line.key);
                documentChanged = true;
                report.removedDefaults.add(line.key);
                LOGGER.info("config/{}: {} is the default; removed the line.", FILE_NAME, line.key);
            }
        }

        // server.properties first: if the server stops in between, a value is in both places,
        // which the next start resolves without a word, rather than in neither.
        if (documentChanged) {
            AtomicFiles.write(file, document.render());
        }
        if (tomls.write()) {
            ServerConfig.load(directory);
        }
        if (report.created) {
            LOGGER.info("Created config/{}. It lists only what differs from the defaults.", FILE_NAME);
        }

        // Line numbers as the file now stands, for the warnings.
        final Analysis after = analyze(read(file));
        warn(after, report);

        final Map<String, Object> global = new HashMap<>();
        for (final SettingEntry entry : catalog.all()) {
            if (entry.allowedInServerProperties()) {
                final Line line = after.global.get(entry.tomlPath());
                global.put(entry.tomlPath(), line != null && line.valid() ? line.value : entry.defaultValue());
            } else {
                global.put(entry.tomlPath(), fileValue(entry));
            }
        }
        final Map<String, Map<String, Object>> worlds = worldsToml(false);
        for (final Map.Entry<String, Map<String, Line>> world : after.worlds.entrySet()) {
            for (final Line line : world.getValue().values()) {
                if (line.valid()) {
                    worlds.computeIfAbsent(world.getKey(), name -> new HashMap<>())
                        .put(line.target.entry.tomlPath(), line.value);
                }
            }
        }
        ServerConfig.setOverrides(ServerConfig.Overrides.of(global, worlds));
        enabled = true;
        return report.build(true);
    }

    /** Moves one TOML value into the document unless the document already has the key. */
    private static boolean moveOrConflict(final PropertiesDocument document, final Analysis before, final Builder report,
                                          final SettingEntry entry, final String key, final Object tomlValue,
                                          final String tomlFile) {
        final Line existing = before.byKey(key);
        if (existing == null) {
            document.set(key, PropertyValues.render(entry, tomlValue), ServerProperties::groupOf,
                ServerProperties::groupLabel);
            report.moved.add(key);
            LOGGER.info("config/{}: moved {} = {} into config/{}, and set the TOML back to its default.",
                tomlFile, entry.tomlPath(), TomlLiterals.render(tomlValue), FILE_NAME);
            return true;
        }
        if (!existing.valid() || !PropertyValues.same(entry, existing.value, tomlValue)) {
            report.conflicts.add(key);
            LOGGER.warn("{} is set in both config/{} ({}) and config/{} ({}). config/{} wins; config/{} goes "
                    + "back to the default, and the value it had is the one in this message.",
                key, FILE_NAME, existing.text, tomlFile, TomlLiterals.render(tomlValue), FILE_NAME, tomlFile);
        }
        return false;
    }

    private static Report reconcileOff(final Builder report) throws IOException {
        return reconcileOff(report, new Tomls(ServerConfig.directory()));
    }

    /**
     * Writes server.properties into the TOML files, if there is one, then deletes it; and works out
     * the values from the TOML files alone.
     */
    private static Report reconcileOff(final Builder report, final Tomls tomls) throws IOException {
        final Path directory = ServerConfig.directory();
        final Path file = directory.resolve(FILE_NAME);
        PropertiesDocument leftover = null;
        if (Files.exists(file)) {
            final PropertiesDocument document = read(file);
            final Analysis analysis = analyze(document);
            warn(analysis, report);
            for (final Line line : analysis.global.values()) {
                if (line.valid()) {
                    final SettingEntry entry = line.target.entry;
                    tomls.edit(entry.tomlFile(), text -> text.set(List.of(), entry.tomlPath(),
                        TomlLiterals.render(line.value), false));
                    report.transferred.add(line.key);
                    document.remove(line.key);
                }
            }
            for (final Map.Entry<String, Map<String, Line>> world : analysis.worlds.entrySet()) {
                for (final Line line : world.getValue().values()) {
                    if (line.valid()) {
                        tomls.edit(ServerConfig.WORLDS, text -> text.set(List.of("worlds", world.getKey()),
                            line.target.entry.tomlPath(), TomlLiterals.render(line.value), true));
                        report.transferred.add(line.key);
                        document.remove(line.key);
                    }
                }
            }
            if (!document.isEmpty()) {
                leftover = document;
                for (final PropertiesDocument.Entry entry : document.entries()) {
                    report.unused.add(entry.key());
                }
            }
        }

        // In this order: the TOML files, then whatever could not go into them, then the delete.
        tomls.write();
        if (leftover != null) {
            final Path unused = unusedFile(directory);
            AtomicFiles.write(unused, leftover.render());
            LOGGER.warn("config/{} had {} line(s) that could not be moved into the TOML files: {}. They are "
                + "kept in {}.", FILE_NAME, report.unused.size(), String.join(", ", report.unused), unused.getFileName());
        }
        if (Files.exists(file)) {
            Files.delete(file);
            LOGGER.info("Moved {} setting(s) from config/{} into the TOML files and deleted it.",
                report.transferred.size(), FILE_NAME);
        }
        ServerConfig.load(directory);

        final Map<String, Object> global = new HashMap<>();
        for (final SettingEntry entry : SettingsCatalog.get().all()) {
            global.put(entry.tomlPath(), fileValue(entry));
        }
        ServerConfig.setOverrides(ServerConfig.Overrides.of(global, worldsToml(false)));
        enabled = false;
        return report.build(false);
    }

    private static Path unusedFile(final Path directory) {
        Path candidate = directory.resolve(UNUSED_FILE_NAME);
        for (int i = 2; Files.exists(candidate); i++) {
            candidate = directory.resolve(UNUSED_FILE_NAME + "." + i);
        }
        return candidate;
    }

    // ------------------------------------------------------------------------------------------
    //  Reading
    // ------------------------------------------------------------------------------------------

    private static void requireLoaded() {
        if (!ServerConfig.isLoaded()) {
            throw new IllegalStateException("ServerConfig.load() has to run before ServerProperties");
        }
    }

    private static boolean readSwitch() {
        final Object value = ServerConfig.file(ServerConfig.SERVER).raw().get(SettingEntry.USE_SERVER_PROPERTIES);
        if (value instanceof final Boolean on) {
            return on;
        }
        if (value != null) {
            LOGGER.warn("config/{}: {} should be true or false, not {}. Using true.", ServerConfig.SERVER,
                SettingEntry.USE_SERVER_PROPERTIES, value);
        }
        return true;
    }

    private static PropertiesDocument read(final Path file) throws IOException {
        if (!Files.exists(file)) {
            return PropertiesDocument.create(List.of());
        }
        return PropertiesDocument.parse(PropertiesDocument.decode(Files.readAllBytes(file)));
    }

    private static Object fileValue(final SettingEntry entry) {
        final Object value = ServerConfig.file(entry.tomlFile()).raw().get(entry.tomlPath());
        return value == null ? entry.defaultValue() : SettingsCatalog.plain(value);
    }

    /** What a server.properties key stands for, or {@code null} if it names no setting it may hold. */
    private static @Nullable Target target(final String key) {
        final SettingsCatalog catalog = SettingsCatalog.get();
        final Optional<SettingEntry> global = catalog.byServerPropertiesKey(key);
        if (global.isPresent()) {
            return new Target(global.get(), null);
        }
        if (key.startsWith(WORLDS_PREFIX)) {
            for (int dot = key.indexOf('.', WORLDS_PREFIX.length()); dot != -1; dot = key.indexOf('.', dot + 1)) {
                final String world = key.substring(WORLDS_PREFIX.length(), dot);
                final SettingEntry entry = perWorldEntry(key.substring(dot + 1));
                if (!world.isEmpty() && entry != null) {
                    return new Target(entry, world);
                }
            }
        }
        return null;
    }

    private static @Nullable SettingEntry perWorldEntry(final String path) {
        return SettingsCatalog.get().byPath(path)
            .filter(entry -> entry.perWorld() && entry.allowedInServerProperties())
            .orElse(null);
    }

    private static Analysis analyze(final PropertiesDocument document) {
        final Map<String, Line> global = new LinkedHashMap<>();
        final Map<String, Map<String, Line>> worlds = new LinkedHashMap<>();
        final Map<String, Integer> unknown = new LinkedHashMap<>();
        final List<String> invalid = new ArrayList<>();
        for (final PropertiesDocument.Entry entry : document.entries()) {
            final Target target = target(entry.key());
            if (target == null) {
                unknown.put(entry.key(), entry.line());
                continue;
            }
            final Object value = PropertyValues.parse(target.entry, entry.value());
            String error = null;
            try {
                SettingReaders.convert(target.entry, value);
            } catch (final RuntimeException e) {
                error = e.getMessage();
            }
            final Line line = new Line(entry.key(), entry.value(), entry.line(), target, value, error);
            // A key written twice: the last one counts, as with java.util.Properties.
            if (target.world == null) {
                global.put(target.entry.tomlPath(), line);
            } else {
                worlds.computeIfAbsent(target.world, name -> new LinkedHashMap<>()).put(target.entry.tomlPath(), line);
            }
        }
        for (final Line line : global.values()) {
            if (!line.valid()) {
                invalid.add(line.key + " (line " + line.line + "): " + line.error);
            }
        }
        for (final Map<String, Line> world : worlds.values()) {
            for (final Line line : world.values()) {
                if (!line.valid()) {
                    invalid.add(line.key + " (line " + line.line + "): " + line.error);
                }
            }
        }
        return new Analysis(global, worlds, unknown, invalid);
    }

    /** Logs the invalid and unknown lines of an analysis and records them in the report. */
    private static void warn(final Analysis analysis, final Builder report) {
        for (final Line line : analysis.global.values()) {
            if (!line.valid()) {
                LOGGER.warn("config/{} line {}: {}={} is not valid ({}). Using the default, {}.", FILE_NAME, line.line,
                    line.key, line.text, line.error, TomlLiterals.render(line.target.entry.defaultValue()));
            }
        }
        for (final Map<String, Line> world : analysis.worlds.values()) {
            for (final Line line : world.values()) {
                if (!line.valid()) {
                    LOGGER.warn("config/{} line {}: {}={} is not valid ({}). The world keeps the value every "
                        + "world uses.", FILE_NAME, line.line, line.key, line.text, line.error);
                }
            }
        }
        report.invalid.addAll(analysis.invalid);
        for (final Map.Entry<String, Integer> unknown : analysis.unknown.entrySet()) {
            report.unknown.add(unknown.getKey());
            if (WARNED.add(unknown.getKey())) {
                LOGGER.warn("config/{} line {}: {} is not a setting this server knows{}. The line is kept as it "
                    + "is and ignored.", FILE_NAME, unknown.getValue(), unknown.getKey(), hint(unknown.getKey()));
            }
        }
    }

    private static String hint(final String key) {
        final Optional<SettingEntry> byPath = SettingsCatalog.get().byPath(key);
        if (byPath.isPresent()) {
            final SettingEntry entry = byPath.get();
            if (!entry.allowedInServerProperties()) {
                return " in this file: " + key + " can only be set in config/" + entry.tomlFile();
            }
            return "; did you mean " + entry.serverPropertiesKey() + "?";
        }
        return "";
    }

    /**
     * The per-world overrides worlds.toml holds, by world and then by dotted path.
     *
     * @param catalogued {@code true} for only the overrides of catalogued per-world settings, which
     *     are what moves to server.properties; {@code false} for the rest, which stay in worlds.toml
     */
    private static Map<String, Map<String, Object>> worldsToml(final boolean catalogued) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        final Object worlds = ServerConfig.file(ServerConfig.WORLDS).raw().get(List.of("worlds"));
        if (worlds instanceof final UnmodifiableConfig table) {
            for (final UnmodifiableConfig.Entry world : table.entrySet()) {
                if (world.getValue() instanceof final UnmodifiableConfig section) {
                    final Map<String, Object> values = new LinkedHashMap<>();
                    collect(section, "", catalogued, values);
                    if (!values.isEmpty()) {
                        out.put(world.getKey(), values);
                    }
                }
            }
        }
        return out;
    }

    private static void collect(final UnmodifiableConfig section, final String prefix, final boolean catalogued,
                                final Map<String, Object> into) {
        for (final UnmodifiableConfig.Entry entry : section.entrySet()) {
            final String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (perWorldEntry(path) != null) {
                if (catalogued) {
                    into.put(path, SettingsCatalog.plain(entry.getValue()));
                }
            } else if (entry.getValue() instanceof final UnmodifiableConfig table) {
                collect(table, path, catalogued, into);
            } else if (!catalogued) {
                into.put(path, SettingsCatalog.plain(entry.getValue()));
            }
        }
    }

    /** The group a key belongs to in server.properties: its TOML file, or its world. */
    private static @Nullable String groupOf(final String key) {
        final Target target = target(key);
        if (target == null) {
            return null;
        }
        return target.world == null ? target.entry.tomlFile() : "world:" + target.world;
    }

    private static String groupLabel(final String group) {
        return group.startsWith("world:") ? "World " + group.substring("world:".length()) + " (worlds.toml)" : group;
    }
}
