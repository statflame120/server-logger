package com.archivist.fingerprint;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GuiCapture {
    public final int syncId;
    public final String containerType;
    public final String title;
    public final String titleRaw;
    public final List<GuiItemData> items;
    public final String timestamp;

    public GuiCapture(int syncId, String containerType, String title, String titleRaw, List<GuiItemData> items) {
        this.syncId = syncId;
        this.containerType = containerType;
        this.title = title;
        this.titleRaw = titleRaw;
        this.items = List.copyOf(items);
        this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public int nonEmptySlotCount() {
        return items.size();
    }
}
