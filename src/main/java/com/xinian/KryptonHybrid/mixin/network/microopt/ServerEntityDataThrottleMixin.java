package com.xinian.KryptonHybrid.mixin.network.microopt;

import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder for the 1.20 entity-data throttle.
 *
 * <p>Forge 1.19.2 uses {@code SynchedEntityData.DataItem} and does not expose the
 * tracked-data field used by the 1.20 implementation, so the mixin is not listed
 * in the 1.19.2 mixin config.</p>
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityDataThrottleMixin {}
