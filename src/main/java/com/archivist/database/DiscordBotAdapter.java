package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends server log data to a Discord channel via webhook or bot token.
 * connectionString = webhook URL or channel endpoint
 * authToken = bot token (optional for webhooks)
 */
public class DiscordBotAdapter implements DatabaseAdapter {

    private String webhookUrl;
    private String botToken;
    private HttpClient client;

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        this.webhookUrl = connectionString;
        this.botToken = authToken;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("Discord webhook URL cannot be empty");
        }
        URI.create(webhookUrl);
    }

    @Override
    public void disconnect() {
        client = null;
    }

    @Override
    public void upload(ServerLogData entry) throws Exception {
        if (client == null) throw new IllegalStateException("Not connected");

        String json = buildDiscordPayload(entry);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (botToken != null && !botToken.isBlank()) {
            builder.header("Authorization", "Bot " + botToken);
        }

        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        ArchivistMod.LOGGER.info("[Archivist] Discord upload ok: {} -> HTTP {}", entry.getDisplayName(), response.statusCode());
    }

    @Override
    public boolean testConnection() {
        try {
            if (client == null || webhookUrl == null) return false;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = client.send(request,
                    HttpResponse.BodyHandlers.discarding());

            return response.statusCode() < 500;
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Discord test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String displayName() {
        return "Discord Bot";
    }

    private String buildDiscordPayload(ServerLogData entry) {
        JsonObject payload = new JsonObject();

        // Build embed
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Archivist: " + entry.getDisplayName());
        embed.addProperty("color", 0xFF9F43); // Amber accent

        List<String> descLines = new ArrayList<>();
        descLines.add("**IP:** " + entry.ip + ":" + entry.port);
        if (!"unknown".equals(entry.domain)) descLines.add("**Domain:** " + entry.domain);
        descLines.add("**Version:** " + entry.version);
        descLines.add("**Brand:** " + entry.brand);
        if (entry.playerCount >= 0) descLines.add("**Players:** " + entry.playerCount);
        embed.addProperty("description", String.join("\n", descLines));

        // Plugin field
        if (!entry.plugins.isEmpty()) {
            JsonObject pluginField = new JsonObject();
            pluginField.addProperty("name", "Plugins (" + entry.plugins.size() + ")");
            String pluginStr = String.join(", ", entry.plugins);
            if (pluginStr.length() > 1024) pluginStr = pluginStr.substring(0, 1020) + "...";
            pluginField.addProperty("value", pluginStr);
            pluginField.addProperty("inline", false);

            JsonArray fields = new JsonArray();
            fields.add(pluginField);
            embed.add("fields", fields);
        }

        // Footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Archivist " + entry.timestamp);
        embed.add("footer", footer);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        return new GsonBuilder().create().toJson(payload);
    }
}
