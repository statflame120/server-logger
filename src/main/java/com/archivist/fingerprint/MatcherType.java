package com.archivist.fingerprint;

public enum MatcherType {
    TITLE_EXACT,
    TITLE_CONTAINS,
    TITLE_REGEX,
    LORE_CONTAINS,
    LORE_REGEX,
    NAME_CONTAINS,
    NAME_REGEX,
    MATERIAL_SLOT,
    HAS_MATERIAL,
    SLOT_COUNT,
    CONTAINER_TYPE;

    public static MatcherType fromString(String s) {
        return switch (s) {
            case "title_exact" -> TITLE_EXACT;
            case "title_contains" -> TITLE_CONTAINS;
            case "title_regex" -> TITLE_REGEX;
            case "lore_contains" -> LORE_CONTAINS;
            case "lore_regex" -> LORE_REGEX;
            case "name_contains" -> NAME_CONTAINS;
            case "name_regex" -> NAME_REGEX;
            case "material_slot" -> MATERIAL_SLOT;
            case "has_material" -> HAS_MATERIAL;
            case "slot_count" -> SLOT_COUNT;
            case "container_type" -> CONTAINER_TYPE;
            default -> null;
        };
    }
}
