package com.xinian.KryptonHybrid.shared.network.broadcast;

import java.util.IdentityHashMap;
import java.util.function.BooleanSupplier;

/**
 * Per-thread, identity-keyed cache with a hard entry cap and full-clear-on-overflow
 * eviction. Used by {@link BroadcastSerializationCache} and
 * {@link BroadcastCompressedCache} to short-circuit redundant per-recipient work
 * when the same {@link net.minecraft.network.protocol.Packet} instance is encoded
 * multiple times on the same Netty I/O thread.
 *
 * <p>Reads/writes are gated by an external {@link BooleanSupplier} (typically a
 * {@code KryptonConfig} flag) so the cache turns into a no-op when disabled,
 * without any state leaking into the {@link ThreadLocal}.
 *
 * <p>Not exported outside this package. Both caches keep their public static
 * surface; this is purely an internal implementation detail.
 */
final class ThreadLocalIdentityCache<V> {

    private final int maxEntries;
    private final BooleanSupplier enabled;
    private final ThreadLocal<IdentityHashMap<Object, V>> tl =
            ThreadLocal.withInitial(IdentityHashMap::new);

    ThreadLocalIdentityCache(int maxEntries, BooleanSupplier enabled) {
        this.maxEntries = maxEntries;
        this.enabled = enabled;
    }

    V get(Object key) {
        if (!enabled.getAsBoolean()) return null;
        return tl.get().get(key);
    }

    void put(Object key, V value) {
        if (!enabled.getAsBoolean()) return;
        IdentityHashMap<Object, V> map = tl.get();
        if (map.size() >= maxEntries) {
            map.clear();
        }
        map.put(key, value);
    }

    void clear() {
        tl.get().clear();
    }
}
