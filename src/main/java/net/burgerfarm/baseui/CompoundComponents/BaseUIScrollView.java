package net.burgerfarm.baseui.CompoundComponents;

import net.burgerfarm.baseui.Components.BaseUISlider;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

/**
 * CS UI 复合组件：滚动视口 (ScrollView) - 坚如磐石最终版
 * 特性：自动计算内容边界、支持横/纵双向滚动、无缝集成硬件裁剪、防悬空机制与动态增删 API。
 */
public class BaseUIScrollView extends BaseUIElement<BaseUIScrollView> {

    /**
     * 滚动方向枚举
     */
    public enum ScrollDirection { VERTICAL, HORIZONTAL }

    /** 当前滚动方向（默认为垂直） */
    private ScrollDirection direction = ScrollDirection.VERTICAL;

    /** 承载内容的内部画布，所有实际内容应添加至此容器 */
    private final BaseUIElement<?> contentContainer;
    /** 滚动条组件，复用 BaseUISlider */
    private final BaseUISlider scrollBar;

    /** 存储所有已添加内容的列表，用于布局计算 */
    private final List<BaseUIElement<?>> contentElements = new ArrayList<>();

    /** 滚动条的粗细（水平时为高度，垂直时为宽度） */
    private int scrollBarThickness = 6;
    /** 滚轮滚动速度（像素/单位滚动量） */
    private double scrollSpeed = 18.0;
    /** 当前滚动偏移量（始终非负，且 ≤ maxScrollOffset） */
    private double currentScrollOffset = 0.0;
    /** 最大可滚动偏移量，基于内容尺寸与视口尺寸计算得出 */
    private int maxScrollOffset = 0;

    /**
     * 构造一个默认的滚动视口，初始为垂直滚动，启用硬件裁剪。
     * 内部创建透明的内容容器和滑块，并将它们作为子组件加入。
     */
    public BaseUIScrollView() {
        this.setClipToBounds(true);

        this.contentContainer = new ScrollContent();

        this.scrollBar = new BaseUISlider()
                .setDirection(BaseUISlider.Direction.VERTICAL) // 默认与 ScrollView 保持一致
                .setRange(0.0, 1.0)
                .setValue(0.0)
                .setPlayClickSound(false)
                .setOnValueChanged(this::onScrollBarDragged);

        super.addChild(this.contentContainer);
        super.addChild(this.scrollBar);
    }

