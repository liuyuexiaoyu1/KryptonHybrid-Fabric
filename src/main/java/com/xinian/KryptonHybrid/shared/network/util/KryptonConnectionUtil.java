package com.xinian.KryptonHybrid.shared.network.util;

import com.xinian.KryptonHybrid.mixin.network.pipeline.ConnectionAccessor;
import com.xinian.KryptonHybrid.mixin.network.pipeline.IServerCommonListenerAccessor;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

public final class KryptonConnectionUtil {
    private KryptonConnectionUtil() {}

    public static Channel channel(Connection connection) {
        return ((ConnectionAccessor) connection).krypton$getChannel();
    }

    public static Connection connection(ServerCommonPacketListenerImpl listener) {
        return ((IServerCommonListenerAccessor) listener).krypton$getConnection();
    }
}
