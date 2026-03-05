package com.serverlogger.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ServerDetailScreen extends Screen {

    private static final int HEADER_H = 68;
    private static final int FOOTER_H = 30;

    private final Screen parent;
    private final ServerLogData data;
    private int scrollOffset = 0;

    public ServerDetailScreen(Screen parent, ServerLogData data) {
        super(Component.literal("Server Detail"));
        this.parent = parent;
        this.data   = data;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int btnY = height - FOOTER_H + 5;

        addRenderableWidget(Button.builder(Component.literal("Copy Plugins"), btn -> {
            String s = String.join(", ", data.plugins);
            Minecraft.getInstance().keyboardHandler.setClipboard(s);
        }).bounds(cx - 115, btnY, 105, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(cx + 10, btnY, 105, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        renderHeader(graphics);
        renderScrollableContent(graphics);
        renderFooterLine(graphics);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, width, HEADER_H, 0xCC050510);
        graphics.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);

        String displayName = data.getDisplayName();
        graphics.drawCenteredString(font, Component.literal(displayName)
                        .withStyle(s -> s.withColor(0xFFFF55).withBold(true)),
                width / 2, 6, 0xFFFFFFFF);

        int row1 = 22;
        int row2 = 34;
        int row3 = 46;
        int col2 = width / 2 + 4;

        drawLabel(graphics, "IP",       data.ip + ":" + data.port, 8, row1, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(graphics, "Software", data.software,              col2, row1, 0xFFAAAAAA, 0xFFFFFFFF);

        drawLabel(graphics, "Version",  data.version,               8, row2, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(graphics, "Logged",   data.timestamp,             col2, row2, 0xFFAAAAAA, 0xFFFFFFFF);

        if (!data.domain.equals("unknown") && !data.domain.equals(data.ip)) {
            drawLabel(graphics, "Domain", data.domain, 8, row3, 0xFFAAAAAA, 0xFF88AAFF);
        }
        drawLabel(graphics, "Plugins", String.valueOf(data.plugins.size()), col2, row3, 0xFFAAAAAA, 0xFF55FF55);
    }

    private void drawLabel(GuiGraphics g, String label, String value, int x, int y, int lc, int vc) {
        g.drawString(font, label + ": ", x, y, lc);
        int lw = font.width(label + ": ");
        g.drawString(font, value, x + lw, y, vc);
    }

    private void renderScrollableContent(GuiGraphics graphics) {
        int contentTop    = HEADER_H;
        int contentBottom = height - FOOTER_H;

        graphics.enableScissor(0, contentTop, width, contentBottom);

        int x = 10;
        int y = contentTop + 6 - scrollOffset;

        y = renderPluginPanel(graphics, x, y);
        y += 8;
        y = renderSection(graphics, x, y, "Detected Addresses", data.detectedAddresses, 0xFF88AAFF, 0xFFCCCCCC);
        y += 8;
        y = renderWorldsSection(graphics, x, y);

        graphics.disableScissor();
    }

    private int renderPluginPanel(GuiGraphics graphics, int x, int y) {
        int panelW = width - x * 2;
        int innerLines = Math.max(1, data.plugins.size());
        int panelH = 15 + innerLines * 11 + 6;

        graphics.fill(x, y, x + panelW, y + panelH, 0x88001830);
        graphics.fill(x, y, x + panelW, y + 14, 0xAA003060);
        graphics.fill(x, y + 14, x + panelW, y + 15, 0xFF004488);

        graphics.drawString(font,
                "Plugins  (" + data.plugins.size() + ")",
                x + 5, y + 3, 0xFF55AAFF);

        y += 16;

        if (data.plugins.isEmpty()) {
            graphics.drawString(font, "  None detected", x + 5, y, 0xFF555555);
            y += 11;
        } else {
            for (String plugin : data.plugins) {
                graphics.drawString(font, "  \u25B8 " + plugin, x + 5, y, 0xFFEEEEEE);
                y += 11;
            }
        }
        y += 6;
        return y;
    }

    private int renderSection(GuiGraphics graphics, int x, int y,
                              String title, List<String> items,
                              int titleColor, int itemColor) {
        graphics.drawString(font, title + " (" + items.size() + ")", x, y, titleColor);
        graphics.fill(x, y + 11, x + font.width(title + " (" + items.size() + ")"), y + 12, titleColor & 0x88FFFFFF);
        y += 14;
        if (items.isEmpty()) {
            graphics.drawString(font, "  None", x + 4, y, 0xFF555555);
            y += 11;
        } else {
            for (String item : items) {
                graphics.drawString(font, "  " + item, x + 4, y, itemColor);
                y += 11;
            }
        }
        return y;
    }

    private int renderWorldsSection(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "Worlds Visited (" + data.worlds.size() + ")", x, y, 0xFFFFAA55);
        graphics.fill(x, y + 11, x + font.width("Worlds Visited (" + data.worlds.size() + ")"), y + 12, 0x88FFAA55);
        y += 14;
        if (data.worlds.isEmpty()) {
            graphics.drawString(font, "  None recorded", x + 4, y, 0xFF555555);
            y += 11;
        } else {
            for (ServerLogData.WorldSession ws : data.worlds) {
                String line = "  " + ws.dimension;
                if (ws.resourcePack != null) line += "  [pack]";
                graphics.drawString(font, line, x + 4, y, 0xFFCCCCCC);
                y += 11;
                if (ws.resourcePack != null) {
                    String short_pack = ws.resourcePack.length() > 50
                            ? ws.resourcePack.substring(0, 47) + "..." : ws.resourcePack;
                    graphics.drawString(font, "      " + short_pack, x + 4, y, 0xFF888888);
                    y += 11;
                }
            }
        }
        return y;
    }

    private void renderFooterLine(GuiGraphics graphics) {
        graphics.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0xFF334466);
        graphics.fill(0, height - FOOTER_H + 1, width, height, 0xCC050510);
    }

    private int getContentHeight() {
        int h = 12;
        h += 15 + Math.max(1, data.plugins.size()) * 11 + 6 + 8;
        h += 14 + Math.max(1, data.detectedAddresses.size()) * 11 + 8;
        int worldLines = 0;
        for (ServerLogData.WorldSession ws : data.worlds) {
            worldLines++;
            if (ws.resourcePack != null) worldLines++;
        }
        h += 14 + Math.max(1, worldLines) * 11;
        return h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, getContentHeight() - (height - HEADER_H - FOOTER_H));
        scrollOffset -= (int) (verticalAmount * 10);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }
}
