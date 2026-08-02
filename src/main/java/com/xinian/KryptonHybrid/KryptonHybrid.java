package com.xinian.KryptonHybrid;

import com.xinian.KryptonHybrid.shared.network.util.KryptonConnectionUtil;

import com.xinian.KryptonHybrid.command.KryptonStatsCommand;
import com.xinian.KryptonHybrid.mixin.network.pipeline.IServerCommonListenerAccessor;
import com.xinian.KryptonHybrid.shared.network.pipeline.ZstdUpgradeExtension;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdAutoTrainer;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import com.xinian.KryptonHybrid.shared.network.control.PacketControlState;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonDictPayload;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonHelloPayload;
import com.xinian.KryptonHybrid.shared.network.payload.KryptonCompressionReadyPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsRequestPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import com.xinian.KryptonHybrid.shared.network.security.MotdCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

        // Silent Zstd dictionary auto-training from captured outbound traffic
        ServerTickEvents.END_SERVER_TICK.register(ZstdAutoTrainer::tick);

        KryptonSharedBootstrap.run(false);
        LOGGER.info("Krypton config loaded - compression algorithm: {}, zstd status: {}",
                KryptonConfig.compressionAlgorithm,
                ZstdUtil.statusDescription());
    }

    private static void registerPayloadTypes() {
        //#if MC >= 26.1
        PayloadTypeRegistry.clientboundConfiguration().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(KryptonCompressionReadyPayload.TYPE, KryptonCompressionReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(KryptonDictPayload.TYPE, KryptonDictPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KryptonDictPayload.TYPE, KryptonDictPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StatsRequestPayload.TYPE, StatsRequestPayload.STREAM_CODEC);
        //#else
        //$$ PayloadTypeRegistry.configurationS2C().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.configurationC2S().register(KryptonHelloPayload.TYPE, KryptonHelloPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.configurationC2S().register(KryptonCompressionReadyPayload.TYPE, KryptonCompressionReadyPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.configurationS2C().register(KryptonDictPayload.TYPE, KryptonDictPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.playS2C().register(KryptonDictPayload.TYPE, KryptonDictPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.playS2C().register(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.STREAM_CODEC);
        //$$ PayloadTypeRegistry.playC2S().register(StatsRequestPayload.TYPE, StatsRequestPayload.STREAM_CODEC);
        //#endif
    }

    private static void registerServerNetworking() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, KryptonHelloPayload.TYPE)) {
                Connection connection = ((IServerCommonListenerAccessor) handler).krypton$getConnection();
                PacketControlState.get(KryptonConnectionUtil.channel(connection)).markHelloAvailable();
                ServerConfigurationNetworking.send(handler, KryptonHelloPayload.current());

                // Push the current Zstd dictionary so the client applies the same
                // dictionary before compression setup. Newly trained dictionaries
                // are picked up automatically by the next connecting client.
                byte[] dict = ZstdUtil.getDictionaryBytesForTransfer();
                if (dict != null) {
                    ServerConfigurationNetworking.send(handler, new KryptonDictPayload(dict));
                }
            }
        });

        ServerConfigurationNetworking.registerGlobalReceiver(KryptonHelloPayload.TYPE, (payload, context) -> {
            //#if MC >= 26.1
            Connection connection = ((IServerCommonListenerAccessor) context.packetListener()).krypton$getConnection();
            //#else
            //$$ Connection connection = ((IServerCommonListenerAccessor) context.networkHandler()).krypton$getConnection();
            //#endif
            PacketControlState.get(KryptonConnectionUtil.channel(connection)).markHelloNegotiated(payload.featureFlags());
        });

        // Client has applied the dictionary and is about to enable compression:
        // install the server-side compressors now. After this point all packets on
        // this connection (remaining configuration + play) travel compressed.
        ServerConfigurationNetworking.registerGlobalReceiver(KryptonCompressionReadyPayload.TYPE, (payload, context) -> {
            //#if MC >= 26.1
            Connection connection = ((IServerCommonListenerAccessor) context.packetListener()).krypton$getConnection();
            //#else
            //$$ Connection connection = ((IServerCommonListenerAccessor) context.networkHandler()).krypton$getConnection();
            //#endif
            ZstdUpgradeExtension ext = (ZstdUpgradeExtension) connection;
            ext.krypton$upgradeToZstd(ext.krypton$getCompressionThreshold(), ext.krypton$getCompressionValidate());
            KryptonHybrid.LOGGER.info("Krypton compression enabled for connection {}", connection);
        });

        ServerPlayNetworking.registerGlobalReceiver(StatsRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    ServerPlayNetworking.send(player, StatsSnapshotPayload.current());
                }));
    }
}
