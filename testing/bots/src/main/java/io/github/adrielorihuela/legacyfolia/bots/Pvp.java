package io.github.adrielorihuela.legacyfolia.bots;

import static io.github.adrielorihuela.legacyfolia.bots.Scn.Fail;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.NotRun;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.fmt;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.near;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.need;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.run;

import java.util.List;

import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/**
 * Scenario group 2 of docs/TESTING.md. Expected numbers are 1.8.8's, as listed in docs/PVP-1.8.md (section named in
 * each detail). Health is read from the victim's own health packets, motion from its own velocity packets.
 */
final class Pvp {
    private Pvp() {
    }

    static void all(Ctx x) throws Exception {
        Bot a = x.a;
        Bot b = x.b;

        run("2a", "PvP: full hit with no cooldown, no sweep", () -> {
            x.pair(a, b);
            x.tp(x.c, x.ox + 0.9, x.gy, x.oz, 0, 0);
            x.hold(a, "diamond_sword");
            double dmg = x.hit(a, b);
            String s = near("damage right after taking the sword", dmg, 8, 0.01);
            need(x.c.health >= 20, "the bystander next to the victim took " + (20 - x.c.health) + " from a sweep");
            return s + " (PVP-1.8.md 'Attack cooldown', 'Weapon damage': diamond sword 8), bystander untouched (no sweep)";
        });

        run("2b", "PvP: 1.8 knockback on the ground, in the air, and with sprint", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            long t = Bot.now();
            x.hit(a, b);
            Vector3d v = b.lastOwnMotionSince(t);
            need(v != null, "the victim was sent no velocity");
            StringBuilder s = new StringBuilder("ground: " + Ctx.vec(v));
            // 1.8: motY = motY / 2 + 0.4, and a player standing on the ground still carries gravity's -0.0784, so 0.361.
            near("ground vertical", v.getY(), 0.4 - 0.0784 / 2, 0.02);
            near("ground horizontal", Bot.horizontal(v), 0.4, 0.04);
            need(v.getZ() < -0.3 && Math.abs(v.getX()) < 0.05, "push should point away from the attacker (-z), got " + Ctx.vec(v));

            x.pair(a, b);
            x.hold(a, "diamond_sword");
            x.tp(b, x.ox, x.gy + 4, x.oz, 0, 0);
            b.onGround = false;
            Thread.sleep(250);
            t = Bot.now();
            x.hit(a, b);
            v = b.lastOwnMotionSince(t);
            need(v != null, "airborne victim was sent no velocity");
            s.append("; airborne: ").append(Ctx.vec(v));
            need(v.getY() > -0.2, "an airborne victim must still be lifted (1.8: y = motY/2 + 0.4, so a victim falling at -0.65 gets about 0.07; modern leaves the fall speed alone); vertical was " + fmt(v.getY()));
            b.onGround = true;

            x.pair(a, b);
            x.hold(a, "diamond_sword");
            a.sprint(true);
            Thread.sleep(300);
            t = Bot.now();
            x.hit(a, b);
            v = b.lastOwnMotionSince(t);
            need(v != null, "sprint victim was sent no velocity");
            s.append("; sprint: ").append(Ctx.vec(v));
            near("sprint horizontal (0.4 base + 0.5 sprint push)", Bot.horizontal(v), 0.9, 0.12);
            a.sprint(false);
            return s + " (PVP-1.8.md 'Knockback', 'Sprint knockback')";
        });

