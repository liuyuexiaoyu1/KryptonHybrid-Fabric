package com.xinian.KryptonHybrid.shared.network.codec;

/**
 * Wire-format variant for Krypton's chunk/light encodings.
 *
 * <p>The variants capture every byte-layout difference of the vanilla
 * <em>section buffer</em> across the versions Krypton supports.  The custom
 * wire only ever flows between two Krypton runtimes on a same-version
 * connection (the via-compatibility rule drops every custom feature when a
 * Via mod translates between versions), so the packet-object-level codec
 * always produces and consumes its own {@link #runtimeVariant() runtime
 * variant}.  The wire still carries a variant-tag byte after the
 * {@code 0x4B} marker, making the format self-describing and letting the read
 * side verify the peer wrote the expected layout.</p>
 *
 * <table>
 *   <caption>Section-buffer layout per variant</caption>
 *   <tr><th>Variant</th><th>Versions</th><th>Section header</th>
 *       <th>Storage layout</th><th>Chunk heightmaps</th></tr>
 *   <tr><td>{@link #OLD}</td><td>1.21.1–1.21.4</td>
 *       <td>short nonEmptyBlockCount</td><td>VarInt length prefix + longs</td>
 *       <td>NBT {@code CompoundTag} (serialization keys)</td></tr>
 *   <tr><td>{@link #MID}</td><td>1.21.5–1.21.11</td>
 *       <td>short nonEmptyBlockCount</td><td>fixed-size longs (no prefix)</td>
 *       <td>Map: VarInt count + (VarInt typeId + VarInt len + longs)</td></tr>
 *   <tr><td>{@link #NEW}</td><td>26.1+</td>
 *       <td>short nonEmptyBlockCount + short fluidCount</td><td>fixed-size longs (no prefix)</td>
 *       <td>Map: VarInt count + (VarInt typeId + VarInt len + longs)</td></tr>
 * </table>
 *
 * <p>Block-entity lists ({@code byte packedXZ + short y + VarInt type + NBT})
 * and the light-update payload (4 BitSets + two {@code byte[]} lists of 2048
 * bytes) are byte-identical across all supported versions and need no variant
 * handling.</p>
 */
public enum KryptonWireVariant {

    /** 1.21.1–1.21.4: NBT heightmaps, 2-byte section header, VarInt-prefixed storage. */
    OLD(0),
    /** 1.21.5–1.21.11: Map heightmaps, 2-byte section header, fixed-size storage. */
    MID(1),
    /** 26.1+: Map heightmaps, 4-byte section header (nonEmpty + fluidCount), fixed-size storage. */
    NEW(2),
    /** Unknown tag: the wire cannot be decoded. */
    UNKNOWN(-1);

    /** Tag byte written into the Krypton wire format after the {@code 0x4B} marker. */
    private final byte tag;

    KryptonWireVariant(int tag) {
        this.tag = (byte) tag;
    }

    /** Tag byte for the Krypton wire format. */
    public byte tag() {
        return this.tag;
    }

    public static KryptonWireVariant fromTag(byte tag) {
        return switch (tag) {
            case 0 -> OLD;
            case 1 -> MID;
            case 2 -> NEW;
            default -> UNKNOWN;
        };
    }

    /**
     * The variant of the local runtime version, resolved at compile time.
     * Used by the packet-object-level (same-version) codec paths, where the
     * connection variant always equals the runtime variant.
     */
    public static KryptonWireVariant runtimeVariant() {
        //#if MC >= 26.1
        return NEW;
        //#elseif MC >= 12105
        //$$ return MID;
        //#else
        //$$ return OLD;
        //#endif
    }

    // ======================================================================
    //  Section-buffer format flags
    // ======================================================================

    /** Bytes of the per-section header (2 = short nonEmpty; 4 = + short fluidCount). */
    public int sectionHeaderBytes() {
        return this == NEW ? 4 : 2;
    }

    /** True when the paletted-container storage carries a VarInt length prefix (≤ 1.21.4). */
    public boolean storagePrefixed() {
        return this == OLD;
    }

    /** True when chunk heightmaps travel as an NBT {@code CompoundTag} (≤ 1.21.4). */
    public boolean heightmapsAsNbt() {
        return this == OLD;
    }

    /** True when a single-value biome section ends with a VarInt storage-size prefix (≤ 1.21.4). */
    public boolean biomeSingleValueTrailingPrefix() {
        return this == OLD;
    }
}
