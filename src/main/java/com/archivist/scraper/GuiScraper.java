package com.archivist.scraper;

import com.archivist.ArchivistMod;
import com.archivist.config.ArchivistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrates GUI scraping by sending commands and intercepting screen opens.
 * State machine: IDLE -> WAITING_FOR_SCREEN -> EXTRACTING -> DELAYING -> DONE.
 *
 * Usage:
 * 1. Call start() to begin scraping (from button or auto-trigger).
 * 2. The mixin on ClientPlayNetworkHandler.onOpenScreen() calls onScreenOpened().
 * 3. Each tick, call tick() to advance the state machine.
 * 4. When done, results are available via getResults() and getIdentifiedPlugins().
 */
public class GuiScraper {

    public enum State {
        IDLE,
        WAITING_FOR_SCREEN,
        EXTRACTING,
        DELAYING,
        DONE
    }

    private State state = State.IDLE;
    private final List<String> commandQueue = new ArrayList<>();
    private int commandIndex = 0;
    private String currentCommand = null;
    private int tickCounter = 0;
    private int timeoutTicks = 0;

    private final List<ScrapeResult> results = new CopyOnWriteArrayList<>();
    private final Set<String> identifiedPlugins = new LinkedHashSet<>();

    private String statusMessage = "Idle";
    private AbstractContainerMenu pendingMenu = null;
    private boolean silentMode = false;

    // Delayed start (for auto-scrape on join)
    private int startDelayTicks = 0;
    private List<String> pendingStartCommands = null;

    private static final int TIMEOUT_TICKS = 40; // 2 seconds

    // ── Public API ──────────────────────────────────────────────────────────

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE && state != State.DONE;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public List<ScrapeResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public Set<String> getIdentifiedPlugins() {
        return Collections.unmodifiableSet(identifiedPlugins);
    }

    /**
     * Schedule a scrape to start after a delay.
     * Used for auto-scrape on join to let the server finish loading.
     */
    public void startDelayed(List<String> commands, int delayTicks) {
        if (isActive()) return;
        pendingStartCommands = new ArrayList<>(commands);
        startDelayTicks = delayTicks;
        statusMessage = "Starting in " + ((delayTicks / 20) + 1) + "s...";
    }

    /** Start a new scrape with the given command list. */
    public void start(List<String> commands) {
        if (isActive()) {
            ArchivistMod.LOGGER.warn("[Archivist] Scraper already active, ignoring start()");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            statusMessage = "Not connected to a server";
            state = State.DONE;
            return;
        }

        results.clear();
        identifiedPlugins.clear();
        commandQueue.clear();
        commandQueue.addAll(commands);
        commandIndex = 0;
        pendingMenu = null;
        state = State.IDLE;

        if (commandQueue.isEmpty()) {
            statusMessage = "No commands to probe";
            state = State.DONE;
            return;
        }

        sendNextCommand();
    }

    /** Called every client tick while scraping is active. */
    public void tick() {
        // Handle delayed start countdown
        if (startDelayTicks > 0) {
            startDelayTicks--;
            statusMessage = "Starting in " + ((startDelayTicks / 20) + 1) + "s...";
            if (startDelayTicks <= 0 && pendingStartCommands != null) {
                List<String> cmds = pendingStartCommands;
                pendingStartCommands = null;
                start(cmds);
            }
            return;
        }

        if (state == State.IDLE || state == State.DONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            statusMessage = "Disconnected";
            state = State.DONE;
            return;
        }

        switch (state) {
            case WAITING_FOR_SCREEN -> {
                timeoutTicks++;
                if (pendingMenu != null) {
                    // Screen opened: extract items
                    state = State.EXTRACTING;
                    extractItems(mc);
                } else if (timeoutTicks >= TIMEOUT_TICKS) {
                    // Timeout: command didn't open a screen, skip
                    ArchivistMod.LOGGER.info("[Archivist] Scraper timeout for {}", currentCommand);
                    statusMessage = "Timeout: " + currentCommand;
                    results.add(new ScrapeResult(currentCommand, Collections.emptyList()));
                    startDelay();
                }
            }
            case EXTRACTING -> {
                // Should not linger here -- extractItems transitions immediately
            }
            case DELAYING -> {
                tickCounter++;
                ArchivistConfig cfg = getConfig();
                int delayTicks = cfg != null ? cfg.scraperDelay / 50 : 20;
                if (tickCounter >= delayTicks) {
                    if (commandIndex < commandQueue.size()) {
                        sendNextCommand();
                    } else {
                        finish();
                    }
                }
            }
        }
    }

