package io.github.adrielorihuela.legacyfolia.config.properties;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes plain Java values as TOML, the way the bundled files write them.
 *
 * <p>Strings use double quotes, tables are written inline as {@code { "key" = value }}, and
 * anything outside printable ASCII is escaped, so a value written into a file keeps the file ASCII
 * like the templates it came from.</p>
 */
public final class TomlLiterals {

    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_-]+");

    private TomlLiterals() {
    }

    /**
     * One value as TOML.
     *
     * @param value a boolean, a number, a string, a list or a map (or a night-config table)
     * @return the literal
     */
    public static String render(final Object value) {
        if (value instanceof final String text) {
            return string(text);
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
            || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof final Number number) {
            final double d = number.doubleValue();
            if (Double.isNaN(d)) {
                return "nan";
            }
            if (Double.isInfinite(d)) {
                return d > 0 ? "inf" : "-inf";
            }
            return Double.toString(d);
        }
        if (value instanceof final List<?> list) {
            final StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                out.append(i == 0 ? "" : ", ").append(render(list.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof final UnmodifiableConfig table) {
            return render(table.valueMap());
        }
        if (value instanceof final Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            final StringBuilder out = new StringBuilder("{ ");
            boolean first = true;
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(first ? "" : ", ").append(key(String.valueOf(entry.getKey())))
                    .append(" = ").append(render(entry.getValue()));
                first = false;
            }
            return out.append(" }").toString();
        }
        return string(String.valueOf(value));
    }

    /** A key: bare when TOML allows it, quoted otherwise. */
    static String key(final String key) {
        return BARE.matcher(key).matches() ? key : string(key);
    }

    /**
     * A basic string, escaped.
     *
     * @param text the text
     * @return it in double quotes
     */
    public static String string(final String text) {
        final StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (cp < 0x20 || cp == 0x7f) {
                        out.append(String.format("\\u%04X", cp));
                    } else if (cp > 0xffff) {
                        out.append(String.format("\\U%08X", cp));
                    } else if (cp > 0x7e) {
                        out.append(String.format("\\u%04X", cp));
                    } else {
                        out.append((char) cp);
                    }
                }
            }
        });
        return out.append('"').toString();
    }
}
