package com.archivist;

import com.mojang.blaze3d.platform.InputConstants;
import com.archivist.gui.ManualLogScreen;
import com.archivist.gui.ServerLogScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchivistMod implements ClientModInitializer {

    public static final String MOD_ID = "archivist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ArchivistMod INSTANCE;

    public final ConfigManager       config             = new ConfigManager();
    public final PluginGlossary      pluginGlossary     = new PluginGlossary();
    public final PluginScanner       pluginScanner      = new PluginScanner();
    public final ServerDataCollector dataCollector      = new ServerDataCollector();
    public final ExceptionResolver  exceptionResolver = new ExceptionResolver();

    private KeyMapping openGuiKey;
    private KeyMapping manualLogKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config.load();
        pluginGlossary.load();
        exceptionResolver.load();

        //? if >=1.21.9 {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Archivist Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KeyMapping.Category.MISC
        ));

        manualLogKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Manual Log",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KeyMapping.Category.MISC
        ));
        //?} else {
        /*openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Archivist Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.misc"
        ));

        manualLogKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Manual Log",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "key.categories.misc"
        ));
        *///?}

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!config.enabled) return;
            dataCollector.onServerJoin(handler, client);
            pluginScanner.onServerJoin(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dataCollector.reset();
            pluginScanner.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pluginScanner.tick(client);
            dataCollector.tick(client);

            if (openGuiKey.consumeClick()) {
                client.setScreen(new ServerLogScreen(null));
            }
            if (manualLogKey.consumeClick() && client.player != null) {
                client.setScreen(new ManualLogScreen());
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
