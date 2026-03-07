package com.serverlogger;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Locale;

public class ServerDataCollector {

    public String ip          = "unknown";
    public int    port        = 25565;
    public String domain      = "unknown";
    public String brand       = "unknown";
    public String version     = "unknown";

    public String dimension    = "minecraft:overworld";
    public String resourcePack = null;

    private List<String> plugins         = new ArrayList<>();
    private boolean      pluginsReceived = false;

    private final Set<String> detectedAddresses     = new LinkedHashSet<>();
    private final Set<String> detectedGameAddresses = new LinkedHashSet<>();

    private boolean joined           = false;
    private boolean onBreadcrumbServer = false;
    private int     pollTicks        = 0;
    private static final int POLL_INTERVAL = 20;
    private static final int MAX_POLL_TIME = 200;

    public void onServerJoin(ClientPacketListener handler, Minecraft client) {
        reset();
        joined = true;

        try {
            //? if >=1.21.6
            version = SharedConstants.getCurrentVersion().name();
            //? if <1.21.6
            //version = SharedConstants.getCurrentVersion().getName();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read MC version: {}", e.getMessage());
        }

        // ── Primary: use the address the player actually typed (ignores proxies) ──
        try {
            var serverData = client.getCurrentServer();
            if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
                String addr = serverData.ip;
                // addr may be "host:port" or bare "host"
                int colonIdx = addr.lastIndexOf(':');
                if (colonIdx > 0 && colonIdx < addr.length() - 1) {
                    domain = addr.substring(0, colonIdx).trim();
                    try { port = Integer.parseInt(addr.substring(colonIdx + 1).trim()); }
                    catch (NumberFormatException ignored) {}
                } else {
                    domain = addr.trim();
                }
            }
        } catch (Exception ignored) {}

        // ── Fallback: resolve from the actual socket address ──────────────────────
        try {
            InetSocketAddress addr =
                    (InetSocketAddress) handler.getConnection().getRemoteAddress();
            ip = addr.getAddress().getHostAddress();
            if (port == 25565) port = addr.getPort();
            if ("unknown".equals(domain)) domain = addr.getHostName();
        } catch (Exception e) {
            ServerLoggerMod.sendMessage("Could not read server address: " + e.getMessage());
        }

