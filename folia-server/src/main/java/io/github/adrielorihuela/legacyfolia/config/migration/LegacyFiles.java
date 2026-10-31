package io.github.adrielorihuela.legacyfolia.config.migration;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Finds the files an older server left behind.
 *
 * <p>Vanilla, Spigot, Paper and Folia between them keep their configuration in eight kinds of
 * file, and depending on the version and on this fork's own earlier releases each may be in the
 * server root or in {@code config/}: {@code ConfigPaths} has been moving {@code server.properties},
 * {@code bukkit.yml}, {@code spigot.yml}, {@code commands.yml} and {@code eula.txt} into
 * {@code config/} since v0.10, and Paper has kept its own two files there since 1.19. Both places
 * are looked at. When a file is in both, the one in {@code config/} is the one the server was
 * using, exactly as {@code ConfigPaths} decides; the other is a stray, backed up but not imported.</p>
 *
 * <p>The whitelist, the ban lists and the operator list are not looked for. They are JSON the
 * server keeps reading after the hook-up, and {@code ConfigPaths} already moves them from the root
 * into {@code config/}; moving them into the backup as well would empty them.</p>
 *
 * <h2>Which server.properties is legacy</h2>
 *
 * <p>With the server.properties mode on, this fork writes a {@code server.properties} of its own,
 * holding only the settings that differ from the default. That file is configuration, not
 * something to import and move away. A file is taken as this fork's own, and left alone, when its
 * leading comment block (the comments before the first key) mentions LegacyFolia, which is how the
 * mode's header starts. Any other file is legacy when one of these holds:</p>
 * <ol>
 *   <li>it sits beside a {@code bukkit.yml} or {@code spigot.yml}, which this fork never writes
 *       once the import has run;</li>
 *   <li>its leading comment is vanilla's {@code #Minecraft server properties};</li>
 *   <li>it has a key that is not a valid key of the mode's file: neither a catalogued setting's
 *       {@link SettingEntry#serverPropertiesKey() server.properties key} nor a
 *       {@code worlds.<world>.<path>} override of a per-world setting. Vanilla writes every key it
 *       knows, {@code rcon.password} among them, and that one is a secret and never a key of the
 *       mode's file, so a file vanilla wrote always has at least one.</li>
 * </ol>
 * <p>A file that satisfies none of them is indistinguishable from the mode's own with its header
 * deleted, and is left alone: importing it would do nothing the mode does not do anyway, and moving
 * it away would lose the operator's settings from the place they expect them.</p>
 */
final class LegacyFiles {

    /** Where every configuration file lives, relative to the server root. */
    static final String CONFIG = "config";

    /** The marker this fork's own server.properties header carries. */
    static final String OWN_HEADER_MARKER = "legacyfolia";

    private LegacyFiles() {
    }

    /**
     * What was found.
     *
     * @param toImport the files to read, at most one of each kind (one per world for
     *     {@code paper-world.yml})
     * @param strays files the server was not using because a copy in {@code config/} won; they are
     *     backed up but not read
     * @param notes things worth telling the operator about the search, such as a server.properties
     *     that was recognised as this fork's own
     */
    record Found(List<LegacyFile> toImport, List<LegacyFile> strays, List<String> notes) {

        boolean isEmpty() {
            return this.toImport.isEmpty() && this.strays.isEmpty();
        }

        List<LegacyFile> all() {
            final List<LegacyFile> all = new ArrayList<>(this.toImport);
            all.addAll(this.strays);
            return all;
        }
    }

    /**
     * Looks for every legacy file.
     *
     * @param root the server root
     * @param worldContainer the directory the worlds are in
     * @return what was found
     * @throws IOException if a directory could not be listed
     */
    static Found find(final Path root, final Path worldContainer) throws IOException {
        return find(root, worldContainer, false);
    }

    /**
     * Looks for the files an unfinished import has already moved into its backup folder. They keep
     * their relative paths there, so the same search applies, except that every
     * {@code server.properties} in the backup was already judged legacy before it was moved.
     *
     * @param backup the backup folder
     * @param worldContainer where the world container's files are inside it
     * @return what is in it
     * @throws IOException if a directory could not be listed
     */
    static Found findInBackup(final Path backup, final Path worldContainer) throws IOException {
        return find(backup, worldContainer, true);
    }

    private static Found find(final Path root, final Path worldContainer, final boolean inBackup) throws IOException {
        final List<LegacyFile> toImport = new ArrayList<>();
        final List<LegacyFile> strays = new ArrayList<>();
        final List<String> notes = new ArrayList<>();

        for (final LegacyFile.Kind kind : List.of(LegacyFile.Kind.BUKKIT, LegacyFile.Kind.SPIGOT,
            LegacyFile.Kind.COMMANDS, LegacyFile.Kind.EULA)) {
            rootOrConfig(root, kind, toImport, strays);
        }
        for (final LegacyFile.Kind kind : List.of(LegacyFile.Kind.PAPER_GLOBAL, LegacyFile.Kind.PAPER_WORLD_DEFAULTS)) {
            final Path inConfig = root.resolve(CONFIG).resolve(kind.fileName());
            if (Files.isRegularFile(inConfig)) {
                toImport.add(new LegacyFile(kind, inConfig, CONFIG + "/" + kind.fileName(), null));
            }
        }

        // server.properties last: whether it is legacy depends on what else is there.
        final Path propertiesInConfig = root.resolve(CONFIG).resolve(LegacyFile.Kind.SERVER_PROPERTIES.fileName());
        final Path propertiesInRoot = root.resolve(LegacyFile.Kind.SERVER_PROPERTIES.fileName());
        final List<LegacyFile> legacyProperties = new ArrayList<>(2);
        for (final Path candidate : List.of(propertiesInConfig, propertiesInRoot)) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            final boolean beside = Files.isRegularFile(candidate.resolveSibling("bukkit.yml"))
                || Files.isRegularFile(candidate.resolveSibling("spigot.yml"));
            final String relative = candidate.equals(propertiesInConfig) ? CONFIG + "/server.properties" : "server.properties";
            if (inBackup || isLegacyServerProperties(candidate, beside)) {
                legacyProperties.add(new LegacyFile(LegacyFile.Kind.SERVER_PROPERTIES, candidate, relative, null));
            } else {
                notes.add(relative + " is LegacyFolia's own (server.properties mode); left where it is");
            }
        }
        if (!legacyProperties.isEmpty()) {
            // The one in config/ wins, as it does in ConfigPaths.
            toImport.add(legacyProperties.get(0));
            strays.addAll(legacyProperties.subList(1, legacyProperties.size()));
        }

        toImport.addAll(paperWorldFiles(root, worldContainer));
        return new Found(List.copyOf(toImport), List.copyOf(strays), List.copyOf(notes));
    }

    private static void rootOrConfig(final Path root, final LegacyFile.Kind kind,
                                     final List<LegacyFile> toImport, final List<LegacyFile> strays) {
        final Path inConfig = root.resolve(CONFIG).resolve(kind.fileName());
        final Path inRoot = root.resolve(kind.fileName());
        final boolean config = Files.isRegularFile(inConfig);
        final boolean rootFile = Files.isRegularFile(inRoot);
        if (config) {
            toImport.add(new LegacyFile(kind, inConfig, CONFIG + "/" + kind.fileName(), null));
            if (rootFile) {
                strays.add(new LegacyFile(kind, inRoot, kind.fileName(), null));
            }
        } else if (rootFile) {
            toImport.add(new LegacyFile(kind, inRoot, kind.fileName(), null));
        }
    }

    /**
     * Finds every world's {@code paper-world.yml} in the world container.
     *
     * <p>Two layouts exist. Up to 1.21 CraftBukkit gave every world a folder of its own in the
     * container, {@code world_nether/paper-world.yml}, and the world was named after it. Paper 26
     * keeps one level folder and puts the other dimensions under
     * {@code <level>/dimensions/<namespace>/<path>/}, naming them as {@code PaperWorldLoader} does:
     * {@code <level>_nether}, {@code <level>_the_end}, and {@code <level>_<namespace>_<path>} for
     * any other dimension. Both are searched, so a server is imported whether or not Paper has
     * already migrated its folders.</p>
     */
    static List<LegacyFile> paperWorldFiles(final Path root, final Path container) throws IOException {
        final List<LegacyFile> found = new ArrayList<>();
        if (!Files.isDirectory(container)) {
            return found;
        }
        final String fileName = LegacyFile.Kind.PAPER_WORLD.fileName();
        for (final Path level : sortedDirectories(container)) {
            final String levelName = level.getFileName().toString();
            if (CONFIG.equals(levelName) && level.normalize().equals(root.resolve(CONFIG).normalize())) {
                continue;
            }
            final Path direct = level.resolve(fileName);
            if (Files.isRegularFile(direct)) {
                found.add(new LegacyFile(LegacyFile.Kind.PAPER_WORLD, direct, relative(root, container, direct), levelName));
            }
            final Path dimensions = level.resolve("dimensions");
            if (!Files.isDirectory(dimensions)) {
                continue;
            }
            for (final Path namespace : sortedDirectories(dimensions)) {
                for (final Path dimension : sortedDirectories(namespace)) {
                    final Path file = dimension.resolve(fileName);
                    if (Files.isRegularFile(file)) {
                        final String world = worldName(levelName, namespace.getFileName().toString(),
                            dimension.getFileName().toString());
                        found.add(new LegacyFile(LegacyFile.Kind.PAPER_WORLD, file, relative(root, container, file), world));
                    }
                }
            }
        }
        return found;
    }

    /** {@code PaperWorldLoader.defaultWorldName}, from the folder a dimension is stored in. */
    static String worldName(final String level, final String namespace, final String path) {
        if ("minecraft".equals(namespace)) {
            switch (path) {
                case "overworld" -> {
                    return level;
                }
                case "the_nether" -> {
                    return level + "_nether";
                }
                case "the_end" -> {
                    return level + "_the_end";
                }
                default -> {
                }
            }
        }
        return level + "_" + namespace + "_" + path;
    }

    private static List<Path> sortedDirectories(final Path directory) throws IOException {
        final List<Path> directories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
            stream.forEach(directories::add);
        }
        directories.sort(null);
        return directories;
    }

    /**
     * The path a file keeps in the backup: relative to the server root when the world container is
     * inside it, and under {@code world-container/} when it is somewhere else.
     */
    static String relative(final Path root, final Path container, final Path file) {
        final Path normalRoot = root.toAbsolutePath().normalize();
        final Path normalFile = file.toAbsolutePath().normalize();
        if (normalFile.startsWith(normalRoot)) {
            return slashes(normalRoot.relativize(normalFile));
        }
        return "world-container/" + slashes(container.toAbsolutePath().normalize().relativize(normalFile));
    }

    private static String slashes(final Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * Whether a server.properties is an older server's rather than the server.properties mode's.
     * The criterion is described on the class.
     *
     * @param file the file
     * @param besideBukkitOrSpigot whether a {@code bukkit.yml} or {@code spigot.yml} is in the same
     *     directory
     * @return whether to import it and move it into the backup
     * @throws IOException if it could not be read
     */
    static boolean isLegacyServerProperties(final Path file, final boolean besideBukkitOrSpigot) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        final StringBuilder leading = new StringBuilder();
        for (final String line : lines) {
            final String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                leading.append(trimmed).append('\n');
                continue;
            }
            break;
        }
        final String header = leading.toString().toLowerCase(Locale.ROOT);
        if (header.contains(OWN_HEADER_MARKER)) {
            return false;
        }
        if (besideBukkitOrSpigot || header.contains("minecraft server properties")) {
            return true;
        }
        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            properties.load(reader);
        }
        for (final String key : properties.stringPropertyNames()) {
            if (!isModeKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a key could appear in the server.properties mode's own file. */
    static boolean isModeKey(final String key) {
        final SettingsCatalog catalog = SettingsCatalog.get();
        if (catalog.byServerPropertiesKey(key).isPresent()) {
            return true;
        }
        if (!key.startsWith("worlds.")) {
            return false;
        }
        // worlds.<world>.<path>: the world's name may itself contain dots, so every split is tried.
        final String rest = key.substring("worlds.".length());
        int dot = rest.indexOf('.');
        while (dot > 0) {
            final @Nullable SettingEntry entry = catalog.byPath(rest.substring(dot + 1)).orElse(null);
            if (entry != null && entry.perWorld()) {
                return true;
            }
            dot = rest.indexOf('.', dot + 1);
        }
        return false;
    }

    /** Every file under a directory, for tests and for the report. */
    static List<Path> walk(final Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
