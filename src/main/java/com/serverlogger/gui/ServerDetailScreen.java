package com.serverlogger.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Detail view for a single server log entry.
 */
public class ServerDetailScreen extends Screen {

    private final Screen parent;
    private final ServerLogData data;
    private int scrollOffset = 0;

    public ServerDetailScreen(Screen parent, ServerLogData data) {
        super(Component.literal("Server Detail"));
        this.parent = parent;
        this.data = data;
    }

    @Override
    protected void init() {
        int btnY = height - 30;
        int centerX = width / 2;

        // Copy Plugins button
        addRenderableWidget(Button.builder(Component.literal("Copy Plugins"), btn -> {
            String pluginStr = String.join(", ", data.plugins);
            Minecraft.getInstance().keyboardHandler.setClipboard(pluginStr);
        }).bounds(centerX - 110, btnY, 100, 20).build());

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(centerX + 10, btnY, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = 20;
        int y = 15 - scrollOffset;
        int col1 = 0xFFAAAAAA; // label color
        int col2 = 0xFFFFFFFF; // value color
        int sectionColor = 0xFF55FFFF; // section header color

        // Title
        graphics.drawCenteredString(font, data.getDisplayName(), width / 2, y, 0xFFFFFFFF);
        y += 18;

        // ── Server Info ────────────────────────────────────
        graphics.drawString(font, "Server Info", x, y, sectionColor);
        y += 14;
        y = drawField(graphics, x, y, "IP", data.ip, col1, col2);
        y = drawField(graphics, x, y, "Port", String.valueOf(data.port), col1, col2);
        y = drawField(graphics, x, y, "Domain", data.domain, col1, col2);
        y = drawField(graphics, x, y, "Software", data.software, col1, col2);
        y = drawField(graphics, x, y, "Version", data.version, col1, col2);
        y += 6;

        // ── World ──────────────────────────────────────────
        graphics.drawString(font, "World", x, y, sectionColor);
        y += 14;
        y = drawField(graphics, x, y, "Dimension", data.dimension, col1, col2);
        y = drawField(graphics, x, y, "Resource Pack", data.resourcePack != null ? data.resourcePack : "None", col1, col2);
        y += 6;

        // ── Plugins ────────────────────────────────────────
        graphics.drawString(font, "Plugins (" + data.plugins.size() + ")", x, y, sectionColor);
        y += 14;
        if (data.plugins.isEmpty()) {
            graphics.drawString(font, "  None detected", x, y, 0xFF666666);
            y += 11;
        } else {
            for (String plugin : data.plugins) {
                graphics.drawString(font, "  " + plugin, x, y, col2);
                y += 11;
            }
        }
        y += 6;

        // ── Detected Addresses ─────────────────────────────
        graphics.drawString(font, "Detected Addresses (" + data.detectedAddresses.size() + ")", x, y, sectionColor);
        y += 14;
        if (data.detectedAddresses.isEmpty()) {
            graphics.drawString(font, "  None detected", x, y, 0xFF666666);
            y += 11;
        } else {
            for (String addr : data.detectedAddresses) {
                graphics.drawString(font, "  " + addr, x, y, col2);
                y += 11;
            }
        }
    }

    private int drawField(GuiGraphics graphics, int x, int y, String label, String value, int labelColor, int valueColor) {
        graphics.drawString(font, "  " + label + ": ", x, y, labelColor);
        int labelWidth = font.width("  " + label + ": ");
        graphics.drawString(font, value, x + labelWidth, y, valueColor);
        return y + 11;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 10);
        if (scrollOffset < 0) scrollOffset = 0;
        return true;
    }
}
