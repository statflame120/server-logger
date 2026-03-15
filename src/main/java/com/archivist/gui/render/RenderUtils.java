package com.archivist.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Static drawing helpers used by all widgets.
 * Handles Stonecutter version compatibility for matrix operations.
 */
public final class RenderUtils {

    private RenderUtils() {}

    public static final float TEXT_SCALE = 0.75f;

    // ── Rectangle Drawing ───────────────────────────────────────────────────

    public static void drawRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);           // top
        g.fill(x, y + h - 1, x + w, y + h, color);   // bottom
        g.fill(x, y + 1, x + 1, y + h - 1, color);   // left
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color); // right
    }

    public static void drawHLine(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void drawVLine(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    // ── Text Drawing ────────────────────────────────────────────────────────

    /** Draw text at TEXT_SCALE with shadow. Coordinates are in unscaled screen space. */
    public static void drawText(GuiGraphics g, String text, int x, int y, int color) {
        drawScaledText(g, text, x, y, color, true);
    }

    /** Draw text at TEXT_SCALE without shadow. */
    public static void drawTextNoShadow(GuiGraphics g, String text, int x, int y, int color) {
        drawScaledText(g, text, x, y, color, false);
    }

    /** Draw text at the configured TEXT_SCALE. */
    public static void drawScaledText(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        var font = Minecraft.getInstance().font;
        float s = TEXT_SCALE;
        //? if >=1.21.6 {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popMatrix();
        //?} else {
        /*var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(s, s, 1.0f);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
        *///?}
    }

    /** Draw text at a custom scale. */
    public static void drawTextAtScale(GuiGraphics g, String text, int x, int y, int color, float scale) {
        var font = Minecraft.getInstance().font;
        //? if >=1.21.6 {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.drawString(font, text, 0, 0, color, true);
        pose.popMatrix();
        //?} else {
        /*var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, true);
        pose.popPose();
        *///?}
    }

    /** Effective line height after scaling. */
    public static int scaledFontHeight() {
        return (int) (Minecraft.getInstance().font.lineHeight * TEXT_SCALE);
    }

    /** Effective string width after scaling. */
    public static int scaledTextWidth(String text) {
        return (int) (Minecraft.getInstance().font.width(text) * TEXT_SCALE);
    }

    /** Trim text to fit within maxWidth, adding "..." if truncated. */
    public static String trimToWidth(String text, int maxWidth) {
        if (scaledTextWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisW = scaledTextWidth(ellipsis);
        if (maxWidth <= ellipsisW) return ellipsis;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (scaledTextWidth(sb.toString()) + ellipsisW > maxWidth) {
                sb.deleteCharAt(sb.length() - 1);
                sb.append(ellipsis);
                return sb.toString();
            }
        }
        return text;
    }

    // ── Text Wrapping ────────────────────────────────────────────────────────

    /** Split text into lines that each fit within maxWidth (word-breaking). */
    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            lines.add(text != null ? text : "");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.isEmpty()) {
                // First word on this line — force it even if too wide
                if (scaledTextWidth(word) > maxWidth) {
                    // Character-break long words
                    for (int i = 0; i < word.length(); i++) {
                        String test = currentLine.toString() + word.charAt(i);
                        if (scaledTextWidth(test) > maxWidth && !currentLine.isEmpty()) {
                            lines.add(currentLine.toString());
                            currentLine = new StringBuilder();
                        }
                        currentLine.append(word.charAt(i));
                    }
                } else {
                    currentLine.append(word);
                }
            } else {
                String test = currentLine + " " + word;
                if (scaledTextWidth(test) > maxWidth) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    // Handle word that's wider than maxWidth
                    if (scaledTextWidth(word) > maxWidth) {
                        for (int i = 0; i < word.length(); i++) {
                            String charTest = currentLine.toString() + word.charAt(i);
                            if (scaledTextWidth(charTest) > maxWidth && !currentLine.isEmpty()) {
                                lines.add(currentLine.toString());
                                currentLine = new StringBuilder();
                            }
                            currentLine.append(word.charAt(i));
                        }
                    } else {
                        currentLine.append(word);
                    }
                } else {
                    currentLine.append(" ").append(word);
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        if (lines.isEmpty()) {
            lines.add("");
        }

        return lines;
    }

    /** Draw text with word wrapping. Returns the total height used. */
    public static int drawWrappedText(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        List<String> lines = wrapText(text, maxWidth);
        int lineH = scaledFontHeight();
        for (int i = 0; i < lines.size(); i++) {
            drawText(g, lines.get(i), x, y + i * lineH, color);
        }
        return lines.size() * lineH;
    }

    /** Calculate the total height wrapped text would occupy. */
    public static int wrappedTextHeight(String text, int maxWidth) {
        return wrapText(text, maxWidth).size() * scaledFontHeight();
    }

    // ── Scissor ─────────────────────────────────────────────────────────────

    public static void enableScissor(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public static void disableScissor(GuiGraphics g) {
        g.disableScissor();
    }
}
