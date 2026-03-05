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
import java.util.LinkedHashMap;
import java.util.Map;

public class GlossaryEditorScreen extends Screen {

    private final Screen parent;
    private final Map<String, String> editedEntries;
    private final Deque<Map<String, String>> undoStack = new ArrayDeque<>();
    private boolean keyHandlerRegistered = false;

    private GlossaryListWidget listWidget;
    private EditBox commandBox;
    private EditBox pluginBox;
    private Button undoBtn;
    private Button removeBtn;

    public GlossaryEditorScreen(Screen parent) {
        super(Component.literal("Glossary Editor"));
        this.parent = parent;
        this.editedEntries = new LinkedHashMap<>(
                ServerLoggerMod.INSTANCE.pluginGlossary.getEntries()
        );
    }

    @Override
    protected void init() {
        int cx = width / 2;

        commandBox = new EditBox(font, cx - 215, 25, 130, 20, Component.literal("command"));
        commandBox.setHint(Component.literal("command"));
        addRenderableWidget(commandBox);

        pluginBox = new EditBox(font, cx - 80, 25, 130, 20, Component.literal("plugin name"));
        pluginBox.setHint(Component.literal("plugin name"));
        addRenderableWidget(pluginBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addEntry())
                .bounds(cx + 55, 25, 45, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Import"), btn -> importFromFile())
                .bounds(cx + 105, 25, 55, 20).build());

        listWidget = new GlossaryListWidget(minecraft, width, height - 90, 50, 22);
        listWidget.updateEntries(editedEntries);
        listWidget.setSelectionListener(this::updateButtonStates);
        addRenderableWidget(listWidget);

        int btnY = height - 30;

        addRenderableWidget(Button.builder(Component.literal("Copy All"), btn -> exportToClipboard())
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
            ScreenKeyboardEvents.allowKeyPress(this).register((screen, key) -> {
                if (key.key() == GLFW.GLFW_KEY_DELETE && listWidget != null && listWidget.getSelected() != null) {
                    removeSelected();
                    return false;
                }
                return true;
            });
        }

        updateButtonStates();
    }

    private void pushUndo() {
        undoStack.push(new LinkedHashMap<>(editedEntries));
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Map<String, String> prev = undoStack.pop();
        editedEntries.clear();
        editedEntries.putAll(prev);
        listWidget.updateEntries(editedEntries);
        updateButtonStates();
    }

    private void addEntry() {
        String cmd    = commandBox.getValue().trim().toLowerCase();
        String plugin = pluginBox.getValue().trim();
        if (!cmd.isEmpty() && !plugin.isEmpty()) {
            pushUndo();
            editedEntries.put(cmd, plugin);
            listWidget.updateEntries(editedEntries);
            commandBox.setValue("");
            pluginBox.setValue("");
            updateButtonStates();
        }
    }

    private void importFromFile() {
        try {
            String path = FabricLoader.getInstance().getConfigDir().toAbsolutePath().toString();
            String os   = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("explorer.exe", path)
                              : os.contains("mac") ? new ProcessBuilder("open", path)
                              :                      new ProcessBuilder("xdg-open", path);
            pb.start();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to open config directory: {}", e.getMessage());
        }
    }

    private void exportToClipboard() {
        StringBuilder sb = new StringBuilder();
        editedEntries.forEach((cmd, plugin) ->
                sb.append(cmd).append('=').append(plugin).append('\n'));
        minecraft.keyboardHandler.setClipboard(sb.toString());
    }

    private void removeSelected() {
        GlossaryListWidget.Entry selected = listWidget.getSelected();
        if (selected == null) return;

        int idx = listWidget.getSelectedIndex();
        pushUndo();
        editedEntries.remove(selected.getCommand());
        listWidget.updateEntries(editedEntries);

        if (!listWidget.children().isEmpty()) {
            listWidget.selectAt(Math.min(idx, listWidget.children().size() - 1));
        }
        updateButtonStates();
    }

    private void saveAndClose() {
        ServerLoggerMod.INSTANCE.pluginGlossary.setEntries(editedEntries);
        ServerLoggerMod.INSTANCE.pluginGlossary.save();
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawString(font, "Command:", width / 2 - 215, 13, 0xFFAAAAAA);
        graphics.drawString(font, "Plugin:",  width / 2 - 78,  13, 0xFFAAAAAA);
    }
}
