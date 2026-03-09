package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class ScanCommand implements Command {

    @Override public String name() { return "scan"; }
    @Override public String description() { return "Re-scan / re-detect current server info"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        if (ArchivistMod.INSTANCE == null) {
            output.accept("Archivist not initialized.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            output.accept("Not connected to a server.");
            return;
        }

        output.accept("Rescanning server info...");

        // Trigger plugin scanner rescan
        ArchivistMod.INSTANCE.pluginScanner.onServerJoin(mc);

        // Re-read brand
        try {
            String brand = ((com.archivist.mixin.accessor.ClientCommonListenerAccessor)
                    mc.getConnection()).getServerBrand();
            if (brand != null && !brand.isBlank()) {
                ArchivistMod.INSTANCE.dataCollector.onServerBrand(brand);
                EventBus.post(LogEvent.Type.BRAND, "Server brand: " + brand);
            }
        } catch (Exception ignored) {}

        output.accept("Scan initiated. Results will appear in data windows.");
    }
}
