package io.github.adrielorihuela.legacyfolia.config.catalog;

/**
 * The shape a setting's value takes in the TOML files.
 *
 * <p>This is the type as an operator writes it, which is not always the type the server keeps.
 * {@link #DURATION} is a string in the file and a Paper {@code Duration} in memory; an
 * {@link #INT_OR_DISABLED} is either an integer or the word {@code "disabled"} in the file and an
 * {@code IntOr.Disabled} in memory. {@code config.types.SettingReaders} does those conversions.</p>
 */
public enum SettingKind {

    BOOLEAN,

    INT,

    LONG,

    DOUBLE,

    STRING,

    /** A TOML array of strings. Where server.properties keeps a comma separated string, the notes say so. */
    STRING_LIST,

    /** An integer, or {@code "disabled"}: Paper's {@code IntOr.Disabled}. */
    INT_OR_DISABLED,

    /** An integer, or {@code "default"}: Paper's {@code IntOr.Default}. */
    INT_OR_DEFAULT,

    /** A number, or {@code "disabled"}: Paper's {@code DoubleOr.Disabled}. */
    DOUBLE_OR_DISABLED,

    /** A number, or {@code "default"}: Paper's {@code DoubleOr.Default}. */
    DOUBLE_OR_DEFAULT,

    /** {@code true}, {@code false} or {@code "default"}: Paper's {@code BooleanOrDefault}. */
    BOOLEAN_OR_DEFAULT,

    /** A duration such as {@code "12h"} or {@code "2d"}: Paper's {@code Duration}. */
    DURATION,

    /** A duration or {@code "disabled"}: Paper's {@code DurationOrDisabled}. */
    DURATION_OR_DISABLED,

    /** An operator permission level, 0 to 4: vanilla's {@code LevelBasedPermissionSet}. */
    PERMISSION_LEVEL,

    /** A MiniMessage string, or {@code "translate:<key>"} for a translation key. */
    MESSAGE,

    /** One of a fixed set of strings, listed in {@link SettingEntry#values()}. */
    ENUM,

    /** Anti-xray's engine mode, written as its number: 1, 2 or 3. */
    ENGINE_MODE,

    /** A table whose keys are data rather than settings, such as an item id mapped to ticks. */
    TABLE
}
