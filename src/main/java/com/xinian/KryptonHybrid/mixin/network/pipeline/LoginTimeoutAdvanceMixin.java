package com.xinian.KryptonHybrid.mixin.network.pipeline;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlPhase;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlState;
import com.xinian.KryptonHybrid.shared.network.security.HandshakeTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advances the {@link HandshakeTimeoutHandler} from LOGIN ??PLAY stage when the
 * login phase completes and the connection transitions to configuration/play.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public class LoginTimeoutAdvanceMixin {

    @Shadow @Final
    Connection connection;

    /**
     * Forge 1.20.1 has no configuration phase; when accepted login completes,
     * the connection is about to become a PLAY listener.
     *
     * <p>Hello negotiation is intentionally <em>not</em> performed here. Wire-format
     * optimizations stay disabled until the client sends
     * {@code KryptonHelloPayload} on {@code KryptonHybrid.NETWORK}, which the
     * server-side handler converts into
     * {@link PacketControlState#markHelloNegotiated(int)} with the client's
     * actual feature flags. Vanilla clients never send one, so they continue
     * receiving vanilla wire format.</p>
     */
    @Inject(method = "handleAcceptedLogin", at = @At("TAIL"))
    private void krypton$advanceToPlayTimeout(CallbackInfo ci) {
        if (!KryptonConfig.securityEnabled) return;
        HandshakeTimeoutHandler.advanceStage(
                this.connection.channel(), HandshakeTimeoutHandler.Stage.PLAY);
        PacketControlState.get(this.connection.channel()).setPhase(PacketControlPhase.PLAY);
    }
}

