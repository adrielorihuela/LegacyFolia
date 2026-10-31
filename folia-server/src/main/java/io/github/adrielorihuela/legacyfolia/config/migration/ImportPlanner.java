package io.github.adrielorihuela.legacyfolia.config.migration;

import io.github.adrielorihuela.legacyfolia.config.WorldConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacyRef;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Works out, from the legacy files' contents, the value every setting had on the older server.
 *
 * <p>The work is in two passes. The first walks every file and files each value it recognises
 * under the TOML key the catalog says it now lives in, keeping apart the three layers the old
 * servers had: the server-wide value ({@code server.properties}, {@code bukkit.yml}, Spigot's
 * global settings, {@code paper-global.yml}), the value for every world
 * ({@code world-settings.default}, {@code paper-world-defaults.yml}) and the value for one world
 * ({@code world-settings.<world>}, {@code <world>/paper-world.yml}). The second pass resolves each
 * key across those layers and files the way the server that read them did.</p>
 *
 * <h2>Where each layer goes</h2>
 *
 * <p>A setting that only ever lived per world (Spigot's world settings, Paper's world
 * configuration) has no server-wide value, so its every-world value goes to the global section of
 * its TOML file: that section is what every world reads unless it says otherwise, which is what
 * {@code world-settings.default} and {@code paper-world-defaults.yml} were. One world's value goes
 * to {@code [worlds.<world>]} in {@code worlds.toml}.</p>
 *
 * <p>A handful of settings had both a server-wide value and an every-world one: the view and
 * simulation distances (server.properties and Spigot), the spawn limits and spawn intervals
 * (Bukkit and Paper) and the chunk autosave interval (Bukkit and Paper). For those the server-wide
 * value is the global section, because it is what the server answers to plugins
 * ({@code Bukkit.getViewDistance()}, {@code Server#getSpawnLimit}), and an every-world value that
 * overrode it goes to {@code [worlds.default]}, which {@code WorldConfig} consults before the
 * global section and which exists precisely for this.</p>
 */
final class ImportPlanner {

    /** One legacy value, filed under its TOML key. */
    record Hit(LegacyRef ref, String from, @Nullable Object raw) {
    }

    /** Every value found for one TOML key, by layer. */
    static final class Hits {

        final Map<LegacyRef, Hit> server = new LinkedHashMap<>();

        final Map<LegacyRef, Hit> allWorlds = new LinkedHashMap<>();

        final Map<String, Map<LegacyRef, Hit>> worlds = new TreeMap<>();

        Map<LegacyRef, Hit> layer(final @Nullable String world) {
            if (world == null) {
                return this.server;
            }
            if (WorldConfig.DEFAULT.equals(world)) {
                return this.allWorlds;
            }
            return this.worlds.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        }
    }

    /** The settings that had a server-wide value and an every-world value that could override it. */
    private static final Set<String> DISTANCES = Set.of("chunks.view-distance", "chunks.simulation-distance");

    /** Keys that only describe the file they are in. */
    private static final Set<String> METADATA = Set.of("_version", "config-version", "version");

    /**
     * Legacy keys deliberately left out of the TOML files (SETTINGS-MAP section 8), with the reason
     * given for each, by file and path.
     */
    private static final Map<String, String> OMITTED = Map.ofEntries(
        Map.entry("PAPER_WORLD:entities.behavior.door-breaking-difficulty",
            "a map of entity type to difficulties computed at run time; no flat TOML form (SETTINGS-MAP 8)"),
        Map.entry("PAPER_GLOBAL:anticheat.obfuscation.items.model-overrides",
            "keyed by item model identifiers, not by a stable key (SETTINGS-MAP 8)"),
        Map.entry("PAPER_WORLD:tick-rates.sensor", "a two-dimensional table with no flat TOML form (SETTINGS-MAP 8)"),
        Map.entry("PAPER_WORLD:tick-rates.behavior", "a two-dimensional table with no flat TOML form (SETTINGS-MAP 8)"),
        Map.entry("PAPER_WORLD:chunks.entity-per-chunk-save-limit", "a map by entity type, not in the TOML files (SETTINGS-MAP 8)"),
        Map.entry("PAPER_WORLD:entities.spawning.despawn-time", "a map by entity type, not in the TOML files (SETTINGS-MAP 8)"),
        Map.entry("PAPER_GLOBAL:packet-limiter.overrides", "keyed by packet class (SETTINGS-MAP 8)"),
        Map.entry("PAPER_GLOBAL:packet-limiter.kick-message", "not in the TOML files (SETTINGS-MAP 8)"),
        Map.entry("SERVER_PROPERTIES:management-server-secret",
            "a secret, regenerated by the server on every start; not imported (SETTINGS-MAP 8)"),
        Map.entry("SERVER_PROPERTIES:management-server-tls-keystore-password",
            "a secret with no TOML key yet; copy it by hand when the management server needs it (SETTINGS-MAP 8)"),
        Map.entry("PAPER_WORLD:chunks.prevent-moving-into-unloaded-chunks",
            "Folia forces it to true whatever the file says (SETTINGS-MAP 8)"),
        Map.entry("BUKKIT:settings.compatibility",
            "dead code: CraftServer.loadCompatibilities() returns before reading it (SETTINGS-MAP 2.6)"),
        Map.entry("PAPER_GLOBAL:messages.use-display-name-in-quit-message",
            "not assigned to any TOML file yet (SETTINGS-MAP 8, pending)"),
        Map.entry("SERVER_PROPERTIES:announce-player-achievements", "dead key: vanilla no longer reads it"));

    private final SettingsCatalog catalog = SettingsCatalog.get();

    private final ImportPlan plan = new ImportPlan();

    private final Map<String, Hits> hits = new LinkedHashMap<>();

    private final Map<String, String> generators = new TreeMap<>();

    private @Nullable String generatorsFrom;

    private @Nullable Map<String, Object> spigot;

    private @Nullable String spigotFile;

    private boolean craftBukkitFamily;

    /**
     * Plans the import of a set of files.
     *
     * @param files each file with its parsed contents
     * @return the plan
     */
    static ImportPlan plan(final Map<LegacyFile, Map<String, Object>> files) {
        final ImportPlanner planner = new ImportPlanner();
        for (final Map.Entry<LegacyFile, Map<String, Object>> file : files.entrySet()) {
            planner.read(file.getKey(), file.getValue());
        }
        planner.resolve();
        return planner.plan;
    }

    // ------------------------------------------------------------------------------------------
    //  First pass: file every value under its key
    // ------------------------------------------------------------------------------------------

    private void read(final LegacyFile file, final Map<String, Object> tree) {
        final String label = file.relative();
        switch (file.kind()) {
            case SERVER_PROPERTIES -> flat(LegacySource.SERVER_PROPERTIES, label, tree);
            case EULA -> flat(LegacySource.EULA_TXT, label, tree);
            case BUKKIT -> {
                this.craftBukkitFamily = true;
                bukkit(label, tree);
            }
            case SPIGOT -> {
                this.craftBukkitFamily = true;
                this.spigot = tree;
                this.spigotFile = label;
                spigot(label, tree);
            }
            case COMMANDS -> walk(LegacySource.COMMANDS, label, tree, "", "", null);
            case PAPER_GLOBAL -> {
                this.craftBukkitFamily = true;
                walk(LegacySource.PAPER_GLOBAL, label, tree, "", "", null);
            }
            case PAPER_WORLD_DEFAULTS -> {
                this.craftBukkitFamily = true;
                walk(LegacySource.PAPER_WORLD, label, tree, "", "", WorldConfig.DEFAULT);
            }
            case PAPER_WORLD -> {
                this.craftBukkitFamily = true;
                walk(LegacySource.PAPER_WORLD, label, tree, "", "", file.world());
            }
        }
    }

    private void flat(final LegacySource source, final String label, final Map<String, Object> tree) {
        for (final Map.Entry<String, Object> property : tree.entrySet()) {
            leaf(source, label, property.getKey(), property.getKey(), property.getValue(), null);
        }
    }

    private void bukkit(final String label, final Map<String, Object> tree) {
        for (final Map.Entry<String, Object> section : tree.entrySet()) {
            final String key = section.getKey();
            final Object value = section.getValue();
            if ("aliases".equals(key) && "now-in-commands.yml".equals(value)) {
                this.plan.ignored.add(label + ": aliases (a pointer to commands.yml)");
            } else if ("worlds".equals(key) && value instanceof final Map<?, ?> worlds) {
                for (final Map.Entry<?, ?> world : worlds.entrySet()) {
                    final String name = String.valueOf(world.getKey());
                    if (!(world.getValue() instanceof final Map<?, ?> settings)) {
                        unplaced(label, "worlds." + name, world.getValue(), "not a section");
                        continue;
                    }
                    for (final Map.Entry<?, ?> setting : settings.entrySet()) {
                        final String path = "worlds." + name + "." + setting.getKey();
                        if ("generator".equals(String.valueOf(setting.getKey())) && setting.getValue() != null) {
                            this.generators.put(name, String.valueOf(setting.getValue()));
                            this.generatorsFrom = label + ": worlds.<world>.generator";
                        } else {
                            unplaced(label, path, setting.getValue(), "the catalog has no key for it");
                        }
                    }
                }
            } else {
                walkOne(LegacySource.BUKKIT, label, key, key, value, null);
            }
        }
    }

    private void spigot(final String label, final Map<String, Object> tree) {
        for (final Map.Entry<String, Object> section : tree.entrySet()) {
            if ("world-settings".equals(section.getKey()) && section.getValue() instanceof final Map<?, ?> worlds) {
                for (final Map.Entry<?, ?> world : worlds.entrySet()) {
                    final String name = String.valueOf(world.getKey());
                    if (world.getValue() instanceof final Map<?, ?> settings) {
                        walk(LegacySource.SPIGOT_WORLD, label, LegacyReader.stringKeys(settings),
                            "world-settings.default.", "world-settings." + name + ".", name);
                    } else {
                        unplaced(label, "world-settings." + name, world.getValue(), "not a section");
                    }
                }
            } else {
                walkOne(LegacySource.SPIGOT, label, section.getKey(), section.getKey(), section.getValue(), null);
            }
        }
    }

    private void walk(final LegacySource source, final String label, final Map<String, Object> tree,
                      final String lookupPrefix, final String shownPrefix, final @Nullable String world) {
        for (final Map.Entry<String, Object> entry : tree.entrySet()) {
            walkOne(source, label, lookupPrefix + entry.getKey(), shownPrefix + entry.getKey(), entry.getValue(), world);
        }
    }

    private void walkOne(final LegacySource source, final String label, final String lookup, final String shown,
                         final @Nullable Object value, final @Nullable String world) {
        if (METADATA.contains(lookup) && this.catalog.byLegacy(source, lookup).isEmpty()) {
            this.plan.ignored.add(label + ": " + shown + " (file metadata)");
            return;
        }
        final String omitted = OMITTED.get(source + ":" + lookup);
        if (omitted != null) {
            unplaced(label, shown, value, omitted);
            return;
        }
        if (this.catalog.byLegacy(source, lookup).isEmpty() && value instanceof final Map<?, ?> nested) {
            if (nested.isEmpty()) {
                return;
            }
            walk(source, label, LegacyReader.stringKeys(nested), lookup + ".", shown + ".", world);
            return;
        }
        leaf(source, label, lookup, shown, value, world);
    }

    private void leaf(final LegacySource source, final String label, final String lookup, final String shown,
                      final @Nullable Object value, final @Nullable String world) {
        final String omitted = OMITTED.get(source + ":" + lookup);
        if (omitted != null) {
            unplaced(label, shown, value, omitted);
            return;
        }
        final Optional<SettingEntry> found = this.catalog.byLegacy(source, lookup);
        if (found.isEmpty()) {
            unplaced(label, shown, value, "the catalog has no key for it");
            return;
        }
        final SettingEntry entry = found.get();
        final String from = label + ": " + shown;
        if (entry.secret()) {
            if (value != null && !String.valueOf(value).isBlank()) {
                this.plan.secrets.put(entry.tomlPath(), new ImportPlan.Planned(entry, String.valueOf(value).trim(), from));
            }
            return;
        }
        if (world != null && !WorldConfig.DEFAULT.equals(world) && !entry.perWorld()) {
            unplaced(label, shown, value, entry.tomlPath() + " is one value for the whole server in LegacyFolia; "
                + "a per-world value has nowhere to go");
            return;
        }
        this.hits.computeIfAbsent(entry.tomlPath(), ignored -> new Hits())
            .layer(world).put(new LegacyRef(source, lookup), new Hit(new LegacyRef(source, lookup), from, value));
    }

    private void unplaced(final String label, final String path, final @Nullable Object value, final String reason) {
        this.plan.unplaced.add(label + ": " + path + " = " + LegacyValues.show(path, value) + "  -- " + reason);
    }

    // ------------------------------------------------------------------------------------------
    //  Second pass: resolve each key as the original server did
    // ------------------------------------------------------------------------------------------

    private void resolve() {
        if (!this.generators.isEmpty()) {
            final SettingEntry entry = this.catalog.require("world.generators");
            this.hits.computeIfAbsent(entry.tomlPath(), ignored -> new Hits()).server.put(entry.legacy(),
                new Hit(entry.legacy(), this.generatorsFrom, new LinkedHashMap<>(this.generators)));
        }
        deriveSpamExclusions();
        tridentFollowsArrows();
        pauseWhenEmpty();

        for (final Map.Entry<String, Hits> key : this.hits.entrySet()) {
            final SettingEntry entry = this.catalog.require(key.getKey());
            final Hits found = key.getValue();
            final String path = entry.tomlPath();
            if (DISTANCES.contains(path)) {
                serverAndWorlds(entry, found, true);
            } else if (path.startsWith("performance.spawning.spawn-limits.")
                || path.startsWith("performance.spawning.ticks-per-spawn.")
                || "chunks.auto-save-interval".equals(path)) {
                serverAndWorlds(entry, found, false);
            } else if ("server.console.debug".equals(path)) {
                either(entry, found);
            } else if ("server.resource-pack.sha1".equals(path)) {
                sha1(entry, found);
            } else if ("server.saving.player-save-interval-ticks".equals(path)) {
                playerSaveInterval(entry, found);
            } else {
                generic(entry, found);
            }
        }
        arrowNote();
    }

    /**
     * The usual case: one value per layer, the server-wide one (or, for a setting that only lived
     * per world, the every-world one) to the global section and each world's own to
     * {@code [worlds.<world>]}.
     */
    private void generic(final SettingEntry entry, final Hits found) {
        @Nullable Object global = null;
        if (!found.server.isEmpty()) {
            final Hit winner = winner(entry, found.server);
            global = convert(entry, winner);
            if (global != null) {
                this.plan.global(entry, global, winner.from());
            }
        }
        if (!found.allWorlds.isEmpty()) {
            final Hit hit = found.allWorlds.values().iterator().next();
            final Object value = convert(entry, hit);
            if (value != null) {
                if (found.server.isEmpty()) {
                    this.plan.global(entry, value, hit.from());
                    global = value;
                } else if (!LegacyValues.same(entry, value, global == null ? entry.defaultValue() : global)) {
                    this.plan.world(WorldConfig.DEFAULT, entry, value, hit.from());
                }
            }
        }
        final Object inherited = inherited(entry);
        for (final Map.Entry<String, Map<LegacyRef, Hit>> world : found.worlds.entrySet()) {
            final Hit hit = world.getValue().values().iterator().next();
            final Object value = convert(entry, hit);
            if (value != null && !LegacyValues.same(entry, value, inherited)) {
                this.plan.world(world.getKey(), entry, value, hit.from());
            }
        }
    }

    /**
     * The main location wins when one file holds the same setting twice under two names, as
     * Spigot's root {@code replace-commands} does next to {@code commands.replace-commands}; the
     * catalog lists the main location first, except where the original server preferred the other.
     */
    private Hit winner(final SettingEntry entry, final Map<LegacyRef, Hit> layer) {
        if (layer.size() == 1) {
            return layer.values().iterator().next();
        }
        final List<LegacyRef> order = new ArrayList<>(entry.allLegacy());
        if ("plugins.commands.replace-commands".equals(entry.tomlPath())) {
            // SpigotConfig moves a root replace-commands under commands., overwriting what is there.
            order.sort((a, b) -> a.path().equals("replace-commands") ? -1 : b.path().equals("replace-commands") ? 1 : 0);
        }
        Hit winner = null;
        for (final LegacyRef ref : order) {
            final Hit hit = layer.get(ref);
            if (hit != null) {
                if (winner == null) {
                    winner = hit;
                } else {
                    this.plan.collisions.add(entry.tomlPath() + ": " + winner.from() + " wins over " + hit.from()
                        + ", as " + (entry.tomlPath().equals("plugins.commands.replace-commands")
                        ? "SpigotConfig moves the root key under commands." : "the original server read it"));
                }
            }
        }
        return winner == null ? layer.values().iterator().next() : winner;
    }

    /**
     * A server-wide value that an every-world value may override (SETTINGS-MAP 3.3, 3.4, 3.6).
     *
     * <p>Spigot's view and simulation distances override server.properties when they are a
     * positive number; {@code default} or anything at or below zero means "the server's value"
     * (SpigotWorldConfig.viewDistance), and the view distance is then clamped to 3..32. Paper's
     * spawn limits, spawn intervals and chunk autosave interval override Bukkit's when they are zero
     * or more; a negative value, -1 by default, means "ask Bukkit" (CraftWorld, AutosavePeriod).
     * Either way an explicit "use the server's value" in one world cancels an every-world
     * override, so that world is given the server's value back.</p>
     */
    private void serverAndWorlds(final SettingEntry entry, final Hits found, final boolean distance) {
        Object server = entry.defaultValue();
        if (!found.server.isEmpty()) {
            final Hit hit = winner(entry, found.server);
            final Object value = convert(entry, hit);
            if (value != null) {
                server = value;
                this.plan.global(entry, value, hit.from());
            }
        }

        Object allWorlds = server;
        if (!found.allWorlds.isEmpty()) {
            final Hit hit = found.allWorlds.values().iterator().next();
            final @Nullable Integer override = override(entry, hit, distance);
            if (override == null) {
                this.plan.collisions.add(entry.tomlPath() + ": " + hit.from() + " = " + LegacyValues.describe(hit.raw())
                    + " defers to the server-wide value " + LegacyValues.describe(server)
                    + (distance ? " (Spigot: default or <= 0 falls back to server.properties)"
                        : " (Paper: a negative value means Bukkit decides)"));
            } else {
                allWorlds = override;
                if (!LegacyValues.same(entry, override, server)) {
                    this.plan.world(WorldConfig.DEFAULT, entry, override, hit.from());
                    this.plan.collisions.add(entry.tomlPath() + ": " + hit.from() + " = " + override
                        + " overrides the server-wide " + LegacyValues.describe(server)
                        + " in every world; written to [worlds.default]");
                }
            }
        }

        for (final Map.Entry<String, Map<LegacyRef, Hit>> world : found.worlds.entrySet()) {
            final Hit hit = world.getValue().values().iterator().next();
            final @Nullable Integer override = override(entry, hit, distance);
            final Object value = override != null ? override : server;
            if (override == null) {
                this.plan.collisions.add(entry.tomlPath() + ": " + hit.from() + " = " + LegacyValues.describe(hit.raw())
                    + " defers to the server-wide value " + LegacyValues.describe(server));
            }
            if (!LegacyValues.same(entry, value, allWorlds)) {
                this.plan.world(world.getKey(), entry, value, hit.from());
            }
        }
    }

    /** The every-world or one-world value, if it overrides the server's; {@code null} if it defers. */
    private @Nullable Integer override(final SettingEntry entry, final Hit hit, final boolean distance) {
        final Object raw = hit.raw();
        if (!(raw instanceof final Number number)) {
            if (raw instanceof final String text && text.trim().matches("-?\\d+")) {
                return override(entry, new Hit(hit.ref(), hit.from(), Integer.parseInt(text.trim())), distance);
            }
            if (raw != null && !LegacyValues.isDefaultWord(raw)) {
                this.plan.invalid.add(hit.from() + " = " + LegacyValues.describe(raw) + " -- not a number; read as the default");
            }
            return null;
        }
        final int value = number.intValue();
        if (distance) {
            if (value <= 0) {
                return null;
            }
            return "chunks.view-distance".equals(entry.tomlPath()) ? Math.max(3, Math.min(32, value)) : value;
        }
        return value < 0 ? null : value;
    }

    /** One key for two: server.properties' debug and Spigot's settings.debug; either one turns it on. */
    private void either(final SettingEntry entry, final Hits found) {
        boolean on = false;
        final List<String> from = new ArrayList<>();
        for (final Hit hit : found.server.values()) {
            final Object value = convert(entry, hit);
            if (Boolean.TRUE.equals(value)) {
                on = true;
            }
            from.add(hit.from() + " = " + LegacyValues.describe(hit.raw()));
        }
        if (from.size() > 1) {
            this.plan.collisions.add(entry.tomlPath() + ": " + String.join(" and ", from) + " -> " + on
                + " (on if either is on)");
        }
        this.plan.global(entry, on, String.join(", ", from));
    }

    /** resource-pack-sha1 wins; vanilla falls back to the deprecated resource-pack-hash when it is empty. */
    private void sha1(final SettingEntry entry, final Hits found) {
        final Hit sha1 = found.server.get(entry.legacy());
        Hit hash = null;
        for (final LegacyRef ref : entry.alsoFrom()) {
            hash = found.server.getOrDefault(ref, hash);
        }
        final Object sha1Value = sha1 == null ? null : convert(entry, sha1);
        final Object hashValue = hash == null ? null : convert(entry, hash);
        if (sha1Value instanceof final String text && !text.isBlank()) {
            if (hashValue instanceof final String other && !other.isBlank() && !other.equals(text)) {
                this.plan.collisions.add(entry.tomlPath() + ": " + sha1.from() + " wins over " + hash.from());
            }
            this.plan.global(entry, text, sha1.from());
        } else if (hashValue instanceof final String text && !text.isBlank()) {
            this.plan.collisions.add(entry.tomlPath() + ": " + (sha1 == null ? "no resource-pack-sha1" : sha1.from() + " is empty")
                + ", so the deprecated " + hash.from() + " is used, as vanilla does");
            this.plan.global(entry, text, hash.from());
        } else if (sha1Value != null) {
            this.plan.global(entry, sha1Value, sha1.from());
        }
    }

    /** Paper's player autosave rate; -1 means the chunk autosave interval, Bukkit's ticks-per.autosave. */
    private void playerSaveInterval(final SettingEntry entry, final Hits found) {
        final Hit hit = winner(entry, found.server);
        final Object value = convert(entry, hit);
        if (value instanceof final Integer rate && rate < 0) {
            final Hits autosave = this.hits.get("chunks.auto-save-interval");
            final SettingEntry autosaveEntry = this.catalog.require("chunks.auto-save-interval");
            Object bukkit = autosaveEntry.defaultValue();
            String from = "the default ticks-per.autosave";
            if (autosave != null && autosave.server.get(autosaveEntry.legacy()) != null) {
                final Hit bukkitHit = autosave.server.get(autosaveEntry.legacy());
                final Object converted = convert(autosaveEntry, bukkitHit);
                if (converted != null) {
                    bukkit = converted;
                    from = bukkitHit.from();
                }
            }
            this.plan.collisions.add(entry.tomlPath() + ": " + hit.from() + " = " + rate
                + " means the chunk autosave interval, so " + from + " = " + bukkit + " is used (Paper)");
            this.plan.global(entry, bukkit, hit.from() + " -> " + from);
        } else if (value != null) {
            this.plan.global(entry, value, hit.from());
        }
    }

    /**
     * {@code commands.enable-spam-exclusions} is not in Spigot's template. When the file lacks it
     * SpigotConfig deduces it, false while {@code commands.spam-exclusions} is still the factory
     * {@code ["/skill"]} and true otherwise, and writes the answer back; the import does the same.
     */
    private void deriveSpamExclusions() {
        if (this.spigot == null) {
            return;
        }
        final SettingEntry entry = this.catalog.require("network.limits.enable-spam-exclusions");
        final Hits existing = this.hits.get(entry.tomlPath());
        if (existing != null && !existing.server.isEmpty()) {
            return;
        }
        final SettingEntry list = this.catalog.require("network.limits.spam-exclusions");
        final Hits listHits = this.hits.get(list.tomlPath());
        Object exclusions = list.defaultValue();
        if (listHits != null && !listHits.server.isEmpty()) {
            final Object converted = convert(list, listHits.server.values().iterator().next());
            if (converted != null) {
                exclusions = converted;
            }
        }
        final boolean derived = !LegacyValues.same(list, exclusions, List.of("/skill"));
        this.plan.collisions.add(entry.tomlPath() + ": absent from " + this.spigotFile + "; deduced as " + derived
            + " from commands.spam-exclusions, as SpigotConfig does");
        this.hits.computeIfAbsent(entry.tomlPath(), ignored -> new Hits()).server.put(entry.legacy(),
            new Hit(entry.legacy(), this.spigotFile + ": commands.enable-spam-exclusions (deduced)", derived));
    }

    /**
     * Spigot reads the trident despawn rate with the arrow despawn rate as its default
     * (SpigotWorldConfig.arrowDespawnRate), so where a layer sets the arrow rate and no layer it
     * inherits from sets the trident rate, the trident rate is the arrow rate.
     */
    private void tridentFollowsArrows() {
        final Hits arrows = this.hits.get("gameplay.items.arrow-despawn-rate");
        if (arrows == null) {
            return;
        }
        final SettingEntry trident = this.catalog.require("gameplay.items.trident-despawn-rate");
        final Hits tridents = this.hits.computeIfAbsent(trident.tomlPath(), ignored -> new Hits());
        if (tridents.allWorlds.isEmpty() && !arrows.allWorlds.isEmpty()) {
            final Hit arrow = arrows.allWorlds.values().iterator().next();
            tridents.allWorlds.put(trident.legacy(), new Hit(trident.legacy(),
                arrow.from() + " (trident-despawn-rate absent; follows the arrow rate)", arrow.raw()));
        }
        for (final Map.Entry<String, Map<LegacyRef, Hit>> world : arrows.worlds.entrySet()) {
            final boolean set = tridents.worlds.containsKey(world.getKey()) || !tridents.allWorlds.isEmpty()
                && !tridents.allWorlds.values().iterator().next().from().endsWith("follows the arrow rate)");
            if (!set) {
                final Hit arrow = world.getValue().values().iterator().next();
                tridents.layer(world.getKey()).put(trident.legacy(), new Hit(trident.legacy(),
                    arrow.from() + " (trident-despawn-rate absent; follows the arrow rate)", arrow.raw()));
            }
        }
        if (tridents.server.isEmpty() && tridents.allWorlds.isEmpty() && tridents.worlds.isEmpty()) {
            this.hits.remove(trident.tomlPath());
        }
    }

    /**
     * Paper forces {@code pause-when-empty-seconds} to -1, so on a Bukkit, Spigot, Paper or Folia
     * server the value in server.properties never applied. Only a vanilla server's is imported.
     */
    private void pauseWhenEmpty() {
        final Hits pause = this.hits.get("server.pause-when-empty-seconds");
        if (pause == null || !this.craftBukkitFamily) {
            return;
        }
        for (final Hit hit : pause.server.values()) {
            this.plan.collisions.add("server.pause-when-empty-seconds: " + hit.from() + " = " + LegacyValues.describe(hit.raw())
                + " not imported: Paper forces -1, so the server never used it");
        }
        this.hits.remove("server.pause-when-empty-seconds");
    }

    /**
     * Paper's arrow despawn rates (SETTINGS-MAP 3.5) default to {@code default}, which falls back to
     * the world's Spigot arrow rate. The TOML keeps both keys and the same fallback, so nothing is
     * rewritten; the report says so, because the Spigot value now decides both.
     */
    private void arrowNote() {
        for (final String paper : List.of("gameplay.items.non-player-arrow-despawn-rate", "gameplay.items.creative-arrow-despawn-rate")) {
            final Hits found = this.hits.get(paper);
            final ImportPlan.Planned arrows = this.plan.global.get("gameplay.items.arrow-despawn-rate");
            if (found == null || arrows == null || found.allWorlds.isEmpty()) {
                continue;
            }
            final Hit hit = found.allWorlds.values().iterator().next();
            if (LegacyValues.isDefaultWord(hit.raw())) {
                this.plan.collisions.add(paper + ": " + hit.from() + " = default falls back to "
                    + "gameplay.items.arrow-despawn-rate = " + arrows.value() + " (Paper FallbackValue); kept as \"default\"");
            }
        }
    }

    /** What a world inherits for a setting, from what has been planned so far. */
    private Object inherited(final SettingEntry entry) {
        final Map<String, ImportPlan.Planned> allWorlds = this.plan.worlds.get(WorldConfig.DEFAULT);
        if (allWorlds != null && allWorlds.containsKey(entry.tomlPath())) {
            return allWorlds.get(entry.tomlPath()).value();
        }
        final ImportPlan.Planned global = this.plan.global.get(entry.tomlPath());
        return global != null ? global.value() : entry.defaultValue();
    }

    /** The TOML value, or {@code null} after reporting a value the setting cannot take. */
    private @Nullable Object convert(final SettingEntry entry, final Hit hit) {
        try {
            final Object value = LegacyValues.toToml(entry, hit.ref().source(), hit.raw());
            return value == LegacyValues.INHERIT ? entry.defaultValue() : value;
        } catch (final RuntimeException e) {
            this.plan.invalid.add(hit.from() + " = " + LegacyValues.show(hit.ref().path(), hit.raw()) + " -- "
                + e.getMessage() + "; " + entry.tomlPath() + " keeps its current value");
            return null;
        }
    }
}
