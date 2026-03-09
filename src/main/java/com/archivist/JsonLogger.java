package com.archivist;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class JsonLogger {

    public static void write(ServerDataCollector data) {
        try {
            Path logDir = FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(ArchivistMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);

            String baseName = (!data.domain.equals("unknown") && !data.domain.equals(data.ip))
                    ? data.domain.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : data.ip.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + data.port;
            Path outFile = logDir.resolve(baseName + ".json");

            Set<String> newPluginNames = new LinkedHashSet<>();
            for (String name : data.getPlugins()) newPluginNames.add(name);

            JsonArray addrArr = new JsonArray();
            data.getDetectedAddresses().forEach(addrArr::add);

            JsonArray gameAddrArr = new JsonArray();
            data.getDetectedGameAddresses().forEach(gameAddrArr::add);

            String now = Instant.now().toString();

            JsonObject currentWorld = new JsonObject();
            currentWorld.addProperty("timestamp", now);
            currentWorld.addProperty("dimension", data.dimension);
            if (data.resourcePack != null) {
                currentWorld.addProperty("resource_pack", data.resourcePack);
            }

            Set<String> mergedPluginNames = new LinkedHashSet<>(newPluginNames);
            JsonArray worldsArr = new JsonArray();

            if (Files.exists(outFile)) {
                try {
                    JsonObject existing = JsonParser.parseString(Files.readString(outFile)).getAsJsonObject();

                    JsonArray existingPlugins = existing.has("plugins")
                            ? existing.getAsJsonArray("plugins") : null;
                    if (existingPlugins != null) {
                        for (JsonElement el : existingPlugins) {
                            if (el.isJsonObject()) {
                                JsonObject p = el.getAsJsonObject();
                                if (p.has("name")) mergedPluginNames.add(p.get("name").getAsString());
                            } else if (el.isJsonPrimitive()) {
                                mergedPluginNames.add(el.getAsString());
                            }
                        }
                    }

                    if (existing.has("worlds")) {
                        worldsArr = existing.getAsJsonArray("worlds");
                    } else if (existing.has("world")) {
                        JsonObject oldWorld = existing.getAsJsonObject("world");
                        JsonObject migrated = new JsonObject();
                        migrated.addProperty("timestamp", existing.has("timestamp")
                                ? existing.get("timestamp").getAsString()
                                : now);
                        if (oldWorld.has("dimension"))
                            migrated.addProperty("dimension", oldWorld.get("dimension").getAsString());
                        if (oldWorld.has("resource_pack"))
                            migrated.addProperty("resource_pack", oldWorld.get("resource_pack").getAsString());
                        worldsArr.add(migrated);
                    }

                    boolean worldExists = false;
                    for (JsonElement el : worldsArr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject w = el.getAsJsonObject();
                        String dim  = w.has("dimension")     ? w.get("dimension").getAsString()     : "";
                        String rp   = w.has("resource_pack") ? w.get("resource_pack").getAsString() : null;
                        if (dim.equals(data.dimension) && Objects.equals(rp, data.resourcePack)) {
                            worldExists = true;
                            break;
                        }
                    }

                    if (!worldExists) {
                        worldsArr.add(currentWorld);
                    }

                } catch (Exception e) {
                    worldsArr.add(currentWorld);
                }
            } else {
                worldsArr.add(currentWorld);
            }

            JsonArray finalPlugins = new JsonArray();
            List<String> sortedPlugins = new ArrayList<>(mergedPluginNames);
            sortedPlugins.sort(String.CASE_INSENSITIVE_ORDER);
            for (String name : sortedPlugins) {
                JsonObject p = new JsonObject();
                p.addProperty("name", name);
                finalPlugins.add(p);
            }

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", now);

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("ip",           data.ip);
            serverInfo.addProperty("port",         data.port);
            serverInfo.addProperty("domain",       data.domain);
            serverInfo.addProperty("brand",        data.brand);
            serverInfo.addProperty("version",      data.version);
            serverInfo.addProperty("player_count", data.playerCount);
            if (data.motd != null && !data.motd.isBlank()) {
                serverInfo.addProperty("motd", data.motd);
            }
            root.add("server_info", serverInfo);

            root.add("plugins",                  finalPlugins);
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
