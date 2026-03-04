package com.serverlogger;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

/**
 * Reads/writes  .minecraft/config/server-logger.json
 *
 * Default config is created automatically on first launch.
 */
public class ConfigManager {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("server-logger.json");

    // ── Configurable fields ───────────────────────────────────────────────
    public boolean enabled       = true;
    public String  logFolder     = "server-logs";

    // ─────────────────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();   // write defaults
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("enabled"))       enabled       = obj.get("enabled").getAsBoolean();
            if (obj.has("logFolder"))     logFolder     = obj.get("logFolder").getAsString();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to load config, using defaults: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled",      enabled);
            obj.addProperty("logFolder",    logFolder);

            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to save config: {}", e.getMessage());
        }
    }
}
