package io.github.adrielorihuela.legacyfolia.update;

/**
 * Why one plugin could not be brought up to date.
 *
 * <p>Its message is written to be read by an operator and completes the sentence "could not update
 * {@code <plugin>}: ...", because that is the only place it is ever shown.</p>
 */
public sealed class PluginUpdateException extends Exception
    permits HangarSource.EmptyChannelException {

    private static final long serialVersionUID = 1L;

    PluginUpdateException(final String message) {
        super(message);
    }

    PluginUpdateException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
