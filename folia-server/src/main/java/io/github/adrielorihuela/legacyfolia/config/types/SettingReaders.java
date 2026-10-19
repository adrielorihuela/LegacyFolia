package io.github.adrielorihuela.legacyfolia.config.types;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingKind;
import io.papermc.paper.configuration.type.BooleanOrDefault;
import io.papermc.paper.configuration.type.Duration;
import io.papermc.paper.configuration.type.DurationOrDisabled;
import io.papermc.paper.configuration.type.EngineMode;
import io.papermc.paper.configuration.type.number.DoubleOr;
import io.papermc.paper.configuration.type.number.IntOr;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Turns the values in the TOML files into the types Paper, Spigot and vanilla keep in memory.
 *
 * <p>A TOML value is a boolean, a number, a string, an array or a table. Several settings are none
 * of those once they are read: Paper keeps {@code "disabled"} or a number as an {@code IntOr},
 * {@code "12h"} as a {@code Duration}, {@code "default"} or a boolean as a
 * {@code BooleanOrDefault}; vanilla keeps the operator level as a {@code LevelBasedPermissionSet};
 * the messages are components. Each method here takes the raw value night-config parsed and
 * returns the object the owning class expects, so that the hook-up never fills a Paper field with
 * something Paper itself would have refused.</p>
 *
 * <p>Wherever Paper has a serializer for the type, it is used, so a value means here exactly what
 * it would mean in {@code paper-global.yml}. The few checks added on top close gaps where Paper is
 * silently lenient, and each says why.</p>
 *
 * <p>Every method throws {@link InvalidSettingException} for a value its setting cannot take, with
 * a message that names the value; none returns a fallback. Deciding what to do about a bad value —
 * usually logging it and keeping the default — is the caller's job, because only the caller knows
 * the setting's name and file.</p>
 */
public final class SettingReaders {

    /** The prefix that marks a message as a translation key rather than MiniMessage text. */
    public static final String TRANSLATE_PREFIX = "translate:";

    /**
     * A duration as Paper writes them: a number and an optional unit, s, m, h or d.
     *
     * <p>Paper's own parser never fails. Anything it cannot read becomes zero seconds, so a typo in
     * a loot refresh time would make chests refill instantly instead of being reported. The format
     * is checked here before Paper parses it.</p>
     */
    private static final Pattern DURATION = Pattern.compile("^\\s*-?\\d+(\\.\\d+)?\\s*[smhd]?\\s*$");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private SettingReaders() {
    }

    /**
     * An integer or {@code "disabled"}.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code IntOr.Disabled}
     */
    public static IntOr.Disabled intOrDisabled(final Object raw) {
        return paper(IntOr.Disabled.SERIALIZER, integral(raw), "an integer or \"disabled\"");
    }

    /**
     * An integer or {@code "default"}.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code IntOr.Default}
     */
    public static IntOr.Default intOrDefault(final Object raw) {
        return paper(IntOr.Default.SERIALIZER, integral(raw), "an integer or \"default\"");
    }

    /**
     * A number or {@code "disabled"}.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code DoubleOr.Disabled}
     */
    public static DoubleOr.Disabled doubleOrDisabled(final Object raw) {
        return paper(DoubleOr.Disabled.SERIALIZER, raw, "a number or \"disabled\"");
    }

    /**
     * A number or {@code "default"}.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code DoubleOr.Default}
     */
    public static DoubleOr.Default doubleOrDefault(final Object raw) {
        return paper(DoubleOr.Default.SERIALIZER, raw, "a number or \"default\"");
    }

    /**
     * {@code true}, {@code false} or {@code "default"}. Quoted {@code "true"} and {@code "false"}
     * are accepted too, as Paper accepts them.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code BooleanOrDefault}
     */
    public static BooleanOrDefault booleanOrDefault(final Object raw) {
        return paper(BooleanOrDefault.SERIALIZER, raw, "true, false or \"default\"");
    }

    /**
     * A duration such as {@code "12h"}, {@code "2d"}, {@code "90s"} or {@code "30"} (seconds).
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code Duration}
     */
    public static Duration duration(final Object raw) {
        if (!(raw instanceof final String text) || !DURATION.matcher(text).matches()) {
            throw new InvalidSettingException(describe(raw)
                + " is not a duration: write a number and a unit, such as \"90s\", \"12h\" or \"2d\"");
        }
        return paper(Duration.SERIALIZER, text, "a duration");
    }

    /**
     * A duration or {@code "disabled"}.
     *
     * @param raw the parsed TOML value
     * @return Paper's {@code DurationOrDisabled}
     */
    public static DurationOrDisabled durationOrDisabled(final Object raw) {
        if (raw instanceof final String text && "disabled".equalsIgnoreCase(text.trim())) {
            return DurationOrDisabled.USE_DISABLED;
        }
        return new DurationOrDisabled(java.util.Optional.of(duration(raw)));
    }

