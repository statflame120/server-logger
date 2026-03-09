package com.archivist.config;

import com.archivist.ArchivistMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists window positions, sizes, visibility, and active theme.
 * Saved to .minecraft/archivist/gui-config.json.
 */
public class GuiConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("gui-config.json");

    /** Per-window state. */
    public static class WindowState {
        public int x, y, width, height;
        public boolean visible;
        public boolean minimized;

        public WindowState(int x, int y, int width, int height, boolean visible, boolean minimized) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.visible = visible;
            this.minimized = minimized;
        }
    }

    private final Map<String, WindowState> windowStates = new LinkedHashMap<>();
    public String activeTheme = "Ocean";

    /** Get saved window state, or null if none exists. */
    public WindowState getWindowState(String windowId) {
        return windowStates.get(windowId);
    }

    /** Save a window's current state. */
    public void setWindowState(String windowId, WindowState state) {
        windowStates.put(windowId, state);
    }

    /** Load from JSON file. */
    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();

            if (obj.has("activeTheme")) {
                activeTheme = obj.get("activeTheme").getAsString();
            }
            if (obj.has("windows")) {
                JsonObject windows = obj.getAsJsonObject("windows");
                for (Map.Entry<String, JsonElement> entry : windows.entrySet()) {
                    JsonObject w = entry.getValue().getAsJsonObject();
                    windowStates.put(entry.getKey(), new WindowState(
                            w.has("x") ? w.get("x").getAsInt() : 0,
                            w.has("y") ? w.get("y").getAsInt() : 0,
                            w.has("width") ? w.get("width").getAsInt() : 200,
                            w.has("height") ? w.get("height").getAsInt() : 200,
                            !w.has("visible") || w.get("visible").getAsBoolean(),
                            w.has("minimized") && w.get("minimized").getAsBoolean()
                    ));
                }
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load GUI config: {}", e.getMessage());
        }
    }

    /** Save to JSON file. */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("activeTheme", activeTheme);

            JsonObject windows = new JsonObject();
            for (Map.Entry<String, WindowState> entry : windowStates.entrySet()) {
                JsonObject w = new JsonObject();
                WindowState s = entry.getValue();
                w.addProperty("x", s.x);
                w.addProperty("y", s.y);
                w.addProperty("width", s.width);
                w.addProperty("height", s.height);
                w.addProperty("visible", s.visible);
                w.addProperty("minimized", s.minimized);
                windows.add(entry.getKey(), w);
            }
            obj.add("windows", windows);

            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to save GUI config: {}", e.getMessage());
        }
    }
}
