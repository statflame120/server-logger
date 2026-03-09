package com.archivist.gui.render;

import com.archivist.ArchivistMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;

/**
 * Manages color themes. Loads bundled JSON themes from resources and custom
 * themes from .minecraft/archivist/themes/. Java themes are also registered.
 */
public class ThemeManager {

    private static ThemeManager instance;
    private final Map<String, ColorScheme> themes = new LinkedHashMap<>();
    private boolean loaded = false;

    public static ThemeManager getInstance() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    /** Lazy-load all themes on first access. */
    public Map<String, ColorScheme> getThemes() {
        if (!loaded) load();
        return Collections.unmodifiableMap(themes);
    }

    public ColorScheme getTheme(String name) {
        if (!loaded) load();
        return themes.get(name.toLowerCase(Locale.ROOT));
    }

    public void load() {
        themes.clear();

        // Load bundled JSON themes from resources
        loadBundledThemes();

        // Load custom themes from .minecraft/archivist/themes/
        loadCustomThemes();

        loaded = true;
    }

    private void loadBundledThemes() {
        // Try to load bundled JSON themes from resources
        String[] bundled = {"amber", "violet", "midnight", "slate", "pear", "rose", "ocean"};
        for (String name : bundled) {
            try (InputStream is = ThemeManager.class.getResourceAsStream("/assets/archivist/themes/" + name + ".json")) {
                if (is != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                    ColorScheme theme = parseJsonTheme(obj);
                    if (theme != null) {
                        themes.put(theme.name().toLowerCase(Locale.ROOT), theme);
                    }
                }
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Failed to load bundled theme {}: {}", name, e.getMessage());
            }
        }
    }

    private void loadCustomThemes() {
        Path themeDir = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("themes");
        if (!Files.isDirectory(themeDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(themeDir, "*.json")) {
            for (Path file : stream) {
                try (Reader r = Files.newBufferedReader(file)) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    ColorScheme theme = parseJsonTheme(obj);
                    if (theme != null) {
                        themes.put(theme.name().toLowerCase(Locale.ROOT), theme);
                        ArchivistMod.LOGGER.info("[Archivist] Loaded custom theme: {}", theme.name());
                    }
                } catch (Exception e) {
                    ArchivistMod.LOGGER.warn("[Archivist] Failed to load theme {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to scan theme dir: {}", e.getMessage());
        }
    }

    /** Load a theme from a URL string (for theme import feature). */
    public ColorScheme loadFromUrl(String url) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }

        JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
        ColorScheme theme = parseJsonTheme(obj);
        if (theme == null) throw new RuntimeException("Invalid theme JSON");

        // Save to custom themes directory
        Path themeDir = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("themes");
        Files.createDirectories(themeDir);
        String safeName = theme.name().replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase(Locale.ROOT);
        Files.writeString(themeDir.resolve(safeName + ".json"), response.body(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Register it
        themes.put(theme.name().toLowerCase(Locale.ROOT), theme);
        return theme;
    }

    /** Reload all themes (for live preview). */
    public void reload() {
        loaded = false;
    }

    /** Parse a JSON object into a JsonTheme. */
    static ColorScheme parseJsonTheme(JsonObject obj) {
        if (!obj.has("name")) return null;
        return new JsonTheme(obj);
    }
}
