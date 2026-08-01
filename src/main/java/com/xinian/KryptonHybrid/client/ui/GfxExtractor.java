//#if MC >= 26.1
package com.xinian.KryptonHybrid.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * {@link Gfx} adapter over the 26.x state-extraction {@link GuiGraphicsExtractor}.
 * Only compiled for mc >= 26.
 */
public final class GfxExtractor implements Gfx {

    private final GuiGraphicsExtractor g;

    private GfxExtractor(GuiGraphicsExtractor g) {
        this.g = g;
    }

    public static GfxExtractor of(GuiGraphicsExtractor g) {
        return new GfxExtractor(g);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void horizontalLine(int x1, int x2, int y, int color) {
        g.horizontalLine(x1, x2, y, color);
    }

    @Override
    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }

    @Override
    public void centeredText(Font font, String text, int centerX, int y, int color) {
        g.centeredText(font, text, centerX, y, color);
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }
}
//#endif
