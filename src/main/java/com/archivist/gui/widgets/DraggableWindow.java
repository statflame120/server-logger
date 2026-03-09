package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draggable, resizable window with title bar, close button, minimize button.
 * Children are laid out vertically in the content area with scrolling.
 *
 * Features:
 * - Draggable by title bar (left click)
 * - Resizable by edges and bottom-right corner
 * - Close button (X) hides the window
 * - Minimize button (-) collapses to title bar only
 * - Scrollable content area with scissor clipping
 * - Window snapping to edges and other windows
 */
public class DraggableWindow extends Widget {

    public static final int TITLE_BAR_HEIGHT = 16;
    private static final int CLOSE_BTN_SIZE = 12;
    private static final int MIN_BTN_SIZE = 12;
    private static final int PADDING = 4;
    private static final int SPACING = 2;

    // Resize constants
    private static final int RESIZE_ZONE = 6;
    private static final int CORNER_ZONE = 12;
    private static final int MIN_WIDTH = 150;
    private static final int MIN_HEIGHT = 80;

    // Snap constants
    private static final int SNAP_DISTANCE = 8;

    private String title;
    private final List<Widget> children = new ArrayList<>();
    private boolean minimized = false;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private float scrollOffset = 0;
    private float maxScroll = 0;
    private boolean isActive = false;
    private boolean closeable = true;
    private String id;
    private Runnable onClose;

    // Resize state
    private boolean resizing = false;
    private boolean resizeLeft, resizeRight, resizeTop, resizeBottom;

    // Sibling windows for snapping
    private List<DraggableWindow> allWindows;

    public DraggableWindow(String id, String title, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.id = id;
        this.title = title;
    }

