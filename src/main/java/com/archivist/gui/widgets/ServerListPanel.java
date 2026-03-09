package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Server list panel showing all previously logged servers.
 * Contains a search bar, sortable scrollable list, and bottom action bar.
 */
public class ServerListPanel extends Widget {

    private static final int SEARCH_BAR_HEIGHT = 16;
    private static final int BOTTOM_BAR_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int HEADER_GAP = 3;
    private static final int INNER_PAD = 4;

    private final List<ServerListEntry> entries = new ArrayList<>();
    private final List<ServerListEntry> filteredEntries = new ArrayList<>();
    private int selectedIndex = -1;
    private float scrollOffset = 0;
    private String filterText = "";

    public enum SortMode { LAST_VISITED, NAME, PLUGIN_COUNT, SESSION_COUNT }
    private SortMode sortMode = SortMode.LAST_VISITED;

    // Child widgets
    private TextField searchField;
    private Button sortButton;
    private Button viewDetailsButton;
    private Button connectButton;
    private Button exportButton;
    private Button deleteButton;

    // Callbacks
    private Consumer<String> onServerSelected;
    private Consumer<String> onViewDetails;
    private Consumer<String> onExportServer;
    private Consumer<String> onDeleteServer;
    private Consumer<String> onQuickConnect;

    // Scrollbar drag
    private boolean draggingScrollbar = false;
    private double scrollbarDragStartY = 0;
    private float scrollOffsetDragStart = 0;

    // Smooth scroll
    private float targetScrollOffset = 0;
    private static final float SCROLL_LERP_SPEED = 0.3f;

    public ServerListPanel(int x, int y, int width, int height) {
        super(x, y, width, height);
        initChildWidgets();
    }

    private void initChildWidgets() {
        int cx = x + INNER_PAD;
        int cw = width - INNER_PAD * 2;

        searchField = new TextField(cx, y + 2, cw - 70, SEARCH_BAR_HEIGHT, "Search servers...");
        searchField.setOnChange(text -> {
            filterText = text.toLowerCase();
            rebuildFilteredList();
        });

        sortButton = new Button(cx + cw - 65, y + 2, 65, SEARCH_BAR_HEIGHT,
                "Sort: Recent", this::cycleSortMode);

        int bottomY = y + height - BOTTOM_BAR_HEIGHT + 2;
        viewDetailsButton = new Button(cx, bottomY, 55, 14, "Details", this::fireViewDetails);
        connectButton = new Button(cx + 60, bottomY, 50, 14, "Connect", this::fireQuickConnect);
        exportButton = new Button(cx + 115, bottomY, 45, 14, "Export", this::fireExportServer);
        deleteButton = new Button(cx + 165, bottomY, 45, 14, "Delete", this::fireDeleteServer);
    }

    /** Must be called when the panel is repositioned by its parent DraggableWindow. */
    private void repositionChildren() {
        int cx = x + INNER_PAD;
        int cw = width - INNER_PAD * 2;

        searchField.setPosition(cx, y + 2);
        searchField.setSize(cw - 70, SEARCH_BAR_HEIGHT);

        sortButton.setPosition(cx + cw - 65, y + 2);
        sortButton.setSize(65, SEARCH_BAR_HEIGHT);

        int bottomY = y + height - BOTTOM_BAR_HEIGHT + 2;
        viewDetailsButton.setPosition(cx, bottomY);
        connectButton.setPosition(cx + 60, bottomY);
        exportButton.setPosition(cx + 115, bottomY);
        deleteButton.setPosition(cx + 165, bottomY);
    }

    // ── Data Management ──────────────────────────────────────────────

    public void addServer(String address, String version, String brand,
                          int pluginCount, int sessionCount, String lastVisited) {
        ServerListEntry entry = new ServerListEntry(
                x + INNER_PAD, 0,
                width - INNER_PAD * 2 - SCROLLBAR_WIDTH,
                address, version, brand, pluginCount, sessionCount, lastVisited
        );
        entries.add(entry);
        rebuildFilteredList();
    }

    public void clearServers() {
        entries.clear();
        filteredEntries.clear();
        selectedIndex = -1;
        scrollOffset = 0;
    }

    public int getServerCount() { return entries.size(); }

