package com.serverlogger.gui;

import com.serverlogger.ServerLoggerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ServerLogScreen extends Screen {

    private static final String[] FILTER_MODES = {"All", "Name", "Plugin", "Software"};
    private static final int HEADER_H = 48;
    private static final int FOOTER_H = 36;

    private record RemovedServer(Path filePath, String jsonContent) {}

    private final Screen parent;
    private final Deque<RemovedServer> undoStack = new ArrayDeque<>();
    private boolean keyHandlerRegistered = false;

    private List<ServerLogData> allEntries = new ArrayList<>();
    private ServerListWidget listWidget;
    private EditBox searchBox;
    private int filterIndex = 0;

    private int filteredCount = 0;
    private int totalCount    = 0;

    private Button removeBtn;
    private Button undoBtn;

    public ServerLogScreen(Screen parent) {
        super(Component.literal("Server Logs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        allEntries = ServerLogReader.readAll();
        totalCount = allEntries.size();

        int cx = width / 2;

        // Search box
        searchBox = new EditBox(font, cx - 160, 24, 140, 20, Component.literal("Search..."));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(text -> refreshList());
        addRenderableWidget(searchBox);

        // Filter mode cycle button
        addRenderableWidget(Button.builder(Component.literal("Filter: " + FILTER_MODES[filterIndex]), btn -> {
            filterIndex = (filterIndex + 1) % FILTER_MODES.length;
            btn.setMessage(Component.literal("Filter: " + FILTER_MODES[filterIndex]));
            refreshList();
        }).bounds(cx - 15, 24, 65, 20).build());

        // Glossary button
        addRenderableWidget(Button.builder(Component.literal("Glossary"), btn ->
                minecraft.setScreen(new GlossaryEditorScreen(this))
        ).bounds(cx + 55, 24, 65, 20).build());

        // Options button — top-right corner
        addRenderableWidget(Button.builder(Component.literal("Options"), btn ->
                minecraft.setScreen(new OptionsScreen(this))
        ).bounds(width - 82, 8, 78, 18).build());

        // Server list widget — body between header and footer
        listWidget = new ServerListWidget(this, minecraft, width, height - HEADER_H - FOOTER_H, HEADER_H, 36);
        listWidget.setSelectionListener(this::updateButtonStates);
        addRenderableWidget(listWidget);

        int btnY = height - FOOTER_H + 8;

        addRenderableWidget(Button.builder(Component.literal("Add"), btn ->
                minecraft.setScreen(new AddServerScreen(this))
        ).bounds(cx - 117, btnY, 45, 20).build());

        removeBtn = Button.builder(Component.literal("Remove"), btn -> removeSelected())
                .bounds(cx - 67, btnY, 65, 20).build();
        addRenderableWidget(removeBtn);

        undoBtn = Button.builder(Component.literal("Undo"), btn -> undoRemoval())
                .bounds(cx - 2, btnY, 55, 20).build();
        addRenderableWidget(undoBtn);

        addRenderableWidget(Button.builder(Component.literal("Back"), btn ->
                minecraft.setScreen(parent)
        ).bounds(cx + 58, btnY, 60, 20).build());

        if (!keyHandlerRegistered) {
            keyHandlerRegistered = true;
            ScreenKeyboardEvents.allowKeyPress(this).register((screen, key) -> {
                if (key.key() == GLFW.GLFW_KEY_DELETE && listWidget != null && listWidget.getSelected() != null) {
                    removeSelected();
                    return false;
                }
                return true;
            });
        }

        refreshList();
        updateButtonStates();
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
                    default:
                        return data.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                                || data.plugins.stream()
                                        .anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(query))
                                || data.software.toLowerCase(Locale.ROOT).contains(query);
                }
            }).collect(Collectors.toList());
        }

        filteredCount = filtered.size();
        totalCount    = allEntries.size();
        listWidget.updateEntries(filtered);
        updateButtonStates();
    }

    private void removeSelected() {
        ServerListWidget.Entry selected = listWidget.getSelected();
        if (selected == null) return;

        ServerLogData data = selected.getData();
        int displayedIdx = listWidget.getSelectedIndex();

        Path logDir = FabricLoader.getInstance().getGameDir()
                .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
        Path filePath = logDir.resolve(data.fileName);

        try {
            String content = Files.exists(filePath) ? Files.readString(filePath) : "{}";
            undoStack.push(new RemovedServer(filePath, content));
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to delete log file: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Failed to delete log file: " + e.getMessage());
        }

        allEntries.remove(data);
        refreshList();

        if (!listWidget.children().isEmpty()) {
            listWidget.selectAt(Math.min(displayedIdx, listWidget.children().size() - 1));
        }
        updateButtonStates();
    }

    private void undoRemoval() {
        if (undoStack.isEmpty()) return;
        RemovedServer removed = undoStack.pop();
        try {
            Files.writeString(removed.filePath(), removed.jsonContent(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            ServerLoggerMod.sendMessage("Undo failed: " + e.getMessage());
        }
        allEntries = ServerLogReader.readAll();
        refreshList();
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (undoBtn != null)   undoBtn.active   = !undoStack.isEmpty();
        if (removeBtn != null) removeBtn.active = listWidget.getSelected() != null;
    }

    public void openDetail(ServerLogData data) {
        minecraft.setScreen(new ServerDetailScreen(this, data));
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

        // Title
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);

        // Filter counter "(x / total)" — right-aligned just to the left of the search box
        String counter = filteredCount + " / " + totalCount;
        int counterY = searchBox.getY() + (searchBox.getHeight() - font.lineHeight) / 2;
        int counterX = searchBox.getX() - font.width(counter) - 5;
        g.drawString(font, counter, counterX, counterY, 0xFF888888, false);
    }
}
