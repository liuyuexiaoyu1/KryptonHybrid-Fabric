package com.xinian.KryptonHybrid.client;

import com.xinian.KryptonHybrid.KryptonFabricConfig;
import com.xinian.KryptonHybrid.client.overlay.KryptonHudOverlay;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonDictPayload;
import com.xinian.KryptonHybrid.shared.network.pipeline.ZstdUpgradeExtension;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.payload.KryptonCompressionReadyPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//#else
//$$ import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//#endif

public final class KryptonHybridClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KryptonFabricConfig.load();
        ZstdUtil.reloadDictionary();
        KryptonSharedBootstrap.run(true);

        ClientConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.responseSender().sendPacket(KryptonHelloPayload.current())));

        // Apply the server's Zstd dictionary before compression is set up, then
        // tell the server we are ready and enable compression on our side:
        //   1. apply dictionary
        //   2. send KryptonCompressionReadyPayload (uncompressed)
        //   3. upgrade this connection to Zstd
        // The server enables compression upon receiving the ready payload, so both
        // sides switch together without a race.
        ClientConfigurationNetworking.registerGlobalReceiver(KryptonDictPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ZstdUtil.applyReceivedDictionary(payload.dictionaryBytes());
                    context.responseSender().sendPacket(KryptonCompressionReadyPayload.INSTANCE);
                    ((ZstdUpgradeExtension) context.client().getConnection().getConnection()).krypton$upgradeToZstd(0, true);
                }));

        // Auto-train updates pushed to already-connected clients: persist the new
        // dictionary so reconnects pick it up (the current session keeps its
        // compression context).
        ClientPlayNetworking.registerGlobalReceiver(KryptonDictPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ZstdUtil.applyReceivedDictionary(payload.dictionaryBytes())));

        ClientPlayNetworking.registerGlobalReceiver(StatsSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> KryptonStatsClientController.receiveSnapshot(payload)));

        KryptonStatsClientController.init();
        registerHudOverlay();
    }

    private static void registerHudOverlay() {
        //#if MC >= 26.1
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("krypton_hybrid", "stats_hud"),
                KryptonHudOverlay::extractRenderState);
        //#else
        //$$ HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> KryptonHudOverlay.render(guiGraphics));
        //#endif
    }
}
