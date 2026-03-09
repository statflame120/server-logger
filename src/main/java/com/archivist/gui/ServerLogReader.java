package com.archivist.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.archivist.ArchivistMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ServerLogReader {

    public static List<ServerLogData> readAll() {
        List<ServerLogData> results = new ArrayList<>();
        Path logDir = FabricLoader.getInstance().getGameDir()
                .resolve(ArchivistMod.INSTANCE.config.logFolder);
        if (!Files.isDirectory(logDir)) {
            ArchivistMod.sendMessage("Log directory does not exist: " + logDir.toAbsolutePath());
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.json")) {
            for (Path file : stream) {
                try (Reader r = Files.newBufferedReader(file)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    results.add(new ServerLogData(file.getFileName().toString(), root));
                } catch (Exception e) {
                    ArchivistMod.sendMessage("Failed to parse log file " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            ArchivistMod.sendMessage("Failed to read log directory: " + e.getMessage());
        }

        results.sort(Comparator.comparing((ServerLogData d) -> d.timestamp).reversed());
        return results;
    }
}
