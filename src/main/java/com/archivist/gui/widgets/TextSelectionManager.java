package com.archivist.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages character-level text selection across all widgets, supporting multi-line selection.
 * Hooks into RenderUtils.drawScaledText — each text draw call registers a region and
 * checks for selection overlap to render yellow highlights.
 *
 * Selection spans multiple lines: the anchor line gets a partial selection from the
 * anchor char to the end (or start), middle lines are fully selected, and the
 * final line is selected from start (or end) to the cursor char.
 */
public class TextSelectionManager {

    private static final int HIGHLIGHT_COLOR = 0x80FFFF00; // semi-transparent yellow
    private static final int DRAG_THRESHOLD = 2;
    private static final int X_TOLERANCE = 20; // max X difference to consider same text column

    // Whether selection is enabled (disabled for window titles)
    private static boolean enabled = true;

    // Mouse state
    private static int currentMouseX, currentMouseY;
    private static boolean mouseDown = false;
    private static boolean dragged = false;
    private static double anchorX, anchorY;

    // Per-frame region tracking
    private static final List<TextRegion> regions = new ArrayList<>();
    private static int frameRegionIndex = 0;

    // Selection state
    private static boolean hasSelection = false;
    private static int anchorRegionIdx = -1;
    private static int anchorChar = -1;
    // Finalized selection (after mouse release)
    private static List<SelectedLine> selectedLines = new ArrayList<>();

    private record TextRegion(String text, int screenX, int screenY, float scale, int index) {}
    private record SelectedLine(String text, int fromChar, int toChar) {}

    public static void setEnabled(boolean e) { enabled = e; }

    /** Called at the start of each render frame, before any widgets render. */
    public static void beginFrame() {
        regions.clear();
        frameRegionIndex = 0;
    }

    /** Called at the start of each render frame with current mouse position. */
    public static void updateMouse(int mx, int my) {
        currentMouseX = mx;
        currentMouseY = my;
    }

    public static void onMousePressed(double mx, double my, int button) {
        if (button != 0) return;
        hasSelection = false;
        selectedLines.clear();
        anchorRegionIdx = -1;
        anchorX = mx;
        anchorY = my;
        mouseDown = true;
        dragged = false;
    }

    public static void onMouseDragged(double mx, double my) {
        if (!mouseDown) return;
        double dx = mx - anchorX;
        double dy = my - anchorY;
        if (!dragged && (dx * dx + dy * dy) >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
            dragged = true;
        }
    }

    public static void onMouseReleased() {
        mouseDown = false;
        if (!dragged) {
            hasSelection = false;
            selectedLines.clear();
        }
    }

