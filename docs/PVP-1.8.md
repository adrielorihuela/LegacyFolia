# 1.8 PvP

Enabled by default. Everything is under `[combat]` in `config/combat.toml`, and every mechanic can
be switched off on its own. With `enabled = false` every hook falls through to the server's own
behaviour, so LegacyFolia then fights exactly as Folia (and Paper, and vanilla 26.3 under them) does.

The settings are read at start-up and again on `/paper reload` or `/spigot reload`
(`Combat.reload()`, called from `ConfigHooks.reload()`). Three things follow a reload only partly:

- `invulnerability-ticks` is copied into each living entity when it is created, so a reload reaches
  entities created afterwards; an online player keeps the old window until they rejoin (Folia keeps
  the same player object across respawns).
- The enchanted golden apple recipe of `legacy-notch-apple` is added when recipes are loaded: at
  start-up and on `/minecraft:reload`.
- A reload that changes the weapon tooltips (`legacy-weapon-damage`, `enabled` or one of the four
  bases) resends every online player's inventory and open container, on that player's own thread.

## Where these came from

The formulas were read out of Minecraft 1.8.8's own source, Spigot build `582b` (hotfix 3), not
reconstructed from memory or from wiki descriptions. `reference/fetch-1.8.8-source.sh` rebuilds those
sources (it needs `JAVA_8_HOME`; the output is never committed) so you can compare them against
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
`BLOCKING` modifier, the restore of a player's motion after a hit, explosion knockback on an entity
still invulnerable from a stronger hit, spawn invulnerability), the Spigot behaviour is the one
reproduced, because that is the server 1.8 PvP was played on.

## How it is built

