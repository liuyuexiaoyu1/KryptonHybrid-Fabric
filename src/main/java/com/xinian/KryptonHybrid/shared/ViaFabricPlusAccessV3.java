package com.xinian.KryptonHybrid.shared;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.viafabricplus.injection.access.IClientConnection;
import io.netty.channel.Channel;

/**
 * Client-side access to the protocol version ViaFabricPlus <strong>3.x</strong>
 * (package {@code de.florianmichael.viafabricplus}) is negotiating for a
 * connection.  ViaFabricPlus stores its state on the vanilla Connection object
 * (the {@code packet_handler} pipeline handler) via the
 * {@link IClientConnection} accessor mixin; the target version is the version
 * it rewrote the handshake to (the server's version).
 *
 * <p>This class references ViaFabricPlus 3.x classes directly and must only be
 * loaded when ViaFabricPlus 3.x is present — callers ({@link ViaCompat}) guard
 * it.</p>
 */
public final class ViaFabricPlusAccessV3 {

    private ViaFabricPlusAccessV3() {}

    /**
     * The protocol version ViaFabricPlus targets for {@code channel} (the
     * server's version), or -1 when no ViaFabricPlus connection state is
     * present.
     */
    public static int getTargetProtocolVersion(Channel channel) {
        Object handler = channel.pipeline().get("packet_handler");
        if (handler instanceof IClientConnection clientConnection) {
            ProtocolVersion target = clientConnection.viaFabricPlus$getTargetVersion();
            if (target != null) {
                return target.getVersion();
            }
        }
        return -1;
    }
}
