package com.serverlogger;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Holds all the data we collect about the current server session.
 * Gets populated by the mixin callbacks and the PluginScanner, then
 * hands everything off to JsonLogger when a scan completes.
 */
public class ServerDataCollector {

    // Server identity
    public String ip          = "unknown";
    public int    port        = 25565;
    public String domain      = "unknown";
    public String software    = "unknown";
    public String version     = "unknown";

    // World
    public String dimension   = "minecraft:overworld";
    public String resourcePack = null;

    // Plugins
    private List<String>  plugins          = new ArrayList<>();
    private boolean       pluginsReceived  = false;

    // Addresses found in TAB list, scoreboard, chat
    private final Set<String> detectedAddresses = new LinkedHashSet<>();

    // Tick counter for late data collection (scoreboard / tab-list polling)
    private boolean joined       = false;
    private int     pollTicks    = 0;
    private static final int POLL_INTERVAL = 20;  // every 1 second
    private static final int MAX_POLL_TIME = 200; // poll for 10 seconds

    // ── Events ────────────────────────────────────────────────────────────

    public void onServerJoin(ClientPacketListener handler, Minecraft client) {
        reset();
        joined = true;

        // Get MC version from the client (must match server protocol)
        try {
            version = SharedConstants.getCurrentVersion().name();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read MC version: {}", e.getMessage());
        }

        try {
            InetSocketAddress addr =
                    (InetSocketAddress) handler.getConnection().getRemoteAddress();
            ip     = addr.getAddress().getHostAddress();
            port   = addr.getPort();
            domain = addr.getHostName();   // DNS name if available, otherwise same as IP
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read server address: {}", e.getMessage());
        }

        // The brand payload is often sent during the configuration phase
        // (before play state), so reset() wiped it. Re-read it now.
        try {
            String brand = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                    handler).getServerBrand();
            if (brand != null && !brand.isBlank()) {
                software = brand;
            }
        } catch (Exception ignored) {}

        ServerLoggerMod.LOGGER.info("[Server Logger] Joined server {}:{} ({}) software={}", ip, port, domain, software);
    }

    /** Called by PluginScanner once plugin detection is complete. */
    public void onPluginsDetected(List<String> detectedPlugins) {
        plugins = detectedPlugins;
        plugins.sort(String.CASE_INSENSITIVE_ORDER);
        pluginsReceived = true;
        attemptWrite();
    }

    /** Called from mixin when a resource pack URL arrives. */
    public void onResourcePack(String url) {
        if (url != null && !url.isBlank()) {
            resourcePack = url;
            detectedAddresses.addAll(UrlExtractor.extract(url));
        }
    }

    /** Called from mixin when the server brand is known. */
    public void onServerBrand(String brand) {
        if (brand != null && !brand.isBlank()) {
            software = brand;
        }
    }

    /** Called from mixin on initial login packet — gives us the starting dimension. */
    public void onDimension(String dimensionId) {
        if (dimensionId != null) dimension = dimensionId;
    }

    /**
     * Called from mixin on incoming chat messages.
     * Used to scrape URLs/addresses from chat.
     */
    public void onChatMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) return;

        // Scrape addresses
        detectedAddresses.addAll(UrlExtractor.extract(plainText));
    }

    // ── Tick loop (polls tab list and scoreboard for URLs) ─────────────────

    public void tick(Minecraft client) {
        if (!joined) return;
        if (client.getConnection() == null) return;

        pollTicks++;
        if (pollTicks % POLL_INTERVAL != 0) return;
        if (pollTicks > MAX_POLL_TIME) return;

        // ── Grab server brand via accessor mixin ──────────────────────────
        if ("unknown".equals(software)) {
            try {
                String brand = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                        client.getConnection()).getServerBrand();
                if (brand != null && !brand.isBlank()) {
                    software = brand;
                }
            } catch (Exception e) {
                // Accessor not yet available or cast failed — will retry next tick
            }
        }

        // ── Scan scoreboard display names for addresses ───────────────────
        if (client.level != null) {
            client.level.getScoreboard().getObjectives().forEach(obj -> {
                String text = obj.getDisplayName().getString();
                detectedAddresses.addAll(UrlExtractor.extract(text));
            });
        }

        // ── Scan tab-list player display names ────────────────────────────
        client.getConnection().getOnlinePlayers().forEach(info -> {
            var display = info.getTabListDisplayName();
            if (display != null) {
                detectedAddresses.addAll(UrlExtractor.extract(display.getString()));
            }
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void reset() {
        ip = "unknown"; port = 25565; domain = "unknown";
        software = "unknown"; version = "unknown";
        dimension = "minecraft:overworld";
        resourcePack = null;
        plugins.clear();
        detectedAddresses.clear();
        pluginsReceived = false;
        joined = false;
        pollTicks = 0;
    }

    // ── Write ─────────────────────────────────────────────────────────────

    private void attemptWrite() {
        // Last-chance brand read: the brand may have arrived during the
        // configuration phase and been wiped by reset(), or simply not
        // yet been delivered via the mixin callback.  Pull it straight
        // from the connection field before we write.
        if ("unknown".equals(software)) {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    String brand = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                            mc.getConnection()).getServerBrand();
                    if (brand != null && !brand.isBlank()) {
                        software = brand;
                    }
                }
            } catch (Exception ignored) {}
        }
        JsonLogger.write(this);
    }

    // ── Getters for JsonLogger ─────────────────────────────────────────────

    public List<String>  getPlugins()           { return Collections.unmodifiableList(plugins); }
    public Set<String>   getDetectedAddresses() { return Collections.unmodifiableSet(detectedAddresses); }
}