The NMS patches are thin hooks; the logic lives in
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/combat/`:

| Class | What it holds |
|---|---|
| `LegacyCombat` | The 1.8.8 arithmetic, pure functions with no server state, checked against fixed numbers in the tests |
| `Combat` | What the hooks call: one volatile `CombatConfig` field read per call, plus the parts that need the world (protection points, reach, explosion rays, the weapon table) |
| `CombatConfig` | The `[combat]` keys of `combat.toml`; `vanilla18(true)` is every 1.8.8 default |
| `WeaponTooltips` | The outgoing and incoming rewrite of the `attribute_modifiers` component |

The combat patches in `folia-server/minecraft-patches/features/`:

| Patch | What it hooks |
|---|---|
| 0016 | No attack cooldown, no sweep, the 1.8 knockback formula, loading the settings at start-up |
| 0017 | Criticals, the critical multiplier, sprint knockback strength, the invulnerability window length, sword blocking (use duration, right click), shields and the off-hand swap |
| 0020 | `(1 + damage) * 0.5` for a raised sword |
| 0021 | Armour and Protection |
| 0022 | The single slow regeneration track |
| 0023 | Attack, regeneration and jump exhaustion |
| 0024 | Instant Healing and Harming |
| 0025 | Flat Sharpness |
| 0026 | Fire Aspect lighting the target before the hit |
| 0027 | `restore-velocity-after-knockback` |
| 0035 | Weapon damage, Strength and Weakness at attack time (`Player.attack`, `Mob.doHurtTarget`) |
| 0036 | Weapon tooltips in the item codecs, item hashes and recipe displays |
| 0038 | All five protection enchantments in one roll |
| 0039 | Sword blocking as the `BLOCKING` damage modifier; the raw-damage invulnerability window |
| 0040 | 0 damage hits landing on a player; a point of armour wear for them |
| 0041 | Helmet wear, the exhaustion of being hurt, difficulty scaling |
| 0042 | Knockback direction, resistance, the extra push, the explosion cause |
| 0043 | Every damage enchantment with Sharpness, Sharpness on mobs, Bane of Arthropods' slowness |
| 0044 | Putting out the fire a missed Fire Aspect hit lit |
| 0045 | No attack sounds, `DAMAGE_DEALT`, the hurt tilt, ladder and water for criticals, the attack speed modifier |
| 0046 | Reach and swings |
| 0047 | Arrows, thrown items and ender pearls |
| 0048 | The fishing rod |
| 0049 | Explosions |
| 0050 | Splash potions, potion durations, hidden effects, Absorption, Hunger |
| 0051 | The enchanted golden apple and its recipe |
| 0052 | Walking, swimming and mining exhaustion; no saturation in peaceful |
| 0053 | Shields, the off-hand and totems |
| 0054 | Falls, hay bales, Fire Protection, fire, Thorns, the iron golem |
| 0055, 0057 | Three seconds of spawn invulnerability, armed only on joining |
| 0056 | Primed TNT pushed from its feet |

0018, 0019, 0028–0034 and 0037 in the same range are not combat (configuration files, the lobby,
the Nether roof, the locator bar).

## What actually changed since 1.8

Reading the two versions side by side, some of what people attribute to 1.9 turned out to be
unchanged, and one difference nobody talks about turned out to matter a great deal.

### Attack cooldown — changed

The obvious one. Since 1.9 a hit is scaled by how long you waited since the last, and the client
draws a charge indicator. `no-attack-cooldown = true` makes every hit a full hit.

The server answers "fully charged" whenever it is asked (`Player.getAttackStrengthScale` returns 1),
which removes the damage scaling and every test that depends on a full-strength hit. The client draws
its indicator from the attack speed it is sent, so every player also gets a transient attack speed
modifier (`legacyfolia:no_attack_cooldown`, +1024), never saved, that keeps the indicator full; weapon
tooltips are untouched. An item with a minimum attack charge (the spear) no longer waits for one.
`no-sweep-attack` removes the sweeping arc separately.

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
  thrower of a projectile, or whoever set off an explosion as 1.8 counted it (whoever lit the TNT, or
  a living thing that blew itself up, such as a creeper). Such an explosion knocks back away from
  its cause even though the modern damage tags exempt explosions from knockback. An ownerless
  projectile other than an arrow does not push. The modern game uses the way a projectile flew, or
  where a splash potion broke.
- **Knockback resistance** is a chance to take none of the base push, not a reduction, so an iron
  golem is never moved.
- **Precision.** The length of the direction is a `float` square root and the strengths are `float`
  constants, so the cap is 0.4000000059604645; a direction shorter than 1.0E-4 is re-rolled with
  `Math.random()`, and nothing clamps a far-off source.
- **The extra push** of a sprint hit and of the Knockback enchantment is added straight onto the
  target's motion: see below.
- **The iron golem** throws whatever it hits 0.4 upward, whatever its knockback resistance.

Only the base knockback of a hit takes the 1.8 formula; every other push in the game (a warden's
sonic boom, a wind charge, a mace) keeps its own.

**The restore.** After a player hit by a player is sent their velocity, CraftBukkit 1.8.8 put their
pre-hit motion back on the server, so the client moves and the server's copy does not. Paper does the
same; `restore-velocity-after-knockback = false` turns it off (only while `legacy-knockback` is on).
A mob keeps its knockback.

### Sprint-jump criticals — changed, and rarely mentioned

Modern's critical test ends with a condition 1.8 did not have:

```java
&& !this.isSprinting()
```

In 1.8, sprinting did not stop a hit critting. That single clause is why sprint-jump crits work
there and not here, and restoring it is `legacy-criticals = true`. The rest of 1.8's test comes back
with it: the attacker must not be on a ladder or vine at its feet (the modern test counts every
climbable block and open trapdoors) and must have no water block in its box shrunk by 0.4 at the top
and bottom (waterlogged blocks do not count). `critical-multiplier` is applied only with
`legacy-criticals` on. Paper's player-crits switch (`gameplay.mobs.disable-player-crits` in `gameplay.toml`) still turns
criticals off.

Paper made `Player.canCriticalAttack` public for its API; the 1.8 test sits inside it, so with
`legacy-criticals` on that API answers with 1.8's rule.

### Weapon damage — changed, and not where people expect

Read out of `ItemSword`, `ItemTool` and its subclasses, and `Item.EnumToolMaterial`. In 1.8 a
weapon's attack damage modifier was a base for its kind plus the material's bonus: 4 for a sword,
3 for an axe (`ItemAxe` passes `3.0F` up), 2 for a pickaxe and 1 for a shovel (`ItemSpade`). The
material bonuses are wood 0, stone 1, iron 2, diamond 3 and gold 0 — the same figures 26.3's
`ToolMaterial` still uses. The player's own attack damage is 1.0 (`EntityHuman.initAttributes`),
and `EntityHuman.attack` reads the attribute and adds nothing else, so there is no extra point for
the fist. `ItemHoe` is not an `ItemTool` and carried no modifier.

What changed is the bases. 26.3 builds swords on 3, pickaxes on 1, shovels on 1.5 and axes on a
different figure per material (wood 6, stone 7, copper 7, iron 6, gold 6, diamond 5, netherite 5).
Totals, counting the player's 1:

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

The 28 covered items are listed one by one (`Combat.Weapons`), not read from the item tags, so a
datapack that adds an item to `#swords` does not get a 1.8 figure it never had.

