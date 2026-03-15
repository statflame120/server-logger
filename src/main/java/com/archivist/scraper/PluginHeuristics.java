package com.archivist.scraper;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Predicate;

/**
 * Static rules that map scraped item attributes (display names, lore, NBT)
 * to known plugin identifiers. Easy to extend by adding more rules.
 */
public final class PluginHeuristics {

    private PluginHeuristics() {}

    /**
     * A single heuristic rule: a predicate that tests a ScrapedItem,
     * and the plugin identifier it indicates when matched.
     */
    public record HeuristicRule(String pluginId, Predicate<ScrapedItem> matcher) {}

    private static final List<HeuristicRule> RULES = new ArrayList<>();

    static {
        loadRules();
        // NBT-based heuristic — must stay in Java (lambda predicate)
        RULES.add(new HeuristicRule("CustomModelData-Plugin",
                item -> item.customModelData != null && item.customModelData > 1000));
    }

    private static void loadRules() {
        try (InputStream is = PluginHeuristics.class.getResourceAsStream("/assets/archivist/heuristics.json")) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String pluginId = entry.getKey();
                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("name")) {
                    for (JsonElement e : obj.getAsJsonArray("name")) {
                        nameRule(pluginId, e.getAsString());
                    }
                }
                if (obj.has("lore")) {
                    for (JsonElement e : obj.getAsJsonArray("lore")) {
                        loreRule(pluginId, e.getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // Defensive: never let bad JSON crash the mod
        }
    }

    /** Add a rule that matches on display name substring. */
    private static void nameRule(String pluginId, String nameSubstring) {
        RULES.add(new HeuristicRule(pluginId, item -> item.nameContains(nameSubstring)));
    }

    /** Add a rule that matches on any lore line substring. */
    private static void loreRule(String pluginId, String loreSubstring) {
        RULES.add(new HeuristicRule(pluginId, item -> item.loreContains(loreSubstring)));
    }

    /**
     * Identify plugins from a list of scraped items.
     * Returns a deduplicated set of plugin identifier strings.
     */
    public static Set<String> identify(List<ScrapedItem> items) {
        Set<String> plugins = new LinkedHashSet<>();
        for (ScrapedItem item : items) {
            for (HeuristicRule rule : RULES) {
                try {
                    if (rule.matcher().test(item)) {
                        plugins.add(rule.pluginId());
                    }
                } catch (Exception ignored) {
                    // Defensive: never let a bad rule crash the scraper
                }
            }
        }
        // Remove the generic marker if we identified real plugins
        if (plugins.size() > 1) {
            plugins.remove("CustomModelData-Plugin");
        }
        return plugins;
    }

    /** Get all registered rules (for inspection/debugging). */
    public static List<HeuristicRule> getRules() {
        return Collections.unmodifiableList(RULES);
    }
}
