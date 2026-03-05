package com.serverlogger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.*;

public class PluginScanner {

    private final List<String> commandTreePlugins = new ArrayList<>();
    private final List<String> tabCompletePlugins = new ArrayList<>();

    private boolean active       = false;
    private int     ticks        = 0;
    private int     suggestionId = -1;
    private String  versionAlias = null;

    private static final Set<String> VERSION_ALIASES = Set.of(
            "version", "ver", "about",
            "bukkit:version", "bukkit:ver", "bukkit:about"
    );

    public void onServerJoin(Minecraft client) {
        reset();
    }

    public void reset() {
        active       = false;
        ticks        = 0;
        suggestionId = -1;
        versionAlias = null;
        commandTreePlugins.clear();
        tabCompletePlugins.clear();
    }

    public void onCommandTree(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        commandTreePlugins.clear();
        versionAlias = null;

        PluginGlossary dict = (ServerLoggerMod.INSTANCE != null)
                ? ServerLoggerMod.INSTANCE.pluginGlossary : null;

        dispatcher.getRoot().getChildren().forEach(node -> {
            String name = node.getName();

            String[] parts = name.split(":", 2);
            if (parts.length == 2 && !parts[0].isEmpty()
                    && !commandTreePlugins.contains(parts[0])) {
                commandTreePlugins.add(parts[0]);
            }

            if (dict != null) {
                String fromDict = dict.lookup(name);
                if (fromDict != null && !commandTreePlugins.contains(fromDict)) {
                    commandTreePlugins.add(fromDict);
                }
            }

            if (versionAlias == null && VERSION_ALIASES.contains(name)) {
                versionAlias = name;
            }
        });

        ServerLoggerMod.LOGGER.info(
                "[Server Logger] Command tree scanned. Plugins found: {}  |  Version alias: {}",
                commandTreePlugins, versionAlias);

        if (versionAlias != null) {
            sendTabCompleteProbe(versionAlias);
            active = true;
        } else {
            finishScan();
        }
    }

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
            ServerLoggerMod.LOGGER.info("[Server Logger] Tab-complete plugins: {}", tabCompletePlugins);
        }
        finishScan();
    }

    public void tick(Minecraft client) {
        if (!ServerLoggerMod.INSTANCE.config.enabled) return;
        if (client.getConnection() == null) return;

        if (active) {
            ticks++;
            if (ticks >= 100) {
                ServerLoggerMod.LOGGER.info("[Server Logger] Tab-complete probe timed out.");
                finishScan();
            }
        }
    }

    private void sendTabCompleteProbe(String alias) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        suggestionId = new Random().nextInt(200);
        mc.getConnection().send(
                new ServerboundCommandSuggestionPacket(suggestionId, "/" + alias + " "));
        ServerLoggerMod.LOGGER.info(
                "[Server Logger] Sent tab-complete probe for /{} (id={})", alias, suggestionId);
    }

    public void finishScan() {
        active = false;
        ticks  = 0;

        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(commandTreePlugins);
        merged.addAll(tabCompletePlugins);

        if (ServerLoggerMod.INSTANCE != null) {
            ServerLoggerMod.INSTANCE.dataCollector.onPluginsDetected(new ArrayList<>(merged));
        }
    }

    public List<String> getCommandTreePlugins() { return Collections.unmodifiableList(commandTreePlugins); }
    public List<String> getTabCompletePlugins()  { return Collections.unmodifiableList(tabCompletePlugins); }
}
