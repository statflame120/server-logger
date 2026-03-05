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
        int cx = width / 2;
        int startY = height / 2 - 45;

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
        }

        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Add Server Entry", width / 2, height / 2 - 58, 0xFFFFFFFF);
    }
}
