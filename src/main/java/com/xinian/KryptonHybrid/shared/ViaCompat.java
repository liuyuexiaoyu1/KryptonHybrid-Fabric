package com.xinian.KryptonHybrid.shared;

import com.xinian.KryptonHybrid.KryptonHybrid;
import io.netty.channel.Channel;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Via-compatibility helper: determines whether a connection is cross-version
 * by asking ViaVersion which protocol version it is currently negotiating for
 * the channel.
 *
 * <p>On the server (ViaFabric) that is the client's real version; on the
 * client (ViaFabricPlus) the target version it rewrote the handshake to.
 * Comparing it against the local runtime version tells us whether Via is
 * actively translating — the via-compatibility rule disables the custom
 * chunk/light/block-entity wire features on cross-version sessions (only the
 * Via-transparent zstd compression remains), while same-version sessions keep
 * every feature even when Via is present.</p>
 *
 */
public final class ViaCompat {

    private ViaCompat() {}

    /**
     * The protocol version Via is negotiating for {@code channel}, or -1 when
     * no Via mod is handling the connection or the version cannot be queried.
     *
     * <p>Three sources are tried: ViaFabricPlus 4.x, ViaFabricPlus 3.x
     * (client — each stores its target version on the Connection object), and
     * ViaFabric (server — its UserConnection hangs off the {@code via-decoder}
     * handler).  Each access class is loaded lazily so environments without
     * the respective mod version are never affected.</p>
     */
    public static int getCurrentProtocolVersion(Channel channel) {
        if (channel == null) {
            return -1;
        }
        if (FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            // Client: ViaFabricPlus 4.x (com.viaversion.viafabricplus).
            try {
                int v4 = ViaFabricPlusAccessV4.getTargetProtocolVersion(channel);
                if (v4 != -1) {
                    return v4;
                }
            } catch (NoClassDefFoundError ignored) {
                // ViaFabricPlus 4.x not loaded.
            }
            // Client: ViaFabricPlus 3.x (de.florianmichael.viafabricplus).
            try {
                int v3 = ViaFabricPlusAccessV3.getTargetProtocolVersion(channel);
                if (v3 != -1) {
                    return v3;
                }
            } catch (NoClassDefFoundError ignored) {
                // ViaFabricPlus 3.x not loaded.
            }
        } else if (FabricLoader.getInstance().isModLoaded("viafabric")) {
            // Server: ViaFabric per-connection negotiated version (client version).
            try {
                return ViaFabricAccess.getProtocolVersion(channel);
            } catch (NoClassDefFoundError ignored) {
                // ViaFabric not loaded.
                return -1;
            } catch (Throwable t) {
                KryptonHybrid.LOGGER.warn("Krypton: failed to query Via protocol version: {}", t.toString());
                return -1;
            }
        }
        return -1;
    }

    /**
     * True when the version Via negotiates for {@code channel} equals the local
     * runtime version — i.e. the session is same-version (Via, if present, is
     * not translating).  A failed/unavailable query counts as "not same" so the
     * via-compatibility rule falls back to disabling custom features.
     */
    public static boolean isSameVersion(Channel channel, int runtimeProtocol) {
        return getCurrentProtocolVersion(channel) == runtimeProtocol;
    }
}
