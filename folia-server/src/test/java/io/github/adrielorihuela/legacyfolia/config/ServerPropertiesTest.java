package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.adrielorihuela.legacyfolia.config.properties.PropertiesDocument;
import io.github.adrielorihuela.legacyfolia.config.properties.TomlText;
import io.github.adrielorihuela.legacyfolia.config.types.InvalidSettingException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The server.properties mode, end to end, in a temporary configuration directory.
 *
 * <p>Each test starts from a server that has just been created: the eight TOML files from their
 * templates and a server.properties holding only its header. That is the state every real server
 * passes through on its first start.</p>
 */
class ServerPropertiesTest {

    private static final String MOTD = "server.motd";
    private static final String MAX_PLAYERS = "server.max-players";
    private static final String VIEW = "chunks.view-distance";
    private static final String SIMULATION = "chunks.simulation-distance";
    private static final String JUMP = "gameplay.hunger.jump-walk-exhaustion";
    private static final String SECRET = "network.velocity.secret-file";

    @TempDir
    Path dir;

    @BeforeEach
    void freshServer() throws Exception {
        ServerConfig.load(this.dir);
        ServerProperties.reconcile();
    }

    @AfterEach
    void forget() {
        ServerProperties.forget();
        ServerConfig.unload();
    }

    // ---- helpers --------------------------------------------------------------------------

    private ServerProperties.Report restart() throws Exception {
        ServerProperties.flush();
        ServerConfig.load(this.dir);
        return ServerProperties.reconcile();
    }

    private Path properties() {
        return this.dir.resolve(ServerProperties.FILE_NAME);
    }

    private String read(final String name) throws Exception {
        return Files.readString(this.dir.resolve(name), StandardCharsets.UTF_8);
    }

    private void write(final String name, final String text) throws Exception {
        Files.writeString(this.dir.resolve(name), text, StandardCharsets.UTF_8);
    }

    private void appendProperties(final String lines) throws Exception {
        write(ServerProperties.FILE_NAME, read(ServerProperties.FILE_NAME) + lines);
    }

    private void editToml(final String file, final String path, final String literal) throws Exception {
        write(file, TomlText.parse(read(file)).set(List.of(), path, literal, false).text());
    }

    private Properties loadProperties() throws Exception {
        final Properties loaded = new Properties();
        try (InputStream in = Files.newInputStream(properties())) {
            loaded.load(in);
        }
        return loaded;
    }

    private CommentedConfig toml(final String name) throws Exception {
        final CommentedConfig config = CommentedConfig.inMemory();
        new TomlParser().parse(new StringReader(read(name)), config, ParsingMode.REPLACE);
        return config;
    }

