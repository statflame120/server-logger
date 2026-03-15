package com.archivist.config;

import com.archivist.ArchivistMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended configuration for the Archivist click-GUI, scraper, and database systems.
 * Persisted to config/archivist_extended.json.
 * This supplements the existing ConfigManager with new settings.
 */
public class ArchivistConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("archivist_extended.json");

    // ── Scraper settings ────────────────────────────────────────────────────
    public List<String> scraperCommands = new ArrayList<>(List.of(
            "/ah", "/ec", "/shop", "/sell", "/pv", "/backpack", "/orders"
    ));
    public int scraperDelay = 1000;         // ms between commands
    public boolean autoScrapeOnJoin = false;
    public boolean smartProbeOnJoin = true;
    public boolean silentScraper = true;

    // ── Database settings ───────────────────────────────────────────────────
    public String databaseAdapterType = "None";  // None, REST API, Discord Bot, Custom
    public String databaseConnectionString = "";
    public String databaseAuthToken = "";
    public boolean autoUploadOnLog = false;

    // ── Logging toggles ─────────────────────────────────────────────────────
    public boolean logPlugins = true;
    public boolean logWorldInfo = true;
    public boolean logConnectionMeta = true;

    // ── Display ─────────────────────────────────────────────────────────────
    public boolean showHudSummary = true;
    public boolean showScanOverlay = true;
    public boolean searchBarPopup = true;

    // ── Custom adapter settings ─────────────────────────────────────────────
    public String customAdapterClasspath = "";
    public String customAdapterClassName = "";

    // ── Persistence ─────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();

            if (obj.has("scraperCommands")) {
                scraperCommands.clear();
                for (JsonElement el : obj.getAsJsonArray("scraperCommands")) {
                    scraperCommands.add(el.getAsString());
                }
            }
            if (obj.has("scraperDelay"))          scraperDelay          = obj.get("scraperDelay").getAsInt();
            if (obj.has("autoScrapeOnJoin"))       autoScrapeOnJoin      = obj.get("autoScrapeOnJoin").getAsBoolean();
            if (obj.has("smartProbeOnJoin"))        smartProbeOnJoin      = obj.get("smartProbeOnJoin").getAsBoolean();
            if (obj.has("silentScraper"))          silentScraper         = obj.get("silentScraper").getAsBoolean();
            if (obj.has("databaseAdapterType"))    databaseAdapterType   = obj.get("databaseAdapterType").getAsString();
            if (obj.has("databaseConnectionString")) databaseConnectionString = obj.get("databaseConnectionString").getAsString();
            if (obj.has("databaseAuthToken"))      databaseAuthToken     = obj.get("databaseAuthToken").getAsString();
            if (obj.has("autoUploadOnLog"))        autoUploadOnLog       = obj.get("autoUploadOnLog").getAsBoolean();
            if (obj.has("logPlugins"))             logPlugins            = obj.get("logPlugins").getAsBoolean();
            if (obj.has("logWorldInfo"))           logWorldInfo          = obj.get("logWorldInfo").getAsBoolean();
            if (obj.has("logConnectionMeta"))      logConnectionMeta     = obj.get("logConnectionMeta").getAsBoolean();
            if (obj.has("showHudSummary"))         showHudSummary        = obj.get("showHudSummary").getAsBoolean();
            if (obj.has("showScanOverlay"))       showScanOverlay       = obj.get("showScanOverlay").getAsBoolean();
            if (obj.has("searchBarPopup"))        searchBarPopup        = obj.get("searchBarPopup").getAsBoolean();
            if (obj.has("customAdapterClasspath")) customAdapterClasspath = obj.get("customAdapterClasspath").getAsString();
            if (obj.has("customAdapterClassName")) customAdapterClassName = obj.get("customAdapterClassName").getAsString();

        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load extended config: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();

            JsonArray cmds = new JsonArray();
            scraperCommands.forEach(cmds::add);
            obj.add("scraperCommands", cmds);

            obj.addProperty("scraperDelay",           scraperDelay);
            obj.addProperty("autoScrapeOnJoin",       autoScrapeOnJoin);
            obj.addProperty("smartProbeOnJoin",       smartProbeOnJoin);
            obj.addProperty("silentScraper",          silentScraper);
            obj.addProperty("databaseAdapterType",    databaseAdapterType);
            obj.addProperty("databaseConnectionString", databaseConnectionString);
            obj.addProperty("databaseAuthToken",      databaseAuthToken);
            obj.addProperty("autoUploadOnLog",        autoUploadOnLog);
            obj.addProperty("logPlugins",             logPlugins);
            obj.addProperty("logWorldInfo",           logWorldInfo);
            obj.addProperty("logConnectionMeta",      logConnectionMeta);
            obj.addProperty("showHudSummary",         showHudSummary);
            obj.addProperty("showScanOverlay",       showScanOverlay);
            obj.addProperty("searchBarPopup",        searchBarPopup);
            obj.addProperty("customAdapterClasspath", customAdapterClasspath);
            obj.addProperty("customAdapterClassName", customAdapterClassName);

            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to save extended config: {}", e.getMessage());
        }
    }
}
