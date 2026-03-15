package com.archivist.gui.widgets;

import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Single-line text input field with cursor, selection, and placeholder.
 * Supports optional password masking.
 */
public class TextField extends Widget {

    private String text = "";
    private String placeholder;
    private boolean masked;
    private Consumer<String> onChange;
    private int cursorPos = 0;
    private int scrollOffset = 0;
    private long cursorBlinkTime = 0;

    // Autocomplete
    private Function<String, List<String>> autoCompleteProvider;
    private Consumer<List<String>> onShowSuggestions;

    public TextField(int x, int y, int width, int height, String placeholder) {
        super(x, y, width, height);
        this.placeholder = placeholder;
        this.masked = false;
    }

    public TextField(int x, int y, int width, String placeholder) {
        this(x, y, width, 14, placeholder);
    }

    public TextField(int x, int y, int width, String placeholder, boolean masked) {
        this(x, y, width, 14, placeholder);
        this.masked = masked;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        updateHover(mouseX, mouseY);
        ColorScheme cs = ColorScheme.get();

        // Background
        RenderUtils.drawRect(g, x, y, width, height, cs.textFieldBg());

        // Border
        int borderColor = focused ? cs.textFieldFocused() : cs.textFieldBorder();
        RenderUtils.drawBorder(g, x, y, width, height, borderColor);

        // Text area with padding
        int pad = 3;
        int textAreaW = width - pad * 2;
        RenderUtils.enableScissor(g, x + pad, y, textAreaW, height);

        String displayText = masked ? "*".repeat(text.length()) : text;

        if (text.isEmpty() && !focused && placeholder != null) {
            RenderUtils.drawText(g, placeholder, x + pad, y + (height - RenderUtils.scaledFontHeight()) / 2, cs.placeholder());
        } else {
            // Ensure cursor is visible by adjusting scroll
            adjustScroll(displayText, textAreaW);

            String scrolledText = displayText.substring(Math.min(scrollOffset, displayText.length()));
            int textY = y + (height - RenderUtils.scaledFontHeight()) / 2;
            RenderUtils.drawText(g, scrolledText, x + pad, textY, cs.textFieldText());

            // Cursor
            if (focused && (System.currentTimeMillis() - cursorBlinkTime) % 1000 < 500) {
                String beforeCursor = displayText.substring(Math.min(scrollOffset, displayText.length()),
                        Math.min(cursorPos, displayText.length()));
                int cursorX = x + pad + RenderUtils.scaledTextWidth(beforeCursor);
                int cursorY1 = y + 2;
                int cursorY2 = y + height - 2;
                g.fill(cursorX, cursorY1, cursorX + 1, cursorY2, cs.cursor());
            }
        }

        RenderUtils.disableScissor(g);
    }

