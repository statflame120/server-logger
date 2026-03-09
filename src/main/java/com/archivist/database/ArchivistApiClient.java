package com.archivist.database;

import com.archivist.ServerDataCollector;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ArchivistApiClient {

    private final ApiConfig config;
    private final HttpClient httpClient;

    public ArchivistApiClient(ApiConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpRequest.Builder authedRequest(String fullUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(15));

        for (Map.Entry<String, String> header : config.getDecodedAuthHeaders().entrySet()) {
            if (header.getKey() != null && !header.getKey().isBlank()
                    && header.getValue() != null && !header.getValue().isBlank()) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder;
    }

    private String buildUrl(String endpoint) {
        String base = config.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String ep = (endpoint.startsWith("/")) ? endpoint : "/" + endpoint;
        return base + ep;
    }

    /** Build session JSON from current ServerDataCollector data (same format as local JSON logs). */
    private String buildSessionJson(ServerDataCollector data) {
        // Debug: log raw data values to verify collector is populated
        System.out.println("[Archivist DEBUG] Data snapshot — ip=" + data.ip
                + " port=" + data.port + " domain=" + data.domain
                + " brand=" + data.brand + " version=" + data.version
                + " playerCount=" + data.playerCount
                + " plugins=" + data.getPlugins().size()
                + " dimension=" + data.dimension);

        JsonObject root = new JsonObject();
        root.addProperty("timestamp", LocalDate.now().toString());

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("ip", data.ip);
        serverInfo.addProperty("port", data.port);
        serverInfo.addProperty("domain", data.domain);
        serverInfo.addProperty("brand", data.brand);
        serverInfo.addProperty("version", data.version);
        serverInfo.addProperty("player_count", data.playerCount);
        root.add("server_info", serverInfo);

        JsonArray pluginsArr = new JsonArray();
        for (String name : data.getPlugins()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", name.toLowerCase(java.util.Locale.ROOT));
            pluginsArr.add(p);
        }
        root.add("plugins", pluginsArr);

        JsonArray addrArr = new JsonArray();
        data.getDetectedAddresses().forEach(addrArr::add);
        root.add("detected_addresses", addrArr);

        JsonArray gameAddrArr = new JsonArray();
        data.getDetectedGameAddresses().forEach(gameAddrArr::add);
        root.add("detected_game_addresses", gameAddrArr);

        JsonArray worldsArr = new JsonArray();
        JsonObject currentWorld = new JsonObject();
        currentWorld.addProperty("dimension", data.dimension);
        worldsArr.add(currentWorld);
        root.add("worlds", worldsArr);

        return new GsonBuilder().create().toJson(root);
    }

    /**
     * Push the current session. Eagerly snapshots JSON from the data collector
     * (so the caller can safely reset() after this returns), then POSTs async.
     */
    public CompletableFuture<ApiResponse> pushSession(ServerDataCollector data) {
        try {
            String url = buildUrl(config.getPushEndpoint());
            // Snapshot JSON eagerly — data may be reset() by caller after this returns
            String sessionJson = buildSessionJson(data);
            String json = "{\"servers\":[" + sessionJson + "]}";

            // Debug logging — remove after confirming push works
            Map<String, String> headers = config.getDecodedAuthHeaders();
            System.out.println("[Archivist DEBUG] Auth headers count: " + headers.size());
            for (String key : headers.keySet()) {
                System.out.println("[Archivist DEBUG] Header present: " + key);
            }
            System.out.println("[Archivist DEBUG] Push URL: " + url);
            System.out.println("[Archivist DEBUG] Push payload length: " + json.length());
            System.out.println("[Archivist DEBUG] Push payload: " + json.substring(0, Math.min(200, json.length())) + "...");

            HttpRequest request = authedRequest(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        System.out.println("[Archivist DEBUG] Response: " + r.statusCode() + " " + r.body());
                        return new ApiResponse(r.statusCode(), r.body(), r.statusCode() >= 200 && r.statusCode() < 300);
                    })
                    .exceptionally(ex -> {
                        System.out.println("[Archivist DEBUG] Exception: " + ex.getMessage());
                        return new ApiResponse(0, ex.getMessage(), false);
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new ApiResponse(0, e.getMessage(), false));
        }
    }

    /** Download logs. GET from downloadEndpoint. Async. */
    public CompletableFuture<ApiResponse> downloadLogs() {
        try {
            HttpRequest request = authedRequest(buildUrl(config.getDownloadEndpoint()))
                    .GET().build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> new ApiResponse(r.statusCode(), r.body(), r.statusCode() >= 200 && r.statusCode() < 300))
                    .exceptionally(ex -> new ApiResponse(0, ex.getMessage(), false));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new ApiResponse(0, e.getMessage(), false));
        }
    }

    /** Reset logs on the remote. POST to resetEndpoint with resetKey. Async. */
    public CompletableFuture<ApiResponse> resetLogs() {
        try {
            String resetKey = config.getDecodedResetKey();
            HttpRequest request = authedRequest(buildUrl(config.getResetEndpoint()))
                    .header("Content-Type", "application/json")
                    .header("X-Reset-Key", resetKey)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"resetKey\":\"" + resetKey + "\"}"))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> new ApiResponse(r.statusCode(), r.body(), r.statusCode() >= 200 && r.statusCode() < 300))
                    .exceptionally(ex -> new ApiResponse(0, ex.getMessage(), false));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new ApiResponse(0, e.getMessage(), false));
        }
    }

    /** Test connection: GET downloadEndpoint. Async. */
    public CompletableFuture<ApiResponse> testConnection() {
        return downloadLogs();
    }
}
