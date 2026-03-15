package com.archivist.mixin;

import com.archivist.ArchivistMod;
import com.archivist.fingerprint.AutoProbeSystem;
import com.archivist.fingerprint.GuiFingerprintEngine;
import com.archivist.gui.screen.ArchivistScreen;
import com.archivist.scraper.GuiScraper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerScreenMixin {

    @Unique
    private Screen archivist$savedScreen = null;

    @Inject(method = "handleOpenScreen", at = @At("HEAD"))
    private void archivist$beforeOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Debug: log what screen is being replaced
        ArchivistMod.LOGGER.info("[Archivist DEBUG] handleOpenScreen HEAD — current screen: {}",
                mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");

        // Save screen only when actively expecting a GUI response from our own command
        if (AutoProbeSystem.getInstance().isAwaitingGui()
                || (ArchivistMod.INSTANCE != null && ArchivistMod.INSTANCE.guiScraper != null
                    && ArchivistMod.INSTANCE.guiScraper.isWaitingForScreen())) {
            archivist$savedScreen = mc.screen;
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void archivist$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Debug: log what the new screen is
        ArchivistMod.LOGGER.info("[Archivist DEBUG] handleOpenScreen TAIL — new screen: {}",
                mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");

        // Route to existing GUI scraper
        GuiScraper scraper = ArchivistMod.INSTANCE.guiScraper;
        if (scraper != null && scraper.isActive()) {
            try {
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

        // Restore saved screen to prevent probe/scraper screen flashing
        if (archivist$savedScreen != null) {
            mc.screen = archivist$savedScreen; // direct field write — no lifecycle triggers
            archivist$savedScreen = null;
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
