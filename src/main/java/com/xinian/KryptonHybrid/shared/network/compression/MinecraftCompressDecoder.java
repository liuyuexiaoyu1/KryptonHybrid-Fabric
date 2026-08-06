package com.xinian.KryptonHybrid.shared.network.compression;

import com.github.luben.zstd.Zstd;
import com.velocitypowered.natives.compression.VelocityCompressor;
import com.xinian.KryptonHybrid.shared.network.security.DecompressionBombGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;

/**
 * Decompresses a Minecraft packet.
 */
public class MinecraftCompressDecoder extends MessageToMessageDecoder<ByteBuf> {

    /** Zstd frame magic: {@code 0x28 0xB5 0x2F 0xFD}. */
    private static final int ZSTD_MAGIC = 0x28B52FFD;

    private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024; // 8MiB
    private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024; // 128MiB

    private static final int UNCOMPRESSED_CAP =
            Boolean.getBoolean("krypton.permit-oversized-packets")
                    ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;
    private final VelocityCompressor compressor;
    private final boolean validate;
    private int threshold;

    public MinecraftCompressDecoder(int threshold, boolean validate, VelocityCompressor compressor) {
        this.threshold = threshold;
        this.compressor = compressor;
        this.validate = validate;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Snapshot the reader index before reading the VarInt so we can rewind
        // if the data is already decompressed (no Zlib header).
        int startReaderIndex = in.readerIndex();

        FriendlyByteBuf bb = new FriendlyByteBuf(in);
        int claimedUncompressedSize = bb.readVarInt();

        // claimedUncompressedSize == 0 → uncompressed pass-through.
        if (claimedUncompressedSize == 0) {
            checkState(in.readableBytes() <= UNCOMPRESSED_CAP,
                    "Uncompressed pass-through size %s exceeds hard cap of %s",
                    in.readableBytes(), UNCOMPRESSED_CAP);
            out.add(in.retain());
            return;
        }

        // Zstd frame (Krypton Hybrid): a downgraded connection may still receive
        // Zstd frames (e.g. proxy passthrough). Must be checked before the zlib
        // header test below, which would otherwise treat the Zstd magic as
        // "already decompressed" and forward raw Zstd bytes to the packet decoder.
        if (in.readableBytes() >= 4 && in.getInt(in.readerIndex()) == ZSTD_MAGIC) {
            if (validate) {
                checkState(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
                        + " threshold %s", claimedUncompressedSize, threshold);
                checkState(claimedUncompressedSize <= UNCOMPRESSED_CAP,
                        "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
                        UNCOMPRESSED_CAP);
            }
            byte[] compressed = new byte[in.readableBytes()];
            in.readBytes(compressed);
            byte[] uncompressed = Zstd.decompress(compressed, claimedUncompressedSize);
            ByteBuf result = ctx.alloc().buffer(uncompressed.length);
            result.writeBytes(uncompressed);
            out.add(result);
            return;
        }

        // The data after the VarInt should start with a valid Zlib header (0x78).
        // If not, the data was already decompressed by another pipeline handler
        // (e.g. ViaFabricPlus).  Rewind and pass through unchanged.
        if (in.readableBytes() >= 2) {
            int b1 = in.getByte(in.readerIndex()) & 0xFF;
            if (b1 != 0x78) {
                in.readerIndex(startReaderIndex); // rewind
                out.add(in.retain());
                return;
            }
        }

        if (validate) {
            checkState(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
                    + " threshold %s", claimedUncompressedSize, threshold);
            checkState(claimedUncompressedSize <= UNCOMPRESSED_CAP,
                    "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
                    UNCOMPRESSED_CAP);
        }

        // --- Decompression bomb guard ---
        DecompressionBombGuard.validate(in.readableBytes(), claimedUncompressedSize, ctx.channel());

        ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
        ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
        try {
            compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
            out.add(uncompressed);
        } catch (Exception e) {
            uncompressed.release();
            throw e;
        } finally {
            compatibleIn.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        compressor.close();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}

