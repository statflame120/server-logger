package com.serverlogger.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ServerLogData {

    public static class WorldSession {
        public final String timestamp;
        public final String dimension;
        public final String resourcePack;

        public WorldSession(String timestamp, String dimension, String resourcePack) {
            this.timestamp   = timestamp;
            this.dimension   = dimension;
            this.resourcePack = resourcePack;
        }
    }

    public final String fileName;
    public final String timestamp;
    public final String ip;
    public final int    port;
    public final String domain;
    public final String brand;
    public final String version;
    public final int    playerCount;
    public final List<String>       plugins;
    public final List<String>       detectedAddresses;
    public final List<String>       detectedGameAddresses;
    public final List<WorldSession> worlds;

    public ServerLogData(String fileName, JsonObject root) {
        this.fileName  = fileName;
        this.timestamp = root.has("timestamp") ? root.get("timestamp").getAsString() : "unknown";

        JsonObject info = root.has("server_info") ? root.getAsJsonObject("server_info") : new JsonObject();
        this.ip       = info.has("ip")       ? info.get("ip").getAsString()       : "unknown";
        this.port     = info.has("port")     ? info.get("port").getAsInt()        : 25565;
        this.domain   = info.has("domain")   ? info.get("domain").getAsString()   : "unknown";
        this.brand       = info.has("brand")    ? info.get("brand").getAsString()
                          : info.has("software") ? info.get("software").getAsString() : "unknown";
        this.version     = info.has("version")      ? info.get("version").getAsString()      : "unknown";
        this.playerCount = info.has("player_count") ? info.get("player_count").getAsInt()    : -1;

        this.plugins = new ArrayList<>();
        if (root.has("plugins")) {
            for (JsonElement el : root.getAsJsonArray("plugins")) {
                if (el.isJsonObject()) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("name")) plugins.add(p.get("name").getAsString());
                } else if (el.isJsonPrimitive()) {
                    plugins.add(el.getAsString());
                }
            }
        }

        java.util.LinkedHashSet<String> addrSet = new java.util.LinkedHashSet<>();
        if (root.has("detected_addresses")) {
            for (JsonElement el : root.getAsJsonArray("detected_addresses")) {
                addrSet.add(el.getAsString());
            }
        }
        this.detectedAddresses = new ArrayList<>(addrSet);

        java.util.LinkedHashSet<String> gameAddrSet = new java.util.LinkedHashSet<>();
        if (root.has("detected_game_addresses")) {
            for (JsonElement el : root.getAsJsonArray("detected_game_addresses")) {
                gameAddrSet.add(el.getAsString());
            }
        }
        this.detectedGameAddresses = new ArrayList<>(gameAddrSet);

        List<WorldSession> rawWorlds = new ArrayList<>();
        if (root.has("worlds")) {
            for (JsonElement el : root.getAsJsonArray("worlds")) {
                if (!el.isJsonObject()) continue;
                JsonObject w = el.getAsJsonObject();
                String ts  = w.has("timestamp")     ? w.get("timestamp").getAsString()     : "unknown";
                String dim = w.has("dimension")     ? w.get("dimension").getAsString()     : "unknown";
                String rp  = w.has("resource_pack") ? w.get("resource_pack").getAsString() : null;
                rawWorlds.add(new WorldSession(ts, dim, rp));
            }
        } else if (root.has("world")) {
            JsonObject world = root.getAsJsonObject("world");
            String dim = world.has("dimension")     ? world.get("dimension").getAsString()     : "unknown";
            String rp  = world.has("resource_pack") ? world.get("resource_pack").getAsString() : null;
            rawWorlds.add(new WorldSession(this.timestamp, dim, rp));
        }
        java.util.LinkedHashSet<String> seenWorldKeys = new java.util.LinkedHashSet<>();
        this.worlds = new ArrayList<>();
        for (WorldSession ws : rawWorlds) {
            String key = ws.dimension + "|" + (ws.resourcePack != null ? ws.resourcePack : "");
            if (seenWorldKeys.add(key)) this.worlds.add(ws);
        }

    }

    public List<String> getResourcePacks() {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (WorldSession ws : worlds) {
            if (ws.resourcePack != null && !ws.resourcePack.isBlank()) seen.add(ws.resourcePack);
        }
        return new ArrayList<>(seen);
    }

    public String getDisplayName() {
        if (domain != null && !domain.equals("unknown") && !domain.equals(ip)) {
            return domain;
        }
        return ip + ":" + port;
    }
}
