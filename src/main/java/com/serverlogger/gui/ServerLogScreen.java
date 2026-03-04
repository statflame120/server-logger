package com.serverlogger.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Main server log list screen with search and filter functionality.
 */
public class ServerLogScreen extends Screen {

    private static final String[] FILTER_MODES = {"All", "Name", "Plugin", "Software"};

    private final Screen parent;
    private List<ServerLogData> allEntries;
    private ServerListWidget listWidget;
    private EditBox searchBox;
    private int filterIndex = 0;

    public ServerLogScreen(Screen parent) {
        super(Component.literal("Server Logs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        allEntries = ServerLogReader.readAll();

        // Search box
        searchBox = new EditBox(font, width / 2 - 120, 22, 190, 20, Component.literal("Search..."));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(text -> refreshList());
        addRenderableWidget(searchBox);

        // Filter button
        addRenderableWidget(Button.builder(Component.literal("Filter: " + FILTER_MODES[filterIndex]), btn -> {
            filterIndex = (filterIndex + 1) % FILTER_MODES.length;
            btn.setMessage(Component.literal("Filter: " + FILTER_MODES[filterIndex]));
            refreshList();
        }).bounds(width / 2 + 75, 22, 80, 20).build());

        // Server list widget
        listWidget = new ServerListWidget(this, minecraft, width, height - 96, 48, 36);
        addRenderableWidget(listWidget);

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 50, height - 30, 100, 20).build());

        refreshList();
    }

    private void refreshList() {
        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        List<ServerLogData> filtered;

        if (query.isEmpty()) {
            filtered = allEntries;
        } else {
            String mode = FILTER_MODES[filterIndex];
            filtered = allEntries.stream().filter(data -> {
                switch (mode) {
                    case "Name":
                        return data.getDisplayName().toLowerCase(Locale.ROOT).contains(query);
                    case "Plugin":
                        return data.plugins.stream()
                                .anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(query));
                    case "Software":
                        return data.software.toLowerCase(Locale.ROOT).contains(query);
                    default: // "All"
                        return data.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                                || data.plugins.stream()
                                        .anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(query))
                                || data.software.toLowerCase(Locale.ROOT).contains(query);
                }
            }).collect(Collectors.toList());
        }

        listWidget.updateEntries(filtered);
    }

    public void openDetail(ServerLogData data) {
        minecraft.setScreen(new ServerDetailScreen(this, data));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
    }
}
