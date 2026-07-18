package com.xinian.KryptonHybrid.mixin.network.blockentity;

import com.mojang.logging.LogUtils;
import com.xinian.KryptonHybrid.shared.network.blockentity.BlockEntityDeltaCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin that detects Krypton delta-encoded
 * {@link ClientboundBlockEntityDataPacket}s and merges the delta tag into
 * the block entity's existing data instead of replacing it wholesale.
 */
@Mixin(ClientPacketListener.class)
public class ClientBlockEntityDeltaMixin {

    @Inject(method = "handleBlockEntityData", at = @At("HEAD"), cancellable = true)
    private void krypton$mergeDelta(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        CompoundTag tag = packet.getTag();
        if (tag == null || !tag.contains(BlockEntityDeltaCache.DELTA_MARKER_KEY)) {
            return;
        }

        if (!tag.getBooleanOr(BlockEntityDeltaCache.DELTA_MARKER_KEY, false)) {
            return;
        }

        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        BlockPos pos = packet.getPos();
        var optionalBe = level.getBlockEntity(pos, packet.getType());
        if (optionalBe.isEmpty()) {
            return;
        }

        BlockEntity be = optionalBe.get();
        HolderLookup.Provider registries = level.registryAccess();

        CompoundTag currentData = be.saveWithoutMetadata(registries);

        if (tag.contains(BlockEntityDeltaCache.REMOVED_KEYS_KEY)) {
            String removedCsv = tag.getStringOr(BlockEntityDeltaCache.REMOVED_KEYS_KEY, "");
            for (String key : removedCsv.split(",")) {
                if (!key.isEmpty()) {
                    currentData.remove(key);
                }
            }
        }

        for (String key : tag.keySet()) {
            if (key.equals(BlockEntityDeltaCache.DELTA_MARKER_KEY)
                    || key.equals(BlockEntityDeltaCache.REMOVED_KEYS_KEY)) {
                continue;
            }
            net.minecraft.nbt.Tag value = tag.get(key);
            if (value != null) {
                currentData.put(key, value.copy());
            }
        }

        // Load the merged tag back using the new ValueInput API
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), LogUtils.getLogger())) {
            be.loadWithComponents(TagValueInput.create(reporter, registries, currentData));
        }
    }
}