    public String getSelectedAddress() {
        if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
            return filteredEntries.get(selectedIndex).getServerAddress();
        }
        return null;
    }

    private void rebuildFilteredList() {
        filteredEntries.clear();
        for (ServerListEntry entry : entries) {
            if (filterText.isEmpty() || entry.getServerAddress().toLowerCase().contains(filterText)) {
                filteredEntries.add(entry);
            }
        }
        sortFilteredList();
        selectedIndex = Math.min(selectedIndex, filteredEntries.size() - 1);
        clampScroll();
    }

    private void sortFilteredList() {
        switch (sortMode) {
            case NAME -> filteredEntries.sort(Comparator.comparing(ServerListEntry::getServerAddress));
            case PLUGIN_COUNT -> filteredEntries.sort(
                    Comparator.comparingInt(ServerListEntry::getPluginCount).reversed());
            case SESSION_COUNT -> filteredEntries.sort(
                    Comparator.comparingInt(ServerListEntry::getSessionCount).reversed());
            case LAST_VISITED -> {} // file-load order (newest first from ServerLogReader)
        }
    }

    private void cycleSortMode() {
        SortMode[] modes = SortMode.values();
        int next = (sortMode.ordinal() + 1) % modes.length;
        sortMode = modes[next];
        sortButton.setText(switch (sortMode) {
            case LAST_VISITED -> "Sort: Recent";
            case NAME -> "Sort: Name";
            case PLUGIN_COUNT -> "Sort: Plugins";
            case SESSION_COUNT -> "Sort: Sessions";
        });
        rebuildFilteredList();
    }

    // ── Callbacks ────────────────────────────────────────────────────

    public void setOnServerSelected(Consumer<String> cb) { this.onServerSelected = cb; }
    public void setOnViewDetails(Consumer<String> cb) { this.onViewDetails = cb; }
    public void setOnExportServer(Consumer<String> cb) { this.onExportServer = cb; }
    public void setOnDeleteServer(Consumer<String> cb) { this.onDeleteServer = cb; }
    public void setOnQuickConnect(Consumer<String> cb) { this.onQuickConnect = cb; }

    private void fireQuickConnect() {
        String addr = getSelectedAddress();
        if (addr != null && onQuickConnect != null) onQuickConnect.accept(addr);
    }

    private void fireViewDetails() {
        String addr = getSelectedAddress();
        if (addr != null && onViewDetails != null) onViewDetails.accept(addr);
    }

    private void fireExportServer() {
        String addr = getSelectedAddress();
        if (addr != null && onExportServer != null) onExportServer.accept(addr);
    }

    private void fireDeleteServer() {
        String addr = getSelectedAddress();
        if (addr != null && onDeleteServer != null) onDeleteServer.accept(addr);
    }

    // ── Rendering ────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        repositionChildren();
        ColorScheme cs = ColorScheme.get();

        // Smooth scroll lerp (skip while dragging scrollbar)
        if (!draggingScrollbar) {
            scrollOffset += (targetScrollOffset - scrollOffset) * SCROLL_LERP_SPEED;
            if (Math.abs(targetScrollOffset - scrollOffset) < 0.5f) {
                scrollOffset = targetScrollOffset;
            }
        }

        int listTop = y + SEARCH_BAR_HEIGHT + HEADER_GAP;
        int listBottom = y + height - BOTTOM_BAR_HEIGHT;
        int listHeight = listBottom - listTop;
        int entryWidth = width - INNER_PAD * 2 - (hasScrollbar() ? SCROLLBAR_WIDTH + 1 : 0);

        // Search bar area
        searchField.render(g, mouseX, mouseY, delta);
        sortButton.render(g, mouseX, mouseY, delta);

        // Separator below search
        RenderUtils.drawHLine(g, x + INNER_PAD, listTop - 1, width - INNER_PAD * 2, cs.separator());

        // Entries (scissored)
        RenderUtils.enableScissor(g, x + INNER_PAD, listTop, width - INNER_PAD * 2, listHeight);

        for (int i = 0; i < filteredEntries.size(); i++) {
            int entryY = listTop + i * ServerListEntry.ENTRY_HEIGHT - (int) scrollOffset;
            if (entryY + ServerListEntry.ENTRY_HEIGHT < listTop) continue;
            if (entryY > listBottom) break;

            ServerListEntry entry = filteredEntries.get(i);
            entry.setPosition(x + INNER_PAD, entryY);
            entry.setSize(entryWidth, ServerListEntry.ENTRY_HEIGHT);
            entry.render(g, mouseX, mouseY, delta);
        }

        RenderUtils.disableScissor(g);

        // Scrollbar
        if (hasScrollbar()) {
            int sbX = x + width - SCROLLBAR_WIDTH - INNER_PAD;
            RenderUtils.drawRect(g, sbX, listTop, SCROLLBAR_WIDTH, listHeight, cs.scrollbarTrack());

            int thumbY = getScrollbarThumbY();
            int thumbH = getScrollbarThumbHeight();
            boolean sbHover = mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            int thumbColor = (draggingScrollbar || sbHover) ? cs.scrollbarHover() : cs.scrollbarThumb();
            RenderUtils.drawRect(g, sbX, thumbY, SCROLLBAR_WIDTH, thumbH, thumbColor);
        }

        // Separator above bottom bar
        RenderUtils.drawHLine(g, x + INNER_PAD, listBottom, width - INNER_PAD * 2, cs.separator());

        // Bottom bar
        if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
            viewDetailsButton.render(g, mouseX, mouseY, delta);
            connectButton.render(g, mouseX, mouseY, delta);
            exportButton.render(g, mouseX, mouseY, delta);
            deleteButton.render(g, mouseX, mouseY, delta);
        } else {
            int textY = y + height - BOTTOM_BAR_HEIGHT + (BOTTOM_BAR_HEIGHT - RenderUtils.scaledFontHeight()) / 2;
            RenderUtils.drawText(g, "No server selected", x + INNER_PAD, textY, cs.textSecondary());
        }
    }

    // ── Input ────────────────────────────────────────────────────────

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !containsPoint(mouseX, mouseY)) return false;

        // Forward to search field
        if (searchField.containsPoint(mouseX, mouseY)) {
            return searchField.onMouseClicked(mouseX, mouseY, button);
        }
        searchField.setFocused(false);

        // Sort button
        if (sortButton.containsPoint(mouseX, mouseY)) {
            return sortButton.onMouseClicked(mouseX, mouseY, button);
        }

        // Bottom bar buttons
        if (selectedIndex >= 0) {
            if (viewDetailsButton.containsPoint(mouseX, mouseY))
                return viewDetailsButton.onMouseClicked(mouseX, mouseY, button);
            if (connectButton.containsPoint(mouseX, mouseY))
                return connectButton.onMouseClicked(mouseX, mouseY, button);
            if (exportButton.containsPoint(mouseX, mouseY))
                return exportButton.onMouseClicked(mouseX, mouseY, button);
            if (deleteButton.containsPoint(mouseX, mouseY))
                return deleteButton.onMouseClicked(mouseX, mouseY, button);
        }

        int listTop = y + SEARCH_BAR_HEIGHT + HEADER_GAP;
        int listBottom = y + height - BOTTOM_BAR_HEIGHT;

        // Scrollbar
        if (hasScrollbar() && button == 0) {
            int sbX = x + width - SCROLLBAR_WIDTH - INNER_PAD;
            if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH && mouseY >= listTop && mouseY < listBottom) {
                draggingScrollbar = true;
                scrollbarDragStartY = mouseY;
                scrollOffsetDragStart = scrollOffset;
                targetScrollOffset = scrollOffset; // sync to prevent lerp fight
                return true;
            }
        }

        // Entry click
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            float clickY = (float) (mouseY - listTop + scrollOffset);
            int clickedIndex = (int) (clickY / ServerListEntry.ENTRY_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < filteredEntries.size()) {
                if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
                    filteredEntries.get(selectedIndex).setSelected(false);
                }
                selectedIndex = clickedIndex;
                filteredEntries.get(selectedIndex).setSelected(true);
                if (onServerSelected != null) {
                    onServerSelected.accept(filteredEntries.get(selectedIndex).getServerAddress());
                }
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        // Forward releases to buttons
        if (sortButton.onMouseReleased(mouseX, mouseY, button)) return true;
        if (viewDetailsButton.onMouseReleased(mouseX, mouseY, button)) return true;
        if (connectButton.onMouseReleased(mouseX, mouseY, button)) return true;
        if (exportButton.onMouseReleased(mouseX, mouseY, button)) return true;
        if (deleteButton.onMouseReleased(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScrollbar) {
            int listHeight = getListAreaHeight();
            int thumbH = getScrollbarThumbHeight();
            int maxThumbTravel = listHeight - thumbH;
            if (maxThumbTravel <= 0) return true;

            double mouseDelta = mouseY - scrollbarDragStartY;
            float maxScroll = getMaxScroll();
            float newOffset = scrollOffsetDragStart + (float) (mouseDelta / maxThumbTravel) * maxScroll;
            newOffset = Math.max(0, Math.min(newOffset, maxScroll));
            // Set both so lerp doesn't fight the drag
            scrollOffset = newOffset;
            targetScrollOffset = newOffset;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (!visible || !containsPoint(mouseX, mouseY)) return false;
        targetScrollOffset -= (float) vAmount * ServerListEntry.ENTRY_HEIGHT * 2;
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, getMaxScroll()));
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.isFocused()) {
            return searchField.onKeyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (searchField.isFocused()) {
            return searchField.onCharTyped(chr, modifiers);
        }
        return false;
    }

    // ── Scroll Helpers ───────────────────────────────────────────────

    private int getListAreaHeight() {
        return height - SEARCH_BAR_HEIGHT - HEADER_GAP - BOTTOM_BAR_HEIGHT;
    }

    private float getTotalContentHeight() {
        return filteredEntries.size() * ServerListEntry.ENTRY_HEIGHT;
    }

    private float getMaxScroll() {
        return Math.max(0, getTotalContentHeight() - getListAreaHeight());
    }

    private boolean hasScrollbar() {
        return getTotalContentHeight() > getListAreaHeight();
    }

    private int getScrollbarThumbHeight() {
        int listHeight = getListAreaHeight();
        float ratio = listHeight / getTotalContentHeight();
        return Math.max(10, (int) (listHeight * ratio));
    }

    private int getScrollbarThumbY() {
        int listTop = y + SEARCH_BAR_HEIGHT + HEADER_GAP;
        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return listTop;
        float ratio = scrollOffset / maxScroll;
        int maxThumbTravel = getListAreaHeight() - getScrollbarThumbHeight();
        return listTop + (int) (ratio * maxThumbTravel);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }
}
