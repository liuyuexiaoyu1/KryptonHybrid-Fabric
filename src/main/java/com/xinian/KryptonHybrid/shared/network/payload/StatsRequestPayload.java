package com.xinian.KryptonHybrid.shared.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server request asking the server to push a fresh
 * {@link StatsSnapshotPayload}.
 *
 * <p>Replaces the previous coupling that used
 * {@code /krypton stats gui} to refresh the dashboard. Using a dedicated
 * payload keeps the GUI refresh path independent of the chat/command pipeline
 * (no permission check, no chat history pollution, no command suggestions).</p>
 */
public record StatsRequestPayload(byte revision) implements CustomPacketPayload {

    public static final byte CURRENT_REVISION = 1;
    public static final StatsRequestPayload INSTANCE = new StatsRequestPayload(CURRENT_REVISION);

    public static final Type<StatsRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("krypton_hybrid", "stats_request"));

    public static final StreamCodec<FriendlyByteBuf, StatsRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeByte(p.revision),
                    buf -> new StatsRequestPayload(buf.readByte())
            );

    @Override
    public Type<StatsRequestPayload> type() {
        return TYPE;
    }
}