    /**
     * 专门为 ScrollView 准备的透明内容画布类，自身不渲染任何图形。
     */
    private static class ScrollContent extends BaseUIElement<ScrollContent> {
        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            // 画布本身是透明的，不渲染任何东西
        }
    }

    // ==========================================
    // 链式 API 与 布局刷新
    // ==========================================

    /**
     * 设置滚动方向，并同步更新内部滑块的渲染方向。
     * @param direction 滚动方向
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView setDirection(ScrollDirection direction) {
        this.direction = direction;
        // 同步更新内部滑块的渲染方向
        if (direction == ScrollDirection.VERTICAL) {
            this.scrollBar.setDirection(BaseUISlider.Direction.VERTICAL);
        } else {
            this.scrollBar.setDirection(BaseUISlider.Direction.HORIZONTAL);
        }
        refreshLayout();
        return this;
    }

    /**
     * 设置滚动条的粗细（宽度或高度）。
     * @param thickness 粗细值（像素）
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView setScrollBarThickness(int thickness) {
        this.scrollBarThickness = thickness;
        refreshLayout();
        return this;
    }

    /**
     * 设置滚轮滚动速度。
     * @param speed 速度值（像素/单位滚动量）
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView setScrollSpeed(double speed) {
        this.scrollSpeed = speed;
        return this;
    }

    /**
     * 向内容区域添加一个子组件，并刷新布局。
     * @param child 要添加的子组件
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView addContent(BaseUIElement<?> child) {
        this.contentElements.add(child);
        this.contentContainer.addChild(child);
        refreshLayout();
        return this;
    }

    /**
     * 从内容区域移除一个子组件，并刷新布局。
     * @param child 要移除的子组件
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView removeContent(BaseUIElement<?> child) {
        if (this.contentElements.remove(child)) {
            this.contentContainer.removeChild(child);
            refreshLayout();
        }
        return this;
    }

    /**
     * 清空所有内容组件，重置滚动偏移并刷新布局。
     * @return 自身实例（链式调用）
     */
    public BaseUIScrollView clearContent() {
        this.contentElements.clear();
        this.contentContainer.clearChildren();
        this.currentScrollOffset = 0.0;
        refreshLayout();
        return this;
    }
    public int getScrollBarThickness() { return this.scrollBarThickness; }

    public double getScrollOffset() { return this.currentScrollOffset; }

    /**
     * 外部代码强制设置滚动位置。
     * 警告：调用此方法前请确保已执行过 refreshLayout()，否则 maxScrollOffset 可能不准确。
     * @param offset 目标像素偏移
     */
    public void setScrollOffset(double offset) {
        // 钳制合法范围
        double clamped = Math.max(0, Math.min(this.maxScrollOffset, offset));

        // 如果偏移量无变化直接返回
        if (Math.abs(this.currentScrollOffset - clamped) < 1e-4) return;

        this.currentScrollOffset = clamped;

        // 同步内容位置,触发视锥体剔除检测
        updateContentPosition();

        // 同步滑块视觉位置
        if (this.maxScrollOffset > 0) {
            this.scrollBar.setValue(this.currentScrollOffset / this.maxScrollOffset);
        } else {
            this.scrollBar.setValue(0);
        }
    }

    /**
     * 重写 setSize 方法，在尺寸变化时自动刷新布局。
     */
    @Override
    public BaseUIScrollView setSize(int width, int height) {
        super.setSize(width, height);
        refreshLayout();
        return this;
    }

    /**
     * 核心排版引擎：动态探测内容边界，计算滚动差值。
     * 该方法会：
     * 1. 遍历所有可见内容，计算最远边界及内容容器的实际尺寸。
     * 2. 计算最大可滚动距离（maxScrollOffset）。
     * 3. 钳制当前滚动偏移以防悬空。
     * 4. 根据是否需要滚动条动态显隐并设置其位置。
     * 5. 更新内容容器的位置。
     */
    public void refreshLayout() {
        if (this.width == 0 || this.height == 0) return;

        // 1. 寻找内容的最远边界，并同步计算内容容器的真实宽高
        int contentMax = 0;
        int contentW = 0;
        int contentH = 0;

        for (BaseUIElement<?> child : contentElements) {
            if (!child.isVisible()) continue; // 忽略不可见的组件

            contentW = Math.max(contentW, child.getX() + child.getWidth());
            contentH = Math.max(contentH, child.getY() + child.getHeight());

            if (direction == ScrollDirection.VERTICAL) {
                contentMax = Math.max(contentMax, child.getY() + child.getHeight());
            } else {
                contentMax = Math.max(contentMax, child.getX() + child.getWidth());
            }
        }

        // 将真实的物理边界同步给内容容器，方便后续可能的 Debug 边框绘制
        this.contentContainer.setSize(contentW, contentH);

        // 2. 计算最大允许滚动距离
        int viewportSize = (direction == ScrollDirection.VERTICAL) ? this.height : this.width;
        this.maxScrollOffset = Math.max(0, contentMax - viewportSize);

        // 防悬空机制：如果容器尺寸突变导致 maxScrollOffset 减小，必须钳制当前进度
        this.currentScrollOffset = Math.max(0, Math.min(this.maxScrollOffset, this.currentScrollOffset));

        // 3. 动态显隐滚动条并设置位置
        if (this.maxScrollOffset > 0) {
            this.scrollBar.setVisible(true);
            if (direction == ScrollDirection.VERTICAL) {
                this.scrollBar.setPos(this.width - scrollBarThickness, 0)
                        .setSize(scrollBarThickness, this.height);
            } else {
                this.scrollBar.setPos(0, this.height - scrollBarThickness)
                        .setSize(this.width, scrollBarThickness);
            }
            // 反向同步校准后的进度给 UI
            this.scrollBar.setValue(this.currentScrollOffset / this.maxScrollOffset);
        } else {
            this.scrollBar.setVisible(false);
            this.currentScrollOffset = 0;
            this.scrollBar.setValue(0);
        }

        updateContentPosition();
    }

    // ==========================================
    // 滚动数学与事件拦截
    // ==========================================

    /**
     * 当滚动条被拖动时回调，根据进度更新滚动偏移并刷新内容位置。
     * @param progress 滑块进度值（0.0 ~ 1.0）
     */
    private void onScrollBarDragged(double progress) {
        this.currentScrollOffset = progress * this.maxScrollOffset;
        updateContentPosition();
    }

    /**
     * 根据当前滚动偏移更新内容容器的位置（取负值以实现视口平移），并执行极速的视锥体剔除。
     */
    private void updateContentPosition() {
        int scrollOffsetInt = (int) this.currentScrollOffset;

        // 修改内容画布的绝对坐标，基类的事件探测将自动跟随
        if (direction == ScrollDirection.VERTICAL) {
            this.contentContainer.setPos(0, -scrollOffsetInt);
        } else {
            this.contentContainer.setPos(-scrollOffsetInt, 0);
        }

        // 2. 执行视锥体剔除 (看不见的东西坚决不画)
        // 计算当前视口的物理可见区域 (基于 ScrollView 自身的尺寸)
        int viewportEnd = scrollOffsetInt + (direction == ScrollDirection.VERTICAL ? this.height : this.width);

        for (BaseUIElement<?> child : this.contentElements) {
            // 比对主滚动轴上的坐标
            int childStart, childEnd;
            if (direction == ScrollDirection.VERTICAL) {
                childStart = child.getY();
                childEnd = child.getY() + child.getHeight();
            } else {
                childStart = child.getX();
                childEnd = child.getX() + child.getWidth();
            }

            // 判断子组件是否在视口范围内 (容忍 1 像素误差防止边缘闪烁)
            boolean isVisibleInViewport = (childEnd >= scrollOffsetInt - 1) && (childStart <= viewportEnd + 1);

            boolean shouldBeCulled = !isVisibleInViewport;

            if (child.culledByScissor != shouldBeCulled) {
                child.setCulledByScissor(!isVisibleInViewport);
            }
        }
    }

    /**
     * 处理鼠标滚轮事件，实现滚动功能。
     * @param mouseX 鼠标相对于组件左上角的 X 坐标
     * @param mouseY 鼠标相对于组件左上角的 Y 坐标
     * @param delta 滚轮滚动量（正负表示方向）
     * @return true 表示事件被消费（阻止进一步传递）
     */
    @Override
    protected boolean onScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScrollOffset <= 0) return false;

        // 滚轮步进计算 (方向反转匹配 Minecraft 原生逻辑)
        double newOffset = this.currentScrollOffset - (delta * scrollSpeed);
        newOffset = Math.max(0, Math.min(this.maxScrollOffset, newOffset));

        if (newOffset != this.currentScrollOffset) {
            this.currentScrollOffset = newOffset;

            double progress = this.currentScrollOffset / this.maxScrollOffset;
            this.scrollBar.setValue(progress);

            updateContentPosition();
            return true; // 吞噬滚轮事件
        }
        return false;
    }

    /**
     * 自身绘制方法为空，因为滚动视口本身不渲染内容（所有内容由子组件绘制）。
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        // 背景留空
    }
}