package com.serverlogger.gui;

import com.google.gson.*;
import com.serverlogger.ServerLoggerMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.*;
import java.time.LocalDate;

public class AddServerScreen extends Screen {

    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 36;

    private final Screen parent;
    private EditBox ipBox;
    private EditBox portBox;
    private EditBox domainBox;

    public AddServerScreen(Screen parent) {
        super(Component.literal("Add Server"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx     = width / 2;
        int bodyMid = HEADER_H + (height - HEADER_H - FOOTER_H) / 2;
        int startY  = bodyMid - 45;

        ipBox = new EditBox(font, cx - 100, startY, 200, 20, Component.literal("IP"));
        ipBox.setHint(Component.literal("IP Address"));
        addRenderableWidget(ipBox);

        portBox = new EditBox(font, cx - 100, startY + 25, 95, 20, Component.literal("Port"));
        portBox.setHint(Component.literal("Port (25565)"));
        addRenderableWidget(portBox);

        domainBox = new EditBox(font, cx - 100, startY + 50, 200, 20, Component.literal("Domain"));
        domainBox.setHint(Component.literal("Domain (optional)"));
        addRenderableWidget(domainBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), btn -> confirm())
                .bounds(cx - 55, startY + 80, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> minecraft.setScreen(parent))
                .bounds(cx + 5, startY + 80, 50, 20).build());
    }

    private void confirm() {
        String ip = ipBox.getValue().trim();
        if (ip.isEmpty()) return;

        int port = 25565;
        try { port = Integer.parseInt(portBox.getValue().trim()); } catch (Exception ignored) {}

        String domain = domainBox.getValue().trim();
        if (domain.isEmpty()) domain = ip;

        try {
            Path logDir = FabricLoader.getInstance().getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);

            String baseName = (!domain.equals(ip))
                    ? domain.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : ip.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + port;
            Path outFile = logDir.resolve(baseName + ".json");

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", LocalDate.now().toString());

            JsonObject info = new JsonObject();
            info.addProperty("ip",       ip);
            info.addProperty("port",     port);
            info.addProperty("domain",   domain);
            info.addProperty("software", "unknown");
            info.addProperty("version",  "unknown");
            root.add("server_info", info);
            root.add("plugins",            new JsonArray());
            root.add("detected_addresses", new JsonArray());
            root.add("worlds",             new JsonArray());

            Files.writeString(outFile, new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to create server entry: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Failed to create server entry: " + e.getMessage());
        }

        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Body background
        g.fill(0, HEADER_H, width, height - FOOTER_H, 0xAA000010);
        // Header panel
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);
        // Footer panel
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);

        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        // Field labels
        int cx    = width / 2;
        int bodyMid = HEADER_H + (height - HEADER_H - FOOTER_H) / 2;
        int startY  = bodyMid - 45;
        g.drawString(font, "IP Address:",      cx - 100, startY - 10,     0xFFAAAAAA, false);
        g.drawString(font, "Port:",            cx - 100, startY + 15,     0xFFAAAAAA, false);
        g.drawString(font, "Domain:",          cx - 100, startY + 40,     0xFFAAAAAA, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
