package net.burgerfarm.baseui.client.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * UI渲染上下文
 * <p>
 * 用于在渲染帧期间传递所有必要的上下文信息。
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
 * @param //graphics      Minecraft绘图对象
 * @param //mouseX       当前鼠标X坐标（相对于屏幕）
 * @param //mouseY       当前鼠标Y坐标（相对于屏幕）
 * @param //partialTick  部分tick值（0.0-1.0），用于插值动画
 * @param //screenWidth  屏幕宽度
 * @param //screenHeight 屏幕高度
 * @param //debugEnabled 是否启用调试信息
 */

public final class BaseUIContext{

    public GuiGraphics graphics;
    public double mouseX;
    public double mouseY;
    public float partialTick;
    public int screenWidth;
    public int screenHeight;
    public boolean debugEnabled;

    public void update(GuiGraphics g, double mx, double my, float pt, int w, int h, boolean dbg) {
        this.graphics = g;
        this.mouseX = mx;
        this.mouseY = my;
        this.partialTick = pt;
        this.screenWidth = w;
        this.screenHeight = h;
        this.debugEnabled = dbg;
    }
}