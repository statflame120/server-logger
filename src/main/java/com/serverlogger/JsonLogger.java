package com.serverlogger;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;

/**
 * Writes the collected server data to:
 *   .minecraft/plugin-logs/<ip>_<port>.json
 *
 * File is overwritten on each join (as configured).
 */
public class JsonLogger {

    public static void write(ServerDataCollector data) {
        try {
            Path logDir = FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);

            // Sanitize the IP for use as a filename
            String safeIp   = data.ip.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = safeIp + "_" + data.port + ".json";
            Path   outFile  = logDir.resolve(fileName);

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", LocalDate.now().toString());

            // ── server_info ───────────────────────────────────────────────
            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("ip",       data.ip);
            serverInfo.addProperty("port",     data.port);
            serverInfo.addProperty("domain",   data.domain);
            serverInfo.addProperty("software", data.software);
            serverInfo.addProperty("version",  data.version);
            root.add("server_info", serverInfo);

            // ── plugins ───────────────────────────────────────────────────
            JsonArray pluginsArr = new JsonArray();
            for (String name : data.getPlugins()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", name);
                pluginsArr.add(p);
            }
            root.add("plugins", pluginsArr);

            // ── detected_addresses ────────────────────────────────────────
            JsonArray addrArr = new JsonArray();
            data.getDetectedAddresses().forEach(addrArr::add);
            root.add("detected_addresses", addrArr);

            // ── world ─────────────────────────────────────────────────────
            JsonObject world = new JsonObject();
            world.addProperty("dimension", data.dimension);
            if (data.resourcePack != null) {
                world.addProperty("resource_pack", data.resourcePack);
            }
            root.add("world", world);

            // ── Skip if existing log already has >= plugins ─────────────
            if (Files.exists(outFile)) {
                try {
                    String existing = Files.readString(outFile);
                    JsonObject existingRoot = JsonParser.parseString(existing).getAsJsonObject();
                    JsonArray existingPlugins = existingRoot.getAsJsonArray("plugins");
                    if (existingPlugins != null && pluginsArr.size() <= existingPlugins.size()) {
                        ServerLoggerMod.LOGGER.info(
                                "[Server Logger] Skipped write for {} — existing log has {} plugins, new scan has {}",
                                outFile.getFileName(), existingPlugins.size(), pluginsArr.size());
                        return;
                    }
                } catch (Exception e) {
                    // Corrupted/unreadable file — overwrite it
                }
            }

            // ── Write ─────────────────────────────────────────────────────
            String json = new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(root);
            Files.writeString(outFile, json, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            ServerLoggerMod.LOGGER.info("[Server Logger] Log written to {}", outFile);

        } catch (IOException e) {
            ServerLoggerMod.LOGGER.error("[Server Logger] Failed to write log: {}", e.getMessage());
        }
    }

}