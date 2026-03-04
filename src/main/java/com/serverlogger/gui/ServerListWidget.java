package com.serverlogger.gui;

import com.serverlogger.ServerLoggerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scrollable list widget that displays server log entries.
 */
public class ServerListWidget extends ObjectSelectionList<ServerListWidget.Entry> {

    private final ServerLogScreen parentScreen;
    private long lastClickTime = 0;
    private Entry lastClickedEntry = null;

    public ServerListWidget(ServerLogScreen parentScreen, Minecraft mc, int width, int height, int top, int itemHeight) {
        super(mc, width, height, top, itemHeight);
        this.parentScreen = parentScreen;
    }

    public void updateEntries(List<ServerLogData> filtered) {
        clearEntries();
        for (ServerLogData data : filtered) {
            addEntry(new Entry(data));
        }
        ServerLoggerMod.LOGGER.info("[Server Logger] Widget has {} entries, widget Y={} H={} rowWidth={}",
                children().size(), getY(), getHeight(), getRowWidth());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        boolean result = super.mouseClicked(event, bl);
        Entry entry = getEntryAtPosition(event.x(), event.y());
        if (entry != null && event.button() == 0) {
            if (System.currentTimeMillis() - lastClickTime < 300 && entry == lastClickedEntry) {
                parentScreen.openDetail(entry.getData());
            }
            lastClickTime = System.currentTimeMillis();
            lastClickedEntry = entry;
        }
        return result;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {

        private final ServerLogData data;

        public Entry(ServerLogData data) {
            this.data = data;
        }

        public ServerLogData getData() {
            return data;
        }

        private boolean loggedOnce = false;

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (!loggedOnce) {
                ServerLoggerMod.LOGGER.info("[Server Logger] renderContent called for '{}' x={} y={} w={} h={}",
                        data.getDisplayName(), getX(), getY(), getWidth(), getHeight());
                loggedOnce = true;
            }
            int left = getContentX();
            int top = getContentY();

            // Server display name (white)
            graphics.drawString(minecraft.font, data.getDisplayName(), left + 3, top + 2, 0xFFFFFFFF);

            // Software + plugin count (gray)
            String info = data.software + " | " + data.plugins.size() + " plugin" + (data.plugins.size() != 1 ? "s" : "");
            graphics.drawString(minecraft.font, info, left + 3, top + 13, 0xFFAAAAAA);

            // Timestamp (dark gray)
            graphics.drawString(minecraft.font, data.timestamp, left + 3, top + 24, 0xFF666666);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0) {
                ServerListWidget.this.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(data.getDisplayName());
        }
    }
}
