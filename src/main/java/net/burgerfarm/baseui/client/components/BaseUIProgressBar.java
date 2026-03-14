package net.burgerfarm.baseui.client.components;

import net.burgerfarm.baseui.client.core.BaseUIElement;
import net.burgerfarm.baseui.client.render.BaseUINineSliceTexture;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Supplier;

/**
 * BaseUI 基础渲染组件：状态/进度条 (ProgressBar)
 * <p>
 * 特性：Tick驱动的帧率无关动画、极小值渲染回退、幽灵残影(Ghost Trail)、动态数据绑定。
 * 该组件支持平滑插值动画、四种填充方向、可配置的纹理与颜色，并能在数值下降时显示残影，
 * 上升时提供预览效果。适用于血量条、经验条、能量条等游戏内进度展示。
 * <p>
 * 注意：动画需要在外部每帧调用 {@link #tick()} 方法驱动。
 *
 * @see BaseUIElement
 */
public class BaseUIProgressBar extends BaseUIElement<BaseUIProgressBar> {

    /**
     * 填充方向枚举。
     */
    public enum FillDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM }

    /** 进度条最小值，默认为 0.0 */
    private double min = 0.0;
    /** 进度条最大值，默认为 100.0 */
    private double max = 100.0;

    /** 当前逻辑值（跳变，由外部设置或动态拉取） */
    private double currentValue = 0.0;
    /** 用于渲染的视觉值（平滑插值目标） */
    private double displayValue = 0.0;

    /** 用于渲染的视觉值（平滑插值目标） */
    private boolean enableLerp = true;
    /** 插值系数，范围 0.01~1.0，值越大变化越快，默认 0.3 */
    private double lerpFactor = 0.3; // 在 20TPS 下，0.3 已经能提供非常丝滑的过渡

    /** 动态数值提供者，若不为 null 则每帧从中拉取当前值 */
    private Supplier<Double> valueSupplier = null;
    /** 填充方向，默认从左到右 */
    private FillDirection direction = FillDirection.LEFT_TO_RIGHT;

    // 视觉配置
    /** 背景纹理（可选） */
    private BaseUINineSliceTexture bgTexture = null;
    /** 填充纹理（可选） */
    private BaseUINineSliceTexture fillTexture = null;
    /** 背景颜色（当无纹理时使用），默认半透明黑色 */
    private int bgColor = 0x55000000;
    /** 填充颜色（当无纹理时使用），默认绿色 */
    private int fillColor = 0xFF00FF00;

    // 幽灵残影引擎
    /** 是否启用幽灵残影，默认 false */
    private boolean enableGhostTrail = false;
    /** 幽灵残影纹理（可选） */
    private BaseUINineSliceTexture ghostTexture = null;
    /** 幽灵残影颜色，默认红色 */
    private int ghostColor = 0xFFFF5555;

    /**
     * 构造一个默认的进度条，初始尺寸 100x10，不可聚焦。
     */
    public BaseUIProgressBar() {
        this.width = 100;
        this.height = 10;
        this.focusable = false;
    }

    // ==========================================
    // 链式 API
    // ==========================================

    /**
     * 设置进度条的范围（最小值和最大值）。
     *
     * @param min 最小值
     * @param max 最大值（允许等于最小值）
     * @return 当前实例，用于链式调用
     */
    public BaseUIProgressBar setRange(double min, double max) {
        this.min = min;
        this.max = Math.max(min, max); // 【采纳 AI1：不再强制 +0.0001，允许 min==max】
        return this;
    }

    /**
     * 直接设置当前逻辑值（会钳位到范围内）。
     * 若未启用插值，视觉值也会同步更新。
     *
     * @param value 新值
     * @return 当前实例
     */
    public BaseUIProgressBar setValue(double value) {
        this.currentValue = Math.max(this.min, Math.min(this.max, value));
        if (!enableLerp) this.displayValue = this.currentValue;
        return this;
    }

    /**
     * 获取当前逻辑值。
     *
     * @return 当前值
     */
    public double getValue() { return this.currentValue; }

    /**
     * 设置动态数值提供者，每帧通过 {@link #tick()} 自动更新。
     *
     * @param supplier 返回 Double 的供给器（可返回 null，此时跳过更新）
     * @return 当前实例
     */
    public BaseUIProgressBar setDynamicValue(Supplier<Double> supplier) { this.valueSupplier = supplier; return this; }

    /**
     * 配置平滑插值动画。
     *
     * @param enable 是否启用
     * @param factor 插值系数 (0.01 ~ 1.0)，值越大变化越快
     * @return 当前实例
     */
    public BaseUIProgressBar setLerp(boolean enable, double factor) {
        this.enableLerp = enable;
        this.lerpFactor = Math.max(0.01, Math.min(1.0, factor));
        return this;
    }

    /**
     * 设置填充方向。
     *
     * @param direction 方向枚举
     * @return 当前实例
     */
    public BaseUIProgressBar setDirection(FillDirection direction) { this.direction = direction; return this; }

    /**
     * 设置背景和填充纹理。
     *
     * @param bg  背景纹理（可为 null）
     * @param fill 填充纹理（可为 null）
     * @return 当前实例
     */
    public BaseUIProgressBar setTextures(BaseUINineSliceTexture bg, BaseUINineSliceTexture fill) { this.bgTexture = bg; this.fillTexture = fill; return this; }

    /**
     * 设置背景和填充颜色（当纹理为 null 时使用）。
     *
     * @param bgColor  背景颜色 (ARGB)
     * @param fillColor 填充颜色 (ARGB)
     * @return 当前实例
     */
    public BaseUIProgressBar setColors(int bgColor, int fillColor) { this.bgColor = bgColor; this.fillColor = fillColor; return this; }

    /**
     * 配置幽灵残影功能。
     *
     * @param enable      是否启用
     * @param ghostColor  残影颜色 (ARGB)
     * @param ghostTexture 残影纹理（可为 null）
     * @return 当前实例
     */
    public BaseUIProgressBar setGhostTrail(boolean enable, int ghostColor, BaseUINineSliceTexture ghostTexture) {
        this.enableGhostTrail = enable;
        this.ghostColor = ghostColor;
        this.ghostTexture = ghostTexture;
        return this;
    }

    // ==========================================
    // Tick 系统 (核心动画与数据驱动引擎)
    // ==========================================

    /**
     * 每帧更新动画和数据。
     * <p>
     * 必须在外界（如屏幕的 tick 方法）中定期调用，以保证动画独立于渲染帧率，
     * 避免卡顿时出现视觉撕裂。
     */
    public void tick() {
        onTick();
    }

    @Override
    protected void onTick() {
        if (valueSupplier != null) {
            Double val = valueSupplier.get();
            if (val != null) {
                this.currentValue = Math.max(min, Math.min(max, val));
            }
        }

        if (enableLerp) {
            double diff = currentValue - displayValue;
            double threshold = Math.max(0.001, 0.001 * (max - min));
            if (Math.abs(diff) < threshold) {
                displayValue = currentValue;
            } else {
                displayValue += diff * lerpFactor;
            }
        } else {
            displayValue = currentValue;
        }
    }

    // ==========================================
    // 渲染管线
    // ==========================================

    /**
     * 绘制进度条自身。
     *
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标（此处未使用）
     * @param mouseY      鼠标相对于当前组件的 Y 坐标（此处未使用）
     * @param partialTick 部分 tick（用于动画，此处未使用）
     * @param finalAlpha  最终透明度（已乘父级透明度）
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {

        // 1. 绘制背景
        if (bgTexture != null) {
            bgTexture.render(graphics, 0, 0, this.width, this.height, finalAlpha);
        } else {
            graphics.fill(0, 0, this.width, this.height, applyAlpha(bgColor, finalAlpha));
        }

        // 2. 利用纯数学逻辑，渲染填充条与幽灵残影
        if (enableGhostTrail) {
            double largerValue = Math.max(currentValue, displayValue);
            double smallerValue = Math.min(currentValue, displayValue);

            drawFill(graphics, largerValue, ghostTexture, ghostColor, finalAlpha); // 垫底的虚影
            drawFill(graphics, smallerValue, fillTexture, fillColor, finalAlpha); // 盖在上面的主条
        } else {
            drawFill(graphics, displayValue, fillTexture, fillColor, finalAlpha);
        }
    }

    /**
     * 内部辅助方法：根据给定数值绘制一段填充条。
     *
     * @param graphics    绘图对象
     * @param valueToDraw 要渲染的数值
     * @param tex         纹理（可为 null）
     * @param color       颜色（当纹理为 null 时使用）
     * @param finalAlpha  最终透明度
     */
    private void drawFill(GuiGraphics graphics, double valueToDraw, BaseUINineSliceTexture tex, int color, float finalAlpha) {
        double range = max - min;
        // 防御除零异常，如果 max == min，则填满或为空
        double progress = (range <= 0) ? (valueToDraw >= max ? 1.0 : 0.0) : (valueToDraw - min) / range;

        if (progress <= 0) return;

        int fillW = this.width;
        int fillH = this.height;
        int fillX = 0;
        int fillY = 0;

        switch (direction) {
            case LEFT_TO_RIGHT:
                fillW = (int) (this.width * progress);
                break;
            case RIGHT_TO_LEFT:
                fillW = (int) (this.width * progress);
                fillX = this.width - fillW;
                break;
            case BOTTOM_TO_TOP:
                fillH = (int) (this.height * progress);
                fillY = this.height - fillH;
                break;
            case TOP_TO_BOTTOM:
                fillH = (int) (this.height * progress);
                break;
        }

        if (fillW <= 0 || fillH <= 0) return;

        // 防扭曲渲染：若填充尺寸小于纹理最小安全尺寸，则回退为纯色
        if (tex != null && fillW >= tex.getMinimumWidth() && fillH >= tex.getMinimumHeight()) {
            tex.render(graphics, fillX, fillY, fillW, fillH, finalAlpha);
        } else {
            graphics.fill(fillX, fillY, fillX + fillW, fillY + fillH, applyAlpha(color, finalAlpha));
        }
    }

    /**
     * 将 RGB 颜色与透明度混合，返回 ARGB 颜色值。
     *
     * @param rgbColor 原始 RGB 颜色（忽略高位）
     * @param alpha    透明度 0.0~1.0
     * @return 混合后的 ARGB 颜色
     */
    private int applyAlpha(int rgbColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (rgbColor & 0x00FFFFFF) | (a << 24);
    }
}
