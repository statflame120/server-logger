package com.serverlogger.gui;

import com.serverlogger.ServerLoggerMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ServerDetailScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────
    private static final int HEADER_H  = 54;  // two-row metadata bar
    private static final int FOOTER_H  = 36;  // action bar (extra padding avoids taskbar)
    private static final int SIDEBAR_W = 170; // fixed-width left column
    private static final int GAP       = 5;   // gap between sidebar and plugin panel
    private static final int PAD       = 5;   // outer edge padding
    private static final int ITEM_H    = 12;  // height of each list / grid row
    private static final int LABEL_H   = 16;  // section heading + underline clearance

    private final Screen       parent;
    private final ServerLogData data;

    // ── Independent scroll offsets ────────────────────────────────────────
    private int pluginScroll  = 0;
    private int sidebarScroll = 0;

    // ── Panel bounds (derived in init, reused for scroll hit-testing) ─────
    private int sbLeft, sbTop, sbRight, sbBottom; // sidebar
    private int plLeft, plTop, plRight, plBottom; // plugin panel
    private int numCols;                           // 3 or 4 grid columns

    // ─────────────────────────────────────────────────────────────────────

    public ServerDetailScreen(Screen parent, ServerLogData data) {
        super(Component.literal("Server Detail"));
        this.parent = parent;
        this.data   = data;
    }

    // ── Initialisation ────────────────────────────────────────────────────

    @Override
    protected void init() {
        int bodyTop    = HEADER_H;
        int bodyBottom = height - FOOTER_H;

        // Sidebar occupies the left strip
        sbLeft   = PAD;
        sbRight  = PAD + SIDEBAR_W;
        sbTop    = bodyTop;
        sbBottom = bodyBottom;

        // Plugin panel occupies the remainder
        plLeft   = sbRight + GAP;
        plRight  = width - PAD;
        plTop    = bodyTop;
        plBottom = bodyBottom;

        // Column count: 4 on wide screens, 3 otherwise
        numCols = (plRight - plLeft) >= 280 ? 4 : 3;

        // ── Footer buttons ─────────────────────────────────────────────────
        // Three buttons centred in the footer: Import | Copy Plugins | Back
        // Total button span = 60 + 5 + 100 + 5 + 60 = 230 px  →  ±115 from cx
        int cx   = width / 2;
        int btnY = height - FOOTER_H + 8;

        addRenderableWidget(Button.builder(
                Component.literal("Import"), btn -> openLogsFolder())
                .bounds(cx - 115, btnY, 60, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Copy Plugins"),
                btn -> Minecraft.getInstance().keyboardHandler
                        .setClipboard(String.join(", ", data.plugins)))
                .bounds(cx - 50, btnY, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Back"), btn -> minecraft.setScreen(parent))
                .bounds(cx + 55, btnY, 60, 20).build());
    }

    // ── Render entry point ────────────────────────────────────────────────
    // Draw order: backgrounds → widgets (super) → content
    // This ensures the footer/header fills are painted before buttons, so
    // buttons are always visible on top of the dark backgrounds.

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderHeader(g);
        renderFooter(g);
        super.render(g, mouseX, mouseY, partialTick); // buttons drawn over backgrounds
        renderBody(g);
    }

    // ── Header ────────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);

        // Title
        g.drawCenteredString(font,
                Component.literal(data.getDisplayName())
                        .withStyle(s -> s.withColor(0xFFFF55).withBold(true)),
                width / 2, 5, 0xFFFFFFFF);

        // Three-column metadata layout across two rows
        int c1 = 8, c2 = width / 3, c3 = (2 * width) / 3;

        // Row 1
        drawLabel(g, "IP",       data.ip + ":" + data.port, c1, 18, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(g, "Software", data.software,              c2, 18, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(g, "Version",  data.version,               c3, 18, 0xFFAAAAAA, 0xFFFFFFFF);

        // Row 2
        if (!data.domain.equals("unknown") && !data.domain.equals(data.ip)) {
            drawLabel(g, "Domain", data.domain, c1, 30, 0xFFAAAAAA, 0xFF88AAFF);
        }
        drawLabel(g, "Logged",  data.timestamp,                     c2, 30, 0xFFAAAAAA, 0xFFAAAAAA);
        drawLabel(g, "Plugins", String.valueOf(data.plugins.size()), c3, 30, 0xFFAAAAAA, 0xFF55FF55);
    }

    // ── Footer ────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g) {
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);
    }

    // ── Body = left sidebar + right plugin grid ────────────────────────────

    private void renderBody(GuiGraphics g) {
        // Vertical divider between the two panels
        g.fill(sbRight, sbTop + 4, sbRight + 1, sbBottom - 4, 0xFF334466);

        // Left sidebar – all content scrolls together
        g.enableScissor(sbLeft, sbTop, sbRight, sbBottom);
        renderSidebar(g);
        g.disableScissor();

        // Plugin panel header stays pinned (no scissor)
        renderPluginLabel(g);

        // Plugin grid content scrolls below the pinned header
        int gridTop = plTop + LABEL_H;
        g.enableScissor(plLeft, gridTop, plRight, plBottom);
        renderPluginGrid(g, gridTop);
        g.disableScissor();
    }

    // ── Left sidebar ──────────────────────────────────────────────────────

    private void renderSidebar(GuiGraphics g) {
        int y = sbTop + PAD - sidebarScroll;

        y = renderListSection(g, sbLeft, y,
                "Detected Addresses", data.detectedAddresses,
                0xFF88AAFF, 0x4488AAFF, 0xFFCCCCCC);
        y += 10;

        renderWorldsSection(g, sbLeft, y);
    }

    /** Renders a titled list of strings and returns the Y coordinate after the last item. */
    private int renderListSection(GuiGraphics g, int x, int y,
                                  String title, List<String> items,
                                  int titleColor, int lineColor, int itemColor) {
        String heading = title + " (" + items.size() + ")";
        g.drawString(font, heading, x, y, titleColor);
        g.fill(x, y + 10, x + font.width(heading), y + 11, lineColor);
        y += LABEL_H;

        if (items.isEmpty()) {
            g.drawString(font, "  None", x + 2, y, 0xFF555555);
            y += ITEM_H;
        } else {
            for (String item : items) {
                g.drawString(font, "  " + fitText(item, SIDEBAR_W - 8), x + 2, y, itemColor);
                y += ITEM_H;
            }
        }
        return y;
    }

    private void renderWorldsSection(GuiGraphics g, int x, int y) {
        String heading = "Worlds Visited (" + data.worlds.size() + ")";
        g.drawString(font, heading, x, y, 0xFFFFAA55);
        g.fill(x, y + 10, x + font.width(heading), y + 11, 0x44FFAA55);
        y += LABEL_H;

        if (data.worlds.isEmpty()) {
            g.drawString(font, "  None recorded", x + 2, y, 0xFF555555);
        } else {
            for (ServerLogData.WorldSession ws : data.worlds) {
                g.drawString(font, "  " + fitText(ws.dimension, SIDEBAR_W - 8), x + 2, y, 0xFFCCCCCC);
                y += ITEM_H;
                if (ws.resourcePack != null) {
                    g.drawString(font, "    " + fitText(ws.resourcePack, SIDEBAR_W - 14), x + 2, y, 0xFF888888);
                    y += ITEM_H;
                }
            }
        }
    }

    // ── Right plugin panel ────────────────────────────────────────────────

    /** Pinned section label rendered above the scrollable grid. */
    private void renderPluginLabel(GuiGraphics g) {
        String heading = "Plugins (" + data.plugins.size() + ")";
        g.drawString(font, heading, plLeft + 2, plTop + 3, 0xFF55AAFF);
        g.fill(plLeft + 2, plTop + 13,
                plLeft + 2 + font.width(heading), plTop + 14, 0x4455AAFF);
    }

    /**
     * Renders the multi-column plugin grid.
     * {@code gridTop} is the first Y pixel inside the scissored content area.
     */
    private void renderPluginGrid(GuiGraphics g, int gridTop) {
        if (data.plugins.isEmpty()) {
            g.drawString(font, "No plugins detected", plLeft + 4, gridTop + 4, 0xFF555555);
            return;
        }

        int panelW    = plRight - plLeft;
        int colW      = panelW / numCols;
        int totalRows = (data.plugins.size() + numCols - 1) / numCols;

        // Alternating row-band backgrounds (drawn for all rows; scissor clips the invisible ones)
        for (int row = 0; row < totalRows; row++) {
            if (row % 2 == 0) {
                int rowY = gridTop + row * ITEM_H - pluginScroll;
                g.fill(plLeft, rowY, plRight, rowY + ITEM_H, 0x0CFFFFFF);
            }
        }

        // Plugin text  – truncated to fit each column
        int maxTextPx = colW - font.width("\u25B8 ") - 6;
        for (int i = 0; i < data.plugins.size(); i++) {
            int col = i % numCols;
            int row = i / numCols;
            int ix  = plLeft + col * colW + 3;
            int iy  = gridTop + row * ITEM_H - pluginScroll + 1;
            g.drawString(font, "\u25B8 " + fitText(data.plugins.get(i), maxTextPx), ix, iy, 0xFFDDDDDD);
        }
    }

    // ── Scrolling ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int delta = (int) (verticalAmount * 10);

        if (mouseX >= plLeft && mouseX < plRight && mouseY >= plTop && mouseY < plBottom) {
            pluginScroll = Math.max(0, Math.min(pluginScroll - delta, maxPluginScroll()));
        } else if (mouseX >= sbLeft && mouseX < sbRight && mouseY >= sbTop && mouseY < sbBottom) {
            sidebarScroll = Math.max(0, Math.min(sidebarScroll - delta, maxSidebarScroll()));
        }
        return true;
    }

    private int maxPluginScroll() {
        int totalRows = (data.plugins.size() + numCols - 1) / numCols;
        int contentH  = totalRows * ITEM_H;
        int viewH     = (plBottom - plTop) - LABEL_H;
        return Math.max(0, contentH - viewH);
    }

    private int maxSidebarScroll() {
        int h = PAD;
        h += LABEL_H + Math.max(1, data.detectedAddresses.size()) * ITEM_H + 10;
        int worldLines = 0;
        for (ServerLogData.WorldSession ws : data.worlds) {
            worldLines++;
            if (ws.resourcePack != null) worldLines++;
        }
        h += LABEL_H + Math.max(1, worldLines) * ITEM_H;
        return Math.max(0, h - (sbBottom - sbTop));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void drawLabel(GuiGraphics g, String label, String value,
                           int x, int y, int lc, int vc) {
        g.drawString(font, label + ": ", x, y, lc);
        g.drawString(font, value, x + font.width(label + ": "), y, vc);
    }

    /** Truncates {@code s} to fit within {@code maxPx} pixels, appending '\u2026' when cut. */
    private String fitText(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        String ellipsis = "\u2026";
        while (s.length() > 0 && font.width(s + ellipsis) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? ellipsis : s + ellipsis;
    }

    /** Opens the server-logs directory in the OS file manager. */
    private void openLogsFolder() {
        try {
            Path logDir = FabricLoader.getInstance().getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);
            java.awt.Desktop.getDesktop().open(logDir.toFile());
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not open logs folder: {}", e.getMessage());
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
