package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.ServerDataCollector;
import com.archivist.command.Command;

import java.util.function.Consumer;

public class InfoCommand implements Command {

    @Override public String name() { return "info"; }
    @Override public String description() { return "Show current server summary"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        if (ArchivistMod.INSTANCE == null) {
            output.accept("Archivist not initialized.");
            return;
        }
        ServerDataCollector dc = ArchivistMod.INSTANCE.dataCollector;
        output.accept("=== Server Info ===");
        output.accept("  IP: " + dc.ip + ":" + dc.port);
        output.accept("  Domain: " + dc.domain);
        output.accept("  Brand: " + dc.brand);
        output.accept("  Version: " + dc.version);
        output.accept("  Dimension: " + dc.dimension);
        output.accept("  Players: " + dc.playerCount);
        output.accept("  Plugins: " + dc.getPlugins().size());
    }
}
