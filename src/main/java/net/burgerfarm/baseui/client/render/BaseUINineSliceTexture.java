package net.burgerfarm.baseui.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * BaseUI 渲染框架：九宫格纹理渲染引擎
 * <p>
 * 职责：处理 UI 边框的无损拉伸，管理透明度状态，提供绝对安全的绘图边界校验。
 * 设计：不可变数据类 (Immutable Data Class)，推荐定义为 static final 常量全局复用。
 * <p>
 * 九宫格纹理将一张图片划分为九个区域（四个角区域、四个边区域、一个中心），
 * 在渲染时，四个角保持原样，四个边和中心可以被拉伸以适配任意尺寸的目标矩形，
 * 从而实现边框的无损拉伸。
 * <p>
 * 注意：渲染时传入的目标宽度和高度必须至少等于 {@link #getMinimumWidth()} 和 {@link #getMinimumHeight()}
 * 所返回的最小尺寸，否则边角可能重叠穿模。此约束应由调用方（如布局系统）保证。
 */
public class BaseUINineSliceTexture {

    /** 纹理资源的位置 */
    private final ResourceLocation texture;
    /** 纹理图片的总宽度（像素） */
    private final int texWidth, texHeight;
    /** 源矩形左上角在纹理中的 X 坐标 */
    private final int u;
    /** 源矩形左上角在纹理中的 Y 坐标 */
    private final int v;
    /** 源矩形的宽度（像素） */
    private final int uWidth;
    /** 源矩形的高度（像素） */
    private final int vHeight;
    /** 左边缘切片宽度（像素） */
    private final int sliceLeft;
    /** 上边缘切片高度（像素） */
    private final int sliceTop;
    /** 右边缘切片宽度（像素） */
    private final int sliceRight;
    /** 下边缘切片高度（像素） */
    private final int sliceBottom;

    /** 中心区域在源纹理中的宽度（像素） */
    private final int uvCenterWidth;
    /** 中心区域在源纹理中的高度（像素） */
    private final int uvCenterHeight;

    /**
     * 构造一个九宫格纹理对象。
     *
     * @param texture      纹理资源位置
     * @param texWidth     纹理图片总宽度
     * @param texHeight    纹理图片总高度
     * @param u            源矩形左上角 X 坐标
     * @param v            源矩形左上角 Y 坐标
     * @param uWidth       源矩形宽度
     * @param vHeight      源矩形高度
     * @param sliceLeft    左边缘切片宽度（像素，不可为负）
     * @param sliceTop     上边缘切片高度（像素，不可为负）
     * @param sliceRight   右边缘切片宽度（像素，不可为负）
     * @param sliceBottom  下边缘切片高度（像素，不可为负）
     * @throws IllegalArgumentException 如果参数无效：
     *                                   - uWidth 或 vHeight 小于等于 0
     *                                   - 任何切片边距为负数
     *                                   - 切片边距之和超过源矩形尺寸
     *                                   - 源矩形超出纹理边界
     */
    public BaseUINineSliceTexture(ResourceLocation texture, int texWidth, int texHeight,
                                  int u, int v, int uWidth, int vHeight,
                                  int sliceLeft, int sliceTop, int sliceRight, int sliceBottom) {

        if (uWidth <= 0 || vHeight <= 0) throw new IllegalArgumentException("UV 尺寸必须大于 0");
        if (sliceLeft < 0 || sliceTop < 0 || sliceRight < 0 || sliceBottom < 0) throw new IllegalArgumentException("切片边距不能为负");
        if (sliceLeft + sliceRight > uWidth || sliceTop + sliceBottom > vHeight) throw new IllegalArgumentException("切边总和超过 UV 尺寸");
        if (u + uWidth > texWidth || v + vHeight > texHeight) throw new IllegalArgumentException("UV 区域超出贴图物理边界");

        this.texture = texture;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.u = u;
        this.v = v;
        this.uWidth = uWidth;
        this.vHeight = vHeight;
        this.sliceLeft = sliceLeft;
        this.sliceTop = sliceTop;
        this.sliceRight = sliceRight;
        this.sliceBottom = sliceBottom;

        this.uvCenterWidth = this.uWidth - this.sliceLeft - this.sliceRight;
        this.uvCenterHeight = this.vHeight - this.sliceTop - this.sliceBottom;
    }

    /**
     * 快速创建一个具有均匀边框的九宫格纹理。
     *
     * @param texture   纹理资源位置
     * @param texWidth  纹理图片总宽度
     * @param texHeight 纹理图片总高度
     * @param u         源矩形左上角 X 坐标
     * @param v         源矩形左上角 Y 坐标
     * @param width     源矩形宽度
     * @param height    源矩形高度
     * @param border    四个边的统一切片宽度/高度（像素）
     * @return 新的九宫格纹理实例
     */
    public static BaseUINineSliceTexture of(ResourceLocation texture, int texWidth, int texHeight,
                                            int u, int v, int width, int height, int border) {
        return new BaseUINineSliceTexture(texture, texWidth, texHeight, u, v, width, height, border, border, border, border);
    }

    /**
     * 将九宫格纹理渲染到屏幕指定位置。
     * <p>
     * ⚠️ 【排版警告】：传入的 width 和 height 绝对不能小于本组件的
     * {@link #getMinimumWidth()} 和 {@link #getMinimumHeight()}！
     * 如果强行渲染过小的尺寸，为了保证边角不被破坏拉伸，四个角的贴图将在视觉上发生重叠穿模。
     * 开发者应在 Layout 层级做好最小尺寸限制。
     *
     * @param graphics Minecraft 的绘图对象
     * @param x        目标矩形左上角 X 坐标（屏幕坐标系）
     * @param y        目标矩形左上角 Y 坐标（屏幕坐标系）
     * @param width    目标矩形宽度（像素）
     * @param height   目标矩形高度（像素）
     * @param alpha    透明度（0.0 完全透明，1.0 完全不透明）
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        if (alpha <= 0.0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int drawCenterWidth = Math.max(0, width - sliceLeft - sliceRight);
        int drawCenterHeight = Math.max(0, height - sliceTop - sliceBottom);

        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            // 上边行（左上、上中、右上）
            blit(graphics, x, y, sliceLeft, sliceTop, u, v, sliceLeft, sliceTop);
            blit(graphics, x + sliceLeft, y, drawCenterWidth, sliceTop, u + sliceLeft, v, uvCenterWidth, sliceTop);
            blit(graphics, x + width - sliceRight, y, sliceRight, sliceTop, u + uWidth - sliceRight, v, sliceRight, sliceTop);

            // 中间行（左中、中心、右中）
            blit(graphics, x, y + sliceTop, sliceLeft, drawCenterHeight, u, v + sliceTop, sliceLeft, uvCenterHeight);
            blit(graphics, x + sliceLeft, y + sliceTop, drawCenterWidth, drawCenterHeight, u + sliceLeft, v + sliceTop, uvCenterWidth, uvCenterHeight);
            blit(graphics, x + width - sliceRight, y + sliceTop, sliceRight, drawCenterHeight, u + uWidth - sliceRight, v + sliceTop, sliceRight, uvCenterHeight);

            // 下边行（左下、下中、右下）
            blit(graphics, x, y + height - sliceBottom, sliceLeft, sliceBottom, u, v + vHeight - sliceBottom, sliceLeft, sliceBottom);
            blit(graphics, x + sliceLeft, y + height - sliceBottom, drawCenterWidth, sliceBottom, u + sliceLeft, v + vHeight - sliceBottom, uvCenterWidth, sliceBottom);
            blit(graphics, x + width - sliceRight, y + height - sliceBottom, sliceRight, sliceBottom, u + uWidth - sliceRight, v + vHeight - sliceBottom, sliceRight, sliceBottom);

        } finally {
            // 恢复颜色为默认值（防止影响后续渲染）
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * 内部辅助方法：绘制九宫格的一个区块。
     * 如果绘制尺寸 <= 0，则跳过绘制。
     *
     * @param graphics 绘图对象
     * @param x        目标 X 坐标
     * @param y        目标 Y 坐标
     * @param drawW    目标宽度
     * @param drawH    目标高度
     * @param u        源纹理 X 坐标
     * @param v        源纹理 Y 坐标
     * @param uW       源纹理宽度
     * @param vH       源纹理高度
     */
    private void blit(GuiGraphics graphics, int x, int y, int drawW, int drawH, int u, int v, int uW, int vH) {
        if (drawW <= 0 || drawH <= 0) return;
        graphics.blit(texture, x, y, drawW, drawH, u, v, uW, vH, texWidth, texHeight);
    }

    /**
     * 获取当前九宫格纹理允许的最小渲染宽度。
     * 该宽度等于左右切片宽度之和，小于此值会导致边角重叠。
     *
     * @return 最小宽度（像素）
     */
    public int getMinimumWidth() { return sliceLeft + sliceRight; }

    /**
     * 获取当前九宫格纹理允许的最小渲染高度。
     * 该高度等于上下切片高度之和，小于此值会导致边角重叠。
     *
     * @return 最小高度（像素）
     */
    public int getMinimumHeight() { return sliceTop + sliceBottom; }
}