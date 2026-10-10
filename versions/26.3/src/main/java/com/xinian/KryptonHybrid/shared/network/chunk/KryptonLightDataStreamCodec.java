package com.xinian.KryptonHybrid.shared.network.chunk;

import com.google.common.collect.Lists;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.codec.KryptonWireVariant;
import com.xinian.KryptonHybrid.shared.network.control.KryptonWireFormat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public final class KryptonLightDataStreamCodec implements StreamCodec<ByteBuf, ClientboundLightUpdatePacketData> {

    private static final int KRYPTON_MARKER = 0x4B;
    private static final byte ENC_RAW = 0x00;
    private static final byte ENC_UNIFORM = 0x01;
    private static final int DATA_LAYER_BYTES = 2048;

    private final StreamCodec<ByteBuf, ClientboundLightUpdatePacketData> vanilla;

    public KryptonLightDataStreamCodec(StreamCodec<ByteBuf, ClientboundLightUpdatePacketData> vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public void encode(ByteBuf buf, ClientboundLightUpdatePacketData value) {
        if (!KryptonConfig.lightOptEnabled || !KryptonWireFormat.canWriteCurrentLightData()) {
            this.vanilla.encode(buf, value);
            return;
        }

        int uniformCount = krypton$countUniform(value.skyUpdates()) + krypton$countUniform(value.blockUpdates());
        int totalCount = value.skyUpdates().size() + value.blockUpdates().size();
        int rawCount = totalCount - uniformCount;

        if (2048L * uniformCount + rawCount <= 1L) {
            this.vanilla.encode(buf, value);
            return;
        }

        FriendlyByteBuf friendly = krypton$friendly(buf);
        friendly.writeByte(KRYPTON_MARKER);
        friendly.writeByte(KryptonWireVariant.runtimeVariant().tag());
        friendly.writeBitSet(value.skyYMask());
        friendly.writeBitSet(value.blockYMask());
        friendly.writeBitSet(value.emptySkyYMask());
        friendly.writeBitSet(value.emptyBlockYMask());
        krypton$writeCompressedList(friendly, value.skyUpdates());
        krypton$writeCompressedList(friendly, value.blockUpdates());
    }

    @Override
    public ClientboundLightUpdatePacketData decode(ByteBuf buf) {
        if (buf.getUnsignedByte(buf.readerIndex()) != KRYPTON_MARKER) {
            return this.vanilla.decode(buf);
        }

        FriendlyByteBuf friendly = krypton$friendly(buf);
        friendly.readByte();

        KryptonWireVariant variant = KryptonWireVariant.fromTag(friendly.readByte());
        if (variant == KryptonWireVariant.UNKNOWN || variant != KryptonWireVariant.runtimeVariant()) {
            throw new RuntimeException("Krypton light data variant mismatch: got " + variant
                    + ", expected " + KryptonWireVariant.runtimeVariant());
        }

        BitSet skyYMask = friendly.readBitSet();
        BitSet blockYMask = friendly.readBitSet();
        BitSet emptySky = friendly.readBitSet();
        BitSet emptyBlock = friendly.readBitSet();
        List<byte[]> skyUpd = krypton$readCompressedList(friendly);
        List<byte[]> blkUpd = krypton$readCompressedList(friendly);

        return new ClientboundLightUpdatePacketData(skyYMask, blockYMask, emptySky, emptyBlock, skyUpd, blkUpd);
    }

    private static FriendlyByteBuf krypton$friendly(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buf);
    }

    private static int krypton$countUniform(List<byte[]> arrays) {
        int count = 0;
        for (byte[] arr : arrays) {
            if (krypton$isUniform(arr)) {
                count++;
            }
        }
        return count;
    }

    private static void krypton$writeCompressedList(FriendlyByteBuf buf, List<byte[]> arrays) {
        buf.writeVarInt(arrays.size());
        for (byte[] arr : arrays) {
            if (krypton$isUniform(arr)) {
                buf.writeByte(ENC_UNIFORM);
                buf.writeByte(arr[0]);
            } else {
                buf.writeByte(ENC_RAW);
                buf.writeBytes(arr);
            }
        }
    }

    private static List<byte[]> krypton$readCompressedList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<byte[]> list = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            byte encoding = buf.readByte();
            if (encoding == ENC_UNIFORM) {
                byte v = buf.readByte();
                byte[] arr = new byte[DATA_LAYER_BYTES];
                Arrays.fill(arr, v);
                list.add(arr);
            } else if (encoding == ENC_RAW) {
                byte[] arr = new byte[DATA_LAYER_BYTES];
                buf.readBytes(arr);
                list.add(arr);
            } else {
                throw new IllegalArgumentException("Unknown light data encoding: " + encoding);
            }
        }
        return list;
    }

    private static boolean krypton$isUniform(byte[] arr) {
        byte first = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] != first) {
                return false;
            }
        }
        return true;
    }
}
