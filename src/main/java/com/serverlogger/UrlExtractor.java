package com.serverlogger;

import java.util.*;
import java.util.regex.*;

public class UrlExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?([a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?" +
                    "(?:\\.[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?)+)" +
                    "(?::\\d{1,5})?(?:/[\\w\\-./?%&=]*)?",
            Pattern.CASE_INSENSITIVE
    );

    public static Set<String> extract(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        Set<String> results = new LinkedHashSet<>();
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String match = m.group().trim();
            if (!isNoise(match)) results.add(match);
        }
        return results;
    }

    private static boolean isNoise(String url) {
        String lower = url.toLowerCase();
        return lower.contains("mojang.com")
                || lower.contains("minecraft.net")
                || lower.contains("microsoft.com")
                || lower.contains("localhost")
                || lower.matches("\\d+");
    }
}
