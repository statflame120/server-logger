package com.serverlogger;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;
import java.util.*;

public class ServerDataCollector {

    public String ip          = "unknown";
    public int    port        = 25565;
    public String domain      = "unknown";
    public String software    = "unknown";
    public String version     = "unknown";

    public String dimension    = "minecraft:overworld";
    public String resourcePack = null;

    private List<String> plugins         = new ArrayList<>();
    private boolean      pluginsReceived = false;

    private final Set<String> detectedAddresses = new LinkedHashSet<>();

    private boolean joined    = false;
    private int     pollTicks = 0;
    private static final int POLL_INTERVAL = 20;
    private static final int MAX_POLL_TIME = 200;

    public void onServerJoin(ClientPacketListener handler, Minecraft client) {
        reset();
        joined = true;

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
            domain = addr.getHostName();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read server address: {}", e.getMessage());
        }

        try {
            String brand = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                    handler).getServerBrand();
            if (brand != null && !brand.isBlank()) {
                software = brand;
            }
        } catch (Exception ignored) {}

        ServerLoggerMod.LOGGER.info("[Server Logger] Joined server {}:{} ({}) software={}", ip, port, domain, software);
    }

    public void onPluginsDetected(List<String> detectedPlugins) {
        plugins = detectedPlugins;
        plugins.sort(String.CASE_INSENSITIVE_ORDER);
        pluginsReceived = true;

        if (!plugins.isEmpty()) {
            String pluginStr = String.join(", ", plugins);
            int count = plugins.size();
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                mc.keyboardHandler.setClipboard(pluginStr);
                SystemToast.add(
                        mc.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Server Logger"),
                        Component.literal("Copied " + count + " plugin" + (count != 1 ? "s" : "") + " to clipboard")
                );
            });
        }

        attemptWrite();
    }

    public void onResourcePack(String url) {
        if (url != null && !url.isBlank()) {
            resourcePack = url;
            detectedAddresses.addAll(UrlExtractor.extract(url));
        }
    }

    public void onServerBrand(String brand) {
        if (brand != null && !brand.isBlank()) {
            software = brand;
        }
    }

    public void onDimension(String dimensionId) {
        if (dimensionId != null) dimension = dimensionId;
    }

    public void onChatMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) return;
        detectedAddresses.addAll(UrlExtractor.extract(plainText));
    }

    public void tick(Minecraft client) {
        if (!joined) return;
        if (client.getConnection() == null) return;

        pollTicks++;
        if (pollTicks % POLL_INTERVAL != 0) return;
        if (pollTicks > MAX_POLL_TIME) return;

        if ("unknown".equals(software)) {
            try {
                String brand = ((com.serverlogger.mixin.accessor.ClientCommonListenerAccessor)
                        client.getConnection()).getServerBrand();
                if (brand != null && !brand.isBlank()) {
                    software = brand;
                }
            } catch (Exception e) {
            }
        }

        if (client.level != null) {
            client.level.getScoreboard().getObjectives().forEach(obj -> {
                String text = obj.getDisplayName().getString();
                detectedAddresses.addAll(UrlExtractor.extract(text));
            });
        }

        client.getConnection().getOnlinePlayers().forEach(info -> {
            var display = info.getTabListDisplayName();
            if (display != null) {
                detectedAddresses.addAll(UrlExtractor.extract(display.getString()));
            }
        });
    }

    public void reset() {
        ip = "unknown"; port = 25565; domain = "unknown";
        software = "unknown"; version = "unknown";
        dimension = "minecraft:overworld";
        resourcePack = null;
        plugins.clear();
        detectedAddresses.clear();
        pluginsReceived = false;
        joined    = false;
        pollTicks = 0;
    }

    private void attemptWrite() {
        if ("unknown".equals(software)) {
            try {
                var mc = Minecraft.getInstance();
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

    public List<String> getPlugins()           { return Collections.unmodifiableList(plugins); }
    public Set<String>  getDetectedAddresses() { return Collections.unmodifiableSet(detectedAddresses); }
}