        // ── Brand ────────────────────────────────────────────────────────────────
        try {
            String b = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                    handler).getServerBrand();
            if (b != null && !b.isBlank()) brand = b;
        } catch (Exception ignored) {}

        // ── Breadcrumb detection ──────────────────────────────────────────────────
        if (ServerLoggerMod.INSTANCE != null
                && ServerLoggerMod.INSTANCE.breadcrumbResolver.isBreadcrumbServer(domain)) {
            onBreadcrumbServer = true;
            ServerLoggerMod.INSTANCE.breadcrumbResolver.reset();
            ServerLoggerMod.INSTANCE.breadcrumbResolver.setProxyDomain(domain);
            ServerLoggerMod.LOGGER.info("[Server Logger] Breadcrumb server: {} — scanning for real domain", domain);
            ServerLoggerMod.sendMessage("Breadcrumb server detected (" + domain + ") — scanning for real domain…");
        }

        ServerLoggerMod.LOGGER.info("[Server Logger] Joined {}:{} ({}) brand={}", ip, port, domain, brand);
        ServerLoggerMod.sendMessage("Joined " + ip + ":" + port + " (" + domain + ") — brand: " + brand);
    }

    public void onPluginsDetected(List<String> detectedPlugins) {
        Set<String> seen = new LinkedHashSet<>();
        for (String p : detectedPlugins) seen.add(p.toLowerCase(Locale.ROOT));
        plugins = new ArrayList<>(seen);
        plugins.sort(String.CASE_INSENSITIVE_ORDER);
        pluginsReceived = true;

        if (!plugins.isEmpty()) {
            String pluginStr = String.join(", ", plugins);
            int count = plugins.size();
            Minecraft mc = Minecraft.getInstance();
            ConfigManager cfg = ServerLoggerMod.INSTANCE.config;
            mc.execute(() -> {
                if (cfg.autoClipboard) mc.keyboardHandler.setClipboard(pluginStr);
                if (cfg.showToasts) {
                    //? if >=1.21.4 {
                    SystemToast.add(
                            mc.getToastManager(),
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("Server Logger"),
                            Component.literal("Detected " + count + " plugin" + (count != 1 ? "s" : "")
                                    + (cfg.autoClipboard ? " (copied to clipboard)" : ""))
                    );
                    //?} else {
                    //?}
                }
            });
        }

        attemptWrite();
    }

    public void onResourcePack(String url) {
        if (url != null && !url.isBlank()) {
            resourcePack = url;
            addExtractedUrls(url);
        }
    }

    public void onServerBrand(String b) {
        if (b != null && !b.isBlank()) brand = b;
    }

    public void onDimension(String dimensionId) {
        if (dimensionId != null) dimension = dimensionId;
        // On a breadcrumb server every world-change / server-switch resets the
        // resolution state and immediately re-scans the new server's UI data.
        if (onBreadcrumbServer && ServerLoggerMod.INSTANCE != null) {
            ServerLoggerMod.INSTANCE.breadcrumbResolver.reset();
            pollTicks = 0;
            // Schedule on the main thread so scoreboard / tab-list are readable.
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.getConnection() != null) doScan(client);
                if (applyResolvedDomain()) JsonLogger.write(this);
            });
        }
    }

    public void onChatMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) return;
        addExtractedUrls(plainText);
        if (onBreadcrumbServer && ServerLoggerMod.INSTANCE != null) {
            ServerLoggerMod.INSTANCE.breadcrumbResolver.tryResolve(plainText);
            // Apply and log on the main thread immediately after resolution.
            Minecraft.getInstance().execute(() -> {
                if (applyResolvedDomain()) JsonLogger.write(this);
            });
        }
    }

    public void tick(Minecraft client) {
        if (!joined) return;
        if (client.getConnection() == null) return;

        pollTicks++;
        if (pollTicks % POLL_INTERVAL != 0) return;
        if (pollTicks > MAX_POLL_TIME) return;

        // brand
        if ("unknown".equals(brand)) {
            try {
                String b = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                        client.getConnection()).getServerBrand();
                if (b != null && !b.isBlank()) brand = b;
            } catch (Exception ignored) {}
        }

        doScan(client);

        if (applyResolvedDomain()) JsonLogger.write(this);
    }

    //TAB and scoreboard for breadcrumbs
    private void doScan(Minecraft client) {
        // Scoreboard
        if (client.level != null) {
            client.level.getScoreboard().getObjectives().forEach(obj -> {
                String text = obj.getDisplayName().getString();
                addExtractedUrls(text);
                if (onBreadcrumbServer && ServerLoggerMod.INSTANCE != null) {
                    ServerLoggerMod.INSTANCE.breadcrumbResolver.tryResolve(text);
                }
            });
        }

        // Tab
        if (client.getConnection() != null) {
            client.getConnection().getOnlinePlayers().forEach(info -> {
                var display = info.getTabListDisplayName();
                if (display != null) {
                    String text = display.getString();
                    addExtractedUrls(text);
                    if (onBreadcrumbServer && ServerLoggerMod.INSTANCE != null) {
                        ServerLoggerMod.INSTANCE.breadcrumbResolver.tryResolve(text);
                    }
                }
            });
        }
    }

    public void reset() {
        ip = "unknown"; port = 25565; domain = "unknown";
        brand = "unknown"; version = "unknown";
        dimension = "minecraft:overworld";
        resourcePack = null;
        plugins.clear();
        detectedAddresses.clear();
        detectedGameAddresses.clear();
        pluginsReceived   = false;
        joined            = false;
        onBreadcrumbServer = false;
        pollTicks         = 0;
    }

    private void attemptWrite() {
        applyResolvedDomain();
        if ("unknown".equals(brand)) {
            try {
                var mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    String b = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                            mc.getConnection()).getServerBrand();
                    if (b != null && !b.isBlank()) brand = b;
                }
            } catch (Exception ignored) {}
        }
        JsonLogger.write(this);
    }

    /**
     * If the breadcrumb resolver has identified a new sub-server domain,
     * update {@code domain} and return {@code true} so the caller can write
     * a JSON log for the newly detected server.
     */
    private boolean applyResolvedDomain() {
        if (!onBreadcrumbServer || ServerLoggerMod.INSTANCE == null) return false;
        String resolved = ServerLoggerMod.INSTANCE.breadcrumbResolver.getResolvedDomain();
        if (resolved != null && !resolved.equals(domain)) {
            ServerLoggerMod.sendMessage("Real domain found: " + resolved);
            domain = resolved;
            return true;
        }
        return false;
    }

    private static boolean isGameAddress(String candidate) {
        return !candidate.contains("://") && !candidate.contains("/");
    }

    private void addExtractedUrls(String text) {
        for (String addr : UrlExtractor.extract(text)) {
            if (isGameAddress(addr)) detectedGameAddresses.add(addr);
            else detectedAddresses.add(addr);
        }
    }

    public List<String> getPlugins()               { return Collections.unmodifiableList(plugins); }
    public Set<String>  getDetectedAddresses()     { return Collections.unmodifiableSet(detectedAddresses); }
    public Set<String>  getDetectedGameAddresses() { return Collections.unmodifiableSet(detectedGameAddresses); }
}
