package io.github.adrielorihuela.legacyfolia.config.properties;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingEntry;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingKind;
import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.github.adrielorihuela.legacyfolia.config.types.SettingReaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Translates between a setting's value in a TOML file and its text in {@code server.properties}.
 *
 * <p>Everything in {@code server.properties} is a string; the TOML files are typed. Going one way
 * the catalog says what type to read the text as, so {@code 20} becomes a number for a number setting
 * and stays the string {@code "20"} for a text one. Going the other way a value is written the way
 * vanilla writes it: booleans and numbers plainly, text as it is, and a list of strings comma
 * separated as vanilla keeps {@code initial-enabled-packs}. A list whose items contain commas, and
 * a table, are written as TOML, which the reading side recognises by the opening bracket.</p>
 *
 * <p>Reading never fails. Text that is not a value of its type comes back as the string it was,
 * and {@link SettingReaders#convert(SettingEntry, Object)} then rejects it with a message that names
 * it, which is what the caller reports.</p>
 */
public final class PropertyValues {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private PropertyValues() {
    }

    /**
     * Reads the text of a value as its setting's type.
     *
     * @param entry the setting
     * @param text the value as it appears in {@code server.properties}, unescaped
     * @return the value as the TOML file would hold it
     */
    public static Object parse(final SettingEntry entry, final String text) {
        return switch (entry.kind()) {
            case STRING, MESSAGE -> text;
            case ENUM, DURATION, DURATION_OR_DISABLED -> text.trim();
            case STRING_LIST -> {
                final String trimmed = text.trim();
                if (trimmed.startsWith("[")) {
                    yield tomlOr(trimmed);
                }
                final List<String> items = new ArrayList<>();
                for (final String item : trimmed.split(",")) {
                    if (!item.isBlank()) {
                        items.add(item.trim());
                    }
                }
                yield List.copyOf(items);
            }
            case TABLE -> {
                final String trimmed = text.trim();
                yield trimmed.isEmpty() ? Map.of() : tomlOr(trimmed);
            }
            default -> scalar(text.trim());
        };
    }

    private static Object tomlOr(final String text) {
        try {
            return TomlText.parseValue(text);
        } catch (final IllegalArgumentException e) {
            return text;
        }
    }

    private static Object scalar(final String text) {
        final String lower = text.toLowerCase(Locale.ROOT);
        if ("true".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower)) {
            return Boolean.FALSE;
        }
        if (INTEGER.matcher(text).matches()) {
            try {
                final long value = Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
                return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (Object) (int) value : (Object) value;
            } catch (final NumberFormatException e) {
                return text;
            }
        }
        if (DECIMAL.matcher(text).matches()) {
            return Double.parseDouble(text);
        }
        return text;
    }

    /**
     * Writes a value as {@code server.properties} text.
     *
     * @param entry the setting
     * @param value the value as the TOML file holds it
     * @return the text, unescaped
     */
    public static String render(final SettingEntry entry, final Object value) {
        final Object plain = SettingsCatalog.plain(value);
        if (plain instanceof final String text) {
            return text;
        }
        if (plain instanceof Boolean || plain instanceof Number) {
            return TomlLiterals.render(plain);
        }
        if (plain instanceof final List<?> list && entry.kind() == SettingKind.STRING_LIST && commaSafe(list)) {
            final StringBuilder out = new StringBuilder();
            for (final Object item : list) {
                out.append(out.isEmpty() ? "" : ",").append(item);
            }
            return out.toString();
        }
        return TomlLiterals.render(plain);
    }

    /** Whether a list survives being written comma separated and split again. */
    private static boolean commaSafe(final List<?> list) {
        for (final Object item : list) {
            if (!(item instanceof final String text) || text.isBlank() || text.contains(",")
                || !text.equals(text.trim()) || list.size() == 1 && text.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether two values mean the same for a setting, so that {@code 1} and {@code 1.0} are the same
     * number to a decimal setting and {@code "Easy"} the same difficulty as {@code "easy"}.
     *
     * @param entry the setting
     * @param a one value, as the TOML file would hold it
     * @param b another
     * @return whether they are equal once read as the setting's type
     */
    public static boolean same(final SettingEntry entry, final Object a, final Object b) {
        final Object plainA = SettingsCatalog.plain(a);
        final Object plainB = SettingsCatalog.plain(b);
        if (Objects.equals(plainA, plainB)) {
            return true;
        }
        try {
            return Objects.equals(SettingReaders.convert(entry, plainA), SettingReaders.convert(entry, plainB));
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
