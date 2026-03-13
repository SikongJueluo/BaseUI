package net.burgerfarm.baseui.Core;

import net.minecraft.client.gui.GuiGraphics;

public record BaseUIContext(
    GuiGraphics graphics,
    double mouseX,
    double mouseY,
    float partialTick,
    int screenWidth,
    int screenHeight,
    boolean debugEnabled
) {
}
