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

    private static final int HEADER_H  = 54;  // two-row metadata bar
    private static final int FOOTER_H  = 36;  // action bar (extra padding avoids taskbar)
    private static final int SIDEBAR_W = 170; // fixed-width left column
    private static final int GAP       = 5;   // gap between sidebar and plugin panel
    private static final int PAD       = 5;   // outer edge padding
    private static final int ITEM_H    = 12;  // height of each list / grid row
    private static final int LABEL_H   = 16;  // section heading + underline clearance

    private final Screen       parent;
    private final ServerLogData data;

    private java.util.Set<String> glossaryPluginNames = java.util.Collections.emptySet();

    private int pluginScroll  = 0;
    private int sidebarScroll = 0;

    private int sbLeft, sbTop, sbRight, sbBottom; // sidebar
    private int plLeft, plTop, plRight, plBottom; // plugin panel
    private int numCols;                           // 3 or 4 grid columns

    public ServerDetailScreen(Screen parent, ServerLogData data) {
        super(Component.literal("Server Detail"));
        this.parent = parent;
        this.data   = data;
    }

    @Override
    protected void init() {
        // Build set of canonical plugin names that the glossary resolves to
        if (com.serverlogger.ServerLoggerMod.INSTANCE != null) {
            glossaryPluginNames = new java.util.HashSet<>(
                    com.serverlogger.ServerLoggerMod.INSTANCE.pluginGlossary.getEntries().values());
        }

        int bodyTop    = HEADER_H;
        int bodyBottom = height - FOOTER_H;

        sbLeft   = PAD;
        sbRight  = PAD + SIDEBAR_W;
        sbTop    = bodyTop;
        sbBottom = bodyBottom;

        plLeft   = sbRight + GAP;
        plRight  = width - PAD;
        plTop    = bodyTop;
        plBottom = bodyBottom;

        numCols = (plRight - plLeft) >= 280 ? 4 : 3;

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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderHeader(g);
        renderFooter(g);
        super.render(g, mouseX, mouseY, partialTick); // buttons drawn over backgrounds
        renderBody(g);
    }
    private void renderHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);

        g.drawCenteredString(font,
                Component.literal(data.getDisplayName())
                        .withStyle(s -> s.withColor(0xFFFF55).withBold(true)),
                width / 2, 5, 0xFFFFFFFF);

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

    //footer
    private void renderFooter(GuiGraphics g) {
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);
    }

    //left sidebar + right plugin grid
    private void renderBody(GuiGraphics g) {
        // Vertical divider
        g.fill(sbRight, sbTop + 4, sbRight + 1, sbBottom - 4, 0xFF334466);

        // Left sidebar
        g.enableScissor(sbLeft, sbTop, sbRight, sbBottom);
        renderSidebar(g);
        g.disableScissor();
        renderPluginLabel(g);

        int gridTop = plTop + LABEL_H;
        g.enableScissor(plLeft, gridTop, plRight, plBottom);
        renderPluginGrid(g, gridTop);
        g.disableScissor();
    }

    //left sidebar

    private void renderSidebar(GuiGraphics g) {
        int y = sbTop + PAD - sidebarScroll;

        y = renderListSection(g, sbLeft, y,
                "Detected Addresses", data.detectedAddresses,
                0xFF88AAFF, 0x4488AAFF, 0xFFCCCCCC);
        y += 10;

        renderWorldsSection(g, sbLeft, y);
    }

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

    //right plugin panel
    private void renderPluginLabel(GuiGraphics g) {
        String heading = "Plugins (" + data.plugins.size() + ")";
        g.drawString(font, heading, plLeft + 2, plTop + 3, 0xFF55AAFF);
        g.fill(plLeft + 2, plTop + 13,
                plLeft + 2 + font.width(heading), plTop + 14, 0x4455AAFF);
    }

    private void renderPluginGrid(GuiGraphics g, int gridTop) {
        if (data.plugins.isEmpty()) {
            g.drawString(font, "No plugins detected", plLeft + 4, gridTop + 4, 0xFF555555);
            return;
        }

        int panelW    = plRight - plLeft;
        int colW      = panelW / numCols;
        int totalRows = (data.plugins.size() + numCols - 1) / numCols;

        for (int row = 0; row < totalRows; row++) {
            if (row % 2 == 0) {
                int rowY = gridTop + row * ITEM_H - pluginScroll;
                g.fill(plLeft, rowY, plRight, rowY + ITEM_H, 0x0CFFFFFF);
            }
        }

        int maxTextPx = colW - font.width("\u25B8 ") - 6;
        for (int i = 0; i < data.plugins.size(); i++) {
            int col = i % numCols;
            int row = i / numCols;
            int ix  = plLeft + col * colW + 3;
            int iy  = gridTop + row * ITEM_H - pluginScroll + 1;
            String pluginName = data.plugins.get(i);
            // Blue for glossary-identified plugins, light grey for everything else
            int color = glossaryPluginNames.contains(pluginName) ? 0xFF5599FF : 0xFFDDDDDD;
            g.drawString(font, "\u25B8 " + fitText(pluginName, maxTextPx), ix, iy, color);
        }
    }

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

    private void drawLabel(GuiGraphics g, String label, String value,
                           int x, int y, int lc, int vc) {
        g.drawString(font, label + ": ", x, y, lc);
        g.drawString(font, value, x + font.width(label + ": "), y, vc);
    }

    private String fitText(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        String ellipsis = "\u2026";
        while (s.length() > 0 && font.width(s + ellipsis) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? ellipsis : s + ellipsis;
    }

    // Opens the server-logs directory in the OS file manager.
    private void openLogsFolder() {
        try {
            Path logDir = FabricLoader.getInstance().getGameDir()
                    .resolve(ServerLoggerMod.INSTANCE.config.logFolder);
            Files.createDirectories(logDir);
            String path = logDir.toAbsolutePath().toString();
            String os   = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("explorer.exe", path)
                              : os.contains("mac") ? new ProcessBuilder("open", path)
                              :                      new ProcessBuilder("xdg-open", path);
            pb.start();
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not open logs folder: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Could not open logs folder: " + e.getMessage());
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
