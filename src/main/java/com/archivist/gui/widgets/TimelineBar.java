package com.archivist.gui.widgets;

import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Visual timeline bar showing event types as colored dots on a center line.
 * Each dot represents one event, color-coded by type.
 * Tooltip on hover shows event detail — no text rendered on the bar itself.
 */
public class TimelineBar extends Widget {

    private static final int DOT_SIZE = 5;
    private static final int DOT_SPACING = 12;
    private static final int PAD = 6;

    private float scrollOffset = 0;

    public TimelineBar(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        ColorScheme cs = ColorScheme.get();

        // Solid background to prevent bleed-through
        RenderUtils.drawRect(g, x, y, width, height, cs.textFieldBg());
        RenderUtils.drawBorder(g, x, y, width, height, cs.separator());

        List<LogEvent> events = EventBus.getEvents();
        if (events.isEmpty()) return;

        // Scissor clip — nothing renders outside the bar
        RenderUtils.enableScissor(g, x + 1, y + 1, width - 2, height - 2);

        int count = events.size();
        int contentWidth = PAD * 2 + (count - 1) * DOT_SPACING;
        int usableWidth = width - PAD * 2;
        int lineWidth = Math.max(usableWidth, contentWidth - PAD * 2);

        // Draw center line
        int lineY = y + height / 2;
        RenderUtils.drawHLine(g, x + PAD - (int) scrollOffset, lineY, lineWidth, cs.textSecondary());

        // Draw dots — small filled squares, NO text on the bar
        for (int i = 0; i < count; i++) {
            int dotX = x + PAD + (i * DOT_SPACING) - (int) scrollOffset;

            // Skip dots fully off-screen
            if (dotX + DOT_SIZE / 2 < x || dotX - DOT_SIZE / 2 > x + width) continue;

            LogEvent event = events.get(i);
            int color = cs.eventColor(event.getType());

            int drawX = dotX - DOT_SIZE / 2;
            int drawY = lineY - DOT_SIZE / 2;

            // Filled dot
            g.fill(drawX, drawY, drawX + DOT_SIZE, drawY + DOT_SIZE, color);

            // Semi-transparent black outline for visibility
            g.fill(drawX, drawY, drawX + DOT_SIZE, drawY + 1, 0x80000000);           // top
            g.fill(drawX, drawY + DOT_SIZE - 1, drawX + DOT_SIZE, drawY + DOT_SIZE, 0x80000000); // bottom
            g.fill(drawX, drawY + 1, drawX + 1, drawY + DOT_SIZE - 1, 0x80000000);   // left
            g.fill(drawX + DOT_SIZE - 1, drawY + 1, drawX + DOT_SIZE, drawY + DOT_SIZE - 1, 0x80000000); // right

            // Tooltip on hover — show event text ONLY in tooltip
            if (mouseX >= drawX - 2 && mouseX <= drawX + DOT_SIZE + 2
                    && mouseY >= y && mouseY <= y + height) {
                TooltipManager.offer(event.getType().name() + ": " + event.getMessage(), mouseX, mouseY);
            }
        }

        RenderUtils.disableScissor(g);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (!visible || !containsPoint(mouseX, mouseY)) return false;

        int count = EventBus.getEvents().size();
        int contentWidth = PAD * 2 + (count - 1) * DOT_SPACING;
        int usableWidth = width;
        int maxScroll = Math.max(0, contentWidth - usableWidth);
        if (maxScroll == 0) return false;

        scrollOffset = (float) Math.max(0, Math.min(maxScroll, scrollOffset - vAmount * DOT_SPACING));
        return true;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
