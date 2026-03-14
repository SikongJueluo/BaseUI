package net.burgerfarm.baseui.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Base UI 渲染框架：轻量渐变引擎
 * 特性：零浮点除法开销、完美适配 PoseStack 矩阵偏移、严格遵循 CCW 渲染规范、零尺寸静默剔除。
 */
public class BaseUIGraphics {

    /**
     * 终极底层方法：绘制一个四角独立颜色的渐变矩形。
     * 警告：由于 BaseUIElement 的 PoseStack 已经接管了三维平移，此处的 Z 轴硬编码为 0。
     *
     * @param graphics 当前的 GuiGraphics，用于获取矩阵变换(PoseStack)
     * @param left     左侧 X 坐标 (通常为 0)
     * @param top      顶部 Y 坐标 (通常为 0)
     * @param right    右侧 X 坐标 (通常为 width)
     * @param bottom   底部 Y 坐标 (通常为 height)
     * @param colorTL  左上角颜色 (ARGB)
     * @param colorTR  右上角颜色 (ARGB)
     * @param colorBL  左下角颜色 (ARGB)
     * @param colorBR  右下角颜色 (ARGB)
     */
    public static void drawGradientQuad(GuiGraphics graphics, int left, int top, int right, int bottom,
                                        int colorTL, int colorTR, int colorBL, int colorBR) {

        // 零尺寸与反向坐标剔除:如果宽度或高度 <= 0，直接放弃渲染
        if (right <= left || bottom <= top) {
            return;
        }

        // 准备渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        Matrix4f matrix = graphics.pose().last().pose();

        // 开始录制顶点数据
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 严格遵循 CCW (逆时针) 顶点录入顺序
        // 这里的 Z 传入 0.0F，防止与 BaseUIElement 的 graphics.pose().translate 发生 Z 轴重叠翻倍！
        addVertex(bufferbuilder, matrix, left, top, colorTL);      // 左上
        addVertex(bufferbuilder, matrix, left, bottom, colorBL);   // 左下
        addVertex(bufferbuilder, matrix, right, bottom, colorBR);  // 右下
        addVertex(bufferbuilder, matrix, right, top, colorTR);     // 右上

        // 进行绘制
        tesselator.end();
    }

    /**
     * 绘制垂直渐变矩形 (上 -> 下)。
     * @param graphics    绘图对象
     * @param left        左侧 X
     * @param top         顶部 Y
     * @param right       右侧 X
     * @param bottom      底部 Y
     * @param colorTop    顶部颜色 (ARGB)
     * @param colorBottom 底部颜色 (ARGB)
     */
    public static void drawVerticalGradient(GuiGraphics graphics, int left, int top, int right, int bottom, int colorTop, int colorBottom) {
        drawGradientQuad(graphics, left, top, right, bottom, colorTop, colorTop, colorBottom, colorBottom);
    }

    /**
     * 绘制水平渐变矩形 (左 -> 右)。
     * @param graphics    绘图对象
     * @param left        左侧 X
     * @param top         顶部 Y
     * @param right       右侧 X
     * @param bottom      底部 Y
     * @param colorLeft   左侧颜色 (ARGB)
     * @param colorRight  右侧颜色 (ARGB)
     */
    public static void drawHorizontalGradient(GuiGraphics graphics, int left, int top, int right, int bottom, int colorLeft, int colorRight) {
        drawGradientQuad(graphics, left, top, right, bottom, colorLeft, colorRight, colorLeft, colorRight);
    }

    /**
     * 极速顶点写入：利用位运算直接解包 ARGB 并传入整型颜色，零对象分配。
     *
     * @param buffer 当前缓冲区
     * @param matrix 变换矩阵
     * @param x      顶点 X 坐标
     * @param y      顶点 Y 坐标
     * @param argb   顶点颜色 (ARGB)
     */
    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b = argb & 0xFF;
        buffer.vertex(matrix, x, y, (float) 0.0).color(r, g, b, a).endVertex();
    }
}