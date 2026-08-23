package io.github.adrielorihuela.legacyfolia.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;

/**
 * Plugins attached to a GitHub project's releases.
 *
 * <p>Entries are {@code owner/repo}, optionally {@code owner/repo:regex} to choose between several
 * jars. The owner cannot be left out: nothing in the API can guess it.</p>
 *
 * <p>The whole release list is walked from newest to oldest rather than only
 * {@code /releases/latest}, which never returns a pre-release. Plenty of plugins ship betas for
 * months between stable tags, and a project whose newest tag happens to be a pre-release would
 * otherwise look to this as though it had published nothing at all. Drafts are skipped, because
 * they are not published; a pre-release is labelled as one in the log so a beta never looks like a
 * stable build.</p>
 *
 * <p>GitHub allows sixty unauthenticated requests an hour per address, which is plenty for a few
 * plugins at start-up but not for a machine that restarts constantly. A token raises that to five
 * thousand. It comes from the file named by {@code github-token-file}, or from a
 * {@code GITHUB_TOKEN} environment variable, which wins when both are set. The token is kept in a
 * file of its own rather than in the configuration, the same way the forwarding secret is.</p>
 */
final class GitHubSource implements PluginSource {

    private static final String API = "https://api.github.com/repos/";

    /** Enough to reach past a run of pre-releases without paging through a decade of history. */
    private static final int RELEASES_TO_CONSIDER = 30;

    /** Attached to sources and javadoc jars, which are never the plugin. */
    private static final Pattern NOT_A_PLUGIN =
        Pattern.compile("sources|javadoc", Pattern.CASE_INSENSITIVE);

    private final JsonApi api;
    private final @Nullable String token;

    GitHubSource(final HttpClient httpClient, final @Nullable String token) {
        this.api = new JsonApi(httpClient);
        final String fromEnvironment = System.getenv("GITHUB_TOKEN");
        final String chosen = fromEnvironment != null && !fromEnvironment.isBlank()
            ? fromEnvironment : token;
        this.token = chosen == null || chosen.isBlank() ? null : chosen.trim();
    }

    @Override
    public String name() {
        return "github";
    }

    /** Whether a token was found, so start-up can say once whether the low rate limit applies. */
    boolean hasToken() {
        return this.token != null;
    }

    @Override
    public PluginRelease resolve(final String spec) throws PluginUpdateException {
        final String repo = repositoryOf(spec);
        final String assetPattern = patternOf(spec);

        final Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        if (this.token != null) {
            headers.put("Authorization", "Bearer " + this.token);
        }

        final JsonElement body;
        try {
            body = this.api.get(API + repo + "/releases?per_page=" + RELEASES_TO_CONSIDER, headers);
        } catch (final JsonApi.NotFoundException e) {
            throw new PluginUpdateException("GitHub has no repository '" + repo + "', or it is "
                + "private and no token was given.");
        }

        // The download carries the same authorization as the lookup. Without it a private
        // repository cannot be reached at all, and neither can a public one belonging to an
        // account GitHub has flagged: github.com paths answer 404 to everybody, token or not,
        // while the API keeps serving the file.
        final Map<String, String> downloadHeaders = new HashMap<>();
        downloadHeaders.put("Accept", "application/octet-stream");
        if (this.token != null) {
            downloadHeaders.put("Authorization", "Bearer " + this.token);
        }
        return parse(repo, assetPattern, body, downloadHeaders);
    }

    /**
     * The {@code owner/repo} part of an entry.
     *
     * @param spec the configuration entry
     * @return the repository
     * @throws PluginUpdateException if it is not in {@code owner/repo} form
     */
    static String repositoryOf(final String spec) throws PluginUpdateException {
        final int colon = spec.indexOf(':');
        final String repo = colon == -1 ? spec : spec.substring(0, colon);
        final int slash = repo.indexOf('/');
        if (slash <= 0 || slash == repo.length() - 1 || repo.indexOf('/', slash + 1) != -1) {
            throw new PluginUpdateException("'" + spec + "' is not a GitHub repository. Write it "
                + "as owner/repo, for example ViaVersion/ViaVersion - the owner cannot be guessed.");
        }
        return repo;
    }

