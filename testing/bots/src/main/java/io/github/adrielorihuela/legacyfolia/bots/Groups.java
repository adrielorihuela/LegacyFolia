package io.github.adrielorihuela.legacyfolia.bots;

import static io.github.adrielorihuela.legacyfolia.bots.Scn.Fail;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.need;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.notRun;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/** The server-level scenario groups: each starts its own servers in fresh folders. */
final class Groups {
    final Path jar;
    final Path root;
    final int port;
    final String propsRel;

    Groups(Path jar, Path root, int port, String propsRel) {
        this.jar = jar;
        this.root = root;
        this.port = port;
        this.propsRel = propsRel;
    }

    // ---- helpers ----

    Path dir(String name) throws IOException {
        Path d = root.resolve(name);
        Files.createDirectories(d.resolve("config"));
        return d;
    }

    static void cfg(Path dir, String file, String text) throws IOException {
        Files.createDirectories(dir.resolve("config"));
        Files.writeString(dir.resolve("config").resolve(file), text);
    }

    ServerProc start(Path dir, String... extra) throws Exception {
        String[] args = new String[extra.length + 2];
        args[0] = "--port";
        args[1] = String.valueOf(port);
        System.arraycopy(extra, 0, args, 2, extra.length);
        return ServerProc.startReady(jar, dir, 240_000, args);
    }

    static String bot(String n) {
        return n;
    }

