package io.github.adrielorihuela.legacyfolia.bots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The functional test bench: starts the given LegacyFolia jar in fresh folders, drives it with protocol bots and the
 * console, and writes one PASS, FAIL or NOT_RUN per scenario of docs/TESTING.md.
 *
 * <pre>
 * --jar PATH     server jar (required)
 * --out PATH     report, default testing/results/bots.json
 * --port N       game port, default 25611 (RCON uses 25612 and 25613)
 * --props config|root   where server.properties is expected, default config (RC 1)
 * --only a,b     groups: main(locator,pvp,tooltips), off, fresh, dims, lobby, migration, eula
 * --keep         keep the temporary server folders
 * </pre>
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Path jar = null;
        Path out = Path.of("testing/results/bots.json");
        int port = 25611;
        String props = "config/server.properties";
        boolean keep = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--props" -> props = args[++i].equals("root") ? "server.properties" : "config/server.properties";
                case "--only" -> System.setProperty("bench.only", args[++i]);
                case "--keep" -> keep = true;
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (jar == null || !Files.exists(jar)) {
            System.err.println("usage: --jar <legacyfolia paperclip jar> [--out file] [--port n] [--props config|root] [--only groups] [--keep]");
            System.exit(2);
        }
        Path root = Files.createTempDirectory("legacyfolia-bench");
        if (System.getProperty("bench.probe") != null) {
            probe(jar, root, port, Path.of(System.getProperty("bench.probe")));
            return;
        }
        System.out.println("jar " + jar + "\nworking folders under " + root);
        Groups g = new Groups(jar, root, port, props);
        step("main", () -> g.main(root), "main");
        step("off", g::combatOff, "off");
        step("fresh", g::fresh, "fresh");
        step("dims", g::dimensions, "dims");
        step("lobby", g::lobby, "lobby");
        step("migration", g::migration, "migration");
        step("eula", g::eula, "eula");
        if (System.getProperty("bench.only", "").isEmpty()) {
            String live = "the updater downloads from Hangar and GitHub; the bench does not depend on live downloads, so run it by hand (docs/TESTING.md item 9)";
            for (String id : new String[] {"9b", "9c", "9d", "9e", "9f", "9g", "9h"}) {
                Scn.notRun(id, "Plugin updater " + id, live);
            }
            Scn.notRun("3d", "Tooltips: a 1.8 client through ViaVersion", "needs a 1.8 client; testing/bot18 drives one (mineflayer 1.8.8)");
            Scn.notRun("10c", "General: normal play with far-apart players and ViaVersion", "a soak with ViaVersion installed; the log of every run here is checked for crashes only through the group results");
        }
        Scn.write(out, jar.getFileName().toString(), "bots, MCProtocolLib 26.3-2, flat world; see testing/bots/README.md");
        long pass = Scn.RESULTS.stream().filter(r -> r.status().equals("PASS")).count();
        long fail = Scn.RESULTS.stream().filter(r -> r.status().equals("FAIL")).count();
        long nr = Scn.RESULTS.size() - pass - fail;
        System.out.printf("%n%d PASS, %d FAIL, %d NOT_RUN -> %s%n", pass, fail, nr, out);
        if (!keep) {
            try (Stream<Path> w = Files.walk(root)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (Exception ignored) {
            }
        }
        System.exit(0);
    }

    /** Debug aid: starts the flat-world server with one bot and prints the answer to each line of a file. */
    static void probe(Path jar, Path root, int port, Path commands) throws Exception {
        Groups g = new Groups(jar, root, port, "config/server.properties");
        Path d = g.dir("probe");
        Groups.cfg(d, "server.toml", "[server]\neula = true\n");
        Groups.cfg(d, "world.toml", "[world.generation]\nlevel-type = \"minecraft:flat\"\n");
        ServerProc s = g.start(d, "--online-mode", "false");
        Bot b = new Bot("BotA", "127.0.0.1", port);
        b.connect();
        Thread.sleep(1500);
        for (String c : Files.readAllLines(commands)) {
            if (!c.isBlank()) {
                System.out.println("> " + c + PROBE_NL + strip(s.cmdOut(c, 900)));
            }
        }
        b.disconnect();
        s.stop();
        System.exit(0);
    }

    static final String PROBE_NL = System.lineSeparator();

    static String strip(String s) {
        return s.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    interface Body {
        void run() throws Exception;
    }

    static void step(String group, Body b, String key) {
        String only = System.getProperty("bench.only", "");
        boolean wanted = only.isEmpty() || java.util.List.of(only.split(",")).contains(key)
            || (key.equals("main") && (only.contains("locator") || only.contains("pvp") || only.contains("tooltips")));
        if (!wanted) {
            return;
        }
        System.out.println("\n=== group " + group + " ===");
        try {
            b.run();
        } catch (Throwable t) {
            Scn.record("group-" + group, "Group " + group + " could not run to the end", "FAIL", t.toString().lines().limit(8).reduce("", (p, q) -> p + q + " | "));
        }
    }
}