    private static String template(final String name) throws Exception {
        try (InputStream in = ServerPropertiesTest.class.getClassLoader().getResourceAsStream("legacyfolia/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> keysInFile() throws Exception {
        final List<String> keys = new ArrayList<>();
        for (final PropertiesDocument.Entry entry : PropertiesDocument.parse(read(ServerProperties.FILE_NAME)).entries()) {
            keys.add(entry.key());
        }
        return keys;
    }

    // ---- the tests ------------------------------------------------------------------------

    @Test
    @DisplayName("a new server gets a server.properties with only the header, and the defaults apply")
    void newServer() throws Exception {
        assertTrue(ServerProperties.isEnabled());
        assertTrue(Files.exists(properties()));
        assertTrue(keysInFile().isEmpty());
        final String text = read(ServerProperties.FILE_NAME);
        assertTrue(text.startsWith(ServerProperties.HEADER.getFirst()));
        for (final char c : text.toCharArray()) {
            assertTrue(c < 0x80, "the header must be ASCII");
        }
        assertEquals("A Minecraft Server", ServerConfig.getString(MOTD, "?"));
        assertEquals(0, ServerProperties.status().overrides());

        final ServerProperties.Report again = restart();
        assertFalse(again.created());
        assertEquals(text, read(ServerProperties.FILE_NAME), "a second start changes nothing");
        for (final String file : ServerConfig.fileNames()) {
            assertEquals(template(file), read(file), file + " must be untouched");
        }
    }

    @Test
    @DisplayName("a value edited into a TOML file moves to server.properties and the TOML goes back to its default")
    void editedTomlMoves() throws Exception {
        editToml(ServerConfig.SERVER, MOTD, "\"Hello\"");
        editToml(ServerConfig.GAMEPLAY, JUMP, "0.5");

        final ServerProperties.Report report = restart();

        assertEquals(List.of("motd", JUMP), report.moved());
        final Properties file = loadProperties();
        assertEquals("Hello", file.getProperty("motd"), "a vanilla setting keeps its vanilla name");
        assertEquals("0.5", file.getProperty(JUMP), "the rest use their TOML path");
        assertEquals(template(ServerConfig.SERVER), read(ServerConfig.SERVER), "back to the template, byte for byte");
        assertEquals(template(ServerConfig.GAMEPLAY), read(ServerConfig.GAMEPLAY));
        assertEquals("Hello", ServerConfig.getString(MOTD, "?"));
        assertEquals(0.5, ServerConfig.getDouble(JUMP, 0));
        assertEquals(0.5, ServerConfig.forWorld("world").getDouble(JUMP, 0), "world views see it too");

        final ServerProperties.Status status = ServerProperties.status();
        assertEquals(2, status.overrides());
        assertEquals(Map.of(ServerConfig.SERVER, 1, ServerConfig.GAMEPLAY, 1), status.byFile());
    }

    @Test
    @DisplayName("when both say something different, server.properties wins and the TOML is reset")
    void conflictServerPropertiesWins() throws Exception {
        appendProperties("motd=From properties\n");
        editToml(ServerConfig.SERVER, MOTD, "\"From TOML\"");

        final ServerProperties.Report report = restart();

        assertEquals(List.of("motd"), report.conflicts());
        assertEquals("From properties", loadProperties().getProperty("motd"));
        assertEquals("A Minecraft Server", toml(ServerConfig.SERVER).get(MOTD));
        assertEquals("From properties", ServerConfig.getString(MOTD, "?"));
        assertEquals(List.of("motd"), keysInFile(), "no second copy of the key");
    }

    @Test
    @DisplayName("deleting a line goes back to the default; adding one applies it")
    void deleteAndAddLines() throws Exception {
        appendProperties("max-players=50\nview-distance=6\n");
        restart();
        assertEquals(50, ServerConfig.getInt(MAX_PLAYERS, 0));
        assertEquals(6, ServerConfig.getInt(VIEW, 0));
        assertEquals(6, ServerConfig.forWorld("world").getInt(VIEW, 0));

        write(ServerProperties.FILE_NAME, read(ServerProperties.FILE_NAME).replaceAll("max-players=50\\R", ""));
        restart();
        assertEquals(20, ServerConfig.getInt(MAX_PLAYERS, 0));
        assertEquals(6, ServerConfig.getInt(VIEW, 0));
        assertEquals("20", toml(ServerConfig.SERVER).get(MAX_PLAYERS).toString(), "the TOML was never touched");
    }

    @Test
    @DisplayName("a line that says the default is removed")
    void defaultLinesRemoved() throws Exception {
        appendProperties("difficulty=Easy\nmax-players=21\n");
        final ServerProperties.Report report = restart();
        assertEquals(List.of("difficulty"), report.removedDefaults());
        assertEquals(List.of("max-players"), keysInFile());
    }

    @Test
    @DisplayName("per-world overrides move from worlds.toml, in either form, and apply to their world only")
    void perWorldOverrides() throws Exception {
        write(ServerConfig.WORLDS, read(ServerConfig.WORLDS)
            + "\n[worlds.world_nether]\n\"chunks.view-distance\" = 4\n"
            + "\n[worlds.world_the_end]\n\"not.a.setting\" = 1\n"
            + "\n[worlds.world_the_end.gameplay.hunger]\njump-walk-exhaustion = 0.2\n");
        appendProperties("worlds.default.chunks.simulation-distance=8\n");

        final ServerProperties.Report report = restart();

        assertEquals(List.of("worlds.world_nether.chunks.view-distance",
            "worlds.world_the_end.gameplay.hunger.jump-walk-exhaustion"), report.moved());
        final Properties file = loadProperties();
        assertEquals("4", file.getProperty("worlds.world_nether.chunks.view-distance"));
        assertEquals("0.2", file.getProperty("worlds.world_the_end.gameplay.hunger.jump-walk-exhaustion"));
        final CommentedConfig worlds = toml(ServerConfig.WORLDS);
        assertFalse(worlds.contains(List.of("worlds", "world_nether")), "the moved override and its empty table are gone");
        assertEquals(1, (int) worlds.get(List.of("worlds", "world_the_end", "not.a.setting")),
            "what is not a catalogued setting stays where it was");
        assertTrue(read(ServerConfig.WORLDS).contains("# [worlds.world_nether]"), "the commented examples survive");

        assertEquals(4, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));
        assertEquals(10, ServerConfig.forWorld("world").getInt(VIEW, 0));
        assertEquals(10, ServerConfig.getInt(VIEW, 0), "a world override is not a global one");
        assertEquals(0.2, ServerConfig.forWorld("world_the_end").getDouble(JUMP, 0));
        assertEquals(8, ServerConfig.forWorld("world").getInt(SIMULATION, 0), "worlds.default applies everywhere");
        assertEquals(8, ServerConfig.forWorld("world_nether").getInt(SIMULATION, 0));
        assertEquals(1, ServerConfig.forWorld("world_the_end").getInt("not.a.setting", 0));
        assertEquals(3, ServerProperties.status().byFile().get(ServerConfig.WORLDS));
    }

    @Test
    @DisplayName("an invalid value is reported with its line, the default is used, and the line is kept")
    void invalidValue() throws Exception {
        appendProperties("max-players=lots\ndifficulty=impossible\nworlds.world.chunks.view-distance=far\n");
        final ServerProperties.Report report = restart();

        assertEquals(3, report.invalid().size());
        assertTrue(report.invalid().getFirst().startsWith("max-players (line "), report.invalid().getFirst());
        assertEquals(20, ServerConfig.getInt(MAX_PLAYERS, 0));
        assertEquals("easy", ServerConfig.getString("server.difficulty", "?"));
        assertEquals(10, ServerConfig.forWorld("world").getInt(VIEW, 0));
        assertEquals("lots", loadProperties().getProperty("max-players"), "the operator's line is not touched");
        assertEquals(3, ServerProperties.status().invalid());
    }

    @Test
    @DisplayName("unknown keys are kept untouched, through reconciles and writes")
    void unknownKeysKept() throws Exception {
        appendProperties("some-panel-key=x y\nserver.motd=wrong name\n");
        final ServerProperties.Report report = restart();
        assertEquals(List.of("some-panel-key", MOTD), report.unknown());
        assertEquals("A Minecraft Server", ServerConfig.getString(MOTD, "?"), "a TOML path is not a vanilla key's name");

        ServerProperties.set(MAX_PLAYERS, 30).join();
        restart();
        final Properties file = loadProperties();
        assertEquals("x y", file.getProperty("some-panel-key"));
        assertEquals("wrong name", file.getProperty(MOTD));
        assertEquals("30", file.getProperty("max-players"));
        assertEquals(2, ServerProperties.status().unknown());
    }

    @Test
    @DisplayName("the operator's comments and order are kept; new keys join their group")
    void commentsAndOrderKept() throws Exception {
        final String mine = "# my server\n\n# players\nmax-players=40\n\n# looks\nmotd=Mine\n! old-style comment\n";
        write(ServerProperties.FILE_NAME, mine);
        editToml(ServerConfig.SERVER, "server.force-gamemode", "true");
        editToml(ServerConfig.PERFORMANCE, VIEW, "7");

        restart();

        final String text = read(ServerProperties.FILE_NAME);
        assertEquals("# my server\n\n# players\nmax-players=40\n\n# looks\nmotd=Mine\nforce-gamemode=true\n"
            + "! old-style comment\n\n# performance.toml\nview-distance=7\n", text.replace("\r\n", "\n"));
    }

    @Test
    @DisplayName("escapes, continued lines and unicode read as Properties reads them, and are written back the same way")
    void escapesAndUnicode() throws Exception {
        appendProperties("motd=Caf\\u00e9 \\= ok\\: \\\n    continued\n");
        restart();
        assertEquals("Café = ok: continued", ServerConfig.getString(MOTD, "?"));

        final String motd = "Ünï\ncode = #1 ☃ 😀";
        ServerProperties.set(MOTD, motd).join();
        assertEquals(motd, ServerConfig.getString(MOTD, "?"));
        assertEquals(motd, loadProperties().getProperty("motd"));
        for (final char c : read(ServerProperties.FILE_NAME).toCharArray()) {
            assertTrue(c < 0x80, "written as ASCII escapes");
        }
        restart();
        assertEquals(motd, ServerConfig.getString(MOTD, "?"));
    }

    @Test
    @DisplayName("on, off and on again loses nothing and duplicates nothing")
    void roundTrip() throws Exception {
        editToml(ServerConfig.SERVER, MOTD, "\"Round trip\"");
        editToml(ServerConfig.PERFORMANCE, VIEW, "7");
        editToml(ServerConfig.SERVER, "server.data-packs.initial-enabled-packs", "[\"vanilla\", \"bundle\"]");
        editToml(ServerConfig.PLUGINS, "plugins.commands.aliases", "{ \"hi\" = [\"say hi\"] }");
        write(ServerConfig.WORLDS, read(ServerConfig.WORLDS) + "\n[worlds.world_nether]\n\"chunks.view-distance\" = 4\n");
        appendProperties("max-players=50\n");
        restart();
        final List<String> keysOn = keysInFile();
        assertEquals(6, keysOn.size(), keysOn.toString());
        assertEquals("vanilla,bundle", loadProperties().getProperty("initial-enabled-packs"));

        final ServerProperties.Report off = ServerProperties.disable();
        assertFalse(off.enabled());
        assertFalse(Files.exists(properties()), "deleted once its values are in the TOML files");
        assertFalse(Files.exists(this.dir.resolve(ServerProperties.UNUSED_FILE_NAME)), "nothing was left over");
        assertEquals(6, off.transferred().size());
        assertEquals(false, toml(ServerConfig.SERVER).get(io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry.USE_SERVER_PROPERTIES));
        assertEquals("Round trip", toml(ServerConfig.SERVER).get(MOTD));
        assertEquals(50, (int) toml(ServerConfig.SERVER).get(MAX_PLAYERS));
        assertEquals(4, (int) toml(ServerConfig.WORLDS).get(List.of("worlds", "world_nether", VIEW)));
        assertEffective();
        assertEquals(6, ServerProperties.status().overrides(), "off, status counts what turning it on would move");

        restart();
        assertFalse(ServerProperties.isEnabled(), "the switch was saved");
        assertFalse(Files.exists(properties()));
        assertEffective();

        final ServerProperties.Report on = ServerProperties.enable();
        assertTrue(on.enabled());
        assertEquals(6, on.moved().size());
        assertEquals(keysOn.stream().sorted().toList(), keysInFile().stream().sorted().toList(), "the same keys, once each");
        assertEquals(template(ServerConfig.SERVER), read(ServerConfig.SERVER));
        assertEquals(template(ServerConfig.PERFORMANCE), read(ServerConfig.PERFORMANCE));
        assertEquals(template(ServerConfig.PLUGINS), read(ServerConfig.PLUGINS));
        assertFalse(toml(ServerConfig.WORLDS).contains(List.of("worlds", "world_nether")));
        assertEffective();

        restart();
        assertEquals(6, keysInFile().size());
        assertEffective();
    }

    private void assertEffective() {
        assertEquals("Round trip", ServerConfig.getString(MOTD, "?"));
        assertEquals(7, ServerConfig.getInt(VIEW, 0));
        assertEquals(50, ServerConfig.getInt(MAX_PLAYERS, 0));
        assertEquals(List.of("vanilla", "bundle"), ServerConfig.getStringList("server.data-packs.initial-enabled-packs"));
        assertEquals(4, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));
        assertEquals(7, ServerConfig.forWorld("world").getInt(VIEW, 0));
    }

