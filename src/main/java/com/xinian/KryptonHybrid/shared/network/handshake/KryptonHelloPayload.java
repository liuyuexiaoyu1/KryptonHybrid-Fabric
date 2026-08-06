package com.xinian.KryptonHybrid.shared.network.handshake;

import io.netty.buffer.ByteBuf;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.ViaCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 12111
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

/**
 * Lightweight capability-negotiation marker payload used during configuration.
 */
public record KryptonHelloPayload(int featureFlags) implements CustomPacketPayload {
    public static final Type<KryptonHelloPayload> TYPE =
//#if MC >= 12111
            new Type<>(Identifier.fromNamespaceAndPath("krypton_hybrid", "hello"));
            //#else
            //$$ new Type<>(ResourceLocation.fromNamespaceAndPath("krypton_hybrid", "hello"));
            //#endif

    public static final int FEATURE_CHUNK_DATA = 1;
    public static final int FEATURE_LIGHT_DATA = 1 << 1;
    public static final int FEATURE_BLOCK_ENTITY_DELTA = 1 << 2;
    /** Wire-v2 (self-describing, variant-tagged) chunk/light format. */
    public static final int FEATURE_WIRE_V2 = 1 << 3;

    public static final StreamCodec<ByteBuf, KryptonHelloPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public KryptonHelloPayload decode(ByteBuf buf) {
            return new KryptonHelloPayload(buf.readUnsignedByte());
        }

        @Override
        public void encode(ByteBuf buf, KryptonHelloPayload payload) {
            buf.writeByte(payload.featureFlags() & 0xFF);
        }
    };

    /**
     * Builds the local feature advertisement for a connection.
     *
     * <p><strong>Via compatibility rule:</strong> when a Via mod is loaded
     * locally and the protocol version Via is <em>currently negotiating</em>
     * for this connection ({@link ViaCompat}) differs from this runtime's
     * version, Via translates the stream and cannot understand Krypton's
     * custom chunk/light/block-entity encodings — every custom feature is
     * dropped and only the Via-transparent zstd compression layer remains.
     * Same-version connections keep all features even when Via is present.</p>
     *
     * @param channel the connection channel (needed to query Via's negotiated
     *                protocol version); may be {@code null} (treated as
     *                cross-version when Via is present — conservative)
     */
    public static KryptonHelloPayload current(io.netty.channel.Channel channel) {
        if (isViaPresent()
                && !ViaCompat.isSameVersion(channel, SharedConstants.getProtocolVersion())) {
            return new KryptonHelloPayload(0);
        }
        // Server-managed feature decisions: the on/off of every feature is
        // decided by the server (its own config, broadcast in the hello, gated
        // by the write-side intersection).  A plain client connecting to a
        // remote server must NOT apply its local config — it advertises all
        // features it can handle and lets the server pick.  Only when a server
        // runs in this process (dedicated, or integrated/LAN) does the local
        // config govern the advertisement.
        boolean serverContext = KryptonSharedBootstrap.isServerRunning();
        int flags = 0;
        if (serverContext && KryptonConfig.blockEntityDeltaEnabled) {
            flags |= FEATURE_BLOCK_ENTITY_DELTA;
        }
        if (serverContext && KryptonConfig.chunkOptEnabled) {
            flags |= FEATURE_CHUNK_DATA;
        }
        if (serverContext && KryptonConfig.lightOptEnabled) {
            flags |= FEATURE_LIGHT_DATA;
        }
        if (!serverContext) {
            // Client: advertise everything, the server decides.
            flags |= FEATURE_BLOCK_ENTITY_DELTA | FEATURE_CHUNK_DATA | FEATURE_LIGHT_DATA;
        }
        if ((flags & (FEATURE_CHUNK_DATA | FEATURE_LIGHT_DATA)) != 0) {
            flags |= FEATURE_WIRE_V2;
        }
        return new KryptonHelloPayload(flags);
    }

    /** True when a Via mod (ViaFabricPlus / ViaFabric) is loaded locally. */
    public static boolean isViaPresent() {
        return FabricLoader.getInstance().isModLoaded("viafabricplus")
                || FabricLoader.getInstance().isModLoaded("viafabric");
    }

    @Override
    public Type<KryptonHelloPayload> type() {
        return TYPE;
    }

    public boolean supportsChunkData() {
        return (this.featureFlags & FEATURE_CHUNK_DATA) != 0;
    }

    public boolean supportsLightData() {
        return (this.featureFlags & FEATURE_LIGHT_DATA) != 0;
    }

    public boolean supportsBlockEntityDelta() {
        return (this.featureFlags & FEATURE_BLOCK_ENTITY_DELTA) != 0;
    }
}

