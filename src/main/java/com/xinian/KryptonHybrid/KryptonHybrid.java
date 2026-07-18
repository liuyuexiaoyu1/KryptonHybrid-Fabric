package com.xinian.KryptonHybrid;

import com.xinian.KryptonHybrid.shared.network.util.KryptonConnectionUtil;

import com.xinian.KryptonHybrid.command.KryptonStatsCommand;
import com.xinian.KryptonHybrid.mixin.network.pipeline.IServerCommonListenerAccessor;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlState;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsRequestPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import com.xinian.KryptonHybrid.shared.network.security.MotdCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KryptonHybrid implements ModInitializer {
    public static final String MODID = "krypton_hybrid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        KryptonFabricConfig.load();
        MotdCache.invalidate();
        ZstdUtil.reloadDictionary();

        registerPayloadTypes();
        registerServerNetworking();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                KryptonStatsCommand.register(dispatcher));

        KryptonSharedBootstrap.run(false);
        LOGGER.info("Krypton config loaded - compression algorithm: {}, zstd status: {}",
                KryptonConfig.compressionAlgorithm,
                ZstdUtil.statusDescription());
    }

    private static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundConfiguration().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StatsRequestPayload.TYPE, StatsRequestPayload.STREAM_CODEC);
    }

    private static void registerServerNetworking() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, KryptonHelloPayload.TYPE)) {
                Connection connection = ((IServerCommonListenerAccessor) handler).krypton$getConnection();
                PacketControlState.get(KryptonConnectionUtil.channel(connection)).markHelloAvailable();
                ServerConfigurationNetworking.send(handler, KryptonHelloPayload.current());
            }
        });

        ServerConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) -> {
            Connection connection = ((IServerCommonListenerAccessor) context.packetListener()).krypton$getConnection();
            PacketControlState.get(KryptonConnectionUtil.channel(connection)).markHelloNegotiated(payload.featureFlags());
        });

        ServerPlayNetworking.registerGlobalReceiver(StatsRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    ServerPlayNetworking.send(player, StatsSnapshotPayload.current());
                }));
    }
}
