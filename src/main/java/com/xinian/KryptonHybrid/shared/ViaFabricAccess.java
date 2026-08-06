package com.xinian.KryptonHybrid.shared;

import com.viaversion.fabric.common.handler.FabricDecodeHandler;
import io.netty.channel.Channel;

public final class ViaFabricAccess {

    private ViaFabricAccess() {}

    public static int getProtocolVersion(Channel channel) {
        Object decoder = channel.pipeline().get("via-decoder");
        if (decoder instanceof FabricDecodeHandler handler && handler.connection() != null) {
            return handler.connection().getProtocolInfo().getProtocolVersion();
        }
        return -1;
    }
}
