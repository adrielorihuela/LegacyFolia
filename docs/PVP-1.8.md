# 1.8 PvP

Enabled by default. Everything is under `[combat]` in `config/server.config`, and every mechanic can
be switched off on its own.

## Where these came from

The formulas were read out of Minecraft 1.8.8's own source — Spigot build `582b`, hotfix 3 —
not reconstructed from memory or from wiki descriptions. `reference/fetch-1.8.8-source.sh` rebuilds
those sources so you can compare them against
`folia-server/src/main/java/io/github/adrielorihuela/legacyfolia/combat/LegacyCombat.java` yourself.

The three methods everything below comes from:

| 1.8.8 | What it gave |
|---|---|
| `EntityLiving.a(Entity, float, double, double)` | knockback |
| `EntityHuman.attack(Entity)` | criticals, sprint knockback, the sprint reset |
| `EntityLiving.damageEntity(DamageSource, float)` | the invulnerability window |

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
behaves as 1.8's did. The server starts with all of it enabled and ViaVersion loaded.

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
| `invulnerability-ticks` | `20` | Length of the window after a hit |
| `damage-overriding` | `true` | A stronger hit lands during it, for the difference |
| `sword-blocking` | `true` | Right-click a sword to block |
| `sword-blocking-reduction` | `0.5` | Fraction of damage blocking removes |
| `disable-shields` | `true` | Shields stop blocking |
| `disable-offhand` | `true` | The off-hand slot is refused |
