# 1.8 PvP

Enabled by default. Everything is under `[combat]` in `config/combat.toml`, and every mechanic can
be switched off on its own.

## Where these came from

The formulas were read out of Minecraft 1.8.8's own source — Spigot build `582b`, hotfix 3 —
not reconstructed from memory or from wiki descriptions. `reference/fetch-1.8.8-source.sh` rebuilds
those sources so you can compare them against
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/combat/LegacyCombat.java` yourself.

The methods everything below comes from:

| 1.8.8 | What it gave |
|---|---|
| `EntityLiving.a(Entity, float, double, double)` | knockback |
| `EntityHuman.attack(Entity)` | criticals, sprint knockback, the sprint reset |
| `EntityLiving.damageEntity(DamageSource, float)` | the invulnerability window |
| `ItemSword`, `ItemTool`, `Item.EnumToolMaterial` | the damage of each weapon |

## What actually changed since 1.8

Reading the two versions side by side, some of what people attribute to 1.9 turned out to be
unchanged, and one difference nobody talks about turned out to matter a great deal.

### Attack cooldown — changed

The obvious one. Since 1.9 a hit is scaled by how long you waited since the last, and the client
draws a charge indicator. `no-attack-cooldown = true` makes every hit a full hit.

This is implemented by making the server answer "fully charged" whenever it is asked, which removes
the cooldown everywhere at once: the damage scaling, the client's indicator, and the sweep test that
depends on a full-strength hit.

### Knockback — changed, and this is the big one

Modern:

```java
onGround() ? Math.min(0.4, deltaY / 2.0 + power) : deltaY
```

1.8:

```java
motY = motY / 2 + 0.4;   if (motY > 0.4) motY = 0.4;
```

Modern only touches the vertical velocity of a target **standing on the ground**. Hit somebody
already in the air and their height is untouched. In 1.8 the lift was applied every time, which is
precisely what lets a combo hold somebody off the ground.

If you change one setting in this file, this is the one that changes how the game feels.

### Sprint-jump criticals — changed, and rarely mentioned

Modern's critical test ends with a condition 1.8 did not have:

```java
&& !this.isSprinting()
```

In 1.8, sprinting did not stop a hit critting. That single clause is why sprint-jump crits work
there and not here, and restoring it is `legacy-criticals = true`.

### Weapon damage — changed, and not where people expect

Read out of `ItemSword`, `ItemTool` and its subclasses, and `Item.EnumToolMaterial`. In 1.8 a
weapon's attack damage modifier was a base for its kind plus the material's bonus: 4 for a sword,
3 for an axe (`ItemAxe` passes `3.0F` up), 2 for a pickaxe and 1 for a shovel (`ItemSpade`). The
material bonuses are wood 0, stone 1, iron 2, diamond 3 and gold 0 — the same figures 26.3's
`ToolMaterial` still uses. The player's own attack damage is 1.0 (`EntityHuman.initAttributes`),
and `EntityHuman.attack` reads the attribute and adds nothing else, so there is no extra point for
the fist. `ItemHoe` is not an `ItemTool` and carried no modifier.

What changed is the bases. 26.3 builds swords on 3, pickaxes on 1, shovels on 1.5 and axes on a
different figure per material (6, 7, 7, 6, 6, 5 and 5). Totals, counting the player's 1:

| Weapon | 1.8 | 26.3 |
|---|---|---|
| Wooden / golden sword | 5 | 4 |
| Stone sword | 6 | 5 |
| Iron sword | 7 | 6 |
| Diamond sword | 8 | 7 |
| Wooden / golden axe | 4 | 7 |
| Stone axe | 5 | 9 |
| Iron axe | 6 | 9 |
| Diamond axe | 7 | 9 |
| Wooden / golden pickaxe | 3 | 2 |
| Stone pickaxe | 4 | 3 |
| Iron pickaxe | 5 | 4 |
| Diamond pickaxe | 6 | 5 |
| Wooden / golden shovel | 2 | 2.5 |
| Stone shovel | 3 | 3.5 |
| Iron shovel | 4 | 4.5 |
| Diamond shovel | 5 | 5.5 |
| Any hoe | 1 | 1 |

Every sword is one point stronger in 1.8, but the larger change is the axe: it hit for **less**
than the sword of the same material, and now hits for more.

Items 1.8 did not have:

| Weapon | Here | 26.3 | Why |
|---|---|---|---|
| Copper sword / axe / pickaxe / shovel | 6 / 5 / 4 / 3 | 5 / 9 / 3 / 3.5 | Copper's bonus is 1, as stone's; same rule |
| Netherite sword / axe / pickaxe / shovel | 9 / 8 / 7 / 6 | 8 / 10 / 6 / 6.5 | Netherite's bonus is 4; same rule |
| Copper and netherite hoes | 1 | 1 | Already equal |
| Trident, mace, spears | modern | 9, 6, 1–5 | No 1.8 class to take a rule from |

Copper and netherite are clear: the 1.8 classes took any material and added its bonus to a fixed
base, and the two new materials have bonuses of their own. The trident, the mace and the spears
are not swords or tools and have mechanics 1.8 never had (throwing, smashing, charging), so there is
no rule to extend and they keep their modern damage.

**How it is applied.** At the moment of the hit, where `Player.attack` and `Mob.doHurtTarget` read
the attack damage attribute — mobs too, because 1.8 applied the same item modifiers to a zombie
holding a sword. The item registry is not touched. That would have meant loading the configuration
before `Bootstrap.bootStrap()`, and it could not follow a reload; this does, and nothing stored on
disk or sent to a client changes. The weapon's modifier is swapped only when the stack carries no
attribute modifiers of its own: a weapon a plugin, a command or a loot table gave modifiers to keeps
them, as 1.8 kept an item's `AttributeModifiers` tag instead of the item's defaults. Every other
modifier on the attacker (Strength, Weakness, a plugin's) is kept and the value worked out again.

**The tooltip still shows the modern figure.** A modern client draws it from its own copy of the
item's default components, and the server only sends the components a stack has changed. Showing
1.8's number would mean adding an `attribute_modifiers` component to every weapon sent, and a
creative-mode client sends the whole stack back, which would write that component into the item for
good: it would survive turning the setting off and would make the weapon look custom to every
plugin. So it is not done. A 1.8 client through ViaVersion draws the tooltip from 1.8's own item
definitions, so it already shows the 1.8 figure.

### Sprint knockback — unchanged

Modern already adds `0.5` of knockback on a sprint hit, cuts the attacker's own horizontal speed to
`0.6`, and cancels their sprint. That is exactly what 1.8 did. The settings exist so you can change
or disable it, not because anything had to be reimplemented.

### The invulnerability window — unchanged

Twenty ticks, and a stronger hit still lands during it for the difference between the two. Both
match 1.8. `invulnerability-ticks` and `damage-overriding` exist to let you change them.

Worth knowing what `damage-overriding` prevents: without it, tapping somebody for half a heart makes
them briefly immune to an axe.

### Sword blocking — reimplemented, not restored

This is the one place the fork does not simply put back what was there, because there is nothing
left to put back: swords have no blocking behaviour in modern Minecraft at all.

What happens instead:

- Right-clicking a sword starts "using" it, the same machinery a bow goes through. That is what sets
  the flag the client reads — and what ViaVersion turns back into the 1.8 blocking animation for a
  client of that version.
- Incoming damage is multiplied by `1 - sword-blocking-reduction` (so halved by default) in the
  damage path.

Deliberately **not** done by giving swords a synthetic `BLOCKS_ATTACKS` component. That would change
what the rest of the game believes a sword is: its tooltip, what an anvil does with it, what a
plugin reads off it.

**A modern client will get the damage reduction without the animation.** There is no blocking pose
left in the client to trigger. Players connecting through ViaVersion from 1.8 see it properly, which
is the intended way to play this.

### Shields and the off-hand — removed

Neither existed in 1.8. Shields stop blocking; the off-hand swap is refused and the inventory resent
so the client does not stay out of step with a swap it already drew.

## What has been verified, and what has not

Being straight about this, because it is a fair question for a claim like "1.8 PvP".

**Verified** — the arithmetic is checked against fixed numbers in
`LegacyCombatTest`, including that an airborne target is still lifted, that upward knockback caps at
0.4, that the push is normalised so distance does not change it, and that each critical condition
behaves as 1.8's did, and the 1.8 total of every weapon in the table above. The server starts
with all of it enabled and ViaVersion loaded. Weapon damage was also checked in a running server: a
zombie (base 3) hit a pig once with each of the 28 swords, axes, pickaxes and shovels for exactly 3
plus the 1.8 modifier, while its attack damage attribute still read the modern figure; the hoes,
the trident, the mace and an empty hand hit for their modern damage; and a diamond sword a plugin
gave a +20 modifier hit for its own 23. The player's path calls the same method from
`Player.attack` and was not exercised with a real client.

**Not verified** — whether it *feels* right, and whether the blocking animation renders correctly
for a 1.8 client through ViaVersion. Both need people in a server hitting each other; neither can be
established from a test. If something feels wrong, the mechanic is almost certainly switchable on
its own, which makes it straightforward to narrow down.

## Every setting

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Whether any of this applies |
| `no-attack-cooldown` | `true` | Every hit is a full hit |
| `no-sweep-attack` | `true` | No sweeping arc |
| `legacy-knockback` | `true` | 1.8's formula, which lifts an airborne target |
| `knockback-horizontal` | `0.4` | Horizontal strength |
| `knockback-vertical` | `0.4` | Vertical strength |
| `knockback-vertical-limit` | `0.4` | Ceiling on upward knockback |
| `sprint-knockback` | `true` | Sprinting adds knockback and cancels itself |
| `sprint-knockback-strength` | `0.5` | Extra push per level |
| `legacy-criticals` | `true` | Sprinting no longer prevents a critical |
| `critical-multiplier` | `1.5` | What a critical multiplies damage by |
| `legacy-weapon-damage` | `true` | Swords, axes, pickaxes and shovels hit for 1.8's damage |
| `sword-base-damage` | `4.0` | What a sword adds its material bonus to |
| `axe-base-damage` | `3.0` | The same for an axe |
| `pickaxe-base-damage` | `2.0` | The same for a pickaxe |
| `shovel-base-damage` | `1.0` | The same for a shovel |
| `invulnerability-ticks` | `20` | Length of the window after a hit |
| `damage-overriding` | `true` | A stronger hit lands during it, for the difference |
| `sword-blocking` | `true` | Right-click a sword to block |
| `sword-blocking-reduction` | `0.5` | Fraction of damage blocking removes |
| `disable-shields` | `true` | Shields stop blocking |
| `disable-offhand` | `true` | The off-hand slot is refused |
