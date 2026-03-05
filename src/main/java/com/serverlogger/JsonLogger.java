package com.serverlogger;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Objects;

public class JsonLogger {

    public static void write(ServerDataCollector data) {
        try {
            Path logDir = FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);

            String baseName = (!data.domain.equals("unknown") && !data.domain.equals(data.ip))
                    ? data.domain.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : data.ip.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + data.port;
            Path outFile = logDir.resolve(baseName + ".json");

            JsonArray pluginsArr = new JsonArray();
            for (String name : data.getPlugins()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", name);
                pluginsArr.add(p);
            }

            JsonArray addrArr = new JsonArray();
            data.getDetectedAddresses().forEach(addrArr::add);

            JsonObject currentWorld = new JsonObject();
            currentWorld.addProperty("timestamp", LocalDate.now().toString());
            currentWorld.addProperty("dimension", data.dimension);
            if (data.resourcePack != null) {
                currentWorld.addProperty("resource_pack", data.resourcePack);
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
                        if (dim.equals(data.dimension) && Objects.equals(rp, data.resourcePack)) {
                            worldExists = true;
                            break;
                        }
                    }

                    if (!worldExists) {
                        worldsArr.add(currentWorld);
                    } else if (finalPlugins == pluginsArr && existingPlugins != null
                            && existingPlugins.size() >= pluginsArr.size()) {
                        ServerLoggerMod.LOGGER.info(
                                "[Server Logger] No new data for {}, skipping write.", outFile.getFileName());
                        ServerLoggerMod.sendMessage("No new data for " + outFile.getFileName() + ", skipping write");
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
            serverInfo.addProperty("ip",       data.ip);
            serverInfo.addProperty("port",     data.port);
            serverInfo.addProperty("domain",   data.domain);
            serverInfo.addProperty("software", data.software);
            serverInfo.addProperty("version",  data.version);
            root.add("server_info", serverInfo);

            root.add("plugins",            finalPlugins);
            root.add("detected_addresses", addrArr);
            root.add("worlds",             worldsArr);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(outFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ServerLoggerMod.sendMessage("Log saved: " + outFile.getFileName());

        } catch (IOException e) {
            ServerLoggerMod.sendMessage("Failed to write log: " + e.getMessage());
        }
    }
}
