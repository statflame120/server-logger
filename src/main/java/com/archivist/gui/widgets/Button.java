package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Clickable button with hover/press states. Colors from active theme.
 */
public class Button extends Widget {

    private String text;
    private Runnable onClick;
    private boolean pressed = false;
    private boolean enabled = true;

    public Button(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = onClick;
    }

    public Button(int x, int y, int width, String text, Runnable onClick) {
        this(x, y, width, 14, text, onClick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        updateHover(mouseX, mouseY);

        ColorScheme cs = ColorScheme.get();
        int bg;
        if (!enabled) {
            bg = ColorScheme.withAlpha(cs.button(), 100);
        } else if (pressed) {
            bg = cs.buttonPressed();
        } else if (hovered) {
            bg = cs.buttonHover();
        } else {
            bg = cs.button();
        }

        RenderUtils.drawRect(g, x, y, width, height, bg);

        int textColor = enabled ? cs.buttonText() : ColorScheme.withAlpha(cs.buttonText(), 100);
        int textW = RenderUtils.scaledTextWidth(text);
        int textX = x + (width - textW) / 2;
        int textY = y + (height - RenderUtils.scaledFontHeight()) / 2;
        RenderUtils.drawText(g, text, textX, textY, textColor);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) return false;
        if (button == 0 && containsPoint(mouseX, mouseY)) {
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (pressed && button == 0) {
            pressed = false;
            if (containsPoint(mouseX, mouseY) && onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    public void setText(String text) { this.text = text; }
    public String getText() { return text; }
    public void setOnClick(Runnable onClick) { this.onClick = onClick; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