    static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return null;
        }
    }

    static boolean dataLines(String text) {
        return text.lines().anyMatch(l -> !l.isBlank() && !l.startsWith("#"));
    }

    static String secretOf(Path p) {
        String s = read(p);
        return s == null ? null : s.strip();
    }

    static String sectionReplace(String text, String section, String from, String to) {
        int i = text.indexOf("[" + section + "]");
        if (i < 0) {
            return text;
        }
        int j = text.indexOf(from, i);
        return j < 0 ? text : text.substring(0, j) + to + text.substring(j + from.length());
    }

    static boolean scanForSecrets(Path dir, String... secrets) throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        try (Stream<Path> s = Files.walk(dir.resolve("config"), 1)) {
            s.filter(p -> p.toString().endsWith(".toml") || p.toString().endsWith("server.properties")).forEach(files::add);
        }
        Path rootProps = dir.resolve("server.properties");
        if (Files.exists(rootProps)) {
            files.add(rootProps);
        }
        for (Path f : files) {
            String t = Files.readString(f);
            for (String sec : secrets) {
                if (sec != null && !sec.isBlank() && t.contains(sec)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean eulaLine(Path props) throws IOException {
        return Files.exists(props) && Files.readAllLines(props).stream().anyMatch(l -> l.strip().startsWith("eula"));
    }

    // ---- main run: flat world, three bots ----

    void main(Path out) throws Exception {
        Path d = dir("main");
        cfg(d, "server.toml", "[server]\neula = true\n");
        cfg(d, "world.toml", "[world.generation]\nlevel-type = \"minecraft:flat\"\n");
        ServerProc s = start(d, "--online-mode", "false");
        Ctx x = new Ctx(s, port);
        try {
            x.joinThree();
            System.out.println("arena: ground y=" + x.gy + " origin " + x.ox + "," + x.oz);
            if (Scn.only("locator")) {
                Locator.all(x);
            }
            if (Scn.only("pvp")) {
                Pvp.all(x);
            }
            if (Scn.only("tooltips")) {
                Tooltips.all(x);
                Tooltips.reload(x, d);
            }
        } finally {
            x.quit();
            s.stop();
        }
    }

    // ---- 2n: combat disabled ----

    void combatOff() throws Exception {
        Path d = dir("off");
        cfg(d, "server.toml", "[server]\neula = true\n");
        cfg(d, "world.toml", "[world.generation]\nlevel-type = \"minecraft:flat\"\n");
        cfg(d, "combat.toml", "[combat]\nenabled = false\n");
        ServerProc s = start(d, "--online-mode", "false");
        Ctx x = new Ctx(s, port);
        try {
            x.joinThree();
            Bot a = x.a;
            Bot b = x.b;
            run("2n", "PvP: combat.enabled = false is pure 26.3 (cooldown, 7 damage, shields, 26.3 tooltips)", () -> {
                x.pair(a, b);
                ItemStack st = x.give(a, "diamond_sword");
                need(!Ctx.hasModifiers(st), "with combat off the sword still carried a 1.8 attribute_modifiers component");
                double quick = x.hit(a, b);
                need(quick > 0 && quick < 6, "a hit right after taking the sword should be reduced by the cooldown (modern), did " + Scn.fmt(quick));
                x.pair(a, b);
                x.hold(a, "diamond_sword");
                Thread.sleep(1500);
                                String full = Scn.near("fully charged diamond sword", x.hitPeak(a, b), 7, 0.1);
                x.pair(a, b);
                x.hold(a, "diamond_sword");
                x.srv.send("clear BotB"); x.srv.send("give BotB minecraft:shield");
                Thread.sleep(300);
                b.use(Hand.MAIN_HAND);
                Thread.sleep(700);
                Thread.sleep(1500);
                double shielded = x.hitPeak(a, b);
                need(shielded < 0.5, "a raised shield should block the hit in 26.3, the victim lost " + Scn.fmt(shielded));
                return "tooltip untouched, immediate hit " + Scn.fmt(quick) + ", " + full + ", shield blocked (PVP-1.8.md 'LegacyFolia, Paper and Folia')";
            });
        } finally {
            x.quit();
            s.stop();
        }
    }

    // ---- fresh server: EULA, properties mode, RCON, files ----

    void fresh() throws Exception {
        Path d = dir("fresh");
        cfg(d, "server.toml", "[server]\neula = true\n");
        Path props = d.resolve(propsRel);
        String[] forbiddenRoot = {"bukkit.yml", "spigot.yml", "commands.yml", "paper-global.yml", "paper-world-defaults.yml", "eula.txt",
            "whitelist.json", "ops.json", "banned-players.json", "banned-ips.json", "usercache.json"};

        // start 1: nothing but the EULA
        ServerProc s = start(d);
        int mark = 0;
        String log1 = s.joined();
        Path secretFile = d.resolve("config/rcon.secret");
        String secret1 = secretOf(secretFile);
        run("8a", "RCON: a 32 character password is generated and never logged", () -> {
            need(log1.contains("Generated an RCON password in") && log1.contains("rcon.secret"), "the log does not announce the generated RCON password");
            need(secret1 != null && secret1.length() == 32, "rcon.secret is missing or not 32 characters: " + (secret1 == null ? "absent" : secret1.length()));
            need(!log1.contains(secret1), "the RCON password appears in the log");
            return "32 characters in config/rcon.secret, absent from the log";
        });
        run("9a", "Plugin updater: config/github.token exists and is empty", () -> {
            Path t = d.resolve("config/github.token");
            need(Files.exists(t), "config/github.token does not exist");
            need(read(t).strip().isEmpty(), "config/github.token is not empty");
            return "exists, empty";
        });
        run("4d", "EULA: a start with eula = true asks nothing", () -> {
            need(!log1.contains("Press ENTER") && !log1.contains("agree to the EULA"), "the log shows an EULA prompt although eula = true");
            return "no prompt in the log";
        });
        s.stop();

        run("5a", "server.properties mode: the file exists with only its header", () -> {
            need(Files.exists(props), propsRel + " was not created");
            need(!dataLines(Files.readString(props)), propsRel + " holds settings on a fresh server:\n" + Files.readString(props));
            return propsRel + " has only comments";
        });
        run("10a", "General: the root holds none of the old files", () -> {
            List<String> names = Ctx.names(d);
            StringBuilder bad = new StringBuilder();
            for (String n : names) {
                if (List.of(forbiddenRoot).contains(n) || (n.startsWith("paper-") && n.endsWith(".yml"))) {
                    bad.append(n).append(' ');
                }
            }
            if (propsRel.startsWith("config") && names.contains("server.properties")) {
                bad.append("server.properties ");
            }
            if (!propsRel.startsWith("config") && !names.contains("server.properties")) {
                bad.append("(server.properties missing from the root) ");
            }
            need(bad.isEmpty(), "unexpected in the root: " + bad + "; root is " + names);
            return "root is " + names;
        });
        run("10b", "General: config/ holds the nine commented TOML files", () -> {
            List<String> want = List.of("combat.toml", "gameplay.toml", "network.toml", "performance.toml", "players.toml", "plugins.toml", "server.toml", "world.toml", "worlds.toml");
            List<String> toml = Ctx.names(d.resolve("config")).stream().filter(n -> n.endsWith(".toml")).toList();
            need(toml.equals(want), "TOML files are " + toml + ", expected " + want);
            for (String f : want) {
                List<String> ls = Files.readAllLines(d.resolve("config").resolve(f));
                for (int i = 0; i < ls.size(); i++) {
                    String l = ls.get(i);
                    if (l.matches("^[A-Za-z0-9_.\"-]+ *=.*")) {
                        int up = i - 1;
                        while (up >= 0 && (ls.get(up).isEmpty() || ls.get(up).startsWith("["))) {
                            up--;
                        }
                        String prev = up < 0 ? "" : ls.get(up);
                        need(prev.startsWith("#") || prev.equals("]") || prev.matches("^[A-Za-z0-9_.\"-]+ *=.*"), f + " line " + (i + 1) + " has no comment above: " + l);
                    }
                }
            }
            return "nine files; every setting is commented (own comment, the key above, or the comment above its [table])";
        });

        // start 2: own password, a motd and max-players edited, RCON moved to our port
        Files.writeString(secretFile, "my-own-password-123");
        Path net = d.resolve("config/network.toml");
        Files.writeString(net, sectionReplace(Files.readString(net), "network.rcon", "port = 25575", "port = 25612"));
        Files.writeString(props, Files.readString(props) + "motd=Test\n");
        Path st = d.resolve("config/server.toml");
        Files.writeString(st, Files.readString(st).replace("max-players = 20", "max-players = 5"));
        s = start(d);
        String motd2 = Net.motd(Net.status("127.0.0.1", port));
        run("5b", "server.properties mode: motd=Test reaches the server list; TOML keeps the default", () -> {
            need("Test".equals(motd2), "server list MOTD is '" + motd2 + "', expected Test");
            need(read(st).contains("motd = \"A Minecraft Server\""), "server.toml no longer shows the default motd");
            return "MOTD 'Test' in the status response";
        });
        run("5c", "server.properties mode: a TOML change moves into server.properties", () -> {
            need(read(props).contains("max-players=5"), "max-players=5 was not moved into " + propsRel);
            need(read(st).contains("max-players = 20"), "server.toml did not go back to the default max-players = 20");
            return "max-players=5 now in " + propsRel + ", server.toml back to 20";
        });
        run("8b", "RCON: local login and a command work", () -> {
            String r = Net.rcon("127.0.0.1", 25612, "my-own-password-123", "list");
            need(r != null, "nothing listens on the RCON port 25612");
            need(!r.equals("AUTH-FAILED"), "RCON refused our own password written into rcon.secret (8d)");
            need(r.toLowerCase().contains("players"), "unexpected RCON answer: " + r);
            return "'list' answered over RCON: " + r.strip();
        });
        run("8d", "RCON: a password written into rcon.secret is the one that works", () -> {
            String bad = Net.rcon("127.0.0.1", 25612, "wrong-password", "list");
            need("AUTH-FAILED".equals(bad), "a wrong password was not refused: " + bad);
            return "own password accepted, a wrong one refused";
        });
        run("8c", "RCON: bound to 127.0.0.1 only", () -> {
            String ip = firstNonLoopback();
            if (ip == null) {
                throw new Scn.NotRun("no non-loopback address on this machine to try");
            }
            need(!Net.open(ip, 25612), "RCON accepts connections on " + ip);
            return "port 25612 closed on " + ip;
        });
        boolean eulaLeak2 = eulaLine(props);
        boolean secretLeak2 = scanForSecrets(d, "my-own-password-123", secretOf(d.resolve("config/forwarding.secret")));
        s.stop();

        // start 3: motd line removed, rcon.secret deleted
        Files.writeString(props, read(props).replaceAll("(?m)^motd=.*\\R?", ""));
        Files.delete(secretFile);
        s = start(d);
        String motd3 = Net.motd(Net.status("127.0.0.1", port));
        run("5d", "server.properties mode: deleting the motd line restores the default", () -> {
            need("A Minecraft Server".equals(motd3), "server list MOTD is '" + motd3 + "' after deleting the line");
            return "default MOTD back";
        });
        String secret3 = secretOf(secretFile);
        run("8e", "RCON: deleting rcon.secret generates a new one", () -> {
            need(secret3 != null && secret3.length() == 32, "no new 32 character rcon.secret");
            need(!secret3.equals("my-own-password-123"), "rcon.secret was not regenerated");
            String r = Net.rcon("127.0.0.1", 25612, secret3, "list");
            need(r != null && !r.equals("AUTH-FAILED"), "the new password does not log in: " + r);
            return "regenerated and working";
        });
        final ServerProc serverRef = s;
        run("5e", "server.properties mode: /whitelist and /setidletimeout write to the file; /defaultgamemode, as in vanilla, does not", () -> {
            serverRef.send("whitelist on");
            serverRef.send("defaultgamemode creative");
            serverRef.send("setidletimeout 5");
            Thread.sleep(2500);
            String p = read(props);
            need(p.contains("white-list=true"), "white-list=true not written after /whitelist on");
            // Vanilla's /defaultgamemode only sets the level's game type (MinecraftServer.setDefaultGameType); the dedicated
            // server's setGameMode, which would write server.properties, is called only by the management protocol
            // (MinecraftServerSettingsServiceImpl), not by the command. So the line must stay out, as in vanilla.
            need(!p.contains("gamemode=creative"), "gamemode=creative was written after /defaultgamemode creative, which vanilla does not do");
            need(serverRef.cmdOut("defaultgamemode creative", 800).toLowerCase().contains("default game mode"), "/defaultgamemode creative did not answer with the new default game mode");
            need(p.contains("player-idle-timeout=5"), "player-idle-timeout=5 not written after /setidletimeout 5");
            serverRef.send("whitelist off");
            Thread.sleep(800);
            return "the two lines appeared without a restart, gamemode stayed out as in vanilla (/difficulty not checked)";
        });
        run("5f", "server.properties mode: 'legacyfolia properties off' moves settings to TOML and deletes the file", () -> {
            serverRef.send("legacyfolia properties status");
            Thread.sleep(800);
            serverRef.send("legacyfolia properties off");
            Thread.sleep(2500);
            need(!Files.exists(props), propsRel + " still exists after 'properties off'");
            String t = read(st);
            need(t.contains("max-players = 5"), "max-players = 5 did not move back into server.toml");
            return "file deleted, settings moved into the TOML files; .unused " + (Files.exists(d.resolve("config/server.properties.unused")) ? "present" : "absent");
        });
        run("5g", "server.properties mode: 'legacyfolia properties on' rebuilds the file", () -> {
            serverRef.send("legacyfolia properties on");
            Thread.sleep(2500);
            need(Files.exists(props), propsRel + " was not recreated by 'properties on'");
            need(read(props).contains("max-players=5"), "the non-default max-players was not moved back into the new file");
            return "recreated with the non-defaults";
        });
        boolean eulaLeak3 = eulaLine(props);
        boolean secretLeak3 = scanForSecrets(d, secret3, secretOf(d.resolve("config/forwarding.secret")));
        run("5h", "server.properties mode: never an eula line, never a secret", () -> {
            need(!eulaLeak2 && !eulaLeak3, "an eula line appeared in server.properties");
            need(!secretLeak2 && !secretLeak3, "a secret appeared in a TOML file or server.properties");
            return "checked after two starts and after properties off/on";
        });
        notRun("5i", "server.properties mode: a plugin reading the server's settings sees them",
            "needs a plugin on the server; the old yml files being absent is covered by 10a");
        s.stop();

        // start 4: RCON switched off
        Files.writeString(net, sectionReplace(read(net), "network.rcon", "enabled = true", "enabled = false"));
        if (Files.exists(props)) {
            Files.writeString(props, read(props).replaceAll("(?m)^rcon\\.enabled=.*\\R?", "").replaceAll("(?m)^network\\.rcon\\.enabled=.*\\R?", ""));
        }
        s = start(d);
        run("8f", "RCON: enabled = false closes the port", () -> {
            need(!Net.open("127.0.0.1", 25612), "port 25612 still open with [network.rcon] enabled = false");
            return "port 25612 closed";
        });
        s.stop();
    }

    static String firstNonLoopback() {
        try {
            for (var ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (var a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLinkLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException ignored) {
        }
        return null;
    }

    // ---- dimensions ----

    static boolean dimensionWorks(ServerProc s, String dim) throws InterruptedException {
        String out = s.cmdOut("execute in " + dim + " run say probe", 1500);
        return Pattern.compile("\\[Server\\] probe").matcher(out).find();
    }

    void dimensions() throws Exception {
        Path d = dir("dims");
        cfg(d, "server.toml", "[server]\neula = true\n");
        ServerProc s = start(d);
        boolean n1 = dimensionWorks(s, "minecraft:the_nether");
        s.stop();
        Path props = d.resolve(propsRel);
        cfg(d, "world.toml", "[dimensions]\nnether = false\nend = false\noverworld = false\n");
        s = start(d);
        String log = s.joined();
        boolean nOff = dimensionWorks(s, "minecraft:the_nether");
        boolean eOff = dimensionWorks(s, "minecraft:the_end");
        boolean oOn = dimensionWorks(s, "minecraft:overworld");
        run("7a", "Dimensions: nether = false is announced and not loaded", () -> {
            need(n1, "precondition: the Nether should exist with defaults");
            need(Pattern.compile("(?i)nether[^\\n]*(disabled|not loaded|off)|(disabl|skip)[^\\n]*nether").matcher(log).find(), "the log never says the Nether is disabled");
            need(!nOff, "the Nether is still loaded");
            return "announced in the log; 'execute in the_nether' finds no dimension (portal not tried)";
        });
        run("7b", "Dimensions: end = false is not loaded", () -> {
            need(Pattern.compile("(?i)(\\bend\\b|the_end)[^\\n]*(disabled|not loaded|off)|(disabl|skip)[^\\n]*(\\bend\\b|the_end)").matcher(log).find(), "the log never says the End is disabled");
            need(!eOff, "the End is still loaded");
            return "announced in the log and not loaded";
        });
        run("7c", "Dimensions: overworld = false is refused with a warning", () -> {
            need(oOn, "the overworld did not load");
            need(Pattern.compile("(?i)overworld[^\\n]*(cannot|can't|refus|must|ignored|always|required)").matcher(log).find(), "no warning about overworld = false in the log");
            return "warning logged, overworld loaded";
        });
        s.stop();
        boolean kept = Files.walk(d.resolve("world"), 5).anyMatch(p -> p.getFileName().toString().equals("the_nether") || p.getFileName().toString().equals("DIM-1"));
        cfg(d, "world.toml", "[dimensions]\nnether = true\nend = true\noverworld = true\n");
        if (Files.exists(props)) {
            Files.writeString(props, read(props).replaceAll("(?m)^(dimensions\\.|world\\.dimensions\\.)?(nether|end|overworld)=.*\\R?", ""));
        }
        s = start(d);
        boolean nBack = dimensionWorks(s, "minecraft:the_nether");
        boolean eBack = dimensionWorks(s, "minecraft:the_end");
        s.stop();
        run("7d", "Dimensions: turning them back on reuses the existing folders", () -> {
            need(kept, "the Nether folder was deleted while the dimension was off");
            need(nBack && eBack, "after turning them back on the Nether=" + nBack + " End=" + eBack + " are not loaded");
            return "folders kept, both dimensions load again";
        });
        placement();
    }

    /** What one right-click on the top of a block did: the stone is there or not, and what the server sent back. */
    record Click(boolean placed, int updates, int acks, String seen) {
        @Override
        public String toString() {
            return (placed ? "placed" : "refused") + " (" + updates + " block update(s), " + acks + " ack(s)) " + seen;
        }
    }

    /** Gives the bot stone, aims at the top of (bx, by, bz) and right-clicks it once; reports if stone appeared above. */
    static Click clickStone(Ctx x, Bot a, int bx, int by, int bz) throws InterruptedException {
        x.hold(a, "stone");
        a.select(0);
        Thread.sleep(300);
        int u = a.blockUpdates.size();
        int k = a.blockAcks.size();
        a.lookAt(bx + 0.5, by + 1, bz + 0.5);
        Thread.sleep(150);
        a.useBlock(bx, by, bz);
        // The server answers on the player's region tick, which can be a second or more behind while chunks load.
        a.waitFor(p -> p.blockAcks.size() > k, 10000);
        Thread.sleep(500);
        java.util.List<String> seen = new java.util.ArrayList<>();
        synchronized (a.blockUpdates) {
            for (Bot.Timed<String> t : a.blockUpdates.subList(u, a.blockUpdates.size())) {
                seen.add(t.value());
            }
        }
        String key = bx + "," + (by + 1) + "," + bz + ":";
        String last = seen.stream().filter(v -> v.startsWith(key)).reduce((p, q) -> q).orElse(null);
        return new Click(last != null && !last.equals(key + "0"), seen.size(), a.blockAcks.size() - k, "target " + bx + "," + by + "," + bz + ", updates there " + seen.stream().filter(v -> v.startsWith(key) || v.startsWith(bx + "," + by + "," + bz + ":")).toList());
    }

    void placement() throws Exception {
        // 7i: a flat world, one op, a non-op bot building two blocks from where it spawns (the world spawn).
        Path d = dir("spawnbuild");
        cfg(d, "server.toml", "[server]\neula = true\n");
        cfg(d, "world.toml", "[world.generation]\nlevel-type = \"minecraft:flat\"\n");
        ServerProc s = start(d, "--online-mode", "false");
        Click spawn = null;
        String why = null;
        try {
            Ctx x = new Ctx(s, port);
            s.send("op BotOp");
            Bot a = x.join("BotA");
            s.send("gamemode survival BotA");
            Thread.sleep(800);
            spawn = clickStone(x, a, (int) Math.floor(a.x) + 2, (int) Math.floor(a.y) - 1, (int) Math.floor(a.z));
            a.disconnect();
        } catch (Exception e) {
            why = e.toString();
        } finally {
            s.stop();
        }
        Click sp = spawn;
        String err = why;
        run("7i", "Dimensions: spawn protection is 0 so a non-op can build at spawn", () -> {
            need(err == null, "bench error: " + err);
            need(sp.placed(), "a non-op could not place stone beside the world spawn: " + sp);
            return "non-op placed stone beside the spawn with an op on the list: " + sp;
        });

        // 7h: a Nether roof placement with the setting off (the control, proves the click works) and on.
        Click[] roof = new Click[2];
        String[] whyRoof = new String[2];
        for (int i = 0; i < 2; i++) {
            Path r = dir("roof" + i);
            cfg(r, "server.toml", "[server]\neula = true\n");
            cfg(r, "world.toml", "[world]\ndisable-nether-roof = " + (i == 1) + "\n");
            ServerProc t = start(r, "--online-mode", "false");
            try {
                Ctx x = new Ctx(t, port);
                Bot a = x.join("BotA");
                t.send("gamemode survival BotA");
                x.tpIn("minecraft:the_nether", a, 8.5, 128, 8.5);
                t.send("execute in minecraft:the_nether run fill 6 127 6 12 127 10 minecraft:bedrock");
                t.send("execute in minecraft:the_nether run fill 6 128 6 12 140 10 minecraft:air");
                Thread.sleep(800);
                roof[i] = clickStone(x, a, 10, 127, 8);
                a.disconnect();
            } catch (Exception e) {
                whyRoof[i] = e.toString();
            } finally {
                t.stop();
            }
        }
        run("7h", "Dimensions: disable-nether-roof refuses blocks above y=127", () -> {
            need(whyRoof[0] == null && whyRoof[1] == null, "bench error: " + whyRoof[0] + " / " + whyRoof[1]);
            need(roof[0].placed(), "control: with disable-nether-roof = false the stone was not placed at y=128: " + roof[0]);
            need(roof[1].acks() > 0, "with disable-nether-roof = true the server never answered the click, so the refusal proves nothing: " + roof[1]);
            need(!roof[1].placed(), "disable-nether-roof = true still let a block go in at y=128: " + roof[1]);
            return "off: " + roof[0] + "; on: " + roof[1];
        });
    }

    // ---- lobby ----

    void lobby() throws Exception {
        String[][] variants = {
            {"7e", "true", "logout-world", "Nether spawn (not where he logged out)"},
            {"7f", "true", "main-world", "overworld spawn"},
            {"7g", "false", "logout-world", "where he logged out"}};
        for (String[] v : variants) {
            Path d = dir("lobby-" + v[0]);
            cfg(d, "server.toml", "[server]\neula = true\n");
            cfg(d, "world.toml", "[world]\nalways-spawn-at-world-spawn = " + v[1] + "\nlobby-spawn-world = \"" + v[2] + "\"\n");
            ServerProc s = start(d, "--online-mode", "false");
            try {
                Ctx x = new Ctx(s, port);
                Bot b = x.join("LobbyBot");
                s.send("clear LobbyBot"); x.srv.send("give LobbyBot minecraft:diamond_sword");
                x.tpIn("minecraft:the_nether", b, 100.5, 131, 100.5);
                Thread.sleep(500);
                b.disconnect();
                Thread.sleep(2500);
                Bot b2 = x.join("LobbyBot");
                String dim = b2.dimension;
                double dist = Math.hypot(b2.x - 100.5, b2.z - 100.5);
                Boolean kept = s.test("if items entity LobbyBot weapon.mainhand minecraft:diamond_sword");
                b2.disconnect();
                run(v[0], "Lobby: always-spawn-at-world-spawn=" + v[1] + ", lobby-spawn-world=" + v[2] + " arrives at " + v[3], () -> {
                    switch (v[0]) {
                        case "7e" -> {
                            need(dim.equals("minecraft:the_nether"), "arrived in " + dim + ", expected the Nether (logout world)");
                            need(dist > 5, "arrived where he logged out (" + Scn.fmt(dist) + " blocks away) instead of the Nether's spawn");
                        }
                        case "7f" -> need(dim.equals("minecraft:overworld"), "arrived in " + dim + ", expected the main world");
                        default -> {
                            need(dim.equals("minecraft:the_nether"), "arrived in " + dim + ", expected the Nether");
                            need(dist < 3, "arrived " + Scn.fmt(dist) + " blocks from where he logged out");
                        }
                    }
                    need(Boolean.TRUE.equals(kept), "the inventory was not kept");
                    return "arrived in " + dim + ", " + Scn.fmt(dist) + " blocks from the logout point, inventory kept";
                });
            } finally {
                s.stop();
            }
        }
    }

    // ---- migration ----

    static String uuidOf(String name) {
        return new Bot(name, "", 0).uuid.toString();
    }

    void migration() throws Exception {
        for (String kind : new String[] {"vanilla", "spigot", "paper"}) {
            Path d = root.resolve("migrate-" + kind);
            Files.createDirectories(d);
            String rconPw = kind + "RconPw123";
            Files.writeString(d.resolve("server.properties"), "motd=Migrated " + kind + "\nwhite-list=true\nmax-players=7\nenable-rcon=true\nrcon.password=" + rconPw
                + "\nrcon.port=25613\nserver-port=" + port + "\nonline-mode=false\nallow-nether=false\n");
            Files.writeString(d.resolve("whitelist.json"), "[{\"uuid\":\"" + uuidOf("Alice") + "\",\"name\":\"Alice\"}]");
            Files.writeString(d.resolve("ops.json"), "[{\"uuid\":\"" + uuidOf("Opsy") + "\",\"name\":\"Opsy\",\"level\":4,\"bypassesPlayerLimit\":false}]");
            Files.writeString(d.resolve("banned-players.json"), "[{\"uuid\":\"" + uuidOf("Badguy") + "\",\"name\":\"Badguy\",\"created\":\"2020-01-01 00:00:00 +0000\",\"source\":\"test\",\"expires\":\"forever\",\"reason\":\"bench\"}]");
            Files.writeString(d.resolve("eula.txt"), "eula=true\n");
            if (!kind.equals("vanilla")) {
                Files.writeString(d.resolve("bukkit.yml"), "settings:\n  allow-end: false\n  connection-throttle: 1234\n");
                Files.writeString(d.resolve("spigot.yml"), "settings:\n  save-user-cache-on-stop-only: true\n");
            }
            String velocity = "VelocitySecret" + kind;
            if (kind.equals("paper")) {
                Files.createDirectories(d.resolve("config"));
                Files.writeString(d.resolve("config/paper-global.yml"), "proxies:\n  velocity:\n    enabled: false\n    secret: '" + velocity + "'\n");
                Files.writeString(d.resolve("config/paper-world-defaults.yml"), "entities:\n  spawning:\n    spawn-limits:\n      monster: 33\n");
            }
            ServerProc s;
            try {
                s = ServerProc.startReady(jar, d, 240_000);
            } catch (Exception e) {
                for (String id : new String[] {"6a", "6b", "6c", "6d", "6e", "6f", "6g", "6h"}) {
                    Scn.record(id + "-" + kind, "Migration from " + kind, "FAIL", "the server did not start from the migrated folder: " + e.getMessage().lines().limit(6).reduce("", (p, q) -> p + q + " | "));
                }
                continue;
            }
            String log = s.joined();
            String ban = s.cmdOut("banlist players", 1200);
            String wl = s.cmdOut("whitelist list", 1200);
            String motd = Net.motd(Net.status("127.0.0.1", port));
            boolean nether = dimensionWorks(s, "minecraft:the_nether");
            boolean end = dimensionWorks(s, "minecraft:the_end");
            boolean overworld = dimensionWorks(s, "minecraft:overworld");
            String rc = Net.rcon("127.0.0.1", 25613, rconPw, "list");
            String json = Net.status("127.0.0.1", port);
            s.stop();
            Path backup = null;
            try (Stream<Path> w = Files.walk(d.resolve("config"), 3)) {
                backup = w.filter(p -> p.getFileName().toString().equals("import-report.txt")).findFirst().orElse(null);
            }
            final Path report = backup;
            String sfx = "-" + kind;
            run("6a" + sfx, "Migration (" + kind + "): the log announces the import", () -> {
                need(log.contains("Found the configuration of an older server; importing it into config/"), "no 'Found the configuration of an older server' line in the log");
                return "announced";
            });
            run("6b" + sfx, "Migration (" + kind + "): originals and import-report.txt are in config/legacy-backup", () -> {
                need(report != null, "no import-report.txt under config/legacy-backup");
                need(report.toString().contains("legacy-backup"), "report is not inside legacy-backup: " + report);
                need(Files.exists(report.getParent().resolve("whitelist.json")) || Files.walk(report.getParent()).anyMatch(p -> p.getFileName().toString().equals("whitelist.json")), "the original whitelist.json is not in the backup");
                return "report at " + d.relativize(report);
            });
            run("6c" + sfx, "Migration (" + kind + "): the old settings are in effect", () -> {
                need(("Migrated " + kind).equals(motd), "MOTD is '" + motd + "', expected 'Migrated " + kind + "'");
                need(json != null && json.contains("\"max\":7"), "max players did not become 7: " + json);
                if (!kind.equals("vanilla")) {
                    need(anyConfigContains(d, "1234"), "connection-throttle 1234 from bukkit.yml was not imported anywhere under config/");
                }
                return "motd, max-players" + (kind.equals("vanilla") ? "" : ", connection-throttle") + " imported";
            });
            run("6d" + sfx, "Migration (" + kind + "): whitelist, op and ban survive", () -> {
                need(wl.contains("Alice"), "Alice is not whitelisted: " + wl.strip());
                need(ban.contains("Badguy"), "Badguy is not banned: " + ban.strip());
                need(anyConfigContains(d, "Opsy"), "the op Opsy is in no file under config/");
                return "whitelist, ban list and op present";
            });
            run("6e" + sfx, "Migration (" + kind + "): secrets go to the secret files, not the TOML", () -> {
                need(rconPw.equals(secretOf(d.resolve("config/rcon.secret"))), "config/rcon.secret does not hold the old RCON password");
                need(rc != null && !rc.equals("AUTH-FAILED"), "RCON on 25613 does not accept the old password: " + rc);
                if (kind.equals("paper")) {
                    need(velocity.equals(secretOf(d.resolve("config/forwarding.secret"))), "config/forwarding.secret does not hold the Velocity secret");
                }
                need(!scanForSecrets(d, rconPw, kind.equals("paper") ? velocity : null), "a secret appears in a TOML file or server.properties");
                return "rcon.secret" + (kind.equals("paper") ? " and forwarding.secret" : "") + " hold them; no TOML does";
            });
            run("6f" + sfx, "Migration (" + kind + "): eula.txt = true means no prompt", () -> {
                need(!log.contains("Press ENTER") && !log.contains("agree to the EULA"), "an EULA prompt or refusal appeared");
                return "started without a prompt";
            });
            run("6g" + sfx, "Migration (" + kind + "): dimensions the old server had off stay off", () -> {
                need(overworld, "control: the overworld did not load, so 'off' proves nothing (probe through 'execute in' is broken)");
                need(!nether, "the Nether loaded although allow-nether=false");
                if (!kind.equals("vanilla")) {
                    need(!end, "the End loaded although bukkit.yml had allow-end: false");
                }
                return "the overworld loaded; the Nether stayed off" + (kind.equals("vanilla") ? "" : ", and so did the End");
            });
            if (kind.equals("paper")) {
                Map<String, String> before = snapshot(d);
                ServerProc s2 = ServerProc.startReady(jar, d, 240_000);
                String log2 = s2.joined();
                s2.stop();
                Map<String, String> after = snapshot(d);
                run("6h", "Migration: a second start imports nothing and changes no file", () -> {
                    need(!log2.contains("Found the configuration of an older server"), "the second start imported again");
                    StringBuilder diff = new StringBuilder();
                    for (String k : after.keySet()) {
                        if (!after.get(k).equals(before.get(k))) {
                            diff.append(before.containsKey(k) ? "changed " : "new ").append(k).append("; ");
                        }
                    }
                    for (String k : before.keySet()) {
                        if (!after.containsKey(k)) {
                            diff.append("removed ").append(k).append("; ");
                        }
                    }
                    need(diff.isEmpty(), "files differ after the second start: " + diff);
                    return "no import line, " + after.size() + " config and root files unchanged";
                });
            }
        }
    }

    static boolean anyConfigContains(Path d, String needle) throws IOException {
        try (Stream<Path> w = Files.walk(d.resolve("config"))) {
            for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile).filter(p -> !p.toString().contains("legacy-backup"))::iterator) {
                String t = read(p);
                if (t != null && t.contains(needle)) {
                    return true;
                }
            }
        }
        Path rp = d.resolve("server.properties");
        return Files.exists(rp) && read(rp).contains(needle);
    }

    static Map<String, String> snapshot(Path d) throws Exception {
        Map<String, String> m = new TreeMap<>();
        try (Stream<Path> w = Files.walk(d.resolve("config"))) {
            for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                m.put(d.relativize(p).toString(), sha(p));
            }
        }
        try (Stream<Path> w = Files.list(d)) {
            for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".jar"))::iterator) {
                m.put(d.relativize(p).toString(), sha(p));
            }
        }
        return m;
    }

    static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    // ---- EULA ----

    void eula() throws Exception {
        Path d = root.resolve("eula");
        Files.createDirectories(d);
        ServerProc s = ServerProc.start(jar, d, false, "--port", String.valueOf(port));
        boolean exited = s.proc.waitFor(150, java.util.concurrent.TimeUnit.SECONDS);
        if (!exited) {
            s.kill();
        }
        String log = s.joined();
        String toml = read(d.resolve("config/server.toml"));
        run("4f", "EULA: eula = false with no console input stops with the instruction", () -> {
            need(exited, "the server was still running after 150 s with eula = false");
            need(!log.contains("Done ("), "the server finished starting without the EULA");
            need(log.contains("You need to agree to the EULA") && log.contains("config/server.toml"), "no 'You need to agree to the EULA ... config/server.toml' message; log tail:\n" + s.tail(6));
            need(toml != null && toml.contains("eula = false"), "config/server.toml does not say eula = false");
            return "stopped with the EULA instruction, eula = false kept";
        });
        String why = "needs a real terminal (a pseudo-console key press); stdin here is a pipe, where the server by design never prompts";
        notRun("4a", "EULA: the prompt appears on a first start in a terminal", why);
        notRun("4b", "EULA: ESC refuses and keeps eula = false", why);
        notRun("4c", "EULA: ENTER agrees and writes eula = true", why);
        notRun("4e", "EULA: eula-prompt-timeout-seconds stops the server after the wait", why);
    }
}
