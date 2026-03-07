package com.serverlogger;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.*;
import java.util.*;

/**
 * Tracks "breadcrumb" proxy/hub servers (e.g. Minehut) where the address
 * given by the client is the network's own domain rather than the real
 * sub-server.  For those servers, the domain is resolved by scraping the
 * tab-list, scoreboard, and chat for a non-proxy hostname.
 * The list of breadcrumb servers is persisted to
 * server-logger-breadcrumbs.json in the config directory.
 */
public class BreadcrumbResolver {

    private static final String FILE_NAME = "server-logger-breadcrumbs.json";

    private final Set<String> breadcrumbServers = new LinkedHashSet<>();

    /** First non-breadcrumb hostname found in scraped text, or null. */
    private volatile String resolvedDomain = null;

    /** The proxy domain we connected through (e.g. "minehut.com"), set when breadcrumb mode activates. */
    private String proxyDomain = null;

    public BreadcrumbResolver() {
        // Shipped defaults
        breadcrumbServers.add("minehut.com");
        breadcrumbServers.add("minehut.gg");
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            save();
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonArray arr  = obj.getAsJsonArray("servers");
            if (arr != null) {
                breadcrumbServers.clear();
                arr.forEach(e -> breadcrumbServers.add(e.getAsString().toLowerCase(Locale.ROOT)));
            }
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to load breadcrumbs: {}", e.getMessage());
        }
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        JsonObject obj = new JsonObject();
        JsonArray  arr = new JsonArray();
        breadcrumbServers.forEach(arr::add);
        obj.add("servers", arr);
        try {
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to save breadcrumbs: {}", e.getMessage());
        }
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given address belongs to a known breadcrumb
     * proxy network (e.g. "play.minehut.com" matches "minehut.com").
     */
    public boolean isBreadcrumbServer(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) return false;
        String lower = serverAddress.toLowerCase(Locale.ROOT);
        return breadcrumbServers.stream().anyMatch(lower::contains);
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Feed raw text from a tab-list entry, scoreboard objective, or chat
     * message.  If an address is found that differs from the proxy we connected
     * through, it is stored as the real sub-server domain.
     *
     * When {@code proxyDomain} is set we compare by hostname so that addresses
     * like "myserver.minehut.gg" are accepted even though they share the
     * "minehut" brand with the proxy.  Without a stored proxy domain we fall
     * back to the old breadcrumb-keyword filter.
     */
    public void tryResolve(String text) {
        if (resolvedDomain != null || text == null || text.isBlank()) return;
        for (String candidate : UrlExtractor.extract(text)) {
            // Only bare hostnames qualify as game addresses — skip anything
            // with a scheme (https://) or path (/shop) which is a website.
            if (candidate.contains("://") || candidate.contains("/")) continue;
            // Filter Bedrock/Geyser usernames (.PlayerName) which have a single
            // leading dot and no further dots — real subdomains are unaffected.
            if (UrlExtractor.isNoise(candidate, true)) continue;

            // Strip port to get the bare hostname for comparison.
            String hostname = candidate.toLowerCase(Locale.ROOT).split("[:/]")[0];
            boolean differs = proxyDomain == null
                    ? !isBreadcrumbServer(candidate)
                    : !hostname.equals(proxyDomain);
            if (differs) {
                resolvedDomain = candidate;
                ServerLoggerMod.LOGGER.info(
                        "[Server Logger] Breadcrumb domain resolved: {}", resolvedDomain);
                return;
            }
        }
    }

    /** The real domain found by scraping, or {@code null} if not yet found. */
    public String getResolvedDomain() { return resolvedDomain; }

    /**
     * Record the proxy domain we connected through.  Call once when breadcrumb
     * mode activates so {@link #tryResolve} can accept same-brand sub-servers.
     */
    public void setProxyDomain(String domain) {
        proxyDomain = domain != null ? domain.toLowerCase(Locale.ROOT) : null;
    }

    /** Clear the resolved sub-server domain (keeps proxyDomain for continued filtering). */
    public void reset() { resolvedDomain = null; }

    // ── List management ───────────────────────────────────────────────────────

    public Set<String> getServers() { return Collections.unmodifiableSet(breadcrumbServers); }

    public void setServers(java.util.Collection<String> servers) {
        breadcrumbServers.clear();
        servers.forEach(s -> breadcrumbServers.add(s.toLowerCase(Locale.ROOT)));
    }

}