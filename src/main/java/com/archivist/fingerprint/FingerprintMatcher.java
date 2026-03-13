package com.archivist.fingerprint;

import java.util.*;
import java.util.regex.Pattern;

public class FingerprintMatcher {

    public List<FingerprintMatch> match(GuiCapture capture, List<Fingerprint> fingerprints) {
        List<FingerprintMatch> results = new ArrayList<>();

        for (Fingerprint fp : fingerprints) {
            int matched = 0;
            for (Fingerprint.Matcher matcher : fp.matchers) {
                if (evaluate(matcher, capture)) {
                    matched++;
                }
            }
            if (matched >= fp.minMatches) {
                results.add(new FingerprintMatch(
                    fp.pluginId, fp.pluginName, fp.category, fp.confidence,
                    capture.title, matched, fp.matchers.size()
                ));
            }
        }

        return results;
    }

    private boolean evaluate(Fingerprint.Matcher matcher, GuiCapture capture) {
        String value = matcher.value();
        return switch (matcher.type()) {
            case TITLE_EXACT -> capture.title.equalsIgnoreCase(value);
            case TITLE_CONTAINS -> capture.title.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
            case TITLE_REGEX -> matchesRegex(capture.title, value);
            case LORE_CONTAINS -> anyLoreContains(capture, value);
            case LORE_REGEX -> anyLoreMatchesRegex(capture, value);
            case NAME_CONTAINS -> anyNameContains(capture, value);
            case NAME_REGEX -> anyNameMatchesRegex(capture, value);
            case MATERIAL_SLOT -> materialInSlot(capture, value);
            case HAS_MATERIAL -> anyHasMaterial(capture, value);
            case SLOT_COUNT -> capture.nonEmptySlotCount() == parseIntSafe(value);
            case CONTAINER_TYPE -> capture.containerType.equalsIgnoreCase(value);
        };
    }

    private boolean matchesRegex(String input, String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input).find();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean anyLoreContains(GuiCapture capture, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (GuiItemData item : capture.items) {
            for (String line : item.lore()) {
                if (line.toLowerCase(Locale.ROOT).contains(lower)) return true;
            }
        }
        return false;
    }

    private boolean anyLoreMatchesRegex(GuiCapture capture, String regex) {
        for (GuiItemData item : capture.items) {
            for (String line : item.lore()) {
                if (matchesRegex(line, regex)) return true;
            }
        }
        return false;
    }

    private boolean anyNameContains(GuiCapture capture, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (GuiItemData item : capture.items) {
            if (item.displayName().toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }

    private boolean anyNameMatchesRegex(GuiCapture capture, String regex) {
        for (GuiItemData item : capture.items) {
            if (matchesRegex(item.displayName(), regex)) return true;
        }
        return false;
    }

    private boolean materialInSlot(GuiCapture capture, String value) {
        // Format: "minecraft:arrow@49"
        String[] parts = value.split("@");
        if (parts.length != 2) return false;
        String material = parts[0];
        int slot = parseIntSafe(parts[1]);
        for (GuiItemData item : capture.items) {
            if (item.slot() == slot && item.materialId().equalsIgnoreCase(material)) return true;
        }
        return false;
    }

    private boolean anyHasMaterial(GuiCapture capture, String material) {
        for (GuiItemData item : capture.items) {
            if (item.materialId().equalsIgnoreCase(material)) return true;
        }
        return false;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }
}
