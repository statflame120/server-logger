package com.archivist.gui.render;

import com.google.gson.JsonObject;

/**
 * Abstract base for all GUI color themes. Every widget reads colors
 * from ColorScheme.get() — no color literals anywhere else.
 *
 * Colors are stored as 0xAARRGGBB ints (Minecraft convention).
 * Themes are swappable at runtime via setActive().
 */
public abstract class ColorScheme {

    private static ColorScheme active;

    public static ColorScheme get() {
        if (active == null) {
            // Default to Ocean via JsonTheme — fallback values in JsonTheme match Ocean
            JsonObject obj = new JsonObject();
            obj.addProperty("name", "Ocean");
            active = new JsonTheme(obj);
        }
        return active;
    }

    public static void setActive(ColorScheme theme) {
        active = theme;
    }

    public abstract String name();

    // ── Window ──────────────────────────────────────────────────────────────
    public abstract int titleBar();
    public abstract int titleText();
    public abstract int closeButton();
    public abstract int minimizeButton();
    public abstract int windowBackground();
    public abstract int windowBorder();
    public abstract int windowBorderActive();

    // ── Taskbar ─────────────────────────────────────────────────────────────
    public abstract int taskbar();
    public abstract int taskbarButton();
    public abstract int taskbarButtonActive();
    public abstract int taskbarText();

    // ── Buttons ─────────────────────────────────────────────────────────────
    public abstract int button();
    public abstract int buttonHover();
    public abstract int buttonPressed();
    public abstract int buttonText();

    // ── Text Fields ─────────────────────────────────────────────────────────
    public abstract int textFieldBg();
    public abstract int textFieldBorder();
    public abstract int textFieldFocused();
    public abstract int textFieldText();
    public abstract int cursor();
    public abstract int placeholder();

    // ── Tabs ────────────────────────────────────────────────────────────────
    public abstract int tab();
    public abstract int tabActive();
    public abstract int tabText();
    public abstract int tabTextActive();

    // ── Lists ───────────────────────────────────────────────────────────────
    public abstract int listHover();
    public abstract int listSelected();
    public abstract int listText();
    public abstract int listTextSelected();

    // ── Scrollbar ───────────────────────────────────────────────────────────
    public abstract int scrollbarTrack();
    public abstract int scrollbarThumb();
    public abstract int scrollbarHover();

    // ── General ─────────────────────────────────────────────────────────────
    public abstract int accent();
    public abstract int accentDim();
    public abstract int textPrimary();
    public abstract int textSecondary();
    public abstract int separator();
    public abstract int screenOverlay();

    // ── Tooltips ──────────────────────────────────────────────────────────────
    public abstract int tooltipBg();
    public abstract int tooltipBorder();

    // ── Console Event Colors ────────────────────────────────────────────────
    public abstract int eventConnect();
    public abstract int eventDisconnect();
    public abstract int eventBrand();
    public abstract int eventPlugin();
    public abstract int eventWorld();
    public abstract int eventGamemode();
    public abstract int eventPacket();
    public abstract int eventSystem();
    public abstract int eventError();
    public abstract int eventDbSync();

    // ── Color Utilities ─────────────────────────────────────────────────────

    /** Pack r, g, b, a (0–255) into 0xAARRGGBB. */
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Return the color with a replaced alpha (0–255). */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Linear interpolation between two 0xAARRGGBB colors. t in [0,1]. */
    public static int lerpColor(int from, int to, float t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        int aF = (from >>> 24) & 0xFF, rF = (from >>> 16) & 0xFF, gF = (from >>> 8) & 0xFF, bF = from & 0xFF;
        int aT = (to >>> 24) & 0xFF, rT = (to >>> 16) & 0xFF, gT = (to >>> 8) & 0xFF, bT = to & 0xFF;
        return rgba(
                (int) (rF + (rT - rF) * t),
                (int) (gF + (gT - gF) * t),
                (int) (bF + (bT - bF) * t),
                (int) (aF + (aT - aF) * t)
        );
    }

    /** Get the background gradient for full-screen rendering, or null for none. */
    public GradientConfig getBackgroundGradient() {
        return null; // override in themes
    }

    /** Get the color for a LogEvent type. */
    public int eventColor(com.archivist.data.LogEvent.Type type) {
        return switch (type) {
            case CONNECT -> eventConnect();
            case DISCONNECT -> eventDisconnect();
            case BRAND -> eventBrand();
            case PLUGIN -> eventPlugin();
            case WORLD -> eventWorld();
            case GAMEMODE -> eventGamemode();
            case PACKET -> eventPacket();
            case SYSTEM -> eventSystem();
            case ERROR -> eventError();
            case DB_SYNC -> eventDbSync();
        };
    }
}
