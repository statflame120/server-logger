package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;
import com.google.gson.*;

import java.sql.*;

/**
 * Remote PostgreSQL storage via JDBC.
 * Requires the PostgreSQL JDBC driver to be shaded into the mod jar.
 */
public class PostgresAdapter implements DatabaseAdapter {

    private Connection connection;

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found. " +
                    "The postgresql driver must be shaded into the mod jar.", e);
        }

        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalArgumentException("PostgreSQL connection string cannot be empty");
        }

        // authToken can be used as password if the connection string doesn't include it
        if (authToken != null && !authToken.isBlank() && !connectionString.contains("password=")) {
            Properties props = new Properties();
            props.setProperty("password", authToken);
            // Extract user from connection string if possible
            connection = DriverManager.getConnection(connectionString, props);
        } else {
            connection = DriverManager.getConnection(connectionString);
        }

        // Create table if not exists
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS server_logs (
                    id SERIAL PRIMARY KEY,
                    server_name TEXT NOT NULL,
                    server_ip TEXT,
                    port INTEGER,
                    brand TEXT,
                    version TEXT,
                    timestamp TEXT,
                    plugins TEXT,
                    world_info JSONB,
                    raw_json JSONB,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);
        }

        ArchivistMod.LOGGER.info("[Archivist] PostgreSQL connected");
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    @Override
    public void upload(ServerLogData entry) throws Exception {
        if (connection == null || connection.isClosed()) {
            throw new IllegalStateException("Not connected to PostgreSQL");
        }

        String sql = """
            INSERT INTO server_logs (server_name, server_ip, port, brand, version, timestamp, plugins, world_info, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, entry.getDisplayName());
            pstmt.setString(2, entry.ip);
            pstmt.setInt(3, entry.port);
            pstmt.setString(4, entry.brand);
            pstmt.setString(5, entry.version);
            pstmt.setString(6, entry.timestamp);
            pstmt.setString(7, String.join(",", entry.plugins));

            JsonArray worldsArr = new JsonArray();
            for (ServerLogData.WorldSession ws : entry.worlds) {
                JsonObject w = new JsonObject();
                w.addProperty("dimension", ws.dimension);
                w.addProperty("timestamp", ws.timestamp);
                worldsArr.add(w);
            }
            pstmt.setString(8, worldsArr.toString());
            pstmt.setString(9, serializeToJson(entry));

            pstmt.executeUpdate();
        }

        ArchivistMod.LOGGER.info("[Archivist] PostgreSQL upload: {}", entry.getDisplayName());
    }

    @Override
    public boolean testConnection() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String displayName() {
        return "PostgreSQL";
    }

    private String serializeToJson(ServerLogData entry) {
        JsonObject root = new JsonObject();
        root.addProperty("serverName", entry.getDisplayName());
        root.addProperty("serverIp", entry.ip);
        root.addProperty("port", entry.port);
        root.addProperty("brand", entry.brand);
        root.addProperty("version", entry.version);
        root.addProperty("timestamp", entry.timestamp);
        JsonArray plugins = new JsonArray();
        entry.plugins.forEach(plugins::add);
        root.add("plugins", plugins);
        return new GsonBuilder().create().toJson(root);
    }

    // Inner Properties class to avoid import clash
    private static class Properties extends java.util.Properties {}
}
