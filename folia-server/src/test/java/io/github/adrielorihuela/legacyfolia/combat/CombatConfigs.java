package io.github.adrielorihuela.legacyfolia.combat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

/**
 * Builds a {@link CombatConfig} that differs from another in named components, so a test does not
 * have to spell out every positional argument of the record and break each time one is added.
 */
final class CombatConfigs {

    private CombatConfigs() {
    }

    /**
     * A copy of the settings with some components changed.
     *
     * @param config the settings to start from
     * @param changes pairs of component name and value
     * @return the changed copy
     */
    static CombatConfig with(final CombatConfig config, final Object... changes) {
        try {
            final RecordComponent[] components = CombatConfig.class.getRecordComponents();
            final Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                values[i] = components[i].getAccessor().invoke(config);
            }
            for (int c = 0; c < changes.length; c += 2) {
                boolean found = false;
                for (int i = 0; i < components.length; i++) {
                    if (components[i].getName().equals(changes[c])) {
                        values[i] = changes[c + 1];
                        found = true;
                    }
                }
                if (!found) {
                    throw new AssertionError("no component " + changes[c]);
                }
            }
            final Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
            return CombatConfig.class.getDeclaredConstructor(types).newInstance(values);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
