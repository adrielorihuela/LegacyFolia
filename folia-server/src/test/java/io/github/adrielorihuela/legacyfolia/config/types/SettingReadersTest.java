package io.github.adrielorihuela.legacyfolia.config.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.adrielorihuela.legacyfolia.config.catalog.SettingsCatalog;
import io.papermc.paper.configuration.type.BooleanOrDefault;
import io.papermc.paper.configuration.type.DurationOrDisabled;
import io.papermc.paper.configuration.type.EngineMode;
import io.papermc.paper.configuration.type.number.IntOr;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each reader is pinned down on what it must accept and on what it must refuse, because the hook-up
 * hands their results straight to Paper and vanilla: a value that slips through here is a value
 * those classes would never have been given.
 */
class SettingReadersTest {

    @Test
    @DisplayName("\"disabled\" or an integer")
    void intOrDisabled() {
        assertEquals(new IntOr.Disabled(OptionalInt.of(300)), SettingReaders.intOrDisabled(300));
        assertEquals(new IntOr.Disabled(OptionalInt.of(300)), SettingReaders.intOrDisabled("300"));
        assertSame(IntOr.Disabled.DISABLED, SettingReaders.intOrDisabled("disabled"));
        assertSame(IntOr.Disabled.DISABLED, SettingReaders.intOrDisabled("DISABLED"));
        assertEquals(-1, SettingReaders.intOrDisabled(-1).intValue(), "only an explicit annotation maps negatives to disabled");

        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDisabled("off"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDisabled(2.5));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDisabled("2.5"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDisabled(true));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDisabled(List.of(1)));
    }

    @Test
    @DisplayName("\"default\" or an integer")
    void intOrDefault() {
        assertSame(IntOr.Default.USE_DEFAULT, SettingReaders.intOrDefault("default"));
        assertEquals(6, SettingReaders.intOrDefault(6).intValue());
        assertEquals(6, SettingReaders.intOrDefault(6.0).intValue(), "a whole float is still a whole number");

        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDefault("disabled"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.intOrDefault(""));
    }

    @Test
    @DisplayName("\"disabled\" or \"default\" or a number")
    void doubleOr() {
        assertEquals(4.0, SettingReaders.doubleOrDisabled(4.0).doubleValue());
        assertEquals(4.0, SettingReaders.doubleOrDisabled(4).doubleValue());
        assertFalse(SettingReaders.doubleOrDisabled("disabled").enabled());
        assertTrue(SettingReaders.doubleOrDefault("default").value().isEmpty());
        assertEquals(0.25, SettingReaders.doubleOrDefault(0.25).doubleValue());

        assertThrows(InvalidSettingException.class, () -> SettingReaders.doubleOrDisabled("default"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.doubleOrDefault("half"));
    }

    @Test
    @DisplayName("true, false or \"default\"")
    void booleanOrDefault() {
        assertSame(BooleanOrDefault.USE_DEFAULT, SettingReaders.booleanOrDefault("default"));
        assertEquals(Boolean.TRUE, SettingReaders.booleanOrDefault(true).value());
        assertEquals(Boolean.FALSE, SettingReaders.booleanOrDefault("false").value());
        assertEquals(Boolean.TRUE, SettingReaders.booleanOrDefault("TRUE").value());

        assertThrows(InvalidSettingException.class, () -> SettingReaders.booleanOrDefault("yes"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.booleanOrDefault(1));
    }

    @Test
    @DisplayName("durations, with and without \"disabled\"")
    void durations() {
        assertEquals(12 * 3600, SettingReaders.duration("12h").seconds());
        assertEquals(2 * 86400, SettingReaders.duration("2d").seconds());
        assertEquals(90, SettingReaders.duration("90s").seconds());
        assertEquals(30, SettingReaders.duration("30").seconds(), "no unit means seconds, as in Paper");
        assertEquals(600, SettingReaders.duration("10m").ticks() / 20);
        assertEquals("12h", SettingReaders.duration("12h").value(), "Paper keeps the text it was given");

        assertSame(DurationOrDisabled.USE_DISABLED, SettingReaders.durationOrDisabled("disabled"));
        assertEquals(3600, SettingReaders.durationOrDisabled("1h").value().orElseThrow().seconds());

        // Paper's parser would read every one of these as zero seconds.
        assertThrows(InvalidSettingException.class, () -> SettingReaders.duration("twelve hours"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.duration("12w"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.duration(""));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.duration(3600));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.duration("disabled"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.durationOrDisabled("never"));
    }

    @Test
    @DisplayName("ticks become a Paper duration and come back")
    void durationFromTicks() {
        assertEquals(200, SettingReaders.durationFromTicks(200).ticks());
        assertEquals("10s", SettingReaders.durationFromTicks(200).value());
        assertEquals(0, SettingReaders.durationFromTicks(19).ticks(), "Paper durations are whole seconds");
        assertThrows(InvalidSettingException.class, () -> SettingReaders.durationFromTicks(-1));
    }

    @Test
    @DisplayName("permission levels 0 to 4, and nothing else")
    void permissionLevels() {
        assertSame(LevelBasedPermissionSet.OWNER, SettingReaders.permissionLevel(4));
        assertSame(LevelBasedPermissionSet.GAMEMASTER, SettingReaders.permissionLevel(2));
        assertSame(LevelBasedPermissionSet.ALL, SettingReaders.permissionLevel("0"));
        assertEquals(PermissionLevel.ADMINS, SettingReaders.permissionLevel(3).level());

        assertThrows(InvalidSettingException.class, () -> SettingReaders.permissionLevel(5), "vanilla would clamp to owner");
        assertThrows(InvalidSettingException.class, () -> SettingReaders.permissionLevel(-1));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.permissionLevel("owner"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.permissionLevel(2.5));
    }

    @Test
    @DisplayName("messages are MiniMessage, and translate: makes a translation key")
    void messages() {
        final Component flying = SettingReaders.message("translate:multiplayer.disconnect.flying");
        assertTrue(flying instanceof TranslatableComponent);
        assertEquals("multiplayer.disconnect.flying", ((TranslatableComponent) flying).key());

        final Component red = SettingReaders.message("<red>No.");
        assertEquals(NamedTextColor.RED, red.color());
        assertEquals(Component.text("Server closed"), SettingReaders.message("Server closed"));
        assertEquals("§cNo.", SettingReaders.legacyMessage("<red>No."));
        assertEquals("Outdated client! Please use {0}", SettingReaders.legacyMessage("Outdated client! Please use {0}"));

        assertThrows(InvalidSettingException.class, () -> SettingReaders.message("translate:"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.message("translate:two words"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.message(42));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.message(null));
    }

    @Test
    @DisplayName("anti-xray engine modes by number")
    void engineModes() {
        assertSame(EngineMode.HIDE, SettingReaders.engineMode(1));
        assertSame(EngineMode.OBFUSCATE, SettingReaders.engineMode(2));
        assertSame(EngineMode.OBFUSCATE_LAYER, SettingReaders.engineMode(3L));

        assertThrows(InvalidSettingException.class, () -> SettingReaders.engineMode(0));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.engineMode(4));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.engineMode("hide"));
    }

    private enum Shape { ELLIPSOID, CUBOID }

    @Test
    @DisplayName("enum names, loosely matched")
    void enums() {
        assertSame(Shape.CUBOID, SettingReaders.enumValue("cuboid", Shape.class));
        assertSame(Shape.ELLIPSOID, SettingReaders.enumValue(" ELLIPSOID ", Shape.class));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.enumValue("sphere", Shape.class));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.enumValue(1, Shape.class));
    }

    @Test
    @DisplayName("convert checks a value against its catalog entry")
    void convertAgainstTheCatalog() {
        final SettingsCatalog catalog = SettingsCatalog.get();
        assertEquals(20, SettingReaders.convert(catalog.require("server.max-players"), 20));
        assertEquals(0.5, SettingReaders.convert(catalog.require("gameplay.mobs.baby-zombie-movement-modifier"), 0.5));
        assertEquals(1.0, SettingReaders.convert(catalog.require("gameplay.mobs.baby-zombie-movement-modifier"), 1));
        assertEquals("hard", SettingReaders.convert(catalog.require("server.difficulty"), "HARD"));
        assertEquals("alternate_current",
            SettingReaders.convert(catalog.require("gameplay.redstone.redstone-implementation"), "alternate-current"));
        assertSame(LevelBasedPermissionSet.OWNER, SettingReaders.convert(catalog.require("server.access.op-permission-level"), 4));
        assertEquals(Map.of(), SettingReaders.convert(catalog.require("server.saving.forced-stats"), Map.of()));

        final InvalidSettingException wrong = assertThrows(InvalidSettingException.class,
            () -> SettingReaders.convert(catalog.require("server.max-players"), "twenty"));
        assertTrue(wrong.getMessage().startsWith("server.max-players: "), wrong.getMessage());
        assertThrows(InvalidSettingException.class, () -> SettingReaders.convert(catalog.require("server.difficulty"), "brutal"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.convert(catalog.require("server.hardcore"), "true"));
        assertThrows(InvalidSettingException.class, () -> SettingReaders.convert(catalog.require("server.max-players"), 3_000_000_000L));
        assertThrows(InvalidSettingException.class,
            () -> SettingReaders.convert(catalog.require("server.saving.disabled-advancements"), List.of(1, 2)));
    }
}
