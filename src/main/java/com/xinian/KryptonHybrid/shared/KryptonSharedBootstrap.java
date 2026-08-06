package com.xinian.KryptonHybrid.shared;

import com.velocitypowered.natives.util.Natives;
import org.slf4j.Logger;

public class KryptonSharedBootstrap {
    public static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(KryptonSharedBootstrap.class);

    /**
     * True while a MinecraftServer is running in this process — a dedicated
     * server, or an integrated (singleplayer/LAN) server inside a client.
     *
     * <p>Server-managed security features (read limits, decompression bomb
     * guard, …) only take effect while this is true: when a plain client
     * connects to a remote server, all feature on/off decisions belong to the
     * server (negotiated via the hello payload) and the client's own config
     * must not apply — otherwise the client's local settings could reject
     * legitimate server traffic (e.g. kicks when the client enables security
     * with stricter limits than the server's).</p>
     */
    private static volatile boolean serverRunning;

    public static boolean isServerRunning() {
        return serverRunning;
    }

    public static void setServerRunning(boolean running) {
        serverRunning = running;
    }

    static {
        if (System.getProperty("io.netty.allocator.maxOrder") == null) {
            System.setProperty("io.netty.allocator.maxOrder", "9");
        }
    }

    public static void run(boolean client) {
        if (!client) {
            LOGGER.info("Krypton is now accelerating your Minecraft server's networking stack \uD83D\uDE80");
        } else {
            LOGGER.info("Krypton is now accelerating your Minecraft client's networking stack \uD83D\uDE80");
            LOGGER.info("Note that Krypton is most effective on servers, not the client.");
        }
        LOGGER.info("Compression will use {}, encryption will use {}",
                Natives.compress.getLoadedVariant(), Natives.cipher.getLoadedVariant());

    }
}

