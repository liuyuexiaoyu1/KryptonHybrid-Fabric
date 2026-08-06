package com.xinian.KryptonHybrid.mixin.network.pipeline;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Client-side counterpart of {@link IServerCommonListenerAccessor}: exposes the
 * {@code connection} field of {@link ClientCommonPacketListenerImpl} so the
 * configuration-phase code can obtain the {@link Connection} without going through
 * {@code Minecraft.getConnection()} — which is {@code null} during LOGIN/CONFIGURATION
 * (it only holds the play listener).
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerAccessor {
    @Accessor("connection")
    Connection krypton$getConnection();
}
