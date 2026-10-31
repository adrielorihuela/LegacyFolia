package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import java.util.Objects;
import java.util.Properties;

/**
 * The {@code java.util.Properties} that {@code DedicatedServerProperties} is built from, made from
 * the settings rather than read from {@code server.properties}.
 *
 * <p>Every catalogued vanilla setting is written under its vanilla name and in the text form
 * vanilla's {@code Settings} parses: booleans and numbers as their text, the data-pack lists
 * comma separated, the permission levels as their number, difficulty and game mode by name. What
 * vanilla reads that the catalogue deliberately does not hold (the management server's two
 * secrets, {@code config.catalog.LegacyExclusions}) is left out, so vanilla gives it its own
 * default, as it would for a file without the line.</p>
 *
 * <p>Three settings need more than a rename:</p>
 * <ul>
 *   <li>{@code rcon.password} is not in any TOML file. It comes from
 *       {@link io.github.adrielorihuela.legacyfolia.config.RconSecret}, and the caller passes it
 *       in; this class stays free of file access.</li>
 *   <li>{@code rcon.ip} is left out when empty. Vanilla falls back to {@code server-ip} only when
 *       the key is missing; an explicit empty address would bind RCON to every interface.</li>
 *   <li>{@code spawn-protection} is {@code server.spawn-protection-radius}, which the fork already
 *       owns; it is written so that {@code DedicatedServerProperties} and its API see it.</li>
 * </ul>
 */
public final class ServerPropertiesBridge {

    /** The TOML path naming the file that holds the RCON password. */
    public static final String RCON_PASSWORD_FILE = "network.rcon.password-file";

    private static final String RCON_IP = "rcon.ip";

    private static final String RCON_PASSWORD = "rcon.password";

    private ServerPropertiesBridge() {
    }

    /**
     * Builds the properties.
     *
     * @param source the settings
     * @param rconPassword the RCON password, as {@code RconSecret.resolve} returned it
     * @return a new, mutable {@code Properties}: {@code DedicatedServerProperties} adds the keys it
     *     defaults, exactly as it does to a loaded file
     */
    public static Properties build(final EffectiveSettings source, final String rconPassword) {
        Objects.requireNonNull(rconPassword, "rconPassword");
        final Properties properties = new Properties();
        for (final SettingEntry entry : SettingsCatalog.get().all()) {
            final String name = entry.vanillaName();
            if (name == null) {
                continue;
            }
            final String text = BridgeValues.properties(entry, BridgeValues.global(source, entry));
            if (RCON_IP.equals(name) && text.isBlank()) {
                continue;
            }
            properties.setProperty(name, text);
        }
        properties.setProperty(RCON_PASSWORD, rconPassword);
        return properties;
    }

    /**
     * The configured name of the RCON password file, for {@code RconSecret.resolve}.
     *
     * @param source the settings
     * @return the name or path, as written in {@code [network.rcon]}
     */
    public static String rconPasswordFile(final EffectiveSettings source) {
        return (String) BridgeValues.global(source, SettingsCatalog.get().require(RCON_PASSWORD_FILE));
    }
}
