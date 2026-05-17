package com.xinian.KryptonHybrid.mixin.network.pipeline;

import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder for the 1.20 status JSON cache hook.
 *
 * <p>Forge 1.19.2 builds the status response directly from the server in
 * {@code handleStatusRequest}, so this mixin is not listed in the 1.19.2 config.</p>
 */
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class ServerStatusPacketListenerImplMixin {}
