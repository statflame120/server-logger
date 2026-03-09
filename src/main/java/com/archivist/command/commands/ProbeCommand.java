package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;
import com.archivist.fingerprint.AutoProbeSystem;
import com.archivist.fingerprint.GuiFingerprintEngine;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class ProbeCommand implements Command {

    @Override public String name() { return "probe"; }
    @Override public String description() { return "Start GUI fingerprint probe to detect plugins"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            output.accept("Not connected to a server.");
            return;
        }

        AutoProbeSystem probe = AutoProbeSystem.getInstance();
        if (probe.isProbing()) {
            output.accept("Probe already running.");
            return;
        }

        var commands = GuiFingerprintEngine.getInstance().getDatabase().getAllProbeCommands();
        if (commands.isEmpty()) {
            output.accept("No probe commands loaded. Check fingerprints.json.");
            return;
        }

        output.accept("Starting probe with " + commands.size() + " commands...");
        probe.startProbing(commands);
    }
}
