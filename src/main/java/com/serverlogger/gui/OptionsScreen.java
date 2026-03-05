package com.serverlogger.gui;

import com.serverlogger.ConfigManager;
import com.serverlogger.ServerLoggerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class OptionsScreen extends Screen {

    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 36;

    private final Screen parent;

    public OptionsScreen(Screen parent) {
        super(Component.literal("Server Logger \u2013 Options"));
        this.parent = parent;
    }

    private static final int STEP = 50; // vertical distance from one button top to the next

    @Override
    protected void init() {
        ConfigManager cfg = ServerLoggerMod.INSTANCE.config;
        int cx  = width / 2;
        int bw  = 220;
        int bh  = 20;
        int sy  = HEADER_H + 20;

        addRenderableWidget(Button.builder(optLabel("Auto Clipboard", cfg.autoClipboard), btn -> {
            cfg.autoClipboard = !cfg.autoClipboard;
            btn.setMessage(optLabel("Auto Clipboard", cfg.autoClipboard));
            cfg.save();
        }).bounds(cx - bw / 2, sy, bw, bh).build());

        addRenderableWidget(Button.builder(optLabel("Show Toasts", cfg.showToasts), btn -> {
            cfg.showToasts = !cfg.showToasts;
            btn.setMessage(optLabel("Show Toasts", cfg.showToasts));
            cfg.save();
        }).bounds(cx - bw / 2, sy + STEP, bw, bh).build());

        addRenderableWidget(Button.builder(optLabel("Show Messages", cfg.showMessages), btn -> {
            cfg.showMessages = !cfg.showMessages;
            btn.setMessage(optLabel("Show Messages", cfg.showMessages));
            cfg.save();
        }).bounds(cx - bw / 2, sy + STEP * 2, bw, bh).build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
                btn -> minecraft.setScreen(parent))
                .bounds(cx - 30, height - FOOTER_H + 8, 60, 20).build());
    }

    private static Component optLabel(String name, boolean value) {
        return Component.literal(name + ": ")
                .append(Component.literal(value ? "ON" : "OFF")
                        .withStyle(s -> s.withColor(value ? 0x55FF55 : 0xFF5555).withBold(true)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Body background
        g.fill(0, HEADER_H, width, height - FOOTER_H, 0xAA000010);
        // Header
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);
        // Footer
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);

        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        // Descriptions
        int cx  = width / 2;
        int sy  = HEADER_H + 20;
        int bh  = 20;
        int descX = cx - 110;
        int descOff = bh + 8; // pixels below the button top where text sits
        g.drawString(font, "Automatically copy detected plugins to clipboard",
                descX, sy + descOff, 0xFF888888, false);
        g.drawString(font, "Show toast notifications on plugin detection",
                descX, sy + STEP + descOff, 0xFF888888, false);
        g.drawString(font, "Show mod status & error messages in chat (Not recommended)",
                descX, sy + STEP * 2 + descOff, 0xFF888888, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
