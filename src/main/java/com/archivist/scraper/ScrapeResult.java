package com.archivist.scraper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Result of probing a single command during a GUI scrape.
 */
public class ScrapeResult {

    public final String command;
    public final List<ScrapedItem> items;
    public final Instant timestamp;

    public ScrapeResult(String command, List<ScrapedItem> items) {
        this.command = command;
        this.items = Collections.unmodifiableList(items);
        this.timestamp = Instant.now();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public String toString() {
        return "ScrapeResult{cmd=" + command + ", items=" + items.size() + "}";
    }
}
