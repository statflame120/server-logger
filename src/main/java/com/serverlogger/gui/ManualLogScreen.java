package com.serverlogger.gui;

import com.serverlogger.JsonLogger;
import com.serverlogger.ServerDataCollector;
import com.serverlogger.ServerLoggerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ManualLogScreen extends Screen {

    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 36;

    private EditBox domainBox;
    private EditBox ipBox;
    private EditBox portBox;

    public ManualLogScreen() {
        super(Component.literal("Manual Log"));
    }

    @Override
    protected void init() {
        int cx     = width / 2;
        int startY = HEADER_H + 30;

        // Pre-fill with whatever the mod has detected so far
        ServerDataCollector dc = ServerLoggerMod.INSTANCE != null
                ? ServerLoggerMod.INSTANCE.dataCollector : null;

        domainBox = new EditBox(font, cx - 120, startY, 240, 20, Component.literal("domain"));
        domainBox.setHint(Component.literal("domain  (e.g. play.hypixel.net)"));
        if (dc != null && !dc.domain.equals("unknown")) domainBox.setValue(dc.domain);
        addRenderableWidget(domainBox);

        ipBox = new EditBox(font, cx - 120, startY + 40, 160, 20, Component.literal("IP address"));
        ipBox.setHint(Component.literal("IP address"));
        if (dc != null && !dc.ip.equals("unknown")) ipBox.setValue(dc.ip);
        addRenderableWidget(ipBox);

        portBox = new EditBox(font, cx + 45, startY + 40, 75, 20, Component.literal("port"));
        portBox.setHint(Component.literal("port"));
        if (dc != null) portBox.setValue(String.valueOf(dc.port));
        addRenderableWidget(portBox);

        int btnY = height - FOOTER_H + 8;

        addRenderableWidget(Button.builder(Component.literal("Confirm"), btn -> confirm())
                .bounds(cx - 60, btnY, 55, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(cx + 5, btnY, 55, 20).build());
    }

    private void confirm() {
        if (ServerLoggerMod.INSTANCE == null) return;
        ServerDataCollector dc = ServerLoggerMod.INSTANCE.dataCollector;

        // Snapshot originals
        String origDomain = dc.domain;
        String origIp     = dc.ip;
        int    origPort   = dc.port;

        // Apply user-provided overrides (non-empty fields only)
        String newDomain = domainBox.getValue().trim();
        String newIp     = ipBox.getValue().trim();
        String portStr   = portBox.getValue().trim();

        if (!newDomain.isEmpty()) dc.domain = newDomain;
        if (!newIp.isEmpty())     dc.ip     = newIp;
        try {
            int p = Integer.parseInt(portStr);
            if (p > 0 && p <= 65535) dc.port = p;
        } catch (NumberFormatException ignored) {}

        JsonLogger.write(dc);

        // Restore so live detection continues unaffected
        dc.domain = origDomain;
        dc.ip     = origIp;
        dc.port   = origPort;

        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null); // return to game HUD
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, HEADER_H, width, height - FOOTER_H, 0xAA000010);
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);

        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        int cx     = width / 2;
        int startY = HEADER_H + 30;

        g.drawString(font, "Domain:",     cx - 120, startY - 10,      0xFFAAAAAA, false);
        g.drawString(font, "IP Address:", cx - 120, startY + 30,      0xFFAAAAAA, false);
        g.drawString(font, "Port:",       cx + 45,  startY + 30,      0xFFAAAAAA, false);

        g.drawCenteredString(font,
                Component.literal("Overrides detected values and re-logs the current session")
                        .withStyle(s -> s.withColor(0x666666)),
                cx, startY + 70, 0xFFFFFFFF);
    }
}
