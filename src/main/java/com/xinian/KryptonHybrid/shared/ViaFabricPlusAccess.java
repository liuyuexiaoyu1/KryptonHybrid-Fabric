package com.xinian.KryptonHybrid.shared;

// ViaFabricPlus changed its accessor interface package/name across versions:
//   3.x                        → de.florianmichael.viafabricplus…IClientConnection
//   4.0–4.2 (1.21.4–1.21.8)    → com.viaversion…access.base.IClientConnection
//   4.3–4.4 (1.21.10–1.21.11)  → com.viaversion…access.base.IConnection
//   4.5+   (26.1+)             → com.viaversion…access.core.IConnection
// The method signatures are identical across all of them
// (viaFabricPlus$getTargetVersion() → ProtocolVersion).
//#if MC >= 26.1
import com.viaversion.viafabricplus.injection.access.core.IConnection;
//#elseif MC >= 12110
//$$ import com.viaversion.viafabricplus.injection.access.base.IConnection;
//#elseif MC >= 12104
//$$ import com.viaversion.viafabricplus.injection.access.base.IClientConnection;
//#else
//$$ import de.florianmichael.viafabricplus.injection.access.IClientConnection;
//#endif
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;

/**
 * Client-side access to the protocol version ViaFabricPlus is negotiating for
 * a connection.  ViaFabricPlus stores its state on the vanilla Connection
 * object (the {@code packet_handler} pipeline handler) via its accessor mixin;
 * the target version is the version it rewrote the handshake to (the server's
 * version).
 *
 * <p>This class references ViaFabricPlus classes directly and must only be
 * loaded when ViaFabricPlus is present — callers ({@link ViaCompat}) guard
 * it.</p>
 */
public final class ViaFabricPlusAccess {

    private ViaFabricPlusAccess() {}

    /**
     * The protocol version ViaFabricPlus targets for {@code channel} (the
     * server's version), or -1 when no ViaFabricPlus connection state is
     * present.
     */
    public static int getTargetProtocolVersion(Channel channel) {
        Object handler = channel.pipeline().get("packet_handler");
        //#if MC >= 26.1
        if (handler instanceof IConnection connection)
        //#elseif MC >= 12110
        //$$ if (handler instanceof IConnection connection)
        //#elseif MC >= 12104
        //$$ if (handler instanceof IClientConnection connection)
        //#else
        //$$ if (handler instanceof IClientConnection connection)
        //#endif
        {
            ProtocolVersion target = connection.viaFabricPlus$getTargetVersion();
            if (target != null) {
                return target.getVersion();
            }
        }
        return -1;
    }
}
