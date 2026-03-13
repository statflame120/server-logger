package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP POST JSON to a configurable URL using java.net.http.HttpClient.
 * Supports Bearer token authentication. No extra dependencies.
 */
public class RestAdapter implements DatabaseAdapter {

    private String url;
    private String authToken;
    private HttpClient client;

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        this.url = connectionString;
        this.authToken = authToken;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST URL cannot be empty");
        }
        // Validate URL format
        URI.create(url);
    }

    @Override
    public void disconnect() {
        client = null;
    }

    @Override
    public void upload(ServerLogData entry) throws Exception {
        if (client == null) throw new IllegalStateException("Not connected");

        String json = serializeLogEntry(entry);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }

        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        ArchivistMod.LOGGER.info("[Archivist] REST upload ok: {} -> HTTP {}", entry.getDisplayName(), response.statusCode());
    }

    @Override
    public boolean testConnection() {
        try {
            if (client == null || url == null) return false;

            // Send a lightweight HEAD or GET request to verify the endpoint exists
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = client.send(request,
                    HttpResponse.BodyHandlers.discarding());

            return response.statusCode() < 500;
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] REST test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String displayName() {
        return "REST API";
    }

    private String serializeLogEntry(ServerLogData entry) {
        JsonObject root = new JsonObject();
        root.addProperty("serverName", entry.getDisplayName());
        root.addProperty("serverIp", entry.ip);
        root.addProperty("port", entry.port);
        root.addProperty("minecraftVersion", entry.version);
        root.addProperty("timestamp", entry.timestamp);

        JsonObject plugins = new JsonObject();
        JsonArray tabComplete = new JsonArray();
        entry.plugins.forEach(tabComplete::add);
        plugins.add("tabComplete", tabComplete);
        plugins.add("guiScraped", new JsonArray());
        root.add("plugins", plugins);

        JsonObject worldInfo = new JsonObject();
        if (!entry.worlds.isEmpty()) {
            ServerLogData.WorldSession ws = entry.worlds.get(0);
            worldInfo.addProperty("dimension", ws.dimension);
        }
        root.add("worldInfo", worldInfo);

        JsonObject connectionMeta = new JsonObject();
        connectionMeta.addProperty("brand", entry.brand);
        JsonArray addresses = new JsonArray();
        entry.detectedAddresses.forEach(addresses::add);
        connectionMeta.add("detectedAddresses", addresses);
        root.add("connectionMeta", connectionMeta);

        return new GsonBuilder().create().toJson(root);
    }
}
