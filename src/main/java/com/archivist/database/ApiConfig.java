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

    // ── Endpoint list ────────────────────────────────────────────────────────

    public List<EndpointConfig> endpoints = new ArrayList<>();
    public String selectedEndpointId = "";

    public static class EndpointConfig {
        public String id = UUID.randomUUID().toString();
        public String name = "New Endpoint";
        public String type = "REST API"; // "Discord", "REST API", "Custom"
        public String url = "";
        public String method = "POST"; // POST, PUT, PATCH (Custom only)
        public String pushEndpoint = "/push";
        public String downloadEndpoint = "/download";
        public String resetEndpoint = "/reset";
        public Map<String, String> authHeadersEncoded = new LinkedHashMap<>();
        public String resetKeyEncoded = "";
        public boolean autoPush = false;
        public boolean enabled = true;

        // ── Auth header helpers ──

        public Map<String, String> getDecodedAuthHeaders() {
            Map<String, String> decoded = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : authHeadersEncoded.entrySet()) {
                try {
                    decoded.put(e.getKey(), new String(Base64.getDecoder().decode(e.getValue())));
                } catch (Exception ex) {
                    decoded.put(e.getKey(), e.getValue());
                }
            }
            return decoded;
        }

        public void setAuthHeader(String name, String plainValue) {
            authHeadersEncoded.put(name, Base64.getEncoder().encodeToString(plainValue.getBytes()));
        }

        public void removeAuthHeader(String name) {
            authHeadersEncoded.remove(name);
        }

        public Set<String> getAuthHeaderNames() {
            return Collections.unmodifiableSet(authHeadersEncoded.keySet());
        }

        public String getDecodedResetKey() {
            if (resetKeyEncoded == null || resetKeyEncoded.isEmpty()) return "";
            try {
                return new String(Base64.getDecoder().decode(resetKeyEncoded));
            } catch (Exception e) {
                return resetKeyEncoded;
            }
        }

        public void setResetKey(String plainValue) {
            this.resetKeyEncoded = Base64.getEncoder().encodeToString(plainValue.getBytes());
        }

        public boolean isConfigured() {
            return enabled && url != null && !url.isBlank();
        }

        private JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", name);
            obj.addProperty("type", type);
            obj.addProperty("url", url);
            obj.addProperty("method", method);
            obj.addProperty("pushEndpoint", pushEndpoint);
            obj.addProperty("downloadEndpoint", downloadEndpoint);
            obj.addProperty("resetEndpoint", resetEndpoint);
            obj.addProperty("resetKey", resetKeyEncoded);
            obj.addProperty("autoPush", autoPush);
            obj.addProperty("enabled", enabled);
            JsonObject headers = new JsonObject();
            for (Map.Entry<String, String> e : authHeadersEncoded.entrySet()) {
                headers.addProperty(e.getKey(), e.getValue());
            }
            obj.add("authHeaders", headers);
            return obj;
        }

        private static EndpointConfig fromJson(JsonObject obj) {
            EndpointConfig ep = new EndpointConfig();
            if (obj.has("id")) ep.id = obj.get("id").getAsString();
            if (obj.has("name")) ep.name = obj.get("name").getAsString();
            if (obj.has("type")) ep.type = obj.get("type").getAsString();
            if (obj.has("url")) ep.url = obj.get("url").getAsString();
            if (obj.has("method")) ep.method = obj.get("method").getAsString();
            if (obj.has("pushEndpoint")) ep.pushEndpoint = obj.get("pushEndpoint").getAsString();
            if (obj.has("downloadEndpoint")) ep.downloadEndpoint = obj.get("downloadEndpoint").getAsString();
            if (obj.has("resetEndpoint")) ep.resetEndpoint = obj.get("resetEndpoint").getAsString();
            if (obj.has("resetKey")) ep.resetKeyEncoded = obj.get("resetKey").getAsString();
            if (obj.has("autoPush")) ep.autoPush = obj.get("autoPush").getAsBoolean();
            if (obj.has("enabled")) ep.enabled = obj.get("enabled").getAsBoolean();
            if (obj.has("authHeaders")) {
                ep.authHeadersEncoded.clear();
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("authHeaders").entrySet()) {
                    ep.authHeadersEncoded.put(e.getKey(), e.getValue().getAsString());
                }
            }
            return ep;
        }
    }

    // ── Convenience ──────────────────────────────────────────────────────────

    public EndpointConfig getSelectedEndpoint() {
        for (EndpointConfig ep : endpoints) {
            if (ep.id.equals(selectedEndpointId)) return ep;
        }
        return endpoints.isEmpty() ? null : endpoints.get(0);
    }

    public EndpointConfig addEndpoint() {
        EndpointConfig ep = new EndpointConfig();
        ep.name = "Endpoint " + (endpoints.size() + 1);
        endpoints.add(ep);
        selectedEndpointId = ep.id;
        return ep;
    }

    public void removeEndpoint(String id) {
        if (endpoints.size() <= 1) return; // must keep at least one
        endpoints.removeIf(ep -> ep.id.equals(id));
        if (selectedEndpointId.equals(id) && !endpoints.isEmpty()) {
            selectedEndpointId = endpoints.get(0).id;
        }
    }

    /** Get all endpoints with autoPush enabled. */
    public List<EndpointConfig> getAutoPushEndpoints() {
        List<EndpointConfig> result = new ArrayList<>();
        for (EndpointConfig ep : endpoints) {
            if (ep.enabled && ep.autoPush && ep.isConfigured()) result.add(ep);
        }
        return result;
    }

    // Legacy compatibility delegates — used by old code paths
    public boolean enabled = false;
    public String baseUrl = "";
    public String pushEndpoint = "/push";
    public String downloadEndpoint = "/download";
    public String resetEndpoint = "/reset";
    public boolean autoPush = true;
    public int pushMessageLimit = 500;

    // Legacy delegates that route to selected endpoint
    public String getBaseUrl() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.url : baseUrl;
    }
    public String getPushEndpoint() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.pushEndpoint : pushEndpoint;
    }
    public String getDownloadEndpoint() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.downloadEndpoint : downloadEndpoint;
    }
    public String getResetEndpoint() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.resetEndpoint : resetEndpoint;
    }
    public Map<String, String> getDecodedAuthHeaders() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.getDecodedAuthHeaders() : new LinkedHashMap<>();
    }
    public void setAuthHeader(String name, String plainValue) {
        EndpointConfig ep = getSelectedEndpoint();
        if (ep != null) ep.setAuthHeader(name, plainValue);
    }
    public void removeAuthHeader(String name) {
        EndpointConfig ep = getSelectedEndpoint();
        if (ep != null) ep.removeAuthHeader(name);
    }
    public Set<String> getAuthHeaderNames() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.getAuthHeaderNames() : Collections.emptySet();
    }
    public boolean hasAuthHeader(String name) {
        EndpointConfig ep = getSelectedEndpoint();
        if (ep == null) return false;
        String val = ep.authHeadersEncoded.get(name);
        return val != null && !val.isEmpty();
    }
    public String getDecodedResetKey() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null ? ep.getDecodedResetKey() : "";
    }
    public void setResetKey(String plainValue) {
        EndpointConfig ep = getSelectedEndpoint();
        if (ep != null) ep.setResetKey(plainValue);
    }
    public boolean isConfigured() {
        EndpointConfig ep = getSelectedEndpoint();
        return ep != null && ep.isConfigured();
    }

    public static String maskSecret(String value) {
        if (value == null || value.length() <= 7) return "••••••";
        return value.substring(0, 4) + "•".repeat(Math.max(3, value.length() - 7)) + value.substring(value.length() - 3);
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            // Create default endpoint
            if (endpoints.isEmpty()) addEndpoint();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();

            // New format: endpoints array
            if (obj.has("endpoints")) {
                endpoints.clear();
                for (JsonElement el : obj.getAsJsonArray("endpoints")) {
                    endpoints.add(EndpointConfig.fromJson(el.getAsJsonObject()));
                }
                if (obj.has("selectedEndpointId")) {
                    selectedEndpointId = obj.get("selectedEndpointId").getAsString();
                }
            } else {
                // ── Migration from old flat format ──
                EndpointConfig ep = new EndpointConfig();
                ep.name = "Migrated Endpoint";
                ep.type = "REST API";
                if (obj.has("baseUrl")) ep.url = obj.get("baseUrl").getAsString();
                if (obj.has("pushEndpoint")) ep.pushEndpoint = obj.get("pushEndpoint").getAsString();
                if (obj.has("downloadEndpoint")) ep.downloadEndpoint = obj.get("downloadEndpoint").getAsString();
                if (obj.has("resetEndpoint")) ep.resetEndpoint = obj.get("resetEndpoint").getAsString();
                if (obj.has("autoPush")) ep.autoPush = obj.get("autoPush").getAsBoolean();
                if (obj.has("resetKey")) ep.resetKeyEncoded = obj.get("resetKey").getAsString();
                if (obj.has("enabled")) ep.enabled = obj.get("enabled").getAsBoolean();
                if (obj.has("authHeaders")) {
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("authHeaders").entrySet()) {
                        ep.authHeadersEncoded.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                endpoints.clear();
                endpoints.add(ep);
                selectedEndpointId = ep.id;
                // Save in new format immediately
                save();
                ArchivistMod.LOGGER.info("[Archivist] Migrated old API config to endpoint format");
            }

            // Legacy fields for backward compat
            if (obj.has("pushMessageLimit")) pushMessageLimit = obj.get("pushMessageLimit").getAsInt();

            if (endpoints.isEmpty()) addEndpoint();

        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load API config: {}", e.getMessage());
            if (endpoints.isEmpty()) addEndpoint();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            JsonObject obj = new JsonObject();
            JsonArray endpointsArr = new JsonArray();
            for (EndpointConfig ep : endpoints) {
                endpointsArr.add(ep.toJson());
            }
            obj.add("endpoints", endpointsArr);
            obj.addProperty("selectedEndpointId", selectedEndpointId);
            obj.addProperty("pushMessageLimit", pushMessageLimit);

            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to save API config: {}", e.getMessage());
        }
    }
}
