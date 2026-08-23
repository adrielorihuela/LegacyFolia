package io.github.adrielorihuela.legacyfolia.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Downloads and updates the plugins listed under {@code [plugins.auto-update]} before they load.
 *
 * <p>Runs once, at start-up, immediately before the plugin loader reads {@code plugins/}, so that
 * by the time it looks, every jar it is meant to find is there. All of them are fetched at once and
 * start-up waits for the last one.</p>
 *
 * <p>Whether a plugin is up to date is decided by the checksum its source publishes, not by the
 * file name. Names are ambiguous — a Hangar build called {@code 5.12.0-SNAPSHOT+1053} ships a file
 * called {@code ViaVersion-5.12.0-SNAPSHOT.jar} — and the same digest verifies the download
 * afterwards, so a truncated or corrupted file is never put in place. LuckPerms publishes no
 * checksum at all, and there the file name is what there is; that is said out loud in the log
 * rather than left looking like a verification that happened.</p>
 *
 * <p>A failure is only fatal when it would leave the server without a plugin: if the jar is already
 * on disk, an outage is a warning and the installed version is used. If it is not, start-up stops,
 * because carrying on means running without a plugin somebody listed on purpose — and on a server
 * that is a permissions plugin, which means running wide open.</p>
 */
public final class PluginUpdater {

    private static final Logger LOGGER = LogManager.getLogger("LegacyFolia");

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    /**
     * One line of the configuration: which source, and what to ask it for.
     *
     * @param source the name of the source, as written in the configuration
     * @param spec the plugin identifier within it
     */
    public record Wanted(String source, String spec) {
    }

    private final HttpClient httpClient;
    private final Path pluginDirectory;
    private final Map<String, PluginSource> sources;

    /**
     * Creates an updater.
     *
     * @param pluginDirectory the directory plugins are loaded from
     * @param minecraftVersion the version to ask the sources for builds of
     * @param githubToken a GitHub token to raise its rate limit with, or {@code null} for none
     */
    public PluginUpdater(final Path pluginDirectory, final String minecraftVersion,
                         final @Nullable String githubToken) {
        this(defaultHttpClient(), pluginDirectory, minecraftVersion, githubToken);
    }

    /**
     * A client of its own, rather than the server's.
     *
     * <p>Every one of these APIs redirects, and GitHub's download links point at another host
     * entirely, so redirects have to be followed. That is not something to go changing on a client
     * shared with Mojang session authentication.</p>
     */
    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    PluginUpdater(final HttpClient httpClient, final Path pluginDirectory,
                  final String minecraftVersion, final @Nullable String githubToken) {
        this.httpClient = httpClient;
        this.pluginDirectory = pluginDirectory;
        this.sources = Map.of(
            "hangar", new HangarSource(httpClient, minecraftVersion),
            "modrinth", new ModrinthSource(httpClient, minecraftVersion),
            "github", new GitHubSource(httpClient, githubToken),
            "luckperms", new LuckPermsSource(httpClient));
    }

    /**
     * Brings every listed plugin up to date, and blocks until they all are.
     *
     * @param wanted what the configuration asks for
     * @throws PluginUpdateException if a plugin is missing and could not be downloaded
     */
    public void updateAll(final List<Wanted> wanted) throws PluginUpdateException {
        if (wanted.isEmpty()) {
            return;
        }

        LOGGER.info("Checking {} plugin(s) for updates...", wanted.size());
        try {
            Files.createDirectories(this.pluginDirectory);
        } catch (final IOException e) {
            throw new PluginUpdateException("could not create the plugins directory", e);
        }

        // One virtual thread per plugin. This is network-bound and short-lived, and every one of
        // them spends its life waiting, which is exactly what these are for -- there is no reason
        // to cap how many wait at once.
        final List<CompletableFuture<String>> futures = new ArrayList<>(wanted.size());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final Wanted one : wanted) {
                futures.add(CompletableFuture.supplyAsync(() -> updateOne(one), pool));
            }
            // join() rather than a timeout: every request already has its own, so this cannot hang
            // for longer than the slowest of them.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        final List<String> fatal = new ArrayList<>();
        for (final CompletableFuture<String> future : futures) {
            final String failure = future.join();
            if (failure != null) {
                fatal.add(failure);
            }
        }

