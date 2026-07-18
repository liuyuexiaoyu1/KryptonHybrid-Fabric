package com.xinian.KryptonHybrid.mixin.network.pipeline;

import com.xinian.KryptonHybrid.shared.network.security.MotdCache;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Serves Server List Ping responses from a short-lived serialized JSON cache.
 *
 * <p>In 26.2 the STATUS protocol does not register a disconnect packet type,
 * so we avoid calling {@code connection.disconnect()} during the STATUS phase.
 * On the first request we send the cached response and cancel vanilla handling
 * (to skip the redundant JSON re-serialisation).  On duplicate requests we fall
 * through to the vanilla handler which will close the connection normally.</p>
 */
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class ServerStatusPacketListenerImplMixin {

    @Shadow @Final
    private ServerStatus status;

    @Shadow @Final
    private Connection connection;

    @Shadow
    private boolean hasRequestedStatus;

    @Inject(method = "handleStatusRequest", at = @At("HEAD"), cancellable = true)
    private void krypton$serveCachedStatus(ServerboundStatusRequestPacket packet, CallbackInfo ci) {
        String cachedJson = MotdCache.cachedStatusJson(this.status);
        if (cachedJson == null) {
            return; // No cache → fall through to vanilla handler
        }
        if (this.hasRequestedStatus) {
            // Duplicate request: let the vanilla handler deal with it.
            // Do NOT cancel — the vanilla handler will clean up the connection.
            return;
        }
        this.hasRequestedStatus = true;
        this.connection.send(new ClientboundStatusResponsePacket(this.status));
        ci.cancel();
    }
}