**How it is applied.** At the moment of the hit, where `Player.attack` and `Mob.doHurtTarget` read
the attack damage attribute (`Combat.attackDamage`) — mobs too, because 1.8 applied the same item
modifiers to a zombie holding a sword. The item registry is not touched. That would have meant loading
the configuration before `Bootstrap.bootStrap()`, and it could not follow a reload; this does, and
nothing stored on disk changes. The weapon's modifier is swapped only when the stack carries no
attribute modifiers of its own and the modifier applied to the attacker is the item's own default: a
weapon a plugin, a command or a loot table gave modifiers to keeps them, as 1.8 kept an item's
`AttributeModifiers` tag instead of the item's defaults. Every other modifier on the attacker (a
plugin's, say) is kept and the value worked out again with the attribute's own arithmetic, with
Strength and Weakness applied as 1.8 applied them (below), and clamped to the attribute's range.

### Weapon tooltips

**The tooltip shows the 1.8 figure.** A modern client draws "N Attack Damage" from its own copy of
the item's defaults, and the server only sends the components a stack has changed. So the figure is
rewritten on the way out, in the item encoder every packet uses (`WeaponTooltips.outgoing`, called
from `ItemStack`'s stream codec): a covered weapon with no attribute modifiers of its own is sent
with an `attribute_modifiers` component that is the item's default with the attack damage amount set
to 1.8's. Same id, slot, operation and display, so the client draws it exactly as it draws the
default — in green, with its own 1.0 added (a diamond sword reads 8) — and the attack speed line is
unchanged. That one encoder carries inventories, opened containers, the cursor, held and worn items
seen on other players, dropped items, item frames and villager trades; recipe-book results
(`SlotDisplay`) are rewritten as well. With `legacy-weapon-damage` or `enabled` off, nothing is added
and the tooltip is 26.3's. Weapons with modifiers of their own are sent as they are, matching what
the hit does.

The server's copy never changes: saved worlds, inventories and what plugins read keep the modern
components, so turning the setting off reverts everything at once.

**The creative-mode safeguard.** A creative client sends whole stacks back, including the component
it was given. Wherever the server decodes a stack from a client (the creative slot packet, which is
also how creative mode drops items, stacks inside a bundle, and `ItemStackTemplate`) a component
exactly equal to what would be added for that item is removed again, so the stored item goes back to
the default. It is compared with the configured bases whether or not the setting is on, so a stack
sent just before the setting was turned off is still recognised. A weapon taken from the creative
menu, which the client builds itself, is sent back to it once with the 1.8 figure. Container clicks
compare the client's item hashes (`HashedStack`) with what it was sent, so the rewrite does not cause
resyncs.

Not rewritten: recipe-book ingredients shown by item type (the client builds those itself), items
named in a villager's price, and items in chat hover text. A 1.8 client through ViaVersion receives
the same component, which ViaVersion translates to the 1.8 attribute tag; that has not been tried
with a real 1.8 client.

### Sprint knockback — changed in how it is applied

A sprint hit adds `sprint-knockback-strength` (0.5) to the knockback of the Knockback enchantment
(half a point a level), cuts the attacker's own horizontal speed to 0.6 and cancels their sprint, as
1.8 did and as 26.3 still does. What differs is where the push goes. 26.3 sends it through
`LivingEntity.knockback`: the target's motion is halved first, the push is reduced by knockback
resistance, and its height is touched only on the ground. 1.8 added
`(-sin(yaw) * amount, 0.1, cos(yaw) * amount)` straight onto the target's motion, worked out in
`float` with the game's sine table, with no halving, no cap and no knockback resistance. With
`legacy-knockback` on that is what happens, for a player's sprint and Knockback and for a mob's
Knockback weapon or its own attack knockback attribute. `sprint-knockback = false` drops the sprint's half point (the Knockback enchantment
still pushes).

Paper's `gameplay.players.disable-sprint-interruption-on-attack` is still honoured.

### The invulnerability window — changed in its details

Twenty ticks, and a stronger hit still lands during it for the difference between the two. But 1.8
compared the **raw** damage with the last hit's, handed only the difference to the damage modifiers
(so the Bukkit event's base is the difference, where Paper passes the whole hit with an
`INVULNERABILITY_REDUCTION` modifier) and remembered the raw figure; the modern game remembers what
was left after blocking and the hard hat. `damage-overriding = false` makes the window absolute.
Without overriding, tapping somebody for half a heart makes them briefly immune to an axe.
`invulnerability-ticks` changes the length (see the reload note at the top).

