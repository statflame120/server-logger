package com.archivist.mixin;

import com.archivist.ArchivistMod;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationListenerMixin {

    @Inject(method = "handleRegistryData", at = @At("HEAD"))
    private void archivist$onRegistryData(ClientboundRegistryDataPacket packet,
                                          CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            packet.entries().forEach(entry -> {
                try {
                    String ns = entry.id().getNamespace();
                    ArchivistMod.INSTANCE.pluginScanner.onRegistryNamespace(ns);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
