package com.serverlogger.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for one JSON server log file.
 */
public class ServerLogData {

    public final String fileName;
    public final String timestamp;
    public final String ip;
    public final int port;
    public final String domain;
    public final String software;
    public final String version;
    public final String dimension;
    public final String resourcePack;
    public final List<String> plugins;
    public final List<String> detectedAddresses;

    public ServerLogData(String fileName, JsonObject root) {
        this.fileName = fileName;
        this.timestamp = root.has("timestamp") ? root.get("timestamp").getAsString() : "unknown";

        JsonObject info = root.has("server_info") ? root.getAsJsonObject("server_info") : new JsonObject();
        this.ip = info.has("ip") ? info.get("ip").getAsString() : "unknown";
        this.port = info.has("port") ? info.get("port").getAsInt() : 25565;
        this.domain = info.has("domain") ? info.get("domain").getAsString() : "unknown";
        this.software = info.has("software") ? info.get("software").getAsString() : "unknown";
        this.version = info.has("version") ? info.get("version").getAsString() : "unknown";

        JsonObject world = root.has("world") ? root.getAsJsonObject("world") : new JsonObject();
        this.dimension = world.has("dimension") ? world.get("dimension").getAsString() : "unknown";
        this.resourcePack = world.has("resource_pack") ? world.get("resource_pack").getAsString() : null;

        this.plugins = new ArrayList<>();
        if (root.has("plugins")) {
            JsonArray arr = root.getAsJsonArray("plugins");
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("name")) {
                        plugins.add(p.get("name").getAsString());
                    }
                } else if (el.isJsonPrimitive()) {
                    plugins.add(el.getAsString());
                }
            }
        }

        this.detectedAddresses = new ArrayList<>();
        if (root.has("detected_addresses")) {
            JsonArray arr = root.getAsJsonArray("detected_addresses");
            for (JsonElement el : arr) {
                detectedAddresses.add(el.getAsString());
            }
        }
    }

    public String getDisplayName() {
        if (domain != null && !domain.equals("unknown") && !domain.equals(ip)) {
            return domain;
        }
        return ip + ":" + port;
    }
}
