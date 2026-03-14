package net.burgerfarm.baseui.client.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * UI渲染上下文
 * <p>
 * 这是一个不可变的记录类型（Record），用于在渲染帧期间传递所有必要的上下文信息。
 * 使用Record确保线程安全且不可变。
 * <p>
 * 包含的信息：
 * <ul>
 *   <li>graphics: Minecraft绘图对象</li>
 *   <li>mouseX/mouseY: 当前鼠标位置</li>
 *   <li>partialTick: 部分tick，用于平滑动画</li>
 *   <li>screenWidth/screenHeight: 屏幕尺寸</li>
 *   <li>debugEnabled: 是否启用调试模式</li>
 * </ul>
 *
 * @param graphics      Minecraft绘图对象
 * @param mouseX       当前鼠标X坐标（相对于屏幕）
 * @param mouseY       当前鼠标Y坐标（相对于屏幕）
 * @param partialTick  部分tick值（0.0-1.0），用于插值动画
 * @param screenWidth  屏幕宽度
 * @param screenHeight 屏幕高度
 * @param debugEnabled 是否启用调试信息
 */
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
