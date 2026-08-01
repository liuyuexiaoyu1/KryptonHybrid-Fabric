package com.xinian.KryptonHybrid.client.ui;

import net.minecraft.client.gui.Font;

/**
 * Cross-version drawing abstraction over the Minecraft gui graphics APIs.
 *
 * <p>26.x exposes {@code GuiGraphicsExtractor} (state-extraction rendering),
 * while 1.21.x uses {@code GuiGraphics}. All UI drawing in this mod goes
 * through {@link Gfx} so the layout/theme code is shared across versions.
 * Version-specific adapters: {@link GfxExtractor} (26.x) and {@link GfxGui} (1.21.x).</p>
 */
public interface Gfx {
    void fill(int x1, int y1, int x2, int y2, int color);

    void horizontalLine(int x1, int x2, int y, int color);

    void text(Font font, String text, int x, int y, int color, boolean shadow);

    void centeredText(Font font, String text, int centerX, int y, int color);

    void enableScissor(int x1, int y1, int x2, int y2);

    void disableScissor();
}
