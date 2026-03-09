package com.archivist.fingerprint;

public record FingerprintMatch(
    String pluginId,
    String pluginName,
    String category,
    String confidence,
    String inventoryTitle,
    int matchedPatterns,
    int totalPatterns
) {}
