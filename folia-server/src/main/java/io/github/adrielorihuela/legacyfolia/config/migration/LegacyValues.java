package io.github.adrielorihuela.legacyfolia.config.migration;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.adrielorihuela.legacyfolia.config.catalog.LegacySource;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.types.InvalidSettingException;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Turns a value read from a legacy file into the value its TOML key holds.
 *
 * <p>The legacy files and the TOML files spell the same setting differently. server.properties
 * holds every value as a string, lists comma separated; Spigot's messages use {@code &} colour
 * codes where the TOML uses MiniMessage; Paper writes a translatable message as
 * {@code <lang:key>} where the TOML writes {@code translate:key}; Paper keeps
 * {@code delay-chunk-unloads-by} as a duration where the TOML counts ticks. Every value is
 * brought to the TOML's spelling here and then checked with {@link SettingReaders#convert}, so the
 * importer never writes something the server would later refuse.</p>
 *
 * <p>A few settings are also normalised the way the server that wrote them normalised them on
 * reading, so that the imported value is the value that server ran with rather than the one its
 * file happened to say: a negative Spigot sample count is 0, a growth modifier of 0 is 100, an
 * entity broadcast range is clamped to 10..1000, a Spigot view distance to 3..32.</p>
 */
final class LegacyValues {

    /** A legacy value that means "whatever the default is", such as Paper's {@code default}. */
    static final Object INHERIT = new Object() {
        @Override
        public String toString() {
            return "default";
        }
    };

    private static final Pattern LANG_TAG = Pattern.compile("^<(?:lang|tr|translate):([^:>\\s]+)>$");

    private static final Pattern AMPERSAND_CODE = Pattern.compile("&([0-9a-fk-orx])", Pattern.CASE_INSENSITIVE);

    private LegacyValues() {
    }

    /**
     * Converts one legacy value.
     *
     * @param entry the setting it now lives in
     * @param source the file it came from
     * @param raw the value as the file holds it
     * @return the TOML value, or {@link #INHERIT} when the legacy value means "the default"
     * @throws InvalidSettingException if the value is not one the setting can take
     */
    static Object toToml(final SettingEntry entry, final LegacySource source, final Object raw) {
        final Object value = convert(entry, source, raw);
        if (value == INHERIT) {
            return INHERIT;
        }
        final Object normalised = normalise(entry, value);
        SettingReaders.convert(entry, normalised);
        return normalised;
    }

    private static Object convert(final SettingEntry entry, final LegacySource source, final Object raw) {
        final String path = entry.tomlPath();
        if ("chunks.delay-chunk-unloads-by".equals(path)) {
            final long ticks = SettingReaders.duration(raw instanceof Number ? raw + "s" : String.valueOf(raw)).ticks();
            return Math.toIntExact(ticks);
        }
        if ("plugins.commands.tab-complete".equals(path) && raw instanceof final Boolean bool) {
            // SpigotConfig, config-version < 6: true -> 0, false -> -1.
            return bool ? 0 : -1;
        }
        return switch (entry.kind()) {
            case BOOLEAN -> bool(raw);
            case INT, PERMISSION_LEVEL, ENGINE_MODE -> {
                if (isDefaultWord(raw)) {
                    yield INHERIT;
                }
                yield Math.toIntExact(integer(raw));
            }
            case LONG -> isDefaultWord(raw) ? INHERIT : integer(raw);
            case DOUBLE -> isDefaultWord(raw) ? INHERIT : number(raw);
            case STRING -> scalarString(raw);
            case STRING_LIST -> stringList(raw);
            case TABLE -> table(raw);
            case INT_OR_DISABLED, INT_OR_DEFAULT -> raw instanceof String text && !isNumeric(text)
                ? text.trim().toLowerCase(Locale.ROOT) : (Object) Math.toIntExact(integer(raw));
            case DOUBLE_OR_DISABLED, DOUBLE_OR_DEFAULT -> raw instanceof String text && !isNumeric(text)
                ? text.trim().toLowerCase(Locale.ROOT) : (Object) number(raw);
            case BOOLEAN_OR_DEFAULT -> raw instanceof String text && "default".equalsIgnoreCase(text.trim())
                ? "default" : (Object) bool(raw);
            case DURATION, DURATION_OR_DISABLED -> raw instanceof Number ? raw + "s" : scalarString(raw).trim();
            case MESSAGE -> message(source, scalarString(raw));
            case ENUM -> enumValue(entry, raw);
        };
    }

