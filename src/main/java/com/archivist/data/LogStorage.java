package com.archivist.data;

import com.archivist.ArchivistMod;
import com.archivist.ServerDataCollector;
import com.archivist.util.ArchivistExecutor;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Manages JSON log file storage. One file per server.
 * Location: .minecraft/archivist/logs/
 *
 * Reads existing logs for the GUI and writes new session data on join/leave.
 */
public class LogStorage {

    private static Path getLogDir() {
        return FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("logs");
    }

    /** Save the current session data for the connected server. */
    public static void saveCurrentSession() {
        if (ArchivistMod.INSTANCE == null) return;
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;
        if ("unknown".equals(dc.domain) && "unknown".equals(dc.ip)) return;

        // Snapshot data on the calling thread
        final String sDomain = dc.domain;
        final String sIp = dc.ip;
        final int sPort = dc.port;
        final String sVersion = dc.version;
        final String sBrand = dc.brand;
        final String sDimension = dc.dimension;
        final String sResourcePack = dc.resourcePack;
        final List<String> sPlugins = new ArrayList<>(dc.getPlugins());
        final List<LogEvent> sEvents = EventBus.getEvents();

        ArchivistExecutor.run(() -> saveSessionImpl(sDomain, sIp, sPort, sVersion, sBrand,
                sDimension, sResourcePack, sPlugins, sEvents));
    }

    private static void saveSessionImpl(String domain, String ip, int port, String version,
                                         String brand, String dimension, String resourcePack,
                                         List<String> pluginList, List<LogEvent> eventList) {
        try {
            Path logDir = getLogDir();
            Files.createDirectories(logDir);

            String baseName = buildFileName(domain, ip, port);
            Path outFile = logDir.resolve(baseName + ".json");

            // Build session
            JsonObject session = new JsonObject();
            session.addProperty("timestamp", Instant.now().toString());
            session.addProperty("version", version);
            session.addProperty("brand", brand);

            JsonArray plugins = new JsonArray();
            pluginList.forEach(plugins::add);
            session.add("plugins", plugins);

            JsonObject world = new JsonObject();
            world.addProperty("dimension", dimension);
            if (resourcePack != null) world.addProperty("resourcePack", resourcePack);
            session.add("world", world);

            JsonArray events = new JsonArray();
            for (LogEvent event : eventList) {
                JsonObject e = new JsonObject();
                e.addProperty("time", event.getTimestamp());
                e.addProperty("type", event.getType().name());
                e.addProperty("data", event.getMessage());
                events.add(e);
            }
            session.add("events", events);

            // Load or create root
            JsonObject root;
            JsonArray sessions;
            if (Files.exists(outFile)) {
                try (Reader r = Files.newBufferedReader(outFile)) {
                    root = JsonParser.parseReader(r).getAsJsonObject();
                    sessions = root.has("sessions") ? root.getAsJsonArray("sessions") : new JsonArray();
                } catch (Exception e) {
                    root = new JsonObject();
                    sessions = new JsonArray();
                }
            } else {
                root = new JsonObject();
                sessions = new JsonArray();
            }

            // Server info
            JsonObject server = new JsonObject();
            server.addProperty("ip", ip);
            server.addProperty("port", port);
            server.addProperty("lastVisited", Instant.now().toString());
            if (!domain.equals("unknown")) server.addProperty("domain", domain);
            root.add("server", server);

            sessions.add(session);
            root.add("sessions", sessions);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to save session: {}", e.getMessage());
        }
    }

    /** Read all log files and return structured data. */
    public static List<JsonObject> readAllLogs() {
        List<JsonObject> results = new ArrayList<>();
        Path logDir = getLogDir();
        if (!Files.isDirectory(logDir)) return results;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.json")) {
            for (Path file : stream) {
                try (Reader r = Files.newBufferedReader(file)) {
                    results.add(JsonParser.parseReader(r).getAsJsonObject());
                } catch (Exception e) {
                    ArchivistMod.LOGGER.warn("[Archivist] Failed to parse {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to read log dir: {}", e.getMessage());
        }

        return results;
    }

    /** Get the exports directory, creating it if needed. */
    public static Path getExportDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("exports");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir;
    }

    private static String buildFileName(String domain, String ip, int port) {
        String base = (!domain.equals("unknown") && !domain.equals(ip))
                ? domain : ip + "_" + port;
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
