package net.burgerfarm.baseui.Components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * BaseUI 异形组件：异形多边形与渐变引擎
 * 特性：零 GC 渲染管线、边构建边探测、抗精度丢失、静态工厂开箱即用。
 */
public class BaseUIShape extends BaseUIElement<BaseUIShape> {

    // ==========================================
    // 零 GC 扁平化数据缓冲
    // ==========================================

    /** 预计算完成的顶点 X 坐标数组 */
    private float[] bakedX = new float[0];
    /** 预计算完成的顶点 Y 坐标数组 */
    private float[] bakedY = new float[0];
    /** 预计算完成的顶点颜色数组 (ARGB) */
    private int[] bakedColors = new int[0];
    /** 预计算完成的顶点颜色数组 (ARGB) */
    private int vertexCount = 0;

    /** 渲染时的图元类型，默认为三角形 */
    private VertexFormat.Mode drawMode = VertexFormat.Mode.TRIANGLES;

    /** 是否处于构建状态 */
    private boolean isBuilding = false;
    /** 临时顶点 X 坐标数组 (构建时使用) */
    private float[] tempX = new float[16];
    /** 临时顶点 Y 坐标数组 (构建时使用) */
    private float[] tempY = new float[16];
    /** 临时顶点颜色数组 (构建时使用) */
    private int[] tempColors = new int[16];

    /** 当前构建过程中所有顶点的最小/最大 X/Y 坐标 */
    private float minX, maxX, minY, maxY;

    /**
     * 构造一个异形图形组件。默认不可聚焦。
     */
    public BaseUIShape() {
        this.focusable = false;
    }

    // ==========================================
    // 状态查询 API
    // ==========================================

    /** @return 当前顶点数量 */
    public int getVertexCount() { return vertexCount; }
    /** @return 当前设置的图元绘制模式 */
    public VertexFormat.Mode getDrawMode() { return drawMode; }
    /** @return 是否正在构建中 (即已调用 beginShape 但尚未 endShape) */
    public boolean isBuilding() { return isBuilding; }

    // ==========================================
    // 核心构建 API
    // ==========================================

    /**
     * 设置图元绘制模式。
     * @param mode 图元类型 (如三角形、三角扇、四边形等)
     * @return 自身实例，用于链式调用
     */
    public BaseUIShape setDrawMode(VertexFormat.Mode mode) {
        this.drawMode = mode;
        return this;
    }

    /**
     * 开始构建新形状。重置临时缓冲区，并初始化边界探测值。
     * @return 自身实例
     */
    public BaseUIShape beginShape() {
        this.isBuilding = true;
        this.vertexCount = 0;
        // 采纳数学界最高标准：无穷大初始化
        this.minX = Float.POSITIVE_INFINITY;
        this.minY = Float.POSITIVE_INFINITY;
        this.maxX = Float.NEGATIVE_INFINITY;
        this.maxY = Float.NEGATIVE_INFINITY;
        return this;
    }

    /**
     * 添加一个顶点。
     * @param x     相对于组件左上角的 X 坐标 (浮点数)
     * @param y     相对于组件左上角的 Y 坐标 (浮点数)
     * @param color 顶点颜色 (ARGB 格式)
     * @return 自身实例
     * @throws IllegalStateException 如果尚未调用 beginShape
     */
    public BaseUIShape addVertex(float x, float y, int color) {
        if (!isBuilding) throw new IllegalStateException("必须先调用 beginShape()！");

        if (vertexCount >= tempX.length) {
            int newCap = tempX.length * 2;
            tempX = Arrays.copyOf(tempX, newCap);
            tempY = Arrays.copyOf(tempY, newCap);
            tempColors = Arrays.copyOf(tempColors, newCap);
        }

        tempX[vertexCount] = x;
        tempY[vertexCount] = y;
        tempColors[vertexCount] = color;
        vertexCount++;

        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;

        return this;
    }

    /**
     * 完成构建，将临时数据预计算为正式数据，并自动归一化坐标，更新组件尺寸。
     * @return 自身实例
     */
    public BaseUIShape endShape() {
        this.isBuilding = false;

        // 精确裁剪数组，避免内存常驻浪费
        this.bakedX = Arrays.copyOf(tempX, vertexCount);
        this.bakedY = Arrays.copyOf(tempY, vertexCount);
        this.bakedColors = Arrays.copyOf(tempColors, vertexCount);

        if (vertexCount > 0) {
            if (minX != 0 || minY != 0) {
                for (int i = 0; i < vertexCount; i++) {
                    bakedX[i] -= minX;
                    bakedY[i] -= minY;
                }
                maxX -= minX;
                maxY -= minY;
            }
            this.setSize((int) Math.ceil(maxX), (int) Math.ceil(maxY));
        } else {
            this.setSize(0, 0);
        }

        return this;
    }

    // ==========================================
    // 静态几何工厂
    // ==========================================

    /**
     * 快速构建一个纯色矩形。
     * @param width  矩形宽度
     * @param height 矩形高度
     * @param color  填充颜色 (ARGB)
     * @return 新的 BaseUIShape 实例
     */
    public static BaseUIShape createRect(float width, float height, int color) {
        return new BaseUIShape()
                .setDrawMode(VertexFormat.Mode.TRIANGLES)
                .beginShape()
                // 遵循 Y轴朝下的逆时针 (CCW) 顺序
                .addVertex(0, 0, color).addVertex(0, height, color).addVertex(width, height, color)
                .addVertex(0, 0, color).addVertex(width, height, color).addVertex(width, 0, color)
                .endShape();
    }

    /**
     * 快速构建一个纯色圆形。
     * @param radius   半径
     * @param segments 分段数 (越大越圆滑)
     * @param color    填充颜色 (ARGB)
     * @return 新的 BaseUIShape 实例
     */
    public static BaseUIShape createCircle(float radius, int segments, int color) {
        return createRadialGradient(radius, segments, color, color);
    }

    /**
     * 快速构建一个径向渐变圆形，中心到边缘颜色渐变。
     * @param radius      半径
     * @param segments    分段数
     * @param centerColor 中心颜色 (ARGB)
     * @param edgeColor   边缘颜色 (ARGB)
     * @return 新的 BaseUIShape 实例
     */
    public static BaseUIShape createRadialGradient(float radius, int segments, int centerColor, int edgeColor) {
        BaseUIShape shape = new BaseUIShape();
        shape.setDrawMode(VertexFormat.Mode.TRIANGLE_FAN).beginShape();
        shape.addVertex(radius, radius, centerColor);
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            float vx = (float) (radius + radius * Math.cos(angle));
            float vy = (float) (radius + radius * Math.sin(angle));
            shape.addVertex(vx, vy, edgeColor);
        }
        return shape.endShape();
    }

    // ==========================================
    // 渲染管线
    // ==========================================

    /**
     * 绘制异形图形。
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标
     * @param mouseY      鼠标相对于当前组件的 Y 坐标
     * @param partialTick 部分 tick
     * @param finalAlpha  最终透明度
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        if (vertexCount == 0 || finalAlpha <= 0.0F) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = graphics.pose().last().pose();

        builder.begin(this.drawMode, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < vertexCount; i++) {
            int argb = bakedColors[i];

            int a = (argb >> 24) & 0xFF;
            if (finalAlpha < 0.999f) {
                a = Math.round(a * finalAlpha);
                a = Math.max(0, Math.min(255, a));
            }

            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            builder.vertex(matrix, bakedX[i], bakedY[i], 0.0F).color(r, g, b, a).endVertex();
        }

        tesselator.end();
    }
}