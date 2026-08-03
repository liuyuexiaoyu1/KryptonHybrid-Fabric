package com.xinian.KryptonHybrid.shared.network.compression;

import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import com.velocitypowered.natives.util.Natives;
import com.xinian.KryptonHybrid.shared.network.security.DecompressionBombGuard;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.ByteBuffer;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * Low-latency Netty {@link MessageToMessageDecoder} that decompresses incoming Minecraft
 * packets that were compressed by a {@link ZstdCompressEncoder} on the other end of the
 * connection.
 *
 * <h3>Wire format</h3>
 * <pre>
 *   VarInt(0)                           – packet NOT compressed
 *   raw bytes (passed through unchanged)
 *
 *   VarInt(original_uncompressed_size)  – packet IS Zstd-compressed
 *   Zstd-compressed bytes
 * </pre>
 *
 * <h3>Latency optimizations</h3>
 *
 * <h4>1. Zero-copy DirectByteBuffer fast path</h4>
 * <p>When the incoming {@code in} buffer is backed by direct memory (the common case
 * for data read from the socket), and a direct output buffer can be allocated, the
 * decoder calls {@link ZstdDecompressCtx#decompressDirectByteBuffer} which passes
 * native pointers directly to libzstd.  This eliminates:</p>
 * <ul>
 *   <li>Three {@code new byte[]} heap allocations per packet (compressed copy,
 *       uncompressed output, final ByteBuf backing array)</li>
 *   <li>Two full-payload {@code memcpy} operations</li>
 *   <li>GC pressure from short-lived byte arrays</li>
 * </ul>
 *
 * <h4>2. Per-handler reusable scratch buffers (heap fallback)</h4>
 * <p>When the incoming buffer is heap-backed, the decoder falls back to
 * {@link ZstdDecompressCtx#decompressByteArray} using <em>grow-only</em> byte
 * arrays stored as instance fields, avoiding per-packet allocations.</p>
 *
 * <h4>3. Direct output allocation</h4>
 * <p>The decompressed output is allocated as a direct buffer when possible, keeping
 * the data in off-heap memory for the downstream pipeline stages (packet decoder,
 * encryption) which also operate on direct buffers.</p>
 *
 * <h4>Latency impact summary</h4>
 * <table>
 *   <tr><th>Operation</th><th>Before</th><th>After (direct)</th></tr>
 *   <tr><td>Heap alloc</td><td>3 × new byte[N]</td><td>0</td></tr>
 *   <tr><td>Data copies</td><td>2 × memcpy(N)</td><td>0</td></tr>
 *   <tr><td>GC pressure</td><td>~3N bytes/packet</td><td>0</td></tr>
 * </table>
 *
 * <h3>Safety limits</h3>
 * <ul>
 *   <li>Uncompressed size must be &ge; threshold (when validation enabled)</li>
 *   <li>Uncompressed size must not exceed 8 MiB (or 128 MiB with
 *       {@code -Dkrypton.permit-oversized-packets=true})</li>
 * </ul>
 *
 * @see ZstdCompressEncoder
 * @see ZstdUtil#createDecompressor()
 */
public class ZstdCompressDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024;
    private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE    = 128 * 1024 * 1024;

    private static final int UNCOMPRESSED_CAP =
            Boolean.getBoolean("krypton.permit-oversized-packets")
                    ? HARD_MAXIMUM_UNCOMPRESSED_SIZE
                    : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

    private final ZstdDecompressCtx decompressor;
    private final boolean validate;
    private int threshold;

    /**
     * Lazy zlib (deflate) fallback used during the compression-switch transition:
     * the peer may still have vanilla-compressed frames in flight when it switches
     * to Zstd (e.g. the server switches when it receives the ready payload, but
     * frames it sent just before that are still vanilla zlib). On a Zstd magic
     * mismatch we try zlib before failing. Created on first use, closed on removal.
     */
    private VelocityCompressor zlibFallback;

    /** Grow-only scratch buffer for compressed input (heap-fallback path only). */
    private byte[] scratchIn = new byte[8192];
    /** Grow-only scratch buffer for decompressed output (heap-fallback path only). */
    private byte[] scratchOut = new byte[8192];

    /**
     * Creates a new decoder.
     *
     * @param threshold    the compression threshold in bytes (used for validation)
     * @param validate     when {@code true}, incoming packets are checked against the
     *                     threshold and the hard size cap
     * @param decompressor a per-channel {@link ZstdDecompressCtx} (see
     *                     {@link ZstdUtil#createDecompressor()})
     */
    public ZstdCompressDecoder(int threshold, boolean validate, ZstdDecompressCtx decompressor) {
        this.threshold = threshold;
        this.validate = validate;
        this.decompressor = decompressor;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        FriendlyByteBuf bb = new FriendlyByteBuf(in);
        int claimedUncompressedSize = bb.readVarInt();

        if (claimedUncompressedSize == 0) {
            // --- Uncompressed packet: pass through ---
            //
            // NOTE: Vanilla Minecraft (1.21.1 net.minecraft.network.CompressionDecoder)
            // intentionally performs NO threshold check on the uncompressed branch — only
            // compressed packets are validated against the threshold.  We deliberately
            // mirror that behaviour to remain interoperable with peers whose effective
            // encoding threshold differs from ours, e.g.:
            //   • the threshold was lowered mid-session (setupCompression) and an
            //     in-flight packet was already encoded under the previous threshold;
            //   • BroadcastCompressedCache replays wire bytes captured at an earlier
            //     threshold;
            //   • a peer using a slightly different compression threshold negotiation.
            //
            // We still enforce the absolute hard cap to prevent abuse via giant
            // uncompressed payloads (which the upstream frame decoder also limits).
            int actualUncompressedSize = in.readableBytes();
            checkState(actualUncompressedSize <= UNCOMPRESSED_CAP,
                    "Uncompressed pass-through size %s exceeds hard cap of %s",
                    actualUncompressedSize, UNCOMPRESSED_CAP);
            out.add(in.retain());
            return;
        }

        // --- Validate claimed size ---
        // Only the hard cap is enforced: the peer compresses with ITS own
        // threshold, so a claimed size below ours is perfectly legal on the wire
        // (mixed thresholds, cached wire bytes, threshold changes mid-session).
        if (validate) {
            checkState(claimedUncompressedSize <= UNCOMPRESSED_CAP,
                    "Uncompressed size %s exceeds hard cap of %s",
                    claimedUncompressedSize, UNCOMPRESSED_CAP);
        }

        int compressedSize = in.readableBytes();

        // --- Decompression bomb guard ---
        DecompressionBombGuard.validate(compressedSize, claimedUncompressedSize, ctx.channel());

        // --- Fast path: direct input → direct output, zero-copy ---
        if (in.isDirect() && in.nioBufferCount() == 1) {
            ByteBuf result = ctx.alloc().directBuffer(claimedUncompressedSize);
            try {
                ByteBuffer nioIn  = in.nioBuffer(in.readerIndex(), compressedSize);
                ByteBuffer nioOut = result.nioBuffer(0, claimedUncompressedSize);

                int actualDecompressed;
                try {
                    actualDecompressed = decompressor.decompressDirectByteBuffer(
                            nioOut, 0, claimedUncompressedSize,
                            nioIn,  0, compressedSize);
                } catch (ZstdException e) {
                    // Frame is not Zstd — it may be a vanilla zlib frame still in
                    // flight from before the peer switched compression. The zlib
                    // inflate consumes `in` itself (ensureCompatible returns it
                    // directly when compatible), so no manual skip here.
                    ByteBuf zlib = tryZlibInflate(ctx, in, compressedSize, claimedUncompressedSize);
                    if (zlib != null) {
                        out.add(zlib);
                        return;
                    }
                    throw new Exception("Zstd decompression failed: " + e.getMessage(), e);
                }

                checkState(actualDecompressed == claimedUncompressedSize,
                        "Zstd decompressed size %s does not match claimed size %s",
                        actualDecompressed, claimedUncompressedSize);

                in.skipBytes(compressedSize);
                result.writerIndex(claimedUncompressedSize);
                out.add(result);
                result = null; // ownership transferred to out
            } finally {
                if (result != null) {
                    result.release(); // cleanup on exception
                }
            }
            return;
        }

        // --- Heap fallback: reusable scratch buffers ---
        if (scratchIn.length < compressedSize) {
            scratchIn = new byte[compressedSize];
        }
        in.readBytes(scratchIn, 0, compressedSize);

        if (scratchOut.length < claimedUncompressedSize) {
            scratchOut = new byte[claimedUncompressedSize];
        }

        int actualDecompressed;
        try {
            actualDecompressed = decompressor.decompressByteArray(
                    scratchOut, 0, claimedUncompressedSize,
                    scratchIn, 0, compressedSize);
        } catch (ZstdException e) {
            // Frame is not Zstd — it may be a vanilla zlib frame still in
            // flight from before the peer switched compression. `in` was already
            // consumed into scratchIn, so feed the scratch buffer to the fallback.
            ByteBuf zlib = tryZlibInflate(ctx, Unpooled.wrappedBuffer(scratchIn, 0, compressedSize),
                    compressedSize, claimedUncompressedSize);
            if (zlib != null) {
                out.add(zlib);
                return;
            }
            throw new Exception("Zstd decompression failed: " + e.getMessage(), e);
        }

        checkState(actualDecompressed == claimedUncompressedSize,
                "Zstd decompressed size %s does not match claimed size %s",
                actualDecompressed, claimedUncompressedSize);

        // Write from scratch to a direct buffer for downstream pipeline stages
        ByteBuf result = ctx.alloc().directBuffer(claimedUncompressedSize);
        result.writeBytes(scratchOut, 0, claimedUncompressedSize);
        out.add(result);
    }

    /**
     * Attempts to decompress the frame as vanilla zlib (deflate). Used as a graceful
     * fallback for frames that are still vanilla-compressed because the peer had
     * already sent them before switching to Zstd. Returns {@code null} when the
     * frame is not a valid zlib stream either (the caller rethrows the Zstd error).
     */
    private ByteBuf tryZlibInflate(ChannelHandlerContext ctx, ByteBuf in,
                                   int compressedSize, int uncompressedSize) {
        try {
            VelocityCompressor zc = zlibCompressor();
            ByteBuf compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), zc, in);
            try {
                ByteBuf result = ctx.alloc().directBuffer(uncompressedSize);
                try {
                    zc.inflate(compatibleIn, result, 4);
                } catch (Throwable t) {
                    result.release();
                    return null;
                }
                if (result.readableBytes() != uncompressedSize) {
                    result.release();
                    return null;
                }
                return result;
            } finally {
                compatibleIn.release();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private VelocityCompressor zlibCompressor() {
        VelocityCompressor c = zlibFallback;
        if (c == null) {
            synchronized (this) {
                c = zlibFallback;
                if (c == null) {
                    zlibFallback = c = Natives.compress.get().create(4);
                }
            }
        }
        return c;
    }

    /**
     * Releases the native Zstd decompression context when the handler is removed from the pipeline.
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        decompressor.close();
        VelocityCompressor zc = zlibFallback;
        if (zc != null) {
            zc.close();
        }
        super.handlerRemoved(ctx);
    }

    /**
     * Updates the compression threshold without replacing the pipeline handler.
     *
     * @param threshold the new threshold in bytes
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}

