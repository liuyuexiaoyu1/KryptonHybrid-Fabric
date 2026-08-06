package com.xinian.KryptonHybrid.shared.network.chunk;

import com.xinian.KryptonHybrid.shared.network.codec.KryptonWireVariant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;

import java.util.Set;

/**
 * Codec for Krypton's optimised chunk-data wire format.
 *
 * <h3>Heightmap binary format (replaces NBT)</h3>
 * <pre>
 *   VarInt  entry_count
 *   Per entry:
 *     UTF-8  key              (FriendlyByteBuf.writeUtf / readUtf)
 *     VarInt long_count
 *     VarLong[] ZigZag(XOR-delta)  (first long raw, subsequent = raw[i] ^ raw[i-1])
 * </pre>
 * <p>XOR-delta exploits the spatial correlation of heightmap columns (adjacent columns
 * have similar heights), producing many near-zero longs that compress significantly
 * better under the connection-level Zstd/ZLIB layer.  The binary format itself removes
 * ~40 bytes of NBT structural overhead per chunk.</p>
 *
 * <h3>Biome delta encoding</h3>
 * <p>Section biome data is extracted from the vanilla section buffer and written as a
 * compact per-section stream.  Single-value biome sections (the common case: flat terrain,
 * ocean, plains) are encoded as 2 bytes ({@code flag + VarInt(biomeId)}) instead of the
 * vanilla PalettedContainer format.  More importantly, because biome data is grouped
 * together rather than interleaved with block-state data, the connection-level compressor
 * can exploit cross-section biome redundancy far more effectively.</p>
 *
 * <h3>Variant dispatch</h3>
 * <p>Every routine that touches the vanilla section buffer is parameterised on a
 * {@link KryptonWireVariant}.  The packet-object level (same-version) path passes the
 * <em>runtime</em> variant; the socket-edge frame codec passes the <em>connection</em>
 * variant (which may differ from the runtime when Via rewrites/translates the stream).
 * The Krypton wire itself carries the variant tag, so it is self-describing: any runtime
 * can decode a wire produced by any other runtime.</p>
 */
public final class ChunkDataCodec {

    /** Marker for a single-value (uniform) biome section. */
    public static final byte BIOME_SINGLE_VALUE = 0x01;
    /** Marker for a multi-value (raw PalettedContainer bytes) biome section. */
    public static final byte BIOME_RAW = 0x00;

    /**
     * Threshold for biome PalettedContainer: bits-per-entry values above this use
     * the global palette (no palette data written).
     * See {@link net.minecraft.world.level.chunk.PalettedContainer.Strategy#SECTION_BIOMES}.
     */
    private static final int BIOME_GLOBAL_THRESHOLD = 3;

    /**
     * Threshold for block-state PalettedContainer: bits-per-entry values above this
     * use the global palette (no palette data written).
     * See {@link net.minecraft.world.level.chunk.PalettedContainer.Strategy#SECTION_STATES}.
     */
    private static final int STATES_GLOBAL_THRESHOLD = 8;

    /** Number of elements per block-state section (16×16×16). */
    private static final int SECTION_ELEMENTS = 4096;
    /** Number of elements per biome section (4×4×4). */
    private static final int BIOME_ELEMENTS = 64;

    private ChunkDataCodec() {}

    // ======================================================================
    //  Heightmap encoding (wire format is version-independent)
    // ======================================================================

    /**
     * Writes heightmaps in Krypton's compact binary format with XOR-delta encoding.
     *
     * @param buf        destination buffer
     * @param heightmaps the vanilla {@link CompoundTag} containing heightmap entries
     */
    public static void writeHeightmaps(FriendlyByteBuf buf, CompoundTag heightmaps) {
        Set<String> keys = heightmaps.keySet();
        buf.writeVarInt(keys.size());
        for (String key : keys) {
            buf.writeUtf(key);
//#if MC >= 12105
            long[] data = heightmaps.getLongArray(key).orElseGet(() -> new long[0]);
            //#else
            //$$ long[] data = heightmaps.getLongArray(key);
            //#endif
            buf.writeVarInt(data.length);
            // XOR-delta + ZigZag VarLong: correlated heightmaps usually produce
            // small deltas, so avoid paying a fixed 8 bytes per long.
            long prev = 0;
            for (long v : data) {
                buf.writeVarLong(zigZag(v ^ prev));
                prev = v;
            }
        }
    }