        if (!fatal.isEmpty()) {
            throw new PluginUpdateException(String.join("; ", fatal));
        }
    }

    /** Returns null when the plugin ended up usable, or a message when start-up must stop. */
    private @Nullable String updateOne(final Wanted wanted) {
        final String label = wanted.spec();
        try {
            final PluginSource source = this.sources.get(wanted.source());
            if (source == null) {
                throw new PluginUpdateException("there is no plugin source called "
                    + wanted.source() + ". The ones there are: "
                    + String.join(", ", this.sources.keySet().stream().sorted().toList()) + ".");
            }
            install(label, source.resolve(wanted.spec()));
            return null;
        } catch (final PluginUpdateException | IOException e) {
            final List<Path> installed = existingJars(lastSegment(label));
            if (installed.isEmpty()) {
                LOGGER.error("Could not install {}: {}", label, sentence(e.getMessage()));
                return label + " (" + e.getMessage() + ")";
            }
            LOGGER.warn("Could not check {} for updates: {} Continuing with the installed {}.",
                label, sentence(e.getMessage()), installed.get(0).getFileName());
            return null;
        }
    }

    /** Ends a reason with a full stop, since some of them already carry one and some do not. */
    private static String sentence(final @Nullable String message) {
        if (message == null || message.isEmpty()) {
            return "no reason given.";
        }
        return message.endsWith(".") ? message : message + ".";
    }

    /**
     * The part of an entry that could name a jar on disk.
     *
     * <p>For {@code ViaVersion/ViaRewind} that is {@code ViaRewind}; for everything else it is the
     * entry itself. Only used to guess whether something is already installed when the source could
     * not be reached, so a near miss costs a warning rather than a wrong decision.</p>
     *
     * @param spec the configuration entry
     * @return the part that might match a file name
     */
    static String lastSegment(final String spec) {
        final int slash = spec.lastIndexOf('/');
        final String withoutOwner = slash == -1 ? spec : spec.substring(slash + 1);
        final int colon = withoutOwner.indexOf(':');
        return colon == -1 ? withoutOwner : withoutOwner.substring(0, colon);
    }

    private void install(final String label, final PluginRelease release)
        throws PluginUpdateException, IOException {
        final Path target = this.pluginDirectory.resolve(release.fileName());
        if (Files.exists(target) && isCurrent(target, release)) {
            // Still prune: an older jar dropped in by hand is not made harmless by us being up to
            // date. The plugin loader refuses the second one it sees for an id, and which of the
            // two it reads first is not something anybody controls.
            pruneOtherJars(release.baseName(), target);
            LOGGER.info("{} {} is up to date.", label, release.version());
            return;
        }

        LOGGER.info("Updating {} to {} from {}...", label, release.version(), release.source());
        final Path part = this.pluginDirectory.resolve(release.fileName() + ".part");
        try {
            download(release, part);

            final PluginRelease.Checksum checksum = release.checksum();
            if (checksum != null) {
                final String actual = digestOf(part, checksum.algorithm());
                if (!checksum.hex().equalsIgnoreCase(actual)) {
                    throw new PluginUpdateException("the downloaded file did not match the "
                        + "checksum " + release.source() + " published for it, so it has been "
                        + "discarded");
                }
            } else {
                LOGGER.warn("{} publishes no checksum, so {} was installed unverified.",
                    release.source(), release.fileName());
            }

            // Only once the replacement is known good -- and before the move, because leaving
            // both on disk is worse than leaving the old one: two jars claiming one plugin id
            // make the loader refuse whichever it reads second.
            try {
                pruneOtherJars(release.baseName(), target);
            } catch (final IOException e) {
                throw new PluginUpdateException("the old " + release.baseName() + " jar could not "
                    + "be removed (" + e.getMessage() + "), and installing the new one alongside "
                    + "it would leave two jars claiming the same plugin. Stop the server and "
                    + "delete the old jar by hand", e);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("{} {} installed.", label, release.version());
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /**
     * Whether the file already on disk is the release the source is offering.
     *
     * <p>Answered by the digest and by nothing else, wherever the source publishes one. A matching
     * file name proves only that something of that name is there — not that it is this build, not
     * that it is intact, and not that it is what the source is serving today.</p>
     *
     * <p>LuckPerms publishes none, anywhere, in any format. There the name is all there is, and it
     * carries the build number the metadata named, so it is a weaker answer rather than a
     * meaningless one — but it is a weaker answer, and this says so.</p>
     */
    boolean isCurrent(final Path target, final PluginRelease release) throws IOException {
        final PluginRelease.Checksum checksum = release.checksum();
        if (checksum != null) {
            return checksum.hex().equalsIgnoreCase(digestOf(target, checksum.algorithm()));
        }
        LOGGER.warn("{} publishes no checksum, so {} is being kept on the strength of its name "
            + "alone.", release.source(), target.getFileName());
        return true;
    }

    private void download(final PluginRelease release, final Path destination)
        throws PluginUpdateException, IOException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(release.downloadUrl()))
            .header("User-Agent", JsonApi.USER_AGENT)
            .timeout(DOWNLOAD_TIMEOUT)
            .GET();
        release.downloadHeaders().forEach(builder::setHeader);
        try {
            final HttpResponse<InputStream> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new PluginUpdateException("the download answered " + response.statusCode()
                    + ".");
            }
            try (InputStream in = response.body()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginUpdateException("interrupted while downloading it", e);
        }
    }

    /**
     * Deletes every other jar of this plugin, so only {@code keep} is left for the loader to find.
     *
     * <p>Two jars claiming one plugin id make the loader refuse whichever it reads second, and
     * nothing decides which that is. Being out of date is recoverable; loading the wrong copy
     * quietly is not.</p>
     */
    private void pruneOtherJars(final String baseName, final Path keep) throws IOException {
        for (final Path old : existingJars(baseName)) {
            if (!old.equals(keep)) {
                Files.deleteIfExists(old);
                LOGGER.info("Removed {}, which claims the same plugin as {}",
                    old.getFileName(), keep.getFileName());
            }
        }
    }

    /** Every jar in the plugin directory that belongs to this plugin. */
    private List<Path> existingJars(final String name) {
        if (!Files.isDirectory(this.pluginDirectory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(this.pluginDirectory)) {
            return entries.filter(path -> isJarOf(name, path.getFileName().toString())).toList();
        } catch (final IOException e) {
            LOGGER.debug("Could not list {}", this.pluginDirectory, e);
            return List.of();
        }
    }

    /**
     * Whether a file in the plugin directory is a jar of the named plugin.
     *
     * <p>Matches {@code Name.jar} and {@code Name-<anything>.jar}, ignoring case. The dash is what
     * keeps {@code ViaVersion} from claiming {@code ViaVersionAddon-1.0.jar}.</p>
     *
     * @param name the plugin's base name
     * @param fileName the file name to test
     * @return whether the file is a jar of that plugin
     */
    static boolean isJarOf(final String name, final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            return false;
        }
        final String stem = lower.substring(0, lower.length() - ".jar".length());
        final String prefix = name.toLowerCase(Locale.ROOT);
        return stem.equals(prefix) || stem.startsWith(prefix + "-");
    }

    private static String digestOf(final Path path, final String algorithm) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (final NoSuchAlgorithmException e) {
            throw new IOException("this JVM has no " + algorithm + " implementation", e);
        }
        try (InputStream in = Files.newInputStream(path)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
