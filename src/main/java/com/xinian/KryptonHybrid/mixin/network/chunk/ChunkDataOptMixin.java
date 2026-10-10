package com.xinian.KryptonHybrid.mixin.network.chunk;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.chunk.ChunkDataCodec;
import com.xinian.KryptonHybrid.shared.network.codec.KryptonWireVariant;
import com.xinian.KryptonHybrid.shared.network.control.KryptonWireFormat;
//#if MC >= 260300
//$$ import com.xinian.KryptonHybrid.shared.network.chunk.KryptonHeightmapsStreamCodec;
//$$ import com.xinian.KryptonHybrid.shared.network.chunk.KryptonSectionBufferStreamCodec;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import org.spongepowered.asm.mixin.injection.ModifyArg;
//#endif
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Optimizes {@link ClientboundLevelChunkPacketData} serialization with two
 * complementary techniques: <strong>Biome delta encoding</strong> and
 * <strong>Heightmap compression</strong>.
 *
 * <h3>Heightmap compression</h3>
 * <p>Replaces NBT-based heightmap serialization with a compact binary format
 * using XOR-delta encoding on the packed {@code long[]} arrays. Adjacent
 * heightmap columns have highly correlated heights, so XOR-delta produces
 * many near-zero longs that compress significantly better under the
 * connection-level Zstd/ZLIB layer.  The binary format also eliminates
 * ~40 bytes of NBT structural overhead (tag types, UTF-8 key prefixes,
 * 4-byte array lengths).</p>
 *
 * <h3>Biome delta encoding</h3>
 * <p>Separates biome data from block-state data in the section buffer.
 * Single-value biome sections (the common case for most overworld terrain)
 * are encoded as just 2 bytes ({@code flag + VarInt(biomeId)}) instead of
 * the vanilla PalettedContainer overhead. Grouping biome data together also
 * improves cross-section redundancy elimination by the compressor.</p>
 *
 * <h3>Wire format</h3>
 * <pre>
 *   [0x4B]  Krypton chunk-data marker (1 byte)
 *   [0x??]  KryptonWireVariant tag (1 byte) — wire-v2 self-description
 *
 *   --- Heightmaps (binary + XOR-delta) ---
 *   VarInt  entry_count
 *   Per entry:
 *     UTF-8  key
 *     VarInt long_count
 *     long[] XOR-delta encoded
 *
 *   --- Block data (sections without biomes) ---
 *   VarInt  blocks_buffer_length
 *   byte[]  blocks_buffer  (per section: short + PalettedContainer&lt;BlockState&gt;)
 *
 *   --- Biome data (compact) ---
 *   VarInt  section_count
 *   Per section:
 *     0x01 + VarInt(biomeId)           — single-value section
 *     0x00 + VarInt(len) + raw bytes   — multi-value section
 *
 *   --- Block entities (unchanged) ---
 *   LIST_STREAM_CODEC encoded
 * </pre>
 *
 * <h3>Read-side compatibility</h3>
 * <p>On the read side, blocks and biome data are merged back into a standard
 * vanilla section buffer so that {@code getReadBuffer()} and
 * {@code LevelChunkSection.read()} work without modification.</p>
 *
 * <h3>Fallback</h3>
 * <p>If the incoming buffer does not start with {@code 0x4B}, the vanilla
 * constructor runs unmodified (vanilla server or optimization disabled).</p>
 */
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ChunkDataOptMixin {

    //#if MC >= 260300
    //$$ @ModifyArg(
    //$$         method = "<clinit>",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function3;)Lnet/minecraft/network/codec/StreamCodec;"
    //$$         ),
    //$$         index = 0
    //$$ )
    //$$ private static StreamCodec<?, ?> krypton$heightmapsCodec(StreamCodec<?, ?> original) {
    //$$     return new KryptonHeightmapsStreamCodec(original);
    //$$ }
    //$$ @ModifyArg(
    //$$         method = "<clinit>",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function3;)Lnet/minecraft/network/codec/StreamCodec;"
    //$$         ),
    //$$         index = 2
    //$$ )
    //$$ private static StreamCodec<?, ?> krypton$sectionBufferCodec(StreamCodec<?, ?> original) {
    //$$     return new KryptonSectionBufferStreamCodec(original);
    //$$ }
    //#else

    /** Krypton chunk-data marker byte. Never collides with vanilla NBT (starts with TAG_Compound = 0x0A). */
    @Unique private static final int KRYPTON_MARKER = 0x4B;

    //#if MC >= 12105
    @Shadow @Final private Map<Heightmap.Types, long[]> heightmaps;
    //#else
    //$$ @Shadow @Final private CompoundTag heightmaps;
    //#endif
    @Shadow @Final private byte[] buffer;
    @Shadow @Final private List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;

    // ================================================================
    //  Write side
    // ================================================================

    /**
     * Replaces the vanilla {@code write()} with Krypton's optimized format
     * when chunk-data optimization is enabled.
     */
    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void krypton$writeOptimized(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        if (!KryptonConfig.chunkOptEnabled
                || !KryptonWireFormat.canWriteCurrentChunkData()
                || this.buffer.length == 0) return;

        buf.writeByte(KRYPTON_MARKER);
        // Wire-v2 variant tag: makes the wire self-describing, so any runtime can
        // decode it. The packet-object-level write path always produces the runtime
        // variant (connection == runtime here; the socket-edge frame codec handles
        // the other variants when Via is in play).
        buf.writeByte(KryptonWireVariant.runtimeVariant().tag());

        // --- Heightmaps (convert Map<Heightmap.Types, long[]> to CompoundTag, then binary + XOR-delta) ---
        //#if MC >= 12105
        CompoundTag heightmapsTag = new CompoundTag();
        for (Map.Entry<Heightmap.Types, long[]> e : this.heightmaps.entrySet()) {
            heightmapsTag.put(e.getKey().getSerializationKey(), new LongArrayTag(e.getValue()));
        }
        ChunkDataCodec.writeHeightmaps(buf, heightmapsTag);
        //#else
        //$$ ChunkDataCodec.writeHeightmaps(buf, this.heightmaps);
        //#endif

        // --- Split section buffer into blocks + biomes (wire-v2: explicit
        //     lengths instead of the section count, fully self-describing) ---
        io.netty.buffer.ByteBuf sectionBuf = Unpooled.wrappedBuffer(this.buffer);
        ChunkDataCodec.SectionSplit split;
        try {
            split = ChunkDataCodec.splitSectionBuffer(sectionBuf, KryptonWireVariant.runtimeVariant());
        } finally {
            sectionBuf.release();
        }
        buf.writeVarInt(split.blocks().length);
        buf.writeBytes(split.blocks());
        buf.writeVarInt(split.biomes().length);
        buf.writeBytes(split.biomes());

        // --- Block entities (unchanged) ---
        ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.encode(buf, this.blockEntitiesData);

        ci.cancel();
    }

    //#endif
}

