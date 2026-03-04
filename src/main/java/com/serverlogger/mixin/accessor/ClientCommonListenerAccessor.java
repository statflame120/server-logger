package com.serverlogger.mixin.accessor;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the serverBrand field of ClientCommonPacketListenerImpl
 * so we can read it from ServerDataCollector without waiting for a mixin callback.
 *
 * Usage:
 *   String brand = ((ClientCommonListenerAccessor) mc.getConnection()).getServerBrand();
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonListenerAccessor {

    @Accessor("serverBrand")
    String getServerBrand();
}