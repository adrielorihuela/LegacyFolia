package io.github.adrielorihuela.legacyfolia.config.migration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.ConfigFile;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Imports the configuration of the server this one replaces: vanilla, Spigot, Paper or Folia.
 *
 * <p>Decision 7 of the hook-up: when the jar starts where another server used to run, every file
 * that server kept its settings in is read, each value goes to the TOML key the settings catalog
 * gives it, collisions between files are resolved the way the original server resolved them, and
 * the originals are moved into {@code config/legacy-backup/<yyyy-MM-dd_HH-mm-ss>/} beside an
 * {@code import-report.txt}. The importer only ever writes the TOML files. It never writes
 * {@code server.properties}: with the server.properties mode on, the mode's own reconcile moves the
 * values that differ from the default there when it runs.</p>
 *
 * <h2>When to call it</h2>
 *
 * <p>{@link #runIfNeeded(Path, Path)} must run <b>before</b> {@code ServerConfig.load()}, that is
 * in {@code Main.main} just ahead of the {@code ServerConfig.load()} call the "unified
 * configuration" block of patch 0030 placed before {@code new DedicatedServerSettings}, and after
 * the command line has been parsed so that {@code --universe} can be passed as the world container.
 * Three reasons. The importer writes the TOML files, and {@code ServerConfig} keeps them in memory
 * and writes its own copy back on {@code set}, which would undo the import if it had loaded first.
 * The server.properties mode, which runs inside or right after {@code load()}, has to see the
 * imported values to move them into server.properties. And everything after that line, from
 * {@code ConfigPaths.resolve("server.properties")} to Paper's configuration setup, would otherwise
 * read or recreate the legacy files the import is about to move.</p>
 *
 * <h2>Order, and what an interruption leaves behind</h2>
 *
 * <ol>
 *   <li>Every legacy file still in place is parsed. A file that cannot be parsed stops the import
 *       before anything has moved.</li>
 *   <li>A backup folder is created and a marker, {@value #MARKER}, is written into it first.</li>
 *   <li>Every legacy file is moved into it. Moves never copy-then-delete, so a file is in exactly
 *       one of the two places at any moment.</li>
 *   <li>The files in the backup are read and planned from, the TOML files written and saved, and
 *       read back to verify that each value landed.</li>
 *   <li>{@value ImportReport#FILE_NAME} is written and the marker deleted.</li>
 * </ol>
 *
 * <p>A run that finds a backup folder with the marker still in it resumes that folder instead of
 * starting another: it moves in whatever the interrupted run had not moved yet and imports the
 * whole set again. Nothing can be lost, because nothing is deleted, and nothing can be doubled,
 * because a TOML key is set rather than appended and every write is skipped when the file already
 * holds the value. Running it on a server with no legacy files and no unfinished backup does
 * nothing at all.</p>
 */
public final class LegacyImport {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** The folder under {@code config/} that holds every import's backup. */
    public static final String BACKUP_DIRECTORY = "legacy-backup";

    /** Present in a backup folder while its import has not finished. */
    static final String MARKER = ".import-in-progress";

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** The settings {@code Dimensions} has been reading from the TOML since v0.3. */
    private static final Set<String> TOML_OWNED = Set.of("dimensions.nether", "dimensions.end");

    private LegacyImport() {
    }

    /**
     * What an import did.
     *
     * @param backupDirectory where the originals and the report are
     * @param report the report
     * @param written how many values were written to the TOML files
     * @param notImported how many legacy keys had no place, or a value the setting cannot take
     */
    public record Result(Path backupDirectory, Path report, int written, int notImported) {
    }

    /** A point in the import, for tests that interrupt it there. */
    @FunctionalInterface
    interface Checkpoint {

        Checkpoint NONE = step -> { };

        void reached(String step) throws IOException;
    }

    /**
     * Imports an older server's configuration if there is one.
     *
     * @param serverRoot the directory the server runs in, the one that holds {@code config/}
     * @param worldContainer where the worlds are, as {@code --universe} gives it; {@code null} to
     *     use {@code settings.world-container} from the legacy {@code bukkit.yml}, then
     *     {@code world.storage.world-container} from {@code world.toml}, then the server root
     * @return what was done, or empty when there was nothing to import
     * @throws IOException if a legacy file could not be parsed, a file could not be moved, or the
     *     TOML files could not be written; the server should not start on a half-imported setup, and
     *     the next start resumes where this one stopped
     */
    public static Optional<Result> runIfNeeded(final Path serverRoot, final @Nullable Path worldContainer) throws IOException {
        return run(serverRoot, worldContainer, LocalDateTime.now(), Checkpoint.NONE);
    }

    static Optional<Result> run(final Path serverRoot, final @Nullable Path worldContainer, final LocalDateTime now,
                                final Checkpoint checkpoint) throws IOException {
        final Path root = serverRoot.toAbsolutePath().normalize();
        final Path configDirectory = root.resolve(LegacyFiles.CONFIG);
        final Path backups = configDirectory.resolve(BACKUP_DIRECTORY);

        final @Nullable Path pending = pendingBackup(backups);
        final Marker marker;
        final Path container;
        if (pending != null) {
            // The interrupted run may already have moved bukkit.yml, so the container it worked out
            // is taken from the marker rather than worked out again.
            marker = Marker.read(pending.resolve(MARKER));
            container = worldContainer != null ? root.resolve(worldContainer).normalize() : Path.of(marker.containerPath());
        } else {
            container = worldContainer(root, worldContainer);
            marker = new Marker(container.toString(), containerInBackup(root, container), preexistingTomlFiles(configDirectory));
        }
        final LegacyFiles.Found inPlace = LegacyFiles.find(root, container);
        if (pending == null && inPlace.isEmpty()) {
            return Optional.empty();
        }

        // Parse first: a file nobody can read stops the import before anything moves.
        for (final LegacyFile file : inPlace.toImport()) {
            LegacyReader.read(file);
        }

        final Path backup = pending != null ? pending : newBackup(backups, now);
        if (pending == null) {
            marker.write(backup.resolve(MARKER));
            LOGGER.info("Found the configuration of an older server; importing it into config/ and moving "
                + "the originals to {}.", root.relativize(backup));
        } else {
            LOGGER.info("Resuming the unfinished import in {}.", root.relativize(backup));
        }
        checkpoint.reached("started");

        final List<String> notes = new ArrayList<>(inPlace.notes());
        for (final LegacyFile file : inPlace.all()) {
            final Path target = freeTarget(backup.resolve(file.relative()), notes);
            Files.createDirectories(target.getParent());
            move(file.path(), target);
            checkpoint.reached("moved:" + file.relative());
        }

        final LegacyFiles.Found backedUp = LegacyFiles.findInBackup(backup, backup.resolve(marker.containerInBackup()));
        final Map<LegacyFile, Map<String, Object>> contents = new LinkedHashMap<>();
        for (final LegacyFile file : backedUp.toImport()) {
            contents.put(file, LegacyReader.read(file));
        }
        final ImportPlan plan = ImportPlanner.plan(contents);

        final ImportReport report = new ImportReport();
        for (final LegacyFile file : backedUp.toImport()) {
            report.files.add(file.relative() + (file.world() != null ? "  (world " + file.world() + ")" : ""));
        }
        for (final LegacyFile file : backedUp.strays()) {
            report.strays.add(file.relative());
        }

        apply(configDirectory, plan, marker, report, checkpoint);

        final Path reportFile = backup.resolve(ImportReport.FILE_NAME);
        Files.writeString(reportFile, report.render(now, root.toString(), plan, notes), StandardCharsets.US_ASCII);
        Files.delete(backup.resolve(MARKER));

        final int notImported = plan.unplaced.size() + plan.invalid.size();
        LOGGER.info("Imported {} setting(s) from {} file(s) into config/. The originals and {} are in {}.",
            report.imported.size(), backedUp.toImport().size(), ImportReport.FILE_NAME, root.relativize(backup));
        if (notImported > 0) {
            LOGGER.warn("{} legacy setting(s) could not be imported; {} lists them.", notImported,
                root.relativize(reportFile));
        }
        return Optional.of(new Result(backup, reportFile, report.imported.size(), notImported));
    }

    // ------------------------------------------------------------------------------------------
    //  Writing the TOML files
    // ------------------------------------------------------------------------------------------

    private static void apply(final Path configDirectory, final ImportPlan plan, final Marker marker,
                              final ImportReport report, final Checkpoint checkpoint) throws IOException {
        final Map<String, ConfigFile> files = new LinkedHashMap<>();
        final Map<String, Map<List<String>, Written>> written = new LinkedHashMap<>();

        for (final ImportPlan.Planned planned : plan.global.values()) {
            final SettingEntry entry = planned.entry();
            final ConfigFile file = open(files, configDirectory, entry.tomlFile());
            final List<String> path = split(entry.tomlPath());
            final Object current = file.raw().get(path);
            if (TOML_OWNED.contains(entry.tomlPath()) && marker.preexisting().contains(entry.tomlFile())) {
                if (!LegacyValues.same(entry, current, planned.value())) {
                    report.kept.add(entry.tomlPath() + " = " + LegacyValues.describe(current) + " kept over "
                        + planned.from() + " = " + LegacyValues.describe(planned.value())
                        + ": this server already read it from " + entry.tomlFile());
                }
                continue;
            }
            write(file, path, entry, current, planned, entry.tomlFile() + "  " + entry.tomlPath(), report, written);
        }

        for (final Map.Entry<String, Map<String, ImportPlan.Planned>> world : plan.worlds.entrySet()) {
            final ConfigFile file = open(files, configDirectory, ServerConfig.WORLDS);
            for (final ImportPlan.Planned planned : world.getValue().values()) {
                final SettingEntry entry = planned.entry();
                final List<String> quoted = List.of("worlds", world.getKey(), entry.tomlPath());
                final List<String> nested = new ArrayList<>(List.of("worlds", world.getKey()));
                nested.addAll(split(entry.tomlPath()));
                // An operator may already have written this override as nested tables; that one is
                // updated rather than shadowed by a quoted key beside it.
                final boolean useNested = file.raw().get(quoted) == null && file.raw().get(nested) != null;
                final List<String> path = useNested ? nested : quoted;
                final Object current = file.raw().get(path);
                write(file, path, entry, current, planned, ServerConfig.WORLDS + "  [worlds." + world.getKey() + "] \""
                    + entry.tomlPath() + "\"", report, written);
            }
        }

        for (final ImportPlan.Planned planned : plan.secrets.values()) {
            secret(configDirectory, files, planned, report);
        }

        for (final String name : written.keySet()) {
            files.get(name).save();
            checkpoint.reached("saved:" + name);
        }

        // Read every written file back from disk, as the server will, and check each value landed.
        for (final Map.Entry<String, Map<List<String>, Written>> file : written.entrySet()) {
            final ConfigFile reread = ConfigFile.open(configDirectory, file.getKey());
            for (final Map.Entry<List<String>, Written> value : file.getValue().entrySet()) {
                final Object onDisk = reread.raw().get(value.getKey());
                if (!LegacyValues.same(value.getValue().entry(), onDisk, value.getValue().value())) {
                    throw new IOException("config/" + file.getKey() + ": " + String.join(".", value.getKey())
                        + " reads back as " + LegacyValues.describe(onDisk) + " instead of "
                        + LegacyValues.describe(value.getValue().value()) + "; the import will be retried on the next start");
                }
            }
        }
    }

    private record Written(SettingEntry entry, Object value) {
    }

    private static void write(final ConfigFile file, final List<String> path, final SettingEntry entry,
                              final @Nullable Object current, final ImportPlan.Planned planned, final String where,
                              final ImportReport report, final Map<String, Map<List<String>, Written>> written) {
        if (current != null && LegacyValues.same(entry, current, planned.value())
            || current == null && LegacyValues.same(entry, entry.defaultValue(), planned.value()) && !path.get(0).equals("worlds")) {
            report.unchanged++;
            return;
        }
        file.raw().set(path, tomlValue(file.raw(), planned.value()));
        written.computeIfAbsent(file.fileName(), ignored -> new LinkedHashMap<>()).put(path, new Written(entry, planned.value()));
        report.imported.add(where + " = " + LegacyValues.describe(planned.value()) + "   <- " + planned.from());
        if (current != null && !LegacyValues.same(entry, current, entry.defaultValue())) {
            report.replaced.add(where + ": was " + LegacyValues.describe(current) + ", now "
                + LegacyValues.describe(planned.value()) + " (the older server ran with the imported value)");
        }
    }

    private static Object tomlValue(final Config parent, final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Config table = parent.createSubConfig();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                table.set(List.of(String.valueOf(entry.getKey())), tomlValue(table, entry.getValue()));
            }
            return table;
        }
        if (value instanceof final List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    /**
     * Moves a secret into the file its setting names, never into a TOML file.
     *
     * <p>A secret file that already holds a different value is left alone: it is the one the
     * server has been using for that purpose, and the legacy value is still in the backup.</p>
     */
    private static void secret(final Path configDirectory, final Map<String, ConfigFile> files,
                               final ImportPlan.Planned planned, final ImportReport report) throws IOException {
        final SettingEntry entry = planned.entry();
        final ConfigFile file = open(files, configDirectory, entry.tomlFile());
        final Object named = file.raw().get(split(entry.tomlPath()));
        final String fileName = named instanceof final String text && !text.isBlank() ? text.trim() : String.valueOf(entry.defaultValue());
        final Path target = Path.of(fileName).isAbsolute() ? Path.of(fileName) : configDirectory.resolve(fileName);
        final String value = String.valueOf(planned.value());
        final String existing = Files.isRegularFile(target) ? Files.readString(target, StandardCharsets.UTF_8).trim() : "";
        if (existing.equals(value)) {
            report.secrets.add(planned.from() + ": already in " + fileName);
            return;
        }
        if (!existing.isEmpty()) {
            report.kept.add(fileName + " kept: it already holds a different secret; the one from " + planned.from()
                + " is only in the backup");
            return;
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, value + "\n", StandardCharsets.UTF_8);
        report.secrets.add(planned.from() + " -> config/" + fileName);
    }

    private static ConfigFile open(final Map<String, ConfigFile> files, final Path configDirectory, final String name) throws IOException {
        ConfigFile file = files.get(name);
        if (file == null) {
            file = ConfigFile.open(configDirectory, name);
            files.put(name, file);
        }
        return file;
    }

    private static List<String> split(final String tomlPath) {
        return Arrays.asList(tomlPath.split("\\."));
    }

    // ------------------------------------------------------------------------------------------
    //  Files and folders
    // ------------------------------------------------------------------------------------------

    private static @Nullable Path pendingBackup(final Path backups) throws IOException {
        if (!Files.isDirectory(backups)) {
            return null;
        }
        final List<Path> pending = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backups, Files::isDirectory)) {
            for (final Path folder : stream) {
                if (Files.isRegularFile(folder.resolve(MARKER))) {
                    pending.add(folder);
                }
            }
        }
        pending.sort(null);
        return pending.isEmpty() ? null : pending.get(0);
    }

    private static Path newBackup(final Path backups, final LocalDateTime now) throws IOException {
        final String name = FOLDER.format(now);
        Path folder = backups.resolve(name);
        for (int n = 2; Files.exists(folder); n++) {
            folder = backups.resolve(name + "_" + n);
        }
        Files.createDirectories(folder);
        return folder;
    }

    /** A target in the backup that is not taken; an earlier file of the same path is never overwritten. */
    private static Path freeTarget(final Path target, final List<String> notes) {
        if (!Files.exists(target)) {
            return target;
        }
        Path free = target;
        for (int n = 2; Files.exists(free); n++) {
            free = target.resolveSibling(target.getFileName() + "." + n);
        }
        notes.add(target.getFileName() + " was already in the backup; the newer copy was saved beside it as "
            + free.getFileName() + " and not imported");
        return free;
    }

    private static void move(final Path from, final Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    private static Path worldContainer(final Path root, final @Nullable Path given) throws IOException {
        if (given != null) {
            return root.resolve(given).normalize();
        }
        for (final Path bukkit : List.of(root.resolve(LegacyFiles.CONFIG).resolve("bukkit.yml"), root.resolve("bukkit.yml"))) {
            if (Files.isRegularFile(bukkit)) {
                final Map<String, Object> tree = LegacyReader.read(new LegacyFile(LegacyFile.Kind.BUKKIT, bukkit, "bukkit.yml", null));
                if (tree.get("settings") instanceof final Map<?, ?> settings && settings.get("world-container") != null) {
                    return root.resolve(String.valueOf(settings.get("world-container"))).normalize();
                }
                break;
            }
        }
        final Path worldToml = root.resolve(LegacyFiles.CONFIG).resolve(ServerConfig.WORLD);
        if (Files.isRegularFile(worldToml)) {
            final CommentedConfig parsed = CommentedConfig.inMemory();
            try (Reader reader = Files.newBufferedReader(worldToml, StandardCharsets.UTF_8)) {
                new TomlParser().parse(reader, parsed, ParsingMode.REPLACE);
            } catch (final RuntimeException e) {
                return root;
            }
            if (parsed.get(List.of("world", "storage", "world-container")) instanceof final String container && !container.isBlank()) {
                return root.resolve(container.trim()).normalize();
            }
        }
        return root;
    }

    /** Where the world container's files go inside a backup folder. */
    private static String containerInBackup(final Path root, final Path container) {
        final Path normal = container.toAbsolutePath().normalize();
        if (normal.startsWith(root)) {
            return root.relativize(normal).toString().replace('\\', '/');
        }
        return "world-container";
    }

    private static Set<String> preexistingTomlFiles(final Path configDirectory) {
        final Set<String> existing = new LinkedHashSet<>();
        for (final String name : ServerConfig.fileNames()) {
            if (Files.isRegularFile(configDirectory.resolve(name))) {
                existing.add(name);
            }
        }
        return existing;
    }

    /**
     * What an unfinished import has to remember: where the world container is, where its files are
     * inside the backup, and which TOML files existed before the import began (a resumed run finds
     * them all existing).
     */
    record Marker(String containerPath, String containerInBackup, Set<String> preexisting) {

        void write(final Path file) throws IOException {
            final Properties properties = new Properties();
            properties.setProperty("container", this.containerPath);
            properties.setProperty("container-in-backup", this.containerInBackup);
            properties.setProperty("preexisting-toml", String.join(",", this.preexisting));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1)) {
                properties.store(writer, "LegacyFolia: an import of an older server's configuration is in progress."
                    + " Do not delete; the next start finishes it.");
            }
        }

        static Marker read(final Path file) throws IOException {
            final Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                properties.load(reader);
            }
            final Set<String> preexisting = new LinkedHashSet<>();
            for (final String name : properties.getProperty("preexisting-toml", "").split(",")) {
                if (!name.isBlank()) {
                    preexisting.add(name.trim());
                }
            }
            return new Marker(properties.getProperty("container", "."), properties.getProperty("container-in-backup", ""),
                preexisting);
        }
    }
}
