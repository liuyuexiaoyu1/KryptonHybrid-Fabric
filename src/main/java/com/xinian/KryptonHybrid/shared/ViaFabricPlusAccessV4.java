package com.xinian.KryptonHybrid.shared;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;

/**
 * Client-side access to the protocol version ViaFabricPlus <strong>4.x</strong>
 * (package {@code com.viaversion.viafabricplus}) is negotiating for a
 * connection.  ViaFabricPlus stores its state on the vanilla Connection object
 * (the {@code packet_handler} pipeline handler) via the {@link IConnection}
 * accessor mixin; the target version is the version it rewrote the handshake
 * to (the server's version).
 *
 * <p>This class references ViaFabricPlus 4.x classes directly and must only be
 * loaded when ViaFabricPlus 4.x is present — callers ({@link ViaCompat}) guard
 * it.</p>
 */
public final class ViaFabricPlusAccessV4 {

    private ViaFabricPlusAccessV4() {}

    /**
     * The protocol version ViaFabricPlus targets for {@code channel} (the
     * server's version), or -1 when no ViaFabricPlus connection state is
     * present.
     */
    public static int getTargetProtocolVersion(Channel channel) {
        Object handler = channel.pipeline().get("packet_handler");
        if (handler instanceof IConnection connection) {
            ProtocolVersion target = connection.viaFabricPlus$getTargetVersion();
            if (target != null) {
                return target.getVersion();
            }
        }
        return -1;
    }
}
