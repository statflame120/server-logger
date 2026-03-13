package com.archivist.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.archivist.ArchivistMod;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import com.archivist.fingerprint.AutoProbeSystem;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Shadow
    private CommandDispatcher<SharedSuggestionProvider> commands;

    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void serverLogger$onCommandTree(ClientboundCommandsPacket packet,
                                            CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        ArchivistMod.INSTANCE.pluginScanner.onCommandTree(this.commands);
    }

    @Inject(method = "handleCommandSuggestions", at = @At("HEAD"))
    private void serverLogger$onSuggestions(ClientboundCommandSuggestionsPacket packet,
                                            CallbackInfo ci) throws Throwable {
        if (ArchivistMod.INSTANCE == null) return;
        ArchivistMod.INSTANCE.pluginScanner.onCommandSuggestions(packet);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void serverLogger$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            //? if >=1.21.11
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            //? if <1.21.11
            //String dim = packet.commonPlayerSpawnInfo().dimension().location().toString();
            ArchivistMod.INSTANCE.dataCollector.onDimension(dim);
            EventBus.post(LogEvent.Type.WORLD, "World loaded: " + dim);

            String gamemode = packet.commonPlayerSpawnInfo().gameType().name();
            EventBus.post(LogEvent.Type.GAMEMODE, "Gamemode: " + gamemode);
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] Could not read dimension from login: {}", e.getMessage());
        }
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void serverLogger$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            //? if >=1.21.11
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            //? if <1.21.11
            //String dim = packet.commonPlayerSpawnInfo().dimension().location().toString();
            ArchivistMod.INSTANCE.dataCollector.onDimension(dim);
        } catch (Exception e) {
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void serverLogger$onSystemChat(ClientboundSystemChatPacket packet,
                                           CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            Component content = packet.content();
            if (content != null) {
                ArchivistMod.INSTANCE.dataCollector.onChatMessage(content.getString());
            }
        } catch (Exception ignored) {}

        // Suppress chat display during probing or silent scraping
        if (AutoProbeSystem.getInstance().isProbing()) {
            ci.cancel();
        } else if (ArchivistMod.INSTANCE != null
                && ArchivistMod.INSTANCE.guiScraper.isActive()
                && ArchivistMod.INSTANCE.guiScraper.isSilentMode()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void serverLogger$onPlayerChat(ClientboundPlayerChatPacket packet,
                                           CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            ArchivistMod.INSTANCE.dataCollector.onChatMessage(
                    Component.nullToEmpty(packet.body().content()).getString());
        } catch (Exception ignored) {}
    }

    @Inject(method = "handleTabListCustomisation", at = @At("HEAD"))
    private void serverLogger$onTabList(ClientboundTabListPacket packet, CallbackInfo ci) {
        if (ArchivistMod.INSTANCE == null) return;
        try {
            String header = packet.header().getString();
            String footer = packet.footer().getString();
            ArchivistMod.INSTANCE.dataCollector.onChatMessage(header);
            ArchivistMod.INSTANCE.dataCollector.onChatMessage(footer);
        } catch (Exception ignored) {}
    }
}
