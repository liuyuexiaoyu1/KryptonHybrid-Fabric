package com.xinian.KryptonHybrid.client;

import com.xinian.KryptonHybrid.KryptonFabricConfig;
import com.xinian.KryptonHybrid.client.overlay.KryptonHudOverlay;
import com.xinian.KryptonHybrid.client.ui.UITheme;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlState;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonDictPayload;
import io.netty.channel.Channel;
import com.xinian.KryptonHybrid.shared.network.pipeline.ZstdUpgradeExtension;
import com.xinian.KryptonHybrid.shared.network.util.KryptonConnectionUtil;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.payload.KryptonCompressionReadyPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//#if MC < 12108
import net.minecraft.network.PacketSendListener;
//#endif
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
        // Apply persisted client GUI/HUD settings (theme, HUD visibility/anchor/…).
        UITheme.loadFromConfig();
        KryptonHudOverlay.loadFromConfig();
        ZstdUtil.reloadDictionary();
        KryptonSharedBootstrap.run();

        ClientConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // Reply with per-connection features: when ViaFabricPlus is
                // loaded and the protocol version it is currently negotiating
                // differs from this runtime's, the via-compatibility rule drops
                // every custom feature from the reply so the server falls back
                // to the vanilla stream (zstd stays, it is Via-transparent).
                Channel channel = KryptonConnectionUtil.channel(context.client().getConnection().getConnection());
                PacketControlState state = PacketControlState.get(channel);
                KryptonHelloPayload hello = KryptonHelloPayload.current(channel);
                state.setLocalFeatureFlags(hello.featureFlags());
                context.responseSender().sendPacket(hello);
            });
            //#if MC >= 12108
            context.responseSender().sendPacket(KryptonCompressionReadyPayload.INSTANCE, (listener) -> {
                // Decoder-only upgrade: the outbound encoder stays on vanilla
                // zlib and upgrades lazily when a Zstd frame is received
                // (KRYPTON_ZSTD_SEEN).  Through a proxy (e.g. Velocity) the
                // client never receives Zstd frames, so the client↔proxy
                // segment stays vanilla-compatible.
                ((ZstdUpgradeExtension) context.client().getConnection().getConnection()).krypton$upgradeDecoderToZstd(0, true);
            });
            //#else
            // On ≤ 1.21.5, net.minecraft.network.PacketSendListener is a non-functional
            // interface (all-default methods), so the listener lambda above only
            // compiles once it is a functional ChannelFutureListener (1.21.8+).
            //$$ context.responseSender().sendPacket(KryptonCompressionReadyPayload.INSTANCE,
            //$$         PacketSendListener.thenRun(() -> {
            //$$             ((ZstdUpgradeExtension) context.client().getConnection().getConnection()).krypton$upgradeDecoderToZstd(0, true);
            //$$         }));
            //#endif
        });

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
