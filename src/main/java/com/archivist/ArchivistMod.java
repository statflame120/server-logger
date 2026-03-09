package com.archivist;

import com.mojang.blaze3d.platform.InputConstants;
import com.archivist.config.ArchivistConfig;
import com.archivist.database.ApiConfig;
import com.archivist.database.ApiSyncManager;
import com.archivist.database.DatabaseManager;
import com.archivist.gui.screen.ArchivistScreen;
import com.archivist.config.GuiConfig;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import com.archivist.fingerprint.AutoProbeSystem;
import com.archivist.fingerprint.GuiFingerprintEngine;
import com.archivist.scraper.GuiScraper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchivistMod implements ClientModInitializer {

    public static final String MOD_ID = "archivist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ArchivistMod INSTANCE;

    public final ConfigManager       config             = new ConfigManager();
    public final ArchivistConfig     extendedConfig     = new ArchivistConfig();
    public final PluginGlossary      pluginGlossary     = new PluginGlossary();
    public final PluginScanner       pluginScanner      = new PluginScanner();
    public final ServerDataCollector dataCollector      = new ServerDataCollector();
    public final ExceptionResolver  exceptionResolver = new ExceptionResolver();
    public final GuiScraper          guiScraper         = new GuiScraper();
    public final DatabaseManager     databaseManager    = new DatabaseManager();
    public final ApiConfig           apiConfig          = new ApiConfig();
    public final ApiSyncManager      apiSyncManager     = new ApiSyncManager(apiConfig);
    public final GuiConfig           guiConfig          = new GuiConfig();

    private KeyMapping openGuiKey;
    private boolean guiKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config.load();
        extendedConfig.load();
        pluginGlossary.load();
        exceptionResolver.load();
        guiConfig.load();
        apiConfig.load();
        GuiFingerprintEngine.getInstance().init();

        //? if >=1.21.9 {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Archivist Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KeyMapping.Category.MISC
        ));
        //?} else {
        /*openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Archivist Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.misc"
        ));
        *///?}

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            EventBus.reset();
            EventBus.post(LogEvent.Type.SYSTEM, "Archivist active");
            dataCollector.onServerJoin(handler, client);
            pluginScanner.onServerJoin(client);
            EventBus.post(LogEvent.Type.CONNECT, "Connected to " + dataCollector.domain
                    + " (" + dataCollector.ip + ":" + dataCollector.port + ")");
            apiSyncManager.onServerJoin(dataCollector);

            // Auto-scrape on join if enabled (delay 5 seconds = 100 ticks)
            if (extendedConfig.autoScrapeOnJoin && !guiScraper.isActive()) {
                if (extendedConfig.silentScraper) {
                    guiScraper.setSilentMode(true);
                }
                guiScraper.startDelayed(extendedConfig.scraperCommands, 100);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            apiSyncManager.onDisconnect(dataCollector);
            EventBus.post(LogEvent.Type.DISCONNECT, "Disconnected");
            dataCollector.reset();
            pluginScanner.reset();
            guiScraper.reset();
            GuiFingerprintEngine.getInstance().reset();
            AutoProbeSystem.getInstance().reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Early exit: don't run tick logic on title/multiplayer screens
            if (client.level == null || client.getConnection() == null) {
                // Still allow keybind handling below
            } else {
                pluginScanner.tick(client);
                dataCollector.tick(client);
                guiScraper.tick();
                AutoProbeSystem.getInstance().tick();
            }

            if (openGuiKey.consumeClick()) {
                client.setScreen(new ArchivistScreen());
            }

            // Also allow opening from title/multiplayer screens (key mappings don't fire on screens)
            if (client.screen instanceof TitleScreen || client.screen instanceof JoinMultiplayerScreen) {
                boolean keyDown = GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                if (keyDown && !guiKeyWasDown) {
                    client.setScreen(new ArchivistScreen(client.screen));
                }
                guiKeyWasDown = keyDown;
            } else {
                guiKeyWasDown = false;
            }
        });

    }

    // this sends a message to the player's chat if showMessages is enabled.
    public static void sendMessage(String text) {
        if (INSTANCE != null && INSTANCE.config.showMessages) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("\u00a77[Archivist]\u00a7r " + text), false);
                }
            });
        }
    }
}
