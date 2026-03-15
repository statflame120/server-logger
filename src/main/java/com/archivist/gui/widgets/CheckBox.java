package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Toggle checkbox widget with animated pill-style switch.
 */
public class CheckBox extends Widget {

    private String label;
    private boolean checked;
    private Consumer<Boolean> onChange;
    private float animProgress; // 0 = off, 1 = on

    public CheckBox(int x, int y, int width, String label, boolean checked, Consumer<Boolean> onChange) {
        super(x, y, width, 12);
        this.label = label;
        this.checked = checked;
        this.onChange = onChange;
        this.animProgress = checked ? 1f : 0f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        updateHover(mouseX, mouseY);

        // Animate
        float target = checked ? 1f : 0f;
        animProgress += (target - animProgress) * 0.25f;
        if (Math.abs(target - animProgress) < 0.01f) animProgress = target;

        ColorScheme cs = ColorScheme.get();

        // Toggle pill (16x8 px)
        int pillW = 16;
        int pillH = 8;
        int pillY = y + (height - pillH) / 2;

        int pillColor = ColorScheme.lerpColor(cs.textFieldBorder(), cs.accent(), animProgress);
        RenderUtils.drawRect(g, x, pillY, pillW, pillH, pillColor);

        // Knob (6x6 px, slides left to right)
        int knobSize = 6;
        int knobX = x + 1 + (int) ((pillW - knobSize - 2) * animProgress);
        int knobY = pillY + 1;
        int knobColor = checked ? 0xFFFFFFFF : cs.textSecondary();
        RenderUtils.drawRect(g, knobX, knobY, knobSize, knobSize, knobColor);

        // Label text (wrapping)
        int textX = x + pillW + 4;
        int textMaxW = width - pillW - 4;
        int textColor = hovered ? cs.textPrimary() : cs.textSecondary();
        int textH = RenderUtils.wrappedTextHeight(label, textMaxW);
        int newHeight = Math.max(12, textH + 2);
        if (newHeight != height) height = newHeight;
        int textY = y + (height - textH) / 2;
        RenderUtils.drawWrappedText(g, label, textX, textY, textMaxW, textColor);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (button == 0 && containsPoint(mouseX, mouseY)) {
            checked = !checked;
            if (onChange != null) onChange.accept(checked);
            return true;
        }
        return false;
    }

    public boolean isChecked() { return checked; }

    public void setChecked(boolean checked) {
        this.checked = checked;
        this.animProgress = checked ? 1f : 0f;
    }

    public void setLabel(String label) { this.label = label; }

    @Override
    public String getTextAtPoint(double px, double py) {
        if (!visible || label == null || label.isEmpty()) return null;
        return containsPoint(px, py) ? label : null;
    }
}
