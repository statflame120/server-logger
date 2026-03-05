package com.serverlogger.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.serverlogger.ServerLoggerMod;
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
        if (ServerLoggerMod.INSTANCE == null) return;
        ServerLoggerMod.INSTANCE.pluginScanner.onCommandTree(this.commands);
    }

    @Inject(method = "handleCommandSuggestions", at = @At("HEAD"))
    private void serverLogger$onSuggestions(ClientboundCommandSuggestionsPacket packet,
                                            CallbackInfo ci) throws Throwable {
        if (ServerLoggerMod.INSTANCE == null) return;
        ServerLoggerMod.INSTANCE.pluginScanner.onCommandSuggestions(packet);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void serverLogger$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            ServerLoggerMod.INSTANCE.dataCollector.onDimension(dim);
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read dimension from login: {}", e.getMessage());
            ServerLoggerMod.sendMessage("Could not read dimension from login: " + e.getMessage());
        }
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void serverLogger$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            ServerLoggerMod.INSTANCE.dataCollector.onDimension(dim);
        } catch (Exception e) {
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void serverLogger$onSystemChat(ClientboundSystemChatPacket packet,
                                           CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            Component content = packet.content();
            if (content != null) {
                ServerLoggerMod.INSTANCE.dataCollector.onChatMessage(content.getString());
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void serverLogger$onPlayerChat(ClientboundPlayerChatPacket packet,
                                           CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            Component body = Component.nullToEmpty(packet.body().content());
            if (body != null) {
                ServerLoggerMod.INSTANCE.dataCollector.onChatMessage(body.getString());
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "handleTabListCustomisation", at = @At("HEAD"))
    private void serverLogger$onTabList(ClientboundTabListPacket packet, CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            String header = packet.header().getString();
            String footer = packet.footer().getString();
            ServerLoggerMod.INSTANCE.dataCollector.onChatMessage(header);
            ServerLoggerMod.INSTANCE.dataCollector.onChatMessage(footer);
        } catch (Exception ignored) {}
    }
}
