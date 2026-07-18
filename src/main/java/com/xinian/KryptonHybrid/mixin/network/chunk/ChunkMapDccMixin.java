package com.xinian.KryptonHybrid.mixin.network.chunk;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import com.xinian.KryptonHybrid.shared.network.chunk.DelayedChunkCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements the Delayed Chunk Cache (DCC) optimization for Krypton Hybrid.
 *
 * <p>Intercepts the {@link ChunkTrackingView#difference} call inside
 * {@link ChunkMap#applyChunkTrackingView} and wraps the enter/leave callbacks
 * with DCC cache logic. {@code require = 0} makes this hook optional — VMP's
 * {@code MixinTACSCancelSending} {@code @Overwrite}s {@code applyChunkTrackingView}
 * to empty, in which case this injection simply does not apply and DCC is
 * effectively disabled (the server still works, just without the cache).</p>
 */
@Mixin(value = ChunkMap.class, priority = 1100)
public abstract class ChunkMapDccMixin {

    @Shadow @Final
    ServerLevel level;

    @Shadow
    private void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos) {
        throw new AssertionError();
    }

    /**
     * Intercepts the {@code ChunkTrackingView.difference()} call inside
     * {@code applyChunkTrackingView} and wraps the enter/leave callbacks
     * with DCC logic.  Optional ({@code require = 0}) — VMP's
     * {@code @Overwrite} may remove this call site entirely.
     */
    @Inject(
            method = "applyChunkTrackingView",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkTrackingView;difference(Lnet/minecraft/server/level/ChunkTrackingView;Lnet/minecraft/server/level/ChunkTrackingView;Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V"
            ),
            cancellable = true,
            require = 0
    )
    private void krypton$applyDccToTrackingDiff(
            ServerPlayer player,
            ChunkTrackingView next,
            CallbackInfo ci,
            @Local(name = "previous") ChunkTrackingView previous
    ) {
        ci.cancel();
        ResourceKey<Level> dim = this.level.dimension();
        ChunkTrackingView.difference(
                previous,
                next,
                pos -> {
                    if (!DelayedChunkCache.INSTANCE.onChunkEnter(player, dim, pos)) {
                        markChunkPendingToSend(player, pos);
                    }
                },
                pos -> {
                    if (player.connection.chunkSender.isPending(pos.pack())) {
                        krypton$dropChunkNow(player, pos);
                        return;
                    }
                    if (!DelayedChunkCache.INSTANCE.onChunkLeave(player, dim, pos)) {
                        krypton$dropChunkNow(player, pos);
                    }
                }
        );
    }

    @Unique
    private static void krypton$dropChunkNow(ServerPlayer player, ChunkPos chunkPos) {
        player.connection.chunkSender.dropChunk(player, chunkPos);
    }

    /** Evicts stale DCC cache entries and performs deferred untrackChunk calls. */
    @Inject(method = "tick()V", at = @At("RETURN"))
    private void tick$evictDccCache(CallbackInfo ci) {
        DelayedChunkCache.INSTANCE.tick(this.level.dimension(), this.level.players(), ChunkMapDccMixin::dropChunkDeferred);
    }

    @Unique
    private static void dropChunkDeferred(ServerPlayer player, ChunkPos chunkPos) {
        player.connection.chunkSender.dropChunk(player, chunkPos);
    }
}
