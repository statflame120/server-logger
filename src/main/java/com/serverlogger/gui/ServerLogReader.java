package com.serverlogger.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.serverlogger.ServerLoggerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads and parses all JSON log files from the server-logs directory.
 */
public class ServerLogReader {

    public static List<ServerLogData> readAll() {
        List<ServerLogData> results = new ArrayList<>();
        Path logDir = FabricLoader.getInstance().getGameDir()
                .resolve(ServerLoggerMod.INSTANCE.config.logFolder);

        ServerLoggerMod.LOGGER.info("[Server Logger] Reading logs from: {} (exists={})",
                logDir.toAbsolutePath(), Files.isDirectory(logDir));

        if (!Files.isDirectory(logDir)) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Log directory does not exist: {}", logDir.toAbsolutePath());
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.json")) {
            for (Path file : stream) {
                ServerLoggerMod.LOGGER.info("[Server Logger] Found log file: {}", file.getFileName());
                try (Reader r = Files.newBufferedReader(file)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    results.add(new ServerLogData(file.getFileName().toString(), root));
                } catch (Exception e) {
                    ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to parse log file {}: {}",
                            file.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Failed to read log directory: {}", e.getMessage());
        }

        ServerLoggerMod.LOGGER.info("[Server Logger] Loaded {} server log entries", results.size());

        // Sort by timestamp descending (newest first)
        results.sort(Comparator.comparing((ServerLogData d) -> d.timestamp).reversed());
        return results;
    }
}
