package com.archivist;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PluginGlossary {

    private static final Path DICT_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("archivist-glossary.json");

    private final Map<String, String> entries = new LinkedHashMap<>();

    public void load() {
        entries.clear();

        // Load bundled glossary from resources
        try (InputStream is = PluginGlossary.class.getResourceAsStream("/assets/archivist/glossary.json")) {
            if (is != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                if (obj.has("entries")) {
                    JsonObject entriesObj = obj.getAsJsonObject("entries");
                    for (Map.Entry<String, JsonElement> e : entriesObj.entrySet()) {
                        entries.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                ArchivistMod.LOGGER.info("[Archivist] Loaded {} bundled glossary entries", entries.size());
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load bundled glossary: {}", e.getMessage());
        }

        // Merge user overrides from config file (user wins on conflicts)
        if (Files.exists(DICT_PATH)) {
            try (Reader r = Files.newBufferedReader(DICT_PATH)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                if (obj.has("entries")) {
                    JsonObject entriesObj = obj.getAsJsonObject("entries");
                    for (Map.Entry<String, JsonElement> e : entriesObj.entrySet()) {
                        entries.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Failed to load glossary: {}", e.getMessage());
                ArchivistMod.sendMessage("Failed to load glossary: " + e.getMessage());
            }
        }
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject entriesObj = new JsonObject();
            entries.forEach(entriesObj::addProperty);
            root.add("entries", entriesObj);
            Files.writeString(DICT_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            ArchivistMod.sendMessage("Failed to save glossary: " + e.getMessage());
        }
    }

    public String lookup(String command) {
        return entries.get(command.toLowerCase());
    }

    public void setEntries(Map<String, String> newEntries) {
        entries.clear();
        entries.putAll(newEntries);
    }

    public Map<String, String> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

}
