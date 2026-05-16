package com.xinian.KryptonHybrid.shared.network.payload;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Empty client→server trigger asking the server to push a fresh
 * {@link StatsSnapshotPayload}. Used by the HUD's auto-refresh tick and the
 * stats screen's refresh button so they no longer have to forge the
 * {@code /krypton stats gui} command (which required op permission and
 * coupled the UI to the command dispatcher).
 */
public record StatsSnapshotRequestPayload() {
    public static final StatsSnapshotRequestPayload INSTANCE = new StatsSnapshotRequestPayload();

    public void encode(FriendlyByteBuf buf) {
        // no payload body
    }

    public static StatsSnapshotRequestPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }
}
