package com.archivist.scraper;

import java.util.Arrays;

/**
 * Data extracted from a single inventory slot during a GUI scrape.
 */
public class ScrapedItem {

    public final int slot;
    public final String itemId;
    public final String displayName;
    public final String nbtString;
    public final String[] lore;
    public final Integer customModelData; // nullable

    public ScrapedItem(int slot, String itemId, String displayName,
                       String nbtString, String[] lore, Integer customModelData) {
        this.slot = slot;
        this.itemId = itemId;
        this.displayName = displayName;
        this.nbtString = nbtString;
        this.lore = lore != null ? lore : new String[0];
        this.customModelData = customModelData;
    }

    /** Check if any lore line contains the given substring (case-insensitive). */
    public boolean loreContains(String text) {
        String lower = text.toLowerCase();
        for (String line : lore) {
            if (line.toLowerCase().contains(lower)) return true;
        }
        return false;
    }

    /** Check if the display name contains the given substring (case-insensitive). */
    public boolean nameContains(String text) {
        return displayName.toLowerCase().contains(text.toLowerCase());
    }

    @Override
    public String toString() {
        return "ScrapedItem{slot=" + slot + ", id=" + itemId + ", name=" + displayName
                + ", lore=" + Arrays.toString(lore) + "}";
    }
}
