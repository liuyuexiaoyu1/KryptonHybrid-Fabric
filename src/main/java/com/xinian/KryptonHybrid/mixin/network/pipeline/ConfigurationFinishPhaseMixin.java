package com.xinian.KryptonHybrid.mixin.network.pipeline;

import com.xinian.KryptonHybrid.KryptonHybrid;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.util.KryptonConnectionUtil;

import com.xinian.KryptonHybrid.shared.network.control.PacketControlPhase;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlState;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the packet-control phase as PLAY when configuration fully completes.
 *
 * <p>Uses {@link IServerCommonListenerAccessor} (a companion accessor mixin on the
 * parent class) instead of {@code @Shadow} so that the connection field can be
 * retrieved without a Mixin refmap, even though the field is declared in the parent
 * class {@code ServerCommonPacketListenerImpl}.</p>
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConfigurationFinishPhaseMixin {

    @Inject(method = "handleConfigurationFinished", at = @At("TAIL"))
    private void krypton$advancePacketControlPhase(ServerboundFinishConfigurationPacket packet, CallbackInfo ci) {
        Connection conn = ((IServerCommonListenerAccessor) (Object) this).krypton$getConnection();
        if (conn == null) {
            return;
        }
        Channel channel = KryptonConnectionUtil.channel(conn);
        PacketControlState state = PacketControlState.get(channel);
        // Force client install gate: when enabled, connections that never
        // negotiated the Krypton hello (vanilla clients without the mod) are
        // kicked with an install request. Memory (singleplayer/LAN)
        // connections skip the hello by design and are never affected.
        if (KryptonConfig.forceClientInstall
                && !conn.isMemoryConnection()
                && !state.isHelloAvailable()) {
            KryptonHybrid.LOGGER.info("Kicking connection {} (no KryptonHybrid client), forceClientInstall is enabled", conn);
            conn.disconnect(Component.translatable("disconnect.krypton_hybrid.client_required"));
            return;
        }
        state.setPhase(PacketControlPhase.PLAY);
    }
}
