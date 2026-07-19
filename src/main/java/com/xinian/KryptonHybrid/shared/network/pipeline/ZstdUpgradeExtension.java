package com.xinian.KryptonHybrid.shared.network.pipeline;

public interface ZstdUpgradeExtension {
    void krypton$upgradeToZstd(int compressionThreshold, boolean validate);
}
