package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;

import java.util.List;
import java.util.function.Consumer;

public class PluginsCommand implements Command {

    @Override public String name() { return "plugins"; }
    @Override public String description() { return "List detected plugins in console"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        if (ArchivistMod.INSTANCE == null) {
            output.accept("Archivist not initialized.");
            return;
        }
        List<String> plugins = ArchivistMod.INSTANCE.dataCollector.getPlugins();
        if (plugins.isEmpty()) {
            output.accept("No plugins detected.");
            return;
        }
        output.accept("=== Detected Plugins (" + plugins.size() + ") ===");
        for (String p : plugins) {
            output.accept("  - " + p);
        }
    }
}
