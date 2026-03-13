package net.burgerfarm.baseui.Client.Render;

import net.minecraft.client.gui.GuiGraphics;

public record BaseUIRenderContext(
    GuiGraphics graphics,
    double mouseX,
    double mouseY,
    float partialTick,
    int screenWidth,
    int screenHeight,
    boolean debugEnabled
) {
}
