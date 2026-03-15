package com.archivist.mixin;

import com.archivist.ArchivistMod;
import com.archivist.fingerprint.GuiFingerprintEngine;
import com.archivist.scraper.GuiScraper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerScreenMixin {

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void archivist$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;

        // Route to existing GUI scraper
        GuiScraper scraper = ArchivistMod.INSTANCE.guiScraper;
        if (scraper != null && scraper.isActive()) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null && mc.player.containerMenu != null) {
                    scraper.onScreenOpened(mc.player.containerMenu);
                }
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Scraper screen intercept error: {}", e.getMessage());
            }
        }

        // Route to fingerprint engine
        try {
            String title = packet.getTitle().getString();
            String titleRaw = packet.getTitle().toString();
            String containerType = BuiltInRegistries.MENU.getKey(packet.getType()).toString();

            GuiFingerprintEngine.getInstance().onScreenOpened(
                    packet.getContainerId(), containerType, title, titleRaw
            );
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Fingerprint screen intercept error: {}", e.getMessage());
        }
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void archivist$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            //? if >=1.21.5
            GuiFingerprintEngine.getInstance().onInventoryContents(packet.containerId(), packet.items());
            //? if <1.21.5
            //GuiFingerprintEngine.getInstance().onInventoryContents(packet.getContainerId(), packet.getItems());
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Fingerprint inventory intercept error: {}", e.getMessage());
        }
    }

}
