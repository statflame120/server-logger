package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.*;

/**
 * Local SQLite storage via JDBC.
 * Requires sqlite-jdbc to be shaded into the mod jar.
 * Falls back gracefully if the driver is not available.
 */
public class SqliteAdapter implements DatabaseAdapter {

    private Connection connection;

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        // Ensure the driver class is loaded
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found. " +
                    "The sqlite-jdbc library must be shaded into the mod jar.", e);
        }

        if (connectionString == null || connectionString.isBlank()) {
            connectionString = "jdbc:sqlite:archivist_logs.db";
        }
        if (!connectionString.startsWith("jdbc:sqlite:")) {
            connectionString = "jdbc:sqlite:" + connectionString;
        }

        connection = DriverManager.getConnection(connectionString);

        // Create table if not exists
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS server_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    server_name TEXT NOT NULL,
                    server_ip TEXT,
                    port INTEGER,
                    brand TEXT,
                    version TEXT,
                    timestamp TEXT,
                    plugins TEXT,
                    world_info TEXT,
                    raw_json TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }

        ArchivistMod.LOGGER.info("[Archivist] SQLite connected: {}", connectionString);
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
            throw new IllegalStateException("Not connected to SQLite");
        }

        String sql = """
            INSERT INTO server_logs (server_name, server_ip, port, brand, version, timestamp, plugins, world_info, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, entry.getDisplayName());
            pstmt.setString(2, entry.ip);
            pstmt.setInt(3, entry.port);
            pstmt.setString(4, entry.brand);
            pstmt.setString(5, entry.version);
            pstmt.setString(6, entry.timestamp);
            pstmt.setString(7, String.join(",", entry.plugins));

            // World info as JSON
            JsonArray worldsArr = new JsonArray();
            for (ServerLogData.WorldSession ws : entry.worlds) {
                JsonObject w = new JsonObject();
                w.addProperty("dimension", ws.dimension);
                w.addProperty("timestamp", ws.timestamp);
                worldsArr.add(w);
            }
            pstmt.setString(8, worldsArr.toString());

            // Full JSON representation
            pstmt.setString(9, serializeToJson(entry));

            pstmt.executeUpdate();
        }

        ArchivistMod.LOGGER.info("[Archivist] SQLite upload: {}", entry.getDisplayName());
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
        return "SQLite";
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
}
