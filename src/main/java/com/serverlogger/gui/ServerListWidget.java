package com.serverlogger.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ServerListWidget extends ObjectSelectionList<ServerListWidget.Entry> {

    private final ServerLogScreen parentScreen;
    private long lastClickTime = 0;
    private Entry lastClickedEntry = null;
    private Runnable selectionListener;

    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    @Override
    public void setSelected(Entry entry) {
        super.setSelected(entry);
        if (selectionListener != null) selectionListener.run();
    }

    public ServerListWidget(ServerLogScreen parentScreen, Minecraft mc, int width, int height, int top, int itemHeight) {
        super(mc, width, height, top, itemHeight);
        this.parentScreen = parentScreen;
    }

    public void updateEntries(List<ServerLogData> filtered) {
        clearEntries();
        for (ServerLogData data : filtered) {
            addEntry(new Entry(data));
        }
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

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int left = getContentX();
            int top  = getContentY();

            graphics.drawString(minecraft.font, data.getDisplayName(), left + 3, top + 2, 0xFFFFFFFF);

            String info = data.software + " | " + data.plugins.size() + " plugin" + (data.plugins.size() != 1 ? "s" : "");
            graphics.drawString(minecraft.font, info, left + 3, top + 13, 0xFFAAAAAA);

            graphics.drawString(minecraft.font, data.timestamp, left + 3, top + 24, 0xFF666666);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0) {
                ServerListWidget.this.setSelected(this);
                return true;
            }
            if (event.button() == 1) {
                // Right-click copies the display name
                Minecraft.getInstance().keyboardHandler.setClipboard(data.getDisplayName());
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(data.getDisplayName());
        }
    }

    public int getSelectedIndex() {
        Entry sel = getSelected();
        return sel == null ? -1 : children().indexOf(sel);
    }

    public void selectAt(int index) {
        if (index >= 0 && index < children().size()) {
            setSelected(children().get(index));
        }
    }
}
