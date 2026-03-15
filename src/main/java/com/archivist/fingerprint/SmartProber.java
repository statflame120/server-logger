package com.archivist.fingerprint;

import com.archivist.ArchivistMod;
import com.archivist.PluginGlossary;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.*;

/**
 * Cross-references the server's command tree with the plugin glossary
 * and fingerprint database to select the most useful probe commands.
 */
public class SmartProber {

    /**
     * Given the server's command tree, returns an ordered list of probe commands
     * that are most likely to reveal plugin GUIs.
     */
    public static List<String> selectProbeCommands(CommandDispatcher<?> dispatcher) {
        Set<String> serverCommands = new HashSet<>();
        dispatcher.getRoot().getChildren().forEach(node -> {
            String name = node.getName();
            if (name.contains(":")) {
                serverCommands.add(name.split(":", 2)[1]);
            }
            serverCommands.add(name);
        });

        Set<String> selected = new LinkedHashSet<>();

        // Priority 1: Fingerprint probe commands that the server actually has
        List<String> probeCommands = GuiFingerprintEngine.getInstance().getDatabase().getAllProbeCommands();
        for (String cmd : probeCommands) {
            String bare = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            if (serverCommands.contains(bare)) {
                selected.add(cmd.startsWith("/") ? cmd : "/" + cmd);
            }
        }

        // Priority 2: Glossary commands present on the server
        if (ArchivistMod.INSTANCE != null) {
            PluginGlossary glossary = ArchivistMod.INSTANCE.pluginGlossary;
            for (String cmd : glossary.getEntries().keySet()) {
                if (serverCommands.contains(cmd)) {
                    String full = "/" + cmd;
                    if (!selected.contains(full)) {
                        selected.add(full);
                    }
                }
            }
        }

        return new ArrayList<>(selected);
    }

    /**
     * Returns the number of potential probe targets on this server.
     */
    public static int countAvailableProbes(CommandDispatcher<?> dispatcher) {
        return selectProbeCommands(dispatcher).size();
    }
}