    @Test
    @DisplayName("turning the switch off by hand moves the values on the next start, as the command would")
    void switchOffByHand() throws Exception {
        appendProperties("max-players=33\n");
        restart();
        editToml(ServerConfig.SERVER, io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry.USE_SERVER_PROPERTIES, "false");

        final ServerProperties.Report report = restart();

        assertFalse(report.enabled());
        assertEquals(List.of("max-players"), report.transferred());
        assertFalse(Files.exists(properties()));
        assertEquals(33, ServerConfig.getInt(MAX_PLAYERS, 0));
    }

    @Test
    @DisplayName("turning it off keeps the lines that cannot go into a TOML file")
    void disableKeepsWhatItCannotMove() throws Exception {
        appendProperties("some-panel-key=x\nmax-players=lots\nmotd=Kept\n");
        restart();

        final ServerProperties.Report report = ServerProperties.disable();

        assertEquals(List.of("motd"), report.transferred());
        assertEquals(List.of("some-panel-key", "max-players"), report.unused());
        assertFalse(Files.exists(properties()));
        final String unused = read(ServerProperties.UNUSED_FILE_NAME);
        assertTrue(unused.contains("some-panel-key=x") && unused.contains("max-players=lots"), unused);
        assertFalse(unused.contains("motd=Kept"), "what moved into the TOML files is not kept twice");
        assertEquals("Kept", ServerConfig.getString(MOTD, "?"));
    }

