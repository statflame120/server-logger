package com.archivist.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Singleton overlay layer that sits above the entire widget tree.
 *
 * Handles ONE active popup at a time (dropdown menu, context menu, etc.)
 * - Receives input BEFORE the widget tree (clicks on popup items
 *   are consumed and never reach widgets underneath)
 * - Renders AFTER the widget tree (popups are always visually on top)
 * - Clicking outside the popup closes it and consumes the click
 */
public class PopupLayer {

    private static Widget activePopup = null;
    private static Runnable onCloseCallback = null;
    private static AnchorProvider anchorProvider = null;

    @FunctionalInterface
    public interface AnchorProvider {
        int[] getAnchor(); // returns {x, y}
    }

    // ── Lifecycle ──

    public static void open(Widget popup, AnchorProvider anchor, Runnable onClose) {
        close();
        activePopup = popup;
        anchorProvider = anchor;
        onCloseCallback = onClose;
    }

    public static void close() {
        if (activePopup != null) {
            Runnable cb = onCloseCallback;
            activePopup = null;
            anchorProvider = null;
            onCloseCallback = null;
            if (cb != null) cb.run();
        }
    }

    public static boolean isOpen() {
        return activePopup != null;
    }

    // ── Input (call BEFORE root widget tree) ──

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activePopup == null) return false;

        if (activePopup.containsPoint(mouseX, mouseY)) {
            return activePopup.onMouseClicked(mouseX, mouseY, button);
        }

        // Click outside popup — close and consume
        close();
        return true;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activePopup == null) return false;
        return activePopup.onMouseReleased(mouseX, mouseY, button);
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button,
                                       double deltaX, double deltaY) {
        if (activePopup == null) return false;
        if (activePopup.containsPoint(mouseX, mouseY)) {
            return activePopup.onMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY,
                                        double hAmount, double vAmount) {
        if (activePopup == null) return false;
        if (activePopup.containsPoint(mouseX, mouseY)) {
            return activePopup.onMouseScrolled(mouseX, mouseY, hAmount, vAmount);
        }
        close();
        return true;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activePopup == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return activePopup.onKeyPressed(keyCode, scanCode, modifiers);
    }

    public static boolean charTyped(char chr, int modifiers) {
        if (activePopup == null) return false;
        return activePopup.onCharTyped(chr, modifiers);
    }

    // ── Rendering (call AFTER widget tree render, BEFORE tooltips) ──

    public static void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (activePopup == null) return;

        if (anchorProvider != null) {
            int[] anchor = anchorProvider.getAnchor();
            int popupX = anchor[0];
            int popupY = anchor[1];

            //? if >=1.21.6 {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            //?} else {
            /*int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            *///?}

            // Flip above anchor if it would extend below screen
            if (popupY + activePopup.getHeight() > screenH) {
                popupY = popupY - activePopup.getHeight();
            }
            if (popupX + activePopup.getWidth() > screenW) {
                popupX = screenW - activePopup.getWidth();
            }
            if (popupX < 0) popupX = 0;
            if (popupY < 0) popupY = 0;

            activePopup.setPosition(popupX, popupY);
        }

        activePopup.render(g, mouseX, mouseY, delta);
    }
}
