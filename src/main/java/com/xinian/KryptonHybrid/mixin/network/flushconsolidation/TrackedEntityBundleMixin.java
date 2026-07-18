package com.xinian.KryptonHybrid.mixin.network.flushconsolidation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xinian.KryptonHybrid.shared.network.broadcast.EntityBundleCollector;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps per-player packet sends inside {@code ChunkMap.TrackedEntity.sendToTrackingPlayers()}
 * and {@code sendToTrackingPlayersFiltered()} to route through the {@link EntityBundleCollector}
 * during the entity tracking tick, enabling automatic packet bundling.
 *
 * @see EntityBundleCollector
 * @see ChunkMapFlushMixin
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackedEntityBundleMixin {

    @WrapOperation(
            method = "sendToTrackingPlayersFiltered",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerConnection;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void krypton$collectForBundleFiltered(ServerPlayerConnection conn, Packet<?> packet, Operation<Void> original) {
        if (!EntityBundleCollector.collect(conn, packet)) {
            original.call(conn, packet);
        }
    }

    @WrapOperation(
            method = "sendToTrackingPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerConnection;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void krypton$collectForBundle(ServerPlayerConnection conn, Packet<?> packet, Operation<Void> original) {
        if (!EntityBundleCollector.collect(conn, packet)) {
            original.call(conn, packet);
        }
    }
}
