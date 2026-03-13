package com.archivist;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;

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

    public  int     playerCount      = 0;
    private List<String> plugins         = new ArrayList<>();
    private boolean      pluginsReceived = false;

    private final Set<String> detectedAddresses     = new LinkedHashSet<>();
    private final Set<String> detectedGameAddresses = new LinkedHashSet<>();

    private boolean joined             = false;
    private boolean onExceptionServer = false;
    private String  exceptionProxyAddress = null;
    private int     pollTicks          = 0;
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
            ArchivistMod.LOGGER.warn("[Archivist] Could not read MC version: {}", e.getMessage());
        }

        // ── Player count from server-list ping data ───────────────────────────────
        try {
            var sd = client.getCurrentServer();
            if (sd != null && sd.players != null) playerCount = sd.players.online();
        } catch (Exception ignored) {}

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
            ArchivistMod.sendMessage("Could not read server address: " + e.getMessage());
        }

        // ── Brand ────────────────────────────────────────────────────────────────
        try {
            String b = ((com.archivist.mixin.accessor.ClientCommonListenerAccessor)
                    handler).getServerBrand();
            if (b != null && !b.isBlank()) brand = b;
        } catch (Exception ignored) {}

        // ── Exception detection ──────────────────────────────────────────────────
        if (ArchivistMod.INSTANCE != null
                && ArchivistMod.INSTANCE.exceptionResolver.isExceptionServer(domain)) {
            onExceptionServer = true;
            exceptionProxyAddress = domain;
            ArchivistMod.INSTANCE.exceptionResolver.reset();
            ArchivistMod.INSTANCE.exceptionResolver.setProxyDomain(domain);
            ArchivistMod.LOGGER.info("[Archivist] Exception server: {} — scanning for real domain", domain);
            ArchivistMod.sendMessage("Exception server detected (" + domain + ") — scanning for real domain…");
        }

        ArchivistMod.LOGGER.info("[Archivist] Joined {}:{} ({}) brand={}", ip, port, domain, brand);
        ArchivistMod.sendMessage("Joined " + ip + ":" + port + " (" + domain + ") — brand: " + brand);
    }

    public void onPluginsDetected(List<String> detectedPlugins) {
        Set<String> seen = new LinkedHashSet<>();
        for (String p : detectedPlugins) seen.add(p.toLowerCase(Locale.ROOT));
        plugins = new ArrayList<>(seen);
        plugins.sort(String.CASE_INSENSITIVE_ORDER);
        pluginsReceived = true;

        EventBus.post(LogEvent.Type.PLUGIN, "Detected " + plugins.size() + " plugin" + (plugins.size() != 1 ? "s" : ""));
        for (String p : plugins) {
            EventBus.post(LogEvent.Type.PLUGIN, "Plugin: " + p);
        }

        if (!plugins.isEmpty()) {
            String pluginStr = String.join(", ", plugins);
            int count = plugins.size();
            Minecraft mc = Minecraft.getInstance();
            ConfigManager cfg = ArchivistMod.INSTANCE.config;
            mc.execute(() -> {
                if (cfg.autoClipboard) mc.keyboardHandler.setClipboard(pluginStr);
                if (cfg.showToasts) {
                    //? if >=1.21.4 {
                    SystemToast.add(
                            mc.getToastManager(),
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("Archivist"),
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
        if (b != null && !b.isBlank()) {
            brand = b;
            EventBus.post(LogEvent.Type.BRAND, "Server brand: " + b);
        }
    }

    public void onDimension(String dimensionId) {
        if (dimensionId != null) {
            dimension = dimensionId;
            EventBus.post(LogEvent.Type.WORLD, "Dimension: " + dimensionId);
        }
        // On a exception server every world-change / server-switch resets the
        // resolution state and immediately re-scans the new server's UI data.
        if (onExceptionServer && ArchivistMod.INSTANCE != null) {
            ArchivistMod.INSTANCE.exceptionResolver.reset();
            pollTicks = 0;
            // Schedule on the main thread so scoreboard / tab-list are readable.
            Minecraft.getInstance().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.getConnection() != null) {
                    if ("unknown".equals(brand)) {
                        try {
                            String b = ((com.archivist.mixin.accessor.ClientCommonListenerAccessor)
                                    client.getConnection()).getServerBrand();
                            if (b != null && !b.isBlank()) brand = b;
                        } catch (Exception ignored) {}
                    }
                    doScan(client);
                }
                if (applyResolvedDomain()) JsonLogger.write(this);
            });
        }
    }

    public void onChatMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) return;
        addExtractedUrls(plainText);
    }

    public void tick(Minecraft client) {
        if (!joined || !onExceptionServer) return;
        if (client.getConnection() == null || client.level == null) return;

        pollTicks++;

        // Scan tab-list and scoreboard every second for the real sub-server domain
        if (pollTicks % 20 == 0) {
            doScan(client);
            if (applyResolvedDomain()) {
                onExceptionServer = false;
                JsonLogger.write(this);
                return;
            }
        }

        if (pollTicks >= MAX_POLL_TIME) {
            // Timed out without finding a real sub-server domain.
            if (domain.equals(exceptionProxyAddress)) {
                if (exceptionProxyAddress != null) detectedGameAddresses.add(exceptionProxyAddress);
                domain = "unknown";
                onExceptionServer = false;
                JsonLogger.write(this);
            }
        }
    }

    //TAB and scoreboard for exceptions
    private void doScan(Minecraft client) {
        // Scoreboard
        if (client.level != null) {
            client.level.getScoreboard().getObjectives().forEach(obj -> {
                String text = obj.getDisplayName().getString();
                addExtractedUrls(text);
            });
        }

        // Tab
        if (client.getConnection() != null) {
            client.getConnection().getOnlinePlayers().forEach(info -> {
                var display = info.getTabListDisplayName();
                if (display != null) {
                    String text = display.getString();
                    addExtractedUrls(text);
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
        playerCount            = 0;
        pluginsReceived        = false;
        joined                 = false;
        onExceptionServer     = false;
        exceptionProxyAddress = null;
        pollTicks              = 0;
    }

    private void attemptWrite() {
        boolean resolved = applyResolvedDomain();
        // On a exception server, never write using the proxy domain — wait for
        // tab/scoreboard resolution or the poll-timeout fallback in tick().
        if (onExceptionServer && !resolved) return;
        if ("unknown".equals(brand)) {
            try {
                var mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    String b = ((com.archivist.mixin.accessor.ClientCommonListenerAccessor)
                            mc.getConnection()).getServerBrand();
                    if (b != null && !b.isBlank()) brand = b;
                }
            } catch (Exception ignored) {}
        }
        JsonLogger.write(this);
    }

    /**
     * If detectedGameAddresses contains a non-proxy entry, use the first one
     * as {@code domain} and return {@code true} so the caller can write a JSON log.
     */
    private boolean applyResolvedDomain() {
        if (!onExceptionServer) return false;
        String proxyLower = exceptionProxyAddress != null
                ? exceptionProxyAddress.toLowerCase(Locale.ROOT).split("[:/]")[0] : null;
        for (String addr : detectedGameAddresses) {
            String hostLower = addr.toLowerCase(Locale.ROOT).split("[:/]")[0];
            if (proxyLower != null && hostLower.equals(proxyLower)) continue;
            if (!addr.equals(domain)) {
                ArchivistMod.sendMessage("Real domain found: " + addr);
                domain = addr;
                return true;
            }
            return false; // first non-proxy entry already set as domain
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
