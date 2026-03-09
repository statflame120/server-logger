package com.archivist.database;

import com.archivist.ArchivistMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;

public class ApiConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("api_config.json");

    public boolean enabled = false;
    public String baseUrl = "";
    public String pushEndpoint = "/push";
    public String downloadEndpoint = "/download";
    public String resetEndpoint = "/reset";

    // Stored Base64-encoded on disk. Keys are header names, values are Base64-encoded header values.
    private Map<String, String> authHeadersEncoded = new LinkedHashMap<>();

    // Stored Base64-encoded on disk
    private String resetKeyEncoded = "";

    public boolean autoPush = true;
    public int pushMessageLimit = 500;

    // --- Runtime accessors (decode on the fly) ---

    public String getBaseUrl() { return baseUrl; }
    public String getPushEndpoint() { return pushEndpoint; }
    public String getDownloadEndpoint() { return downloadEndpoint; }
    public String getResetEndpoint() { return resetEndpoint; }

    /** Get decoded auth headers for HTTP requests. */
    public Map<String, String> getDecodedAuthHeaders() {
        Map<String, String> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : authHeadersEncoded.entrySet()) {
            try {
                decoded.put(e.getKey(), new String(Base64.getDecoder().decode(e.getValue())));
            } catch (Exception ex) {
                decoded.put(e.getKey(), e.getValue()); // fallback if not valid base64
            }
        }
        return decoded;
    }

    /** Add or update an auth header. Value is stored Base64-encoded. */
    public void setAuthHeader(String name, String plainValue) {
        authHeadersEncoded.put(name, Base64.getEncoder().encodeToString(plainValue.getBytes()));
    }

    /** Remove an auth header by name. */
    public void removeAuthHeader(String name) {
        authHeadersEncoded.remove(name);
    }

    /** Get all auth header names (for display). */
    public Set<String> getAuthHeaderNames() {
        return Collections.unmodifiableSet(authHeadersEncoded.keySet());
    }

    /** Check if a specific header is set (non-empty). */
    public boolean hasAuthHeader(String name) {
        String val = authHeadersEncoded.get(name);
        return val != null && !val.isEmpty();
    }

    /** Get decoded reset key. */
    public String getDecodedResetKey() {
        if (resetKeyEncoded == null || resetKeyEncoded.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(resetKeyEncoded));
        } catch (Exception e) {
            return resetKeyEncoded;
        }
    }

    /** Set reset key. Stored Base64-encoded. */
    public void setResetKey(String plainValue) {
        this.resetKeyEncoded = Base64.getEncoder().encodeToString(plainValue.getBytes());
    }

    /** Check if the API is configured enough to make requests. */
    public boolean isConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && !authHeadersEncoded.isEmpty();
    }

    /** Mask a secret value for display: show first 4 and last 3 chars. */
    public static String maskSecret(String value) {
        if (value == null || value.length() <= 7) return "••••••";
        return value.substring(0, 4) + "•".repeat(Math.max(3, value.length() - 7)) + value.substring(value.length() - 3);
    }

    // --- Persistence ---

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();

            if (obj.has("enabled")) enabled = obj.get("enabled").getAsBoolean();
            if (obj.has("baseUrl")) baseUrl = obj.get("baseUrl").getAsString();
            if (obj.has("pushEndpoint")) pushEndpoint = obj.get("pushEndpoint").getAsString();
            if (obj.has("downloadEndpoint")) downloadEndpoint = obj.get("downloadEndpoint").getAsString();
            if (obj.has("resetEndpoint")) resetEndpoint = obj.get("resetEndpoint").getAsString();
            if (obj.has("autoPush")) autoPush = obj.get("autoPush").getAsBoolean();
            if (obj.has("pushMessageLimit")) pushMessageLimit = obj.get("pushMessageLimit").getAsInt();
            if (obj.has("resetKey")) resetKeyEncoded = obj.get("resetKey").getAsString();

            if (obj.has("authHeaders")) {
                authHeadersEncoded.clear();
                JsonObject headers = obj.getAsJsonObject("authHeaders");
                for (Map.Entry<String, JsonElement> e : headers.entrySet()) {
                    authHeadersEncoded.put(e.getKey(), e.getValue().getAsString());
                }
            }

        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load API config: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            obj.addProperty("baseUrl", baseUrl);
            obj.addProperty("pushEndpoint", pushEndpoint);
            obj.addProperty("downloadEndpoint", downloadEndpoint);
            obj.addProperty("resetEndpoint", resetEndpoint);
            obj.addProperty("autoPush", autoPush);
            obj.addProperty("pushMessageLimit", pushMessageLimit);
            obj.addProperty("resetKey", resetKeyEncoded);

            JsonObject headers = new JsonObject();
            for (Map.Entry<String, String> e : authHeadersEncoded.entrySet()) {
                headers.addProperty(e.getKey(), e.getValue());
            }
            obj.add("authHeaders", headers);

            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to save API config: {}", e.getMessage());
        }
    }
}
