package com.serverlogger.gui;

import com.serverlogger.ServerLoggerMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class AddServerScreen extends Screen {

    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 36;

    private final Screen parent;

    public AddServerScreen(Screen parent) {
        super(Component.literal("Import"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx   = width / 2;
        int btnY = height - FOOTER_H + 8;

        addRenderableWidget(Button.builder(
                Component.literal("Open Logs Folder"), btn -> openLogsFolder())
                .bounds(cx - 80, btnY, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Back"), btn -> minecraft.setScreen(parent))
                .bounds(cx + 35, btnY, 45, 20).build());
    }

    private void openLogsFolder() {
        try {
            Path logDir = FabricLoader.getInstance().getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);
            String path = logDir.toAbsolutePath().toString();
            String os   = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("explorer.exe", path)
                              : os.contains("mac") ? new ProcessBuilder("open", path)
                              :                      new ProcessBuilder("xdg-open", path);
            pb.start();
        } catch (Exception e) {
            ServerLoggerMod.sendMessage("Could not open logs folder: " + e.getMessage());
        }
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

        int cx      = width / 2;
        int bodyMid = HEADER_H + (height - HEADER_H - FOOTER_H) / 2;
        g.drawCenteredString(font, "Place JSON log files into the logs folder,",
                cx, bodyMid - 10, 0xFF888888);
        g.drawCenteredString(font, "then reopen the Server Logs screen to see them.",
                cx, bodyMid + 4,  0xFF666666);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
