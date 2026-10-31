package io.github.adrielorihuela.legacyfolia.config.bridge;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.types.InvalidSettingException;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import io.papermc.paper.configuration.type.BooleanOrDefault;
import io.papermc.paper.configuration.type.Duration;
import io.papermc.paper.configuration.type.DurationOrDisabled;
import io.papermc.paper.configuration.type.EngineMode;
import io.papermc.paper.configuration.type.number.DoubleOr;
import io.papermc.paper.configuration.type.number.IntOr;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Reads a setting from a source, checks it, and writes it the way each legacy object expects.
 *
 * <p>A value that its setting cannot take is reported and replaced by the catalogued default,
 * never passed on: Spigot and Paper would otherwise each react to it in their own way, from
 * silently truncating it to refusing to start.</p>
 */
final class BridgeValues {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private BridgeValues() {
    }

    /** The checked server-wide value of a setting. */
    static Object global(final EffectiveSettings source, final SettingEntry entry) {
        return checked(entry, source.get(entry.tomlPath()), null);
    }

    /** The checked value of a setting for one world, or for {@code default}. */
    static Object world(final EffectiveSettings source, final SettingEntry entry, final String world) {
        return checked(entry, source.get(entry.tomlPath(), world), world);
    }

    private static Object checked(final SettingEntry entry, final @Nullable Object raw, final @Nullable String world) {
        if (raw == null) {
            return SettingReaders.convert(entry, entry.defaultValue());
        }
        try {
            return SettingReaders.convert(entry, SettingsCatalog.plain(raw));
        } catch (final InvalidSettingException e) {
            LOGGER.warn("{}{}; using the default instead.", world == null ? "" : "[worlds." + world + "] ", e.getMessage());
            return SettingReaders.convert(entry, entry.defaultValue());
        }
    }

    /**
     * A value as {@code server.properties} writes it, which is what {@code Settings} parses.
     *
     * @return the text, or {@code null} for a kind {@code server.properties} has no form for
     */
    static String properties(final SettingEntry entry, final Object value) {
        return switch (entry.kind()) {
            case BOOLEAN, INT, LONG, DOUBLE, STRING, ENUM -> String.valueOf(value);
            case STRING_LIST -> String.join(",", strings(value));
            case PERMISSION_LEVEL -> DedicatedServerProperties.serializePermission((LevelBasedPermissionSet) value);
            default -> throw new IllegalStateException(entry.tomlPath() + " (" + entry.kind() + ") has no server.properties form");
        };
    }

    /**
     * A value as {@code spigot.yml}, {@code bukkit.yml} and {@code commands.yml} hold it.
     *
     * <p>Messages are strings with section-sign colours there: Spigot and Bukkit hand them to the
     * client as legacy text. A string cannot ask the client to translate it, so a
     * {@code translate:} key is rendered once, here, through Paper's flattener: in the server's
     * language (vanilla's English), not as the bare key.</p>
     */
    static Object yaml(final SettingEntry entry, final Object value) {
        return switch (entry.kind()) {
            case BOOLEAN, INT, LONG, DOUBLE, STRING, ENUM -> value;
            case STRING_LIST -> List.copyOf(strings(value));
            case MESSAGE -> LEGACY.serialize((Component) value);
            case BOOLEAN_OR_DEFAULT -> {
                final Boolean bool = ((BooleanOrDefault) value).value();
                yield bool == null ? "default" : bool;
            }
            case TABLE -> table(value);
            default -> throw new IllegalStateException(entry.tomlPath() + " (" + entry.kind() + ") has no YAML form in Spigot or Bukkit");
        };
    }

    /**
     * A value as Paper's own YAML holds it, in the form Paper's serializers read back: MiniMessage
     * for messages, {@code "disabled"} or {@code "default"} for the empty alternatives, a duration's
     * text, an engine mode's number.
     */
    static Object paper(final SettingEntry entry, final Object value) {
        return switch (entry.kind()) {
            case BOOLEAN, INT, LONG, DOUBLE, STRING, ENUM -> value;
            case STRING_LIST -> List.copyOf(strings(value));
            case MESSAGE -> MiniMessage.miniMessage().serialize((Component) value);
            case INT_OR_DISABLED -> ((IntOr.Disabled) value).value().isPresent() ? (Object) ((IntOr.Disabled) value).intValue() : "disabled";
            case INT_OR_DEFAULT -> ((IntOr.Default) value).value().isPresent() ? (Object) ((IntOr.Default) value).intValue() : "default";
            case DOUBLE_OR_DISABLED -> ((DoubleOr.Disabled) value).value().isPresent() ? (Object) ((DoubleOr.Disabled) value).value().getAsDouble() : "disabled";
            case DOUBLE_OR_DEFAULT -> ((DoubleOr.Default) value).value().isPresent() ? (Object) ((DoubleOr.Default) value).value().getAsDouble() : "default";
            case BOOLEAN_OR_DEFAULT -> {
                final Boolean bool = ((BooleanOrDefault) value).value();
                yield bool == null ? "default" : bool;
            }
            case DURATION -> ((Duration) value).value();
            case DURATION_OR_DISABLED -> ((DurationOrDisabled) value).value().map(Duration::value).orElse("disabled");
            case ENGINE_MODE -> ((EngineMode) value).getId();
            case TABLE -> table(value);
            default -> throw new IllegalStateException(entry.tomlPath() + " (" + entry.kind() + ") has no Paper form");
        };
    }

    private static List<String> strings(final Object value) {
        final List<String> strings = new ArrayList<>();
        for (final Object element : (List<?>) value) {
            strings.add(String.valueOf(element));
        }
        return strings;
    }

    /**
     * A table as a plain map in file order, with lists copied so the YAML side can never share
     * (and mutate) the source's own collections.
     */
    static Map<String, Object> table(final Object value) {
        final Map<String, Object> copy = new LinkedHashMap<>();
        final Object plain = SettingsCatalog.plain(value);
        if (plain instanceof final Map<?, ?> map) {
            for (final Map.Entry<?, ?> element : map.entrySet()) {
                final Object inner = element.getValue();
                copy.put(String.valueOf(element.getKey()), inner instanceof final List<?> list ? new ArrayList<>(list)
                    : inner instanceof Map<?, ?> ? table(inner) : inner);
            }
        }
        return copy;
    }
}
