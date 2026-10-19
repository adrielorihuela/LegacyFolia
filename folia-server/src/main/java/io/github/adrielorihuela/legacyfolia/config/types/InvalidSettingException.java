package io.github.adrielorihuela.legacyfolia.config.types;

/**
 * A value in a configuration file that is not a legal value of its setting.
 *
 * <p>Unchecked, because a caller that wants to recover usually does so in one place — log the
 * problem and keep the default — and every other caller would only rethrow it.</p>
 */
public final class InvalidSettingException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidSettingException(final String message) {
        super(message);
    }

    public InvalidSettingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
