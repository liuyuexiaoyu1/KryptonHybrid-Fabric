package com.xinian.KryptonHybrid.shared.network.handshake;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 12111
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

/**
 * Configuration-stage payload that carries the server's current Zstd dictionary
 * to the client. Sent right after {@link KryptonHelloPayload} during
 * configuration, before compression is set up, so the client can apply the
 * server's dictionary and both sides stay in sync.
 */
public record KryptonDictPayload(byte[] dictionaryBytes) implements CustomPacketPayload {
    public static final Type<KryptonDictPayload> TYPE =
//#if MC >= 12111
            new Type<>(Identifier.fromNamespaceAndPath("krypton_hybrid", "dict"));
            //#else
            //$$ new Type<>(ResourceLocation.fromNamespaceAndPath("krypton_hybrid", "dict"));
            //#endif

    public static final StreamCodec<ByteBuf, KryptonDictPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public KryptonDictPayload decode(ByteBuf buf) {
            int len = VarInt.read(buf);
            if (len < 0 || len > 4 * 1024 * 1024) {
                throw new IllegalArgumentException("Invalid dictionary length: " + len);
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new KryptonDictPayload(bytes);
        }

        @Override
        public void encode(ByteBuf buf, KryptonDictPayload payload) {
            VarInt.write(buf, payload.dictionaryBytes().length);
            buf.writeBytes(payload.dictionaryBytes());
        }
    };

    @Override
    public Type<KryptonDictPayload> type() {
        return TYPE;
    }
}
