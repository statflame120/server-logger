package com.serverlogger.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor to call protected Screen#addRenderableWidget from mixins.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable> T serverLogger$addButton(T widget);
}
