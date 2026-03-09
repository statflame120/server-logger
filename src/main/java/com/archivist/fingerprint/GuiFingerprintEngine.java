package com.archivist.fingerprint;

import com.archivist.ArchivistMod;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core fingerprint engine. Receives GUI data from mixins, builds captures,
 * runs fingerprint matching, and stores results.
 */
public class GuiFingerprintEngine {

    private static GuiFingerprintEngine instance;

    private final FingerprintDatabase database = new FingerprintDatabase();
    private final FingerprintMatcher matcher = new FingerprintMatcher();
    private final List<FingerprintMatch> matches = new CopyOnWriteArrayList<>();
    private final List<GuiCapture> captures = new CopyOnWriteArrayList<>();

    // Pending screen state (screen open arrives before inventory contents)
    private int pendingSyncId = -1;
    private String pendingContainerType = "";
    private String pendingTitle = "";
    private String pendingTitleRaw = "";

    // Inspector mode
    private boolean inspectorEnabled = false;
    private GuiCapture lastInspectorCapture = null;

    public static GuiFingerprintEngine getInstance() {
        if (instance == null) instance = new GuiFingerprintEngine();
        return instance;
    }

    private boolean initialized = false;

    public void init() {
        // Lazy: defer actual loading until first use
        initialized = false;
    }

    private void ensureLoaded() {
        if (!initialized) {
            database.load();
            initialized = true;
        }
    }

    public void reset() {
        matches.clear();
        captures.clear();
        pendingSyncId = -1;
        lastInspectorCapture = null;
    }

    // Called from mixin when OpenScreenS2CPacket arrives
    public void onScreenOpened(int syncId, String containerType, String title, String titleRaw) {
        pendingSyncId = syncId;
        pendingContainerType = containerType;
        pendingTitle = title;
        pendingTitleRaw = titleRaw;
    }

    // Called from mixin when InventoryS2CPacket arrives
    public void onInventoryContents(int syncId, List<ItemStack> contents) {
        if (syncId != pendingSyncId) return;

        List<GuiItemData> items = extractItems(contents);
        GuiCapture capture = new GuiCapture(syncId, pendingContainerType, pendingTitle, pendingTitleRaw, items);
        processCapture(capture);

        pendingSyncId = -1;
    }

    // Called from mixin for individual slot updates
    // We only process bulk inventory contents, so individual slot updates
    // just refresh the last capture if it matches the syncId
    public void onSlotUpdate(int syncId, int slot, ItemStack stack) {
        // For simplicity, the primary matching happens on bulk inventory.
        // Individual slot updates are used for inspector refresh only.
    }

    private void processCapture(GuiCapture capture) {
        ensureLoaded();
        captures.add(capture);

        // Inspector mode - store capture for display
        if (inspectorEnabled) {
            lastInspectorCapture = capture;
            EventBus.post(LogEvent.Type.SYSTEM, "[INSPECTOR] Captured: \"" + capture.title + "\" (" + capture.containerType + ", " + capture.items.size() + " items)");
        }

        // Run fingerprint matching
        List<FingerprintMatch> found = matcher.match(capture, database.getFingerprints());
        if (!found.isEmpty()) {
            for (FingerprintMatch m : found) {
                matches.add(m);
                EventBus.post(LogEvent.Type.PLUGIN, "[GUI] " + m.pluginName() + " — confidence: " + m.confidence().toUpperCase() + " (" + m.matchedPatterns() + "/" + m.totalPatterns() + " matchers)");
            }
        }

        // Notify auto-probe system if active
        AutoProbeSystem probe = AutoProbeSystem.getInstance();
        if (probe.isProbing()) {
            probe.onGuiOpened(capture.syncId);
        }
    }

    private List<GuiItemData> extractItems(List<ItemStack> contents) {
        List<GuiItemData> items = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (stack == null || stack.isEmpty()) continue;

            try {
                String materialId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                Component nameComp = stack.getHoverName();
                String displayName = nameComp.getString();
                String displayNameRaw = nameComp.toString();

                List<String> lore = new ArrayList<>();
                List<String> loreRaw = new ArrayList<>();
                var loreComponent = stack.get(DataComponents.LORE);
                if (loreComponent != null) {
                    for (Component line : loreComponent.lines()) {
                        lore.add(line.getString());
                        loreRaw.add(line.toString());
                    }
                }

                boolean glint = stack.hasFoil();
                int count = stack.getCount();

                items.add(new GuiItemData(i, materialId, displayName, displayNameRaw, lore, loreRaw, count, glint));
            } catch (Exception e) {
                ArchivistMod.LOGGER.debug("[Archivist] Failed to extract item at slot {}: {}", i, e.getMessage());
            }
        }
        return items;
    }

    // Public accessors
    public List<FingerprintMatch> getMatches() { return Collections.unmodifiableList(matches); }
    public List<GuiCapture> getCaptures() { return Collections.unmodifiableList(captures); }
    public FingerprintDatabase getDatabase() { ensureLoaded(); return database; }

    public boolean isInspectorEnabled() { return inspectorEnabled; }
    public void setInspectorEnabled(boolean enabled) { inspectorEnabled = enabled; }
    public GuiCapture getLastInspectorCapture() { return lastInspectorCapture; }

    /** Get all GUI-detected plugin names for display in plugin list. */
    public Set<String> getDetectedPluginNames() {
        Set<String> names = new LinkedHashSet<>();
        for (FingerprintMatch m : matches) {
            names.add(m.pluginName());
        }
        return names;
    }
}
