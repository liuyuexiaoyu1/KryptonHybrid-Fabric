package com.xinian.KryptonHybrid.client;

import com.xinian.KryptonHybrid.KryptonFabricConfig;
import com.xinian.KryptonHybrid.KryptonHybrid;
import com.xinian.KryptonHybrid.client.overlay.KryptonHudOverlay;
import com.xinian.KryptonHybrid.mixin.network.pipeline.ClientCommonPacketListenerAccessor;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonDictPayload;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.pipeline.ZstdUpgradeExtension;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.Connection;
//#if MC <= 12105
//$$ import net.minecraft.network.PacketSendListener;
//#endif
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//#else
//$$ import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//#endif

public final class KryptonHybridClient implements ClientModInitializer {
    /**
     * True once this (never-recreated) connection has been upgraded to wire-level
     * Zstd at the first configuration phase. Server transfers keep the connection,
     * so the upgrade must only happen once.
     */
    private static volatile boolean clientCompressionUpgraded;

    @Override
    public void onInitializeClient() {
        KryptonFabricConfig.load();
        ZstdUtil.reloadDictionary();
        KryptonSharedBootstrap.run(true);

        // Configuration-phase capability: the server (or proxy) advertises
        // wire-level Zstd support. Respond so the server knows this client is
        // modded, and upgrade this connection to Zstd once (it persists across
        // server transfers — later configuration hellos simply skip the upgrade).
        ClientConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    //#if MC >= 26.1
                    Connection conn = ((ClientCommonPacketListenerAccessor) context.packetListener()).krypton$getConnection();
                    //#else
                    //$$ Connection conn = ((ClientCommonPacketListenerAccessor) context.networkHandler()).krypton$getConnection();
                    //#endif
                    if (conn == null) {
                        return;
                    }
                    // Send the hello response out first (with the old encoder), and
                    // only apply the wire-Zstd upgrade after the write completes.
                    ServerboundCustomPayloadPacket hello =
                            new ServerboundCustomPayloadPacket(KryptonHelloPayload.current());
                    Runnable upgrade = () -> {
                        if (clientCompressionUpgraded) {
                            return;
                        }
                        clientCompressionUpgraded = true;
                        try {
                            ((ZstdUpgradeExtension) conn).krypton$upgradeToZstd(0, true);
                        } catch (Throwable t) {
                            KryptonHybrid.LOGGER.error("client upgradeToZstd failed", t);
                        }
                    };
                    //#if MC >= 12108
                    conn.send(hello, (future) -> upgrade.run());
                    //#else
                    //$$ conn.send(hello, new PacketSendListener() {
                    //$$     @Override
                    //$$     public void onSuccess() {
                    //$$         upgrade.run();
                    //$$     }
                    //$$ });
                    //#endif
                }));

        // The dictionary is applied for the wire-level Zstd compression contexts.
        ClientConfigurationNetworking.registerGlobalReceiver(KryptonDictPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ZstdUtil.applyReceivedDictionary(payload.dictionaryBytes())));

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
