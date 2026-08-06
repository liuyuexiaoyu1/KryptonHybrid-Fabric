package com.xinian.KryptonHybrid.shared.network.pipeline;

public interface ZstdUpgradeExtension {
    /**
     * Full Zstd upgrade (decoder + encoder), used by the server after the
     * compression-ready handshake.  Only takes effect when the connection
     * negotiated vanilla compression (setupCompression ran) — i.e. a direct
     * end-to-end link — so the peer can actually receive Zstd frames.
     */
    void krypton$upgradeToZstd(int compressionThreshold, boolean validate);

    /**
     * Decoder-only Zstd upgrade, used by the client: the decoder is installed
     * immediately (so inbound Zstd frames from a direct server are decoded),
     * while the outbound encoder stays on vanilla zlib and upgrades lazily
     * via the KRYPTON_ZSTD_SEEN frame signal.  This keeps the client
     * compatible with proxies (e.g. Velocity), which only understand vanilla
     * zlib and never emit Zstd frames.
     */
    void krypton$upgradeDecoderToZstd(int compressionThreshold, boolean validate);

    int krypton$getCompressionThreshold();

    boolean krypton$getCompressionValidate();
}
