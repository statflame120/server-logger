package com.serverlogger.gui;

import com.serverlogger.ServerLoggerMod;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;

public class BreadcrumbEditorScreen extends Screen {

    private static final int HEADER_H = 50;
    private static final int FOOTER_H = 36;

    private final Screen parent;
    private final LinkedHashSet<String> editedServers;
    private final Deque<LinkedHashSet<String>> undoStack = new ArrayDeque<>();
    private boolean keyHandlerRegistered = false;

    private BreadcrumbListWidget listWidget;
    private EditBox serverBox;
    private Button undoBtn;
    private Button removeBtn;

    public BreadcrumbEditorScreen(Screen parent) {
        super(Component.literal("Breadcrumb Editor"));
        this.parent = parent;
        this.editedServers = new LinkedHashSet<>(
                ServerLoggerMod.INSTANCE.breadcrumbResolver.getServers()
        );
    }

    @Override
    protected void init() {
        int cx = width / 2;

        serverBox = new EditBox(font, cx - 215, 25, 260, 20, Component.literal("server address"));
        serverBox.setHint(Component.literal("server address"));
        addRenderableWidget(serverBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addEntry())
                .bounds(cx + 50, 25, 45, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Import"), btn -> openConfigDir())
                .bounds(cx + 100, 25, 60, 20).build());

        listWidget = new BreadcrumbListWidget(minecraft, width, height - HEADER_H - FOOTER_H, HEADER_H, 22);
        listWidget.updateEntries(editedServers);
        listWidget.setSelectionListener(this::updateButtonStates);
        addRenderableWidget(listWidget);

        int btnY = height - 30;

        addRenderableWidget(Button.builder(Component.literal("Copy All"), btn -> copyAll())
                .bounds(cx - 180, btnY, 75, 20).build());

        removeBtn = Button.builder(Component.literal("Remove"), btn -> removeSelected())
                .bounds(cx - 100, btnY, 75, 20).build();
        addRenderableWidget(removeBtn);

        undoBtn = Button.builder(Component.literal("Undo"), btn -> undo())
                .bounds(cx - 20, btnY, 65, 20).build();
        addRenderableWidget(undoBtn);

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveAndClose())
                .bounds(cx + 50, btnY, 55, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreen(parent))
                .bounds(cx + 110, btnY, 55, 20).build());

        if (!keyHandlerRegistered) {
            keyHandlerRegistered = true;
            //? if >=1.21.9 {
            ScreenKeyboardEvents.allowKeyPress(this).register((screen, key) -> {
                boolean editBoxFocused = serverBox != null && serverBox.isFocused();
                int k = key.key();
                if (!editBoxFocused) {
                    if ((k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE)
                            && listWidget != null && listWidget.getSelected() != null) {
                        removeSelected();
                        return false;
                    }
                    if (k == GLFW.GLFW_KEY_Z && (key.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                        undo();
                        return false;
                    }
                }
                return true;
            });
            //?} else {
            /*ScreenKeyboardEvents.allowKeyPress(this).register((screen, key, scancode, modifiers) -> {
                boolean editBoxFocused = serverBox != null && serverBox.isFocused();
                int k = key;
                if (!editBoxFocused) {
                    if ((k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE)
                            && listWidget != null && listWidget.getSelected() != null) {
                        removeSelected();
                        return false;
                    }
                    if (k == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                        undo();
                        return false;
                    }
                }
                return true;
            });
            *///?}
        }

        updateButtonStates();
    }

    private void pushUndo() {
        undoStack.push(new LinkedHashSet<>(editedServers));
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        LinkedHashSet<String> prev = undoStack.pop();
        editedServers.clear();
        editedServers.addAll(prev);
        listWidget.updateEntries(editedServers);
        updateButtonStates();
    }

    private void addEntry() {
        String server = serverBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (!server.isEmpty() && !editedServers.contains(server)) {
            pushUndo();
            editedServers.add(server);
            listWidget.updateEntries(editedServers);
            serverBox.setValue("");
            updateButtonStates();
        }
    }

    private void openConfigDir() {
        try {
            String path = FabricLoader.getInstance().getConfigDir().toAbsolutePath().toString();
            String os   = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("explorer.exe", path)
                              : os.contains("mac") ? new ProcessBuilder("open", path)
                              :                      new ProcessBuilder("xdg-open", path);
            pb.start();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to open config directory: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Failed to open config directory: " + e.getMessage());
        }
    }

    private void copyAll() {
        StringBuilder sb = new StringBuilder();
        editedServers.forEach(s -> sb.append(s).append('\n'));
        minecraft.keyboardHandler.setClipboard(sb.toString().trim());
    }

    private void removeSelected() {
        BreadcrumbListWidget.Entry selected = listWidget.getSelected();
        if (selected == null) return;

        int idx = listWidget.getSelectedIndex();
        pushUndo();
        editedServers.remove(selected.getServer());
        listWidget.updateEntries(editedServers);

        if (!listWidget.children().isEmpty()) {
            listWidget.selectAt(Math.min(idx, listWidget.children().size() - 1));
        }
        updateButtonStates();
    }

    private void saveAndClose() {
        ServerLoggerMod.INSTANCE.breadcrumbResolver.setServers(editedServers);
        ServerLoggerMod.INSTANCE.breadcrumbResolver.save();
        minecraft.setScreen(parent);
    }

    private void updateButtonStates() {
        if (undoBtn != null)   undoBtn.active   = !undoStack.isEmpty();
        if (removeBtn != null) removeBtn.active = listWidget.getSelected() != null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, HEADER_H, width, height - FOOTER_H, 0xAA000010);
        graphics.fill(0, 0, width, HEADER_H, 0xCC050510);
        graphics.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);
        graphics.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        graphics.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawString(font, "Server:", width / 2 - 215, 13, 0xFFAAAAAA);
    }
}
