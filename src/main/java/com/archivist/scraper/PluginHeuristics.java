package com.archivist.scraper;

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
        // ── Auction House plugins ───────────────────────────────────────────
        nameRule("AuctionHouse", "auction house");
        nameRule("AuctionHouse", "auction");
        loreRule("AuctionHouse", "click to bid");
        loreRule("AuctionHouse", "buy it now");
        loreRule("AuctionHouse", "browse auctions");

        // ── Shop plugins ────────────────────────────────────────────────────
        nameRule("ShopGUIPlus", "shop");
        loreRule("ShopGUIPlus", "click to purchase");
        loreRule("ShopGUIPlus", "click to buy");
        loreRule("ShopGUIPlus", "price:");
        loreRule("EconomyShopGUI", "economy shop");

        // ── Ender Chest / Virtual storage ───────────────────────────────────
        nameRule("EnderChest", "ender chest");
        nameRule("EnderChest", "virtual chest");

        // ── Crate plugins ───────────────────────────────────────────────────
        nameRule("CrazyCrates", "crazy crate");
        nameRule("CrazyCrates", "crate key");
        loreRule("CrazyCrates", "right-click to open");
        nameRule("ExcellentCrates", "crate");
        loreRule("ExcellentCrates", "crate reward");

        // ── Menu / GUI plugins ──────────────────────────────────────────────
        nameRule("DeluxeMenus", "main menu");
        nameRule("DeluxeMenus", "server menu");
        nameRule("ChestCommands", "gui menu");

        // ── Economy / Sell ──────────────────────────────────────────────────
        nameRule("SellGUI", "sell");
        loreRule("SellGUI", "sell all");
        loreRule("SellGUI", "click to sell");
        nameRule("EssentialsX", "worth");

        // ── Player Warps ────────────────────────────────────────────────────
        nameRule("PlayerWarps", "player warp");
        loreRule("PlayerWarps", "warp to");

        // ── Jobs ────────────────────────────────────────────────────────────
        nameRule("JobsReborn", "jobs");
        loreRule("JobsReborn", "join this job");
        loreRule("JobsReborn", "job level");

        // ── McMMO ───────────────────────────────────────────────────────────
        nameRule("McMMO", "mcmmo");
        loreRule("McMMO", "skill level");

        // ── Quests ──────────────────────────────────────────────────────────
        nameRule("Quests", "quest");
        loreRule("Quests", "quest progress");
        loreRule("Quests", "objectives");

        // ── Cosmetics ───────────────────────────────────────────────────────
        nameRule("ProCosmetics", "cosmetic");
        loreRule("ProCosmetics", "unlock cosmetic");

        // ── Kits ────────────────────────────────────────────────────────────
        nameRule("PlayerKits", "kit");
        loreRule("PlayerKits", "click to claim");
        loreRule("PlayerKits", "cooldown:");

        // ── Spawner plugins ─────────────────────────────────────────────────
        nameRule("EpicSpawners", "spawner");
        loreRule("SmartSpawners", "spawner level");

        // ── SkyBlock ────────────────────────────────────────────────────────
        nameRule("SuperiorSkyblock", "island");
        loreRule("SuperiorSkyblock", "island level");
        loreRule("BentoBox", "island settings");

        // ── GriefPrevention / Claims ────────────────────────────────────────
        loreRule("GriefPrevention", "claim blocks");
        loreRule("Lands", "land claim");

        // ── Voting ──────────────────────────────────────────────────────────
        nameRule("VotingPlugin", "vote");
        loreRule("VotingPlugin", "vote reward");

        // ── Daily Rewards ───────────────────────────────────────────────────
        nameRule("DailyRewards", "daily reward");
        loreRule("DailyRewards", "claim your reward");

        // ── NBT-based heuristics (generic) ──────────────────────────────────
        RULES.add(new HeuristicRule("CustomModelData-Plugin",
                item -> item.customModelData != null && item.customModelData > 1000));
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
