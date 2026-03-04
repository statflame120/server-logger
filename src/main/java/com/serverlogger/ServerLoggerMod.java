package com.serverlogger;

import com.mojang.blaze3d.platform.InputConstants;
import com.serverlogger.gui.ServerLogScreen;
import net.minecraft.client.KeyMapping;
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

    public final ConfigManager       config        = new ConfigManager();
    public final PluginScanner       pluginScanner = new PluginScanner();
    public final ServerDataCollector dataCollector = new ServerDataCollector();

    private KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config.load();

        // ── Keybind: Z to open server logs GUI ─────────────────────────────
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.server-logger.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KeyMapping.Category.MISC
        ));

        // Register our custom payload type (required even if unused for now)
        PayloadTypeRegistry.playS2C().register(MyPayload.ID, MyPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MyPayload.ID, (payload, context) -> {
            context.client().execute(() -> LOGGER.info("Custom payload: {}", payload.data()));
        });

        // ── Server join ───────────────────────────────────────────────────
        // In 1.21.11 the JOIN callback is (handler, sender, client)
        // where sender is PacketSender, NOT ClientPlayNetworking.Context
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            dataCollector.onServerJoin(handler, client);
            pluginScanner.onServerJoin(client);
        });

        // ── Disconnect ────────────────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dataCollector.reset();
            pluginScanner.reset();
        });

        // ── Tick ──────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pluginScanner.tick(client);
            dataCollector.tick(client);

            // Open GUI keybind (Z key)
            if (openGuiKey.consumeClick()) {
                client.setScreen(new ServerLogScreen(null));
            }
        });

        LOGGER.info("[Server Logger] Initialized for Minecraft 1.21.11");
    }

    // ── Custom packet payload (boilerplate required by Fabric 0.141+) ─────
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