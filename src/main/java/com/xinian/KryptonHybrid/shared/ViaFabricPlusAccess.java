package com.xinian.KryptonHybrid.shared;

//#if MC >= 12104
import com.viaversion.viafabricplus.injection.access.core.IConnection;
//#else
//$$ import de.florianmichael.viafabricplus.injection.access.IClientConnection;
//#endif
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;

public final class ViaFabricPlusAccess {

    private ViaFabricPlusAccess() {}

    /**
     * The protocol version ViaFabricPlus targets for {@code channel} (the
     * server's version), or -1 when no ViaFabricPlus connection state is
     * present.
     */
    public static int getTargetProtocolVersion(Channel channel) {
        Object handler = channel.pipeline().get("packet_handler");
        //#if MC >= 12104
        if (handler instanceof IConnection connection)
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
