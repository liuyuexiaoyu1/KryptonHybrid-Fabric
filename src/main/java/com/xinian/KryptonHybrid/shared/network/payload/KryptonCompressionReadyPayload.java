package com.xinian.KryptonHybrid.shared.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 12111
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

/**
 * Configuration-stage payload sent by the client after it has received and applied
 * the server's Zstd dictionary.  The server responds by installing its compressors
 * (upgradeToZstd); the client installs its own compressors right after sending this,
 * so both sides switch to compressed traffic without a race.
 */
public record KryptonCompressionReadyPayload() implements CustomPacketPayload {
    public static final KryptonCompressionReadyPayload INSTANCE = new KryptonCompressionReadyPayload();

    public static final Type<KryptonCompressionReadyPayload> TYPE =
//#if MC >= 12111
            new Type<>(Identifier.fromNamespaceAndPath("krypton_hybrid", "compression_ready"));
    //#else
    //$$ new Type<>(ResourceLocation.fromNamespaceAndPath("krypton_hybrid", "compression_ready"));
    //#endif

    public static final StreamCodec<ByteBuf, KryptonCompressionReadyPayload> STREAM_CODEC =
            StreamCodec.unit(new KryptonCompressionReadyPayload());

    @Override
    public Type<KryptonCompressionReadyPayload> type() {
        return TYPE;
    }
}
