package com.xinian.KryptonHybrid.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xinian.KryptonHybrid.client.overlay.KryptonHudOverlay;
import com.xinian.KryptonHybrid.client.screen.KryptonStatsScreen;
import com.xinian.KryptonHybrid.shared.network.payload.StatsRequestPayload;
import com.xinian.KryptonHybrid.shared.network.payload.StatsSnapshotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//#if MC >= 12111
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
/**
 * Client-side controller for the stats dashboard.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Registers Controls-menu key bindings (open dashboard, toggle HUD)</li>
 *   <li>Caches the latest server-pushed {@link StatsSnapshotPayload}</li>
 *   <li>Opens the dashboard immediately from cache, then requests a fresh snapshot</li>
 *   <li>Registers the in-game HUD overlay layer</li>
 * </ul>
 */
public final class KryptonStatsClientController {

    private static final long SNAPSHOT_REQUEST_COOLDOWN_MS = 750L;

    //#if MC >= 12111
    /** Category for Krypton Hybrid keybindings. */
    private static final KeyMapping.Category KRYPTON_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("krypton_hybrid", "key.categories.krypton_hybrid"));
    //#elseif MC >= 12110
    //$$ /** Category for Krypton Hybrid keybindings. */
    //$$ private static final KeyMapping.Category KRYPTON_CATEGORY =
    //$$         KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("krypton_hybrid", "key.categories.krypton_hybrid"));
    //#else
    //$$ /** Category for Krypton Hybrid keybindings. */
    //$$ private static final String KRYPTON_CATEGORY = "key.categories.krypton_hybrid";
    //#endif

    private static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping(
            "key.krypton_hybrid.toggle_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            KRYPTON_CATEGORY
    );

    private static StatsSnapshotPayload latestSnapshot;
    private static long latestSnapshotReceivedAtMs;
    private static long lastRequestAtMs;
    private static long lastRequestSentAtMs;
    private static long lastRoundTripMs;
    private static long snapshotRequestCount;
    private static long snapshotReceiveCount;

    private KryptonStatsClientController() {}

    public static void init() {
        // KeyMappings auto-register via their constructor — no Fabric helper needed.
        ClientTickEvents.END_CLIENT_TICK.register(KryptonStatsClientController::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null) return;

        while (TOGGLE_HUD_KEY.consumeClick()) {
            KryptonHudOverlay.toggleVisible();
            //#if MC >= 26.1
            minecraft.player.sendOverlayMessage(
            //#else
            //$$ minecraft.player.displayClientMessage(
            //#endif
                    Component.translatable(KryptonHudOverlay.isVisible()
                            ? "gui.krypton_hybrid.hud.on"
                            : "gui.krypton_hybrid.hud.off")
                    //#if MC < 26.1
                    //$$ , true
                    //#endif
            );
        }
    }

    public static void receiveSnapshot(StatsSnapshotPayload payload) {
        latestSnapshot = payload;
        latestSnapshotReceivedAtMs = System.currentTimeMillis();
        snapshotReceiveCount++;
        if (lastRequestSentAtMs > 0L && latestSnapshotReceivedAtMs >= lastRequestSentAtMs) {
            lastRoundTripMs = latestSnapshotReceivedAtMs - lastRequestSentAtMs;
        }

        Minecraft minecraft = Minecraft.getInstance();
        //#if MC >= 26.2
        if (minecraft.gui.screen() instanceof KryptonStatsScreen screen) {
        //#else
        //$$ if (minecraft.screen instanceof KryptonStatsScreen screen) {
        //#endif
            screen.updateSnapshot(payload);
        } else if (!KryptonHudOverlay.isVisible()) {
            open(payload);
        }
    }

    public static KeyMapping toggleHudKey() { return TOGGLE_HUD_KEY; }
    public static StatsSnapshotPayload latestSnapshot() { return latestSnapshot; }
    public static long snapshotRequestCount() { return snapshotRequestCount; }
    public static long snapshotReceiveCount() { return snapshotReceiveCount; }
    public static long lastRoundTripMs() { return lastRoundTripMs; }

    public static long latestSnapshotAgeMs() {
        return latestSnapshotReceivedAtMs == 0L
                ? Long.MAX_VALUE
                : Math.max(0L, System.currentTimeMillis() - latestSnapshotReceivedAtMs);
    }

    public static void openOrRequestLatest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (latestSnapshot != null) {
            open(latestSnapshot);
        }
        requestFreshSnapshot();
        if (latestSnapshot == null) {
            //#if MC >= 26.1
            minecraft.player.sendOverlayMessage(Component.translatable("gui.krypton_hybrid.hint.requesting"));
            //#else
            //$$ minecraft.player.displayClientMessage(Component.translatable("gui.krypton_hybrid.hint.requesting"), true);
            //#endif
        }
    }

    public static void requestFreshSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastRequestAtMs < SNAPSHOT_REQUEST_COOLDOWN_MS) return;

        lastRequestAtMs = now;
        lastRequestSentAtMs = now;
        snapshotRequestCount++;
        ClientPlayNetworking.send(StatsRequestPayload.INSTANCE);
    }

    public static void open(StatsSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        //#if MC >= 26.2
        minecraft.gui.setScreen(new KryptonStatsScreen(payload));
        //#else
        //$$ minecraft.setScreen(new KryptonStatsScreen(payload));
        //#endif
    }
}
