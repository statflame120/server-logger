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

/**
 * Intercepts key S2C packets from ClientPacketListener.
 *
 * All injections are at TAIL so vanilla processing has already run —
 * the command dispatcher is fully populated by the time we read it.
 *
 * ⚠️  If you get a compile error on a method name, open the Minecraft
 *     source in your IDE (after running genSources) and search for the
 *     packet class name to find the correct handler method name.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    // We shadow the commands field so we can pass the populated dispatcher
    // to PluginScanner without an extra getCommands() call.
    @Shadow
    private CommandDispatcher<SharedSuggestionProvider> commands;

    // ── Command tree ───────────────────────────────────────────────────────
    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void serverLogger$onCommandTree(ClientboundCommandsPacket packet,
                                            CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        // `commands` is now fully populated by vanilla code
        ServerLoggerMod.INSTANCE.pluginScanner.onCommandTree(this.commands);
    }

    // ── Tab-complete response ──────────────────────────────────────────────
    @Inject(method = "handleCommandSuggestions", at = @At("HEAD"))
    private void serverLogger$onSuggestions(ClientboundCommandSuggestionsPacket packet,
                                            CallbackInfo ci) throws Throwable {
        if (ServerLoggerMod.INSTANCE == null) return;
        ServerLoggerMod.INSTANCE.pluginScanner.onCommandSuggestions(packet);
    }

    // ── Login packet (initial dimension) ──────────────────────────────────
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void serverLogger$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            ServerLoggerMod.INSTANCE.dataCollector.onDimension(dim);
        } catch (Exception e) {
            ServerLoggerMod.LOGGER.warn("[Server Logger] Could not read dimension from login: {}", e.getMessage());
        }
    }

    // ── Respawn (dimension change) ────────────────────────────────────────
    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void serverLogger$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (ServerLoggerMod.INSTANCE == null) return;
        try {
            String dim = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
            ServerLoggerMod.INSTANCE.dataCollector.onDimension(dim);
        } catch (Exception e) {
            // Non-critical — dimension change after initial log
        }
    }

    // ── System chat (parse plugin output + URL scraping) ──────────────────
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

    // ── Player chat ───────────────────────────────────────────────────────
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

    // ── Tab list header / footer ──────────────────────────────────────────
    // Method may be named handleTabListCustomisation or handleSetTabListHeaderAndFooter
    // depending on MC version. Verify in your IDE if you get a compile error.
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