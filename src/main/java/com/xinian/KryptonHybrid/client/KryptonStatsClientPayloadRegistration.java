package com.xinian.KryptonHybrid.client;

import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;

/**
 * Dispatch shim that forwards an incoming {@link StatsSnapshotPayload} to the
 * client-side controller.
 *
 * <p>This is a deliberate dist-isolation seam: the common-side packet handler
 * in {@code KryptonHybrid#handleStatsSnapshot} references this class by fully
 * qualified name behind a {@code FMLEnvironment.dist.isClient()} guard, so the
 * dedicated-server JVM never resolves it. Inlining the call would pull
 * {@code net.minecraft.client.*} types into the server classpath and trigger
 * {@code NoClassDefFoundError} at mod load.</p>
 */
public final class KryptonStatsClientPayloadRegistration {
    private KryptonStatsClientPayloadRegistration() {}

    public static void handleSnapshot(StatsSnapshotPayload payload) {
        KryptonStatsClientController.receiveSnapshot(payload);
    }
}