### Strength and Weakness — changed

1.8's Strength multiplied the whole attack damage by `1 + 1.3 * level`, after every other bonus,
and Weakness took `0.5 * level` off before that. Now Strength adds 3 a level and Weakness takes 4
off. A diamond sword under Strength I hits for 18.4 rather than 11, a fist for 2.3, and a fist under
Weakness I for 0.5 rather than nothing. `legacy-strength-and-weakness`, for players and mobs, whatever
is in their hand.

### Armour and protection — changed

Armour is `(25 - points) / 25` whatever the size of the hit, with points capped at 20
(`legacy-armor`; armour toughness plays no part). Protection is rolled on every hit
(`legacy-protection`): each piece is worth `floor((6 + level²) / 3 * factor)` points, the factor
being 0.75 for Protection against anything, 1.25 for Fire Protection against fire, 2.5 for Feather
Falling against falls (and pearl landings), 1.5 for Blast and Projectile Protection against their own
kind. The sum is capped at 25 and rolled as `((p + 1) >> 1) + random((p >> 1) + 1)`, capped at 20,
and the result comes off as `(25 - rolled) / 25`. Full Protection IV is 20 points, rolling 10 to 20
(40 to 80 % off). A player's four armour pieces count; a mob's held item counts as well, as it was
part of a mob's equipment in 1.8. Nothing protects against the void. Fire Protection also shortens
burning by 15 % a level of the best piece (with `enabled`), and Blast Protection does not reduce
explosion knockback (with `legacy-explosions`).

### Damage enchantments — changed

Sharpness is a flat `sharpness-per-level` (1.25) a level for players and mobs, where 26.3 gives
`0.5 * level + 0.5` (Sharpness V: 6.25 against 3). Both versions add the enchantment bonus after the
critical multiplier, so a critical never amplifies it. Any Smite or Bane on the same weapon still
counts, as 1.8 summed them. Bane of Arthropods slows for `20 + random(10 * level)` ticks
(Slowness IV), where 26.3 gives 30 at level I. Thorns rolls 15 % a level per piece, hurts for 1 to 4
whole points (`level - 10` above level 10) and wears a random thorny piece by 3, or by 1 when it does
not trigger. Fire Aspect lights a living target that is not burning for `fire-aspect-seconds` (1)
**before** the hit, then four seconds a level if it lands (never shortening a longer fire), and puts
that first second out if it does not; the early light fires an `EntityCombustByEntityEvent` a plugin
can cancel or change.

### Small things around a hit — changed

