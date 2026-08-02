# Krypton Hybrid

[![](https://badges.moddingx.org/curseforge/downloads/1474749)](https://www.curseforge.com/minecraft/mc-mods/krypton-hybrid)
[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](LICENSE)

[![curseforge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg)](https://www.curseforge.com/minecraft/mc-mods/krypton-hybrid)
[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/dreamforSh/KryptonHybrid)

[中文](README.md) | **English**

---

## Overview

Krypton Hybrid is a fork based on [Krypton fnp](https://github.com/404Setup/KryptonFNP) legacy (1.19.2). It completes the missing optimizations of the Krypton fnp lite version, adds support for the zstd compression algorithm, and improves the original encoding and Netty processing, while retaining the features of [Krypton fnp](https://github.com/404Setup/KryptonFNP) and RecastLib.

### Key Features

1. **Hybrid server compatibility** — compatibility support for hybrid servers.

2. **Traffic monitoring commands** — commands to monitor traffic usage and security status.

3. **Zstd-first compression** — zstd as the primary compression algorithm, while retaining the original entity tracking and encoding optimizations.

4. **Built-in network security layer** — connection rate limiting, stage-aware timeouts, packet size and payload validation, anomaly detection, and Netty resource protection (DDoS protection).

---

## Installation

- **Both server and client must have the mod installed** (custom compression/chunk formats require negotiation on both sides).
- Supported Minecraft versions: **1.21.1 ~ 26.2** (Fabric + Fabric API).
- Drop the jar into the `mods/` folder.

> ⚠️ **Both sides must use the same compression algorithm** — a mismatch corrupts the session immediately.

---

## Configuration

The config file is located at `config/krypton_hybrid.json` (independent on server/client). Reload with `/krypton config reload`.

### 1. Compression

| Key | Default | Description |
|---|---|---|
| `compressionAlgorithm` | `ZSTD` | Compression algorithm: `ZSTD` or `ZLIB`. |
| `zstdLevel` | `3` | Zstd compression level, range [1, 22]. Higher = better ratio, more CPU. |
| `zstdAdaptiveLargeLevel` | `0` | Adaptive level for large packets (e.g. chunk data). `0` = off. |
| `zstdAdaptiveLargeThreshold` | `8192` | Byte threshold above which the adaptive level is used. |
| `zstdWorkers` | `0` | Multi-threaded compression workers; `≥1` enables the native thread pool. |
| `zstdOverlapLog` | `0` | Advanced tuning: overlap log. |
| `zstdJobSize` | `0` | Advanced tuning: job size. |
| `zstdEnableLDM` | `false` | Enable Long Distance Matching. |
| `zstdLongDistanceWindowLog` | `27` | LDM window log. |
| `zstdStrategy` | `0` | Compression strategy (`0=default, 1=fast, 2=dfast, ...`). |

### 2. Zstd Dictionary

| Key | Default | Description |
|---|---|---|
> The dictionary is **fully auto-trained on the server and pushed to clients** (during configuration and PLAY-stage pushes) — no manual dictionary file is needed.

| `zstdDictEnabled` | `true` | Dictionary compression switch (auto-managed: force-enabled and pushed once trained). |
| `zstdDictPath` | `config/krypton_hybrid.zdict` | Dictionary storage path (auto-managed, no manual config). |
| `zstdDictTrainingCaptureEnabled` | `true` | Capture outbound traffic samples (required for auto-training, on by default). |
| `zstdDictTrainingSamplesDir` | `run/krypton_zstd_samples` | Samples output directory. |
| `zstdDictTrainingMaxSamples` | `12000` | Max samples per JVM session. |
| `zstdDictTrainingSampleEvery` | `3` | Capture one packet every N packets. |
| `zstdDictTrainingMinBytes` | `24` | Min serialized sample size. |
| `zstdDictTrainingMaxBytes` | `262144` | Max serialized sample size. |
| `zstdDictAutoTrainEnabled` | `true` | Silent auto-training of the dictionary (requires capture enabled). Trains and hot-reloads automatically once enough samples are collected — no command or restart needed. |
| `zstdDictAutoTrainMinSamples` | `2000` | Min samples before auto-training triggers. |
| `zstdDictAutoTrainMinBytes` | `8388608` | Min total sample bytes before auto-training triggers (8 MiB). |
| `zstdDictAutoTrainDictSize` | `65536` | Target dictionary size. |
| `zstdDictAutoTrainIntervalSec` | `3600` | Cooldown between auto-training runs (seconds; 0 = no cooldown). |
| `zstdDictAutoTrainClearSamples` | `true` | Clear captured samples after a successful auto-training run. |

> See [docs/zstd-dictionary-training.md](docs/zstd-dictionary-training.md) for the training guide.

### 3. Network Security

| Key | Default | Description |
|---|---|---|
| `securityEnabled` | `true` | Global kill-switch. |
| `securityConnectionRatePerSecond` | `8` | Sustained connections/sec per remote IP. |
| `securityConnectionBurstLimit` | `20` | Burst capacity for new connections per IP. |
| `securityConnectionQuarantineSeconds` | `30` | Quarantine seconds after repeated connection abuse. |
| `securityRapidReconnectWindowMs` | `1500` | Window to detect suspicious rapid reconnects. |
| `securityRapidReconnectPenalty` | `4` | Extra tokens consumed on rapid reconnect. |
| `securityMaxDecompressedBytes` | `16777216` | Max decompressed payload (16 MiB). |
| `securityMaxCompressionRatio` | `100` | Max compression ratio before decompression is refused. |
| `securityMinCompressedBytesForRatioCheck` | `256` | Min compressed bytes before ratio checks apply. |
| `securityHandshakeTimeoutSec` | `5` | Handshake read timeout. |
| `securityLoginTimeoutSec` | `10` | Login stage timeout. |
| `securityPlayTimeoutSec` | `30` | Play stage timeout. |
| `securityMaxPacketBytes` | `2097152` | Max decoded frame after splitting (2 MiB). |
| `securityMaxCustomPayloadBytes` | `131072` | Max custom payload size. |
| `securityReadLimitsEnabled` | `false` | FriendlyByteBuf read-side guardrails (off by default for compatibility). |
| `securityMaxStringChars` | `32767` | Max UTF-8 string chars when decoding. |
| `securityMaxCollectionElements` | `16384` | Max element count for decoded collections. |
| `securityMaxMapEntries` | `8192` | Max entry count for decoded maps. |
| `securityMaxCountedElements` | `16384` | Max loop count for readWithCount payloads. |
| `securityMaxByteArrayBytes` | `2097152` | Max decoded byte[] length. |
| `securityAnomalyStrikeThreshold` | `10` | Weighted anomaly strike threshold before force-disconnect. |
| `securityWriteWatermarkLow` | `524288` | Netty write watermark low (512 KiB). |
| `securityWriteWatermarkHigh` | `2097152` | Netty write watermark high (2 MiB). |
| `securityMaxPendingWrites` | `4096` | Max pending write queue. |
| `securityMaxUnwritableSeconds` | `15` | Force-close channels unwritable for too long. |
| `securityMinProtocolVersion` | `3` | Min handshake protocol version. |
| `securityMaxProtocolVersion` | `1100` | Max handshake protocol version. |
| `securityMaxHandshakeAddressLength` | `255` | Max handshake address length. |
| `motdCacheEnabled` | `true` | Cache MOTD/status responses. |
| `motdCacheTtlMs` | `3000` | MOTD cache TTL (0 = disabled). |
| `securityStatusPingGuardEnabled` | `true` | Independent per-IP throttling for STATUS pings. |
| `securityStatusPingRatePerSecond` | `4` | Status ping rate per second. |
| `securityStatusPingBurstLimit` | `8` | Status ping burst capacity. |
| `securityStatusPingQuarantineSeconds` | `15` | Status ping quarantine seconds. |
| `securityStatusPingSilentDrop` | `true` | Silently close excessive STATUS connections. |
| `securityLegacyQueryGuardEnabled` | `true` | Apply the same guard to pre-1.7 legacy pings. |

### 4. Chunk Optimization

| Key | Default | Description |
|---|---|---|
| `chunkOptEnabled` | `true` | Chunk data optimization: biome delta encoding + heightmap XOR-delta binary compression. |
| `lightOptEnabled` | `true` | Light data Uniform-RLE compression (uniform sky layer 2048B → 2B). |
| `blockEntityDeltaEnabled` | `true` | Block entity NBT delta encoding (only changed keys sent; requires the mod on both sides). |

### 5. Delayed Chunk Cache (DCC)

| Key | Default | Description |
|---|---|---|
| `dccEnabled` | `true` | Master switch. |
| `dccSizeLimit` | `60` | Max cached chunks per player. |
| `dccDistance` | `5` | Cache radius around the player (chunks). |
| `dccTimeoutSeconds` | `30` | Forced eviction timeout. |

Departing chunks are buffered; if the player re-enters range before expiry, the full resend is **skipped**.

### 6. Broadcast Cache

| Key | Default | Description |
|---|---|---|
| `broadcastCacheEnabled` | `true` | Cache serialized bytes of broadcast packets (same Packet instance, multiple players). |
| `broadcastCompressedCacheEnabled` | `true` | Cache post-Zstd compressed bytes (chunk/light packets). |

### 7. Bundle

| Key | Default | Description |
|---|---|---|
| `bundleAlwaysCompress` | `true` | Force-compress bundle sub-packets even below the regular threshold. |
| `bundleCompressMinBytes` | `24` | Min bytes for forced bundle compression. |

### 8. Micro-Batch Flush

| Key | Default | Description |
|---|---|---|
| `microBatchFlushEnabled` | `false` | Replace per-tick immediate flush with a deferred channel flush (coalesce across ticks). |
| `microBatchFlushDelayMs` | `5` | Deferred flush delay (1-20 ms; recommended 5). |

### 9. Motion Delta Filtering

| Key | Default | Description |
|---|---|---|
| `motionDeltaEnabled` | `true` | Drop visually identical motion/teleport updates. |
| `motionDeltaThreshold` | `40` | Per-axis encoded tolerance (80 units ≈ 0.01 blocks/tick; default 40 ≈ 0.005 b/t). |
| `teleportDeltaSquared` | `1.0E-4` | Squared-distance tolerance (0.01-block radius). |

### 10. Packet Coalescing

| Key | Default | Description |
|---|---|---|
| `packetCoalescingEnabled` | `true` | Deduplicate redundant packets in tracking bundles (velocity/teleport/entity data/head rotation/block entity). |

### 11. Proxy Forwarding

| Key | Default | Description |
|---|---|---|
| `proxyMode` | `NONE` | Proxy mode: `NONE` / `VELOCITY` (modern forwarding). |
| `velocityForwardingSecret` | `""` | Velocity forwarding secret (must match your proxy). |

---

## Commands

Requires operator permission level 2.

```
/krypton stats show          – compression ratio, bandwidth saving, uptime
/krypton stats reset         – reset all counters
/krypton stats gui           – send a stats snapshot to the client (opens the dashboard)
/krypton packets bycount [n] – top N packet types by count
/krypton packets bybytes [n] – top N packet types by bytes
/krypton packets bywire [n]  – top N packet types by compressed wire bytes
/krypton mods bycount [n]    – top N mod namespaces by count
/krypton mods bybytes [n]    – top N mod namespaces by bytes
/krypton mods bywire [n]     – top N mod namespaces by compressed wire bytes
/krypton security status     – security metrics snapshot
/krypton zstd status         – Zstd compression/dictionary status
/krypton zstd dict reload    – reload the dictionary
/krypton config reload       – hot-reload the config file
```

---

## Technical Details

### 1. Network Compression Pipeline

Krypton Hybrid replaces Minecraft's default zlib codec by injecting into `Connection.setupCompression`.

| Feature | Detail |
|---|---|
| Primary algorithm | Zstd via `zstd-jni` (native) |
| Fallback algorithm | zlib via `velocity-native` |
| Level range | 1 – 22 (default 3) |
| Multi-threaded | `workers ≥ 1` activates the native thread pool |
| Dictionary | Optional `.zdict` pre-trained file, fail-open or fail-fast |

### 2. Light Payload Optimization — Uniform-RLE

Sky-light sections above the surface are almost always uniform (`0xFF`). Krypton encodes each 2048-byte uniform layer as 2 bytes (`[0x01] + 1 byte`). In open-sky environments this saves up to ~40 KB per chunk.

### 3. Chunk Data Optimization

- **Heightmaps**: XOR-delta + ZigZag VarLong (adjacent columns correlate → many near-zero longs)
- **Biome stream**: single-value sections 2 bytes (vs 3+), multi-value sections grouped contiguously

### 4. Flush Consolidation + Entity Packet Bundling

```
ChunkMap.tick() HEAD
  ① Recovery: flush any stale batch from a previous failed tick
  ② Disable auto-flush for all players
  ③ Open EntityBundleCollector batch window

  ↳ TrackedEntity sends → collected per player

ChunkMap.tick() RETURN
  ④ Close batch → emit ClientboundBundlePackets (buffered, not flushed)
  ⑤ Re-enable auto-flush → single kernel flush per player
```

| Layer | Mechanism | Benefit |
|---|---|---|
| Flush consolidation (Netty) | Buffer writes, one `flush()` at end of tick | Fewer syscalls on busy servers |
| Bundle coalescing (protocol) | N entity packets → 1 `ClientboundBundlePacket` | Less framing overhead; better compression; client-side atomicity |

### 5. Hot-Path Micro-Optimizations

| Target | Optimization |
|---|---|
| `VarInt.read()` | Branchless 4-byte `getIntLE` fast path |
| `VarInt/VarLong.getByteSize()` | `numberOfLeadingZeros` lookup table |
| `FriendlyByteBuf.writeVarInt()` | Peeled 1/2/3/4/5-byte paths |
| `FriendlyByteBuf.writeUtf()` | Single-pass via `ByteBufUtil.utf8Bytes` |
| `Varint21FrameDecoder` | Velocity-derived efficient framing + nullping mitigation |

---

## Multi-Version Build

Supports 1.21.1 ~ 26.2 (version list in `settings.json`), using the [Fallen-Breath preprocessor](https://github.com/Fallen-Breath/preprocessor) for conditional compilation:

```bash
# Build all versions
./gradlew preprocessResources preprocessCode build

# Build a specific version
./gradlew :26.2:build

# Mixin injection audit (server)
./gradlew runServerMixinAudit
```

---

## Test Data

In the original test, this mod reduced the initial package size to **13%** of the original.

<img src="./test.png" alt="test" />

---

## Credit

- [Krypton Fabric](https://modrinth.com/mod/krypton)
- [Velocity](https://github.com/PaperMC/Velocity)
- [VelocityNT Recast](https://github.com/404Setup/VelocityNT-Recast)
- [Paper](https://github.com/PaperMC/Paper)
- [RecastSSL](https://github.com/404Setup/RecastSSL)
- [NotEnoughBandwidth](https://github.com/USS-Shenzhou/NotEnoughBandwidth)