    /**
     * Reads heightmaps from Krypton's compact binary format with XOR-delta decoding.
     *
     * @param buf source buffer
     * @return a {@link CompoundTag} identical to the vanilla heightmaps tag
     */
    public static CompoundTag readHeightmaps(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            String key = buf.readUtf();
            int len = buf.readVarInt();
            long[] data = new long[len];
            // XOR-delta decode: raw[0] = delta[0], raw[i] = delta[i] ^ raw[i-1]
            long prev = 0;
            for (int j = 0; j < len; j++) {
                long delta = unZigZag(buf.readVarLong());
                data[j] = delta ^ prev;
                prev = data[j];
            }
            tag.put(key, new LongArrayTag(data));
        }
        return tag;
    }

    private static long zigZag(long value) {
        return (value << 1) ^ (value >> 63);
    }

    private static long unZigZag(long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

    // ======================================================================
    //  PalettedContainer scanner (skips over serialised containers)
    // ======================================================================

    /**
     * Advances the reader index of {@code buf} past one serialised
     * {@link net.minecraft.world.level.chunk.PalettedContainer}, returning the
     * number of bytes consumed.  Uses the {@link KryptonWireVariant#runtimeVariant()
     * runtime variant}.
     */
    public static int skipPalettedContainer(ByteBuf buf, int globalThreshold) {
        return skipPalettedContainer(buf, globalThreshold, SECTION_ELEMENTS, KryptonWireVariant.runtimeVariant());
    }

    /**
     * Advances the reader index of {@code buf} past one serialised
     * {@link net.minecraft.world.level.chunk.PalettedContainer}, returning the
     * number of bytes consumed.  Uses the {@link KryptonWireVariant#runtimeVariant()
     * runtime variant}.
     */
    public static int skipPalettedContainer(ByteBuf buf, int globalThreshold, int sectionElements) {
        return skipPalettedContainer(buf, globalThreshold, sectionElements, KryptonWireVariant.runtimeVariant());
    }

    /**
     * Advances the reader index of {@code buf} past one serialised
     * {@link net.minecraft.world.level.chunk.PalettedContainer}, returning the
     * number of bytes consumed.
     *
     * <p>The byte layout differs by variant:</p>
     * <ul>
     *   <li>≤ 1.21.4 ({@link KryptonWireVariant#OLD}): the storage is written with
     *       {@code writeLongArray} which emits a leading VarInt size; a single-value
     *       container is {@code byte(0) + VarInt(id) + VarInt(0)}.</li>
     *   <li>1.21.5+ ({@link KryptonWireVariant#MID}, {@link KryptonWireVariant#NEW}):
     *       the storage is written with {@code writeFixedSizeLongArray} — no leading
     *       VarInt size; the long count is derived from the bits-per-entry and the
     *       section size (4096 for blocks, 64 for biomes); a single-value container is
     *       {@code byte(0) + VarInt(id)}.</li>
     * </ul>
     *
     * @param buf             the buffer positioned at the start of a PalettedContainer
     * @param globalThreshold bits-per-entry above which the global palette is used
     *                        (8 for block states, 3 for biomes)
     * @param sectionElements number of elements in the section (4096 for blocks, 64 for biomes)
     * @param variant         wire variant controlling the byte layout
     * @return bytes consumed
     */
    public static int skipPalettedContainer(ByteBuf buf, int globalThreshold, int sectionElements, KryptonWireVariant variant) {
        int start = buf.readerIndex();
        int bpe = buf.readUnsignedByte();

        if (bpe == 0) {
            // SingleValuePalette: one VarInt (the palette entry), no storage data.
            VarInt.read(buf);
            if (variant.storagePrefixed()) {
                // ≤ 1.21.4 SimpleBitStorage writes a VarInt length prefix
                // (always 0 for a single-value palette).
                VarInt.read(buf);
            }
            return buf.readerIndex() - start;
        } else if (bpe <= globalThreshold) {
            // LinearPalette or HashMapPalette: VarInt(size) + size × VarInt(entry)
            int paletteSize = VarInt.read(buf);
            for (int i = 0; i < paletteSize; i++) {
                VarInt.read(buf);
            }
        }
        // else: GlobalPalette — no palette data

        if (variant.storagePrefixed()) {
            // ≤ 1.21.4: VarInt(longCount) length prefix + longCount × long
            int longCount = VarInt.read(buf);
            buf.skipBytes(longCount * 8);
        } else {
            // 1.21.5+ / 26.x: written with writeFixedSizeLongArray — NO leading VarInt size.
            // The number of longs is derived from bits-per-entry and section element count,
            // matching SimpleBitStorage: valuesPerLong = 64 / bpe (integer division),
            // longCount = (sectionElements + valuesPerLong - 1) / valuesPerLong.
            int valuesPerLong = 64 / bpe;
            int longCount = (sectionElements + valuesPerLong - 1) / valuesPerLong;
            buf.skipBytes(longCount * 8);
        }

        return buf.readerIndex() - start;
    }

    // ======================================================================
    //  Section-buffer splitting / merging
    // ======================================================================

    /** Result of {@link #splitSectionBuffer}: the blocks-only bytes and the compact biome stream. */
    public record SectionSplit(byte[] blocks, byte[] biomes) {}

    /**
     * Splits a vanilla section buffer into a blocks-only buffer and a compact biome
     * stream.  The section buffer format per section is:
     * {@code short(nonEmptyBlockCount) [+ short(fluidCount) on 26.1+] + PalettedContainer<BlockState> + PalettedContainer<Biome>}.
     *
     * <p>The loop is count-free: it parses sections until {@code src} is exhausted.
     * The vanilla buffer always contains exactly whole sections (it is built by
     * {@code calculateChunkSize} + {@code extractChunkData}), so end-of-input is an
     * unambiguous terminator and no explicit section count is carried on the wire.</p>
     *
     * @param src     positioned at the start of the vanilla section buffer; fully consumed
     * @param variant wire variant controlling the byte layout of the input
     */
    public static SectionSplit splitSectionBuffer(ByteBuf src, KryptonWireVariant variant) {
        FriendlyByteBuf blocksOut = new FriendlyByteBuf(Unpooled.buffer(src.readableBytes() / 2));
        FriendlyByteBuf biomesOut = new FriendlyByteBuf(Unpooled.buffer(src.readableBytes() / 4));
        try {
            while (src.isReadable()) {
                // ---- Block data: short(nonEmptyBlockCount) [+ short(fluidCount) on 26.1+]
                //                    + PalettedContainer<BlockState> ----
                int blockStart = src.readerIndex();
                src.skipBytes(variant.sectionHeaderBytes());
                skipPalettedContainer(src, STATES_GLOBAL_THRESHOLD, SECTION_ELEMENTS, variant);
                int blockSize = src.readerIndex() - blockStart;
                blocksOut.writeBytes(src, blockStart, blockSize);

                // ---- Biome data: PalettedContainer<Biome> ----
                int biomeStart = src.readerIndex();
                int biomeBpe = src.getUnsignedByte(biomeStart); // peek

                if (biomeBpe == 0) {
                    // Single-value biome: byte(0) + VarInt(biomeId) [+ VarInt(0) storage prefix on ≤ 1.21.4]
                    src.readByte(); // skip bpe=0
                    int biomeId = VarInt.read(src);
                    if (variant.biomeSingleValueTrailingPrefix()) {
                        // ≤ 1.21.4 writes the (always-zero) storage length prefix.
                        // Note: this byte must be consumed here or the next section's
                        // parse would silently shift by one byte.
                        VarInt.read(src);
                    }
                    biomesOut.writeByte(BIOME_SINGLE_VALUE);
                    biomesOut.writeVarInt(biomeId);
                } else {
                    // Multi-value biome: copy raw PalettedContainer bytes
                    skipPalettedContainer(src, BIOME_GLOBAL_THRESHOLD, BIOME_ELEMENTS, variant);
                    int biomeSize = src.readerIndex() - biomeStart;
                    src.readerIndex(biomeStart); // reset to copy
                    biomesOut.writeByte(BIOME_RAW);
                    biomesOut.writeVarInt(biomeSize);
                    byte[] biomeBytes = new byte[biomeSize];
                    src.readBytes(biomeBytes);
                    biomesOut.writeBytes(biomeBytes);
                }
            }
            return new SectionSplit(toBytes(blocksOut), toBytes(biomesOut));
        } finally {
            blocksOut.release();
            biomesOut.release();
        }
    }

    /**
     * Merges a blocks-only buffer and a compact biome stream back into a vanilla
     * section buffer suitable for {@code LevelChunkSection.read()}.  Both streams
     * are self-delimiting (blocks: header + PalettedContainer; biomes: flag + data),
     * so the merge is count-free: it alternates one blocks section and one biome
     * entry until the blocks buffer is exhausted, then requires the biome buffer
     * to be exhausted too.
     *
     * @param blocksData blocks-only section data
     * @param biomesData compact biome stream
     * @param variant    wire variant controlling the byte layout of the output
     * @return the reconstructed vanilla section buffer as a byte[]
     */
    public static byte[] mergeSectionBuffer(byte[] blocksData, byte[] biomesData, KryptonWireVariant variant) {
        ByteBuf blocksSrc = Unpooled.wrappedBuffer(blocksData);
        ByteBuf biomesSrc = Unpooled.wrappedBuffer(biomesData);
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer(blocksData.length + biomesData.length + 16));

        try {
            while (blocksSrc.isReadable()) {
                // ---- Block data: short(nonEmptyBlockCount) [+ short(fluidCount) on 26.1+]
                //                    + PalettedContainer<BlockState> ----
                int blockStart = blocksSrc.readerIndex();
                blocksSrc.skipBytes(variant.sectionHeaderBytes());
                skipPalettedContainer(blocksSrc, STATES_GLOBAL_THRESHOLD, SECTION_ELEMENTS, variant);
                int blockSize = blocksSrc.readerIndex() - blockStart;
                blocksSrc.readerIndex(blockStart);
                out.writeBytes(blocksSrc, blockSize);

                // ---- Biome data ----
                if (!biomesSrc.isReadable()) {
                    throw new IllegalStateException("Krypton biome stream ended before the block stream");
                }
                byte biomeFlag = biomesSrc.readByte();
                if (biomeFlag == BIOME_SINGLE_VALUE) {
                    int biomeId = VarInt.read(biomesSrc);
                    // Reconstruct single-value PalettedContainer:
                    if (variant.biomeSingleValueTrailingPrefix()) {
                        // ≤ 1.21.4 format: byte(0) + VarInt(biomeId) + VarInt(0) (storage size prefix)
                        out.writeByte(0);
                        out.writeVarInt(biomeId);
                        out.writeVarInt(0);
                    } else {
                        // 1.21.5+ / 26.x: byte(0) + VarInt(biomeId)  (no trailing storage —
                        // writeFixedSizeLongArray with empty array writes nothing.)
                        out.writeByte(0);
                        out.writeVarInt(biomeId);
                    }
                } else {
                    // BIOME_RAW: VarInt(length) + raw bytes
                    int biomeLen = VarInt.read(biomesSrc);
                    out.writeBytes(biomesSrc, biomeLen);
                }
            }
            if (biomesSrc.isReadable()) {
                throw new IllegalStateException("Krypton biome stream has " + biomesSrc.readableBytes()
                        + " trailing bytes after the block stream");
            }
            return toBytes(out);
        } finally {
            blocksSrc.release();
            biomesSrc.release();
            out.release();
        }
    }

    private static byte[] toBytes(FriendlyByteBuf buf) {
        byte[] result = new byte[buf.readableBytes()];
        buf.readBytes(result);
        return result;
    }
}
