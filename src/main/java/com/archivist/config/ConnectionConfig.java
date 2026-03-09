package com.archivist.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;

/**
 * Unified connection configuration supporting multiple database types and REST API.
 * Supports multiple named connections with one active for auto-push.
 */
public class ConnectionConfig {

    public enum ConnectionType {
        MYSQL, POSTGRESQL, MONGODB, SQLITE, REST_API
    }

    public static class Connection {
        public String name = "";
        public ConnectionType type = ConnectionType.REST_API;
        public boolean enabled = false;
        public boolean autoPush = false;

        // DB types
        public String host = "";
        public int port = 3306;
        public String database = "";
        public String username = "";
        public String password = "";  // Base64-encoded in config

        // REST_API type
        public String baseUrl = "";
        public String pushEndpoint = "/push";
        public String downloadEndpoint = "/download";
        public String resetEndpoint = "/reset";
        public Map<String, String> authHeaders = new LinkedHashMap<>();  // Base64-encoded values
        public String resetKey = "";  // Base64-encoded

        public Connection() {}

        public Connection(String name, ConnectionType type) {
            this.name = name;
            this.type = type;
        }

        public String getDecodedPassword() {
            if (password.isEmpty()) return "";
            try {
                return new String(Base64.getDecoder().decode(password));
            } catch (Exception e) {
                return password;
            }
        }

        public void setEncodedPassword(String raw) {
            this.password = Base64.getEncoder().encodeToString(raw.getBytes());
        }

        public Map<String, String> getDecodedAuthHeaders() {
            Map<String, String> decoded = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : authHeaders.entrySet()) {
                try {
                    decoded.put(entry.getKey(), new String(Base64.getDecoder().decode(entry.getValue())));
                } catch (Exception e) {
                    decoded.put(entry.getKey(), entry.getValue());
                }
            }
            return decoded;
        }
    }

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("connections.json");

    private final List<Connection> connections = new ArrayList<>();

    public List<Connection> getConnections() {
        return connections;
    }

    public void addConnection(Connection conn) {
        connections.add(conn);
    }

    public void removeConnection(String name) {
        connections.removeIf(c -> c.name.equals(name));
    }

    public Connection getActiveConnection() {
        return connections.stream().filter(c -> c.enabled).findFirst().orElse(null);
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            Gson gson = new Gson();
            for (JsonElement el : arr) {
                connections.add(gson.fromJson(el, Connection.class));
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(connections);
            Files.writeString(CONFIG_PATH, json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
}
