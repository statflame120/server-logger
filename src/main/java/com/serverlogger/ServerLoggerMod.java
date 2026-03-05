package com.serverlogger;

import com.mojang.blaze3d.platform.InputConstants;
import com.serverlogger.gui.ServerLogScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerLoggerMod implements ClientModInitializer {

    public static final String MOD_ID = "server-logger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ServerLoggerMod INSTANCE;

    public final ConfigManager       config           = new ConfigManager();
    public final PluginGlossary    pluginGlossary = new PluginGlossary();
    public final PluginScanner       pluginScanner    = new PluginScanner();
    public final ServerDataCollector dataCollector    = new ServerDataCollector();

    private KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config.load();
        pluginGlossary.load();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Server Logger Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KeyMapping.Category.MISC
        ));

        PayloadTypeRegistry.playS2C().register(MyPayload.ID, MyPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MyPayload.ID, (payload, context) -> {
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
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
        });

    }

    // Sends a message to the player's chat if showMessages is enabled.
    public static void sendMessage(String text) {
        if (INSTANCE != null && INSTANCE.config.showMessages) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("\u00a77[Server Logger]\u00a7r " + text), false);
                }
            });
        }
    }

    public record MyPayload(String data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<MyPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("serverlogger", "my_packet"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MyPayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeUtf(payload.data()),
                        buf -> new MyPayload(buf.readUtf())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
