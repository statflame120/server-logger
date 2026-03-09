package com.archivist.fingerprint;

import java.util.List;

public record GuiItemData(
    int slot,
    String materialId,
    String displayName,
    String displayNameRaw,
    List<String> lore,
    List<String> loreRaw,
    int count,
    boolean hasEnchantGlint
) {}
