package com.xinian.KryptonHybrid.shared;

import com.xinian.KryptonHybrid.mixin.via.FabricDecodeHandlerAccessor;
import io.netty.channel.Channel;

/**
 * Server-side access to the protocol version ViaFabric is negotiating for a
 * connection.  ViaFabric does <em>not</em> register its UserConnection in
 * ViaVersion's global connection manager; instead the connection object hangs
 * off the {@code via-decoder} pipeline handler, exposed through the
 * {@link FabricDecodeHandlerAccessor} mixin (applied only when ViaFabric is
 * loaded).
 */
public final class ViaFabricAccess {

    private ViaFabricAccess() {}

    /**
     * The client's protocol version as ViaFabric sees it for {@code channel},
     * or -1 when no ViaFabric handler is installed.
     */
    public static int getProtocolVersion(Channel channel) {
        Object decoder = channel.pipeline().get("via-decoder");
        if (decoder instanceof FabricDecodeHandlerAccessor accessor && accessor.getInfo() != null) {
            return accessor.getInfo().getProtocolInfo().getProtocolVersion();
        }
        return -1;
    }
}
