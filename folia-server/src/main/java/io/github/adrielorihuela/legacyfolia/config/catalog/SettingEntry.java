package io.github.adrielorihuela.legacyfolia.config.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Everything known about one TOML key: where it lives, what it holds, and where it came from.
 *
 * @param tomlPath the dotted path, as {@code ServerConfig} and {@code WorldConfig} take it, such as
 *     {@code gameplay.hunger.jump-walk-exhaustion}
 * @param tomlFile the file that holds it, such as {@code gameplay.toml}
 * @param kind the shape of its value in the file
 * @param defaultValue the value the bundled TOML ships with: a {@code Boolean}, {@code Integer},
 *     {@code Long}, {@code Double}, {@code String}, an unmodifiable {@code List}, or an unmodifiable
 *     {@code Map} for a {@link SettingKind#TABLE}
 * @param perWorld whether {@code [worlds.<name>]} may override it
 * @param legacy where it lived before, or {@link LegacyRef#NONE} for the fork's own settings
 * @param alsoFrom further legacy locations of the same decision, such as Paper's per-world spawn
 *     limit beside Bukkit's; empty for most settings
 * @param vanillaName its name in {@code server.properties} when it is a vanilla setting
 * @param mutable whether the running server writes it back, as {@code /difficulty} or
 *     {@code /whitelist on} do
 * @param secret whether it names a secret file; such a setting never goes to
 *     {@code server.properties}
 * @param values the allowed values of an {@link SettingKind#ENUM}; empty otherwise
 * @param notes fallbacks between files, conversions, and anything else a reader or the migration
 *     has to know, or {@code null}
 */
public record SettingEntry(
    String tomlPath,
    String tomlFile,
    SettingKind kind,
    Object defaultValue,
    boolean perWorld,
    LegacyRef legacy,
    List<LegacyRef> alsoFrom,
    @Nullable String vanillaName,
    boolean mutable,
    boolean secret,
    List<String> values,
    @Nullable String notes
) {

    /** The switch for the server.properties mode, which cannot itself live in server.properties. */
    public static final String USE_SERVER_PROPERTIES = "server.use-server-properties";

    public SettingEntry {
        Objects.requireNonNull(tomlPath, "tomlPath");
        Objects.requireNonNull(tomlFile, "tomlFile");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(legacy, "legacy");
        alsoFrom = List.copyOf(alsoFrom);
        values = List.copyOf(values);
        if (kind == SettingKind.ENUM && values.isEmpty()) {
            throw new IllegalArgumentException(tomlPath + " is an ENUM with no values");
        }
        if (kind != SettingKind.ENUM && !values.isEmpty()) {
            throw new IllegalArgumentException(tomlPath + " lists values but is not an ENUM");
        }
        if (vanillaName != null && legacy.source() != LegacySource.SERVER_PROPERTIES) {
            throw new IllegalArgumentException(tomlPath + " has a vanilla name but does not come from server.properties");
        }
    }

    /** The legacy file, shorthand for {@code legacy().source()}. */
    public LegacySource legacySource() {
        return this.legacy.source();
    }

    /** The path inside the legacy file, shorthand for {@code legacy().path()}; empty for the fork's own. */
    public String legacyPath() {
        return this.legacy.path();
    }

    /** Whether this setting is the fork's own, with nothing to migrate from. */
    public boolean isForkOnly() {
        return this.legacy.isNone();
    }

    /** Whether this is a vanilla {@code server.properties} setting. */
    public boolean isVanilla() {
        return this.vanillaName != null;
    }

    /** Every legacy location, the main one first; empty for the fork's own settings. */
    public List<LegacyRef> allLegacy() {
        if (this.legacy.isNone() && this.alsoFrom.isEmpty()) {
            return List.of();
        }
        final List<LegacyRef> all = new ArrayList<>(1 + this.alsoFrom.size());
        if (!this.legacy.isNone()) {
            all.add(this.legacy);
        }
        all.addAll(this.alsoFrom);
        return List.copyOf(all);
    }

    /**
     * Whether this setting may appear in {@code server.properties} at all.
     *
     * <p>Secrets never do, and neither does the switch that decides whether the file exists.</p>
     */
    public boolean allowedInServerProperties() {
        return !this.secret && !USE_SERVER_PROPERTIES.equals(this.tomlPath);
    }

    /**
     * The key this setting's server-wide value has in {@code server.properties}: the vanilla name
     * when there is one, so panels keep working, and the full TOML path otherwise.
     *
     * @return the key, or {@code null} if {@link #allowedInServerProperties()} is false
     */
    public @Nullable String serverPropertiesKey() {
        if (!this.allowedInServerProperties()) {
            return null;
        }
        return this.vanillaName != null ? this.vanillaName : this.tomlPath;
    }

    /**
     * The key one world's override of this setting has in {@code server.properties}:
     * {@code worlds.<world>.<path>}. Vanilla names are not used here; vanilla has no per-world keys.
     *
     * @param world the world's folder name
     * @return the key
     * @throws IllegalStateException if the setting is not per world
     */
    public String serverPropertiesKey(final String world) {
        if (!this.perWorld) {
            throw new IllegalStateException(this.tomlPath + " is not a per-world setting");
        }
        return "worlds." + world + "." + this.tomlPath;
    }
}
