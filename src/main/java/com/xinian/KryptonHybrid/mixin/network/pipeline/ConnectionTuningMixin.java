package com.xinian.KryptonHybrid.mixin.network.pipeline;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tunes the TCP send/receive buffers and the Netty write-buffer watermarks
 * for every connection.
 *
 * <p>Vanilla only sets {@code TCP_NODELAY}; the default Netty write watermark
 * (32 KiB low / 64 KiB high) makes the channel go un-writable almost
 * immediately under high throughput, causing back-pressure latency spikes.
 * Larger buffers + watermarks keep packets flowing under load; on Windows the
 * socket buffers can be raised after connect as well.</p>
 *
 * <p>The watermarks reuse the existing {@code securityWriteWatermarkLow/High}
 * configuration values (also used by {@code NettyResourceGuard} for forced
 * disconnects).</p>
 */
@Mixin(Connection.class)
public abstract class ConnectionTuningMixin {

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void krypton$tuneChannel(ChannelHandlerContext ctx, CallbackInfo ci) {
        if (!KryptonConfig.microBatchFlushEnabled) return;
        Channel channel = ctx.channel();
        try {
            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
            channel.config().setOption(ChannelOption.SO_SNDBUF, 1 << 20); // 1 MiB
            channel.config().setOption(ChannelOption.SO_RCVBUF, 1 << 20); // 1 MiB
            channel.config().setOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                    new WriteBufferWaterMark(
                            KryptonConfig.securityWriteWatermarkLow,
                            KryptonConfig.securityWriteWatermarkHigh));
        } catch (Throwable ignored) {
            // Option not supported on this transport — keep the default.
        }
    }
}
