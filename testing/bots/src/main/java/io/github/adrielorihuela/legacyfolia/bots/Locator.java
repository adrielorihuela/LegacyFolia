package io.github.adrielorihuela.legacyfolia.bots;

import static io.github.adrielorihuela.legacyfolia.bots.Scn.Fail;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.need;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.run;

import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.AzimuthWaypointData;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.TrackedWaypoint;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.WaypointOperation;

/** Scenario group 1 of docs/TESTING.md: the locator bar, read from the waypoint packets each bot receives. */
final class Locator {
    private Locator() {
    }

    static String kind(Bot observer, Bot target) {
        Bot.WpEvent e = observer.waypointOf(target.uuid);
        return e == null ? "none" : e.wp().type().name();
    }

    static boolean visible(Bot o, Bot t, long ms) throws InterruptedException {
        return o.waitFor(p -> p.waypointOf(t.uuid) != null, ms);
    }

    static boolean hidden(Bot o, Bot t, long ms) throws InterruptedException {
        return o.waitFor(p -> p.waypointOf(t.uuid) == null, ms);
    }

    static boolean azimuth(Bot o, Bot t, long ms) throws InterruptedException {
        return o.waitFor(p -> "AZIMUTH".equals(kind(p, t)), ms);
    }

    static double angle(Bot o, Bot t) {
        return ((AzimuthWaypointData) o.waypointOf(t.uuid).wp().data()).angle();
    }

