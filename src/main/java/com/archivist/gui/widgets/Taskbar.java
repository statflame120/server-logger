package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Bottom taskbar showing one button per window.
 * Clicking a button toggles window visibility / brings it to front.
 * Always renders on top of all windows.
 */
public class Taskbar extends Widget {

    public static final int TASKBAR_HEIGHT = 18;
    private static final int BUTTON_PADDING = 4;
    private static final int BUTTON_SPACING = 2;

    private List<DraggableWindow> windows;
    private int screenWidth;
    private DraggableWindow activeWindow = null;

    /** Called by ArchivistScreen to provide the window list and screen width. */
    public void setup(List<DraggableWindow> windows, int screenWidth) {
        this.windows = windows;
        this.screenWidth = screenWidth;
        this.width = screenWidth;
        this.height = TASKBAR_HEIGHT;
    }

    public Taskbar() {
        super(0, 0, 0, TASKBAR_HEIGHT);
    }

    /** Update position to bottom of screen. Call each frame. */
    public void updatePosition(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.x = 0;
        this.y = screenHeight - TASKBAR_HEIGHT;
        this.width = screenWidth;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible || windows == null) return;
        ColorScheme cs = ColorScheme.get();

        // Taskbar background
        RenderUtils.drawRect(g, x, y, width, height, cs.taskbar());

        // Top border
        RenderUtils.drawHLine(g, x, y, width, cs.separator());

        // Render buttons
        int bx = x + BUTTON_PADDING;
        int by = y + 3;
        int btnH = TASKBAR_HEIGHT - 6;

        for (DraggableWindow window : windows) {
            String title = window.getTitle();
            int btnW = RenderUtils.scaledTextWidth(title) + 12;
            if (btnW < 40) btnW = 40;

            boolean isOpen = window.isVisible();
            boolean isActive = (window == activeWindow && isOpen);
            boolean hover = mouseX >= bx && mouseX < bx + btnW
                    && mouseY >= by && mouseY < by + btnH;

            int bg;
            if (isActive) {
                bg = cs.taskbarButtonActive();
            } else if (hover) {
                bg = cs.buttonHover();
            } else {
                bg = cs.taskbarButton();
            }

            RenderUtils.drawRect(g, bx, by, btnW, btnH, bg);

            // Active indicator (thin line at bottom)
            if (isOpen) {
                RenderUtils.drawHLine(g, bx, by + btnH - 1, btnW, cs.accent());
            }

            // Text
            int textColor = isOpen ? cs.taskbarText() : cs.textSecondary();
            int textX = bx + (btnW - RenderUtils.scaledTextWidth(title)) / 2;
            int textY = by + (btnH - RenderUtils.scaledFontHeight()) / 2;
            RenderUtils.drawText(g, title, textX, textY, textColor);

            bx += btnW + BUTTON_SPACING;
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || windows == null || button != 0) return false;
        if (!containsPoint(mouseX, mouseY)) return false;

        int bx = x + BUTTON_PADDING;
        int by = y + 3;
        int btnH = TASKBAR_HEIGHT - 6;

        for (DraggableWindow window : windows) {
            String title = window.getTitle();
            int btnW = RenderUtils.scaledTextWidth(title) + 12;
            if (btnW < 40) btnW = 40;

            if (mouseX >= bx && mouseX < bx + btnW
                    && mouseY >= by && mouseY < by + btnH) {
                if (window == activeWindow && window.isVisible()) {
                    // Clicking the already-active window — hide it
                    window.setVisible(false);
                    activeWindow = null;
                } else {
                    // Clicking a different or hidden window — show and focus it
                    window.setVisible(true);
                    window.setMinimized(false);
                    activeWindow = window;
                }
                return true;
            }

            bx += btnW + BUTTON_SPACING;
        }
        return true; // consume click on taskbar area
    }

    public void setActiveWindow(DraggableWindow window) {
        this.activeWindow = window;
    }

    public DraggableWindow getActiveWindow() {
        return activeWindow;
    }

    /** Returns the center {x, y} of the taskbar button for the given window, or null if not found. */
    public int[] getButtonCenter(DraggableWindow target) {
        if (windows == null) return null;
        int bx = x + BUTTON_PADDING;
        int by = y + 3;
        int btnH = TASKBAR_HEIGHT - 6;

        for (DraggableWindow window : windows) {
            String title = window.getTitle();
            int btnW = RenderUtils.scaledTextWidth(title) + 12;
            if (btnW < 40) btnW = 40;

            if (window == target) {
                return new int[]{ bx + btnW / 2, by + btnH / 2 };
            }
            bx += btnW + BUTTON_SPACING;
        }
        return null;
    }

    @Override
    public boolean containsPoint(double px, double py) {
        return visible && px >= x && px < x + width && py >= y && py < y + height;
    }
}
