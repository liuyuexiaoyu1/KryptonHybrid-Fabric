package com.xinian.KryptonHybrid.client;

import com.xinian.KryptonHybrid.KryptonFabricConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class KryptonHybridClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KryptonFabricConfig.load();
        ZstdUtil.reloadDictionary();
        KryptonSharedBootstrap.run(true);

        ClientConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.responseSender().sendPacket(KryptonHelloPayload.current())));

        ClientPlayNetworking.registerGlobalReceiver(StatsSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> KryptonStatsClientController.receiveSnapshot(payload)));

        KryptonStatsClientController.init();
    }
}
