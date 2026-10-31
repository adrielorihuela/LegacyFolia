package io.github.adrielorihuela.legacyfolia.config.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compares two YAML trees value by value, the way a plugin reading them would see them. */
final class Leaves {

    private Leaves() {
    }

    /** Every value that is not a section, by its dotted path, sorted. */
    static Map<String, Object> of(final ConfigurationSection config) {
        final Map<String, Object> leaves = new TreeMap<>();
        for (final Map.Entry<String, Object> entry : config.getValues(true).entrySet()) {
            final Object value = entry.getValue();
            if (value instanceof final ConfigurationSection section) {
                if (section.getKeys(false).isEmpty()) {
                    leaves.put(entry.getKey(), Map.of());
                }
            } else {
                leaves.put(entry.getKey(), normalise(value));
            }
        }
        return leaves;
    }

    static Map<String, Object> of(final File file) {
        return of(YamlConfiguration.loadConfiguration(file));
    }

    /** Numbers by value, so an {@code Integer} 1 and a {@code Long} 1 read the same, as Bukkit reads them. */
    private static Object normalise(final Object value) {
        if (value instanceof final Number number) {
            return number.doubleValue() == Math.rint(number.doubleValue()) && !(value instanceof Double || value instanceof Float)
                ? (Object) number.longValue() : (Object) number.doubleValue();
        }
        if (value instanceof final List<?> list) {
            final List<Object> copy = new ArrayList<>();
            list.forEach(element -> copy.add(normalise(element)));
            return copy;
        }
        return value;
    }

    /**
     * Asserts that two trees hold the same values, except for the paths listed, and reports every
     * difference at once rather than the first.
     */
    static void assertSame(final Map<String, Object> expected, final Map<String, Object> actual, final Set<String> except, final String what) {
        // A comparison of two empty trees would pass for the wrong reason.
        if (expected.size() < 3) {
            fail(what + " holds only " + expected.size() + " value(s); the reference did not load");
        }
        final Set<String> paths = new TreeSet<>(expected.keySet());
        paths.addAll(actual.keySet());
        final List<String> differences = new ArrayList<>();
        for (final String path : paths) {
            if (except.contains(path)) {
                continue;
            }
            final Object want = expected.getOrDefault(path, "<absent>");
            final Object got = actual.getOrDefault(path, "<absent>");
            if (!want.equals(got)) {
                differences.add(path + ": expected " + want + " but the bridge gives " + got);
            }
        }
        if (!differences.isEmpty()) {
            fail(what + " differs in " + differences.size() + " place(s):\n  " + String.join("\n  ", differences));
        }
        assertEquals(0, differences.size());
    }
}
