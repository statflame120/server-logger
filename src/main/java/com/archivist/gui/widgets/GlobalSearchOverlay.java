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
 * Global search overlay. Appears centered at top of screen (Ctrl+F).
 * Searches across all windows with filtering, highlighting, and cross-server search.
 */
public class GlobalSearchOverlay extends Widget {

    private static final int OVERLAY_WIDTH = 340;
    private static final int RESULT_HEIGHT = 12;
    private static final int MAX_RESULTS = 20;
    private static final int PADDING = 4;
    private static final int FILTER_HEIGHT = 12;

    private final TextField searchField;
    private final List<SearchResult> results = new ArrayList<>();
    private String currentQuery = "";

    private Function<SearchQuery, List<SearchResult>> searchProvider;
    private BiConsumer<String, String> onResultSelected; // windowId, matchText

    private SearchFilter activeFilter = SearchFilter.ALL;
    private float animProgress = 1f; // 0 = off-screen, 1 = fully visible

    public enum SearchFilter {
        ALL("All"), PLUGINS("Plugins"), BRAND("Brand"), VERSION("Version");
        public final String label;
        SearchFilter(String label) { this.label = label; }
    }

    public record SearchResult(String windowId, String windowName, String matchText, int color, boolean crossServer) {
        public SearchResult(String windowId, String windowName, String matchText, int color) {
            this(windowId, windowName, matchText, color, false);
        }
    }

    public record SearchQuery(String query, SearchFilter filter) {}

    public GlobalSearchOverlay() {
        super(0, 0, OVERLAY_WIDTH, 20);
        searchField = new TextField(0, 0, OVERLAY_WIDTH - PADDING * 2, 14, "Search everywhere...");
        searchField.setOnChange(this::performSearch);
    }

    public void setSearchProvider(Function<SearchQuery, List<SearchResult>> provider) {
        this.searchProvider = provider;
    }

    public void setOnResultSelected(BiConsumer<String, String> callback) {
        this.onResultSelected = callback;
    }

    /** Open fresh (clears state). Used by CTRL+F. */
    public void open() {
        visible = true;
        searchField.setFocused(true);
        searchField.clear();
        results.clear();
        currentQuery = "";
        animProgress = 0f;
        recalcPosition();
    }

    /** Open and restore previous state (if any). Used by hover trigger. */
    public void restore() {
        if (visible) return;
        visible = true;
        searchField.setFocused(true);
        animProgress = 0f;
        recalcPosition();
        if (!currentQuery.isEmpty()) {
            recalcHeight();
        }
    }

    /** Collapse: hide but keep state (query + results). */
    public void collapse() {
        visible = false;
        searchField.setFocused(false);
    }

    /** Close and clear state. */
    public void close() {
        visible = false;
        searchField.setFocused(false);
        results.clear();
        currentQuery = "";
    }

    public boolean isOpen() {
        return visible;
    }

    /** Get the last query (for rendering the impression). */
    public String getLastQuery() {
        return currentQuery;
    }

    /** Whether the search text field is currently focused. */
    public boolean isSearchFieldFocused() {
        return searchField.isFocused();
    }

    /** Whether the user has typed a query. */
    public boolean hasQuery() {
        return !currentQuery.isEmpty();
    }

