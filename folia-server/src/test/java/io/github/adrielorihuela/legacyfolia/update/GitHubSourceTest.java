package io.github.adrielorihuela.legacyfolia.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Choosing a release and an asset is where this can quietly install the wrong file, so the rules
 * are pinned down against the shapes GitHub actually answers with.
 */
class GitHubSourceTest {

    private static PluginRelease parse(final String json, final String pattern)
        throws PluginUpdateException {
        return GitHubSource.parse("owner/repo", pattern, JsonParser.parseString(json));
    }

    private static String release(final String tag, final boolean prerelease, final boolean draft,
                                  final String assets) {
        return "{\"tag_name\":\"" + tag + "\",\"prerelease\":" + prerelease
            + ",\"draft\":" + draft + ",\"assets\":[" + assets + "]}";
    }

    private static String asset(final String name) {
        return "{\"name\":\"" + name + "\",\"url\":\"https://api.github.com/a/" + name + "\"}";
    }

    @Test
    @DisplayName("the newest release with a jar wins")
    void takesTheNewestReleaseWithAJar() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v2.0.0", false, false, asset("Thing-2.0.0.jar"))
            + "," + release("v1.0.0", false, false, asset("Thing-1.0.0.jar")) + "]", null);

        assertEquals("2.0.0", found.version());
        assertEquals("Thing-2.0.0.jar", found.fileName());
    }

    @Test
    @DisplayName("a pre-release is accepted, and labelled so it does not read as stable")
    void acceptsPreReleasesAndSaysSo() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v2.0.0-beta", true, false, asset("Thing-2.0.0-beta.jar"))
            + "," + release("v1.0.0", false, false, asset("Thing-1.0.0.jar")) + "]", null);

        assertEquals("2.0.0-beta (pre-release)", found.version(),
            "this is the whole reason the release list is walked instead of asking for latest");
    }

    @Test
    @DisplayName("drafts are skipped, because they are not published")
    void skipsDrafts() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v3.0.0", false, true, asset("Thing-3.0.0.jar"))
            + "," + release("v2.0.0", false, false, asset("Thing-2.0.0.jar")) + "]", null);

        assertEquals("2.0.0", found.version());
    }

    @Test
    @DisplayName("a release with no jar is passed over for an older one that has one")
    void fallsPastReleasesWithoutAJar() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v3.0.0", false, false, asset("notes.txt"))
            + "," + release("v2.0.0", false, false, asset("Thing-2.0.0.jar")) + "]", null);

        assertEquals("2.0.0", found.version());
    }

    @Test
    @DisplayName("sources and javadoc jars are never the plugin")
    void ignoresSourcesAndJavadocJars() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v1.0.0", false, false,
            asset("Thing-1.0.0-sources.jar") + "," + asset("Thing-1.0.0-javadoc.jar") + ","
                + asset("Thing-1.0.0.jar")) + "]", null);

        assertEquals("Thing-1.0.0.jar", found.fileName());
    }

    @Test
    @DisplayName("a regex picks between several jars in one release")
    void aPatternChoosesBetweenJars() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v1.0.0", false, false,
            asset("Thing-Velocity-1.0.0.jar") + "," + asset("Thing-Bukkit-1.0.0.jar")) + "]",
            "Bukkit");

        assertEquals("Thing-Bukkit-1.0.0.jar", found.fileName());
    }

    @Test
    @DisplayName("the API asset URL is used, since only it honours an authorization header")
    void usesTheApiAssetUrl() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v1.0.0", false, false,
            "{\"name\":\"Thing-1.0.0.jar\",\"url\":\"https://api.github.com/assets/1\","
                + "\"browser_download_url\":\"https://github.com/owner/repo/releases/x.jar\"}")
            + "]", null);

        assertTrue(found.downloadUrl().startsWith("https://api.github.com/"),
            "the browser path answers 404 for private and flagged accounts, token or not");
    }

    @Test
    @DisplayName("a sha256 digest is read when GitHub publishes one")
    void readsTheDigest() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v1.0.0", false, false,
            "{\"name\":\"Thing.jar\",\"url\":\"https://api.github.com/a\","
                + "\"digest\":\"sha256:abc123\"}") + "]", null);

        assertEquals("SHA-256", found.checksum().algorithm());
        assertEquals("abc123", found.checksum().hex());
    }

    @Test
    @DisplayName("an older release without a digest is installed unverified rather than refused")
    void toleratesAMissingDigest() throws PluginUpdateException {
        final PluginRelease found = parse("[" + release("v1.0.0", false, false,
            asset("Thing.jar")) + "]", null);

        assertNull(found.checksum());
    }

    @Test
    @DisplayName("an entry without an owner is refused, because it cannot be guessed")
    void refusesAnEntryWithoutAnOwner() {
        assertThrows(PluginUpdateException.class, () -> GitHubSource.repositoryOf("ViaVersion"));
    }

    @Test
    @DisplayName("owner/repo and owner/repo:regex are split apart")
    void splitsTheEntry() throws PluginUpdateException {
        assertEquals("a/b", GitHubSource.repositoryOf("a/b"));
        assertEquals("a/b", GitHubSource.repositoryOf("a/b:Bukkit"));
        assertNull(GitHubSource.patternOf("a/b"));
        assertEquals("Bukkit", GitHubSource.patternOf("a/b:Bukkit"));
    }

    @Test
    @DisplayName("no jar anywhere in the list is an error, not a silent skip")
    void noJarAtAllFails() {
        assertThrows(PluginUpdateException.class,
            () -> parse("[" + release("v1.0.0", false, false, asset("notes.txt")) + "]", null));
    }
}
