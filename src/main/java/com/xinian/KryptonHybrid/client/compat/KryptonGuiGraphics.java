package com.xinian.KryptonHybrid.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

/**
 * Minimal 1.20-style drawing facade for the 1.19.2 client API.
 */
public final class KryptonGuiGraphics {
    private final PoseStack poseStack;

    public KryptonGuiGraphics(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack pose() {
        return poseStack;
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        GuiComponent.fill(poseStack, minX, minY, maxX, maxY, color);
    }

    public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        font.draw(poseStack, text, x, y, color);
    }

    public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        font.draw(poseStack, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(poseStack, font, text, x, y, color);
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        GuiComponent.enableScissor(minX, minY, maxX, maxY);
    }

    public void disableScissor() {
        GuiComponent.disableScissor();
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
