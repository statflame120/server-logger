package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;
import com.archivist.data.LogExporter;

import java.util.function.Consumer;

public class ExportCommand implements Command {

    @Override public String name() { return "export"; }
    @Override public String description() { return "Export current server log (json/csv/clipboard)"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        if (ArchivistMod.INSTANCE == null) {
            output.accept("Archivist not initialized.");
            return;
        }

        String format = args.trim().toLowerCase();
        if (format.isEmpty()) format = "json";

        switch (format) {
            case "json" -> {
                String path = LogExporter.exportJson();
                output.accept(path != null ? "Exported JSON: " + path : "Export failed.");
            }
            case "csv" -> {
                String path = LogExporter.exportCsv();
                output.accept(path != null ? "Exported CSV: " + path : "Export failed.");
            }
            case "clipboard" -> {
                LogExporter.exportToClipboard();
                output.accept("Copied to clipboard.");
            }
            default -> output.accept("Unknown format: " + format + ". Use: json, csv, clipboard");
        }
    }
}