    private void adjustScroll(String displayText, int textAreaW) {
        if (cursorPos < scrollOffset) {
            scrollOffset = cursorPos;
        }
        String visible = displayText.substring(Math.min(scrollOffset, displayText.length()),
                Math.min(cursorPos, displayText.length()));
        while (RenderUtils.scaledTextWidth(visible) > textAreaW - 4 && scrollOffset < cursorPos) {
            scrollOffset++;
            visible = displayText.substring(Math.min(scrollOffset, displayText.length()),
                    Math.min(cursorPos, displayText.length()));
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (containsPoint(mouseX, mouseY)) {
            focused = true;
            cursorBlinkTime = System.currentTimeMillis();
            // Position cursor based on click location
            int pad = 3;
            String displayText = masked ? "*".repeat(text.length()) : text;
            String scrolledText = displayText.substring(Math.min(scrollOffset, displayText.length()));
            int relX = (int) mouseX - x - pad;
            int pos = scrollOffset;
            for (int i = 0; i < scrolledText.length(); i++) {
                int charW = RenderUtils.scaledTextWidth(scrolledText.substring(0, i + 1));
                if (charW > relX) break;
                pos = scrollOffset + i + 1;
            }
            cursorPos = Math.min(pos, text.length());
            return true;
        } else {
            focused = false;
            return false;
        }
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        cursorBlinkTime = System.currentTimeMillis();

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty() && cursorPos > 0) {
                    if (ctrl) {
                        // Delete word
                        int end = cursorPos;
                        while (cursorPos > 0 && text.charAt(cursorPos - 1) == ' ') cursorPos--;
                        while (cursorPos > 0 && text.charAt(cursorPos - 1) != ' ') cursorPos--;
                        text = text.substring(0, cursorPos) + text.substring(end);
                    } else {
                        text = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                        cursorPos--;
                    }
                    notifyChange();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorPos < text.length()) {
                    text = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
                    notifyChange();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPos > 0) {
                    if (ctrl) {
                        while (cursorPos > 0 && text.charAt(cursorPos - 1) == ' ') cursorPos--;
                        while (cursorPos > 0 && text.charAt(cursorPos - 1) != ' ') cursorPos--;
                    } else {
                        cursorPos--;
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPos < text.length()) {
                    if (ctrl) {
                        while (cursorPos < text.length() && text.charAt(cursorPos) != ' ') cursorPos++;
                        while (cursorPos < text.length() && text.charAt(cursorPos) == ' ') cursorPos++;
                    } else {
                        cursorPos++;
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> { cursorPos = 0; return true; }
            case GLFW.GLFW_KEY_END -> { cursorPos = text.length(); return true; }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    cursorPos = text.length();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clipboard = net.minecraft.client.Minecraft.getInstance()
                            .keyboardHandler.getClipboard();
                    if (clipboard != null && !clipboard.isEmpty()) {
                        // Strip newlines for single-line field
                        clipboard = clipboard.replaceAll("[\\r\\n]", " ");
                        text = text.substring(0, cursorPos) + clipboard + text.substring(cursorPos);
                        cursorPos += clipboard.length();
                        notifyChange();
                    }
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && !text.isEmpty()) {
                    net.minecraft.client.Minecraft.getInstance()
                            .keyboardHandler.setClipboard(text);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (autoCompleteProvider != null) {
                    String input = getText();
                    if (input.isEmpty()) return false;
                    List<String> matches = autoCompleteProvider.apply(input.toLowerCase());
                    if (matches == null || matches.isEmpty()) return true;
                    if (matches.size() == 1) {
                        setText(matches.get(0) + " ");
                        cursorPos = text.length();
                    } else {
                        if (onShowSuggestions != null) {
                            onShowSuggestions.accept(matches);
                        }
                        String prefix = findCommonPrefix(matches);
                        if (prefix.length() > input.length()) {
                            setText(prefix);
                            cursorPos = text.length();
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private String findCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) return "";
        String first = strings.get(0);
        int prefixLen = first.length();
        for (int i = 1; i < strings.size(); i++) {
            String s = strings.get(i);
            prefixLen = Math.min(prefixLen, s.length());
            for (int j = 0; j < prefixLen; j++) {
                if (first.charAt(j) != s.charAt(j)) {
                    prefixLen = j;
                    break;
                }
            }
        }
        return first.substring(0, prefixLen);
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (chr < 32) return false; // control characters
        cursorBlinkTime = System.currentTimeMillis();
        text = text.substring(0, cursorPos) + chr + text.substring(cursorPos);
        cursorPos++;
        notifyChange();
        return true;
    }

    private void notifyChange() {
        if (onChange != null) onChange.accept(text);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public String getText() { return text; }

    public void setText(String text) {
        this.text = text != null ? text : "";
        this.cursorPos = Math.min(cursorPos, this.text.length());
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
    }

    public void clear() {
        text = "";
        cursorPos = 0;
        scrollOffset = 0;
        notifyChange();
    }

    public void setAutoCompleteProvider(Function<String, List<String>> provider) {
        this.autoCompleteProvider = provider;
    }

    public void setOnShowSuggestions(Consumer<List<String>> callback) {
        this.onShowSuggestions = callback;
    }

    @Override
    public String getTextAtPoint(double px, double py) {
        if (!visible || text == null || text.isEmpty()) return null;
        return containsPoint(px, py) ? text : null;
    }
}
