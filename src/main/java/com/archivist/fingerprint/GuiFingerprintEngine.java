package com.archivist.fingerprint;

import com.archivist.ArchivistMod;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.archivist.util.ArchivistExecutor;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.*;
import java.time.Instant;
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

    /** Save a GUI capture to disk as JSON for later replay/analysis. */
    public void saveCapture(GuiCapture capture) {
        ArchivistExecutor.run(() -> {
            try {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("captures");
                Files.createDirectories(dir);

                String safeName = capture.title.replaceAll("[^a-zA-Z0-9._\\- ]", "").trim();
                if (safeName.isEmpty()) safeName = "capture";
                String timestamp = Instant.now().toString().replace(":", "-").substring(0, 19);
                Path file = dir.resolve(timestamp + "_" + safeName + ".json");

                JsonObject root = new JsonObject();
                root.addProperty("timestamp", Instant.now().toString());
                root.addProperty("containerType", capture.containerType);
                root.addProperty("title", capture.title);
                root.addProperty("titleRaw", capture.titleRaw);

                JsonArray items = new JsonArray();
                for (GuiItemData item : capture.items) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("slot", item.slot());
                    obj.addProperty("materialId", item.materialId());
                    obj.addProperty("displayName", item.displayName());
                    obj.addProperty("displayNameRaw", item.displayNameRaw());
                    JsonArray lore = new JsonArray();
                    for (String line : item.lore()) lore.add(line);
                    obj.add("lore", lore);
                    JsonArray loreRaw = new JsonArray();
                    for (String line : item.loreRaw()) loreRaw.add(line);
                    obj.add("loreRaw", loreRaw);
                    obj.addProperty("count", item.count());
                    obj.addProperty("hasEnchantGlint", item.hasEnchantGlint());
                    items.add(obj);
                }
                root.add("items", items);

                Files.writeString(file,
                        new GsonBuilder().setPrettyPrinting().create().toJson(root),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                EventBus.post(LogEvent.Type.SYSTEM, "Capture saved: " + file.getFileName());
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Failed to save capture: {}", e.getMessage());
            }
        });
    }

    /** Get all GUI-detected plugin names for display in plugin list. */
    public Set<String> getDetectedPluginNames() {
        Set<String> names = new LinkedHashSet<>();
        for (FingerprintMatch m : matches) {
            names.add(m.pluginName());
        }
        return names;
    }
}
