package com.archivist.data;

import com.archivist.ArchivistMod;
import com.archivist.ServerDataCollector;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import com.archivist.gui.ServerLogData;
import com.archivist.util.ArchivistExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports server data to JSON, CSV, or clipboard.
 */
public class LogExporter {

    /** Export current server data as JSON file. Returns file path or null. */
    public static String exportJson() {
        if (ArchivistMod.INSTANCE == null) return null;
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;

        try {
            JsonObject root = buildExportJson(dc);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);

            Path dir = LogStorage.getExportDir();
            String fileName = "export_" + timestamp() + ".json";
            Path outFile = dir.resolve(fileName);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return outFile.toAbsolutePath().toString();
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] JSON export failed: {}", e.getMessage());
            return null;
        }
    }

    /** Export current server data as CSV file. Returns file path or null. */
    public static String exportCsv() {
        if (ArchivistMod.INSTANCE == null) return null;
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;

        try {
            StringBuilder csv = new StringBuilder();
            csv.append("Field,Value\n");
            csv.append("IP,").append(escapeCsv(dc.ip)).append("\n");
            csv.append("Port,").append(dc.port).append("\n");
            csv.append("Domain,").append(escapeCsv(dc.domain)).append("\n");
            csv.append("Brand,").append(escapeCsv(dc.brand)).append("\n");
            csv.append("Version,").append(escapeCsv(dc.version)).append("\n");
            csv.append("Dimension,").append(escapeCsv(dc.dimension)).append("\n");
            csv.append("Players,").append(dc.playerCount).append("\n");
            csv.append("Plugins,\"").append(String.join("; ", dc.getPlugins())).append("\"\n");

            // Events
            csv.append("\nTimestamp,Type,Message\n");
            for (LogEvent event : EventBus.getEvents()) {
                csv.append(escapeCsv(event.getTimestamp())).append(",");
                csv.append(event.getType().name()).append(",");
                csv.append(escapeCsv(event.getMessage())).append("\n");
            }

            Path dir = LogStorage.getExportDir();
            String fileName = "export_" + timestamp() + ".csv";
            Path outFile = dir.resolve(fileName);
            Files.writeString(outFile, csv.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return outFile.toAbsolutePath().toString();
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] CSV export failed: {}", e.getMessage());
            return null;
        }
    }

    /** Export world info to file. Runs I/O on background thread. */
    public static void exportWorldInfo() {
        if (ArchivistMod.INSTANCE == null) return;
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;

        // Snapshot mutable state on calling thread
        String domain = dc.domain;
        String ip = dc.ip;
        int port = dc.port;
        String dimension = dc.dimension;
        String resourcePack = dc.resourcePack;

        ArchivistExecutor.run(() -> {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("exportedAt", Instant.now().toString());
                root.addProperty("domain", domain);
                root.addProperty("ip", ip);
                root.addProperty("port", port);
                root.addProperty("dimension", dimension);
                if (resourcePack != null) root.addProperty("resourcePack", resourcePack);

                String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
                Path dir = LogStorage.getExportDir();
                String fileName = "worldinfo_" + timestamp() + ".json";
                Path outFile = dir.resolve(fileName);
                Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                EventBus.post(LogEvent.Type.SYSTEM, "Exported: " + outFile.toAbsolutePath());
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] World info export failed: {}", e.getMessage());
                EventBus.post(LogEvent.Type.ERROR, "World info export failed");
            }
        });
    }

    /** Export a server log entry to file. Runs I/O on background thread. */
    public static void exportServerLog(ServerLogData log) {
        // Snapshot all data from the log (it's already immutable final fields)
        String addr = log.getDisplayName();

        ArchivistExecutor.run(() -> {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("timestamp", log.timestamp);
                JsonObject info = new JsonObject();
                info.addProperty("ip", log.ip);
                info.addProperty("port", log.port);
                info.addProperty("domain", log.domain);
                info.addProperty("brand", log.brand);
                info.addProperty("version", log.version);
                info.addProperty("player_count", log.playerCount);
                root.add("server_info", info);

                JsonArray plugins = new JsonArray();
                for (String p : log.plugins) {
                    JsonObject po = new JsonObject();
                    po.addProperty("name", p);
                    plugins.add(po);
                }
                root.add("plugins", plugins);

                String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
                Path dir = LogStorage.getExportDir();
                String safeName = addr.replaceAll("[^a-zA-Z0-9._-]", "_");
                String fileName = "serverlog_" + safeName + "_" + timestamp() + ".json";
                Path outFile = dir.resolve(fileName);
                Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                EventBus.post(LogEvent.Type.SYSTEM, "Exported: " + outFile.toAbsolutePath());
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Server log export failed: {}", e.getMessage());
                EventBus.post(LogEvent.Type.ERROR, "Server log export failed");
            }
        });
    }

    /** Copy current server summary to clipboard. */
    public static void exportToClipboard() {
        if (ArchivistMod.INSTANCE == null) return;
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;

        StringBuilder sb = new StringBuilder();
        sb.append("Server: ").append(dc.domain).append(" (").append(dc.ip).append(":").append(dc.port).append(")\n");
        sb.append("Brand: ").append(dc.brand).append("\n");
        sb.append("Version: ").append(dc.version).append("\n");
        sb.append("Players: ").append(dc.playerCount).append("\n");

        List<String> plugins = dc.getPlugins();
        if (!plugins.isEmpty()) {
            sb.append("Plugins (").append(plugins.size()).append("): ");
            sb.append(String.join(", ", plugins)).append("\n");
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    private static JsonObject buildExportJson(ServerDataCollector dc) {
        JsonObject root = new JsonObject();
        root.addProperty("exportedAt", Instant.now().toString());

        JsonObject server = new JsonObject();
        server.addProperty("ip", dc.ip);
        server.addProperty("port", dc.port);
        server.addProperty("domain", dc.domain);
        server.addProperty("brand", dc.brand);
        server.addProperty("version", dc.version);
        server.addProperty("dimension", dc.dimension);
        server.addProperty("playerCount", dc.playerCount);
        root.add("server", server);

        JsonArray plugins = new JsonArray();
        dc.getPlugins().forEach(plugins::add);
        root.add("plugins", plugins);

        JsonArray events = new JsonArray();
        for (LogEvent event : EventBus.getEvents()) {
            JsonObject e = new JsonObject();
            e.addProperty("time", event.getTimestamp());
            e.addProperty("type", event.getType().name());
            e.addProperty("data", event.getMessage());
            events.add(e);
        }
        root.add("events", events);

        return root;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
