package com.xinian.KryptonHybrid.shared.network.chunk;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.control.KryptonWireFormat;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumMap;
import java.util.Map;

public final class KryptonHeightmapsStreamCodec implements StreamCodec<ByteBuf, Map<Heightmap.Types, long[]>> {

    private final StreamCodec<ByteBuf, Map<Heightmap.Types, long[]>> vanilla;

    public KryptonHeightmapsStreamCodec(StreamCodec<?, ?> vanilla) {
        this.vanilla = (StreamCodec<ByteBuf, Map<Heightmap.Types, long[]>>) vanilla;
    }

    @Override
    public void encode(ByteBuf buf, Map<Heightmap.Types, long[]> value) {
        if (!KryptonConfig.chunkOptEnabled || !KryptonWireFormat.canWriteCurrentChunkData()) {
            this.vanilla.encode(buf, value);
            return;
        }

        KryptonSectionWire.writeHeader(buf);
        CompoundTag heightmapsTag = new CompoundTag();
        for (Map.Entry<Heightmap.Types, long[]> entry : value.entrySet()) {
            heightmapsTag.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue()));
        }
        ChunkDataCodec.writeHeightmaps(KryptonSectionWire.friendly(buf), heightmapsTag);
    }

    @Override
    public Map<Heightmap.Types, long[]> decode(ByteBuf buf) {
        if (!KryptonSectionWire.hasHeader(buf)) {
            return this.vanilla.decode(buf);
        }

        CompoundTag heightmapsTag = ChunkDataCodec.readHeightmaps(KryptonSectionWire.friendly(buf));
        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
        for (String key : heightmapsTag.keySet()) {
            heightmaps.put(Heightmap.Types.valueOf(key), heightmapsTag.getLongArray(key).orElseGet(() -> new long[0]));
        }
        return heightmaps;
    }
}
