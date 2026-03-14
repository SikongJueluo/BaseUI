package net.burgerfarm.baseui.client.layouts;

import net.burgerfarm.baseui.client.core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

/**
 * BaseUI 布局：动态网格布局 (Grid / Flexbox) V1.2
 * 特性：流式排版、自动高度撑开、高性能批量添加、完美适配锚点引擎缩放、绝对安全的迭代防御。
 * 该组件自动将其子元素按照指定的列数进行流式排列，支持自定义间距、内边距和自动高度。
 * 当 autoHeight 开启时，网格高度会根据内容自动撑开，便于与滚动视口配合。
 * 所有子元素会被强制设置为 TOP_LEFT 锚点，以确保布局控制权。
 * <p><strong>
 * 注意：当您动态改变子组件的可见性（setVisible）或尺寸时，
 * 网格不会自动重新排列！您需要手动调用 {@link #rearrangeGrid()} 来更新布局。
 * 这是为了性能考虑，避免不必要的重排。
 */
public class BaseUIGrid extends BaseUIElement {

    /** 网格列数，至少为1 */
    private int columns = 1;
    /** 水平间距（像素） */
    private int spacingX = 0;
    /** 垂直间距（像素） */
    private int spacingY = 0;
    /** 水平内边距（像素） */
    private int paddingX = 0;
    /** 垂直内边距（像素） */
    private int paddingY = 0;

    /** 是否自动撑开高度以适应内容（默认 true） */
    private boolean autoHeight = true;

    /**
     * 构造一个默认网格。
     * 默认不启用裁剪，不可聚焦。
     */
    public BaseUIGrid() {
        this.setClipToBounds(false);
        this.setFocusable(false);
    }

    // ==========================================
    // 链式排版 API
    // ==========================================

    /**
     * 设置网格列数，并重新排版。
     * @param columns 列数（至少为1）
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid setColumns(int columns) {
        this.columns = Math.max(1, columns);
        this.rearrangeGrid();
        return this;
    }

    /**
     * 设置水平与垂直间距，并重新排版。
     * @param spacingX 水平间距（像素）
     * @param spacingY 垂直间距（像素）
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid setSpacing(int spacingX, int spacingY) {
        this.spacingX = spacingX;
        this.spacingY = spacingY;
        this.rearrangeGrid();
        return this;
    }

    /**
     * 设置内边距，并重新排版。
     * @param paddingX 水平内边距（像素）
     * @param paddingY 垂直内边距（像素）
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid setPadding(int paddingX, int paddingY) {
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.rearrangeGrid();
        return this;
    }

    /**
     * 设置是否自动撑开高度。
     * @param autoHeight true 表示自动撑开，false 表示固定高度
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid setAutoHeight(boolean autoHeight) {
        this.autoHeight = autoHeight;
        this.rearrangeGrid();
        return this;
    }

    // ==========================================
    // 生命周期与高性能批处理
    // ==========================================

    /**
     * 重写添加子组件方法，添加后自动重新排版。
     * @param child 要添加的子组件
     * @return 自身实例（链式调用）
     */
    @Override
    public BaseUIGrid addChild(BaseUIElement child) {
        super.addChild(child);
        this.rearrangeGrid();
        return this;
    }

    /**
     * 高性能批量添加 API：一次性添加多个子组件，仅触发一次重新排版，避免 O(N²) 布局抖动。
     * @param newChildren 要添加的子组件数组
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid addChildren(BaseUIElement... newChildren) {
        for (BaseUIElement child : newChildren) {
            super.addChild(child);
        }
        this.rearrangeGrid(); // 所有子组件挂载完毕后，仅执行一次终极排版！
        return this;
    }

    /**
     * 批量添加子组件的重载版本，接受 Iterable 集合。
     * @param newChildren 要添加的子组件集合
     * @return 自身实例（链式调用）
     */
    public BaseUIGrid addChildren(Iterable<? extends BaseUIElement> newChildren) {
        for (BaseUIElement child : newChildren) {
            super.addChild(child);
        }
        this.rearrangeGrid();
        return this;
    }

    /**
     * 重写移除子组件方法，移除后自动重新排版。
     * @param child 要移除的子组件
     * @return 自身实例（链式调用）
     */
    @Override
    public BaseUIGrid removeChild(BaseUIElement child) {
        super.removeChild(child);
        this.rearrangeGrid();
        return this;
    }

    /**
     * 重写清空子组件方法，清空后自动重新排版并重置高度。
     * @return 自身实例（链式调用）
     */
    @Override
    public BaseUIGrid clearChildren() {
        super.clearChildren();
        this.rearrangeGrid();
        return this;
    }

    // ==========================================
    // 核心流式布局引擎
    // ==========================================

    /**
     * 拦截基类的锚点更新管线。
     * 当网格自身因为父容器缩放而发生坐标变化时，强制内部元素重新流式对齐。
     */
    @Override
    protected void afterLayoutPass() {
        
        this.rearrangeGrid(); // 再根据新的宽高重新排列内部物品
    }

    /**
     * 核心排版方法：根据当前设置重新计算所有可见子组件的位置。
     * 遍历所有子组件，按照列数进行流式排列，计算每个子组件应处的坐标，
     * 并通过 setAnchor 强制其锚点为 TOP_LEFT 并设置偏移量。
     * 如果 autoHeight 为 true，则根据内容调整网格自身高度。
     * 使用浅拷贝迭代以防并发修改异常。
     */
    public void rearrangeGrid() {
        if (this.children.isEmpty()) {
            if (autoHeight) this.setHeight(paddingY * 2);
            return;
        }

        int currentX = paddingX;
        int currentY = paddingY;
        int rowMaxHeight = 0;
        int colIndex = 0;

        // 【安全防线升级】引入浅拷贝迭代，彻底杜绝极小概率的并发修改异常！
        List<BaseUIElement> safeChildren = new ArrayList<>(this.children);

        for (BaseUIElement child : safeChildren) {
            if (!child.isVisible()) continue;

            // 利用 V13.1 的锚点引擎：强制相对于网格左上角偏移
            child.setAnchor(UIAnchor.TOP_LEFT, currentX, currentY);

            rowMaxHeight = Math.max(rowMaxHeight, child.getHeight());
            colIndex++;

            if (colIndex >= columns) {
                // 当前行已满，换行
                colIndex = 0;
                currentX = paddingX;
                currentY += rowMaxHeight + spacingY;
                rowMaxHeight = 0;
            } else {
                // 同行下一个元素，水平推移
                currentX += child.getWidth() + spacingX;
            }
        }

        if (autoHeight) {
            // 计算最终所需高度：当前Y坐标 + 最后一行的高度（如果存在未闭合的行） + 底部内边距
            int finalHeight = currentY + (colIndex > 0 ? rowMaxHeight : 0) + paddingY;
            if (this.height != finalHeight) {
                // 安全触发：这里的 setSize 会被 V13.1 的 isUpdatingLayout 完美锁住，防死循环
                super.setSize(this.width, finalHeight);
            }
        }
    }

    /**
     * 自身绘制方法为空，因为网格本身不渲染任何内容（所有视觉由子组件提供）。
     * 若需要调试边框，可在此处临时添加 fill 调用。
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        // 背景留空
    }
}