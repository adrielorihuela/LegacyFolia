package io.github.adrielorihuela.legacyfolia.bots;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemAttributeModifiers;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;

/** A running server with up to three bots in a flat world, and the helpers every scenario shares. */
final class Ctx {
    static final String HOST = "127.0.0.1";

    final ServerProc srv;
    final int port;
    Bot a, b, c;
    /** Ground level (feet y) and arena origin, taken from where the first bot spawned. */
    double gy, ox, oz;

    Ctx(ServerProc srv, int port) {
        this.srv = srv;
        this.port = port;
    }

    Bot join(String name) throws Exception {
        Bot bot = new Bot(name, HOST, port);
        bot.connect();
        return bot;
    }

    /** Joins A, B and C and fixes the arena. The flat world makes the ground level the spawn height. */
    void joinThree() throws Exception {
        a = join("BotA");
        b = join("BotB");
        c = join("BotC");
        gy = Math.floor(a.y);
        ox = Math.floor(a.x) + 0.5;
        oz = Math.floor(a.z) + 0.5;
        srv.send("time set noon");
        srv.send("gamerule advance_time false");
        srv.send("gamerule spawn_mobs false");
        srv.send("gamerule do_mob_spawning false");
        srv.send("difficulty easy");
    }

    void quit() {
        for (Bot x : new Bot[] {a, b, c}) {
            if (x != null) {
                x.disconnect();
            }
        }
    }

    // ---- placing and resetting ----

    void tp(Bot bot, double x, double y, double z, float yaw, float pitch) throws InterruptedException {
        long before = bot.positionPackets;
        srv.send(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f %.1f", bot.name, x, y, z, yaw, pitch));
        bot.waitFor(p -> p.positionPackets > before && Math.abs(p.x - x) < 0.05 && Math.abs(p.z - z) < 0.05, 8000);
        Thread.sleep(150);
    }

    void tpIn(String dim, Bot bot, double x, double y, double z) throws InterruptedException {
        srv.send(String.format(Locale.ROOT, "execute in %s run tp %s %.3f %.3f %.3f", dim, bot.name, x, y, z));
        bot.waitFor(p -> p.dimension.equals(dim) && Math.abs(p.x - x) < 0.05 && Math.abs(p.z - z) < 0.05, 20000);
        Thread.sleep(300);
    }

    /** Survival, full health and food, no items or effects, back in the overworld arena. */
    void reset(Bot... bots) throws InterruptedException {
        for (Bot x : bots) {
            x.sprint(false);
            x.sneak(false);
            srv.send("gamemode survival " + x.name);
            srv.send("effect clear " + x.name);
            srv.send("clear " + x.name);
            srv.send("effect give " + x.name + " minecraft:instant_health 1 10 true");
            srv.send("effect give " + x.name + " minecraft:saturation 1 10 true");
        }
        Thread.sleep(400);
        for (Bot x : bots) {
            if (!x.dimension.equals("minecraft:overworld")) {
                tpIn("minecraft:overworld", x, ox, gy, oz);
            }
            x.waitFor(p -> p.health >= 20 && !p.dead, 4000);
            x.onGround = true;
        }
        Thread.sleep(1200);
    }

    /** Victim at the arena centre facing the attacker, attacker two blocks south of it facing back, C far away. */
    void pair(Bot attacker, Bot victim) throws InterruptedException {
        reset(a, b, c);
        tp(victim, ox, gy, oz, 0f, 0f);
        tp(attacker, ox, gy, oz + 2, 180f, 0f);
        for (Bot x : new Bot[] {a, b, c}) {
            if (x != attacker && x != victim) {
                tp(x, ox + 40, gy, oz + 40, 0f, 0f);
            }
        }
        Thread.sleep(300);
    }

    void hold(Bot bot, String item) throws InterruptedException {
        srv.send("clear " + bot.name);
        srv.send("give " + bot.name + " minecraft:" + item);
        Thread.sleep(200);
    }

    /** Puts armour on the way a client does: holds it and right-clicks. */
    void equip(Bot bot, String item) throws InterruptedException {
        hold(bot, item);
        bot.use(org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.MAIN_HAND);
        Thread.sleep(500);
    }

    /** One attack, then how much health the victim lost within half a second. */
    double hit(Bot attacker, Bot victim) throws InterruptedException {
        float before = victim.health;
        attacker.attack(victim.entityId);
        Thread.sleep(500);
        return before - victim.health;
    }

    /**
     * Like {@link #hit}, but reads the lowest health seen in half a second. 26.3's saturation regeneration heals 1 every
     * 10 ticks on a full stomach, which would otherwise eat a point of the damage before the reading.
     */
    double hitPeak(Bot attacker, Bot victim) throws InterruptedException {
        float before = victim.health;
        float low = before;
        attacker.attack(victim.entityId);
        for (int i = 0; i < 50; i++) {
            Thread.sleep(10);
            low = Math.min(low, victim.health);
        }
        return before - low;
    }

    /** Console command output for a quick look at errors. */
    String run(String cmd) throws InterruptedException {
        return srv.cmdOut(cmd, 700);
    }

    // ---- reading items ----

    /** Last stack the server put in a slot of the bot's own inventory since a moment. */
    ItemStack slotSince(Bot bot, int slot, long since) {
        ItemStack last = null;
        synchronized (bot.slotEvents) {
            for (Bot.SlotEv e : bot.slotEvents) {
                if (e.container() == 0 && e.slot() == slot && e.at() >= since && e.item() != null) {
                    last = e.item();
                }
            }
        }
        return last;
    }

    /** The attack damage modifier amount a stack carries on the wire, or null when it has no component. */
    static Double attackDamage(ItemStack s) {
        return modifier(s, AttributeType.Builtin.ATTACK_DAMAGE.getId());
    }

    static Double attackSpeed(ItemStack s) {
        return modifier(s, AttributeType.Builtin.ATTACK_SPEED.getId());
    }

    private static Double modifier(ItemStack s, int attribute) {
        if (s == null || s.getDataComponentsPatch() == null) {
            return null;
        }
        ItemAttributeModifiers m = s.getDataComponentsPatch().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (m == null) {
            return null;
        }
        for (ItemAttributeModifiers.Entry e : m.getModifiers()) {
            if (e.getAttribute() == attribute) {
                return e.getModifier().getAmount();
            }
        }
        return null;
    }

    static boolean hasModifiers(ItemStack s) {
        return s != null && s.getDataComponentsPatch() != null && s.getDataComponentsPatch().get(DataComponentTypes.ATTRIBUTE_MODIFIERS) != null;
    }

    /** Gives the item in the main hand and returns the stack the client was sent for the first hotbar slot. */
    ItemStack give(Bot bot, String item) throws InterruptedException {
        long t = Bot.now();
        hold(bot, item);
        bot.waitFor(p -> slotSince(p, 36, t) != null, 3000);
        return slotSince(bot, 36, t);
    }

    static String vec(Vector3d v) {
        return v == null ? "none" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.getX(), v.getY(), v.getZ());
    }

    static void need(boolean c, String m, Object... ignore) {
        Scn.need(c, m);
    }

    static String read(Path p) throws java.io.IOException {
        return Files.readString(p);
    }

    static List<String> names(Path dir) throws java.io.IOException {
        try (var s = Files.list(dir)) {
            return s.map(x -> x.getFileName().toString()).sorted().toList();
        }
    }
}
