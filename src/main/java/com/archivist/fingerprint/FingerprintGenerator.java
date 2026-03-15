package com.archivist.fingerprint;

import com.google.gson.*;

import java.util.*;

/**
 * Generates a fingerprint JSON template from a captured GUI.
 * Analyzes the capture to suggest useful matchers automatically.
 */
public final class FingerprintGenerator {

    private static final Set<String> ACTION_WORDS = Set.of(
            "click", "buy", "sell", "back", "next", "close",
            "confirm", "cancel", "page", "previous", "return",
            "accept", "decline", "submit", "open", "view"
    );

    private FingerprintGenerator() {}

    /**
     * Generate a fingerprint JSON template from a GUI capture.
     * @return pretty-printed JSON string ready to paste into fingerprints.json
     */
    public static String generate(GuiCapture capture) {
        JsonObject fp = new JsonObject();
        fp.addProperty("pluginId", "unknown");
        fp.addProperty("pluginName", "Unknown Plugin");
        fp.addProperty("category", "unknown");
        fp.addProperty("confidence", "medium");

        JsonArray triggeredBy = new JsonArray();
        fp.add("triggeredBy", triggeredBy);

        JsonArray matchers = new JsonArray();

        // Always add title matcher
        addMatcher(matchers, "title_exact", capture.title);

        // Always add container type matcher
        addMatcher(matchers, "container_type", capture.containerType);

        // Collect lore strings that appear on 2+ items (likely plugin-generated)
        Map<String, Integer> loreCounts = new LinkedHashMap<>();
        for (GuiItemData item : capture.items) {
            for (String line : item.lore()) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    loreCounts.merge(trimmed, 1, Integer::sum);
                }
            }
        }
        int loreAdded = 0;
        for (Map.Entry<String, Integer> entry : loreCounts.entrySet()) {
            if (entry.getValue() >= 2 && loreAdded < 3) {
                addMatcher(matchers, "lore_contains", entry.getKey());
                loreAdded++;
            }
        }

        // Collect item display names containing action words
        Set<String> actionNames = new LinkedHashSet<>();
        for (GuiItemData item : capture.items) {
            String name = item.displayName();
            String lower = name.toLowerCase(Locale.ROOT);
            for (String word : ACTION_WORDS) {
                if (lower.contains(word)) {
                    actionNames.add(name);
                    break;
                }
            }
        }
        for (String name : actionNames) {
            if (matchers.size() >= 8) break; // cap total matchers
            addMatcher(matchers, "name_contains", name);
        }

        fp.add("matchers", matchers);

        // minMatches = ceil(totalMatchers * 0.6), minimum 2
        int total = matchers.size();
        int minMatches = Math.max(2, (int) Math.ceil(total * 0.6));
        fp.addProperty("minMatches", minMatches);

        return new GsonBuilder().setPrettyPrinting().create().toJson(fp);
    }

    private static void addMatcher(JsonArray matchers, String type, String value) {
        JsonObject m = new JsonObject();
        m.addProperty("type", type);
        m.addProperty("value", value);
        matchers.add(m);
    }
}
