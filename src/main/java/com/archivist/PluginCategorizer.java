package com.archivist;

import java.util.*;

public class PluginCategorizer {

    public enum Category {
        ANTICHEAT("Anticheat", 0xFFFF5555),
        PERMISSIONS("Permissions", 0xFF55FF55),
        ECONOMY("Economy", 0xFFFFAA00),
        WORLD_MANAGEMENT("World Mgmt", 0xFF55FFFF),
        ANTI_EXPLOIT("Anti-Exploit", 0xFFFF5555),
        MONITORING("Monitoring", 0xFF5555FF),
        CHAT("Chat", 0xFFAAAAAA),
        COSMETICS("Cosmetics", 0xFFFF55FF),
        STACKING("Stacking", 0xFF55AAAA);

        public final String label;
        public final int color;

        Category(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final Map<String, Category> PLUGIN_CATEGORIES = new HashMap<>();

    static {
        // Anticheat
        for (String p : List.of("vulcan anticheat", "grim anticheat", "nocheatplus",
                "spartan", "matrix anticheat", "antiaura", "themis"))
            PLUGIN_CATEGORIES.put(p, Category.ANTICHEAT);

        // Permissions
        for (String p : List.of("luckperms", "permissionsex", "ultrapermissions", "huskperms"))
            PLUGIN_CATEGORIES.put(p, Category.PERMISSIONS);

        // Economy
        for (String p : List.of("vault", "essentialsx", "jobs reborn", "shopgui+",
                "playerpoints", "coinsengine", "ultraeconomy", "economyshopgui-market",
                "shopkeepers", "playerauction", "auctionhouse"))
            PLUGIN_CATEGORIES.put(p, Category.ECONOMY);

        // World Management
        for (String p : List.of("worldedit", "worldguard", "multiverse-core",
                "griefprevention", "griefdefender", "protectionstones", "lands",
                "plotsquared", "worldmanager", "bentobox"))
            PLUGIN_CATEGORIES.put(p, Category.WORLD_MANAGEMENT);

        // Anti-exploit
        for (String p : List.of("exploitfixer", "illegalstack", "spigotguard", "antimalware"))
            PLUGIN_CATEGORIES.put(p, Category.ANTI_EXPLOIT);

        // Monitoring
        for (String p : List.of("spark", "plan analytics", "coreprotect", "litebans",
                "advancedban"))
            PLUGIN_CATEGORIES.put(p, Category.MONITORING);

        // Chat
        for (String p : List.of("chatcontrol", "venturechat", "interactivechat",
                "discordsrv", "freedomchat", "carbon chat"))
            PLUGIN_CATEGORIES.put(p, Category.CHAT);

        // Cosmetics
        for (String p : List.of("skinsrestorer", "nametagedit", "fancyholograms",
                "procosmetics", "animatednames", "playerglow"))
            PLUGIN_CATEGORIES.put(p, Category.COSMETICS);

        // Stacking
        for (String p : List.of("rosestacker", "wildstacker", "mobstacker", "mergestack",
                "epicspawners", "smartspawners"))
            PLUGIN_CATEGORIES.put(p, Category.STACKING);
    }

    public static Category categorize(String pluginName) {
        return PLUGIN_CATEGORIES.get(pluginName.toLowerCase(Locale.ROOT));
    }

    public static class ProfileResult {
        public final Map<Category, List<String>> byCategory;
        public final int antiCheatCount;

        public ProfileResult(Map<Category, List<String>> byCategory, int antiCheatCount) {
            this.byCategory = byCategory;
            this.antiCheatCount = antiCheatCount;
        }
    }

    public static ProfileResult profile(List<String> plugins) {
        Map<Category, List<String>> byCategory = new LinkedHashMap<>();
        int antiCheatCount = 0;

        for (String plugin : plugins) {
            Category cat = categorize(plugin);
            if (cat != null) {
                byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(plugin);
                if (cat == Category.ANTICHEAT || cat == Category.ANTI_EXPLOIT)
                    antiCheatCount++;
            }
        }
        return new ProfileResult(byCategory, antiCheatCount);
    }
}
