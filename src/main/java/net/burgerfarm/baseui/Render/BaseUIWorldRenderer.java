package net.burgerfarm.baseui.Render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.OptionalDouble;

/**
 * BaseUI 空间渲染引擎 (Level 5)
 * <p>
 * 特性：纯净原版代码，采用双精度降维防抖，实现自定义渲染管线。
 * 提供在世界空间中渲染线框立方体的功能，支持透视/被遮挡切换。
 *
 * @see RenderType
 */
public class BaseUIWorldRenderer {

    /**
     * 【架构师黑魔法：伪装继承类】
     * 因为 RenderType 继承自 RenderStateShard，
     * 所以只要我们写一个继承自 RenderType 的内部类，
     * 我们就拥有了访问所有 protected 渲染状态的合法权限！
     */
    private static abstract class BaseUICustomRenderTypes extends RenderType {
        // 占位，永远不会实例化这个类
        private BaseUICustomRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int bufSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, fmt, mode, bufSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        // 静态常量，游戏启动后只 new 一次
        private static final RenderType SEE_THROUGH_LINES = RenderType.create(
                "baseui_see_through_lines",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        // 在这个类里可以直接合法访问 protected 字段
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST) // 关闭深度测试实现透视
                        .createCompositeState(false)
        );
    }

    /**
     * CS UI 空间渲染引擎 (Level 5)
     * <p>
     * 100% 纯净原版代码，采用双精度降维防抖。
     * 利用伪装继承模式优雅越过 Mojang 的 Protected 权限封锁，实现自定义渲染管线。
     * 提供在世界空间中渲染线框立方体的功能，支持透视/被遮挡切换。
     *
     * @see RenderType
     */
    public static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, AABB box, int color, boolean seeThrough) {

        float a = (color >> 24 & 255) / 255.0F;
        if (a == 0.0F) return; // 完全透明直接抛弃

        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        // 获取管线：直接调用我们伪装类里的缓存管线
        RenderType renderType = seeThrough ? BaseUICustomRenderTypes.SEE_THROUGH_LINES : RenderType.lines();
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // CPU 双精度防抖动降维：将世界坐标转换为相机局部坐标
        Vec3 camPos = camera.getPosition();
        double minX = box.minX - camPos.x;
        double minY = box.minY - camPos.y;
        double minZ = box.minZ - camPos.z;
        double maxX = box.maxX - camPos.x;
        double maxY = box.maxY - camPos.y;
        double maxZ = box.maxZ - camPos.z;

        poseStack.pushPose();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // 注入 12 条边的顶点数据
        drawLine(consumer, pose, normal, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(consumer, pose, normal, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        drawLine(consumer, pose, normal, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, pose, normal, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        drawLine(consumer, pose, normal, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, pose, normal, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, pose, normal, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

        poseStack.popPose();
    }

    /**
     * 内部原语：在 3D 空间中绘制一条线段。
     *
     * @param consumer 顶点消费者
     * @param pose    当前变换矩阵
     * @param normal  法线矩阵
     * @param x1,y1,z1 起点坐标（相机局部坐标）
     * @param x2,y2,z2 终点坐标（相机局部坐标）
     * @param r,g,b,a 颜色分量（0~1）
     */
    private static void drawLine(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (len > 0.0001F) {
            nx /= len; ny /= len; nz /= len;
        } else {
            nx = 0; ny = 1; nz = 0; // 退化为向上向量
        }

        consumer.vertex(pose, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(normal, nx, ny, nz).endVertex();
        consumer.vertex(pose, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(normal, nx, ny, nz).endVertex();
    }
}