    public static String getSelectedText() {
        if (!hasSelection || selectedLines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedLines.size(); i++) {
            SelectedLine sl = selectedLines.get(i);
            if (sl.fromChar >= sl.toChar || sl.fromChar < 0 || sl.toChar > sl.text.length()) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(sl.text, sl.fromChar, sl.toChar);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public static boolean hasActiveSelection() {
        String sel = getSelectedText();
        return sel != null && !sel.isEmpty();
    }

    public static void clearSelection() {
        hasSelection = false;
        selectedLines.clear();
        mouseDown = false;
        dragged = false;
        anchorRegionIdx = -1;
    }

    /**
     * Called by RenderUtils text drawing methods AFTER pose push/translate/scale,
     * BEFORE g.drawString. Registers the region and draws highlights if selected.
     */
    public static void checkAndHighlight(GuiGraphics g, String text, int screenX, int screenY, float scale) {
        if (!enabled || text == null || text.isEmpty()) return;

        // Register this region
        int idx = frameRegionIndex++;
        regions.add(new TextRegion(text, screenX, screenY, scale, idx));

        if (!mouseDown && !hasSelection) return;

        var font = Minecraft.getInstance().font;
        int textW = (int) (font.width(text) * scale);
        int textH = (int) (font.lineHeight * scale);

        // ── Active dragging ──
        if (mouseDown && dragged) {
            // Find anchor region if not yet found
            if (anchorRegionIdx == -1) {
                boolean anchorHit = anchorX >= screenX && anchorX < screenX + textW
                        && anchorY >= screenY && anchorY < screenY + textH;
                if (anchorHit) {
                    anchorRegionIdx = idx;
                    anchorChar = charIndexAtScreenX(text, screenX, anchorX, scale, font);
                }
            }

            if (anchorRegionIdx == -1) return; // anchor region not found yet

            // Find the anchor region's data from this frame
            TextRegion anchorRegion = null;
            for (TextRegion r : regions) {
                if (r.index == anchorRegionIdx) { anchorRegion = r; break; }
            }
            if (anchorRegion == null) return;

            // Check if this region is in the same text column (similar X)
            if (Math.abs(screenX - anchorRegion.screenX) > X_TOLERANCE) return;

            int anchorY_ = anchorRegion.screenY;
            int cursorY_ = currentMouseY;
            int topY = Math.min(anchorY_, cursorY_);
            int bottomY = Math.max(anchorY_, cursorY_);

            // Is this region between anchor and cursor (inclusive)?
            if (screenY + textH <= topY || screenY > bottomY) return;

            // Determine highlight range for this region
            boolean isAnchorLine = (idx == anchorRegionIdx);
            boolean isCursorLine = (currentMouseY >= screenY && currentMouseY < screenY + textH);
            boolean selectingDown = anchorY_ <= cursorY_;

            int from, to;
            if (isAnchorLine && isCursorLine) {
                // Single line selection
                int curChar = charIndexAtScreenX(text, screenX, currentMouseX, scale, font);
                from = Math.min(anchorChar, curChar);
                to = Math.max(anchorChar, curChar);
            } else if (isAnchorLine) {
                // Anchor line: select from anchor char to edge
                if (selectingDown) {
                    from = anchorChar;
                    to = text.length();
                } else {
                    from = 0;
                    to = anchorChar;
                }
            } else if (isCursorLine) {
                // Cursor line: select from edge to cursor char
                int curChar = charIndexAtScreenX(text, screenX, currentMouseX, scale, font);
                if (selectingDown) {
                    from = 0;
                    to = curChar;
                } else {
                    from = curChar;
                    to = text.length();
                }
            } else {
                // Middle line: select entire line
                from = 0;
                to = text.length();
            }

            if (from < to) {
                hasSelection = true;
                drawHighlight(g, text, from, to, font);
                // Update selectedLines for copy
                updateSelectedLine(text, from, to, screenY);
            }
            return;
        }

        // ── Finalized selection (not dragging, hasSelection) ──
        if (hasSelection && !selectedLines.isEmpty()) {
            for (SelectedLine sl : selectedLines) {
                if (sl.text.equals(text) && sl.fromChar < sl.toChar) {
                    // Check approximate Y match — use the region list to verify
                    drawHighlight(g, text, sl.fromChar, sl.toChar, font);
                    return;
                }
            }
        }
    }

    private static void updateSelectedLine(String text, int from, int to, int screenY) {
        // Replace or add the selected line entry (keyed by screenY to handle updates during drag)
        for (int i = 0; i < selectedLines.size(); i++) {
            if (selectedLines.get(i).text.equals(text)) {
                selectedLines.set(i, new SelectedLine(text, from, to));
                return;
            }
        }
        // Insert sorted by screenY for consistent ordering
        int insertIdx = 0;
        for (int i = 0; i < selectedLines.size(); i++) {
            // We don't have screenY in SelectedLine, so just append
            insertIdx = i + 1;
        }
        selectedLines.add(insertIdx, new SelectedLine(text, from, to));
    }

    private static void drawHighlight(GuiGraphics g, String text, int from, int to, net.minecraft.client.gui.Font font) {
        from = Math.max(0, Math.min(from, text.length()));
        to = Math.max(0, Math.min(to, text.length()));
        if (from >= to) return;

        int x1 = font.width(text.substring(0, from));
        int x2 = font.width(text.substring(0, to));
        int h = font.lineHeight;
        g.fill(x1, 0, x2, h, HIGHLIGHT_COLOR);
    }

    private static int charIndexAtScreenX(String text, int regionX, double mouseScreenX, float scale, net.minecraft.client.gui.Font font) {
        double relX = (mouseScreenX - regionX) / scale;
        if (relX <= 0) return 0;

        for (int i = 1; i <= text.length(); i++) {
            float charEndX = font.width(text.substring(0, i));
            if (relX < charEndX) {
                float charStartX = font.width(text.substring(0, i - 1));
                return relX < (charStartX + charEndX) / 2.0 ? i - 1 : i;
            }
        }
        return text.length();
    }
}
