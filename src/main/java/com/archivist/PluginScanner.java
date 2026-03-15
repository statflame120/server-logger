package com.archivist;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PluginScanner {

    private final List<String> commandTreePlugins = new ArrayList<>();
    private final List<String> tabCompletePlugins = new ArrayList<>();
    private final List<String> channelPlugins     = new ArrayList<>();
    private final List<String> registryPlugins    = new ArrayList<>();

    private final Set<String> pendingChannelNamespaces  = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRegistryNamespaces = ConcurrentHashMap.newKeySet();

    private boolean active                = false;
    private boolean commandTreeProcessed = false;
    private boolean scanFinished         = false;
    private int     ticks                = 0;
    private int     suggestionId         = -1;
    private String  versionAlias         = null;

    private static final Set<String> IGNORED_NAMESPACES = Set.of(
            "minecraft", "fabric", "fabric-api", "fabricloader", "java", "c"
    );

    private static final Set<String> VERSION_ALIASES = Set.of(
            "version", "ver", "about",
            "bukkit:version", "bukkit:ver", "bukkit:about"
    );

    public void onServerJoin(Minecraft client) {
        reset();

        // Transfer pending namespaces collected during config phase
        for (String ns : pendingChannelNamespaces) {
            resolveAndAdd(ns, channelPlugins, false);
        }
        for (String ns : pendingRegistryNamespaces) {
            resolveAndAdd(ns, registryPlugins, true);
        }
        pendingChannelNamespaces.clear();
        pendingRegistryNamespaces.clear();
        scanFinished = true;
    }

    public void reset() {
        active                = false;
        commandTreeProcessed  = false;
        scanFinished          = false;
        ticks                 = 0;
        suggestionId          = -1;
        versionAlias          = null;
        commandTreePlugins.clear();
        tabCompletePlugins.clear();
        channelPlugins.clear();
        registryPlugins.clear();
        pendingChannelNamespaces.clear();
        pendingRegistryNamespaces.clear();
    }

    public void onChannelNamespace(String ns) {
        if (ns == null || IGNORED_NAMESPACES.contains(ns)) return;
        if (scanFinished) {
            resolveAndAdd(ns, channelPlugins, false);
        } else {
            pendingChannelNamespaces.add(ns);
        }
    }

    public void onRegistryNamespace(String ns) {
        if (ns == null || IGNORED_NAMESPACES.contains(ns)) return;
        pendingRegistryNamespaces.add(ns);
    }

    private void resolveAndAdd(String ns, List<String> target, boolean glossaryOnly) {
        PluginGlossary dict = (ArchivistMod.INSTANCE != null)
                ? ArchivistMod.INSTANCE.pluginGlossary : null;
        String resolved = (dict != null) ? dict.lookup(ns) : null;
        if (resolved == null && glossaryOnly) return;
        String toAdd = (resolved != null) ? resolved : ns;
        if (!target.contains(toAdd)) {
            target.add(toAdd);
        }
    }

    public void onCommandTree(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        if (commandTreeProcessed) return;
        commandTreeProcessed = true;

        commandTreePlugins.clear();
        versionAlias = null;

        PluginGlossary dict = (ArchivistMod.INSTANCE != null)
                ? ArchivistMod.INSTANCE.pluginGlossary : null;

        dispatcher.getRoot().getChildren().forEach(node -> {
            String name = node.getName();

            String[] parts = name.split(":", 2);
            if (parts.length == 2 && !parts[0].isEmpty()) {
                // Namespaced command (e.g. "essentials:fly") — use only the namespace part
                String ns = parts[0];
                String resolved = (dict != null) ? dict.lookup(ns) : null;
                String toAdd = (resolved != null) ? resolved : ns;
                if (!commandTreePlugins.contains(toAdd)) {
                    commandTreePlugins.add(toAdd);
                }
            } else {
                // Plain command — look up in glossary
                if (dict != null) {
                    String fromDict = dict.lookup(name);
                    if (fromDict != null && !commandTreePlugins.contains(fromDict)) {
                        commandTreePlugins.add(fromDict);
                    }
                }
            }

            if (versionAlias == null && VERSION_ALIASES.contains(name)
                    && commandTreePlugins.size() <= 15) {
                versionAlias = name;
                sendTabCompleteProbe(versionAlias);
                active = true;
            }
        });
        ArchivistMod.sendMessage("Command tree scanned: " + commandTreePlugins.size()
                + " plugin(s)" + (versionAlias != null ? " | alias: " + versionAlias : ""));

        if (versionAlias == null) {
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
            ArchivistMod.LOGGER.info("[Archivist] Tab-complete plugins: {}", tabCompletePlugins);
            ArchivistMod.sendMessage("Tab-complete: " + tabCompletePlugins.size() + " plugin(s) — " + tabCompletePlugins);
        }
        finishScan();
    }

    public void tick(Minecraft client) {
        if (ArchivistMod.INSTANCE == null || !ArchivistMod.INSTANCE.config.enabled) return;
        if (client.getConnection() == null || client.level == null) return;

        if (active) {
            ticks++;
            if (ticks >= 20) {
                ArchivistMod.LOGGER.info("[Archivist] Tab-complete probe timed out.");
                ArchivistMod.sendMessage("Tab-complete probe timed out");
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
        ArchivistMod.LOGGER.info(
                "[Archivist] Sent tab-complete probe for /{} (id={})", alias, suggestionId);
        ArchivistMod.sendMessage("Sent tab-complete probe for /" + alias);
    }

    public void finishScan() {
        active = false;
        ticks  = 0;

        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(commandTreePlugins);
        merged.addAll(tabCompletePlugins);
        merged.addAll(channelPlugins);
        merged.addAll(registryPlugins);

        scanFinished = true;

        if (ArchivistMod.INSTANCE != null) {
            ArchivistMod.INSTANCE.dataCollector.onPluginsDetected(new ArrayList<>(merged));
        }
    }
}