1.8 played no attack sounds (only the victim's hurt sound), showed no damage particles, counted
`DAMAGE_DEALT` as ten times the hit's damage, and tilted a hurt player's camera the same way every
time. Being hurt cost 0.3 exhaustion (`damage-exhaustion`) for anything armour stops and nothing for
the rest. A falling anvil or block wore a helmet by `damage * 4 + random * damage * 2`, on mobs too.
A hit on a player scaled with the difficulty only when the entity that dealt it directly was a mob
(so never a skeleton's arrow), and Easy made a hit `damage / 2 + 1` with no ceiling (a 1 damage hit
does 1.5). All follow 1.8 while `enabled` is on.

### 0 damage hits — changed by Paper, put back

CraftBukkit 1.8.8 let a 0 damage hit land on a player, so a snowball, an egg or a fishing hook
flashed them red, started the invulnerability window, knocked them back from the thrower and wore
each armour piece by a point. Paper filters those hits out again. With `enabled` on they land.

### Reach — changed

1.8 accepted a hit or a use when the two entities' feet were closer than 6 blocks, or 3 without line
of sight (traced head to head, a sneaking head at 1.54, and stopped by tall grass, flowers, torches
and signs as well as solid blocks), in any game mode; the modern check is eye to box, 3 (5 in
creative) plus Paper's leniency (`gameplay.players.client-interaction-leniency-distance`), which is
not used under `legacy-reach`. A swing fires `LEFT_CLICK_AIR` whenever no block is within 4.5 (5 in creative), a
swing at a player included; Paper's other swing events (`LEFT_CLICK_BLOCK` for an adventure player)
are not fired then. Folia's own rule still applies on top: an entity owned by another region cannot
be hit or used.

### Sword blocking — reimplemented, not restored

This is the one place the fork does not simply put back what was there, because there is nothing
left to put back: swords have no blocking behaviour in modern Minecraft at all.

What happens instead:

- Right-clicking a sword (anything in `#swords`) starts "using" it for up to 72000 ticks, the same
  machinery a bow goes through. That is what sets the flag the client reads — and what ViaVersion
  turns back into the 1.8 blocking animation for a client of that version.
- Incoming damage becomes `(1 + damage) * sword-blocking-reduction`, as the `BLOCKING` modifier of
  the damage event, after the invulnerability window and the hard hat and before armour, as
  CraftBukkit 1.8.8 had it. A hit of 7 blocks down to 4, a hit of 1 not at all, and a 7 then an 8
  inside the window to `(1 + 1) * 0.5`. Only players block, and only damage armour stops: falls,
  pearls, burning, drowning, suffocation, starving, the void, magic, harming and wither go through.

Deliberately **not** done by giving swords a synthetic `BLOCKS_ATTACKS` component. That would change
what the rest of the game believes a sword is: its tooltip, what an anvil does with it, what a
plugin reads off it. None of the shield's side effects (disabling, sounds, damage to the item)
follow from a raised sword.

**A modern client gets the damage reduction without the animation.** There is no blocking pose left
in the client to trigger. Players connecting through ViaVersion and ViaRewind from 1.8 see it
properly, which is the intended way to play this. Those are plugins the server has to install; they
are not part of LegacyFolia (the plugin updater can keep them current: see
[PLUGIN-UPDATER.md](PLUGIN-UPDATER.md)).

### Shields, the off-hand and totems — removed

None existed in 1.8. With `disable-shields` a shield cannot be raised and blocks nothing. With
`disable-offhand` nothing can be put in the off-hand (the swap key is refused and the inventory
resent; clicks, drags and shift-clicks into the slot are refused), whatever is there goes back into
the inventory or is dropped every tick, and nothing is used from it. With `disable-totems` a Totem of
Undying in either hand saves nobody.

### Projectiles and the fishing rod — changed

Under `legacy-projectiles`, projectiles do not take on the thrower's motion (which also makes
Paper's `gameplay.items.disable-relative-projectile-velocity` moot), spread by a Gaussian
(`0.0075 * inaccuracy`) rather than a triangle, hit with a 0.3 margin from the first tick and spare
the thrower for five ticks. Thrown items (snowballs, eggs, pearls, potions) move before they slow and
fall. Thrown items, and arrows a player shoots, leave from 0.16 to the side of the head, 0.1 below
it (1.54 high for a sneaking player); arrows from mobs keep the modern start, as 1.8's skeleton
arrows used another constructor. Arrows pass through invulnerable (creative) players and are slowed
by water after their move, and Punch pushes `level * 0.6` along the arrow's flight, plus 0.1 up,
ignoring knockback resistance. Ender pearls have no cooldown (the cooldown is set and then cleared,
so a modern client is told it is over) and land where the pearl was before its last move.

Under `legacy-fishing-rod`, the hook hurts what it hits for 0 and catches it only if that landed,
which knocks a player back from the angler: rod PvP. It is cast like a thrown item at 1.5 from the
same point as a thrown item, falls 0.04 and slows to 0.92 (0.5 on a collision) after each move while
flying, cannot catch dropped items, adds `sqrt(distance) * 0.08` of lift to what it reels in, costs
3 durability for an entity (5 now), and never moves a hooked player's client: the pull is added to
the server's copy only and the reel-in event, which makes a modern client pull itself, is not sent,
as 1.8 never told the client to move.

### Explosions — changed

Under `legacy-explosions`, the damage is `(int) ((p² + p) / 2 * 8 * 2r + 1)` (7 and no truncation
now: a creeper does 49 rather than 43). The rays that decide exposure step with `float` counters and
are stopped by tall grass, flowers, torches and signs as well as solid blocks, as 1.8's
`World.rayTrace` was. The push lands only on what the explosion hurt, and always on primed TNT and
falling blocks, and on an entity still invulnerable from a stronger hit (Spigot's SPIGOT-949); it is
not reduced by Blast Protection, and a creative player is never sent it (the server's copy still
moves, as in 1.8). Primed TNT is pushed from its feet. An explosion something living set off (a
creeper, whoever lit the TNT; not a ghast's fireball, a wither skull or an end crystal, which had no
cause in 1.8) also knocks back away from it, under `legacy-knockback`. Paper's
`gameplay.environment.disable-explosion-knockback` still applies.

### Potions, the notch apple and food — changed

Under `legacy-potions`: a splash reaches `1 - distance / splash-radius` measured from where the
potion was before its last move to each entity's feet, and full strength for the one it struck,
among the entities within 4 blocks across and 2 up and down of it (the same search box 1.8 used); it
lasts `round(d * 0.75 + 0.5)` ticks scaled by the intensity (Speed splashes for 2701); Regeneration
and Poison extended last 2:00 and Poison II 450 ticks; a weaker, longer effect is dropped rather than
kept hidden; Absorption lasts its full time even once its hearts are gone; Hunger costs 0.025 a tick
a level. Under `legacy-instant-potions`, Harming does `instant-damage-base << amplifier` (6, 12) and
Healing restores `instant-heal-base << amplifier` (4, 8); 26.3 uses the same shifts, so this only
makes the bases configurable.

Under `legacy-notch-apple` the enchanted golden apple gives Absorption I for 2:00, Regeneration V
for 0:30, and Resistance and Fire Resistance for 5:00 (26.3: Absorption IV and Regeneration II for
0:20), and can be crafted from eight gold blocks around an apple again
(`legacyfolia:enchanted_golden_apple`). An apple a plugin or command gave effects of its own keeps
them.

Regeneration is the slow track only (`legacy-regeneration`): a heart every 80 ticks at food 18 or
more, without the modern track that spends saturation to heal every 10 ticks
(`saturated-fast-regen` puts it back). The food costs are 1.8's: 0.3 a hit dealt and taken, 3 a
heart regenerated, 0.2 and 0.8 a jump, 0.01 a metre walked or sneaked, 0.015 a metre in water,
0.025 a block broken; peaceful mode gives no saturation.

### Falls, fire and spawning — changed

All with `enabled`, no switch of their own. Fall damage rounds up (3.5 blocks hurts for 1) and hay
bales do not soften a fall. Standing in fire holds a player at eight seconds of burning, rather than
adding a tick or two for every tick in it. A player who joins is protected from everything but the
void for three seconds, and from falls too when the world has PvP off, rather than until their client
says it has loaded the world. As on Spigot 1.8.8, a respawn or leaving the End does not protect them
again: the timer belongs to the player object, which Folia keeps across both.

## LegacyFolia, Paper and Folia

With `enabled = false`, combat is Folia's, which is Paper's, which is vanilla 26.3 plus Paper's
options. With it on, these are the places the three differ in ways a server owner or a plugin can
see:

| | Paper / Folia | LegacyFolia with combat on |
|---|---|---|
| 0 damage hit on a player | Dropped (Paper's filter) | Lands, as on CraftBukkit 1.8.8 |
| Player's motion after a PvP hit | Restored on the server after the velocity packet | The same, switchable |
| Extra push (sprint, Knockback) | `EntityKnockbackByEntityEvent`, cause `ENTITY_ATTACK` | `EntityPushedByEntityAttackEvent`, cause `PUSH`, because it is added through `Entity.push` with the attacker |
| Invulnerability window in `EntityDamageEvent` | Base = whole hit, `INVULNERABILITY_REDUCTION` takes the last hit off | Base = the difference |
| Sword blocking | None | `BLOCKING` modifier of `EntityDamageEvent` |
| Swing events | `LEFT_CLICK_AIR` / `LEFT_CLICK_BLOCK` from Paper's ray trace | `LEFT_CLICK_AIR` from 1.8's block-only trace (with `legacy-reach`) |
| Weapon items | Modern components | Modern components stored; only the packets carry 1.8's figure |
| `Player.canCriticalAttack()` (Paper API) | 26.3's rule | 1.8's rule (with `legacy-criticals`) |

Paper and Spigot settings that keep working (all in `gameplay.toml`):
`gameplay.mobs.disable-player-crits`, `gameplay.players.disable-sprint-interruption-on-attack`,
`gameplay.environment.disable-explosion-knockback`. Settings that are not consulted while combat is
on, because `combat.toml` holds the 1.8 figure instead: Spigot's hunger costs
`gameplay.hunger.combat-exhaustion`, `regen-exhaustion`, `jump-sprint-exhaustion`,
`jump-walk-exhaustion`, `other-multiplier` and `swim-multiplier` (see
[SETTINGS-MAP.md](SETTINGS-MAP.md)); `gameplay.players.client-interaction-leniency-distance` under
`legacy-reach`; `gameplay.items.disable-relative-projectile-velocity` under `legacy-projectiles`.

Folia's region threading changes nothing in the formulas. Every hook runs on the thread that owns
the entity being hit, as the vanilla code around it does; the inventory resend after a reload is
scheduled on each player's own thread; an entity in another region cannot be hit or used, as in
Folia; and an ender pearl lands through Folia's own teleport path, from the position 1.8 used.

## What could not be made identical

- **Sneaking hitbox and eye height.** 1.8 kept a sneaking player 1.8 blocks tall with eyes at 1.54;
  a modern client crouches to 1.5 with eyes at 1.27 and predicts its own collisions from that, so
  the server cannot keep the old box without the client being pushed back whenever it crouches
  under something 1.5 high. Reach, line of sight and projectiles thrown while sneaking do use 1.54.
  Swimming and crawling poses do not exist for a 1.8 client through ViaVersion either way.
- **Beds and honey.** A modern client bounces on a bed and predicts that movement itself, and honey
  did not exist; both keep their modern fall handling. So does the fall immunity a wind charge
  gives, which no 1.8 item could cause.
- **The fishing rod once it lands.** Only the hook's flight follows 1.8; bobbing in water, waiting for
  a bite and staying stuck in a block keep the modern logic.
- **Modern projectiles.** The spread, the inherited motion, the 0.3 margin and the five-tick rule
  are in the code every projectile shares, so tridents, wind charges and other projectiles 1.8 never
  had follow them too under `legacy-projectiles`.
- **The blocking animation** for a modern client, as above.
- **Blast Protection above level 6.** 1.8's knockback reduction rounded down to nothing for every
  level a survival player can have; above that it would have reduced the push slightly, and here it
  never does.
- **Things only a client draws**: the hurt camera tilt of a modern client follows the packet sent
  (now always the same direction), and a modern client still shows a brief pearl cooldown until the
  server's "no cooldown" reaches it.

## What has been verified, and what has not

Being straight about this, because it is a fair question for a claim like "1.8 PvP".

**Verified.** The arithmetic is checked against fixed numbers, with the figures 1.8.8 gives, in
65 tests: `LegacyCombatTest` (49: knockback, including that an airborne target is still lifted, that
upward knockback caps at 0.4 and that the push is normalised; the extra push; each critical
condition; the window; armour; all five protections and the roll; blocking; sharpness, bane and the
critical order; every weapon in the table above; Strength and Weakness; instant potions and
splashes; regeneration and every exhaustion cost; helmet wear; reach; projectiles, the rod and
explosions; falls, Fire Protection and Thorns; spawn protection; and that the defaults are 1.8.8's),
`LegacyItemsTest` (3: the notch apple's effects and recipe; shields, the off-hand and totems) and
`WeaponTooltipsTest` (13: the bytes a client receives carry 1.8's modifier, a creative client's
stack sent back, also inside a bundle, is stored with the default, container-click hashes match,
recipe displays are rewritten, nothing is added with the setting off, and a reload only resends
inventories when the tooltips change). They run in `./gradlew :folia-server:test` with the rest of
LegacyFolia's tests.

The server starts with all of it enabled and ViaVersion loaded. Weapon damage was also checked in a
running server with a test plugin: a zombie (base 3) hit a pig once with each of the 28 swords,
axes, pickaxes and shovels for exactly 3 plus the 1.8 modifier, while its attack damage attribute
still read the modern figure; the hoes, the trident, the mace and an empty hand hit for their modern
damage; and a diamond sword a plugin gave a +20 modifier hit for its own 23. The player's path calls
the same method from `Player.attack`.

**Not verified.** Nothing above has been played with real clients yet: hits, combos and knockback
between two players, sword blocking and its animation for a 1.8 client through ViaVersion, the
hidden cooldown indicator, reach through grass, the rod, pearls and arrows, TNT and creepers, potions
and the notch apple, being hit right after joining, and the tooltip on a modern client and on a 1.8
one. Nor whether it *feels* right. Both need people in a server hitting each other; neither can be
established from a test. If something feels wrong, the mechanic is almost certainly switchable on its
own, which makes it straightforward to narrow down.

## Every setting

All under `[combat]` in `config/combat.toml`. Every switch below does nothing while `enabled` is off.

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Whether any of this applies; also the switch for the rules with no key of their own (below) |
| `no-attack-cooldown` | `true` | Every hit is a full hit, and the client's charge indicator stays full |
| `no-sweep-attack` | `true` | No sweeping arc |
| `legacy-knockback` | `true` | 1.8's formula, which lifts an airborne target; 1.8's direction, resistance roll, extra push, explosion cause and iron golem |
| `knockback-horizontal` | `0.4` | Horizontal strength |
| `knockback-vertical` | `0.4` | Vertical strength |
| `knockback-vertical-limit` | `0.4` | Ceiling on upward knockback |
| `sprint-knockback` | `true` | A sprint hit adds the push below; it, or a Knockback weapon, also slows the attacker and ends their sprint |
| `sprint-knockback-strength` | `0.5` | The push a sprint hit adds |
| `restore-velocity-after-knockback` | `true` | A player hit by a player gets their pre-hit motion back on the server after the packet (only read with `legacy-knockback` on) |
| `legacy-criticals` | `true` | 1.8's critical conditions; sprinting no longer prevents one |
| `critical-multiplier` | `1.5` | What a critical multiplies damage by (only read with `legacy-criticals` on) |
| `legacy-reach` | `true` | Reach is feet to feet, 6 blocks (3 unseen); a swing fires `LEFT_CLICK_AIR` unless it hits a block |
| `invulnerability-ticks` | `20` | Length of the window after a hit, for entities created after it is read |
| `damage-overriding` | `true` | A stronger hit lands during it, for the difference |
| `legacy-weapon-damage` | `true` | Swords, axes, pickaxes and shovels hit for 1.8's damage, and their tooltips say so |
| `sword-base-damage` | `4.0` | What a sword adds its material bonus to |
| `axe-base-damage` | `3.0` | The same for an axe |
| `pickaxe-base-damage` | `2.0` | The same for a pickaxe |
| `shovel-base-damage` | `1.0` | The same for a shovel |
| `legacy-strength-and-weakness` | `true` | Strength multiplies by `1 + 1.3 * level`, Weakness takes 0.5 a level |
| `legacy-sharpness` | `true` | Sharpness is a flat bonus a level, for players and mobs |
| `sharpness-per-level` | `1.25` | What each level of Sharpness adds |
| `fire-aspect-before-damage` | `true` | Fire Aspect lights the target before the hit and puts it out if the hit misses |
| `fire-aspect-seconds` | `1` | How long that first light lasts |
| `legacy-armor` | `true` | Armour takes `(25 - points) / 25`, whatever the hit |
| `armor-damage-divisor` | `25.0` | The 25 in that formula, also used for protection |
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
| `splash-radius` | `4.0` | Distance at which a splash reaches nothing (only read with `legacy-potions` on) |
| `legacy-potions` | `true` | 1.8's splash reach and duration, potion durations, no hidden effects, Absorption and Hunger |
| `legacy-notch-apple` | `true` | 1.8's enchanted golden apple, and its recipe |
| `sword-blocking` | `true` | Right-click a sword to block |
| `sword-blocking-reduction` | `0.5` | The factor in `(1 + damage) * 0.5` |
| `legacy-projectiles` | `true` | Projectiles fly, hit and spawn as in 1.8; pearls have no cooldown |
| `legacy-fishing-rod` | `true` | The rod hits for 0 with knockback, casts, pulls and wears as in 1.8 |
| `legacy-explosions` | `true` | 1.8's explosion damage, exposure and push |
| `disable-shields` | `true` | Shields cannot be raised |
| `disable-offhand` | `true` | Nothing goes in, stays in or is used from the off-hand |
| `disable-totems` | `true` | A Totem of Undying saves nobody |

The eight exhaustion costs apply whenever `enabled` is on; there is no separate switch for them.

**Rules with no key of their own**, on whenever `enabled` is: the raw-damage invulnerability window;
0 damage hits landing on a player and wearing their armour; helmet wear; difficulty scaling and Easy;
no attack sounds, no damage particles, `DAMAGE_DEALT` and the hurt tilt; Bane of Arthropods' slowness;
Thorns; Fire Protection's shorter burning; fall damage rounding up and hay bales; eight seconds of
fire; no saturation in peaceful; three seconds of spawn invulnerability. The ladder and water tests
for a critical go with `legacy-criticals`.

Under `[combat.attribute-limits]`, read whether or not combat is on, are the highest values four
attributes may take: `max-health` (1024), `max-absorption` (2048), `movement-speed` (1024) and
`attack-damage` (2048). They are Spigot's `settings.attribute.*.max` moved here, and their defaults
are vanilla's own caps; a value above the cap is clamped to it.

`legacy-sword-damage`, the switch `legacy-weapon-damage` replaced, is renamed in an existing
`combat.toml` on start-up, keeping its value.

See also [CONFIGURATION.md](CONFIGURATION.md) for how the TOML files work and
[SETTINGS-MAP.md](SETTINGS-MAP.md) for where every setting came from.
