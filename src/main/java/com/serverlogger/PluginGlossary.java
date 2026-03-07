package com.serverlogger;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PluginGlossary {

    private static final Path DICT_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("server-logger-glossary.json");

    private final Map<String, String> entries = new LinkedHashMap<>();

    private static final List<Map.Entry<String, String>> DEFAULT_ENTRIES = List.of(
            //ADD AS MANY AS YOU WANT TO
            Map.entry("lp",                    "luckperms"),
            Map.entry("luckperms",             "luckperms"),
            Map.entry("pex",                   "permissionsex"),
            Map.entry("permissionsex",         "permissionsex"),
            Map.entry("ess",                   "essentialsx"),
            Map.entry("essentials",            "essentialsx"),
            Map.entry("essentialsx",           "essentialsx"),
            Map.entry("vault",                 "vault"),
            Map.entry("we",                    "worldedit"),
            Map.entry("worldedit",             "worldedit"),
            Map.entry("wg",                    "worldguard"),
            Map.entry("worldguard",            "worldguard"),
            Map.entry("co",                    "coreprotect"),
            Map.entry("coreprotect",           "coreprotect"),
            Map.entry("dynmap",                "dynmap"),
            Map.entry("via",                   "viaversion"),
            Map.entry("viaversion",            "viaversion"),
            Map.entry("vb",                    "viabackwards"),
            Map.entry("viabackwards",          "viabackwards"),
            Map.entry("skript",                "skript"),
            Map.entry("mv",                    "multiverse-core"),
            Map.entry("multiverse",            "multiverse-core"),
            Map.entry("papi",                  "placeholderapi"),
            Map.entry("placeholderapi",        "placeholderapi"),
            Map.entry("npc",                   "citizens"),
            Map.entry("citizens",              "citizens"),
            Map.entry("mythicmobs",            "mythicmobs"),
            Map.entry("myth",                  "mythicmobs"),
            Map.entry("plot",                  "plotsquared"),
            Map.entry("plots",                 "plotsquared"),
            Map.entry("plotsquared",           "plotsquared"),
            Map.entry("gp",                    "griefprevention"),
            Map.entry("griefprevention",       "griefprevention"),
            Map.entry("towny",                 "towny"),
            Map.entry("factions",              "factions"),
            Map.entry("lands",                 "lands"),
            Map.entry("authme",                "authme"),
            Map.entry("protocollib",           "protocollib"),
            Map.entry("packetevents",          "packetevents"),
            Map.entry("litebans",              "litebans"),
            Map.entry("advancedban",           "advancedban"),
            Map.entry("ncp",                   "nocheatplus"),
            Map.entry("nocheatplus",           "nocheatplus"),
            Map.entry("spartan",               "spartan"),
            Map.entry("matrix",                "matrix anticheat"),
            Map.entry("aac",                   "antiaura"),
            Map.entry("jobs",                  "jobs reborn"),
            Map.entry("job",                   "jobs reborn"),
            Map.entry("quests",                "quests"),
            Map.entry("mcmmo",                 "mcmmo"),
            Map.entry("itemsadder",            "itemsadder"),
            Map.entry("ia",                    "itemsadder"),
            Map.entry("oraxen",                "oraxen"),
            Map.entry("nexo",                  "nexo"),
            Map.entry("cmi",                   "cmi"),
            Map.entry("tab",                   "tab"),
            Map.entry("chunky",                "chunky"),
            Map.entry("spark",                 "spark"),
            Map.entry("clearlag",              "clearlag"),
            Map.entry("geyser",                "geyser"),
            Map.entry("floodgate",             "floodgate"),
            Map.entry("deluxemenu",            "deluxemenus"),
            Map.entry("deluxemenus",           "deluxemenus"),
            Map.entry("dm",                    "deluxemenus"),
            Map.entry("shopguiplus",           "shopgui+"),
            Map.entry("pp",                    "playerpoints"),
            Map.entry("playerpoints",          "playerpoints"),
            Map.entry("modelengine",           "modelengine"),
            Map.entry("mmoitems",              "mmoitems"),
            Map.entry("advancedenchantments",  "advancedenchantments"),
            Map.entry("veinminer",             "veinminer"),
            Map.entry("treecapitator",         "treecapitator"),
            Map.entry("chestsort",             "chestsort"),
            Map.entry("bungeeguard",           "bungeeguard"),
            Map.entry("chatcontrol",           "chatcontrol"),
            Map.entry("antispam",              "antispam"),
            Map.entry("discord",               "discordsrv"),
            Map.entry("discordsrv",            "discordsrv")
    );

    public void load() {
        entries.clear();
        if (!Files.exists(DICT_PATH)) {
            // First run: seed with defaults then persist
            for (Map.Entry<String, String> e : DEFAULT_ENTRIES) {
                entries.put(e.getKey(), e.getValue());
            }
            save();
            return;
        }

        try (Reader r = Files.newBufferedReader(DICT_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("entries")) {
                JsonObject entriesObj = obj.getAsJsonObject("entries");
                for (Map.Entry<String, JsonElement> e : entriesObj.entrySet()) {
                    entries.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to load glossary: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Failed to load glossary: " + e.getMessage());
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
            ServerLoggerMod.sendMessage("Failed to save glossary: " + e.getMessage());
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