    @Test
    @DisplayName("secrets and the switch never go to server.properties")
    void secretsExcluded() throws Exception {
        editToml(ServerConfig.NETWORK, SECRET, "\"other.secret\"");
        appendProperties(SECRET + "=stolen.secret\nserver.use-server-properties=false\n");

        final ServerProperties.Report report = restart();

        assertTrue(report.moved().isEmpty());
        assertEquals(List.of(SECRET, "server.use-server-properties"), report.unknown());
        assertEquals("other.secret", toml(ServerConfig.NETWORK).get(SECRET), "the secret stays in its TOML");
        assertEquals("other.secret", ServerConfig.getString(SECRET, "?"));
        assertTrue(ServerProperties.isEnabled(), "the switch is read from server.toml only");

        ServerProperties.set(SECRET, "third.secret").join();
        assertEquals("third.secret", toml(ServerConfig.NETWORK).get(SECRET), "a write to a secret goes to its TOML");
        assertFalse(read(ServerProperties.FILE_NAME).contains("third.secret"));
        assertThrows(IllegalArgumentException.class,
            () -> ServerProperties.set(io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry.USE_SERVER_PROPERTIES, false));
    }

    @Test
    @DisplayName("writes go to server.properties when on, and setting the default removes the line")
    void writesWhenOn() throws Exception {
        ServerProperties.set("server.difficulty", "hard").join();
        ServerProperties.set(VIEW, 5, "world_nether").join();
        assertEquals("hard", ServerConfig.getString("server.difficulty", "?"));
        assertEquals(5, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));
        assertEquals("hard", loadProperties().getProperty("difficulty"));
        assertEquals("5", loadProperties().getProperty("worlds.world_nether.chunks.view-distance"));
        assertEquals(template(ServerConfig.SERVER), read(ServerConfig.SERVER), "the TOML is not written when on");

