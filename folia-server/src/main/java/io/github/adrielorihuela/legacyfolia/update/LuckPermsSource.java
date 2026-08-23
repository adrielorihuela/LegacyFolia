package io.github.adrielorihuela.legacyfolia.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.http.HttpClient;

/**
 * LuckPerms, from its own metadata service.
 *
 * <p>LuckPerms is not on Hangar or Modrinth in a form worth using, and its own service publishes
 * no checksum of any kind, in any format, for any platform. It is the one source where "is this up
 * to date?" cannot be answered by hashing the file.</p>
 *
 * <p>What it does publish is a version that carries the build number, and the file name carries it
 * too, so the answer comes from the name instead. That is a weaker answer rather than a meaningless
 * one, and {@link PluginUpdater} says so out loud in the log rather than letting it look like a
 * verification that happened.</p>
 */
final class LuckPermsSource implements PluginSource {

    private static final String METADATA = "https://metadata.luckperms.net/data/all";

    /** Bukkit, not Folia: LuckPerms ships one Bukkit build and it declares Folia support. */
    private static final String PLATFORM = "bukkit";

    private final JsonApi api;

    LuckPermsSource(final HttpClient httpClient) {
        this.api = new JsonApi(httpClient);
    }

    @Override
    public String name() {
        return "luckperms";
    }

    @Override
    public PluginRelease resolve(final String ignored) throws PluginUpdateException {
        final JsonElement body;
        try {
            body = this.api.get(METADATA);
        } catch (final JsonApi.NotFoundException e) {
            throw new PluginUpdateException("metadata.luckperms.net had nothing to say.");
        }
        return parse(body);
    }

    /** Split out from the request so the shape of a real answer can be tested without a network. */
    static PluginRelease parse(final JsonElement body) throws PluginUpdateException {
        if (!body.isJsonObject()) {
            throw new PluginUpdateException("LuckPerms' answer was not the JSON object expected.");
        }
        final JsonObject root = body.getAsJsonObject();

        final String version = JsonApi.string(root, "version");
        final String url = JsonApi.string(JsonApi.object(root, "downloads"), PLATFORM);
        if (version == null || url == null) {
            throw new PluginUpdateException("LuckPerms publishes no " + PLATFORM + " download in "
                + "its metadata.");
        }

        // The URL ends in LuckPerms-Bukkit-<version>.jar, which is also what the file is called on
        // disk, and the version in it is what stands in for a checksum here.
        final String fromUrl = url.substring(url.lastIndexOf('/') + 1);
        final String fileName = fromUrl.endsWith(".jar") ? fromUrl
            : "LuckPerms-Bukkit-" + version + ".jar";

        return new PluginRelease("luckperms", "luckperms", version, fileName, null, url);
    }
}