    static double wrap(double a) {
        while (a > Math.PI) {
            a -= 2 * Math.PI;
        }
        while (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }

    static void all(Ctx x) throws Exception {
        Bot a = x.a;
        Bot b = x.b;
        String d = "waypoint packets, vanilla 26.3 rules (TESTING.md section 1)";

        run("1a", "Locator: two players a few blocks apart see each other", () -> {
            x.reset(a, b, x.c);
            x.tp(a, x.ox, x.gy, x.oz, 0, 0);
            x.tp(b, x.ox + 3, x.gy, x.oz, 0, 0);
            x.tp(x.c, x.ox + 60, x.gy, x.oz + 60, 0, 0);
            need(visible(a, b, 6000), "A never received a waypoint for B");
            need(visible(b, a, 6000), "B never received a waypoint for A");
            need(!kind(a, b).equals("AZIMUTH"), "close transmitter should not be direction-only, got " + kind(a, b));
            return "A sees B as " + kind(a, b) + ", B sees A as " + kind(b, a);
        });

        run("1b", "Locator: B 1000 blocks away stays on A's bar as a direction", () -> {
            x.tp(b, x.ox + 1000, x.gy, x.oz, 0, 0);
            need(azimuth(a, b, 20000), "A's waypoint for B did not become a direction (azimuth); it is " + kind(a, b));
            return "A sees B as AZIMUTH, angle " + Scn.fmt(angle(a, b)) + " rad (vanilla goes direction-only beyond 332 blocks)";
        });

        run("1c", "Locator: B circles A at 1000 blocks while A stands still; the angle follows", () -> {
            double ax = a.x, az = a.z;
            double[][] pts = {{1000, 0}, {0, 1000}, {-1000, 0}, {0, -1000}};
            double[] ang = new double[4];
            for (int i = 0; i < 4; i++) {
                double[] p = pts[i];
                long t0 = Bot.now();
                x.tp(b, x.ox + p[0], x.gy, x.oz + p[1], 0, 0);
                double prev = i == 0 ? Double.NaN : ang[i - 1];
                int idx = i;
                need(a.waitFor(q -> "AZIMUTH".equals(kind(q, b)) && (idx == 0 || Math.abs(wrap(angle(q, b) - prev)) > 0.5), 20000), "step " + i + ": A's bar did not follow B (still " + kind(a, b) + ")");
                ang[i] = angle(a, b);
            }
            for (int i = 1; i < 4; i++) {
                Scn.near("step " + i + " turn", wrap(ang[i] - ang[i - 1]), wrap(ang[1] - ang[0]), 0.15);
            }
            Scn.near("quarter turn", Math.abs(wrap(ang[1] - ang[0])), Math.PI / 2, 0.15);
            need(Math.abs(a.x - ax) < 0.01 && Math.abs(a.z - az) < 0.01, "A moved during the test");
            return "angles " + Scn.fmt(ang[0]) + ", " + Scn.fmt(ang[1]) + ", " + Scn.fmt(ang[2]) + ", " + Scn.fmt(ang[3]) + "; each quarter turn is 90 degrees";
        });

        run("1d", "Locator: B crosses 332 blocks; the dot stays, no duplicate, none left frozen", () -> {
            long t0 = Bot.now();
            x.tp(b, x.ox + 360, x.gy, x.oz, 0, 0);
            need(azimuth(a, b, 20000), "at 360 blocks the waypoint should be a direction, it is " + kind(a, b));
            x.tp(b, x.ox + 300, x.gy, x.oz, 0, 0);
            need(a.waitFor(p -> p.waypointOf(b.uuid) != null && !kind(p, b).equals("AZIMUTH"), 20000), "at 300 blocks the waypoint should be positional, it is " + kind(a, b));
            int tracks = 0;
            int live = 0;
            int maxLive = 0;
            for (Bot.WpEvent e : a.waypointEventsSince(t0, b.uuid)) {
                if (e.op() == WaypointOperation.TRACK) {
                    live++;
                    tracks++;
                } else if (e.op() == WaypointOperation.UNTRACK) {
                    live--;
                }
                maxLive = Math.max(maxLive, live);
            }
            need(maxLive <= 1, "B was tracked twice at once (TRACK without UNTRACK)");
            need(a.waypointOf(b.uuid) != null, "no dot at the end");
            return "360 blocks: AZIMUTH, 300 blocks: " + kind(a, b) + "; " + tracks + " TRACK packet(s), never more than one live";
        });

        run("1e", "Locator: B crouches and disappears, stands and returns", () -> {
            x.tp(b, x.ox + 3, x.gy, x.oz, 0, 0);
            need(visible(a, b, 15000), "B not visible before crouching");
            b.sneak(true);
            need(hidden(a, b, 5000), "B stayed on A's bar while crouching");
            b.sneak(false);
            need(visible(a, b, 5000), "B did not come back after standing");
            return "hidden while crouching, back after";
        });

        run("1f", "Locator: pumpkin and Invisibility hide B", () -> {
            x.equip(b, "carved_pumpkin");
            String put = "(equipped by right-click)";
            need(hidden(a, b, 6000), "B stayed visible with a carved pumpkin on; server said: " + put.trim());
            x.srv.send("clear BotB");
            need(visible(a, b, 5000), "B did not return after the pumpkin came off");
            x.srv.send("effect give BotB minecraft:invisibility 60 0 true");
            need(hidden(a, b, 5000), "B stayed visible while invisible");
            x.srv.send("effect clear BotB");
            need(visible(a, b, 5000), "B did not return after Invisibility ended");
            return "pumpkin and invisibility hide and restore (mob head not tried)";
        });

        run("1g", "Locator: B in the Nether vanishes from the overworld bar", () -> {
            x.tpIn("minecraft:the_nether", b, 8, 131, 8);
            need(hidden(a, b, 6000), "A still sees B who is in the Nether (" + kind(a, b) + ")");
            need(hidden(b, a, 6000), "B in the Nether still sees A in the overworld (" + kind(b, a) + ")");
            return "A and B in different dimensions see nothing of each other";
        });

        run("1h", "Locator: they see each other again sharing the Nether and the End", () -> {
            x.tpIn("minecraft:the_nether", a, 8, 131, 8);
            need(visible(a, b, 15000) && visible(b, a, 15000), "A and B in the Nether do not see each other");
            x.tpIn("minecraft:the_end", a, 100.5, 50, 0.5);
            x.tpIn("minecraft:the_end", b, 103.5, 50, 0.5);
            need(visible(a, b, 15000) && visible(b, a, 15000), "A and B in the End do not see each other");
            x.tpIn("minecraft:overworld", a, x.ox, x.gy, x.oz);
            x.tpIn("minecraft:overworld", b, x.ox + 3, x.gy, x.oz);
            need(visible(a, b, 15000) && visible(b, a, 15000), "A and B back in the overworld do not see each other");
            return "visible in the Nether, the End and back in the overworld";
        });

        run("1i", "Locator: /gamerule locator_bar false then true, nobody moves", () -> {
            x.srv.send("gamerule locator_bar false");
            need(hidden(a, b, 5000) && hidden(b, a, 5000), "dots stayed after locator_bar false");
            x.srv.send("gamerule locator_bar true");
            need(visible(a, b, 5000) && visible(b, a, 5000), "dots did not return after locator_bar true without movement");
            return "dots go and return at once";
        });

        Scn.notRun("1j-team", "Locator: a team colour changes B's dot", "the /team command does not exist on this server (Folia removes it), so no team can be created from the console; the colour itself is drawn by the client from the team packet");
        run("1j", "Locator: /waypoint modify changes B's dot", () -> {
            String out = x.run("waypoint modify BotB color hex 0000FF");
            need(a.waitFor(p -> {
                var w = p.waypointOf(b.uuid);
                return w != null && w.wp().icon().color().isPresent() && w.wp().icon().color().get() == 0x0000FF;
            }, 5000), "colour did not become 0000FF; server said: " + out.trim());
            return "waypoint modify -> 0x0000FF reached A in the waypoint packet";
        });

        run("1k", "Locator: bar keeps working after B dies and after B logs out and in", () -> {
            x.srv.send("kill BotB");
            b.waitFor(p -> p.dead, 3000);
            Thread.sleep(1500);
            need(b.waitFor(p -> !p.dead && p.health > 0, 5000), "B did not respawn");
            x.tp(b, x.ox + 3, x.gy, x.oz, 0, 0);
            need(visible(a, b, 10000) && visible(b, a, 10000), "dots missing after B respawned");
            b.disconnect();
            Thread.sleep(1500);
            Bot nb = x.join("BotB");
            x.b = nb;
            x.tp(nb, x.ox + 3, x.gy, x.oz, 0, 0);
            need(visible(a, nb, 10000) && visible(nb, a, 10000), "dots missing after B logged back in");
            return "working after respawn and after relogging";
        });
    }
}
