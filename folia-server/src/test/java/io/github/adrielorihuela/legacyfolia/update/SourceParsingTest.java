package io.github.adrielorihuela.legacyfolia.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What each source's answers mean, against the shapes they actually send. */
class SourceParsingTest {

    @Nested
    @DisplayName("Hangar")
    class Hangar {

        private static String versions(final String name, final String downloads) {
            return "{\"result\":[{\"name\":\"" + name + "\",\"downloads\":" + downloads + "}]}";
        }

        private static String paperDownload(final String file, final String sha) {
            return "{\"PAPER\":{\"downloadUrl\":\"https://hangar/x.jar\",\"fileInfo\":{\"name\":\""
                + file + "\"" + (sha == null ? "" : ",\"sha256Hash\":\"" + sha + "\"") + "}}}";
        }

        @Test
        @DisplayName("the newest build's Paper file is taken, with its SHA-256")
        void readsThePaperDownload() throws PluginUpdateException {
            final PluginRelease release = HangarSource.parse("ViaVersion",
                JsonParser.parseString(versions("5.11.0", paperDownload("ViaVersion-5.11.0.jar",
                    "deadbeef"))));

            assertEquals("5.11.0", release.version());
            assertEquals("ViaVersion-5.11.0.jar", release.fileName());
            assertEquals("SHA-256", release.checksum().algorithm());
            assertEquals("deadbeef", release.checksum().hex());
        }

        @Test
        @DisplayName("a build off the Release channel is labelled, so a beta does not read stable")
        void labelsNonReleaseChannels() throws PluginUpdateException {
            final PluginRelease release = HangarSource.parse("Thing",
                JsonParser.parseString(versions("1.0.0", paperDownload("Thing-1.0.0.jar", "ab"))),
                "Beta");

            assertEquals("1.0.0 (Beta)", release.version());
        }

        @Test
        @DisplayName("an empty channel is its own signal, so the next channel can be tried")
        void anEmptyChannelIsDistinct() {
            assertThrows(HangarSource.EmptyChannelException.class,
                () -> HangarSource.parse("Thing", JsonParser.parseString("{\"result\":[]}")));
        }

        @Test
        @DisplayName("a project that only links elsewhere is refused, having nothing to verify")
        void refusesAnExternalLink() {
            final PluginUpdateException thrown = assertThrows(PluginUpdateException.class,
                () -> HangarSource.parse("Geyser", JsonParser.parseString(
                    versions("1.0", "{\"PAPER\":{\"externalUrl\":\"https://elsewhere/x.jar\"}}"))));

            assertTrue(thrown.getMessage().contains("does not host its file"));
        }

        @Test
        @DisplayName("a build with no Paper download at all is refused")
        void refusesAMissingPaperDownload() {
            assertThrows(PluginUpdateException.class, () -> HangarSource.parse("Thing",
                JsonParser.parseString(versions("1.0", "{\"VELOCITY\":{}}"))));
        }
    }

    @Nested
    @DisplayName("Modrinth")
    class Modrinth {

        private static String version(final String number, final String type, final String files) {
            return "{\"version_number\":\"" + number + "\",\"version_type\":\"" + type
                + "\",\"files\":[" + files + "]}";
        }

        private static String file(final String name, final boolean primary, final String hashes) {
            return "{\"filename\":\"" + name + "\",\"url\":\"https://cdn/" + name
                + "\",\"primary\":" + primary + ",\"hashes\":" + hashes + "}";
        }

        @Test
        @DisplayName("a release is preferred over a newer beta")
        void prefersAReleaseOverANewerBeta() throws PluginUpdateException {
            final PluginRelease found = ModrinthSource.parse("thing", JsonParser.parseString("["
                + version("2.0.0", "beta", file("a.jar", true, "{\"sha512\":\"aa\"}")) + ","
                + version("1.0.0", "release", file("b.jar", true, "{\"sha512\":\"bb\"}")) + "]"));

            assertEquals("1.0.0", found.version(),
                "stability comes before recency, or a server quietly rides development builds");
        }

        @Test
        @DisplayName("a beta is used when there is no release, and labelled")
        void fallsToABetaAndLabelsIt() throws PluginUpdateException {
            final PluginRelease found = ModrinthSource.parse("thing", JsonParser.parseString("["
                + version("2.0.0", "beta", file("a.jar", true, "{\"sha512\":\"aa\"}")) + "]"));

            assertEquals("2.0.0 (beta)", found.version());
        }

