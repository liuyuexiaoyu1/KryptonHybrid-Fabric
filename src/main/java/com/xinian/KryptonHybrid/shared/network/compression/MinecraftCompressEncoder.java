package com.xinian.KryptonHybrid.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//#if MC >= 12111
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import com.xinian.KryptonHybrid.shared.network.broadcast.BroadcastSerializationCache;
import com.xinian.KryptonHybrid.shared.network.stats.NetworkTrafficStats;

public class MinecraftCompressEncoder extends MessageToByteEncoder<ByteBuf> {

  private int threshold;
  private final VelocityCompressor compressor;

  public MinecraftCompressEncoder(int threshold, VelocityCompressor compressor) {
    this.threshold = threshold;
    this.compressor = compressor;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
    // Send-side check: this connection received a Zstd frame, so the peer is
    // Krypton-modded — replace this zlib encoder with a Zstd one and encode the
    // current packet with Zstd immediately (no one-frame zlib delay).
    if (ctx.channel().attr(ZstdCompressDecoder.KRYPTON_ZSTD_SEEN).get() == Boolean.TRUE) {
      ZstdCompressEncoder zstd = new ZstdCompressEncoder(this.threshold, ZstdUtil.createCompressor());
      ctx.channel().pipeline().replace("compress", "compress", zstd);
      zstd.krypton$encodeTo(ctx, msg, out);
      return;
    }

    FriendlyByteBuf wrappedBuf = new FriendlyByteBuf(out);
    int uncompressed = msg.readableBytes();
    int startWireIndex = out.writerIndex();
    Object currentPacket = BroadcastSerializationCache.pollCurrentPacket();
    Packet<?> packet = (currentPacket instanceof Packet<?> p) ? p : null;
    if (uncompressed < threshold) {
      // Under the threshold, there is nothing to do.
      wrappedBuf.writeVarInt(0);
      out.writeBytes(msg);
    } else {
      wrappedBuf.writeVarInt(uncompressed);
      ByteBuf compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, msg);
      try {
        compressor.deflate(compatibleIn, out);
      } finally {
        compatibleIn.release();
      }
    }
    int wire = out.writerIndex() - startWireIndex;
    NetworkTrafficStats.INSTANCE.recordEncode(uncompressed, wire);
    kryptonRecordWirePacket(packet, wire);
  }

  @Override
  protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect)
      throws Exception {
    // We allocate bytes to be compressed plus 1 byte. This covers two cases:
    //
    // - Compression
    //    According to https://github.com/ebiggers/libdeflate/blob/master/libdeflate.h#L103,
    //    if the data compresses well (and we do not have some pathological case) then the maximum
    //    size the compressed size will ever be is the input size minus one.
    // - Uncompressed
    //    This is fairly obvious - we will then have one more than the uncompressed size.
    int initialBufferSize = msg.readableBytes() + 1;
    return MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, initialBufferSize);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    compressor.close();
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  private static void kryptonRecordWirePacket(Packet<?> packet, int wireBytes) {
    if (packet == null || wireBytes <= 0) return;
    NetworkTrafficStats.INSTANCE.recordPacketWire(
        kryptonResolveKey(packet),
        kryptonResolveModId(packet),
        wireBytes);
  }

  private static String kryptonResolveKey(Packet<?> packet) {
    if (packet instanceof ClientboundCustomPayloadPacket cp) {
//#if MC >= 12111
      Identifier id = cp.payload().type().id();
      //#else
      //$$ ResourceLocation id = cp.payload().type().id();
      //#endif
      return "custom:" + id.getNamespace() + "/" + id.getPath();
    }
    if (packet instanceof ServerboundCustomPayloadPacket sp) {
//#if MC >= 12111
      Identifier id = sp.payload().type().id();
      //#else
      //$$ ResourceLocation id = sp.payload().type().id();
      //#endif
      return "custom:" + id.getNamespace() + "/" + id.getPath();
    }
    return packet.getClass().getSimpleName();
  }

  private static String kryptonResolveModId(Packet<?> packet) {
    if (packet instanceof ClientboundCustomPayloadPacket cp) {
      return cp.payload().type().id().getNamespace();
    }
    if (packet instanceof ServerboundCustomPayloadPacket sp) {
      return sp.payload().type().id().getNamespace();
    }
    String pkg = packet.getClass().getPackageName();
    if (pkg.startsWith("net.minecraft.")) return "minecraft";
    String[] parts = pkg.split("\\.", 4);
    return parts.length >= 3 ? parts[2] : "unknown";
  }
}

