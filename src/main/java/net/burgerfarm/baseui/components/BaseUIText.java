package net.burgerfarm.baseui.components;

import net.burgerfarm.baseui.core.BaseUIElement;
import net.burgerfarm.baseui.render.BaseUINineSliceTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * BaseUI 基础渲染组件：状态文本 (Text)
 * <p>
 * 特性：支持动态 Lambda 绑定、文本对齐、极低开销的响应式尺寸自适应(AutoSize)、九宫格底板。
 * 可渲染静态文本或通过 {@link Supplier} 实时拉取动态文本，并支持左中右对齐、阴影、内边距。
 * 当开启自动尺寸时，组件会根据文本内容和背景最小尺寸自动调整宽高。
 * <p>
 * 注意：动态文本模式下，组件会在渲染时检测文本宽度变化，并自动触发布局更新，无需手动干预。
 *
 * @see BaseUIElement
 */
public class BaseUIText extends BaseUIElement<BaseUIText> {

    /**
     * 文本对齐方式枚举。
     */
    public enum Alignment { LEFT, CENTER, RIGHT }

    // 核心数据
    /** 动态文本提供者，若不为 null 则优先使用它获取文本 */
    private Supplier<Component> textSupplier = null;
    /** 静态文本，当没有动态提供者时使用 */
    private Component staticText = Component.empty();

    // 视觉排版
    /** 对齐方式，默认左对齐 */
    private Alignment alignment = Alignment.LEFT;
    /** 文本颜色 (RGB)，默认白色 */
    private int color = 0xFFFFFF;
    /** 是否绘制阴影，默认 true */
    private boolean dropShadow = true;
    /** 水平内边距（像素） */
    private int paddingX = 0;
    /** 水平内边距（像素） */
    private int paddingY = 0;
    /** 是否开启自动尺寸，默认 false */
    private boolean autoSize = false;

    /** 上一次渲染的文本宽度，用于动态尺寸检测 */
    private int lastTextWidth = -1;

    /** 背景九宫格纹理（可选） */
    private BaseUINineSliceTexture background = null;

    /**
     * 构造一个默认的文本组件，初始尺寸由后续设置决定，不可聚焦。
     */
    public BaseUIText() {
        this.focusable = false; // 纯展示组件，不抢焦点
    }

    // ==========================================
    // 链式 API：数据与排版
    // ==========================================

    /**
     * 设置静态文本（字符串形式）。
     *
     * @param text 文本内容，若为 null 则视为空字符串
     * @return 当前实例，用于链式调用
     */
    public BaseUIText setText(String text) {
        this.staticText = Component.literal(text != null ? text : "");
        this.textSupplier = null;
        if (autoSize) refreshSize();
        return this;
    }

    /**
     * 设置静态文本（组件形式）。
     *
     * @param text 文本组件，若为 null 则视为空
     * @return 当前实例
     */
    public BaseUIText setText(Component text) {
        this.staticText = text != null ? text : Component.empty();
        this.textSupplier = null;
        if (autoSize) refreshSize();
        return this;
    }

    /**
     * 设置动态文本提供者。
     * <p>
     * 例如: {@code () -> Component.literal("金币: " + playerData.getMoney())}
     *
     * @param supplier 返回 {@link Component} 的供给器
     * @return 当前实例
     */
    public BaseUIText setDynamicText(Supplier<Component> supplier) {
        this.textSupplier = supplier;
        // 重置探针，确保下一次渲染时必然触发尺寸校验
        this.lastTextWidth = -1;
        return this;
    }

    /** 设置对齐方式 */
    public BaseUIText setAlignment(Alignment alignment) { this.alignment = alignment; return this; }
    /** 设置文本颜色 (RGB) */
    public BaseUIText setColor(int color) { this.color = color; return this; }
    /** 设置是否绘制阴影 */
    public BaseUIText setDropShadow(boolean shadow) { this.dropShadow = shadow; return this; }
    /** 设置背景纹理（可为 null） */
    public BaseUIText setBackground(BaseUINineSliceTexture bg) { this.background = bg; return this; }

    /**
     * 设置内边距。
     *
     * @param px 水平内边距
     * @param py 垂直内边距
     * @return 当前实例
     */
    public BaseUIText setPadding(int px, int py) {
        this.paddingX = px;
        this.paddingY = py;
        if (autoSize) refreshSize();
        return this;
    }

    /**
     * 设置是否开启自动尺寸。
     *
     * @param autoSize true 时组件尺寸将根据文本内容和背景最小尺寸自动调整
     * @return 当前实例
     */
    public BaseUIText setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
        if (autoSize) refreshSize();
        return this;
    }

    /** 刷新尺寸（基于当前文本和背景） */
    private void refreshSize() {
        Component currentText = (textSupplier != null) ? textSupplier.get() : staticText;
        if (currentText == null) currentText = Component.empty();
        refreshSize(currentText);
    }

    @Override
    protected boolean requiresPerFrameLayoutPass() {
        return autoSize && textSupplier != null;
    }

    @Override
    protected void beforeLayoutPass() {
        if (!autoSize) {
            return;
        }
        Component currentText = (textSupplier != null) ? textSupplier.get() : staticText;
        if (currentText == null) {
            currentText = Component.empty();
        }
        int currentWidth = Minecraft.getInstance().font.width(currentText);
        if (currentWidth != this.lastTextWidth) {
            refreshSize(currentText);
        }
    }

    /** 内部重载：使用已获取的文本刷新尺寸，避免重复拉取 Supplier */
    private void refreshSize(Component currentText) {
        Font font = Minecraft.getInstance().font;

        int calcWidth = paddingX * 2 + font.width(currentText);
        int calcHeight = paddingY * 2 + font.lineHeight;

        if (background != null) {
            calcWidth = Math.max(calcWidth, background.getMinimumWidth());
            calcHeight = Math.max(calcHeight, background.getMinimumHeight());
        }

        this.lastTextWidth = font.width(currentText); // 更新探针基准
        this.setSize(calcWidth, calcHeight); // 底层自带防死循环保护
    }

    // ==========================================
    // 渲染管线
    // ==========================================

    /**
     * 绘制文本组件。
     *
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标（此处未使用）
     * @param mouseY      鼠标相对于当前组件的 Y 坐标（此处未使用）
     * @param partialTick 部分 tick（此处未使用）
     * @param finalAlpha  最终透明度（已乘父级透明度）
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        Component currentText = (textSupplier != null) ? textSupplier.get() : staticText;
        if (currentText == null) currentText = Component.empty();

        Font font = Minecraft.getInstance().font;
        int currentWidth = font.width(currentText);

        // 绘制背景
        if (background != null) {
            background.render(graphics, 0, 0, this.width, this.height, finalAlpha);
        }

        // 解析对齐方式
        int textX = paddingX;
        if (alignment == Alignment.CENTER) {
            textX = (this.width - currentWidth) / 2;
        } else if (alignment == Alignment.RIGHT) {
            textX = this.width - currentWidth - paddingX;
        }

        int textY = (this.height - font.lineHeight) / 2 + 1;

        int a = Math.max(0, Math.min(255, Math.round(255 * finalAlpha)));
        int c = (color & 0x00FFFFFF) | (a << 24);

        graphics.drawString(font, currentText, textX, textY, c, dropShadow);
    }
}
