package com.xinian.KryptonHybrid.mixin.network.chunk;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundLevelChunkPacketData.BlockEntityInfo.class)
public interface BlockEntityInfoAccessor {
    @Invoker("<init>")
    static ClientboundLevelChunkPacketData.BlockEntityInfo krypton$create(FriendlyByteBuf buf) {
        throw new AssertionError();
    }

    @Invoker("write")
    void krypton$write(FriendlyByteBuf buf);
}
