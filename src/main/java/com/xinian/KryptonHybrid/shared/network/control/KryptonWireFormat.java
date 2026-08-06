package com.xinian.KryptonHybrid.shared.network.control;

import com.xinian.KryptonHybrid.shared.network.util.KryptonConnectionUtil;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;

/**
 * Central checks for connection-scoped Krypton wire-format capability.
 */
public final class KryptonWireFormat {
    private KryptonWireFormat() {}

    public static boolean canWriteCurrentConnection() {
        Channel channel = PacketControlContext.getCurrentChannel();
        return channel != null && canWrite(channel);
    }

    /**
     * Chunk-data wire gate.  Requires the wire-v2 bit and is disabled while the
     * socket-edge frame codec is active for the connection (the codec performs
     * the encoding instead of the packet-object-level {@code write()}).
     */
    public static boolean canWriteCurrentChunkData() {
        return canWriteCurrentFeature(KryptonHelloPayload.FEATURE_CHUNK_DATA);
    }

    /**
     * Light-data wire gate — same semantics as {@link #canWriteCurrentChunkData()}.
     */
    public static boolean canWriteCurrentLightData() {
        return canWriteCurrentFeature(KryptonHelloPayload.FEATURE_LIGHT_DATA);
    }

    /**
     * Block-entity delta gate.  The delta is conveyed through vanilla NBT keys
     * inside an ordinary packet, so it is version-transparent and remains active
     * while the frame codec is in play (the codec passes those packets through).
     */
    public static boolean canWriteBlockEntityDelta(Connection connection) {
        return canWriteFeature(connection, KryptonHelloPayload.FEATURE_BLOCK_ENTITY_DELTA);
    }

    public static boolean canWrite(Connection connection) {
        return connection != null && canWrite(KryptonConnectionUtil.channel(connection));
    }

    public static boolean canWrite(Channel channel) {
        return channel != null && PacketControlState.get(channel).isHelloNegotiated();
    }

    private static boolean canWriteCurrentFeature(int featureFlag) {
        Channel channel = PacketControlContext.getCurrentChannel();
        return channel != null && canWriteFeature(channel, featureFlag);
    }

    private static boolean canWriteFeature(Connection connection, int featureFlag) {
        return connection != null && canWriteFeature(KryptonConnectionUtil.channel(connection), featureFlag);
    }

    private static boolean canWriteFeature(Channel channel, int featureFlag) {
        if (channel == null) {
            return false;
        }
        PacketControlState state = PacketControlState.get(channel);
        // A feature is only used when BOTH ends advertised it (the via
        // compatibility rule may drop features from either side's hello).
        if (!state.supportsLocalFeature(featureFlag) || !state.supportsRemoteFeature(featureFlag)) {
            return false;
        }
        if (featureFlag != KryptonHelloPayload.FEATURE_BLOCK_ENTITY_DELTA
                && !state.supportsRemoteFeature(KryptonHelloPayload.FEATURE_WIRE_V2)) {
            // Wire-v2 features (chunk/light) require the peer to understand the
            // self-describing v2 format; older peers keep the vanilla wire.
            return false;
        }
        return true;
    }
}