    private void recalcPosition() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        x = (screenW - OVERLAY_WIDTH) / 2;
        y = 10;
    }

    private void performSearch(String query) {
        results.clear();
        currentQuery = query != null ? query.trim() : "";
        if (currentQuery.isEmpty() || searchProvider == null) {
            recalcHeight();
            return;
        }
        List<SearchResult> found = searchProvider.apply(new SearchQuery(currentQuery.toLowerCase(), activeFilter));
        if (found != null) {
            results.addAll(found.subList(0, Math.min(found.size(), MAX_RESULTS)));
        }
        recalcHeight();
    }

    private void recalcHeight() {
        int contentH = PADDING + 14 + 2 + FILTER_HEIGHT + 4; // search field + filters
        contentH += 10; // counter line
        contentH += results.size() * RESULT_HEIGHT;
        if (results.isEmpty() && !currentQuery.isEmpty()) contentH += RESULT_HEIGHT; // "No results" line
        height = contentH + PADDING;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        recalcPosition();

        // Animate slide-in
        if (animProgress < 1f) {
            animProgress += 0.15f;
            if (animProgress > 1f) animProgress = 1f;
        }
        // Apply animation offset (slide from above)
        int targetY = y;
        y = targetY - (int) ((1f - animProgress) * (height + targetY));

        ColorScheme cs = ColorScheme.get();

        // Background
        RenderUtils.drawRect(g, x, y, OVERLAY_WIDTH, height, cs.tooltipBg());
        RenderUtils.drawBorder(g, x, y, OVERLAY_WIDTH, height, cs.accent());

        // Search field
        searchField.setPosition(x + PADDING, y + PADDING);
        searchField.setSize(OVERLAY_WIDTH - PADDING * 2, 14);
        searchField.render(g, mouseX, mouseY, delta);

        int ry = y + PADDING + 14 + 2;

        // Filter buttons
        int filterX = x + PADDING;
        for (SearchFilter filter : SearchFilter.values()) {
            int fw = RenderUtils.scaledTextWidth(filter.label) + 8;
            boolean isActive = filter == activeFilter;
            boolean filterHover = mouseX >= filterX && mouseX < filterX + fw
                    && mouseY >= ry && mouseY < ry + FILTER_HEIGHT;

            if (isActive) {
                RenderUtils.drawRect(g, filterX, ry, fw, FILTER_HEIGHT, cs.accent());
                RenderUtils.drawText(g, filter.label, filterX + 4, ry + 1, 0xFFFFFFFF);
            } else {
                if (filterHover) {
                    RenderUtils.drawRect(g, filterX, ry, fw, FILTER_HEIGHT, cs.listHover());
                }
                RenderUtils.drawText(g, filter.label, filterX + 4, ry + 1, cs.textSecondary());
            }
            filterX += fw + 2;
        }
        ry += FILTER_HEIGHT + 4;

        // Counter
        long localCount = results.stream().filter(r -> !r.crossServer).count();
        long serverCount = results.stream().filter(r -> r.crossServer).count();
        String counterText;
        if (currentQuery.isEmpty()) {
            counterText = "";
        } else if (results.isEmpty()) {
            counterText = "No results";
        } else {
            counterText = localCount + " result" + (localCount != 1 ? "s" : "");
            if (serverCount > 0) {
                counterText += ", " + serverCount + " server match" + (serverCount != 1 ? "es" : "");
            }
        }
        if (!counterText.isEmpty()) {
            int counterColor = results.isEmpty() ? cs.textSecondary() : cs.textPrimary();
            RenderUtils.drawText(g, counterText, x + PADDING + 2, ry, counterColor);
        }
        ry += 10;

        // Results
        if (results.isEmpty() && !currentQuery.isEmpty()) {
            RenderUtils.drawText(g, "No matches found", x + PADDING + 2, ry + 1, cs.textSecondary());
        } else {
            String lastCategory = null;
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                boolean hover = mouseX >= x + PADDING && mouseX < x + OVERLAY_WIDTH - PADDING
                        && mouseY >= ry && mouseY < ry + RESULT_HEIGHT;

                if (hover) {
                    RenderUtils.drawRect(g, x + PADDING, ry, OVERLAY_WIDTH - PADDING * 2, RESULT_HEIGHT, cs.listHover());
                }

                // Window name tag
                String tag = "[" + result.windowName + "] ";
                int tagColor = result.crossServer ? 0xFFFFAA00 : cs.accent();
                RenderUtils.drawText(g, tag, x + PADDING + 2, ry + 1, tagColor);

                // Match text with highlighting
                int tagW = RenderUtils.scaledTextWidth(tag);
                int availW = OVERLAY_WIDTH - PADDING * 2 - tagW - 4;
                int textColor = result.color != 0 ? result.color : cs.textPrimary();
                int highlightColor = result.crossServer ? 0xFFFFAA00 : cs.accent();

                RenderUtils.drawHighlightedText(g, result.matchText, currentQuery,
                        x + PADDING + 2 + tagW, ry + 1, textColor, highlightColor, availW);

                ry += RESULT_HEIGHT;
            }
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (!containsPoint(mouseX, mouseY)) {
            collapse();
            return false;
        }

        // Forward to search field
        if (searchField.containsPoint(mouseX, mouseY)) {
            return searchField.onMouseClicked(mouseX, mouseY, button);
        }

        // Check filter clicks
        int ry = y + PADDING + 14 + 2;
        int filterX = x + PADDING;
        for (SearchFilter filter : SearchFilter.values()) {
            int fw = RenderUtils.scaledTextWidth(filter.label) + 8;
            if (mouseX >= filterX && mouseX < filterX + fw
                    && mouseY >= ry && mouseY < ry + FILTER_HEIGHT) {
                activeFilter = filter;
                performSearch(searchField.getText());
                return true;
            }
            filterX += fw + 2;
        }

        // Click on result
        int resultStartY = ry + FILTER_HEIGHT + 4 + 10;
        for (int i = 0; i < results.size(); i++) {
            if (mouseY >= resultStartY && mouseY < resultStartY + RESULT_HEIGHT) {
                SearchResult result = results.get(i);
                if (onResultSelected != null) {
                    onResultSelected.accept(result.windowId, result.matchText);
                }
                close();
                return true;
            }
            resultStartY += RESULT_HEIGHT;
        }

        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            collapse();
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
