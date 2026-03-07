package com.serverlogger.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GlossaryListWidget extends ObjectSelectionList<GlossaryListWidget.Entry> {

    private Runnable selectionListener;

    public GlossaryListWidget(Minecraft mc, int width, int height, int top, int itemHeight) {
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

    public void updateEntries(Map<String, String> entries) {
        clearEntries();
        entries.forEach((cmd, plugin) -> addEntry(new Entry(cmd, plugin)));
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {

        private final String command;
        private final String plugin;

        public Entry(String command, String plugin) {
            this.command = command;
            this.plugin = plugin;
        }

        public String getCommand() {
            return command;
        }

        //? if >=1.21.9 {
        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int left = getContentX();
            int top  = getContentY();
            graphics.drawString(minecraft.font, command, left + 4, top + 5, 0xFFFFFFFF);
            graphics.drawString(minecraft.font, "\u2192", left + 120, top + 5, 0xFF888888);
            graphics.drawString(minecraft.font, plugin, left + 136, top + 5, 0xFF55FF55);
        }
        //?} else {
        /*@Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(minecraft.font, command, left + 4, top + 5, 0xFFFFFFFF);
            graphics.drawString(minecraft.font, "\u2192", left + 120, top + 5, 0xFF888888);
            graphics.drawString(minecraft.font, plugin, left + 136, top + 5, 0xFF55FF55);
        }
        *///?}

        //? if >=1.21.9 {
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0) {
                GlossaryListWidget.this.setSelected(this);
                return true;
            }
            if (event.button() == 1) {
                // Right-click copies the entry text
                Minecraft.getInstance().keyboardHandler.setClipboard(command + "=" + plugin);
                return true;
            }
            return false;
        }
        //?} else {
        /*@Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                GlossaryListWidget.this.setSelected(this);
                return true;
            }
            if (button == 1) {
                // Right-click copies the entry text
                Minecraft.getInstance().keyboardHandler.setClipboard(command + "=" + plugin);
                return true;
            }
            return false;
        }
        *///?}

        @Override
        public Component getNarration() {
            return Component.literal(command + " -> " + plugin);
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
