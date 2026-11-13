package io.github.adrielorihuela.legacyfolia.bots;

import static io.github.adrielorihuela.legacyfolia.bots.Scn.fmt;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.near;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.need;
import static io.github.adrielorihuela.legacyfolia.bots.Scn.run;

import java.nio.file.Files;
import java.nio.file.Path;

import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/**
 * Scenario group 3: the attack damage a client is shown. The wire carries the modifier; the client adds the player's
 * own 1.0, so 1.8's diamond sword (8) is a modifier of 7 (PVP-1.8.md 'Weapon tooltips', 'Weapon damage').
 */
final class Tooltips {
    private Tooltips() {
    }

    static void all(Ctx x) throws Exception {
        Bot a = x.a;

        run("3a", "Tooltips: the item sent to the client carries the 1.8 attack damage", () -> {
            x.reset(a, x.b, x.c);
            String[][] items = {{"diamond_sword", "7"}, {"diamond_axe", "6"}, {"iron_pickaxe", "4"}, {"wooden_shovel", "1"}};
            StringBuilder s = new StringBuilder();
            for (String[] it : items) {
                ItemStack st = x.give(a, it[0]);
                need(st != null, "no slot update arrived for " + it[0]);
                Double d = Ctx.attackDamage(st);
                need(d != null, it[0] + " arrived without an attack damage modifier, so the client shows 26.3's default (the item patch has no attribute_modifiers)");
                s.append(near(it[0], d, Double.parseDouble(it[1]), 0.001)).append(" (shows ").append(fmt(d + 1)).append("); ");
            }
            ItemStack sword = x.give(a, "diamond_sword");
            Double speed = Ctx.attackSpeed(sword);
            need(speed != null, "the attack speed line is missing from the rewritten component");
            s.append(near("sword attack speed unchanged", speed, -2.4, 0.001));
            return s.toString();
        });

        run("3b", "Tooltips: the same figure on another player, a dropped item and an item frame", () -> {
            x.pair(a, x.b);
            x.hold(x.b, "diamond_sword");
            Thread.sleep(500);
            var held = a.equipment.get(x.b.entityId);
            ItemStack inHand = held == null ? null : held.get(EquipmentSlot.MAIN_HAND);
            need(inHand != null, "A never saw B's held item");
            Double heldDmg = Ctx.attackDamage(inHand);
            need(heldDmg != null, "B's sword as seen by A has no modifier");
            String s = near("held by another player", heldDmg, 7, 0.001);

            a.entityItems.clear();
            x.srv.send("summon item " + fmt(x.ox) + " " + fmt(x.gy) + " " + fmt(x.oz + 1) + " {Item:{id:\"minecraft:diamond_sword\",count:1},PickupDelay:32767}");
            need(a.waitFor(p -> !p.entityItems.isEmpty(), 4000), "A saw no dropped item");
            Double dropped = Ctx.attackDamage(a.entityItems.values().iterator().next());
            need(dropped != null, "a dropped diamond sword has no modifier on the wire");
            s += "; " + near("dropped on the ground", dropped, 7, 0.001);

            a.entityItems.clear();
            x.srv.send("summon item_frame " + (int) Math.floor(x.ox + 1) + " " + (int) x.gy + " " + (int) Math.floor(x.oz + 1) + " {Facing:1b,Item:{id:\"minecraft:diamond_sword\",count:1}}");
            need(a.waitFor(p -> !p.entityItems.isEmpty(), 4000), "A saw no item frame item");
            Double framed = Ctx.attackDamage(a.entityItems.values().iterator().next());
            need(framed != null, "an item frame's diamond sword has no modifier on the wire");
            s += "; " + near("in an item frame", framed, 7, 0.001);
            x.srv.send("kill @e[type=item_frame]");
            x.srv.send("kill @e[type=item]");
            return s + " (chest and villager trade not driven)";
        });

        run("3c", "Tooltips: creative round trip stores the default item, no resync", () -> {
            x.reset(a, x.b, x.c);
            x.srv.send("gamemode creative BotA");
            Thread.sleep(500);
            ItemStack shown = x.give(a, "diamond_sword");
            need(Ctx.hasModifiers(shown), "the creative player was not sent the 1.8 component");
            // The client sends back the stack it was shown, in hotbar slot 2 (container slot 37).
            long t = Bot.now();
            a.creativeSlot(37, shown);
            Thread.sleep(800);
            Boolean stored = x.srv.test("if items entity BotA container.1 minecraft:diamond_sword");
            need(Boolean.TRUE.equals(stored), "the sword sent back was not stored at all");
            String dflt = "[{type:\"minecraft:attack_damage\",id:\"minecraft:base_attack_damage\",amount:6.0d,operation:\"add_value\",slot:\"mainhand\"},"
                + "{type:\"minecraft:attack_speed\",id:\"minecraft:base_attack_speed\",amount:-2.4d,operation:\"add_value\",slot:\"mainhand\"}]";
            Boolean isDefault = x.srv.test("if items entity BotA container.1 minecraft:diamond_sword[minecraft:attribute_modifiers=" + dflt + "]");
            need(isDefault != null, "the server could not evaluate the component test");
            need(isDefault, "the stored sword's attribute modifiers are not 26.3's default (attack damage 6.0): the 1.8 figure was stored");
            boolean resync = false;
            synchronized (a.slotEvents) {
                for (Bot.SlotEv e : a.slotEvents) {
                    if (e.at() >= t && e.container() == 0 && e.slot() == 37) {
                        resync = true;
                    }
                }
            }
            need(!resync, "the server resent slot 37 after the creative client sent its stack (resync flicker)");
            // A stack the client built itself (no components) is sent back once with the 1.8 figure.
            long t2 = Bot.now();
            a.creativeSlot(38, new ItemStack(shown.getId(), 1));
            need(a.waitFor(p -> x.slotSince(p, 38, t2) != null, 3000), "a bare creative sword was not sent back with the 1.8 figure");
            Double d = Ctx.attackDamage(x.slotSince(a, 38, t2));
            need(d != null && Math.abs(d - 7) < 0.001, "the bare creative sword came back with modifier " + d);
            x.srv.send("gamemode survival BotA");
            return "stored item has no attribute_modifiers, no resync, bare creative sword returned with +7";
        });
    }

