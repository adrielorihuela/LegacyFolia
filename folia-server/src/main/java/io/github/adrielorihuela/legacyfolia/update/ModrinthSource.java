package io.github.adrielorihuela.legacyfolia.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Plugins from <a href="https://modrinth.com">Modrinth</a>.
 *
 * <p>Asked for both the {@code paper} and {@code folia} loaders. Almost everything that runs on
 * Folia is published as a Paper plugin, and the handful that tag themselves {@code folia} as well
 * would be missed by asking for only one of the two.</p>
 *
 * <p>Modrinth publishes SHA-512 and SHA-1 for every file but never SHA-256, which is why the
 * algorithm travels with the digest rather than being assumed anywhere.</p>
 */
final class ModrinthSource implements PluginSource {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String API = "https://api.modrinth.com/v2/project/";

    private static final String LOADERS = "[\"paper\",\"folia\"]";

    /** Modrinth's release types, most stable first. */
    private static final String[] TYPES_BY_STABILITY = {"release", "beta", "alpha"};

    private final JsonApi api;
    private final String minecraftVersion;

    ModrinthSource(final HttpClient httpClient, final String minecraftVersion) {
        this.api = new JsonApi(httpClient);
        this.minecraftVersion = minecraftVersion;
    }

    @Override
    public String name() {
        return "modrinth";
    }

    @Override
    public PluginRelease resolve(final String slug) throws PluginUpdateException {
        final JsonElement pinned = list(slug, true);
        if (!isEmpty(pinned)) {
            return parse(slug, pinned);
        }

        // Nothing built for this exact version. Rather than refuse, take the plugin's newest build
        // and say so: an operator who listed the plugin wants it installed, and Minecraft point
        // releases rarely break one.
        final JsonElement any = list(slug, false);
        if (isEmpty(any)) {
            throw new PluginUpdateException("Modrinth knows the project but publishes no Paper or "
                + "Folia build of it. It may be a mod rather than a plugin.");
        }
        final PluginRelease release = parse(slug, any);
        LOGGER.warn("Modrinth has no build of {} for {}, so {} is being used instead.",
            slug, this.minecraftVersion, release.version());
        return release;
    }

    private JsonElement list(final String slug, final boolean pinVersion)
        throws PluginUpdateException {
        final String project = URLEncoder.encode(slug, StandardCharsets.UTF_8);
        final StringBuilder url = new StringBuilder(API).append(project).append("/version?loaders=")
            .append(URLEncoder.encode(LOADERS, StandardCharsets.UTF_8));
        if (pinVersion) {
            url.append("&game_versions=").append(URLEncoder.encode(
                "[\"" + this.minecraftVersion + "\"]", StandardCharsets.UTF_8));
        }
        try {
            return this.api.get(url.toString());
        } catch (final JsonApi.NotFoundException e) {
            throw new PluginUpdateException("Modrinth has no project called '" + slug + "'. Use "
                + "the slug from its page's address, not its display name.");
        }
    }

    private static boolean isEmpty(final JsonElement body) {
        return !body.isJsonArray() || body.getAsJsonArray().isEmpty();
    }

    /** Split out from the request so version selection can be tested without the network. */
    static PluginRelease parse(final String slug, final JsonElement body)
        throws PluginUpdateException {
        if (!body.isJsonArray()) {
            throw new PluginUpdateException("Modrinth's answer was not the JSON array expected.");
        }
        final JsonArray versions = body.getAsJsonArray();

        // The array arrives newest first, so the first entry of the most stable type present is
        // the newest build of that type.
        for (final String type : TYPES_BY_STABILITY) {
            for (final JsonElement element : versions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject version = element.getAsJsonObject();
                if (!type.equals(JsonApi.string(version, "version_type"))) {
                    continue;
                }
                return read(slug, version, type);
            }
        }

        throw new PluginUpdateException("none of Modrinth's builds of " + slug + " are a release, "
            + "beta or alpha.");
    }

    private static PluginRelease read(final String slug, final JsonObject version,
                                      final String type) throws PluginUpdateException {
        final JsonElement files = version.get("files");
        if (files == null || !files.isJsonArray() || files.getAsJsonArray().isEmpty()) {
            throw new PluginUpdateException("Modrinth's newest build of " + slug + " has no files.");
        }

        JsonObject chosen = null;
        for (final JsonElement element : files.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject file = element.getAsJsonObject();
            if (chosen == null) {
                chosen = file;
            }
            final JsonElement primary = file.get("primary");
            if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) {
                chosen = file;
                break;
            }
        }
        if (chosen == null) {
            throw new PluginUpdateException("Modrinth's newest build of " + slug + " has no files.");
        }

        final String fileName = JsonApi.string(chosen, "filename");
        final String url = JsonApi.string(chosen, "url");
        if (fileName == null || url == null) {
            throw new PluginUpdateException("Modrinth's answer was missing the file name or URL.");
        }

        final String versionNumber = JsonApi.string(version, "version_number");
        final String label = versionNumber == null ? "unknown" : versionNumber;
        return new PluginRelease("modrinth", slug,
            "release".equals(type) ? label : label + " (" + type + ")",
            fileName, checksumOf(chosen), url);
    }

    /**
     * The strongest digest Modrinth published for a file.
     *
     * <p>SHA-512 for preference; SHA-1 is what older files carry and is still far better than
     * trusting the file name.</p>
     */
    private static PluginRelease.@Nullable Checksum checksumOf(final JsonObject file) {
        final JsonObject hashes = JsonApi.object(file, "hashes");
        final String sha512 = JsonApi.string(hashes, "sha512");
        if (sha512 != null) {
            return new PluginRelease.Checksum("SHA-512", sha512);
        }
        final String sha1 = JsonApi.string(hashes, "sha1");
        return sha1 == null ? null : new PluginRelease.Checksum("SHA-1", sha1);
    }
}
