package com.serverlogger.mixin;

import com.serverlogger.ServerLoggerMod;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonListenerMixin {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void serverLogger$onCustomPayload(ClientboundCustomPayloadPacket packet,
                                              CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            if (packet.payload() instanceof BrandPayload brandPayload) {
                ServerLoggerMod.INSTANCE.dataCollector.onServerBrand(brandPayload.brand());
                ServerLoggerMod.LOGGER.info(
                        "[Server Logger] Server brand: {}", brandPayload.brand());
                ServerLoggerMod.sendMessage("Server brand: " + brandPayload.brand());
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void serverLogger$onResourcePack(ClientboundResourcePackPushPacket packet,
                                             CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            ServerLoggerMod.INSTANCE.dataCollector.onResourcePack(packet.url());
            ServerLoggerMod.LOGGER.info(
                    "[Server Logger] Resource pack: {}", packet.url());
            ServerLoggerMod.sendMessage("Resource pack: " + packet.url());
        } catch (Exception ignored) {}
    }
}
