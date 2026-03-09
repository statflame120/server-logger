package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.ServerDataCollector;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class ApiSyncManager {

    private final ApiConfig config;
    private ArchivistApiClient client;

    // Snapshot of last session data for push-after-disconnect
    private String lastSessionIp;
    private int lastSessionPort;
    private String lastSessionDomain;

    public ApiSyncManager(ApiConfig config) {
        this.config = config;
    }

    private ArchivistApiClient getClient() {
        if (client == null) {
            client = new ArchivistApiClient(config);
        }
        return client;
    }

    /** Called when player joins a server — snapshot session identity. */
    public void onServerJoin(ServerDataCollector data) {
        lastSessionIp = data.ip;
        lastSessionPort = data.port;
        lastSessionDomain = data.domain;
    }

    /** Called when player disconnects. Auto-push if configured. */
    public void onDisconnect(ServerDataCollector data) {
        if (!config.isConfigured() || !config.autoPush) return;
        pushSession(data);
    }

    /** Manually push current session data. */
    public void pushSession(ServerDataCollector data) {
        pushSession(data, null);
    }

    /** Push with optional callback for GUI status updates. */
    public void pushSession(ServerDataCollector data, Consumer<ApiResponse> callback) {
        if (!config.isConfigured()) {
            EventBus.post(LogEvent.Type.DB_SYNC, "API not configured — set base URL and auth headers");
            if (callback != null) callback.accept(new ApiResponse(0, "API not configured", false));
            return;
        }

        String pushUrl = normalizeUrl(config.getBaseUrl(), config.getPushEndpoint());
        EventBus.post(LogEvent.Type.DB_SYNC, "Pushing session to " + pushUrl + "...");

        getClient().pushSession(data).thenAccept(response -> {
            Minecraft.getInstance().execute(() -> {
                if (response.success()) {
                    EventBus.post(LogEvent.Type.DB_SYNC, "Push OK (" + response.statusCode() + ")");
                } else if (response.statusCode() == 0) {
                    EventBus.post(LogEvent.Type.ERROR, "Push failed: " + response.body());
                } else {
                    EventBus.post(LogEvent.Type.ERROR, "Push failed: HTTP " + response.statusCode() + " — " + truncate(response.body(), 100));
                }
                if (callback != null) callback.accept(response);
            });
        });
    }

    /** Test connection. */
    public void testConnection() {
        testConnection(null);
    }

    /** Test connection with optional callback for GUI status updates. */
    public void testConnection(Consumer<ApiResponse> callback) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            EventBus.post(LogEvent.Type.DB_SYNC, "No base URL configured");
            if (callback != null) callback.accept(new ApiResponse(0, "No base URL configured", false));
            return;
        }

        String testUrl = normalizeUrl(config.getBaseUrl(), config.getDownloadEndpoint());
        EventBus.post(LogEvent.Type.DB_SYNC, "Testing connection to " + testUrl + "...");

        getClient().testConnection().thenAccept(response -> {
            Minecraft.getInstance().execute(() -> {
                if (response.success()) {
                    EventBus.post(LogEvent.Type.DB_SYNC, "Connection OK (" + response.statusCode() + ")");
                } else if (response.statusCode() == 0) {
                    EventBus.post(LogEvent.Type.ERROR, "Connection failed: " + response.body());
                } else {
                    EventBus.post(LogEvent.Type.ERROR, "Connection failed: HTTP " + response.statusCode() + " — " + truncate(response.body(), 100));
                }
                if (callback != null) callback.accept(response);
            });
        });
    }

    /** Download logs from remote, save locally. */
    public void downloadLogs() {
        downloadLogs(null);
    }

    /** Download with optional callback for GUI status updates. */
    public void downloadLogs(Consumer<ApiResponse> callback) {
        if (!config.isConfigured()) {
            EventBus.post(LogEvent.Type.DB_SYNC, "API not configured");
            if (callback != null) callback.accept(new ApiResponse(0, "API not configured", false));
            return;
        }

        String dlUrl = normalizeUrl(config.getBaseUrl(), config.getDownloadEndpoint());
        EventBus.post(LogEvent.Type.DB_SYNC, "Downloading logs from " + dlUrl + "...");

        getClient().downloadLogs().thenAccept(response -> {
            Minecraft.getInstance().execute(() -> {
                if (response.success()) {
                    // Save response body to local file
                    try {
                        java.nio.file.Path logDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getGameDir().resolve("archivist").resolve("downloaded");
                        java.nio.file.Files.createDirectories(logDir);
                        String fileName = "api_download_" + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json";
                        java.nio.file.Files.writeString(logDir.resolve(fileName), response.body());
                        EventBus.post(LogEvent.Type.DB_SYNC, "Downloaded and saved: archivist/downloaded/" + fileName);
                    } catch (Exception e) {
                        EventBus.post(LogEvent.Type.ERROR, "Download save failed: " + e.getMessage());
                    }
                } else if (response.statusCode() == 0) {
                    EventBus.post(LogEvent.Type.ERROR, "Download failed: " + response.body());
                } else {
                    EventBus.post(LogEvent.Type.ERROR, "Download failed: HTTP " + response.statusCode());
                }
                if (callback != null) callback.accept(response);
            });
        });
    }

    /** Reset logs on remote (requires confirm). */
    public void resetLogs() {
        resetLogs(null);
    }

    /** Reset with optional callback for GUI status updates. */
    public void resetLogs(Consumer<ApiResponse> callback) {
        if (!config.isConfigured()) {
            EventBus.post(LogEvent.Type.DB_SYNC, "API not configured");
            if (callback != null) callback.accept(new ApiResponse(0, "API not configured", false));
            return;
        }

        EventBus.post(LogEvent.Type.DB_SYNC, "Resetting remote logs...");

        getClient().resetLogs().thenAccept(response -> {
            Minecraft.getInstance().execute(() -> {
                if (response.success()) {
                    EventBus.post(LogEvent.Type.DB_SYNC, "Remote logs reset OK (" + response.statusCode() + ")");
                } else {
                    EventBus.post(LogEvent.Type.ERROR, "Reset failed: HTTP " + response.statusCode() + " — " + truncate(response.body(), 100));
                }
                if (callback != null) callback.accept(response);
            });
        });
    }

    /** Recreate the HTTP client (e.g. after config change). */
    public void refreshClient() {
        client = null;
    }

    /** Build a clean URL from base + endpoint (no double slashes). */
    private static String normalizeUrl(String base, String endpoint) {
        if (base == null) base = "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String ep = (endpoint != null && endpoint.startsWith("/")) ? endpoint : "/" + endpoint;
        return base + ep;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
