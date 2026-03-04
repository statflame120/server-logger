package com.serverlogger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.*;

/**
 * Detects server plugins using two complementary strategies:
 *
 *  1. Command-tree scan — when the server sends its command tree, any command
 *     registered as "plugin:command" leaks the plugin namespace.
 *
 *  2. Tab-completion probe — sends "/version " (or whichever alias is found)
 *     as a tab-complete request; Bukkit servers respond with a list of plugin
 *     names, the same trick used by UI-Utils.
 */
public class PluginScanner {

    // Collected from command tree
    private final List<String> commandTreePlugins = new ArrayList<>();
    // Collected from tab-complete response
    private final List<String> tabCompletePlugins = new ArrayList<>();

    private boolean active        = false;
    private int     ticks         = 0;
    private int     suggestionId  = -1;
    private String  versionAlias  = null;   // first /version-like command found

    /** Commands that Bukkit's /version is registered under. */
    private static final Set<String> VERSION_ALIASES = Set.of(
            "version", "ver", "about",
            "bukkit:version", "bukkit:ver", "bukkit:about"
    );

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void onServerJoin(Minecraft client) {
        reset();
    }

    public void reset() {
        active = false;
        ticks  = 0;
        suggestionId  = -1;
        versionAlias  = null;
        commandTreePlugins.clear();
        tabCompletePlugins.clear();
    }

    // ── Called from ClientPacketListenerMixin ─────────────────────────────

    /**
     * Called after the server sends its full command tree.
     * We scan every root node for namespaced names ("plugin:command").
     */
    public void onCommandTree(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        commandTreePlugins.clear();
        versionAlias = null;

        dispatcher.getRoot().getChildren().forEach(node -> {
            String name = node.getName();

            // Detect "pluginName:command" pattern
            String[] parts = name.split(":", 2);
            if (parts.length == 2 && !parts[0].isEmpty()
                    && !commandTreePlugins.contains(parts[0])) {
                commandTreePlugins.add(parts[0]);
            }

            // Find the version alias to use for tab-complete probe
            if (versionAlias == null && VERSION_ALIASES.contains(name)) {
                versionAlias = name;
            }
        });

        ServerLoggerMod.LOGGER.info(
                "[Server Logger] Command tree scanned. Namespace plugins: {}  |  Version alias: {}",
                commandTreePlugins, versionAlias);

        // Kick off the tab-complete probe if we found a version alias
        if (versionAlias != null) {
            sendTabCompleteProbe(versionAlias);
            active = true;
        } else {
            finishScan();
        }
    }

    /**
     * Called when the server sends tab-complete suggestions in response to
     * our probe packet.
     */
    public void onCommandSuggestions(ClientboundCommandSuggestionsPacket packet) throws Throwable {
        if (!active || packet.id() != suggestionId) return;

        tabCompletePlugins.clear();
        if (!packet.toSuggestions().isEmpty()) {
            for (Suggestion s : packet.toSuggestions().getList()) {
                String name = s.getText();
                if (name != null && !name.isBlank()) {
                    tabCompletePlugins.add(name);
                }
            }
            ServerLoggerMod.LOGGER.info("[Server Logger] Tab-complete plugins found: {}", tabCompletePlugins);
        }
        finishScan();
    }


    // ── Tick loop ─────────────────────────────────────────────────────────

    public void tick(Minecraft client) {
        if (!ServerLoggerMod.INSTANCE.config.enabled) return;
        if (client.getConnection() == null) return;

        // Tab-complete probe timeout (5 seconds = 100 ticks)
        if (active) {
            ticks++;
            if (ticks >= 100) {
                ServerLoggerMod.LOGGER.info("[Server Logger] Tab-complete probe timed out.");
                finishScan();
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private void sendTabCompleteProbe(String alias) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        // Generate a random-ish ID (matching what Brigadier client uses)
        suggestionId = new Random().nextInt(200);
        // Send the packet: "/version " — the trailing space triggers suggestions
        mc.getConnection().send(
                new ServerboundCommandSuggestionPacket(suggestionId, "/" + alias + " "));
        ServerLoggerMod.LOGGER.info(
                "[Server Logger] Sent tab-complete probe for /{} (id={})", alias, suggestionId);
    }

    public void finishScan() {
        active = false;
        ticks  = 0;

        // Merge both lists, deduplicate
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(commandTreePlugins);
        merged.addAll(tabCompletePlugins);

        // Pass to data collector
        if (ServerLoggerMod.INSTANCE != null) {
            ServerLoggerMod.INSTANCE.dataCollector.onPluginsDetected(new ArrayList<>(merged));
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public List<String> getCommandTreePlugins()  { return Collections.unmodifiableList(commandTreePlugins); }
    public List<String> getTabCompletePlugins()  { return Collections.unmodifiableList(tabCompletePlugins); }
}
