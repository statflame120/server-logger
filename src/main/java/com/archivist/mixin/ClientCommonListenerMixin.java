package com.archivist.mixin;

import com.archivist.ArchivistMod;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
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
        if (ArchivistMod.INSTANCE == null) return;
        try {
            if (packet.payload() instanceof BrandPayload brandPayload) {
                ArchivistMod.INSTANCE.dataCollector.onServerBrand(brandPayload.brand());
                ArchivistMod.LOGGER.info(
                        "[Archivist] Server brand: {}", brandPayload.brand());
                ArchivistMod.sendMessage("Server brand: " + brandPayload.brand());
            } else {
                // Log custom packets for the connection log
                EventBus.post(LogEvent.Type.PACKET, "Custom packet: " + packet.payload().type().id());
                ArchivistMod.INSTANCE.pluginScanner.onChannelNamespace(
                        packet.payload().type().id().getNamespace());
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void serverLogger$onResourcePack(ClientboundResourcePackPushPacket packet,
                                             CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            ArchivistMod.INSTANCE.dataCollector.onResourcePack(packet.url());
            ArchivistMod.LOGGER.info(
                    "[Archivist] Resource pack: {}", packet.url());
            ArchivistMod.sendMessage("Resource pack: " + packet.url());
        } catch (Exception ignored) {}
    }
}