        run("2c", "PvP: critical while sprinting and falling is 1.5x", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            double plain = x.hit(a, b);
            near("plain hit", plain, 8, 0.01);

            x.pair(a, b);
            x.hold(a, "diamond_sword");
            a.sprint(true);
            x.tp(a, x.ox, x.gy + 3, x.oz + 2, 180, 0);
            a.onGround = false;
            for (double yy = x.gy + 2.5; yy >= x.gy + 1; yy -= 0.5) {
                a.moveRaw(x.ox, yy, x.oz + 2, false);
            }
            double crit = x.hit(a, b);
            a.sprint(false);
            return "plain " + fmt(plain) + ", " + near("sprinting fall hit", crit, 12, 0.05) + " (PVP-1.8.md 'Sprint-jump criticals': 8 * 1.5, sprinting allowed)";
        });

        run("2d", "PvP: reach is 6 blocks with a clear line, 3 through tall grass", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            x.tp(a, x.ox, x.gy, x.oz + 5, 180, 0);
            double clear = x.hit(a, b);
            near("hit from 5 blocks, clear line", clear, 8, 0.01);

            x.pair(a, b);
            x.hold(a, "diamond_sword");
            int bx = (int) Math.floor(x.ox), bz = (int) Math.floor(x.oz) + 3, by = (int) x.gy;
            x.srv.send("setblock " + bx + " " + by + " " + bz + " minecraft:tall_grass[half=lower]");
            x.srv.send("setblock " + bx + " " + (by + 1) + " " + bz + " minecraft:tall_grass[half=upper]");
            Thread.sleep(300);
            x.tp(a, x.ox, x.gy, x.oz + 5, 180, 0);
            double blocked = x.hit(a, b);
            need(Math.abs(blocked) < 0.01, "a hit from 5 blocks through tall grass landed for " + fmt(blocked) + " (grass blocks the line, range 3 without it)");
            x.tp(a, x.ox, x.gy, x.oz + 2.5, 180, 0);
            x.srv.send("setblock " + bx + " " + by + " " + (bz - 1) + " minecraft:tall_grass[half=lower]");
            Thread.sleep(300);
            double near = x.hit(a, b);
            x.srv.send("fill " + (bx - 1) + " " + by + " " + (bz - 2) + " " + (bx + 1) + " " + (by + 1) + " " + (bz + 1) + " minecraft:air");
            return "5 blocks clear: " + fmt(clear) + "; 5 blocks through grass: " + fmt(blocked) + "; 2.5 blocks through grass: " + fmt(near) + " (PVP-1.8.md 'Reach')";
        });

        run("2e", "PvP: sword blocking turns 8 into 4.5", () -> {
            x.pair(a, b);
            x.hold(b, "iron_sword");
            x.hold(a, "diamond_sword");
            b.use(Hand.MAIN_HAND);
            Thread.sleep(300);
            double dmg = x.hit(a, b);
            return near("damage into a raised sword", dmg, 4.5, 0.05) + " ((1 + 8) * 0.5, PVP-1.8.md 'Sword blocking')";
        });

        run("2f", "PvP: shields, the off-hand and totems do nothing", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            x.srv.send("clear BotB"); x.srv.send("give BotB minecraft:shield");
            Thread.sleep(200);
            b.use(Hand.MAIN_HAND);
            Thread.sleep(500);
            double shielded = x.hit(a, b);
            need(Math.abs(shielded - 8) < 0.01, "a raised shield changed the damage to " + fmt(shielded) + " (should still be 8)");

            x.hold(b, "diamond_sword");
            b.swapHands();
            Thread.sleep(400);
            Boolean off = x.srv.test("if items entity BotB weapon.offhand *");
            need(off != null, "the server gave no verdict on the off-hand");
            need(!off, "the swap key put an item in the off-hand");

            x.pair(a, b);
            x.hold(b, "totem_of_undying");
            x.srv.send("damage BotB 100");
            boolean died = b.waitFor(p -> p.health <= 0 || p.dead, 3000);
            need(died, "a Totem of Undying in hand saved the player (health " + b.health + ")");
            Thread.sleep(1500);
            return "shield blocked nothing (damage 8), the off-hand stayed empty, the totem saved nobody";
        });

        run("2g", "PvP: snowball and fishing rod flash the player and push them for 0 damage", () -> {
            x.pair(a, b);
            x.srv.send("clear BotA"); x.srv.send("give BotA minecraft:snowball 8");
            Thread.sleep(200);
            a.lookAt(x.ox, x.gy + 1.0, x.oz);
            long t = Bot.now();
            a.use(Hand.MAIN_HAND);
            Thread.sleep(900);
            String s = hurtAndPush(b, t, "snowball");
            need(b.health >= 20, "the snowball did " + (20 - b.health) + " damage");

            x.pair(a, b);
            x.hold(a, "fishing_rod");
            a.lookAt(x.ox, x.gy + 1.0, x.oz);
            t = Bot.now();
            a.use(Hand.MAIN_HAND);
            Thread.sleep(1200);
            String s2 = hurtAndPush(b, t, "fishing rod hook");
            need(b.health >= 20, "the hook did " + (20 - b.health) + " damage");
            return s + "; " + s2 + " (PVP-1.8.md '0 damage hits', 'Projectiles and the fishing rod')";
        });

        run("2h", "PvP: ender pearls one after another have no cooldown", () -> {
            x.pair(a, b);
            x.srv.send("clear BotA"); x.srv.send("give BotA minecraft:ender_pearl 8");
            Thread.sleep(200);
            a.yaw = 180;
            a.pitch = -60;
            Thread.sleep(150);
            long t = Bot.now();
            a.use(Hand.MAIN_HAND);
            Thread.sleep(150);
            a.use(Hand.MAIN_HAND);
            Thread.sleep(700);
            long pearls;
            synchronized (a.adds) {
                pearls = a.adds.stream().filter(e -> e.at() >= t && e.type() == EntityType.ENDER_PEARL).count();
            }
            need(pearls >= 2, "only " + pearls + " pearl(s) were thrown in two quick right-clicks (modern 1.5 s cooldown)");
            return pearls + " pearls thrown 150 ms apart (PVP-1.8.md 'Projectiles')";
        });
        Scn.notRun("2h-arrow", "PvP: arrows do not take on the shooter's motion",
            "the shooter's motion is client-owned; a packet bot cannot move at a controlled speed while shooting, so the effect is not observable");

        run("2i", "PvP: TNT damage follows 1.8", () -> {
            x.pair(a, b);
            x.tp(a, x.ox + 40, x.gy, x.oz + 40, 0, 0);
            x.srv.send("summon tnt " + fmt(x.ox).replace(',', '.') + " " + fmt(x.gy + 1).replace(',', '.') + " " + fmt(x.oz + 6).replace(',', '.') + " {fuse:50,NoGravity:1b}");
            Thread.sleep(4500);
            double dmg = 20 - b.health;
            need(Math.abs(dmg - 10) < 0.15, "TNT 6 blocks away and 1 up should do 10 (1.8 formula (int)((p^2+p)/2*8*8+1), p about 0.24, clear line), did " + fmt(dmg) + "; modern 26.3 would give about 9.3");
            return "damage " + fmt(dmg) + " at 6 blocks (PVP-1.8.md 'Explosions'); push direction and Blast Protection not checked";
        });

        run("2j", "PvP: Strength I, Weakness I and Instant Damage", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            x.srv.send("effect give BotA minecraft:strength 120 0 true");
            Thread.sleep(300);
            String s = near("Strength I diamond sword", x.hit(a, b), 18.4, 0.1);

            x.pair(a, b);
            x.srv.send("effect give BotA minecraft:weakness 120 0 true");
            Thread.sleep(300);
            String s2 = near("fist under Weakness I", x.hit(a, b), 0.5, 0.05);

            x.pair(a, b);
            float before = b.health;
            x.srv.send("effect give BotB minecraft:instant_damage 1 0 true");
            Thread.sleep(600);
            String s3 = near("Instant Damage I", before - b.health, 6, 0.05);
            return s + ", " + s2 + ", " + s3 + " (PVP-1.8.md 'Strength and Weakness'); splash falloff not tried";
        });

        run("2k", "PvP: enchanted golden apple gives the 1.8 effects", () -> {
            x.pair(a, b);
            x.srv.send("clear BotA"); x.srv.send("give BotA minecraft:enchanted_golden_apple");
            Thread.sleep(250);
            long t = Bot.now();
            a.use(Hand.MAIN_HAND);
            Thread.sleep(2300);
            String out = x.run("recipe give BotA legacyfolia:enchanted_golden_apple");
            StringBuilder s = new StringBuilder();
            String[][] want = {{"ABSORPTION", "0", "2400"}, {"REGENERATION", "4", "600"}, {"RESISTANCE", "0", "6000"}, {"FIRE_RESISTANCE", "0", "6000"}};
            for (String[] w : want) {
                Bot.EffectEv e;
                synchronized (a.effects) {
                    e = a.effects.stream().filter(q -> q.at() >= t && q.entity() == a.entityId && q.effect().equals(w[0])).findFirst().orElse(null);
                }
                need(e != null, w[0] + " was not applied by the apple");
                need(e.amp() == Integer.parseInt(w[1]) && Math.abs(e.duration() - Integer.parseInt(w[2])) <= 20,
                    w[0] + ": expected amplifier " + w[1] + " for " + w[2] + " ticks, got " + e.amp() + " for " + e.duration());
                s.append(w[0]).append(' ').append(e.amp() + 1).append(" ").append(e.duration()).append("t; ");
            }
            need(!out.toLowerCase().contains("unknown") && !out.toLowerCase().contains("no recipe"), "the crafting recipe legacyfolia:enchanted_golden_apple is missing: " + out.trim());
            return s + "recipe exists (PVP-1.8.md 'Potions, the notch apple and food')";
        });

        run("2l", "PvP: a 3.5 block fall hurts for 1", () -> {
            x.pair(a, b);
            x.tp(a, x.ox + 10, x.gy + 3.5, x.oz, 0, 0);
            a.onGround = false;
            for (double yy = x.gy + 3.0; yy > x.gy; yy -= 0.5) {
                a.moveRaw(x.ox + 10, yy, x.oz, false);
            }
            a.moveRaw(x.ox + 10, x.gy, x.oz, true);
            Thread.sleep(600);
            return near("fall damage", 20 - a.health, 1, 0.01) + " (PVP-1.8.md 'Falls, fire and spawning')";
        });

        run("2m", "PvP: three seconds of protection after joining, none after a respawn", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            Bot d = x.join("BotD");
            try {
                x.tp(a, d.x, d.y, d.z + 2, 180, 0);
                double early = x.hit(a, d);
                need(Math.abs(early) < 0.01, "a hit within moments of joining did " + fmt(early));
                long pp = d.positionPackets;
                x.srv.send("kill BotD");
                d.waitFor(p -> p.dead, 3000);
                need(d.waitFor(p -> !p.dead && p.health > 0 && p.positionPackets > pp, 8000), "BotD did not respawn");
                Thread.sleep(500);
                x.tp(a, d.x, d.y, d.z + 2, 180, 0);
                double after = x.hit(a, d);
                String s = near("hit right after a respawn", after, 8, 0.01);
                return "hit while new: " + fmt(early) + ", " + s + " (PVP-1.8.md 'Falls, fire and spawning')";
            } finally {
                d.disconnect();
            }
        });

        run("2w", "PvP: a stronger hit inside the 20 tick window lands for the difference", () -> {
            x.pair(a, b);
            x.hold(a, "iron_sword");
            float before = b.health;
            a.attack(b.entityId);
            Thread.sleep(120);
            x.srv.send("clear BotA"); x.srv.send("give BotA minecraft:diamond_sword");
            Thread.sleep(150);
            a.attack(b.entityId);
            Thread.sleep(400);
            double stronger = before - b.health;
            near("iron 7 then diamond 8", stronger, 8, 0.01);
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            before = b.health;
            a.attack(b.entityId);
            Thread.sleep(150);
            a.attack(b.entityId);
            Thread.sleep(400);
            double equal = before - b.health;
            near("two equal hits in the window", equal, 8, 0.01);
            return "7 then 8 -> " + fmt(stronger) + " total; 8 then 8 -> " + fmt(equal) + " total (PVP-1.8.md 'The invulnerability window')";
        });

        run("2x", "PvP: armour is (25 - points) / 25 whatever the hit", () -> {
            x.pair(a, b);
            x.hold(a, "diamond_sword");
            x.equip(b, "iron_chestplate");
            Thread.sleep(300);
            double dmg = x.hit(a, b);
            return near("8 damage into an iron chestplate (6 points)", dmg, 8 * (25 - 6) / 25.0, 0.02) + " (PVP-1.8.md 'Armour and protection')";
        });
    }

    private static String hurtAndPush(Bot victim, long since, String what) {
        boolean hurt;
        synchronized (victim.hurts) {
            hurt = victim.hurts.stream().anyMatch(h -> h.at() >= since && h.value().startsWith(victim.entityId + ":"));
        }
        Vector3d v = victim.lastOwnMotionSince(since);
        need(hurt, "the " + what + " did not make the victim flash (no damage/hurt packet)");
        need(v != null, "the " + what + " did not push the victim");
        need(v.getZ() < -0.1, "the " + what + " should push away from the thrower (-z), got " + Ctx.vec(v));
        return what + " flashed and pushed " + Ctx.vec(v);
    }
}
