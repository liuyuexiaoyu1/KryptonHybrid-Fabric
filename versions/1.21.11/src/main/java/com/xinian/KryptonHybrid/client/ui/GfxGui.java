//#if MC < 26.1
package com.xinian.KryptonHybrid.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class GfxGui implements Gfx {

    private final GuiGraphics g;

    private GfxGui(GuiGraphics g) {
        this.g = g;
    }

    public static GfxGui of(GuiGraphics g) {
        return new GfxGui(g);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void horizontalLine(int x1, int x2, int y, int color) {
        // GuiGraphics.horizontalLine only exists in the 26.x GuiGraphicsExtractor;
        // on pre-26.x GuiGraphics it is absent in every version.
        g.fill(x1, y, x2 + 1, y + 1, color);
    }

    @Override
    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    @Override
    public void centeredText(Font font, String text, int centerX, int y, int color) {
        g.drawCenteredString(font, text, centerX, y, color);
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