    /** The optional {@code :regex} part of an entry, or null. */
    static @Nullable String patternOf(final String spec) {
        final int colon = spec.indexOf(':');
        if (colon == -1 || colon == spec.length() - 1) {
            return null;
        }
        return spec.substring(colon + 1);
    }

    /** Split out from the request so release and asset selection can be tested without a network. */
    static PluginRelease parse(final String repo, final @Nullable String assetPattern,
                               final JsonElement body) throws PluginUpdateException {
        return parse(repo, assetPattern, body, Map.of());
    }

    static PluginRelease parse(final String repo, final @Nullable String assetPattern,
                               final JsonElement body, final Map<String, String> downloadHeaders)
        throws PluginUpdateException {
        if (!body.isJsonArray()) {
            throw new PluginUpdateException("GitHub's answer was not the JSON array expected.");
        }
        final JsonArray releases = body.getAsJsonArray();
        if (releases.isEmpty()) {
            throw new PluginUpdateException("GitHub has no published releases of " + repo + ".");
        }

        final Pattern chooser;
        try {
            chooser = assetPattern == null ? null : Pattern.compile(assetPattern);
        } catch (final PatternSyntaxException e) {
            throw new PluginUpdateException("'" + assetPattern + "' is not a valid regular "
                + "expression.", e);
        }

        boolean sawADraft = false;
        for (final JsonElement element : releases) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject release = element.getAsJsonObject();
            if (isTrue(release, "draft")) {
                // Not published: its assets are not downloadable without a token, and it is not
                // something the author has released.
                sawADraft = true;
                continue;
            }
            final PluginRelease found =
                firstJarIn(repo, release, chooser, downloadHeaders);
            if (found != null) {
                return found;
            }
        }

        throw new PluginUpdateException(assetPattern == null
            ? "none of the last " + releases.size() + " releases of " + repo + " has a .jar "
                + "attached" + (sawADraft ? " (drafts are skipped)" : "") + "."
            : "no .jar in the last " + releases.size() + " releases of " + repo + " matches '"
                + assetPattern + "'.");
    }

    /** The first asset of a release that looks like the plugin, or null if it has none. */
    private static @Nullable PluginRelease firstJarIn(final String repo, final JsonObject release,
                                                      final @Nullable Pattern chooser,
                                                      final Map<String, String> downloadHeaders) {
        final JsonElement assets = release.get("assets");
        if (assets == null || !assets.isJsonArray()) {
            return null;
        }

        for (final JsonElement element : (JsonArray) assets) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject asset = element.getAsJsonObject();
            final String fileName = JsonApi.string(asset, "name");
            // The API's own asset URL, not browser_download_url: this one honours the
            // Authorization header, and the browser path does not.
            final String url = JsonApi.string(asset, "url");
            if (fileName == null || url == null
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                || NOT_A_PLUGIN.matcher(fileName).find()
                || (chooser != null && !chooser.matcher(fileName).find())) {
                continue;
            }

            // Newer GitHub releases carry "digest": "sha256:<hex>". Older ones carry nothing, and
            // then the file name is all there is to go on.
            final String digest = JsonApi.string(asset, "digest");
            final PluginRelease.Checksum checksum = digest != null && digest.startsWith("sha256:")
                ? new PluginRelease.Checksum("SHA-256", digest.substring("sha256:".length()))
                : null;

            return new PluginRelease("github", repo, versionOf(release), fileName, checksum, url,
                downloadHeaders);
        }
        return null;
    }

    /** The tag, without a leading {@code v}, marked as a pre-release when it is one. */
    private static String versionOf(final JsonObject release) {
        final String tag = JsonApi.string(release, "tag_name");
        final String version = tag == null ? "unknown"
            : tag.startsWith("v") ? tag.substring(1) : tag;
        return isTrue(release, "prerelease") ? version + " (pre-release)" : version;
    }

    private static boolean isTrue(final JsonObject object, final String member) {
        final JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
