package com.archivist.fingerprint;

import com.archivist.ArchivistMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;

public class FingerprintDatabase {

    private final List<Fingerprint> fingerprints = new ArrayList<>();

    public void load() {
        fingerprints.clear();

        // Load bundled fingerprints from resources
        try (InputStream is = FingerprintDatabase.class.getResourceAsStream("/assets/archivist/fingerprints.json")) {
            if (is != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                parseFingerprints(obj);
                ArchivistMod.LOGGER.info("[Archivist] Loaded {} bundled fingerprints", fingerprints.size());
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Failed to load bundled fingerprints: {}", e.getMessage());
        }

        // Load custom overrides from .minecraft/archivist/fingerprints_custom.json
        Path customPath = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("fingerprints_custom.json");
        if (Files.exists(customPath)) {
            try (Reader r = Files.newBufferedReader(customPath)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                int before = fingerprints.size();
                parseFingerprints(obj);
                ArchivistMod.LOGGER.info("[Archivist] Loaded {} custom fingerprints", fingerprints.size() - before);
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Failed to load custom fingerprints: {}", e.getMessage());
            }
        }
    }

    private void parseFingerprints(JsonObject root) {
        if (!root.has("fingerprints")) return;
        for (JsonElement el : root.getAsJsonArray("fingerprints")) {
            try {
                JsonObject fp = el.getAsJsonObject();
                String pluginId = fp.get("pluginId").getAsString();
                String pluginName = fp.get("pluginName").getAsString();
                String category = fp.has("category") ? fp.get("category").getAsString() : "unknown";
                String confidence = fp.has("confidence") ? fp.get("confidence").getAsString() : "medium";
                int minMatches = fp.has("minMatches") ? fp.get("minMatches").getAsInt() : 1;

                List<String> triggeredBy = new ArrayList<>();
                if (fp.has("triggeredBy")) {
                    for (JsonElement t : fp.getAsJsonArray("triggeredBy")) {
                        triggeredBy.add(t.getAsString());
                    }
                }

                List<Fingerprint.Matcher> matchers = new ArrayList<>();
                if (fp.has("matchers")) {
                    for (JsonElement m : fp.getAsJsonArray("matchers")) {
                        JsonObject mo = m.getAsJsonObject();
                        MatcherType type = MatcherType.fromString(mo.get("type").getAsString());
                        String value = mo.get("value").getAsString();
                        if (type != null) {
                            matchers.add(new Fingerprint.Matcher(type, value));
                        }
                    }
                }

                fingerprints.add(new Fingerprint(pluginId, pluginName, category, confidence, triggeredBy, matchers, minMatches));
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Skipping malformed fingerprint: {}", e.getMessage());
            }
        }
    }

    public List<Fingerprint> getFingerprints() {
        return Collections.unmodifiableList(fingerprints);
    }

    /** Get all unique probe commands from all fingerprints' triggeredBy lists. */
    public List<String> getAllProbeCommands() {
        Set<String> commands = new LinkedHashSet<>();
        for (Fingerprint fp : fingerprints) {
            commands.addAll(fp.triggeredBy);
        }
        return new ArrayList<>(commands);
    }
}