    private static Object normalise(final SettingEntry entry, final Object value) {
        final String path = entry.tomlPath();
        if (value instanceof final Integer number) {
            if ("server.status.sample-count".equals(path) && number < 0) {
                return 0;
            }
            if (path.startsWith("gameplay.growth.") && path.endsWith("-modifier") && number == 0) {
                return 100;
            }
            if ("performance.entities.entity-broadcast-range-percentage".equals(path)) {
                return Math.max(10, Math.min(1000, number));
            }
        }
        return value;
    }

    /** {@code default}, which several Paper and Spigot integers accept to mean "not set". */
    static boolean isDefaultWord(final Object raw) {
        return raw instanceof final String text && "default".equalsIgnoreCase(text.trim());
    }

    private static boolean bool(final Object raw) {
        if (raw instanceof final Boolean bool) {
            return bool;
        }
        if (raw instanceof final String text) {
            final String trimmed = text.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        throw new InvalidSettingException(describe(raw) + " is not true or false");
    }

    private static long integer(final Object raw) {
        if (raw instanceof final Number number) {
            final double value = number.doubleValue();
            if (value != Math.rint(value) || Double.isInfinite(value)) {
                throw new InvalidSettingException(number + " is not a whole number");
            }
            return number.longValue();
        }
        if (raw instanceof final String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (final NumberFormatException e) {
                throw new InvalidSettingException(describe(raw) + " is not a whole number", e);
            }
        }
        throw new InvalidSettingException(describe(raw) + " is not a whole number");
    }

    private static double number(final Object raw) {
        if (raw instanceof final Number number) {
            return number.doubleValue();
        }
        if (raw instanceof final String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (final NumberFormatException e) {
                throw new InvalidSettingException(describe(raw) + " is not a number", e);
            }
        }
        throw new InvalidSettingException(describe(raw) + " is not a number");
    }

    private static boolean isNumeric(final String text) {
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    private static String scalarString(final Object raw) {
        if (raw instanceof Map<?, ?> || raw instanceof List<?>) {
            throw new InvalidSettingException(describe(raw) + " is not a single value");
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static List<String> stringList(final Object raw) {
        final List<String> list = new ArrayList<>();
        if (raw instanceof final List<?> items) {
            for (final Object item : items) {
                list.add(String.valueOf(item));
            }
            return List.copyOf(list);
        }
        if (raw instanceof final String text) {
            // server.properties keeps a list comma separated.
            for (final String part : text.split(",")) {
                if (!part.isBlank()) {
                    list.add(part.trim());
                }
            }
            return List.copyOf(list);
        }
        if (raw == null) {
            return List.of();
        }
        throw new InvalidSettingException(describe(raw) + " is not a list");
    }

    private static Map<String, Object> table(final Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof final Map<?, ?> map)) {
            throw new InvalidSettingException(describe(raw) + " is not a table");
        }
        final Map<String, Object> copy = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final Object value = entry.getValue();
            copy.put(String.valueOf(entry.getKey()), value instanceof Map<?, ?> nested ? table(nested)
                : value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : value);
        }
        return copy;
    }

    private static String enumValue(final SettingEntry entry, final Object raw) {
        // Vanilla accepts difficulty and gamemode as their numeric ids too.
        if (("server.difficulty".equals(entry.tomlPath()) || "server.gamemode".equals(entry.tomlPath()))) {
            final String text = String.valueOf(raw).trim();
            if (text.matches("\\d+")) {
                final int id = Integer.parseInt(text);
                if (id >= 0 && id < entry.values().size()) {
                    return entry.values().get(id);
                }
                throw new InvalidSettingException(text + " is not one of " + entry.values());
            }
        }
        final String text = scalarString(raw).trim();
        for (final String value : entry.values()) {
            if (value.replace('-', '_').equalsIgnoreCase(text.replace('-', '_'))) {
                return value;
            }
        }
        throw new InvalidSettingException(describe(raw) + " is not one of " + entry.values());
    }

    /**
     * A message, in the TOML's MiniMessage spelling.
     *
     * <p>Spigot and Bukkit keep plain strings with {@code &} or section-sign colour codes, and
     * Spigot translates the ampersands when it reads them; they become MiniMessage tags here.
     * Paper's messages already are MiniMessage, and a message that is nothing but a translation
     * key, {@code <lang:key>}, is written as {@code translate:key}, the form decision 5 chose.</p>
     */
    static String message(final LegacySource source, final String text) {
        if (source == LegacySource.PAPER_GLOBAL || source == LegacySource.PAPER_WORLD) {
            final Matcher lang = LANG_TAG.matcher(text.trim());
            return lang.matches() ? SettingReaders.TRANSLATE_PREFIX + lang.group(1) : text;
        }
        final String sections = AMPERSAND_CODE.matcher(text).replaceAll("§$1");
        if (sections.indexOf('§') < 0) {
            // No colour: MiniMessage only needs the characters it would read as tags escaped.
            return MiniMessage.miniMessage().escapeTags(text);
        }
        final Component component = LegacyComponentSerializer.legacySection().deserialize(sections);
        return MiniMessage.miniMessage().serialize(component);
    }

    /**
     * Brings a value to a form that compares equal to the same value read back from a TOML file:
     * integers as {@code Long}, other numbers as {@code Double}, the words {@code default} and
     * {@code disabled} in lower case, tables as plain maps.
     */
    static Object comparable(final SettingEntry entry, final Object value) {
        final Object plain = value instanceof UnmodifiableConfig ? SettingsCatalog.plain(value) : value;
        return switch (entry.kind()) {
            case DOUBLE, DOUBLE_OR_DEFAULT, DOUBLE_OR_DISABLED -> plain instanceof final Number number
                ? (Object) number.doubleValue() : lowerWord(plain);
            case ENUM -> plain instanceof final String text ? enumValue(entry, text) : plain;
            default -> generic(plain);
        };
    }

    private static Object generic(final Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof final Number number) {
            final double d = number.doubleValue();
            return d == Math.rint(d) && !Double.isInfinite(d) ? (Object) (long) d : (Object) d;
        }
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), generic(nested)));
            return copy;
        }
        if (value instanceof final UnmodifiableConfig config) {
            return generic(SettingsCatalog.plain(config));
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(LegacyValues::generic).toList();
        }
        return lowerWord(value);
    }

    private static Object lowerWord(final Object value) {
        if (value instanceof final String text && ("default".equalsIgnoreCase(text) || "disabled".equalsIgnoreCase(text))) {
            return text.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    /** Whether two values of a setting mean the same. */
    static boolean same(final SettingEntry entry, final Object a, final Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        try {
            return comparable(entry, a).equals(comparable(entry, b));
        } catch (final InvalidSettingException e) {
            return false;
        }
    }

    /** The legacy value as the report shows it, with secrets hidden. */
    static String show(final String path, final Object value) {
        final String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("secret") || lower.contains("password")) {
            return value == null || String.valueOf(value).isEmpty() ? "(empty)" : "(hidden)";
        }
        return describe(value);
    }

    static String describe(final Object value) {
        if (value instanceof final String text) {
            return '"' + text + '"';
        }
        if (value instanceof final UnmodifiableConfig config) {
            return String.valueOf(SettingsCatalog.plain(config));
        }
        return String.valueOf(value);
    }
}