        @Test
        @DisplayName("the primary file is chosen over the others")
        void choosesThePrimaryFile() throws PluginUpdateException {
            final PluginRelease found = ModrinthSource.parse("thing", JsonParser.parseString("["
                + version("1.0.0", "release",
                    file("sources.jar", false, "{\"sha512\":\"aa\"}") + ","
                        + file("plugin.jar", true, "{\"sha512\":\"bb\"}")) + "]"));

            assertEquals("plugin.jar", found.fileName());
        }

        @Test
        @DisplayName("SHA-512 is preferred, and SHA-1 is used when it is all there is")
        void prefersTheStrongerDigest() throws PluginUpdateException {
            final PluginRelease strong = ModrinthSource.parse("thing", JsonParser.parseString("["
                + version("1.0.0", "release",
                    file("a.jar", true, "{\"sha512\":\"aa\",\"sha1\":\"bb\"}")) + "]"));
            final PluginRelease weak = ModrinthSource.parse("thing", JsonParser.parseString("["
                + version("1.0.0", "release", file("a.jar", true, "{\"sha1\":\"bb\"}")) + "]"));

            assertEquals("SHA-512", strong.checksum().algorithm());
            assertEquals("SHA-1", weak.checksum().algorithm());
        }

        @Test
        @DisplayName("nothing usable in the list is an error rather than a silent skip")
        void nothingUsableFails() {
            assertThrows(PluginUpdateException.class,
                () -> ModrinthSource.parse("thing", JsonParser.parseString("[]")));
        }
    }

    @Nested
    @DisplayName("LuckPerms")
    class LuckPerms {

        @Test
        @DisplayName("the bukkit download is taken, and there is deliberately no checksum")
        void readsTheBukkitDownload() throws PluginUpdateException {
            final PluginRelease found = LuckPermsSource.parse(JsonParser.parseString(
                "{\"version\":\"5.5.77\",\"downloads\":{\"bukkit\":"
                    + "\"https://ci/LuckPerms-Bukkit-5.5.77.jar\"}}"));

            assertEquals("5.5.77", found.version());
            assertEquals("LuckPerms-Bukkit-5.5.77.jar", found.fileName());
            assertNull(found.checksum(),
                "LuckPerms publishes none, and pretending otherwise would hide that");
        }

        @Test
        @DisplayName("no bukkit download is an error, not an empty install")
        void refusesAMissingDownload() {
            assertThrows(PluginUpdateException.class, () -> LuckPermsSource.parse(
                JsonParser.parseString("{\"version\":\"1\",\"downloads\":{\"velocity\":\"x\"}}")));
        }
    }

    @Nested
    @DisplayName("Naming jars on disk")
    class Naming {

        @Test
        @DisplayName("a plugin's own jars are matched, and a longer name is not")
        void matchesOnlyItsOwnJars() {
            assertTrue(PluginUpdater.isJarOf("ViaVersion", "ViaVersion-5.11.0.jar"));
            assertTrue(PluginUpdater.isJarOf("ViaVersion", "viaversion.jar"));
            assertTrue(!PluginUpdater.isJarOf("ViaVersion", "ViaVersionAddon-1.0.jar"),
                "the dash is what stops one plugin claiming another's jar");
            assertTrue(!PluginUpdater.isJarOf("ViaVersion", "ViaVersion-5.11.0.jar.disabled"));
        }

        @Test
        @DisplayName("the base name comes off the file name, not the version")
        void derivesTheBaseNameFromTheFile() {
            assertEquals("ViaVersion", new PluginRelease("hangar", "x",
                "5.12.0-SNAPSHOT+1053", "ViaVersion-5.12.0-SNAPSHOT.jar", null, "u").baseName(),
                "Hangar's build number and its file name do not agree");
        }

        @Test
        @DisplayName("the guess at an installed jar drops the owner and any regex")
        void lastSegmentStrips() {
            assertEquals("ViaRewind", PluginUpdater.lastSegment("ViaVersion/ViaRewind"));
            assertEquals("ViaRewind", PluginUpdater.lastSegment("ViaVersion/ViaRewind:Bukkit"));
            assertEquals("luckperms", PluginUpdater.lastSegment("luckperms"));
        }
    }
}