    /**
     * A duration from a number of ticks, for settings the TOML counts in ticks where Paper keeps a
     * {@code Duration}, such as {@code chunks.delay-chunk-unloads-by}. Paper's durations have whole
     * seconds, so this rounds down to one; {@link Duration#ticks()} gives the ticks back.
     *
     * @param ticks the ticks, 20 to a second
     * @return the duration
     */
    public static Duration durationFromTicks(final long ticks) {
        if (ticks < 0) {
            throw new InvalidSettingException(ticks + " ticks is not a duration: it cannot be negative");
        }
        return Duration.of((ticks / 20) + "s");
    }

    /**
     * An operator permission level, 0 to 4, as {@code op-permission-level} and
     * {@code function-permission-level} take it.
     *
     * <p>Vanilla clamps an out-of-range level to the nearest one, so a 7 becomes owner without a
     * word. A level outside 0 to 4 is refused here instead: it is always a mistake, and the clamp
     * can silently hand somebody more authority than intended.</p>
     *
     * @param raw the parsed TOML value: an integer, or a string holding one
     * @return the permission set {@code DedicatedServerProperties} keeps
     */
    public static LevelBasedPermissionSet permissionLevel(final Object raw) {
        final int level;
        if (raw instanceof final Number number && isIntegral(number)) {
            level = number.intValue();
        } else if (raw instanceof final String text && text.trim().matches("-?\\d+")) {
            level = Integer.parseInt(text.trim());
        } else {
            throw new InvalidSettingException(describe(raw) + " is not a permission level: use a whole number from 0 to 4");
        }
        if (level < 0 || level > 4) {
            throw new InvalidSettingException(level + " is not a permission level: use a whole number from 0 to 4");
        }
        return LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level));
    }

    /**
     * A message for players: MiniMessage, or a translation key after {@value #TRANSLATE_PREFIX}.
     *
     * <p>MiniMessage is parsed the way Paper parses its own messages, leniently: an unknown or
     * unclosed tag is shown as text rather than refused.</p>
     *
     * @param raw the parsed TOML value
     * @return the component
     */
    public static Component message(final Object raw) {
        if (!(raw instanceof final String text)) {
            throw new InvalidSettingException(describe(raw) + " is not a message: messages are strings");
        }
        if (text.startsWith(TRANSLATE_PREFIX)) {
            final String key = text.substring(TRANSLATE_PREFIX.length()).trim();
            if (key.isEmpty() || key.chars().anyMatch(Character::isWhitespace)) {
                throw new InvalidSettingException('"' + text + "\" is not a translation key: write "
                    + TRANSLATE_PREFIX + " followed by the key, such as translate:multiplayer.disconnect.flying");
            }
            return Component.translatable(key);
        }
        return MiniMessage.miniMessage().deserialize(text);
    }

    /**
     * A message for the places that keep a plain string with section-sign colours — Spigot's
     * {@code messages.*} and Bukkit's shutdown message.
     *
     * <p>A translation key cannot survive this: a string has no way to ask the client to translate
     * it, so it is rendered as the key itself.</p>
     *
     * @param raw the parsed TOML value
     * @return the message as a legacy string
     */
    public static String legacyMessage(final Object raw) {
        return LEGACY.serialize(message(raw));
    }

    /**
     * Anti-xray's engine mode, written as its number.
     *
     * @param raw the parsed TOML value: 1, 2 or 3
     * @return Paper's {@code EngineMode}
     */
    public static EngineMode engineMode(final Object raw) {
        if (!(raw instanceof final Number number) || !isIntegral(number)) {
            throw new InvalidSettingException(describe(raw) + " is not an engine mode: use 1, 2 or 3");
        }
        try {
            return EngineMode.valueOf(number.intValue());
        } catch (final IllegalArgumentException e) {
            throw new InvalidSettingException(number + " is not an engine mode: use 1, 2 or 3", e);
        }
    }

    /**
     * One of a fixed set of names, matched without regard to case and with dashes standing in for
     * underscores, as {@code ConfigFile.getEnum} matches them.
     *
     * @param raw the parsed TOML value
     * @param type the enum
     * @param <E> the enum type
     * @return the constant
     */
    public static <E extends Enum<E>> E enumValue(final Object raw, final Class<E> type) {
        if (raw instanceof final String text) {
            final String wanted = text.trim().replace('-', '_');
            for (final E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(wanted)) {
                    return constant;
                }
            }
        }
        throw new InvalidSettingException(describe(raw) + " is not one of " + names(type));
    }

    /**
     * Checks a value against a catalogued setting and converts it to the type the server keeps.
     *
     * <p>Scalars come back as the Java type the kind names — an {@code INT} as an {@code Integer},
     * a {@code DOUBLE} as a {@code Double} even when written {@code 1} — and the other kinds as the
     * objects the methods above return. An {@code ENUM} comes back as the matching entry of
     * {@link SettingEntry#values()}, spelled as the catalog spells it; convert it to the owning
     * enum with {@link #enumValue(Object, Class)}.</p>
     *
     * @param entry the setting
     * @param raw the parsed TOML value
     * @return the converted value
     */
    public static Object convert(final SettingEntry entry, final Object raw) {
        try {
            return convert(entry.kind(), entry.values(), raw);
        } catch (final InvalidSettingException e) {
            throw new InvalidSettingException(entry.tomlPath() + ": " + e.getMessage(), e);
        }
    }

    private static Object convert(final SettingKind kind, final List<String> values, final Object raw) {
        return switch (kind) {
            case BOOLEAN -> {
                if (raw instanceof final Boolean bool) {
                    yield bool;
                }
                throw new InvalidSettingException(describe(raw) + " is not true or false");
            }
            case INT -> {
                if (raw instanceof final Number number && isIntegral(number)
                    && number.longValue() >= Integer.MIN_VALUE && number.longValue() <= Integer.MAX_VALUE) {
                    yield number.intValue();
                }
                throw new InvalidSettingException(describe(raw) + " is not a whole number");
            }
            case LONG -> {
                if (raw instanceof final Number number && isIntegral(number)) {
                    yield number.longValue();
                }
                throw new InvalidSettingException(describe(raw) + " is not a whole number");
            }
            case DOUBLE -> {
                if (raw instanceof final Number number) {
                    yield number.doubleValue();
                }
                throw new InvalidSettingException(describe(raw) + " is not a number");
            }
            case STRING -> {
                if (raw instanceof final String text) {
                    yield text;
                }
                throw new InvalidSettingException(describe(raw) + " is not a string");
            }
            case STRING_LIST -> {
                if (raw instanceof final List<?> list && list.stream().allMatch(String.class::isInstance)) {
                    yield List.copyOf(list);
                }
                throw new InvalidSettingException(describe(raw) + " is not a list of strings");
            }
            case TABLE -> {
                if (raw instanceof Map<?, ?> || raw instanceof com.electronwill.nightconfig.core.UnmodifiableConfig) {
                    yield raw;
                }
                throw new InvalidSettingException(describe(raw) + " is not a table");
            }
            case INT_OR_DISABLED -> intOrDisabled(raw);
            case INT_OR_DEFAULT -> intOrDefault(raw);
            case DOUBLE_OR_DISABLED -> doubleOrDisabled(raw);
            case DOUBLE_OR_DEFAULT -> doubleOrDefault(raw);
            case BOOLEAN_OR_DEFAULT -> booleanOrDefault(raw);
            case DURATION -> duration(raw);
            case DURATION_OR_DISABLED -> durationOrDisabled(raw);
            case PERMISSION_LEVEL -> permissionLevel(raw);
            case MESSAGE -> message(raw);
            case ENGINE_MODE -> engineMode(raw);
            case ENUM -> {
                if (raw instanceof final String text) {
                    final String wanted = text.trim().replace('-', '_');
                    for (final String value : values) {
                        if (value.replace('-', '_').equalsIgnoreCase(wanted)) {
                            yield value;
                        }
                    }
                }
                throw new InvalidSettingException(describe(raw) + " is not one of " + values);
            }
        };
    }

    private static <T> T paper(final ScalarSerializer<T> serializer, final Object raw, final String expected) {
        try {
            return serializer.deserialize(raw);
        } catch (final SerializationException | RuntimeException e) {
            throw new InvalidSettingException(describe(raw) + " is not " + expected, e);
        }
    }

    /**
     * Refuses a fractional number before it reaches an integer serializer: Paper's would log an
     * error and truncate it, turning 2.5 into 2 behind the operator's back.
     */
    private static Object integral(final Object raw) {
        if (raw instanceof final Number number && !isIntegral(number)) {
            throw new InvalidSettingException(number + " is not a whole number");
        }
        return raw;
    }

    private static boolean isIntegral(final Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return true;
        }
        final double value = number.doubleValue();
        return !Double.isInfinite(value) && value == Math.rint(value);
    }

    private static String describe(final Object raw) {
        if (raw instanceof String) {
            return '"' + (String) raw + '"';
        }
        return String.valueOf(raw);
    }

    private static <E extends Enum<E>> String names(final Class<E> type) {
        final StringBuilder names = new StringBuilder();
        for (final E constant : type.getEnumConstants()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(constant.name().toLowerCase(Locale.ROOT));
        }
        return names.toString();
    }
}
