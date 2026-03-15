package com.archivist.command.commands;

import com.archivist.ArchivistMod;
import com.archivist.command.Command;
import com.archivist.fingerprint.*;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Replays saved GUI captures against the current fingerprint database.
 * Useful for testing fingerprints without connecting to a server.
 */
public class ReplayFingerprintsCommand implements Command {

    @Override
    public String name() { return "replay"; }

    @Override
    public String description() { return "Replay saved captures against fingerprint database"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        Path captureDir = FabricLoader.getInstance().getGameDir()
                .resolve("archivist").resolve("captures");

        if (!Files.isDirectory(captureDir)) {
            output.accept("No captures directory found. Use the inspector to save captures first.");
            return;
        }

        List<Path> captureFiles;
        try (Stream<Path> stream = Files.list(captureDir)) {
            captureFiles = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            output.accept("Failed to list captures: " + e.getMessage());
            return;
        }

        if (captureFiles.isEmpty()) {
            output.accept("No capture files found in " + captureDir);
            return;
        }

        GuiFingerprintEngine engine = GuiFingerprintEngine.getInstance();
        FingerprintMatcher matcher = new FingerprintMatcher();
        List<Fingerprint> fingerprints = engine.getDatabase().getFingerprints();

        output.accept("Replaying " + captureFiles.size() + " capture(s)...");

        int matched = 0;
        for (Path file : captureFiles) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                GuiCapture capture = parseCapture(obj);

                List<FingerprintMatch> results = matcher.match(capture, fingerprints);
                String fileName = file.getFileName().toString();

                if (results.isEmpty()) {
                    output.accept("[REPLAY] " + fileName + ": no match");
                } else {
                    for (FingerprintMatch m : results) {
                        output.accept("[REPLAY] " + fileName + ": " + m.pluginName()
                                + " (" + m.confidence().toUpperCase() + ", "
                                + m.matchedPatterns() + "/" + m.totalPatterns() + ")");
                    }
                    matched++;
                }
            } catch (Exception e) {
                output.accept("[REPLAY] " + file.getFileName() + ": error — " + e.getMessage());
            }
        }

        output.accept("Replay complete. " + matched + "/" + captureFiles.size() + " captures matched.");
    }

    private GuiCapture parseCapture(JsonObject obj) {
        String containerType = obj.has("containerType") ? obj.get("containerType").getAsString() : "";
        String title = obj.has("title") ? obj.get("title").getAsString() : "";
        String titleRaw = obj.has("titleRaw") ? obj.get("titleRaw").getAsString() : "";

        List<GuiItemData> items = new ArrayList<>();
        if (obj.has("items")) {
            for (JsonElement el : obj.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                int slot = item.has("slot") ? item.get("slot").getAsInt() : 0;
                String materialId = item.has("materialId") ? item.get("materialId").getAsString() : "";
                String displayName = item.has("displayName") ? item.get("displayName").getAsString() : "";
                String displayNameRaw = item.has("displayNameRaw") ? item.get("displayNameRaw").getAsString() : "";

                List<String> lore = new ArrayList<>();
                List<String> loreRaw = new ArrayList<>();
                if (item.has("lore")) {
                    for (JsonElement l : item.getAsJsonArray("lore")) {
                        lore.add(l.getAsString());
                    }
                }
                if (item.has("loreRaw")) {
                    for (JsonElement l : item.getAsJsonArray("loreRaw")) {
                        loreRaw.add(l.getAsString());
                    }
                }

                int count = item.has("count") ? item.get("count").getAsInt() : 1;
                boolean glint = item.has("hasEnchantGlint") && item.get("hasEnchantGlint").getAsBoolean();

                items.add(new GuiItemData(slot, materialId, displayName, displayNameRaw, lore, loreRaw, count, glint));
            }
        }

        return new GuiCapture(0, containerType, title, titleRaw, items);
    }
}
