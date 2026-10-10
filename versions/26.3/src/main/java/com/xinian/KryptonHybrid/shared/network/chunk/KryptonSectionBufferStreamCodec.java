package com.xinian.KryptonHybrid.shared.network.chunk;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.codec.KryptonWireVariant;
import com.xinian.KryptonHybrid.shared.network.control.KryptonWireFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;

public final class KryptonSectionBufferStreamCodec implements StreamCodec<ByteBuf, byte[]> {

    private final StreamCodec<ByteBuf, byte[]> vanilla;

    public KryptonSectionBufferStreamCodec(StreamCodec<?, ?> vanilla) {
        this.vanilla = (StreamCodec<ByteBuf, byte[]>) vanilla;
    }

    @Override
    public void encode(ByteBuf buf, byte[] value) {
        if (!KryptonConfig.chunkOptEnabled
                || !KryptonWireFormat.canWriteCurrentChunkData()
                || value.length == 0) {
            this.vanilla.encode(buf, value);
            return;
        }

        KryptonSectionWire.writeHeader(buf);
        ByteBuf sectionBuf = Unpooled.wrappedBuffer(value);
        ChunkDataCodec.SectionSplit split;
        try {
            split = ChunkDataCodec.splitSectionBuffer(sectionBuf, KryptonWireVariant.runtimeVariant());
        } finally {
            sectionBuf.release();
        }
        KryptonSectionWire.writePart(buf, split.blocks());
        KryptonSectionWire.writePart(buf, split.biomes());
    }

    @Override
    public byte[] decode(ByteBuf buf) {
        if (!KryptonSectionWire.hasHeader(buf)) {
            return this.vanilla.decode(buf);
        }

        byte[] blocks = KryptonSectionWire.readPart(buf);
        byte[] biomes = KryptonSectionWire.readPart(buf);
        return ChunkDataCodec.mergeSectionBuffer(blocks, biomes, KryptonWireVariant.runtimeVariant());
    }
}
