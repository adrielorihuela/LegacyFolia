package io.github.adrielorihuela.legacyfolia.config.migration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Server folders for the importer's tests, copied from {@code src/test/resources/legacyfolia-import}. */
final class ImportFixtures {

    static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 30, 12, 0, 0);

    static final String BACKUP = "config/legacy-backup/2026-10-30_12-00-00";

    private ImportFixtures() {
    }

    /** Copies one fixture server into a directory. */
    static Path copy(final String fixture, final Path into) throws IOException {
        final URL url = ImportFixtures.class.getClassLoader().getResource("legacyfolia-import/" + fixture);
        if (url == null) {
            throw new IllegalStateException("no fixture " + fixture);
        }
        final Path source;
        try {
            source = Path.of(url.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (Stream<Path> files = Files.walk(source)) {
            for (final Path file : files.filter(Files::isRegularFile).toList()) {
                final Path target = into.resolve(source.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
        return into;
    }

    /** Writes a file, creating its folders. */
    static void write(final Path root, final String relative, final String content) throws IOException {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    static Optional<LegacyImport.Result> run(final Path root) throws IOException {
        return LegacyImport.run(root, null, WHEN, LegacyImport.Checkpoint.NONE);
    }

    /** One value from a TOML file under {@code config/}, as plain Java, or {@code null}. */
    static Object toml(final Path root, final String file, final String... path) {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        try (Reader reader = Files.newBufferedReader(root.resolve("config").resolve(file), StandardCharsets.UTF_8)) {
            new TomlParser().parse(reader, parsed, ParsingMode.REPLACE);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final List<String> keys = new ArrayList<>();
        for (final String part : path) {
            keys.addAll(Arrays.asList(part.split("\\.")));
        }
        final Object value = parsed.get(keys);
        return value == null ? null : SettingsCatalog.plain(value);
    }

    /** An override in {@code worlds.toml}: {@code [worlds.<world>] "<path>" = ...}. */
    static Object override(final Path root, final String world, final String tomlPath) {
        final CommentedConfig parsed = CommentedConfig.inMemory();
        try (Reader reader = Files.newBufferedReader(root.resolve("config/worlds.toml"), StandardCharsets.UTF_8)) {
            new TomlParser().parse(reader, parsed, ParsingMode.REPLACE);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final Object value = parsed.get(List.of("worlds", world, tomlPath));
        return value == null ? null : SettingsCatalog.plain(value);
    }

    static String report(final Path root) throws IOException {
        return Files.readString(root.resolve(BACKUP).resolve(ImportReport.FILE_NAME), StandardCharsets.US_ASCII);
    }

    /** Every file under a folder, relative and with forward slashes, sorted. */
    static List<String> tree(final Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                .map(file -> folder.relativize(file).toString().replace('\\', '/'))
                .sorted().toList();
        }
    }
}
