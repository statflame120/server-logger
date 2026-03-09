package com.archivist.gui;

import com.archivist.ArchivistMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Locale;

public class ExceptionListWidget extends ObjectSelectionList<ExceptionListWidget.Entry> {

    private Runnable selectionListener;

    public ExceptionListWidget(Minecraft mc, int width, int height, int top, int itemHeight) {
        super(mc, width, height, top, itemHeight);
    }

    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    @Override
    public void setSelected(Entry entry) {
        super.setSelected(entry);
        if (selectionListener != null) selectionListener.run();
    }

    public void updateEntries(Collection<String> servers) {
        clearEntries();
        servers.forEach(s -> addEntry(new Entry(s)));
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {

        private final String server;

        public Entry(String server) {
            this.server = server;
        }

        public String getServer() {
            return server;
        }

        //? if >=1.21.9 {
        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int left = getContentX();
            int top  = getContentY();
            int color = isCurrentServer() ? 0xFF5599FF : 0xFFFFFFFF;
            graphics.drawString(minecraft.font, server, left + 4, top + 5, color);
        }
        //?} else {
        /*@Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int color = isCurrentServer() ? 0xFF5599FF : 0xFFFFFFFF;
            graphics.drawString(minecraft.font, server, left + 4, top + 5, color);
        }
        *///?}

        private boolean isCurrentServer() {
            if (ArchivistMod.INSTANCE == null) return false;
            String domain = ArchivistMod.INSTANCE.dataCollector.domain;
            if (domain == null || domain.equals("unknown") || domain.isBlank()) return false;
            return domain.toLowerCase(Locale.ROOT).contains(server);
        }

        //? if >=1.21.9 {
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0) {
                ExceptionListWidget.this.setSelected(this);
                return true;
            }
            if (event.button() == 1) {
                Minecraft.getInstance().keyboardHandler.setClipboard(server);
                return true;
            }
            return false;
        }
        //?} else {
        /*@Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                ExceptionListWidget.this.setSelected(this);
                return true;
            }
            if (button == 1) {
                Minecraft.getInstance().keyboardHandler.setClipboard(server);
                return true;
            }
            return false;
        }
        *///?}

        @Override
        public Component getNarration() {
            return Component.literal(server);
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
