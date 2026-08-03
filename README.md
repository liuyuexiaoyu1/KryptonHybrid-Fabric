# Krypton Hybrid

[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](LICENSE)

[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/liuyuexiaoyu1/KryptonHybrid)

**中文** | [English](README_en.md)

---

## 简介

**Krypton Hybrid** 是基于 [Krypton fnp](https://github.com/404Setup/KryptonFNP) legacy（1.19.2）的 fork。补全了 Krypton fnp lite 版本缺失的优化，新增 **zstd 压缩算法**支持，并对原始编码与 Netty 处理流程做了改进，同时保留了 [Krypton fnp](https://github.com/404Setup/KryptonFNP) 与 RecastLib 的特性。

### 主要特性

1. **混合服务器兼容** —— 提供混合服务器（hybrid server）的兼容支持。

2. **流量监控命令** —— 提供命令监控流量使用与安全状态。

3. **zstd 优先压缩** —— 以 zstd 为主要压缩算法，同时保留原版的实体追踪与通信编码优化。

4. **内置网络安全层** —— 连接限速、分阶段超时、包大小与载荷校验、异常检测、Netty 资源保护（DDoS 防护）。

---

## 安装

- **服务端与客户端都必须安装本 mod**（自定义压缩/区块格式需要双端协商）。
- 支持的 Minecraft 版本：**1.21.1 ~ 26.2**（Fabric + Fabric API）。
- 将 jar 放入 `mods/` 目录即可。

> ⚠️ **压缩算法必须双端一致**，不一致会立即损坏会话。

---

## 配置

配置文件位于 `config/krypton_hybrid.json`（服务端/客户端各自独立）。修改后使用 `/krypton config reload` 热重载。

### 1. 压缩

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `compressionAlgorithm` | `ZSTD` | 压缩算法：`ZSTD` 或 `ZLIB`。 |
| `zstdLevel` | `3` | Zstd 压缩级别，范围 [1, 22]。级别越高压缩率越好、CPU 开销越大。 |
| `zstdAdaptiveLargeLevel` | `0` | 大包（如区块数据）自适应压缩级别。`0` 关闭自适应。 |
| `zstdAdaptiveLargeThreshold` | `8192` | 超过该字节数使用自适应高级别。 |
| `zstdWorkers` | `0` | 多线程压缩线程数，`≥1` 启用原生线程池。 |
| `zstdOverlapLog` | `0` | 重叠日志（overlap log）调优参数。 |
| `zstdJobSize` | `0` | 任务大小调优参数。 |
| `zstdEnableLDM` | `false` | 启用长距离匹配（Long Distance Matching）。 |
| `zstdLongDistanceWindowLog` | `27` | LDM 窗口大小日志。 |
| `zstdStrategy` | `0` | 压缩策略（`0=default, 1=fast, 2=dfast, ...`）。 |

### 2. Zstd 字典

> 字典**完全由服务端自动训练并推送给客户端**。
> *The dictionary is fully auto-trained on the server and pushed to clients — no manual dictionary file is needed.*

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zstdDictEnabled` | `true` | 字典压缩开关（自动管理：训练完成后服务端自动启用并推送）。 |
| `zstdDictPath` | `config/krypton_hybrid.zdict` | 字典存储路径（自动管理，无需手动配置）。 |
| `zstdDictTrainingCaptureEnabled` | `true` | 捕获出口流量样本（自动训练前提，默认开启）。 |
| `zstdDictTrainingSamplesDir` | `run/krypton_zstd_samples` | 样本输出目录。 |
| `zstdDictTrainingMaxSamples` | `12000` | 单次 JVM 会话最大样本数。 |
| `zstdDictTrainingSampleEvery` | `3` | 每 N 个包捕获一个。 |
| `zstdDictTrainingMinBytes` | `24` | 样本最小序列化字节数。 |
| `zstdDictTrainingMaxBytes` | `262144` | 样本最大序列化字节数。 |
| `zstdDictAutoTrainEnabled` | `true` | 静默自动训练字典（需同时开启捕获）。达到样本阈值后自动训练并热加载，无需命令或重启。 |
| `zstdDictAutoTrainMinSamples` | `2000` | 触发自动训练的最小样本数。 |
| `zstdDictAutoTrainMinBytes` | `8388608` | 触发自动训练的最小总字节数（8 MiB）。 |
| `zstdDictAutoTrainDictSize` | `65536` | 自动训练的目标字典大小。 |
| `zstdDictAutoTrainIntervalSec` | `3600` | 两次自动训练的最小间隔（秒，0 = 无冷却）。 |
| `zstdDictAutoTrainClearSamples` | `true` | 自动训练成功后清空样本。 |

### 3. 网络安全

| 配置项 | 默认值        | 说明 |
|---|------------|---|
| `securityEnabled` | `false`    | 安全层总开关。 |
| `securityConnectionRatePerSecond` | `8`        | 每 IP 每秒持续连接数上限。 |
| `securityConnectionBurstLimit` | `20`       | 每 IP 连接突发上限。 |
| `securityConnectionQuarantineSeconds` | `30`       | 滥用地址的隔离秒数。 |
| `securityRapidReconnectWindowMs` | `1500`     | 快速重连检测窗口。 |
| `securityRapidReconnectPenalty` | `4`        | 快速重连消耗的额外令牌。 |
| `securityMaxDecompressedBytes` | `16777216` | 解压后最大载荷（16 MiB）。 |
| `securityMaxCompressionRatio` | `100`      | 最大压缩比，超限拒绝解压。 |
| `securityMinCompressedBytesForRatioCheck` | `256`      | 压缩比检查的最小压缩字节数。 |
| `securityHandshakeTimeoutSec` | `5`        | 握手阶段读取超时。 |
| `securityLoginTimeoutSec` | `10`       | 登录阶段超时。 |
| `securityPlayTimeoutSec` | `30`       | 游戏阶段超时。 |
| `securityMaxPacketBytes` | `2097152`  | 分包后最大解码帧（2 MiB）。 |
| `securityMaxCustomPayloadBytes` | `131072`   | 自定义载荷最大字节数。 |
| `securityReadLimitsEnabled` | `false`    | FriendlyByteBuf 读取侧防护（默认关闭以兼容）。 |
| `securityMaxStringChars` | `32767`    | 解码字符串最大字符数。 |
| `securityMaxCollectionElements` | `16384`    | 解码集合最大元素数。 |
| `securityMaxMapEntries` | `8192`     | 解码 Map 最大条目数。 |
| `securityMaxCountedElements` | `16384`    | readWithCount 载荷最大循环数。 |
| `securityMaxByteArrayBytes` | `2097152`  | 解码 byte[] 最大长度。 |
| `securityAnomalyStrikeThreshold` | `10`       | 加权异常积分阈值，超限强制断开。 |
| `securityWriteWatermarkLow` | `524288`   | Netty 写缓冲低水位（512 KiB）。 |
| `securityWriteWatermarkHigh` | `2097152`  | Netty 写缓冲高水位（2 MiB）。 |
| `securityMaxPendingWrites` | `4096`     | 挂起写入队列上限。 |
| `securityMaxUnwritableSeconds` | `15`       | 通道长时间不可写的强制断开秒数。 |
| `securityMinProtocolVersion` | `3`        | 握手最低协议版本。 |
| `securityMaxProtocolVersion` | `1100`     | 握手最高协议版本。 |
| `securityMaxHandshakeAddressLength` | `255`      | 握手地址最大长度。 |
| `motdCacheEnabled` | `true`     | 缓存 MOTD/状态响应。 |
| `motdCacheTtlMs` | `3000`     | MOTD 缓存有效期（0 = 关闭）。 |
| `securityStatusPingGuardEnabled` | `true`     | 现代 STATUS ping 独立限速。 |
| `securityStatusPingRatePerSecond` | `4`        | 状态 ping 每秒上限。 |
| `securityStatusPingBurstLimit` | `8`        | 状态 ping 突发上限。 |
| `securityStatusPingQuarantineSeconds` | `15`       | 状态 ping 滥用隔离秒数。 |
| `securityStatusPingSilentDrop` | `true`     | 静默关闭超限连接（不发送断开包）。 |
| `securityLegacyQueryGuardEnabled` | `true`     | 旧版（1.7 前）legacy ping 同样防护。 |

### 4. 区块优化

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `chunkOptEnabled` | `true` | 区块数据优化：生物群系增量编码 + 高度图 XOR 差分二进制压缩。 |
| `lightOptEnabled` | `true` | 光照数据 Uniform-RLE 压缩（天空光全亮层 2048B → 2B）。 |
| `blockEntityDeltaEnabled` | `true` | 方块实体 NBT 增量编码（只发送变化的键，双端需都装本 mod）。 |

### 5. 延迟区块缓存（DCC）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `dccEnabled` | `true` | DCC 总开关。 |
| `dccSizeLimit` | `60` | 每玩家最大缓存区块数。 |
| `dccDistance` | `5` | 玩家周围缓存半径（区块）。 |
| `dccTimeoutSeconds` | `30` | 缓存强制过期秒数。 |

离开视野的区块先缓存，玩家在过期前回来则**跳过重发**。

### 6. 广播缓存

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `broadcastCacheEnabled` | `true` | 广播序列化缓存：同一 Packet 实例多玩家广播时复用序列化字节。 |
| `broadcastCompressedCacheEnabled` | `true` | 压缩后字节缓存（区块/光照包）。 |

### 7. Bundle 打包

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `bundleAlwaysCompress` | `true` | Bundle 子包即使小于压缩阈值也强制压缩。 |
| `bundleCompressMinBytes` | `24` | Bundle 子包强制压缩的最小字节数。 |

### 8. 微批 flush

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `microBatchFlushEnabled` | `false` | 用延迟通道 flush 替代每 tick 立即 flush，跨 tick 合并写入。 |
| `microBatchFlushDelayMs` | `5` | 延迟 flush 毫秒数（1-20，建议 5）。 |

### 9. 运动过滤

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `motionDeltaEnabled` | `true` | 过滤变化小于阈值的运动/传送更新包。 |
| `motionDeltaThreshold` | `40` | 每轴编码容差（80 单位 ≈ 0.01 格/tick，默认 40 ≈ 0.005 格/tick）。 |
| `teleportDeltaSquared` | `1.0E-4` | 传送距离平方容差（0.01 格半径）。 |

### 10. 包合并

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `packetCoalescingEnabled` | `true` | 实体追踪 tick 内去重冗余包（速度/传送/实体数据/头部旋转/方块实体）。 |

### 11. 代理转发

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `proxyMode` | `NONE` | 代理模式：`NONE` / `VELOCITY`（现代转发）。 |
| `velocityForwardingSecret` | `""` | Velocity 转发密钥（与 proxy 配置一致）。 |

---

## 命令

部分子指令需要权限等级 2。

```
/krypton stats show          – 压缩率、带宽节省、运行时长
/krypton stats reset         – 重置计数器
/krypton stats gui           – 向客户端发送统计快照（打开仪表盘）
/krypton packets bycount [n] – 按数量统计 Top N 包类型
/krypton packets bybytes [n] – 按字节统计 Top N 包类型
/krypton packets bywire [n]  – 按压缩后链路字节统计 Top N 包类型
/krypton mods bycount [n]    – 按数量统计 Top N 模组
/krypton mods bybytes [n]    – 按字节统计 Top N 模组
/krypton mods bywire [n]     – 按压缩后链路字节统计 Top N 模组
/krypton security status     – 安全指标快照
/krypton zstd status         – Zstd 压缩/字典状态
/krypton zstd dict reload    – 重载字典
/krypton config reload       – 热重载配置文件
```

---

## 技术细节

### 1. 网络压缩管线

Krypton Hybrid 替换了 Minecraft 默认 zlib 编解码器。

| 特性 | 详情 |
|---|---|
| 主算法 | Zstd（`zstd-jni` 原生） |
| 备选算法 | zlib（`velocity-native`） |
| 级别范围 | 1 – 22（默认 3） |
| 多线程 | `workers ≥ 1` 启用原生线程池 |
| 字典 | 可选 `.zdict` 预训练文件，fail-open 或 fail-fast |

### 2. 光照优化（Uniform-RLE）

天空光照层几乎总是统一值（`0xFF`）。Krypton 将每层 2048 字节编码为 2 字节（`[0x01] + 1 byte`）。开阔天空下每个区块最多节省约 40 KB。

### 3. 区块数据优化

- **高度图**：XOR 差分 + ZigZag VarLong（相邻列相关 → 大量近零值）
- **生物群系流**：单值区块 2 字节（原 3+ 字节），多值区块分组连续

### 4. flush 合并 + 实体包打包

```
ChunkMap.tick() HEAD
  ① 恢复清理（异常恢复）
  ② 禁用所有玩家 auto-flush
  ③ 打开 EntityBundleCollector 收集窗口

  ↳ TrackedEntity 发包 → 按玩家收集

ChunkMap.tick() RETURN
  ④ 关闭窗口 → 发出 ClientboundBundlePacket（缓冲未 flush）
  ⑤ 重新启用 auto-flush → 每玩家一次内核 flush
```

| 层 | 机制 | 收益 |
|---|---|---|
| Flush 合并（Netty） | 缓冲写入，tick 末尾一次 `flush()` | 繁忙服务器减少 syscall |
| Bundle 合并（协议） | N 个实体包 → 1 个 `ClientboundBundlePacket` | 减少帧开销、提高压缩率、客户端原子应用 |

### 5. 热路径微优化

| 目标 | 优化 |
|---|---|
| `VarInt.read()` | 无分支 4 字节 `getIntLE` 快路径 |
| `VarInt/VarLong.getByteSize()` | `numberOfLeadingZeros` 查找表 |
| `FriendlyByteBuf.writeVarInt()` | 剥离 1/2/3/4/5 字节路径 |
| `FriendlyByteBuf.writeUtf()` | 单趟 `ByteBufUtil.utf8Bytes` |
| `Varint21FrameDecoder` | Velocity 衍生高效组帧 + nullping 缓解 |

---

## 多版本构建

支持 1.21.1 ~ 26.2（`settings.json` 定义版本列表），使用 [Fallen-Breath preprocessor](https://github.com/Fallen-Breath/preprocessor) 条件编译：

```bash
# 构建全部版本
./gradlew preprocessResources preprocessCode build

# 构建指定版本
./gradlew :26.2:build

# mixin 注入审计（服务器）
./gradlew runServerMixinAudit
```

---

## 测试数据

原始测试中，本 mod 可将初始包大小降至原版的 **13%**。

<img src="./test.png" alt="test" />

---

## 致谢

- [Krypton Fabric](https://modrinth.com/mod/krypton)
- [Velocity](https://github.com/PaperMC/Velocity)
- [VelocityNT Recast](https://github.com/404Setup/VelocityNT-Recast)
- [Paper](https://github.com/PaperMC/Paper)
- [RecastSSL](https://github.com/404Setup/RecastSSL)
- [NotEnoughBandwidth](https://github.com/USS-Shenzhou/NotEnoughBandwidth)