    /**
     * Called by the mixin when a screen handler opens.
     * Only intercepts when the scraper is actively waiting.
     */
    public void onScreenOpened(AbstractContainerMenu menu) {
        if (state == State.WAITING_FOR_SCREEN) {
            pendingMenu = menu;
        }
    }

    /** Enable/disable silent mode (suppresses chat error messages during probing). */
    public void setSilentMode(boolean silent) { this.silentMode = silent; }
    public boolean isSilentMode() { return silentMode; }

    /** Reset to idle state. */
    public void reset() {
        state = State.IDLE;
        commandQueue.clear();
        commandIndex = 0;
        currentCommand = null;
        pendingMenu = null;
        startDelayTicks = 0;
        pendingStartCommands = null;
        silentMode = false;
        statusMessage = "Idle";
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void sendNextCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            state = State.DONE;
            return;
        }

        currentCommand = commandQueue.get(commandIndex);
        commandIndex++;
        timeoutTicks = 0;
        pendingMenu = null;
        state = State.WAITING_FOR_SCREEN;

        statusMessage = "Running " + currentCommand + "...";
        ArchivistMod.LOGGER.info("[Archivist] Scraper sending: {}", currentCommand);

        // Strip leading / for sendChatCommand
        String cmd = currentCommand.startsWith("/") ? currentCommand.substring(1) : currentCommand;
        try {
            mc.player.connection.sendCommand(cmd);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Scraper failed to send {}: {}", currentCommand, e.getMessage());
            startDelay();
        }
    }

    private void extractItems(Minecraft mc) {
        List<ScrapedItem> items = new ArrayList<>();

        try {
            AbstractContainerMenu menu = pendingMenu;
            if (menu != null) {
                for (Slot slot : menu.slots) {
                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) continue;

                    int slotIndex = slot.index;
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String displayName = stack.getHoverName().getString();

                    // NBT / components representation
                    String nbtString = stack.toString();

                    // Lore
                    String[] lore = extractLore(stack);

                    // CustomModelData
                    Integer customModelData = extractCustomModelData(stack);

                    items.add(new ScrapedItem(slotIndex, itemId, displayName, nbtString, lore, customModelData));
                }
            }
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Scraper extract error for {}: {}", currentCommand, e.getMessage());
        }

        ScrapeResult result = new ScrapeResult(currentCommand, items);
        results.add(result);

        // Run heuristics
        Set<String> found = PluginHeuristics.identify(items);
        identifiedPlugins.addAll(found);

        statusMessage = currentCommand + ": " + items.size() + " items, " + found.size() + " plugins";
        ArchivistMod.LOGGER.info("[Archivist] Scraped {} -> {} items, plugins: {}", currentCommand, items.size(), found);

        // Close the screen
        try {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
        } catch (Exception ignored) {}

        pendingMenu = null;
        startDelay();
    }

    private String[] extractLore(ItemStack stack) {
        try {
            var loreComponent = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (loreComponent != null) {
                List<String> lines = new ArrayList<>();
                for (var line : loreComponent.lines()) {
                    lines.add(line.getString());
                }
                return lines.toArray(new String[0]);
            }
        } catch (Exception ignored) {}
        return new String[0];
    }

    private Integer extractCustomModelData(ItemStack stack) {
        try {
            var cmd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) {
                // CustomModelData structure varies by MC version; use pure reflection
                // Try value() first (1.21.1-style), then floats() (1.21.5+-style)
                for (String methodName : new String[]{"value", "floats"}) {
                    try {
                        var method = cmd.getClass().getMethod(methodName);
                        Object result = method.invoke(cmd);
                        if (result instanceof Number n) return n.intValue();
                        if (result instanceof java.util.List<?> list && !list.isEmpty()) {
                            Object first = list.getFirst();
                            if (first instanceof Number n) return n.intValue();
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void startDelay() {
        state = State.DELAYING;
        tickCounter = 0;
    }

    private void finish() {
        state = State.DONE;
        statusMessage = "Done: " + identifiedPlugins.size() + " plugin(s) found across "
                + results.size() + " commands";
        ArchivistMod.LOGGER.info("[Archivist] Scrape complete: {}", statusMessage);
    }

    private ArchivistConfig getConfig() {
        if (ArchivistMod.INSTANCE != null) {
            return ArchivistMod.INSTANCE.extendedConfig;
        }
        return null;
    }
}
