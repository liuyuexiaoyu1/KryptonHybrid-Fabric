package com.xinian.KryptonHybrid.shared.network.chunk;

import com.xinian.KryptonHybrid.shared.network.codec.KryptonWireVariant;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

public final class KryptonSectionWire {

    private static final int SECTION_MAGIC = 0x4B4B4B;
    private static final int MAX_SECTION_BYTES = 2097152;

    private KryptonSectionWire() {}

    static FriendlyByteBuf friendly(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buf);
    }

    static void writeHeader(ByteBuf buf) {
        FriendlyByteBuf friendly = friendly(buf);
        friendly.writeVarInt(SECTION_MAGIC);
        friendly.writeByte(KryptonWireVariant.runtimeVariant().tag());
    }

    static boolean hasHeader(ByteBuf buf) {
        FriendlyByteBuf friendly = friendly(buf);
        friendly.markReaderIndex();
        if (friendly.readVarInt() != SECTION_MAGIC) {
            friendly.resetReaderIndex();
            return false;
        }
        KryptonWireVariant variant = KryptonWireVariant.fromTag(friendly.readByte());
        if (variant != KryptonWireVariant.runtimeVariant()) {
            throw new RuntimeException("Krypton chunk data variant mismatch: got " + variant
                    + ", expected " + KryptonWireVariant.runtimeVariant());
        }
        return true;
    }

    static void writePart(ByteBuf buf, byte[] data) {
        FriendlyByteBuf friendly = friendly(buf);
        friendly.writeVarInt(data.length);
        friendly.writeBytes(data);
    }

    static byte[] readPart(ByteBuf buf) {
        FriendlyByteBuf friendly = friendly(buf);
        int length = friendly.readVarInt();
        if (length > MAX_SECTION_BYTES) {
            throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
        }
        byte[] data = new byte[length];
        friendly.readBytes(data);
        return data;
    }
}
