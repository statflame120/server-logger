package com.archivist.gui;

import com.archivist.ArchivistMod;
import com.archivist.fingerprint.AutoProbeSystem;
import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small themed HUD overlay that shows scan progress after joining a server.
 * Appears in the bottom-right corner when the mod GUI is not open.
 * Fades out like a toast once all operations complete.
 */
public class ScanProgressOverlay {

    private static ScanProgressOverlay instance;

    private static final int FADE_OUT_DURATION = 40; // 2 seconds
    private static final int WIDTH = 130;
    private static final int HEIGHT = 28;
    private static final int MARGIN = 6;

    private boolean active = false;
    private int estimatedTicks = 0;
    private int elapsedTicks = 0;
    private int fadeOutTicks = 0;
    private boolean fading = false;

    public static ScanProgressOverlay getInstance() {
        if (instance == null) instance = new ScanProgressOverlay();
        return instance;
    }

    /**
     * Start showing the overlay with an estimated total scan time.
     * Called on server join after calculating the estimate.
     */
    public void startScan(int estimatedTotalTicks) {
        if (!isEnabled()) return;
        this.estimatedTicks = Math.max(20, estimatedTotalTicks);
        this.elapsedTicks = 0;
        this.fadeOutTicks = 0;
        this.fading = false;
        this.active = true;
    }

    /** Called every client tick to update countdown state. */
    public void tick() {
        if (!active) return;

        if (fading) {
            fadeOutTicks++;
            if (fadeOutTicks >= FADE_OUT_DURATION) {
                active = false;
            }
            return;
        }

        elapsedTicks++;

        if (isAllDone()) {
            fading = true;
            fadeOutTicks = 0;
        }
    }

    /** Render the overlay on the HUD. */
    public void render(GuiGraphics g) {
        if (!active || !isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // don't draw over any screen

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int x = screenW - WIDTH - MARGIN;
        int y = screenH - HEIGHT - MARGIN - 40; // above hotbar area

        // Calculate alpha for fade-out
        int alpha = 255;
        if (fading) {
            alpha = Math.max(0, 255 - (255 * fadeOutTicks / FADE_OUT_DURATION));
        }
        if (alpha <= 0) return;

        ColorScheme cs = ColorScheme.get();

        // Apply alpha to colors
        int bg = applyAlpha(cs.tooltipBg(), alpha);
        int border = applyAlpha(cs.tooltipBorder(), alpha);
        int accent = applyAlpha(cs.accent(), alpha);
        int accentDim = applyAlpha(cs.accentDim(), alpha);
        int textPrimary = applyAlpha(cs.textPrimary(), alpha);

        // Background + border
        RenderUtils.drawRect(g, x, y, WIDTH, HEIGHT, bg);
        RenderUtils.drawBorder(g, x, y, WIDTH, HEIGHT, border);

        // "Archivist" label (top-left)
        RenderUtils.drawText(g, "Archivist", x + 4, y + 3, accent);

        // Remaining time (top-right)
        int remainingTicks = Math.max(0, estimatedTicks - elapsedTicks);
        int remainingSecs = (remainingTicks + 19) / 20; // ceil
        String timeText;
        if (fading) {
            timeText = "Done";
        } else {
            timeText = remainingSecs + "s";
        }
        int timeWidth = RenderUtils.scaledTextWidth(timeText);
        RenderUtils.drawText(g, timeText, x + WIDTH - timeWidth - 4, y + 3, textPrimary);

        // "Scanning..." label
        String statusText = fading ? "Complete" : "Scanning...";
        RenderUtils.drawText(g, statusText, x + 4, y + 12, textPrimary);

        // Progress bar
        int barX = x + 4;
        int barY = y + HEIGHT - 6;
        int barW = WIDTH - 8;
        int barH = 3;

        float progress = Math.min(1.0f, (float) elapsedTicks / estimatedTicks);
        int filledW = (int) (barW * progress);

        RenderUtils.drawRect(g, barX, barY, barW, barH, applyAlpha(cs.scrollbarTrack(), alpha));
        if (filledW > 0) {
            RenderUtils.drawRect(g, barX, barY, filledW, barH, accentDim);
        }
    }

    /** Reset on disconnect. */
    public void reset() {
        active = false;
        fading = false;
        elapsedTicks = 0;
        fadeOutTicks = 0;
    }

    private boolean isEnabled() {
        return ArchivistMod.INSTANCE != null
                && ArchivistMod.INSTANCE.extendedConfig.showScanOverlay;
    }

    private boolean isAllDone() {
        if (elapsedTicks < 40) return false;
        if (AutoProbeSystem.getInstance().isProbing()) return false;
        if (ArchivistMod.INSTANCE != null && ArchivistMod.INSTANCE.guiScraper.isActive()) return false;
        return true;
    }

    /**
     * Calculate estimated total scan ticks based on current config and runtime state.
     */
    public static int estimateTotalTicks(com.mojang.brigadier.CommandDispatcher<?> dispatcher) {
        int ticks = 0;

        // Check if tab-complete probe will run (skipped when >15 plugins found from command tree)
        java.util.Set<String> namespaces = new java.util.HashSet<>();
        dispatcher.getRoot().getChildren().forEach(node -> {
            String[] parts = node.getName().split(":", 2);
            if (parts.length == 2 && !parts[0].isEmpty()) {
                namespaces.add(parts[0]);
            }
        });
        if (namespaces.size() <= 15) {
            ticks += 20; // tab-complete round-trip ~1s
        }

        // Auto-probe estimate (filtered to commands actually on this server)
        int probeCommands = com.archivist.fingerprint.SmartProber.countAvailableProbes(dispatcher);
        if (probeCommands > 0) {
            ticks += 5 + (probeCommands * 10); // delay + avg response time per command
        }

        // Auto-scrape estimate
        if (ArchivistMod.INSTANCE != null) {
            var config = ArchivistMod.INSTANCE.extendedConfig;
            if (config.autoScrapeOnJoin || config.smartProbeOnJoin) {
                int scrapeCommands = config.scraperCommands.size();
                int delayTicks = config.scraperDelay / 50; // ms to ticks
                ticks += 100 + (scrapeCommands * (delayTicks + 10)); // initial delay + per-command
            }
        }

        return ticks;
    }

    private static int applyAlpha(int color, int alpha) {
        int existingAlpha = (color >>> 24) & 0xFF;
        int newAlpha = (existingAlpha * alpha) / 255;
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }
}