    /** 3e: legacy-weapon-damage = false and a reload refresh the open inventory. Runs last; it edits combat.toml. */
    static void reload(Ctx x, Path dir) throws Exception {
        Bot a = x.a;
        run("3e", "Tooltips: legacy-weapon-damage = false plus /paper reload refreshes the tooltips", () -> {
            x.reset(a, x.b, x.c);
            ItemStack before = x.give(a, "diamond_sword");
            need(Ctx.hasModifiers(before), "precondition: sword should carry the 1.8 component");
            Path props = dir.resolve("config/server.properties");
            Path toml = dir.resolve("config/combat.toml");
            String s = Files.readString(toml);
            need(s.contains("legacy-weapon-damage = true"), "combat.toml has no 'legacy-weapon-damage = true' line to edit");
            Files.writeString(toml, s.replace("legacy-weapon-damage = true", "legacy-weapon-damage = false"));
            if (Files.exists(props)) {
                String p = Files.readString(props);
                Files.writeString(props, p.replaceAll("(?m)^combat\\.legacy-weapon-damage=.*\\R?", ""));
            }
            long t = Bot.now();
            String out = x.run("paper reload");
            boolean refreshed = a.waitFor(p -> {
                ItemStack st = x.slotSince(p, 36, t);
                return st != null && !Ctx.hasModifiers(st);
            }, 8000);
            need(refreshed, "no refreshed sword reached the client after the reload; server said: " + out.trim().replace("\n", " | "));
            return "after the reload the sword came back without the 1.8 component (26.3 figures)";
        });
    }
}
