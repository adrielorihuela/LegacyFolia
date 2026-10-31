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
| `EntityHuman.attack(Entity)` | criticals, sprint knockback, the sprint reset, fire aspect, the damage statistic |
| `EntityLiving.damageEntity(DamageSource, float)` and CraftBukkit's `EntityLiving.d` | the invulnerability window, the order of the damage modifiers, sword blocking, helmet wear |
| `EntityHuman.damageEntity` | difficulty scaling, 0 damage hits |
| `ItemSword`, `ItemTool`, `Item.EnumToolMaterial` | the damage of each weapon |
| `MobEffectList`, `MobEffectAttackDamage`, `PotionBrewer`, `EntityPotion` | Strength, Weakness, potions and splashes |
| `EnchantmentProtection`, `EnchantmentManager`, `EnchantmentWeaponDamage`, `EnchantmentThorns` | protection, sharpness, bane, thorns |
| `EntityProjectile`, `EntityArrow`, `EntityFishingHook`, `EntityEnderPearl` | projectiles and the rod |
| `Explosion`, `ItemGoldenApple`, `PlayerConnection`, `FoodMetaData` | explosions, the notch apple, reach, food |

Where Spigot or CraftBukkit 1.8.8 changed vanilla 1.8 (0 damage hits on players, the
`BLOCKING` modifier, the restore of a player's motion after a hit), the Spigot behaviour is the one
reproduced, because that is the server 1.8 PvP was played on.

## What actually changed since 1.8

Reading the two versions side by side, some of what people attribute to 1.9 turned out to be
unchanged, and one difference nobody talks about turned out to matter a great deal.

### Attack cooldown — changed

The obvious one. Since 1.9 a hit is scaled by how long you waited since the last, and the client
draws a charge indicator. `no-attack-cooldown = true` makes every hit a full hit.

This is implemented by making the server answer "fully charged" whenever it is asked, which removes
the damage scaling and the sweep test that depends on a full-strength hit. The client draws its
indicator from the attack speed it is sent, so every player also gets a transient attack speed
modifier, never saved, that keeps the indicator full; weapon tooltips are untouched. An item with a
minimum attack charge (the spear) no longer waits for one.

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

The rest of 1.8's knockback, all under `legacy-knockback`:

- **Direction.** The push points away from the entity behind the hit: the attacker, the archer or
  thrower of a projectile, or whoever set off an explosion (which the modern tags exempt from
  knockback altogether). An ownerless projectile other than an arrow does not push. The modern game
  uses the way a projectile flew, or where a splash potion broke.
- **Knockback resistance** is a chance to take none of the base push, not a reduction, so an iron
  golem is never moved.
- **Precision.** The length of the direction is a `float` square root and the strengths are `float`
  constants, so the cap is 0.4000000059604645; a direction shorter than 1.0E-4 is re-rolled with
  `Math.random()`, and nothing clamps a far-off source.
- **The sprint and Knockback push** is added straight onto the target's motion: see below.
- **The restore.** After a player hit by a player is sent their velocity, CraftBukkit puts their
  pre-hit motion back on the server. Paper does the same; `restore-velocity-after-knockback` switches
  it. A mob keeps its knockback.

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
disk changes. The weapon's modifier is swapped only when the stack carries no attribute modifiers
of its own: a weapon a plugin, a command or a loot table gave modifiers to keeps them, as 1.8 kept
an item's `AttributeModifiers` tag instead of the item's defaults. Every other modifier on the
attacker (a plugin's, say) is kept and the value worked out again, with Strength and Weakness
applied as 1.8 applied them (below).

**The tooltip shows the 1.8 figure.** A modern client draws "N Attack Damage" from its own copy of
the item's defaults, and the server only sends the components a stack has changed. So the figure is
rewritten on the way out, in the item encoder every packet uses (`WeaponTooltips.outgoing`, called
from `ItemStack`'s stream codec): a covered weapon with no attribute modifiers of its own is sent
with an `attribute_modifiers` component that is the item's default with the attack damage amount set
to 1.8's. Same id, slot and display, so the client draws it exactly as it draws the default — in
green, with its own 1.0 added (a diamond sword reads 8) — and the attack speed line is unchanged.
That one encoder carries inventories, opened containers, the cursor, held and worn items seen on
other players, dropped items, item frames and villager trades; the recipe book's results are
rewritten as well. With `legacy-weapon-damage` or `enabled` off, nothing is added and the tooltip is
26.3's. Weapons with modifiers of their own are sent as they are, matching what the hit does.

The server's copy never changes: saved worlds, inventories and what plugins read keep the modern
components, so turning the setting off reverts everything at once. A reload that changes the figures
resends every online player's inventory and open container, so their tooltips follow it.

**The creative-mode safeguard.** A creative client sends whole stacks back, including the component
it was given. Wherever the server decodes a stack from a client (the creative slot packet, which is
also how creative mode drops items, and stacks inside a bundle) a component exactly equal to what
would be added for that item is removed again, so the stored item goes back to the default. It is
compared with the configured bases whether or not the setting is on, so a stack sent just before the
setting was turned off is still recognised. A weapon taken from the creative menu, which the client
builds itself, is sent back to it once with the 1.8 figure. Container clicks compare the client's
item hashes with what it was sent, so the rewrite does not cause resyncs.

Not rewritten: recipe-book ingredients shown by item type (the client builds those itself), items
named in a villager's price, and items in chat hover text. A 1.8 client through ViaVersion receives
the same component, which ViaVersion translates to the 1.8 attribute tag; that was not tried with a
real 1.8 client.

### Sprint knockback — changed in how it is applied

Modern adds `0.5` of knockback on a sprint hit, cuts the attacker's own horizontal speed to `0.6`
and cancels their sprint, as 1.8 did. But it sends that 0.5 through the base knockback formula,
which (under the 1.8 formula) ignores its strength, so a sprint hit pushed no further than any
other. 1.8 added `(-sin(yaw) * i * 0.5, 0.1, cos(yaw) * i * 0.5)` straight onto the target's motion,
with `i` the Knockback level plus one for sprinting, no cap and no knockback resistance, for mobs
as well. That is what it does now.

### The invulnerability window — changed in its details

Twenty ticks, and a stronger hit still lands during it for the difference between the two. But 1.8
compared the **raw** damage with the last hit's, handed only the difference to the damage modifiers
(so the event's base is the difference) and remembered the raw figure; the modern game remembers
what was left after blocking and the hard hat. `damage-overriding = false` makes the window
absolute. Without overriding, tapping somebody for half a heart makes them briefly immune to an axe.

### Strength and Weakness — changed

1.8's Strength multiplied the whole attack damage by `1 + 1.3 * level`, after every other bonus,
and Weakness took `0.5 * level` off before that. Now Strength adds 3 a level and Weakness takes 4
off. A diamond sword under Strength I hits for 18.4 rather than 11, and a fist under Weakness I for
0.5 rather than nothing. `legacy-strength-and-weakness`, for players and mobs.

### Armour and protection — changed

Armour is `(25 - points) / 25` whatever the size of the hit (`legacy-armor`). Protection is rolled
on every hit (`legacy-protection`): each piece is worth `floor((6 + level²) / 3 * factor)` points,
the factor being 0.75 for Protection against anything, 1.25 for Fire Protection against fire, 2.5
for Feather Falling against falls (and pearl landings), 1.5 for Blast and Projectile Protection
against their own kind. The sum is capped at 25 and rolled as
`((p + 1) >> 1) + random((p >> 1) + 1)`, capped at 20. Full Protection IV is 20 points, rolling
10 to 20 (40 to 80 % off). Nothing protects against the void. Fire Protection also shortens burning
by 15 % a level of the best piece, and Blast Protection does not reduce explosion knockback.

### Damage enchantments — changed

Sharpness is a flat 1.25 a level, added after the critical multiplier, for players and mobs; any
Smite or Bane on the same weapon still counts. Bane of Arthropods slows for `20 + random(10 *
level)` ticks. Thorns rolls 15 % a level per piece, hurts for 1 to 4 whole points and wears a
thorny piece by 3 (or by 1 when it does not trigger). Fire Aspect lights a living target that is
not burning for a second **before** the hit, then four seconds a level if it lands (never
shortening a longer fire), and puts the first second out if it does not.

### Small things around a hit — changed

1.8 played no attack sounds (only the victim's hurt sound), showed no damage particles, counted
`DAMAGE_DEALT` as ten times the hit's damage, and tilted a hurt player's camera the same way every
time. A critical needed the attacker off a ladder or vine at its feet and out of any water block in
its box shrunk by 0.4. Being hurt cost 0.3 exhaustion for anything armour stops. A falling anvil
wore a helmet by `damage * 4 + random * damage * 2`, on mobs too. A hit on a player scaled with the
difficulty only when the entity that dealt it directly was a mob (so never a skeleton's arrow), and
Easy made a hit `damage / 2 + 1` with no ceiling. All follow 1.8 while `enabled` is on.

### 0 damage hits — changed by Paper, put back

CraftBukkit 1.8.8 let a 0 damage hit land on a player, so a snowball, an egg or a fishing hook
flashed them red, started the invulnerability window, knocked them back from the thrower and wore
each armour piece by a point. Paper filters those hits out again. With combat on they land.

### Reach — changed

1.8 accepted a hit or a use when the two entities' feet were closer than 6 blocks, or 3 without line
of sight, in any game mode; the modern check is eye to box, 3 (5 in creative) plus 3 of leniency.
A swing fired `LEFT_CLICK_AIR` whenever no block was within 4.5 (5 in creative), a swing at a
player included. `legacy-reach`.

### Sword blocking — reimplemented, not restored

This is the one place the fork does not simply put back what was there, because there is nothing
left to put back: swords have no blocking behaviour in modern Minecraft at all.

What happens instead:

- Right-clicking a sword starts "using" it, the same machinery a bow goes through. That is what sets
  the flag the client reads — and what ViaVersion turns back into the 1.8 blocking animation for a
  client of that version.
- Incoming damage becomes `(1 + damage) * sword-blocking-reduction`, as the `BLOCKING` modifier of
  the damage event, after the invulnerability window and the hard hat and before armour, as
  CraftBukkit 1.8.8 had it. A hit of 7 blocks down to 4, a hit of 1 not at all, and a 7 then an 8
  inside the window to `(1 + 1) * 0.5`. Only players block, and only damage armour stops: falls,
  pearls, burning, drowning, suffocation, starving, the void, magic, harming and wither go through.

Deliberately **not** done by giving swords a synthetic `BLOCKS_ATTACKS` component. That would change
what the rest of the game believes a sword is: its tooltip, what an anvil does with it, what a
plugin reads off it.

**A modern client will get the damage reduction without the animation.** There is no blocking pose
left in the client to trigger. Players connecting through ViaVersion from 1.8 see it properly, which
is the intended way to play this.

### Shields, the off-hand and totems — removed

None existed in 1.8. A shield cannot be raised and blocks nothing. Nothing can be put in the off-hand
(the swap key is refused and the inventory resent; clicks, drags and shift-clicks into the slot are
refused), whatever is there goes back into the inventory or is dropped, and nothing is used from it.
A Totem of Undying saves nobody (`disable-totems`).

### Projectiles and the fishing rod — changed

Under `legacy-projectiles`, arrows, snowballs, eggs, pearls and potions do not take on the thrower's
motion, spread by a Gaussian (`0.0075 * inaccuracy`), move before they slow and fall, hit with a 0.3
margin from the first tick, spare the thrower for five ticks and leave from 0.16 to the side of the
head (1.54 high for a sneaking player). Arrows pass through creative players and are slowed by
water after their move, and Punch ignores knockback resistance. Ender pearls have no cooldown and
land where the pearl was before its last move.

Under `legacy-fishing-rod`, the hook hurts what it hits for 0 and catches it only if that landed,
which knocks a player back from the angler: rod PvP. It is cast like a thrown item at 1.5, falls
0.04 and slows to 0.92 (0.5 on a collision) after each move, cannot catch dropped items, lifts what
it reels in by `sqrt(distance) * 0.08`, costs 3 durability for an entity, and never pulls a hooked
player (1.8 never told their client to move).

### Explosions — changed

Under `legacy-explosions`, the damage is `(int) ((p² + p) / 2 * 8 * 2r + 1)` (7 and no truncation
now: a creeper does 49 rather than 43), the push lands only on what the explosion hurt (and always
on TNT and falling blocks, and on an entity still invulnerable from a stronger hit), never on a
creative player, is not reduced by Blast Protection, and starts from TNT's head height. An explosion
something living set off also knocks back away from it.

### Potions, the notch apple and food — changed

Under `legacy-potions`: a splash reaches `1 - distance / 4` measured from where the potion was to
each entity's feet, and full strength for the one it struck; it lasts `round(d * 0.75 + 0.5)` ticks
scaled by the intensity (Speed splashes for 2701); Regeneration and Poison extended last 2:00 and
Poison II 450 ticks; a weaker, longer effect is dropped rather than kept hidden; Absorption lasts its
full time; Hunger costs 0.025 a tick a level. `instant-damage-base`, `instant-heal-base` and
`splash-radius` shape Harming, Healing and the splash.

Under `legacy-notch-apple` the enchanted golden apple gives Absorption I for 2:00, Regeneration V
for 0:30, and Resistance and Fire Resistance for 5:00, and can be crafted from eight gold blocks
around an apple again.

Regeneration is the slow track only (`legacy-regeneration`), and the food costs are 1.8's: 0.3 a
hit dealt and taken, 3 a heart regenerated, 0.2 and 0.8 a jump, 0.01 a metre walked, 0.015 a metre
in water, 0.025 a block broken; peaceful mode gives no saturation.

### Falls, fire and spawning — changed

Fall damage rounds up (3.5 blocks hurts for 1) and hay bales do not soften a fall. Standing in fire
holds a player at eight seconds of burning. An iron golem throws what it hits 0.4 up whatever its
knockback resistance. A player who joins or respawns is protected from everything but the void for
three seconds.

### What could not be made identical

- **Sneaking hitbox and eye height.** 1.8 kept a sneaking player 1.8 blocks tall with eyes at 1.54;
  a modern client crouches to 1.5 with eyes at 1.27 and predicts its own collisions from that, so
  the server cannot keep the old box without the client being pushed back whenever it crouches
  under something 1.5 high. Projectiles thrown while sneaking do leave from 1.54. Swimming and
  crawling poses do not exist for a 1.8 client through ViaVersion either way.
- **Beds and honey.** A modern client bounces on a bed and predicts that movement itself, and honey
  did not exist; both keep their modern fall handling. So does the fall immunity a wind charge
  gives, which no 1.8 item could cause.
- **The blocking animation** for a modern client, as above.
- **Blast Protection above level 6.** 1.8's knockback reduction rounded down to nothing for every
  level a survival player can have; above that it would have reduced the push slightly, and here it
  never does.
- **Things only a client draws**: the hurt camera tilt of a modern client follows the packet sent
  (now always the same direction), and a modern client still shows a brief pearl cooldown until the
  server's "no cooldown" reaches it.

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
`Player.attack` and was not exercised with a real client. The tooltip rewrite is checked in
`WeaponTooltipsTest` through the game's own item codecs: the bytes a client receives carry 1.8's
modifier, a creative client's stack sent back (also inside a bundle) is stored with the default,
container-click hashes match, and nothing is added with the setting off. No real client has drawn
the tooltip yet. Every formula in the sections above has its own case in `LegacyCombatTest` or
`LegacyItemsTest`, with the figures 1.8.8 gives.

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
| `restore-velocity-after-knockback` | `true` | A player hit by a player gets their pre-hit motion back on the server after the packet |
| `legacy-criticals` | `true` | 1.8's critical conditions; sprinting no longer prevents one |
| `critical-multiplier` | `1.5` | What a critical multiplies damage by |
| `legacy-reach` | `true` | Reach is feet to feet, 6 blocks (3 unseen); a swing fires `LEFT_CLICK_AIR` unless it hits a block |
| `invulnerability-ticks` | `20` | Length of the window after a hit |
| `damage-overriding` | `true` | A stronger hit lands during it, for the difference |
| `legacy-weapon-damage` | `true` | Swords, axes, pickaxes and shovels hit for 1.8's damage |
| `sword-base-damage` | `4.0` | What a sword adds its material bonus to |
| `axe-base-damage` | `3.0` | The same for an axe |
| `pickaxe-base-damage` | `2.0` | The same for a pickaxe |
| `shovel-base-damage` | `1.0` | The same for a shovel |
| `legacy-strength-and-weakness` | `true` | Strength multiplies by `1 + 1.3 * level`, Weakness takes 0.5 a level |
| `legacy-sharpness` | `true` | Sharpness is flat, added after the critical, for players and mobs |
| `sharpness-per-level` | `1.25` | What each level of Sharpness adds |
| `fire-aspect-before-damage` | `true` | Fire Aspect lights the target before the hit and puts it out if the hit misses |
| `fire-aspect-seconds` | `1` | How long that first light lasts |
| `legacy-armor` | `true` | Armour takes `(25 - points) / 25`, whatever the hit |
| `armor-damage-divisor` | `25.0` | The 25 in that formula |
| `armor-points-cap` | `20` | The most armour points that count |
| `legacy-protection` | `true` | All five protections rolled afresh on every hit |
| `protection-factor` | `0.75` | Plain Protection's factor |
| `protection-points-cap` | `25` | Ceiling on the summed points before the roll |
| `protection-roll-cap` | `20` | Ceiling on the rolled points |
| `legacy-regeneration` | `true` | Only the slow healing track |
| `regen-interval-ticks` | `80` | Ticks between regenerated hearts |
| `regen-food-threshold` | `18` | Food level healing needs |
| `saturated-fast-regen` | `false` | Whether the modern fast saturation track runs anyway |
| `attack-exhaustion` | `0.3` | Exhaustion for a landed hit |
| `regen-exhaustion` | `3.0` | Exhaustion for a regenerated heart |
| `sprint-jump-exhaustion` | `0.8` | Exhaustion for a sprinting jump |
| `jump-exhaustion` | `0.2` | Exhaustion for a jump |
| `damage-exhaustion` | `0.3` | Exhaustion for being hurt by anything armour stops |
| `walk-exhaustion` | `0.01` | Exhaustion a metre walked or sneaked |
| `swim-exhaustion` | `0.015` | Exhaustion a metre in water |
| `block-break-exhaustion` | `0.025` | Exhaustion a block broken |
| `legacy-instant-potions` | `true` | Healing and Harming as shifts of the bases below |
| `instant-damage-base` | `6` | Harming I's damage |
| `instant-heal-base` | `4` | Healing I's health |
| `splash-radius` | `4.0` | Distance at which a splash reaches nothing (with `legacy-potions`) |
| `legacy-potions` | `true` | 1.8's splash reach and duration, potion durations, no hidden effects, Absorption and Hunger |
| `legacy-notch-apple` | `true` | 1.8's enchanted golden apple, and its recipe |
| `sword-blocking` | `true` | Right-click a sword to block |
| `sword-blocking-reduction` | `0.5` | The factor in `(1 + damage) * 0.5` |
| `legacy-projectiles` | `true` | Projectiles fly, hit and spawn as in 1.8; pearls have no cooldown |
| `legacy-fishing-rod` | `true` | The rod hits for 0 with knockback, casts, pulls and wears as in 1.8 |
| `legacy-explosions` | `true` | 1.8's explosion damage and push |
| `disable-shields` | `true` | Shields cannot be raised |
| `disable-offhand` | `true` | Nothing goes in, stays in or is used from the off-hand |
| `disable-totems` | `true` | A Totem of Undying saves nobody |

Under `[combat.attribute-limits]`, read whether or not combat is on: `max-health` (1024),
`max-absorption` (2048), `movement-speed` (1024) and `attack-damage` (2048), the highest value each
attribute may take.

`legacy-sword-damage`, the switch `legacy-weapon-damage` replaced, is renamed in an existing
`combat.toml` on start-up, keeping its value.
