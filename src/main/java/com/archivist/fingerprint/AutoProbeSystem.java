package com.archivist.fingerprint;

import com.archivist.ArchivistMod;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import com.archivist.util.ArchivistExecutor;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;

/**
 * Auto-probe system: sends commands to force plugins to reveal their GUIs,
 * then the fingerprint engine captures and identifies them.
 */
public class AutoProbeSystem {

    private static AutoProbeSystem instance;

    private static final int DEFAULT_DELAY_TICKS = 5;       // 250ms
    private static final int DEFAULT_INTERVAL_TICKS = 2;   // 100ms
    private static final int CAPTURE_WAIT_TICKS = 2;       // 100ms
    private static final int TIMEOUT_TICKS = 15;           // 750ms

    private final List<String> probeCommands = new ArrayList<>();
    private boolean probing = false;
    private int currentCommandIndex = 0;
    private int tickCounter = 0;
    private int captureWaitCounter = 0;
    private boolean awaitingCapture = false;
    private int probeSyncId = -1;
    private int guisOpened = 0;
    private int pluginsFound = 0;

    // Delay before starting
    private int startDelayTicks = 0;


    public static AutoProbeSystem getInstance() {
        if (instance == null) instance = new AutoProbeSystem();
        return instance;
    }

    public boolean isProbing() { return probing; }

    public void startProbing(List<String> commands) {
        if (probing) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        probeCommands.clear();
        probeCommands.addAll(commands);
        currentCommandIndex = 0;
        guisOpened = 0;
        pluginsFound = GuiFingerprintEngine.getInstance().getMatches().size();
        probing = true;
        startDelayTicks = DEFAULT_DELAY_TICKS;
        awaitingCapture = false;
        probeSyncId = -1;

        EventBus.post(LogEvent.Type.SYSTEM, "[PROBE] Starting probe (" + commands.size() + " commands)...");
    }

    public void tick() {
        if (!probing) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            abort("Disconnected");
            return;
        }

        // Start delay
        if (startDelayTicks > 0) {
            startDelayTicks--;
            return;
        }

        // Awaiting GUI capture after sending a command
        if (awaitingCapture) {
            captureWaitCounter++;
            if (probeSyncId >= 0) {
                // GUI opened, wait for capture then close
                if (captureWaitCounter >= CAPTURE_WAIT_TICKS) {
                    closeCurrentGui(mc);
                    advanceToNext();
                }
            } else if (captureWaitCounter >= TIMEOUT_TICKS) {
                // No GUI opened, skip
                String cmd = currentCommandIndex > 0 ? probeCommands.get(currentCommandIndex - 1) : "?";
                EventBus.post(LogEvent.Type.SYSTEM, "[PROBE] No GUI from " + cmd);
                advanceToNext();
            }
            return;
        }

        // Interval between commands
        tickCounter++;
        if (tickCounter >= DEFAULT_INTERVAL_TICKS) {
            sendNextCommand(mc);
        }
    }

    public void onGuiOpened(int syncId) {
        if (!probing || !awaitingCapture) return;
        probeSyncId = syncId;
        guisOpened++;
    }

    public void abort(String reason) {
        if (!probing) return;
        probing = false;
        EventBus.post(LogEvent.Type.SYSTEM, "[PROBE] Aborted: " + reason);
    }

    public void reset() {
        probing = false;
        probeCommands.clear();
        currentCommandIndex = 0;
        awaitingCapture = false;
        probeSyncId = -1;
    }

    private void sendNextCommand(Minecraft mc) {
        if (currentCommandIndex >= probeCommands.size()) {
            finish();
            return;
        }

        String command = probeCommands.get(currentCommandIndex);
        currentCommandIndex++;
        tickCounter = 0;
        captureWaitCounter = 0;
        awaitingCapture = true;
        probeSyncId = -1;

        EventBus.post(LogEvent.Type.SYSTEM, "[PROBE] Sending " + command + "...");

        String cmd = command.startsWith("/") ? command.substring(1) : command;
        try {
            mc.player.connection.sendCommand(cmd);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Probe failed to send {}: {}", command, e.getMessage());
            advanceToNext();
        }
    }

    private void closeCurrentGui(Minecraft mc) {
        try {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
        } catch (Exception ignored) {}
        probeSyncId = -1;
    }

    private void advanceToNext() {
        awaitingCapture = false;
        probeSyncId = -1;
        tickCounter = 0;

        if (currentCommandIndex >= probeCommands.size()) {
            finish();
        }
    }

    private void finish() {
        probing = false;
        int newPlugins = GuiFingerprintEngine.getInstance().getMatches().size() - pluginsFound;
        EventBus.post(LogEvent.Type.SYSTEM, "[PROBE] Complete. " + guisOpened + " GUIs captured, " + newPlugins + " plugins identified.");

        String serverKey = getServerKey();
        if (serverKey != null) {
            cacheProbeResults(serverKey);
        }
    }

    /** Cache probe results per server in JSON. */
    private void cacheProbeResults(String serverKey) {
        List<FingerprintMatch> matches = GuiFingerprintEngine.getInstance().getMatches();
        if (matches.isEmpty()) return;

        ArchivistExecutor.run(() -> {
            try {
                Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("archivist").resolve("probe_cache");
                Files.createDirectories(cacheDir);
                String safeName = serverKey.replaceAll("[^a-zA-Z0-9._-]", "_");
                Path cacheFile = cacheDir.resolve(safeName + ".json");

                JsonObject root = new JsonObject();
                root.addProperty("server", serverKey);
                root.addProperty("timestamp", java.time.Instant.now().toString());

                JsonArray arr = new JsonArray();
                for (FingerprintMatch m : matches) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("pluginId", m.pluginId());
                    obj.addProperty("pluginName", m.pluginName());
                    obj.addProperty("confidence", m.confidence());
                    obj.addProperty("inventoryTitle", m.inventoryTitle());
                    obj.addProperty("matchedPatterns", m.matchedPatterns());
                    arr.add(obj);
                }
                root.add("matches", arr);

                Files.writeString(cacheFile,
                        new GsonBuilder().setPrettyPrinting().create().toJson(root),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Failed to cache probe results: {}", e.getMessage());
            }
        });
    }

    /** Check if we have cached probe results for the current server. */
    public boolean hasCachedResults() {
        String serverKey = getServerKey();
        if (serverKey == null) return false;
        String safeName = serverKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path cacheFile = FabricLoader.getInstance().getGameDir()
                .resolve("archivist").resolve("probe_cache").resolve(safeName + ".json");
        return Files.exists(cacheFile);
    }

    private String getServerKey() {
        if (ArchivistMod.INSTANCE == null) return null;
        var dc = ArchivistMod.INSTANCE.dataCollector;
        return dc.ip + ":" + dc.port;
    }
}
