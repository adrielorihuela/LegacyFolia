package io.github.adrielorihuela.legacyfolia.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The notice about GitHub's rate limit must agree with the token the request really carries. */
class PluginUpdatesTest {

    private static final List<PluginUpdater.Wanted> GITHUB = List.of(new PluginUpdater.Wanted("github", "a/b"));
    private static final List<PluginUpdater.Wanted> HANGAR = List.of(new PluginUpdater.Wanted("hangar", "x"));

    @Test
    @DisplayName("no notice when only GITHUB_TOKEN holds a token")
    void environmentTokenSilencesTheNotice() {
        assertFalse(PluginUpdates.noTokenNotice(GITHUB, null, "ghp_env"));
    }

    @Test
    @DisplayName("no notice when only the token file holds a token")
    void fileTokenSilencesTheNotice() {
        assertFalse(PluginUpdates.noTokenNotice(GITHUB, "ghp_file", null));
        assertFalse(PluginUpdates.noTokenNotice(GITHUB, "ghp_file", "  "));
    }

    @Test
    @DisplayName("the notice is given when a GitHub plugin is listed and no token exists anywhere")
    void noTokenAnywhere() {
        assertTrue(PluginUpdates.noTokenNotice(GITHUB, null, null));
        assertTrue(PluginUpdates.noTokenNotice(GITHUB, "", ""));
    }

    @Test
    @DisplayName("the notice is not given when no GitHub plugin is listed")
    void onlyForGithub() {
        assertFalse(PluginUpdates.noTokenNotice(HANGAR, null, null));
    }

    @Test
    @DisplayName("the environment variable wins over the file and both are trimmed")
    void environmentWins() {
        assertEquals("env", GitHubSource.effectiveToken("file", " env "));
        assertEquals("file", GitHubSource.effectiveToken(" file ", null));
        assertNull(GitHubSource.effectiveToken(null, null));
    }
}
