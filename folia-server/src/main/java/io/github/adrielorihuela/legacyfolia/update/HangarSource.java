package io.github.adrielorihuela.legacyfolia.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Plugins from <a href="https://hangar.papermc.io">Hangar</a>.
 *
 * <p>The query pins two things down. {@code platform=PAPER} is what Folia plugins are published
 * under; there is no Folia platform on Hangar, because a Folia plugin is a Paper plugin that
 * declares {@code folia-supported}. The channel is not obvious and matters: unfiltered, Hangar
 * answers with whatever is newest, which for an actively developed plugin is a snapshot. A server
 * that quietly updates itself onto development builds is not what anybody asked for.</p>
 */
final class HangarSource implements PluginSource {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final String API = "https://hangar.papermc.io/api/v1/projects/";

    /**
     * Hangar's channels, most stable first.
     *
     * <p>Taken in order, and the newest build on the first channel that has any is the one
     * installed. Falling down the list matters: Simple Voice Chat has published builds only on
     * Alpha and Beta for long stretches, and stopping at Release would mean refusing to install a
     * plugin that exists and works.</p>
     */
    private static final String[] CHANNELS_BY_STABILITY = {"Release", "Beta", "Alpha", "Snapshot"};

    private final JsonApi api;
    private final String minecraftVersion;

    HangarSource(final HttpClient httpClient, final String minecraftVersion) {
        this.api = new JsonApi(httpClient);
        this.minecraftVersion = minecraftVersion;
    }

    @Override
    public String name() {
        return "hangar";
    }

    @Override
    public PluginRelease resolve(final String slug) throws PluginUpdateException {
        try {
            return resolve(slug, true);
        } catch (final PluginUpdateException versionSpecific) {
            // Nothing built for this exact version. Rather than refuse, fall back to whatever the
            // plugin's newest build is and say so: Minecraft point releases rarely break plugins,
            // and an operator who listed the plugin wants it installed.
            final PluginRelease release = resolve(slug, false);
            LOGGER.warn("Hangar has no {} build of {} for {}, so {} is being used instead.",
                "Paper", slug, this.minecraftVersion, release.version());
            return release;
        }
    }

    private PluginRelease resolve(final String slug, final boolean pinVersion)
        throws PluginUpdateException {
        final String project = URLEncoder.encode(slug, StandardCharsets.UTF_8);
        final String versionFilter = pinVersion
            ? "&platformVersion=" + URLEncoder.encode(this.minecraftVersion, StandardCharsets.UTF_8)
            : "";
        PluginUpdateException lastFailure = null;

        for (final String channel : CHANNELS_BY_STABILITY) {
            final JsonElement body;
            try {
                body = this.api.get(API + project + "/versions?limit=1&platform=PAPER&channel="
                    + channel + versionFilter);
            } catch (final JsonApi.NotFoundException e) {
                throw new PluginUpdateException("Hangar has no project by that name. Check the "
                    + "spelling against the project's page on hangar.papermc.io.");
            }
            try {
                return parse(slug, body, channel);
            } catch (final EmptyChannelException e) {
                continue;
            } catch (final PluginUpdateException e) {
                // A real problem with this build rather than an empty channel -- an external link,
                // say. Remember it, but keep looking: a lower channel may be perfectly installable.
                lastFailure = e;
            }
        }

        throw lastFailure != null ? lastFailure
            : new PluginUpdateException("Hangar knows the project but publishes no Paper build of "
                + "it on any channel" + (pinVersion ? " for " + this.minecraftVersion : "") + ".");
    }

    /** Thrown when a channel simply has nothing, which is not a failure worth reporting. */
    static final class EmptyChannelException extends PluginUpdateException {
        private static final long serialVersionUID = 1L;

        EmptyChannelException() {
            super("this channel has no Paper build.");
        }
    }

    /** Split out from the request so the shape of a real answer can be tested without the network. */
    static PluginRelease parse(final String slug, final JsonElement body)
        throws PluginUpdateException {
        return parse(slug, body, "Release");
    }

    static PluginRelease parse(final String slug, final JsonElement body, final String channel)
        throws PluginUpdateException {
        if (!body.isJsonObject()) {
            throw new PluginUpdateException("Hangar's answer was not the JSON object expected.");
        }
        final JsonElement result = body.getAsJsonObject().get("result");
        if (result == null || !result.isJsonArray() || result.getAsJsonArray().isEmpty()) {
            throw new EmptyChannelException();
        }

        final JsonArray versions = result.getAsJsonArray();
        final JsonObject version = versions.get(0).getAsJsonObject();
        final JsonObject paper = JsonApi.object(JsonApi.object(version, "downloads"), "PAPER");
        if (paper == null) {
            throw new PluginUpdateException("Hangar's newest build has no Paper download.");
        }

        final JsonObject file = JsonApi.object(paper, "fileInfo");
        final String downloadUrl = JsonApi.string(paper, "downloadUrl");
        if (file == null || downloadUrl == null) {
            // Hangar lets a project list a link to somewhere else instead of hosting the file.
            // There is then no file name, no version to speak of and no checksum, so there is
            // nothing here to install or to compare against.
            throw new PluginUpdateException("this project does not host its file on Hangar, it "
                + "only links elsewhere, so there is no version or checksum to work from.");
        }

        final String fileName = JsonApi.string(file, "name");
        final String sha256 = JsonApi.string(file, "sha256Hash");
        if (fileName == null) {
            throw new PluginUpdateException("Hangar's answer was missing the file name.");
        }
        final String versionName = JsonApi.string(version, "name");

        // The channel goes in the version string when it is not Release, so the log says what it
        // installed rather than leaving a beta looking like a stable build.
        final String label = versionName == null ? "unknown" : versionName;
        return new PluginRelease("hangar", slug,
            "Release".equals(channel) ? label : label + " (" + channel + ")",
            fileName,
            sha256 == null ? null : new PluginRelease.Checksum("SHA-256", sha256),
            downloadUrl);
    }
}
