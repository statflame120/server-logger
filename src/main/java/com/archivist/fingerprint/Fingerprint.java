package com.archivist.fingerprint;

import java.util.List;

public class Fingerprint {
    public final String pluginId;
    public final String pluginName;
    public final String category;
    public final String confidence;
    public final List<String> triggeredBy;
    public final List<Matcher> matchers;
    public final int minMatches;

    public Fingerprint(String pluginId, String pluginName, String category, String confidence,
                       List<String> triggeredBy, List<Matcher> matchers, int minMatches) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
        this.category = category;
        this.confidence = confidence;
        this.triggeredBy = triggeredBy != null ? List.copyOf(triggeredBy) : List.of();
        this.matchers = List.copyOf(matchers);
        this.minMatches = minMatches;
    }

    public record Matcher(MatcherType type, String value) {}
}
