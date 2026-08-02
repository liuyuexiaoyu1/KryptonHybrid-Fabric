package com.xinian.KryptonHybrid.shared.network.compression;

import com.github.luben.zstd.Zstd;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.handshake.KryptonDictPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Silent auto-training of the Zstd dictionary from captured outbound traffic.
 *
 * <p>Works together with {@link ZstdSampleRecorder}: once {@code zstdDictAutoTrainEnabled}
 * and {@code zstdDictTrainingCaptureEnabled} are both on, the server periodically checks
 * the captured sample directory.  When enough samples/bytes are available (and the
 * cooldown has passed), a background thread trains a new dictionary via
 * {@link Zstd#trainFromBuffer}, atomically writes it to {@code zstdDictPath}, hot-reloads
 * it with {@link ZstdUtil#reloadDictionary()} and (optionally) clears the samples.
 * No command or restart is required.</p>
 *
 * <p>All work happens on a daemon thread — the server main thread is never blocked.</p>
 */
public final class ZstdAutoTrainer {

    private static final int CHECK_INTERVAL_TICKS = 100; // every 5 s at 20 TPS

    private static final AtomicBoolean TRAINING = new AtomicBoolean();
    /** Set by the training thread after a successful run; consumed on the main thread. */
    private static final AtomicBoolean PENDING_PUSH = new AtomicBoolean();
    private static long lastTrainAtMs;
    private static long lastCheckTick;

    private ZstdAutoTrainer() {}

    /** Called once per server tick on the main thread. Cheap — most ticks do nothing. */
    public static void tick(MinecraftServer server) {
        // Deliver the freshly trained dictionary to all online players (PLAY stage).
        // Their compression contexts are fixed for the current session, but the
        // dictionary file is persisted so reconnects pick it up; new connections
        // already receive it during configuration.
        if (PENDING_PUSH.compareAndSet(true, false)) {
            byte[] dict = ZstdUtil.getDictionaryBytesForTransfer();
            if (dict != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, new KryptonDictPayload(dict));
                }
                KryptonSharedBootstrap.LOGGER.info(
                        "Krypton Zstd auto-train: pushed {} bytes dictionary to {} online player(s)",
                        dict.length, server.getPlayerList().getPlayers().size());
            }
        }

        if (!KryptonConfig.zstdDictAutoTrainEnabled || !KryptonConfig.zstdDictTrainingCaptureEnabled) {
            return;
        }
        if (TRAINING.get()) {
            return;
        }

        if (lastCheckTick++ % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        int cooldownSec = Math.max(0, KryptonConfig.zstdDictAutoTrainIntervalSec);
        if (cooldownSec > 0 && now - lastTrainAtMs < cooldownSec * 1000L) {
            return;
        }

        if (!hasEnoughSamples()) {
            return;
        }

        triggerTraining();
    }

    private static boolean hasEnoughSamples() {
        Path dir = samplesDir();
        if (!Files.isDirectory(dir)) {
            return false;
        }
        int minSamples = Math.max(1, KryptonConfig.zstdDictAutoTrainMinSamples);
        long minBytes = Math.max(1L, KryptonConfig.zstdDictAutoTrainMinBytes);
        try (Stream<Path> stream = Files.list(dir)) {
            long count = 0;
            long total = 0;
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) continue;
                count++;
                total += Files.size(p);
                if (count >= minSamples && total >= minBytes) {
                    return true;
                }
            }
        } catch (IOException ioe) {
            return false;
        }
        return false;
    }

    private static void triggerTraining() {
        if (!TRAINING.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(ZstdAutoTrainer::train, "Krypton-Zstd-AutoTrain");
        worker.setDaemon(true);
        worker.start();
    }

    private static void train() {
        try {
            int dictSize = Math.max(1024, KryptonConfig.zstdDictAutoTrainDictSize);
            Path dir = samplesDir();
            List<byte[]> samples = loadSamples(dir, Math.max(1, KryptonConfig.zstdDictAutoTrainMinSamples));
            if (samples.size() < Math.max(1, KryptonConfig.zstdDictAutoTrainMinSamples)) {
                KryptonSharedBootstrap.LOGGER.info(
                        "Krypton Zstd auto-train skipped: only {} samples available", samples.size());
                return;
            }

            KryptonSharedBootstrap.LOGGER.info(
                    "Krypton Zstd auto-train started: {} samples, {} bytes, dictSize={}",
                    samples.size(), totalBytes(samples), dictSize);

            byte[] dict = new byte[dictSize];
            long result = Zstd.trainFromBuffer(samples.toArray(new byte[0][]), dict);
            if (Zstd.isError(result)) {
                KryptonSharedBootstrap.LOGGER.warn("Krypton Zstd auto-train failed: {}",
                        Zstd.getErrorName(result));
                return;
            }

            byte[] trained = new byte[(int) result];
            System.arraycopy(dict, 0, trained, 0, (int) result);

            Path target = Paths.get(KryptonConfig.zstdDictPath);
            writeAtomically(target, trained);

            if (KryptonConfig.zstdDictAutoTrainClearSamples) {
                clearSamples(dir);
            }

            // Force-enable dictionary use (the server owns the dictionary) and apply
            // it immediately (affects new contexts), then mark it for push to online
            // players on the next server tick.
            KryptonConfig.zstdDictEnabled = true;
            ZstdUtil.reloadDictionary();
            lastTrainAtMs = System.currentTimeMillis();
            PENDING_PUSH.set(true);

            KryptonSharedBootstrap.LOGGER.info(
                    "Krypton Zstd auto-train complete: {} bytes dictionary written to {} and applied",
                    trained.length, target.toAbsolutePath());
        } catch (Exception e) {
            KryptonSharedBootstrap.LOGGER.warn("Krypton Zstd auto-train failed", e);
        } finally {
            TRAINING.set(false);
        }
    }

    private static List<byte[]> loadSamples(Path dir, int maxSamples) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (Files.isRegularFile(p) && p.toString().endsWith(".pkt")) {
                        files.add(p);
                    }
                }
            }
        }
        // Deterministic order: by name (RUN_ID + sample number), oldest session first.
        files.sort(Comparator.comparing(Path::getFileName, Comparator.naturalOrder()));

        List<byte[]> samples = new ArrayList<>(Math.min(files.size(), maxSamples));
        for (Path p : files) {
            if (samples.size() >= maxSamples) break;
            byte[] data = Files.readAllBytes(p);
            if (data.length > 0) {
                samples.add(data);
            }
        }
        return samples;
    }

    private static long totalBytes(List<byte[]> samples) {
        long total = 0;
        for (byte[] s : samples) total += s.length;
        return total;
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void clearSamples(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(p)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ioe) {
            KryptonSharedBootstrap.LOGGER.warn("Krypton Zstd auto-train: failed to clear samples", ioe);
        }
    }

    private static Path samplesDir() {
        return Paths.get(KryptonConfig.zstdDictTrainingSamplesDir);
    }
}
