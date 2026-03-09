package com.archivist;

import com.archivist.fingerprint.FingerprintMatch;
import com.archivist.util.ArchivistExecutor;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JsonLogger {

    public static void write(ServerDataCollector data) {
        // Snapshot mutable state on the calling thread, then do I/O in background
        final String ip = data.ip;
        final int port = data.port;
        final String domain = data.domain;
        final String brand = data.brand;
        final String version = data.version;
        final int playerCount = data.playerCount;
        final String dimension = data.dimension;
        final String resourcePack = data.resourcePack;
        final List<String> plugins = new ArrayList<>(data.getPlugins());
        final List<String> detectedAddresses = new ArrayList<>(data.getDetectedAddresses());
        final List<String> detectedGameAddresses = new ArrayList<>(data.getDetectedGameAddresses());
        final List<FingerprintMatch> guiMatches = new ArrayList<>(
                com.archivist.fingerprint.GuiFingerprintEngine.getInstance().getMatches());
        final String logFolder = ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.config.logFolder : "archivist/logs";

        ArchivistExecutor.run(() -> writeImpl(ip, port, domain, brand, version, playerCount,
                dimension, resourcePack, plugins, detectedAddresses, detectedGameAddresses,
                guiMatches, logFolder));
    }

    private static void writeImpl(String ip, int port, String domain, String brand,
                                   String version, int playerCount, String dimension,
                                   String resourcePack, List<String> plugins,
                                   List<String> detectedAddresses,
                                   List<String> detectedGameAddresses,
                                   List<FingerprintMatch> guiMatches,
                                   String logFolder) {
        try {
            Path logDir = FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(logFolder);
            Files.createDirectories(logDir);

            String baseName = (!domain.equals("unknown") && !domain.equals(ip))
                    ? domain.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : ip.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + port;
            Path outFile = logDir.resolve(baseName + ".json");

            JsonArray pluginsArr = new JsonArray();
            for (String name : plugins) {
                JsonObject p = new JsonObject();
                p.addProperty("name", name);
                pluginsArr.add(p);
            }

            JsonArray addrArr = new JsonArray();
            detectedAddresses.forEach(addrArr::add);

            JsonArray gameAddrArr = new JsonArray();
            detectedGameAddresses.forEach(gameAddrArr::add);

            JsonObject currentWorld = new JsonObject();
            currentWorld.addProperty("timestamp", LocalDate.now().toString());
            currentWorld.addProperty("dimension", dimension);
            if (resourcePack != null) {
                currentWorld.addProperty("resource_pack", resourcePack);
            }

            JsonArray finalPlugins = pluginsArr;
            JsonArray worldsArr    = new JsonArray();

            if (Files.exists(outFile)) {
                try {
                    JsonObject existing = JsonParser.parseString(Files.readString(outFile)).getAsJsonObject();

                    JsonArray existingPlugins = existing.has("plugins")
                            ? existing.getAsJsonArray("plugins") : null;
                    if (existingPlugins != null && existingPlugins.size() > pluginsArr.size()) {
                        finalPlugins = existingPlugins;
                    }

                    if (existing.has("worlds")) {
                        worldsArr = existing.getAsJsonArray("worlds");
                    } else if (existing.has("world")) {
                        JsonObject oldWorld = existing.getAsJsonObject("world");
                        JsonObject migrated = new JsonObject();
                        migrated.addProperty("timestamp", existing.has("timestamp")
                                ? existing.get("timestamp").getAsString()
                                : LocalDate.now().toString());
                        if (oldWorld.has("dimension"))
                            migrated.addProperty("dimension", oldWorld.get("dimension").getAsString());
                        if (oldWorld.has("resource_pack"))
                            migrated.addProperty("resource_pack", oldWorld.get("resource_pack").getAsString());
                        worldsArr.add(migrated);
                    }

                    boolean worldExists = false;
                    for (JsonElement el : worldsArr) {
                        JsonObject w = el.getAsJsonObject();
                        String dim  = w.has("dimension")     ? w.get("dimension").getAsString()     : "";
                        String rp   = w.has("resource_pack") ? w.get("resource_pack").getAsString() : null;
                        if (dim.equals(dimension) && Objects.equals(rp, resourcePack)) {
                            worldExists = true;
                            break;
                        }
                    }

                    if (!worldExists) {
                        worldsArr.add(currentWorld);
                    } else if (finalPlugins == pluginsArr && existingPlugins != null
                            && existingPlugins.size() >= pluginsArr.size()) {
                        ArchivistMod.sendMessage("No new data for " + outFile.getFileName() + ", skipping write");
                        return;
                    }

                } catch (Exception e) {
                    worldsArr.add(currentWorld);
                }
            } else {
                worldsArr.add(currentWorld);
            }

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", LocalDate.now().toString());

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("ip",           ip);
            serverInfo.addProperty("port",         port);
            serverInfo.addProperty("domain",       domain);
            serverInfo.addProperty("brand",        brand);
            serverInfo.addProperty("version",      version);
            serverInfo.addProperty("player_count", playerCount);
            root.add("server_info", serverInfo);

            root.add("plugins",                  finalPlugins);

            // GUI-fingerprinted plugins
            JsonArray guiPluginsArr = new JsonArray();
            for (FingerprintMatch m : guiMatches) {
                JsonObject gp = new JsonObject();
                gp.addProperty("pluginId", m.pluginId());
                gp.addProperty("pluginName", m.pluginName());
                gp.addProperty("confidence", m.confidence());
                gp.addProperty("inventoryTitle", m.inventoryTitle());
                gp.addProperty("matchedPatterns", m.matchedPatterns());
                guiPluginsArr.add(gp);
            }
            if (guiPluginsArr.size() > 0) {
                root.add("gui_plugins", guiPluginsArr);
            }

            root.add("detected_addresses",       addrArr);
            root.add("detected_game_addresses",  gameAddrArr);
            root.add("worlds",                   worldsArr);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ArchivistMod.sendMessage("Log saved: " + outFile.getFileName());

        } catch (IOException e) {
            ArchivistMod.sendMessage("Failed to write log: " + e.getMessage());
        }
    }
}
