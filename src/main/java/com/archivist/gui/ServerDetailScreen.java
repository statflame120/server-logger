package com.archivist.gui;

import com.archivist.ArchivistMod;
import com.archivist.PluginCategorizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ServerDetailScreen extends Screen {

    private static final int HEADER_H  = 56;
    private static final int FOOTER_H  = 36;
    private static final int SIDEBAR_W = 170;
    private static final int GAP       = 5;
    private static final int PAD       = 5;
    private static final int ITEM_H    = 12;
    private static final int LABEL_H   = 16;

    private final Screen       parent;
    private final ServerLogData data;

    private java.util.Set<String> glossaryPluginNames = java.util.Collections.emptySet();
    private PluginCategorizer.ProfileResult serverProfile;

    private int pluginScroll  = 0;
    private int sidebarScroll = 0;

    private int sbLeft, sbTop, sbRight, sbBottom;
    private int plLeft, plTop, plRight, plBottom;
    private int numCols;

    // Click-to-copy regions for fixed header text
    private record CopyRegion(int x, int y, int w, String text) {}
    private final List<CopyRegion> headerCopyRegions = new ArrayList<>();

    // Underline flash feedback (0.2 s)
    private int  feedbackX, feedbackY, feedbackW;
    private long feedbackTime = 0;

    public ServerDetailScreen(Screen parent, ServerLogData data) {
        super(Component.literal("Server Detail"));
        this.parent = parent;
        this.data   = data;
    }

    @Override
    protected void init() {
        if (ArchivistMod.INSTANCE != null) {
            glossaryPluginNames = new java.util.HashSet<>(
                    ArchivistMod.INSTANCE.pluginGlossary.getEntries().values());
        }
        serverProfile = PluginCategorizer.profile(data.plugins);

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
                Component.literal("Copy Plugins"),
                btn -> Minecraft.getInstance().keyboardHandler
                        .setClipboard(String.join(", ", data.plugins)))
                .bounds(cx - 55, btnY, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Back"), btn -> minecraft.setScreen(parent))
                .bounds(cx + 60, btnY, 55, 20).build());

        // Build header click-to-copy regions (font is ready here)
        buildHeaderCopyRegions();
    }

    private void buildHeaderCopyRegions() {
        headerCopyRegions.clear();
        int c1 = 8, c2 = width / 3, c3 = (2 * width) / 3;

        // Title
        String displayName = data.getDisplayName();
        int titleX = width / 2 - font.width(displayName) / 2;
        headerCopyRegions.add(new CopyRegion(titleX, 5, font.width(displayName), displayName));

        // Row 1
        addHeaderRegion(c1, 18, "IP: ",      data.ip + ":" + data.port);
        addHeaderRegion(c2, 18, "Brand: ",   data.brand);
        addHeaderRegion(c3, 18, "Version: ", data.version);

        // Row 2 — Players
        String playersVal = data.playerCount >= 0 ? String.valueOf(data.playerCount) : "unknown";
        addHeaderRegion(c1, 30, "Players: ", playersVal);

        // Row 3
        if (!data.domain.equals("unknown") && !data.domain.equals(data.ip)) {
            addHeaderRegion(c1, 42, "Domain: ", data.domain);
        }
        addHeaderRegion(c2, 42, "Logged: ",  data.timestamp);
        addHeaderRegion(c3, 42, "Plugins: ", String.valueOf(data.plugins.size()));
    }

    private void addHeaderRegion(int x, int y, String label, String value) {
        int vx = x + font.width(label);
        headerCopyRegions.add(new CopyRegion(vx, y, font.width(value), value));
    }

    private void copyWithFeedback(String text, int x, int y, int w) {
        minecraft.keyboardHandler.setClipboard(text);
        feedbackX    = x;
        feedbackY    = y;
        feedbackW    = w;
        feedbackTime = System.currentTimeMillis();
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        // ── Header click-to-copy ─────────────────────────────────────────────
        if (mouseY < HEADER_H) {
            for (CopyRegion r : headerCopyRegions) {
                if (mouseX >= r.x() && mouseX <= r.x() + r.w()
                        && mouseY >= r.y() && mouseY <= r.y() + font.lineHeight) {
                    copyWithFeedback(r.text(), r.x(), r.y(), r.w());
                    return true;
                }
            }
        }

        // ── Sidebar click-to-copy ────────────────────────────────────────────
        if (mouseX >= sbLeft && mouseX < sbRight && mouseY >= sbTop && mouseY < sbBottom) {
            int y = sbTop + PAD - sidebarScroll + LABEL_H;

            if (!data.detectedGameAddresses.isEmpty()) {
                for (String addr : data.detectedGameAddresses) {
                    if (mouseY >= y && mouseY < y + ITEM_H) {
                        int tx = sbLeft + 2 + font.width("  ");
                        copyWithFeedback(addr, tx, y, font.width(fitText(addr, SIDEBAR_W - 8)));
                        return true;
                    }
                    y += ITEM_H;
                }
            } else {
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            if (!data.detectedAddresses.isEmpty()) {
                for (String addr : data.detectedAddresses) {
                    if (mouseY >= y && mouseY < y + ITEM_H) {
                        int tx = sbLeft + 2 + font.width("  ");
                        copyWithFeedback(addr, tx, y, font.width(fitText(addr, SIDEBAR_W - 8)));
                        return true;
                    }
                    y += ITEM_H;
                }
            } else {
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            for (ServerLogData.WorldSession ws : data.worlds) {
                if (mouseY >= y && mouseY < y + ITEM_H) {
                    int tx = sbLeft + 2 + font.width("  ");
                    copyWithFeedback(ws.dimension, tx, y, font.width(fitText(ws.dimension, SIDEBAR_W - 8)));
                    return true;
                }
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            for (String rp : data.getResourcePacks()) {
                if (mouseY >= y && mouseY < y + ITEM_H) {
                    int tx = sbLeft + 2 + font.width("  ");
                    copyWithFeedback(rp, tx, y, font.width(fitText(rp, SIDEBAR_W - 8)));
                    return true;
                }
                y += ITEM_H;
            }
        }

        // ── Plugin grid click-to-copy ────────────────────────────────────────
        int gridTop = plTop + LABEL_H;
        if (mouseX >= plLeft && mouseX < plRight && mouseY >= gridTop && mouseY < plBottom) {
            int panelW = plRight - plLeft;
            int colW   = panelW / numCols;
            int relY   = (int)(mouseY - gridTop) + pluginScroll;
            int row    = relY / ITEM_H;
            int col    = (int)(mouseX - plLeft) / colW;
            int idx    = row * numCols + col;
            if (idx >= 0 && idx < data.plugins.size()) {
                String plugin  = data.plugins.get(idx);
                int    arrowW  = font.width("\u25B8 ");
                int    ix      = plLeft + col * colW + 3 + arrowW;
                int    iy      = gridTop + row * ITEM_H - pluginScroll + 1;
                int    maxW    = colW - arrowW - 6;
                copyWithFeedback(plugin, ix, iy, Math.min(font.width(plugin), maxW));
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // ── Header click-to-copy ─────────────────────────────────────────────
        if (mouseY < HEADER_H) {
            for (CopyRegion r : headerCopyRegions) {
                if (mouseX >= r.x() && mouseX <= r.x() + r.w()
                        && mouseY >= r.y() && mouseY <= r.y() + font.lineHeight) {
                    copyWithFeedback(r.text(), r.x(), r.y(), r.w());
                    return true;
                }
            }
        }

        // ── Sidebar click-to-copy ────────────────────────────────────────────
        if (mouseX >= sbLeft && mouseX < sbRight && mouseY >= sbTop && mouseY < sbBottom) {
            int y = sbTop + PAD - sidebarScroll + LABEL_H;

            if (!data.detectedGameAddresses.isEmpty()) {
                for (String addr : data.detectedGameAddresses) {
                    if (mouseY >= y && mouseY < y + ITEM_H) {
                        int tx = sbLeft + 2 + font.width("  ");
                        copyWithFeedback(addr, tx, y, font.width(fitText(addr, SIDEBAR_W - 8)));
                        return true;
                    }
                    y += ITEM_H;
                }
            } else {
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            if (!data.detectedAddresses.isEmpty()) {
                for (String addr : data.detectedAddresses) {
                    if (mouseY >= y && mouseY < y + ITEM_H) {
                        int tx = sbLeft + 2 + font.width("  ");
                        copyWithFeedback(addr, tx, y, font.width(fitText(addr, SIDEBAR_W - 8)));
                        return true;
                    }
                    y += ITEM_H;
                }
            } else {
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            for (ServerLogData.WorldSession ws : data.worlds) {
                if (mouseY >= y && mouseY < y + ITEM_H) {
                    int tx = sbLeft + 2 + font.width("  ");
                    copyWithFeedback(ws.dimension, tx, y, font.width(fitText(ws.dimension, SIDEBAR_W - 8)));
                    return true;
                }
                y += ITEM_H;
            }

            y += 10 + LABEL_H;

            for (String rp : data.getResourcePacks()) {
                if (mouseY >= y && mouseY < y + ITEM_H) {
                    int tx = sbLeft + 2 + font.width("  ");
                    copyWithFeedback(rp, tx, y, font.width(fitText(rp, SIDEBAR_W - 8)));
                    return true;
                }
                y += ITEM_H;
            }
        }

        // ── Plugin grid click-to-copy ────────────────────────────────────────
        int gridTop = plTop + LABEL_H;
        if (mouseX >= plLeft && mouseX < plRight && mouseY >= gridTop && mouseY < plBottom) {
            int panelW = plRight - plLeft;
            int colW   = panelW / numCols;
            int relY   = (int)(mouseY - gridTop) + pluginScroll;
            int row    = relY / ITEM_H;
            int col    = (int)(mouseX - plLeft) / colW;
            int idx    = row * numCols + col;
            if (idx >= 0 && idx < data.plugins.size()) {
                String plugin  = data.plugins.get(idx);
                int    arrowW  = font.width("\u25B8 ");
                int    ix      = plLeft + col * colW + 3 + arrowW;
                int    iy      = gridTop + row * ITEM_H - pluginScroll + 1;
                int    maxW    = colW - arrowW - 6;
                copyWithFeedback(plugin, ix, iy, Math.min(font.width(plugin), maxW));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderHeader(g);
        renderFooter(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderBody(g);

        // Underline flash on copy (200 ms)
        if (System.currentTimeMillis() - feedbackTime < 200) {
            g.fill(feedbackX, feedbackY + font.lineHeight,
                   feedbackX + feedbackW, feedbackY + font.lineHeight + 1,
                   0xFFFFFFFF);
        }
    }

    private void renderHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER_H, 0xCC050510);
        g.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF334466);

        g.drawCenteredString(font,
                Component.literal(data.getDisplayName())
                        .withStyle(s -> s.withColor(0xFFFF55).withBold(true)),
                width / 2, 5, 0xFFFFFFFF);

        int c1 = 8, c2 = width / 3, c3 = (2 * width) / 3;

        drawLabel(g, "IP",      data.ip + ":" + data.port, c1, 18, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(g, "Brand",   data.brand,                 c2, 18, 0xFFAAAAAA, 0xFFFFFFFF);
        drawLabel(g, "Version", data.version,               c3, 18, 0xFFAAAAAA, 0xFFFFFFFF);

        String playersVal = data.playerCount >= 0 ? String.valueOf(data.playerCount) : "unknown";
        drawLabel(g, "Players", playersVal, c1, 30, 0xFFAAAAAA, 0xFFFFDD55);

        if (!data.domain.equals("unknown") && !data.domain.equals(data.ip)) {
            drawLabel(g, "Domain", data.domain, c1, 42, 0xFFAAAAAA, 0xFF88AAFF);
        }
        drawLabel(g, "Logged",  data.timestamp,                     c2, 42, 0xFFAAAAAA, 0xFFAAAAAA);
        drawLabel(g, "Plugins", String.valueOf(data.plugins.size()), c3, 42, 0xFFAAAAAA, 0xFF55FF55);
    }

    private void renderFooter(GuiGraphics g) {
        g.fill(0, height - FOOTER_H,     width, height - FOOTER_H + 1, 0xFF334466);
        g.fill(0, height - FOOTER_H + 1, width, height,                 0xCC050510);
    }

    private void renderBody(GuiGraphics g) {
        g.fill(sbRight, sbTop + 4, sbRight + 1, sbBottom - 4, 0xFF334466);

        g.enableScissor(sbLeft, sbTop, sbRight, sbBottom);
        renderSidebar(g);
        g.disableScissor();
        renderPluginLabel(g);

        int gridTop = plTop + LABEL_H;
        g.enableScissor(plLeft, gridTop, plRight, plBottom);
        renderPluginGrid(g, gridTop);
        g.disableScissor();
    }

    private void renderSidebar(GuiGraphics g) {
        int y = sbTop + PAD - sidebarScroll;

        if (!serverProfile.byCategory.isEmpty()) {
            g.drawString(font, "Server Profile", sbLeft, y, 0xFFFFFF55);
            g.fill(sbLeft, y + 10, sbLeft + font.width("Server Profile"), y + 11, 0x44FFFF55);
            y += LABEL_H;
            for (var entry : serverProfile.byCategory.entrySet()) {
                String badge = "  " + entry.getKey().label + " (" + entry.getValue().size() + ")";
                g.drawString(font, badge, sbLeft + 2, y, entry.getKey().color);
                y += ITEM_H;
            }
            y += 10;
        }

        y = renderListSection(g, sbLeft, y,
                "Game Addresses", data.detectedGameAddresses,
                0xFF55FF55, 0x4455FF55, 0xFFAAFFAA);
        y += 10;
        y = renderListSection(g, sbLeft, y,
                "Detected URLs", data.detectedAddresses,
                0xFF88AAFF, 0x4488AAFF, 0xFFCCCCCC);
        y += 10;
        y = renderWorldsSection(g, sbLeft, y);
        y += 10;
        renderResourcePacksSection(g, sbLeft, y);
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

    private int renderWorldsSection(GuiGraphics g, int x, int y) {
        String heading = "Worlds Visited (" + data.worlds.size() + ")";
        g.drawString(font, heading, x, y, 0xFFFFAA55);
        g.fill(x, y + 10, x + font.width(heading), y + 11, 0x44FFAA55);
        y += LABEL_H;

        if (data.worlds.isEmpty()) {
            g.drawString(font, "  None recorded", x + 2, y, 0xFF555555);
            y += ITEM_H;
        } else {
            for (ServerLogData.WorldSession ws : data.worlds) {
                g.drawString(font, "  " + fitText(ws.dimension, SIDEBAR_W - 8), x + 2, y, 0xFFCCCCCC);
                y += ITEM_H;
            }
        }
        return y;
    }

    private void renderResourcePacksSection(GuiGraphics g, int x, int y) {
        renderListSection(g, x, y, "Resource Packs", data.getResourcePacks(),
                0xFFFF55AA, 0x44FF55AA, 0xFFCCCCCC);
    }

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
        if (!serverProfile.byCategory.isEmpty()) {
            h += LABEL_H + serverProfile.byCategory.size() * ITEM_H + 10;
        }
        h += LABEL_H + Math.max(1, data.detectedGameAddresses.size()) * ITEM_H + 10;
        h += LABEL_H + Math.max(1, data.detectedAddresses.size()) * ITEM_H + 10;
        h += LABEL_H + Math.max(1, data.worlds.size()) * ITEM_H + 10;
        h += LABEL_H + Math.max(1, data.getResourcePacks().size()) * ITEM_H;
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
        while (!s.isEmpty() && font.width(s + ellipsis) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? ellipsis : s + ellipsis;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
