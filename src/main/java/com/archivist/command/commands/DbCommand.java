package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;
import com.archivist.database.ApiConfig;
import com.archivist.database.ApiSyncManager;

import java.util.function.Consumer;

public class DbCommand implements Command {

    @Override public String name() { return "db"; }
    @Override public String description() { return "API sync: status, sync, download, test, reset, set"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        if (ArchivistMod.INSTANCE == null) {
            output.accept("Archivist not initialized.");
            return;
        }

        ApiConfig cfg = ArchivistMod.INSTANCE.apiConfig;
        ApiSyncManager sync = ArchivistMod.INSTANCE.apiSyncManager;
        String[] parts = args.trim().split("\\s+", 3);
        String sub = parts.length > 0 ? parts[0].toLowerCase() : "";

        switch (sub) {
            case "status" -> {
                output.accept("API sync: " + (cfg.enabled ? "enabled" : "disabled"));
                output.accept("Base URL: " + (cfg.getBaseUrl().isBlank() ? "(not set)" : cfg.getBaseUrl()));
                output.accept("Endpoints: " + cfg.getPushEndpoint() + ", " + cfg.getDownloadEndpoint() + ", " + cfg.getResetEndpoint());
                StringBuilder hdr = new StringBuilder("Auth headers: ");
                if (cfg.getAuthHeaderNames().isEmpty()) {
                    hdr.append("(none)");
                } else {
                    boolean first = true;
                    for (String name : cfg.getAuthHeaderNames()) {
                        if (!first) hdr.append(", ");
                        hdr.append(name).append(cfg.hasAuthHeader(name) ? " (set)" : " (empty)");
                        first = false;
                    }
                }
                output.accept(hdr.toString());
                output.accept("Auto-push: " + (cfg.autoPush ? "on" : "off"));
                output.accept("Reset key: " + (cfg.getDecodedResetKey().isEmpty() ? "(not set)" : "(set)"));
            }
            case "sync" -> {
                output.accept("Pushing current session...");
                sync.pushSession(ArchivistMod.INSTANCE.dataCollector);
            }
            case "download" -> {
                output.accept("Downloading logs...");
                sync.downloadLogs();
            }
            case "test" -> {
                output.accept("Testing connection...");
                sync.testConnection();
            }
            case "reset" -> {
                if (parts.length >= 2 && "confirm".equalsIgnoreCase(parts[1])) {
                    output.accept("Resetting remote logs...");
                    sync.resetLogs();
                } else {
                    output.accept("This will reset ALL remote logs. Type: db reset confirm");
                }
            }
            case "set" -> handleSet(parts, cfg, sync, output);
            case "remove" -> handleRemove(parts, cfg, output);
            default -> {
                output.accept("Usage: db <status|sync|download|test|reset|set|remove>");
                output.accept("  db set baseurl <url>");
                output.accept("  db set header <name> <value>");
                output.accept("  db remove header <name>");
            }
        }
    }

    private void handleSet(String[] parts, ApiConfig cfg, ApiSyncManager sync, Consumer<String> output) {
        if (parts.length < 2) {
            output.accept("Usage: db set <baseurl|header> <value>");
            return;
        }
        // parts[0]="set", parts[1]="baseurl <url>" or "header <name> <value>"
        String[] setParts = parts.length >= 3 ? (parts[1] + " " + parts[2]).split("\\s+", 3) : parts[1].split("\\s+", 3);
        String key = setParts[0].toLowerCase();

        switch (key) {
            case "baseurl" -> {
                if (setParts.length < 2) {
                    output.accept("Usage: db set baseurl <url>");
                    return;
                }
                cfg.baseUrl = setParts[1].trim();
                cfg.save();
                sync.refreshClient();
                output.accept("Base URL set to: " + cfg.baseUrl);
            }
            case "header" -> {
                if (setParts.length < 3) {
                    output.accept("Usage: db set header <name> <value>");
                    return;
                }
                String headerName = setParts[1];
                String headerValue = setParts[2];
                cfg.setAuthHeader(headerName, headerValue);
                cfg.save();
                sync.refreshClient();
                output.accept("Header " + headerName + " set to: " + ApiConfig.maskSecret(headerValue));
            }
            default -> output.accept("Unknown setting: " + key + ". Use: baseurl, header");
        }
    }

    private void handleRemove(String[] parts, ApiConfig cfg, Consumer<String> output) {
        if (parts.length < 2) {
            output.accept("Usage: db remove header <name>");
            return;
        }
        String[] rmParts = parts.length >= 3 ? (parts[1] + " " + parts[2]).split("\\s+", 2) : parts[1].split("\\s+", 2);
        if (rmParts.length < 2 || !"header".equalsIgnoreCase(rmParts[0])) {
            output.accept("Usage: db remove header <name>");
            return;
        }
        String headerName = rmParts[1].trim();
        cfg.removeAuthHeader(headerName);
        cfg.save();
        output.accept("Removed header: " + headerName);
    }
}