        ServerProperties.set("server.difficulty", "easy").join();
        ServerProperties.reset(VIEW, "world_nether").join();
        assertTrue(keysInFile().isEmpty());
        assertEquals(10, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));

        assertThrows(InvalidSettingException.class, () -> ServerProperties.set(MAX_PLAYERS, "many"));
        assertThrows(IllegalArgumentException.class, () -> ServerProperties.set(MOTD, "x", "world"));
        assertThrows(IllegalArgumentException.class, () -> ServerProperties.set("no.such.setting", 1));
    }

    @Test
    @DisplayName("writes go to the TOML files when off, keeping their comments")
    void writesWhenOff() throws Exception {
        ServerProperties.disable();
        ServerProperties.set(MOTD, "Off").join();
        ServerProperties.set(VIEW, 3, "world_nether").join();

        assertFalse(Files.exists(properties()));
        assertEquals("Off", ServerConfig.getString(MOTD, "?"));
        assertEquals(3, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));
        assertEquals("Off", toml(ServerConfig.SERVER).get(MOTD));
        assertEquals(template(ServerConfig.SERVER).lines().count(), read(ServerConfig.SERVER).lines().count());
        assertEquals(3, (int) toml(ServerConfig.WORLDS).get(List.of("worlds", "world_nether", VIEW)));

        ServerProperties.reset(VIEW, "world_nether").join();
        assertEquals(10, ServerConfig.forWorld("world_nether").getInt(VIEW, 0));
        assertFalse(toml(ServerConfig.WORLDS).contains(List.of("worlds", "world_nether")));
    }

    @Test
    @DisplayName("concurrent writes from many threads all land, in order, once each")
    void concurrentWrites() throws Exception {
        final int threads = 8;
        final int writes = 25;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> done = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final String world = "w" + t;
            done.add(pool.submit(() -> {
                start.await();
                for (int i = 1; i <= writes; i++) {
                    ServerProperties.set(VIEW, 2 + i, world);
                    ServerProperties.set(MAX_PLAYERS, 100 + i);
                }
                return null;
            }));
        }
        start.countDown();
        for (final Future<?> future : done) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        ServerProperties.flush();

        final Properties file = loadProperties();
        for (int t = 0; t < threads; t++) {
            assertEquals(String.valueOf(2 + writes), file.getProperty("worlds.w" + t + ".chunks.view-distance"));
            assertEquals(2 + writes, ServerConfig.forWorld("w" + t).getInt(VIEW, 0));
        }
        assertEquals(String.valueOf(100 + writes), file.getProperty("max-players"));
        assertEquals(100 + writes, ServerConfig.getInt(MAX_PLAYERS, 0));
        assertEquals(threads + 1, keysInFile().size(), "every key once");
        assertEquals(keysInFile().stream().distinct().count(), keysInFile().size());
        assertEquals(List.of(), Files.list(this.dir).filter(path -> path.toString().endsWith(".tmp")).toList(),
            "no temporary file left behind");

        restart();
        assertEquals(2 + writes, ServerConfig.forWorld("w3").getInt(VIEW, 0), "and they read back after a restart");
    }
}
