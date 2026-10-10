package com.xinian.KryptonHybrid.shared.network.motion;

import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.stats.NetworkTrafficStats;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Per-player cache of the last sent motion / teleport state for each entity,
 * used to drop redundant {@link ClientboundSetEntityMotionPacket} and
 * {@link ClientboundTeleportEntityPacket} updates whose component delta is
 * below {@link KryptonConfig#motionDeltaThreshold} (motion) or
 * {@link KryptonConfig#teleportDeltaSquared} (teleport).
 *
 * <h3>Why this works</h3>
 * <p>Projectiles, dropped items and falling blocks tick continuously and emit
 * a velocity packet every server tick even when the velocity barely changed.
 * The encoded short representation cannot resolve sub-{@code 1/8000} block-per-
 * tick changes, so dropping packets whose all-axis encoded delta is within
 * a few units is visually identical to sending them.</p>
 *
 * <h3>Storage / lifecycle</h3>
 * <p>Outer map is a {@link WeakHashMap} keyed by {@link ServerPlayerConnection}
 * so disconnected players' state is GC'd automatically.  Inner map is a fastutil
 * {@code Int2ObjectOpenHashMap} keyed by entityId.  All access is on the server
 * thread (entity tracking + bundle flush both run there), so no synchronization.</p>
 */
public final class MotionDeltaCache {

    /** Compact mutable triple used as the motion cache value. */
    private static final class MotionState {
        double xa, ya, za;
        MotionState(double xa, double ya, double za) { this.xa = xa; this.ya = ya; this.za = za; }
    }

    /** Compact mutable record used as the teleport cache value. */
    private static final class TeleportState {
        double x, y, z;
        byte yRot, xRot;
        TeleportState(double x, double y, double z, byte yRot, byte xRot) {
            this.x = x; this.y = y; this.z = z; this.yRot = yRot; this.xRot = xRot;
        }
    }

    private static final WeakHashMap<ServerPlayerConnection, Int2ObjectOpenHashMap<MotionState>> MOTION =
            new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayerConnection, Int2ObjectOpenHashMap<TeleportState>> TELEPORT =
            new WeakHashMap<>();

    private MotionDeltaCache() {}

    public static int filter(ServerPlayerConnection conn, List<Packet<?>> packets) {
        if (!KryptonConfig.motionDeltaEnabled || conn == null || packets.isEmpty()) {
            return 0;
        }
        int dropped = 0;
        // Vanilla encodes velocity as (int)(v * 8000); convert encoded-unit threshold to double tolerance.
        double motionThresh = Math.max(0, KryptonConfig.motionDeltaThreshold) / 8000.0;
        double teleSqThresh = KryptonConfig.teleportDeltaSquared;

        Int2ObjectOpenHashMap<MotionState> motionMap = null;
        Int2ObjectOpenHashMap<TeleportState> teleportMap = null;

        Iterator<Packet<?>> it = packets.iterator();
        while (it.hasNext()) {
            Packet<?> pkt = it.next();
            if (pkt instanceof ClientboundSetEntityMotionPacket m) {
                if (motionMap == null) motionMap = motionMapFor(conn);
                //#if MC >= 26.1
                int motionId = m.id();
                double xa = m.movement().x(), ya = m.movement().y(), za = m.movement().z();
                //#elseif MC >= 12110
                //$$ int motionId = m.getId();
                //$$ Vec3 motion = m.getMovement();
                //$$ double xa = motion.x(), ya = motion.y(), za = motion.z();
                //#else
                //$$ int motionId = m.getId();
                //$$ double xa = m.getXa(), ya = m.getYa(), za = m.getZa();
                //#endif
                MotionState prev = motionMap.get(motionId);
                if (prev != null
                        && Math.abs(xa - prev.xa) <= motionThresh
                        && Math.abs(ya - prev.ya) <= motionThresh
                        && Math.abs(za - prev.za) <= motionThresh) {
                    it.remove();
                    dropped++;
                } else {
                    if (prev == null) motionMap.put(motionId, new MotionState(xa, ya, za));
                    else { prev.xa = xa; prev.ya = ya; prev.za = za; }
                }
            } else if (pkt instanceof ClientboundTeleportEntityPacket t) {
                if (teleportMap == null) teleportMap = teleportMapFor(conn);
                //#if MC >= 12102
                int teleportId = t.id();
                double x = t.change().position().x(), y = t.change().position().y(), z = t.change().position().z();
                byte yRot = (byte) t.change().yRot(), xRot = (byte) t.change().xRot();
                //#else
                //$$ int teleportId = t.getId();
                //$$ double x = t.getX(), y = t.getY(), z = t.getZ();
                //$$ byte yRot = t.getyRot(), xRot = t.getxRot();
                //#endif
                TeleportState prev = teleportMap.get(teleportId);
                if (prev != null
                        && yRot == prev.yRot
                        && xRot == prev.xRot
                        && squaredDistance(x, y, z, prev.x, prev.y, prev.z) <= teleSqThresh) {
                    it.remove();
                    dropped++;
                } else {
                    if (prev == null) teleportMap.put(teleportId, new TeleportState(x, y, z, yRot, xRot));
                    else { prev.x = x; prev.y = y; prev.z = z; prev.yRot = yRot; prev.xRot = xRot; }
                }
            }
        }
        if (dropped > 0) {
            NetworkTrafficStats.INSTANCE.recordCoalesceDropped(dropped);
        }
        return dropped;
    }

    private static Int2ObjectOpenHashMap<MotionState> motionMapFor(ServerPlayerConnection conn) {
        Int2ObjectOpenHashMap<MotionState> m = MOTION.get(conn);
        if (m == null) {
            m = new Int2ObjectOpenHashMap<>();
            MOTION.put(conn, m);
        }
        return m;
    }

    private static Int2ObjectOpenHashMap<TeleportState> teleportMapFor(ServerPlayerConnection conn) {
        Int2ObjectOpenHashMap<TeleportState> m = TELEPORT.get(conn);
        if (m == null) {
            m = new Int2ObjectOpenHashMap<>();
            TELEPORT.put(conn, m);
        }
        return m;
    }

    private static double squaredDistance(double x1, double y1, double z1,
                                          double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Drops cached state for a specific entity (e.g. on entity removal/untrack). */
    @SuppressWarnings("unused")
    public static void invalidateEntity(int entityId) {
        for (Int2ObjectMap<MotionState> m : MOTION.values()) m.remove(entityId);
        for (Int2ObjectMap<TeleportState> m : TELEPORT.values()) m.remove(entityId);
    }

    /**
     * Drops the cached state of {@code entityId} for one player's connection.
     *
     * <p>Called from {@code ServerEntity.removePairing} (the per-player entity
     * untrack hook): vanilla sends the remove packet straight to the player's
     * connection there, bypassing the bundle collectors that {@link #filter}
     * runs on, so without this the per-player maps would grow one entry per
     * entity ever seen by the player — a memory leak for long sessions on
     * entity-heavy servers.</p>
     */
    public static void invalidateFor(ServerPlayerConnection conn, int entityId) {
        Int2ObjectOpenHashMap<MotionState> m = MOTION.get(conn);
        if (m != null) m.remove(entityId);
        Int2ObjectOpenHashMap<TeleportState> t = TELEPORT.get(conn);
        if (t != null) t.remove(entityId);
    }
}



