package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Global fuzzy search overlay. Appears centered at top of screen (Ctrl+F).
 * Searches across all windows and displays results.
 */
public class GlobalSearchOverlay extends Widget {

    private static final int OVERLAY_WIDTH = 300;
    private static final int RESULT_HEIGHT = 12;
    private static final int MAX_RESULTS = 10;
    private static final int PADDING = 4;

    private final TextField searchField;
    private final List<SearchResult> results = new ArrayList<>();

    private Function<String, List<SearchResult>> searchProvider;
    private BiConsumer<String, String> onResultSelected; // windowId, matchText

    public record SearchResult(String windowId, String windowName, String matchText, int color) {}

    public GlobalSearchOverlay() {
        super(0, 0, OVERLAY_WIDTH, 20);
        searchField = new TextField(0, 0, OVERLAY_WIDTH - PADDING * 2, 14, "Search everywhere...");
        searchField.setOnChange(this::performSearch);
    }

    public void setSearchProvider(Function<String, List<SearchResult>> provider) {
        this.searchProvider = provider;
    }

    public void setOnResultSelected(BiConsumer<String, String> callback) {
        this.onResultSelected = callback;
    }

    public void open() {
        visible = true;
        searchField.setFocused(true);
        searchField.clear();
        results.clear();
        recalcPosition();
    }

    public void close() {
        visible = false;
        searchField.setFocused(false);
        results.clear();
    }

    public boolean isOpen() {
        return visible;
    }

    private void recalcPosition() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        x = (screenW - OVERLAY_WIDTH) / 2;
        y = 10;
    }

    private void performSearch(String query) {
        results.clear();
        if (query == null || query.trim().isEmpty() || searchProvider == null) return;
        List<SearchResult> found = searchProvider.apply(query.trim().toLowerCase());
        if (found != null) {
            results.addAll(found.subList(0, Math.min(found.size(), MAX_RESULTS)));
        }
        recalcHeight();
    }

    private void recalcHeight() {
        height = 14 + PADDING * 2 + results.size() * RESULT_HEIGHT;
        if (results.isEmpty()) height = 14 + PADDING * 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        recalcPosition();
        ColorScheme cs = ColorScheme.get();

        // Background
        RenderUtils.drawRect(g, x, y, OVERLAY_WIDTH, height, cs.tooltipBg());
        RenderUtils.drawBorder(g, x, y, OVERLAY_WIDTH, height, cs.accent());

        // Search field
        searchField.setPosition(x + PADDING, y + PADDING);
        searchField.setSize(OVERLAY_WIDTH - PADDING * 2, 14);
        searchField.render(g, mouseX, mouseY, delta);

        // Results
        int ry = y + PADDING + 14 + 2;
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            boolean hover = mouseX >= x + PADDING && mouseX < x + OVERLAY_WIDTH - PADDING
                    && mouseY >= ry && mouseY < ry + RESULT_HEIGHT;

            if (hover) {
                RenderUtils.drawRect(g, x + PADDING, ry, OVERLAY_WIDTH - PADDING * 2, RESULT_HEIGHT, cs.listHover());
            }

            // Window name tag
            String tag = "[" + result.windowName + "] ";
            RenderUtils.drawText(g, tag, x + PADDING + 2, ry + 1, cs.accent());

            // Match text
            int tagW = RenderUtils.scaledTextWidth(tag);
            String matchDisplay = RenderUtils.trimToWidth(result.matchText, OVERLAY_WIDTH - PADDING * 2 - tagW - 4);
            RenderUtils.drawText(g, matchDisplay, x + PADDING + 2 + tagW, ry + 1,
                    result.color != 0 ? result.color : cs.textPrimary());

            ry += RESULT_HEIGHT;
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (!containsPoint(mouseX, mouseY)) {
            close();
            return false;
        }

        // Forward to search field
        if (searchField.containsPoint(mouseX, mouseY)) {
            return searchField.onMouseClicked(mouseX, mouseY, button);
        }

        // Click on result
        int ry = y + PADDING + 14 + 2;
        for (int i = 0; i < results.size(); i++) {
            if (mouseY >= ry && mouseY < ry + RESULT_HEIGHT) {
                SearchResult result = results.get(i);
                if (onResultSelected != null) {
                    onResultSelected.accept(result.windowId, result.matchText);
                }
                close();
                return true;
            }
            ry += RESULT_HEIGHT;
        }

        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return searchField.onKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (!visible) return false;
        return searchField.onCharTyped(chr, modifiers);
    }
}