    /** Set the full window list for snapping. */
    public void setAllWindows(List<DraggableWindow> windows) {
        this.allWindows = windows;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        updateHover(mouseX, mouseY);
        ColorScheme cs = ColorScheme.get();

        // ── Title Bar ───────────────────────────────────────────────────────
        RenderUtils.drawRect(g, x, y, width, TITLE_BAR_HEIGHT, cs.titleBar());

        // Title text
        String displayTitle = RenderUtils.trimToWidth(title, width - CLOSE_BTN_SIZE - MIN_BTN_SIZE - 12);
        RenderUtils.drawText(g, displayTitle, x + 4, y + (TITLE_BAR_HEIGHT - RenderUtils.scaledFontHeight()) / 2, cs.titleText());

        // Close button (X)
        if (closeable) {
            int closeBtnX = x + width - CLOSE_BTN_SIZE - 2;
            int closeBtnY = y + 2;
            boolean closeHover = mouseX >= closeBtnX && mouseX < closeBtnX + CLOSE_BTN_SIZE
                    && mouseY >= closeBtnY && mouseY < closeBtnY + CLOSE_BTN_SIZE;
            int closeColor = closeHover ? 0xFFFF3333 : cs.closeButton();
            RenderUtils.drawText(g, "X", closeBtnX + 2, closeBtnY + 1, closeColor);
        }

        // Minimize button (-)
        int minBtnX = x + width - CLOSE_BTN_SIZE - MIN_BTN_SIZE - 4;
        int minBtnY = y + 2;
        boolean minHover = mouseX >= minBtnX && mouseX < minBtnX + MIN_BTN_SIZE
                && mouseY >= minBtnY && mouseY < minBtnY + MIN_BTN_SIZE;
        int minColor = minHover ? cs.accent() : cs.minimizeButton();
        RenderUtils.drawText(g, minimized ? "+" : "-", minBtnX + 3, minBtnY + 1, minColor);

        if (minimized) {
            // Border around title bar only
            RenderUtils.drawBorder(g, x, y, width, TITLE_BAR_HEIGHT,
                    isActive ? cs.windowBorderActive() : cs.windowBorder());
            return;
        }

        // ── Content Area ────────────────────────────────────────────────────
        int contentY = y + TITLE_BAR_HEIGHT;
        int contentH = height - TITLE_BAR_HEIGHT;

        RenderUtils.drawRect(g, x, contentY, width, contentH, cs.windowBackground());

        // Border
        RenderUtils.drawBorder(g, x, y, width, height,
                isActive ? cs.windowBorderActive() : cs.windowBorder());

        // Compute max scroll (only from non-anchored children)
        int totalContentH = computeContentHeight();
        maxScroll = Math.max(0, totalContentH - contentH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Reflow anchored children every frame (cheap arithmetic)
        reflowChildren();

        // Scissor clip for content area
        RenderUtils.enableScissor(g, x + 1, contentY, width - 2, contentH - 1);

        // Layout and render children
        int cy = contentY + PADDING - (int) scrollOffset;
        int contentWidth = width - PADDING * 2;
        // Account for scrollbar if needed
        if (maxScroll > 0) contentWidth -= 6;

        for (Widget child : children) {
            if (!child.isVisible()) continue;
            if (child.getAnchor() == Widget.Anchor.NONE) {
                // Vertical stack layout (existing behavior)
                child.setPosition(x + PADDING, cy);
                child.setSize(contentWidth, child.getHeight());
                if (cy + child.getHeight() > contentY && cy < contentY + contentH) {
                    child.render(g, mouseX, mouseY, delta);
                }
                cy += child.getHeight() + SPACING;
            } else {
                // Anchored child — positioned by reflow(), render directly
                child.render(g, mouseX, mouseY, delta);
            }
        }

        RenderUtils.disableScissor(g);

        // ── Scrollbar ───────────────────────────────────────────────────────
        if (maxScroll > 0) {
            int scrollTrackX = x + width - 6;
            int scrollTrackY = contentY + 1;
            int scrollTrackH = contentH - 2;

            RenderUtils.drawRect(g, scrollTrackX, scrollTrackY, 5, scrollTrackH, cs.scrollbarTrack());

            float ratio = (float) contentH / totalContentH;
            int thumbH = Math.max(10, (int) (scrollTrackH * ratio));
            int thumbY = scrollTrackY + (int) ((scrollTrackH - thumbH) * (scrollOffset / maxScroll));

            boolean scrollHover = mouseX >= scrollTrackX && mouseX < scrollTrackX + 5
                    && mouseY >= thumbY && mouseY < thumbY + thumbH;
            RenderUtils.drawRect(g, scrollTrackX, thumbY, 5, thumbH,
                    scrollHover ? cs.scrollbarHover() : cs.scrollbarThumb());
        }

        // ── Resize corner indicator ─────────────────────────────────────────
        int cornerX = x + width - 5;
        int cornerY = y + height - 5;
        int cornerColor = ColorScheme.withAlpha(cs.accent(), 128);
        // Small triangle: 3 lines
        g.fill(cornerX + 3, cornerY + 4, cornerX + 5, cornerY + 5, cornerColor);
        g.fill(cornerX + 1, cornerY + 4, cornerX + 5, cornerY + 5, cornerColor);
        g.fill(cornerX + 3, cornerY + 2, cornerX + 5, cornerY + 3, cornerColor);
    }

    private int computeContentHeight() {
        int h = PADDING;
        for (Widget child : children) {
            if (!child.isVisible()) continue;
            if (child.getAnchor() != Widget.Anchor.NONE) continue; // anchored children don't scroll
            h += child.getHeight() + SPACING;
        }
        return h > PADDING ? h - SPACING + PADDING : PADDING * 2;
    }

    @Override
    public void tick() {
        for (Widget child : children) child.tick();
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !containsPoint(mouseX, mouseY)) return false;

        // Close button
        if (closeable && button == 0) {
            int closeBtnX = x + width - CLOSE_BTN_SIZE - 2;
            int closeBtnY = y + 2;
            if (mouseX >= closeBtnX && mouseX < closeBtnX + CLOSE_BTN_SIZE
                    && mouseY >= closeBtnY && mouseY < closeBtnY + CLOSE_BTN_SIZE) {
                visible = false;
                if (onClose != null) onClose.run();
                return true;
            }
        }

        // Minimize button
        if (button == 0) {
            int minBtnX = x + width - CLOSE_BTN_SIZE - MIN_BTN_SIZE - 4;
            int minBtnY = y + 2;
            if (mouseX >= minBtnX && mouseX < minBtnX + MIN_BTN_SIZE
                    && mouseY >= minBtnY && mouseY < minBtnY + MIN_BTN_SIZE) {
                minimized = !minimized;
                return true;
            }
        }

        // ★ CHECK RESIZE ZONES BEFORE DRAG
        if (button == 0 && !minimized && isOnResizeZone(mouseX, mouseY)) {
            resizing = true;
            return true;
        }

        // Only NOW check title bar for drag
        if (button == 0 && mouseY >= y && mouseY < y + TITLE_BAR_HEIGHT) {
            dragging = true;
            dragOffsetX = (int) mouseX - x;
            dragOffsetY = (int) mouseY - y;
            return true;
        }

        // Forward to children
        if (!minimized) {
            unfocusAllTextFields();
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget child = children.get(i);
                if (child.isVisible() && child.onMouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return true; // consume click even if no child handled it (bring to front)
    }

    private boolean isOnResizeZone(double mx, double my) {
        resizeLeft = mx >= x && mx <= x + RESIZE_ZONE
                && my >= y + RESIZE_ZONE && my <= y + height - RESIZE_ZONE;
        resizeRight = mx >= x + width - RESIZE_ZONE && mx <= x + width
                && my >= y + RESIZE_ZONE && my <= y + height - RESIZE_ZONE;
        // Top edge: only the strip ABOVE the title bar content (y to y+RESIZE_ZONE)
        resizeTop = my >= y && my <= y + RESIZE_ZONE
                && mx >= x + RESIZE_ZONE && mx <= x + width - RESIZE_ZONE;
        resizeBottom = my >= y + height - RESIZE_ZONE && my <= y + height
                && mx >= x + RESIZE_ZONE && mx <= x + width - RESIZE_ZONE;

        // Corner override: bottom-right activates both right + bottom
        if (mx >= x + width - CORNER_ZONE && my >= y + height - CORNER_ZONE) {
            resizeRight = true;
            resizeBottom = true;
        }
        return resizeLeft || resizeRight || resizeTop || resizeBottom;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (resizing && button == 0) {
            resizing = false;
            resizeLeft = resizeRight = resizeTop = resizeBottom = false;
            // Reflow children ONCE on release
            reflowChildren();
            return true;
        }
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.onMouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (resizing) {
            if (resizeRight) width = Math.max(MIN_WIDTH, (int) (mouseX - x));
            if (resizeBottom) height = Math.max(MIN_HEIGHT, (int) (mouseY - y));
            if (resizeLeft) {
                int newX = (int) Math.min(mouseX, x + width - MIN_WIDTH);
                width += (x - newX);
                x = newX;
            }
            if (resizeTop) {
                int newY = (int) Math.min(mouseY, y + height - MIN_HEIGHT);
                height += (y - newY);
                y = newY;
            }
            reflowChildren();
            return true;
        }
        if (dragging) {
            int rawX = (int) mouseX - dragOffsetX;
            int rawY = (int) mouseY - dragOffsetY;
            int[] snapped = applySnapping(rawX, rawY);
            x = snapped[0];
            y = snapped[1];
            return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.onMouseDragged(mouseX, mouseY, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    private int[] applySnapping(int newX, int newY) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        List<int[]> xCandidates = new ArrayList<>();
        List<int[]> yCandidates = new ArrayList<>();

        // Screen edges
        xCandidates.add(new int[]{0, Math.abs(newX)});
        xCandidates.add(new int[]{screenW - width, Math.abs(newX + width - screenW)});
        yCandidates.add(new int[]{0, Math.abs(newY)});
        yCandidates.add(new int[]{screenH - Taskbar.TASKBAR_HEIGHT - height,
                Math.abs(newY + height - (screenH - Taskbar.TASKBAR_HEIGHT))});

        // Sibling windows
        if (allWindows != null) {
            for (DraggableWindow other : allWindows) {
                if (other == this || !other.visible) continue;

                // My right → their left
                xCandidates.add(new int[]{other.x - width, Math.abs((newX + width) - other.x)});
                // My left → their right
                xCandidates.add(new int[]{other.x + other.width, Math.abs(newX - (other.x + other.width))});
                // Align lefts
                xCandidates.add(new int[]{other.x, Math.abs(newX - other.x)});

                // My bottom → their top
                yCandidates.add(new int[]{other.y - height, Math.abs((newY + height) - other.y)});
                // My top → their bottom
                yCandidates.add(new int[]{other.y + other.height, Math.abs(newY - (other.y + other.height))});
                // Align tops
                yCandidates.add(new int[]{other.y, Math.abs(newY - other.y)});
            }
        }

        // Pick the closest candidate within threshold for each axis
        int bestSnapX = newX;
        int bestSnapDistX = SNAP_DISTANCE + 1;
        for (int[] candidate : xCandidates) {
            if (candidate[1] < SNAP_DISTANCE && candidate[1] < bestSnapDistX) {
                bestSnapX = candidate[0];
                bestSnapDistX = candidate[1];
            }
        }

        int bestSnapY = newY;
        int bestSnapDistY = SNAP_DISTANCE + 1;
        for (int[] candidate : yCandidates) {
            if (candidate[1] < SNAP_DISTANCE && candidate[1] < bestSnapDistY) {
                bestSnapY = candidate[0];
                bestSnapDistY = candidate[1];
            }
        }

        return new int[]{
                bestSnapDistX <= SNAP_DISTANCE ? bestSnapX : newX,
                bestSnapDistY <= SNAP_DISTANCE ? bestSnapY : newY
        };
    }

    /** Reflow anchored children based on current content bounds. */
    public void reflowChildren() {
        double cx = x + PADDING;
        double cy = y + TITLE_BAR_HEIGHT;
        double cw = width - PADDING * 2;
        double ch = height - TITLE_BAR_HEIGHT - PADDING;

        for (Widget child : children) {
            child.reflow(cx, cy, cw, ch);
        }
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (!visible || minimized || !containsPoint(mouseX, mouseY)) return false;
        // Forward to children first (e.g., dropdowns, sub-scrollers)
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.onMouseScrolled(mouseX, mouseY, hAmount, vAmount)) {
                return true;
            }
        }
        // Scroll content
        if (maxScroll > 0) {
            scrollOffset -= (float) vAmount * 8;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || minimized) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.onKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (!visible || minimized) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.onCharTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsPoint(double px, double py) {
        if (!visible) return false;
        int h = minimized ? TITLE_BAR_HEIGHT : height;
        // Include resize zone around edges
        int rz = RESIZE_ZONE;
        return px >= x - rz && px < x + width + rz && py >= y - rz && py < y + h + rz;
    }

    /** Exact hit test without resize zone expansion (for z-order click testing). */
    public boolean containsPointExact(double px, double py) {
        if (!visible) return false;
        int h = minimized ? TITLE_BAR_HEIGHT : height;
        return px >= x && px < x + width && py >= y && py < y + h;
    }

    private void unfocusAllTextFields() {
        for (Widget child : children) {
            if (child instanceof TextField tf) tf.setFocused(false);
            if (child instanceof Panel p) {
                for (Widget pc : p.getChildren()) {
                    if (pc instanceof TextField tf) tf.setFocused(false);
                }
            }
        }
    }

    // ── Children Management ─────────────────────────────────────────────────

    public void addChild(Widget child) { children.add(child); }
    public void removeChild(Widget child) { children.remove(child); }
    public void clearChildren() { children.clear(); }
    public List<Widget> getChildren() { return Collections.unmodifiableList(children); }

    // ── State ───────────────────────────────────────────────────────────────

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getId() { return id; }
    public boolean isMinimized() { return minimized; }
    public void setMinimized(boolean minimized) { this.minimized = minimized; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }
    public boolean isCloseable() { return closeable; }
    public void setCloseable(boolean closeable) { this.closeable = closeable; }
    public void scrollToBottom() { scrollOffset = maxScroll; }
    public void resetScroll() { scrollOffset = 0; }

    /** Bring this window to front in its parent list. */
    public void bringToFront() {
        if (allWindows != null) {
            allWindows.remove(this);
            allWindows.add(this);
        }
    }
}
