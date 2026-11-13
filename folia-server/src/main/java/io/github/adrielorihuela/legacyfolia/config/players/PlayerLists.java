package io.github.adrielorihuela.legacyfolia.config.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.github.adrielorihuela.legacyfolia.config.ConfigFile;
import io.github.adrielorihuela.legacyfolia.config.ConfigHooks;
import io.github.adrielorihuela.legacyfolia.config.ServerConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The whitelist, the operators and the bans, kept in {@code config/players.toml} instead of four
 * JSON files.
 *
 * <p>Vanilla's {@code StoredUserList} still holds the lists in memory and still produces and reads
 * entries as {@code JsonObject}s, which is what its entry classes know how to do. A hook in its
 * {@code save} and {@code load} hands the array here instead of to a file, and this class turns it
 * into {@code [[whitelist]]}, {@code [[ops]]}, {@code [[banned-players]]} and
 * {@code [[banned-ips]]} tables and back ({@link PlayersFile}). Commands, the Bukkit API and
 * plugins use the lists exactly as before; only where they are kept has changed.</p>
 *
 * <p>The hook is active only on a running LegacyFolia server ({@code ConfigHooks.isStarted}). With
 * the server not started, as in tests that exercise vanilla's own classes, the lists stay JSON.</p>
 *
 * <p>The old files are imported once by {@link #importLegacy}: {@code whitelist.json},
 * {@code ops.json}, {@code banned-players.json} and {@code banned-ips.json}, from the server root
 * or from {@code config/}. Their entries are added to {@code players.toml} (an entry already there
 * wins) and the files are moved to {@code config/legacy-backup/<date>/} with the path they had.</p>
 */
public final class PlayerLists {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    /** The file, within {@code config/}. */
    public static final String FILE_NAME = "players.toml";

    private static final String JSON_SUFFIX = ".json";

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** One file serves four lists, saved from whichever region thread ran the command. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    private PlayerLists() {
    }

    /**
     * The file a list is known by. Nothing is ever created at this path; it names the list
     * ({@code whitelist.json} is the whitelist) for the code that was written against files, such
     * as vanilla's own messages.
     *
     * @param jsonName {@code whitelist.json}, {@code ops.json}, {@code banned-players.json} or
     *     {@code banned-ips.json}
     * @return the name's place under {@code config/}
     */
    public static File file(final String jsonName) {
        return ConfigFile.DIRECTORY.resolve(jsonName).toFile();
    }

    /**
     * Whether lists are kept in the TOML file, that is whether this is a running server.
     *
     * @return whether {@link #save} and {@link #load} handle the lists
     */
    public static boolean active() {
        return ConfigHooks.isStarted();
    }

    private static boolean handles(final File listFile) {
        final String name = listFile.getName();
        return active() && name.endsWith(JSON_SUFFIX)
            && PlayersFile.SECTIONS.contains(name.substring(0, name.length() - JSON_SUFFIX.length()));
    }

    /**
     * Saves a list, if the lists are kept in TOML.
     *
     * @param listFile the list's file, as {@link #file} named it
     * @param entries the whole list as JSON objects
     * @return whether it was handled; when false the caller writes its JSON file as vanilla does
     * @throws IOException if {@code players.toml} could not be read or written
     */
    public static boolean save(final File listFile, final JsonArray entries) throws IOException {
        if (!handles(listFile)) {
            return false;
        }
        final String section = section(listFile);
        final List<Map<String, Object>> maps = new ArrayList<>();
        for (final JsonElement element : entries) {
            maps.add(toMap(element.getAsJsonObject()));
        }
        LOCK.lock();
        try {
            new PlayersFile(path()).write(section, maps);
        } finally {
            LOCK.unlock();
        }
        return true;
    }

    /**
     * Reads a list, if the lists are kept in TOML.
     *
     * @param listFile the list's file, as {@link #file} named it
     * @return the entries as JSON objects, or {@code null} when the lists are not kept in TOML and
     *     the caller reads its JSON file
     * @throws IOException if {@code players.toml} is not valid TOML or could not be read
     */
    public static @Nullable JsonArray load(final File listFile) throws IOException {
        if (!handles(listFile)) {
            return null;
        }
        final String section = section(listFile);
        final List<Map<String, Object>> maps;
        LOCK.lock();
        try {
            maps = new PlayersFile(path()).read(section);
        } finally {
            LOCK.unlock();
        }
        final JsonArray array = new JsonArray();
        for (final Map<String, Object> map : maps) {
            array.add(toJson(map));
        }
        return array;
    }

    private static Path path() {
        return ServerConfig.directory().resolve(FILE_NAME);
    }

    private static String section(final File listFile) {
        final String name = listFile.getName();
        if (!name.endsWith(JSON_SUFFIX) || !PlayersFile.SECTIONS.contains(name.substring(0, name.length() - JSON_SUFFIX.length()))) {
            throw new IllegalArgumentException("not one of the player lists: " + listFile);
        }
        return name.substring(0, name.length() - JSON_SUFFIX.length());
    }

    // ------------------------------------------------------------------------------------------
    //  JSON <-> maps
    // ------------------------------------------------------------------------------------------

    private static Map<String, Object> toMap(final JsonObject object) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> field : object.entrySet()) {
            final JsonElement value = field.getValue();
            if (value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                final JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    map.put(field.getKey(), primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    map.put(field.getKey(), primitive.getAsLong());
                } else {
                    map.put(field.getKey(), primitive.getAsString());
                }
            } else {
                // Nothing in the four lists is nested; keep it as text rather than lose it.
                map.put(field.getKey(), value.toString());
            }
        }
        return map;
    }

    private static JsonObject toJson(final Map<String, Object> map) {
        final JsonObject object = new JsonObject();
        for (final Map.Entry<String, Object> field : map.entrySet()) {
            final Object value = field.getValue();
            if (value instanceof final Boolean bool) {
                object.addProperty(field.getKey(), bool);
            } else if (value instanceof final Number number) {
                object.addProperty(field.getKey(), number);
            } else {
                object.addProperty(field.getKey(), String.valueOf(value));
            }
        }
        return object;
    }

    // ------------------------------------------------------------------------------------------
    //  Importing the JSON files
    // ------------------------------------------------------------------------------------------

    /**
     * Imports the four JSON files of an older server into {@code config/players.toml}.
     *
     * <p>Each file is looked for in the server root and in {@code config/}; a server that ran an
     * earlier release of this fork has them in the second place, vanilla and Paper in the first.
     * Entries are matched by {@code uuid} (or {@code ip}), and one already in {@code players.toml}
     * is kept as it is. The TOML is written before any file is moved, so an interruption repeats
     * the import and finds nothing missing. A file that is not valid JSON is left where it is and
     * reported.</p>
     *
     * @param serverRoot the directory that holds {@code config/}
     * @return how many files were imported
     * @throws IOException if {@code players.toml} could not be written or a file could not be moved
     */
    public static int importLegacy(final Path serverRoot) throws IOException {
        return importLegacy(serverRoot, LocalDateTime.now(), null);
    }

    /**
     * Imports the four JSON files, moving the originals into a backup folder that is already there.
     *
     * <p>When the same start has just imported an older server's other files, the originals of
     * these go beside them, so that one folder holds everything that was replaced.</p>
     *
     * @param serverRoot the directory that holds {@code config/}
     * @param backup the folder the import of the other files made, or {@code null} to make one
     *     under {@code config/legacy-backup/}
     * @return how many files were imported
     * @throws IOException if {@code players.toml} could not be written or a file could not be moved
     */
    public static int importLegacy(final Path serverRoot, final @Nullable Path backup)
            throws IOException {
        return importLegacy(serverRoot, LocalDateTime.now(), backup);
    }

    static int importLegacy(final Path serverRoot, final LocalDateTime now) throws IOException {
        return importLegacy(serverRoot, now, null);
    }

    static int importLegacy(final Path serverRoot, final LocalDateTime now,
                            final @Nullable Path shared) throws IOException {
        final Path root = serverRoot.toAbsolutePath().normalize();
        final Path config = root.resolve("config");
        final Map<String, List<Path>> found = new LinkedHashMap<>();
        for (final String section : PlayersFile.SECTIONS) {
            final List<Path> paths = new ArrayList<>();
            for (final Path candidate : List.of(config.resolve(section + JSON_SUFFIX), root.resolve(section + JSON_SUFFIX))) {
                if (Files.isRegularFile(candidate)) {
                    paths.add(candidate);
                }
            }
            if (!paths.isEmpty()) {
                found.put(section, paths);
            }
        }
        if (found.isEmpty()) {
            return 0;
        }

        final PlayersFile target = new PlayersFile(config.resolve(FILE_NAME));
        final Map<String, List<Path>> done = new LinkedHashMap<>();
        LOCK.lock();
        try {
            for (final Map.Entry<String, List<Path>> list : found.entrySet()) {
                final String section = list.getKey();
                final List<Map<String, Object>> merged = new ArrayList<>(target.read(section));
                final List<Path> read = new ArrayList<>();
                int added = 0;
                for (final Path file : list.getValue()) {
                    final JsonArray entries;
                    try {
                        entries = readJson(file);
                    } catch (final IOException | JsonParseException | IllegalStateException e) {
                        LOGGER.warn("Could not read {} to import it into {}: {}. It is left where it is.",
                            root.relativize(file), FILE_NAME, e.getMessage());
                        continue;
                    }
                    read.add(file);
                    for (final JsonElement element : entries) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        final Map<String, Object> entry = toMap(element.getAsJsonObject());
                        if (merged.stream().noneMatch(existing -> sameSubject(existing, entry))) {
                            merged.add(entry);
                            added++;
                        }
                    }
                }
                if (read.isEmpty()) {
                    continue;
                }
                target.write(section, merged);
                done.put(section, read);
                LOGGER.info("Imported {} entr{} for [[{}]] from {} into config/{}.", added, added == 1 ? "y" : "ies",
                    section, read.stream().map(root::relativize).toList(), FILE_NAME);
            }

            if (done.isEmpty()) {
                return 0;
            }
            final Path backup = shared != null ? Files.createDirectories(shared.toAbsolutePath().normalize()) : backupFolder(config.resolve("legacy-backup"), now);
            for (final List<Path> files : done.values()) {
                for (final Path file : files) {
                    final Path destination = free(backup.resolve(root.relativize(file).toString()));
                    Files.createDirectories(destination.getParent());
                    move(file, destination);
                }
            }
            LOGGER.info("The originals are in {}.", root.relativize(backup));
        } finally {
            LOCK.unlock();
        }
        return done.size();
    }

    private static JsonArray readJson(final Path file) throws IOException {
        final String text = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            return new JsonArray();
        }
        final JsonElement parsed = com.google.gson.JsonParser.parseString(text);
        if (parsed.isJsonNull()) {
            return new JsonArray();
        }
        return parsed.getAsJsonArray();
    }

    /** The same person or address: by uuid when there is one, by address, otherwise by name. */
    static boolean sameSubject(final Map<String, Object> a, final Map<String, Object> b) {
        for (final String key : List.of("uuid", "ip", "name")) {
            if (a.containsKey(key) && b.containsKey(key)) {
                return String.valueOf(a.get(key)).equalsIgnoreCase(String.valueOf(b.get(key)));
            }
        }
        return a.equals(b);
    }

    private static Path backupFolder(final Path backups, final LocalDateTime now) throws IOException {
        final Path folder = backups.resolve(FOLDER.format(now));
        Files.createDirectories(folder);
        return folder;
    }

    /** A target that is not taken; an earlier file in the backup is never overwritten. */
    private static Path free(final Path target) {
        Path free = target;
        for (int n = 2; Files.exists(free); n++) {
            free = target.resolveSibling(target.getFileName() + "." + n);
        }
        return free;
    }

    private static void move(final Path from, final Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }
}
