package com.xinian.KryptonHybrid.mixin.network.pipeline.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import com.xinian.KryptonHybrid.shared.misc.KryptonPipelineEvent;
import com.xinian.KryptonHybrid.shared.network.compression.MinecraftCompressDecoder;
import com.xinian.KryptonHybrid.shared.network.compression.MinecraftCompressEncoder;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdCompressDecoder;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdCompressEncoder;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.pipeline.ZstdUpgradeExtension;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin implements ZstdUpgradeExtension {
    @Shadow
    private Channel channel;

    @Unique
    private int krypton$compressionThreshold;

    @Unique
    private boolean krypton$compressionValidate;

    @Unique
    public int krypton$getCompressionThreshold() { return krypton$compressionThreshold; }

    @Unique
    public boolean krypton$getCompressionValidate() { return krypton$compressionValidate; }

    /**
     * Upgrades the pipeline from Velocity/Minecraft compression to Zstd.
     * Called after the Krypton hello handshake confirms Zstd support.
     *
     * <p>The decoder check mirrors the "upgrade the decoder when the hello
     * arrives" rule: a setupCompression half-upgrade already installs a Zstd
     * decoder (with zlib fallback), so there is nothing to do here — the encoder
     * is upgraded lazily when a Zstd frame is received. A transferred connection
     * (fresh {@code Connection} without a login-phase setupCompression) has no
     * decompress handler at all, so the Zstd layer is installed here.</p>
     */
    @Unique
    public void krypton$upgradeToZstd(int compressionThreshold, boolean validate) {
        if (channel.pipeline().get("decompress") instanceof ZstdCompressDecoder) {
            return; // decoder already Zstd (half-upgrade or previous upgrade)
        }

        ZstdCompressEncoder zstdEncoder =
                new ZstdCompressEncoder(compressionThreshold, ZstdUtil.createCompressor());
        ZstdCompressDecoder zstdDecoder =
                new ZstdCompressDecoder(compressionThreshold, validate, ZstdUtil.createDecompressor());

        // Replace existing compressors atomically. Never remove-then-add:
        // "decoder"/"encoder" are renamed asynchronously on 26.x (they may still
        // be "inbound_config"/"outbound_config" placeholders here), so an add
        // failure after a remove would leave the connection without any
        // compressor. "splitter"/"prepender" always exist (the same anchors
        // vanilla 26.x setupCompression uses), so they cover transferred
        // connections that have no compression layer at all.
        if (channel.pipeline().get("decompress") != null) {
            channel.pipeline().replace("decompress", "decompress", zstdDecoder);
        } else {
            channel.pipeline().addAfter("splitter", "decompress", zstdDecoder);
        }
        if (channel.pipeline().get("compress") != null) {
            channel.pipeline().replace("compress", "compress", zstdEncoder);
        } else {
            channel.pipeline().addAfter("prepender", "compress", zstdEncoder);
        }

        channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
    }

    /**
     * Decoder-only Zstd upgrade, used by the client: installs the Zstd decoder
     * (so inbound Zstd frames from a direct server are decoded), while the
     * outbound encoder stays on vanilla zlib and upgrades lazily via the
     * KRYPTON_ZSTD_SEEN frame signal.  Through a proxy the client never
     * receives Zstd frames, so it never upgrades its encoder — keeping the
     * client↔proxy segment vanilla-compatible.
     */
    @Unique
    public void krypton$upgradeDecoderToZstd(int compressionThreshold, boolean validate) {
        if (channel.pipeline().get("decompress") instanceof ZstdCompressDecoder) {
            return; // already Zstd
        }
        ZstdCompressDecoder zstdDecoder =
                new ZstdCompressDecoder(compressionThreshold, validate, ZstdUtil.createDecompressor());
        if (channel.pipeline().get("decompress") != null) {
            channel.pipeline().replace("decompress", "decompress", zstdDecoder);
        } else {
            channel.pipeline().addAfter("splitter", "decompress", zstdDecoder);
        }
    }

    @Inject(method = "setupCompression", at = @At("HEAD"))
    public void setCompressionThreshold(int compressionThreshold, boolean validate, CallbackInfo ci) {
        this.krypton$compressionThreshold = compressionThreshold;
        this.krypton$compressionValidate = validate;
    }

    /** Replaces vanilla compressors with Velocity-native ones after vanilla setup is done. */
    @Inject(method = "setupCompression", at = @At("RETURN"))
    public void postSetupCompression(int compressionThreshold, boolean validate, CallbackInfo ci) {
        if (compressionThreshold < 0) return;

        // Already upgraded to Krypton compression during the configuration handshake
        // (krypton$upgradeToZstd): keep the installed compressors untouched.
        Object existingCompress = this.channel.pipeline().get("compress");
        if (existingCompress instanceof ZstdCompressEncoder || existingCompress instanceof MinecraftCompressEncoder) {
            return;
        }

        VelocityCompressor compressor = Natives.compress.get().create(4);

        MinecraftCompressEncoder encoder =
                new MinecraftCompressEncoder(compressionThreshold, compressor);

        MessageToMessageDecoder<?> decoder;
        if (ZstdUtil.isEnabled()) {
            decoder = new ZstdCompressDecoder(compressionThreshold, validate, ZstdUtil.createDecompressor());
        } else {
            decoder = new MinecraftCompressDecoder(compressionThreshold, validate, compressor);
        }

        // Replace the vanilla compressors atomically (vanilla setupCompression
        // just installed "decompress"/"compress"). Anchoring on
        // "splitter"/"prepender" instead of "decoder"/"encoder" avoids the
        // async-rename trap on 26.x, and replace never leaves the pipeline
        // without a compressor.
        if (this.channel.pipeline().get("decompress") != null) {
            this.channel.pipeline().replace("decompress", "decompress", decoder);
        } else {
            this.channel.pipeline().addAfter("splitter", "decompress", decoder);
        }
        if (this.channel.pipeline().get("compress") != null) {
            this.channel.pipeline().replace("compress", "compress", encoder);
        } else {
            this.channel.pipeline().addAfter("prepender", "compress", encoder);
        }

        this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
    }
}
