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
     */
    @Unique
    public void krypton$upgradeToZstd(int compressionThreshold, boolean validate) {
        Object existingDecoder = channel.pipeline().get("decompress");
        Object existingEncoder = channel.pipeline().get("compress");

        // Remove existing Velocity/Minecraft handlers
        if (existingDecoder != null) channel.pipeline().remove("decompress");
        if (existingEncoder != null) channel.pipeline().remove("compress");

        ZstdCompressEncoder zstdEncoder =
                new ZstdCompressEncoder(compressionThreshold, ZstdUtil.createCompressor());
        ZstdCompressDecoder zstdDecoder =
                new ZstdCompressDecoder(compressionThreshold, validate, ZstdUtil.createDecompressor());

        // "decoder"/"encoder" are not guaranteed to exist: on 26.x the serialization
        // handlers start as "inbound_config"/"outbound_config" placeholders that are
        // renamed asynchronously. "splitter"/"prepender" always exist (same anchors
        // vanilla setupCompression uses), so fall back to them.
        if (channel.pipeline().get("decoder") != null) {
            channel.pipeline().addBefore("decoder", "decompress", zstdDecoder);
        } else {
            channel.pipeline().addAfter("splitter", "decompress", zstdDecoder);
        }
        if (channel.pipeline().get("encoder") != null) {
            channel.pipeline().addBefore("encoder", "compress", zstdEncoder);
        } else {
            channel.pipeline().addAfter("prepender", "compress", zstdEncoder);
        }

        channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
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

        // Replace the vanilla compressors with Velocity-native ones.
        // Remove vanilla handlers first.
        if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
            this.channel.pipeline().remove("decompress");
        }
        if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
            this.channel.pipeline().remove("compress");
        }

        VelocityCompressor compressor = Natives.compress.get().create(4);

        // Half upgrade (login-phase wire Zstd): the ENCODER stays zlib-compatible
        // (MinecraftCompressEncoder) until the peer's configuration-phase hello
        // upgrades it to Zstd (see krypton$upgradeToZstd), while the DECODER is the
        // Zstd one right away — its built-in zlib fallback also accepts vanilla
        // peers, so single-side installs and cross-server legs keep working.
        MinecraftCompressEncoder encoder =
                new MinecraftCompressEncoder(compressionThreshold, compressor);
        ZstdCompressDecoder decoder =
                new ZstdCompressDecoder(compressionThreshold, validate, ZstdUtil.createDecompressor());

        // Same anchors as krypton$upgradeToZstd: "decoder"/"encoder" may not exist
        // yet on 26.x (placeholder names), "splitter"/"prepender" always do.
        if (this.channel.pipeline().get("decoder") != null) {
            this.channel.pipeline().addBefore("decoder", "decompress", decoder);
        } else {
            this.channel.pipeline().addAfter("splitter", "decompress", decoder);
        }
        if (this.channel.pipeline().get("encoder") != null) {
            this.channel.pipeline().addBefore("encoder", "compress", encoder);
        } else {
            this.channel.pipeline().addAfter("prepender", "compress", encoder);
        }

        this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
    }
}
