package com.xinian.KryptonHybrid.mixin.network.flushconsolidation;

import com.xinian.KryptonHybrid.shared.network.motion.MotionDeltaCache;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates {@link MotionDeltaCache} entries when an entity leaves a player's
 * tracking range or despawns.
 *
 * <p>Vanilla sends the remove packet from {@code ServerEntity.removePairing}
 * straight to {@code player.connection.send(...)} — it never flows through the
 * entity-bundle collectors that {@code MotionDeltaCache.filter} processes, so
 * without this hook the per-player motion/teleport state maps would accumulate
 * one entry per entity ever seen by the player, forever (a memory leak on
 * entity-heavy servers).  Both untrack paths (range exit and full removal)
 * funnel through this method, so a single injection covers them all.</p>
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityRemovePairingMixin {

    @Final
    @Shadow
    private Entity entity;

    @Inject(method = "removePairing", at = @At("HEAD"))
    private void krypton$invalidateMotionCache(ServerPlayer player, CallbackInfo ci) {
        MotionDeltaCache.invalidateFor(player.connection, this.entity.getId());
    }
}
