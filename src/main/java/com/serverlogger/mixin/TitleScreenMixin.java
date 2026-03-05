package com.serverlogger.mixin;

import com.serverlogger.gui.ServerLogScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void serverLogger$addLogButton(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;

        Button multiplayerButton = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button btn) {
                String text = btn.getMessage().getString();
                if (text.equals("Multiplayer")) {
                    multiplayerButton = btn;
                    break;
                }
            }
        }

        if (multiplayerButton != null) {
            int x = multiplayerButton.getX() + multiplayerButton.getWidth() + 4;
            int y = multiplayerButton.getY();
            Button logButton = Button.builder(Component.literal("Logs"), btn -> {
                Minecraft.getInstance().setScreen(new ServerLogScreen(screen));
            }).bounds(x, y, 50, 20).build();
            ((ScreenAccessor) screen).serverLogger$addButton(logButton);
        }
    }
}
