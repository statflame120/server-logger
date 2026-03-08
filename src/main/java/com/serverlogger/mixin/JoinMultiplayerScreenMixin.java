package com.serverlogger.mixin;

import com.serverlogger.gui.ServerLogScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void serverLogger$init(CallbackInfo ci) {
        // 1. Calculate the same dynamic width as OpSec
        int buttonWidth = this.font.width("Logs") + 8;
        int buttonHeight = 20;
        int x = this.width / 2 + 152 - buttonWidth;
        int y = 6;

        addRenderableWidget(Button.builder(
                Component.literal("Logs"),
                btn -> Minecraft.getInstance().setScreen(new ServerLogScreen((Screen) (Object) this)))
                .bounds(x, y, buttonWidth, buttonHeight).build());

    }
}