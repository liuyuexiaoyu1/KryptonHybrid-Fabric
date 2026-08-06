package com.xinian.KryptonHybrid.mixin.via;

import com.viaversion.viaversion.api.connection.UserConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes ViaFabric's per-connection {@code UserConnection} (the version it is
 * negotiating) through the {@code via-decoder} pipeline handler.
 *
 * <p>{@code @Pseudo} + string target: ViaFabric is optional — when the mod is
 * not installed the target class does not exist and this accessor is skipped
 * without failing the mixin application.</p>
 */
@Pseudo
@Mixin(targets = "com.viaversion.fabric.common.handler.FabricDecodeHandler")
public interface FabricDecodeHandlerAccessor {

    @Shadow
    UserConnection getInfo();
}
