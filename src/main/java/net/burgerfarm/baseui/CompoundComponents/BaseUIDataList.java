package net.burgerfarm.baseui.CompoundComponents;

import net.burgerfarm.baseui.Core.BaseUIElement;
import net.burgerfarm.baseui.Layouts.BaseUIGrid;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * BaseUI 复合组件：数据驱动的动态表格/列表
 * 特性：泛型驱动、极速增量追加、绝对安全的数据变动刷新、精确行定位跳转 (ScrollTo)、空状态展示。
 *
 * @param <T> 绑定的数据模型类型
 */
public class BaseUIDataList<T> extends BaseUIElement<BaseUIDataList<T>> {

    /** 内部的滚动视口，提供滚动能力和硬件裁剪 */
    private final BaseUIScrollView scrollView;
    /** 内部的行容器，使用单列网格实现行的垂直堆叠 */
    private final BaseUIGrid rowContainer;

    /** 数据源列表 */
    private List<T> dataSource = new ArrayList<>();
    /** 行工厂：将数据模型转换为 UI 组件 */
    private Function<T, BaseUIElement<?>> rowFactory = null;
    /** 数据为空时显示的自定义 UI (如 "暂无数据" 文本) */
    private BaseUIElement<?> emptyStateUI = null;

    /**
     * 构造一个动态数据列表。
     * 默认启用裁剪，并初始化内部的滚动视口和网格容器。
     */
    public BaseUIDataList() {
        this.clipToBounds = true;

        this.scrollView = new BaseUIScrollView()
                .setDirection(BaseUIScrollView.ScrollDirection.VERTICAL);

        // 使用 1 列的网格作为列表容器，自动撑开高度
        this.rowContainer = new BaseUIGrid()
                .setColumns(1)
                .setSpacing(0, 2)
                .setAutoHeight(true);

        this.scrollView.addContent(rowContainer);
        this.addChild(scrollView);
    }

    // ==========================================
    // 布局与状态 API
    // ==========================================

    /**
     * 设置列表的整体尺寸，并同步调整内部视口和行容器的宽度。
     * @param width  新宽度
     * @param height 新高度
     * @return 自身实例
     */
    @Override
    public BaseUIDataList<T> setSize(int width, int height) {
        super.setSize(width, height);
        this.scrollView.setSize(width, height);

        // 动态获取滚动条厚度，增加负数保护，预留 2px 边距防贴边
        int scrollThickness = this.scrollView.getScrollBarThickness();
        int safeWidth = Math.max(0, width - scrollThickness - 2);
        this.rowContainer.setWidth(safeWidth);
        return this;
    }

    /**
     * 设置行之间的间距。
     * @param spacingY 垂直间距 (像素)
     * @return 自身实例
     */
    public BaseUIDataList<T> setRowSpacing(int spacingY) {
        this.rowContainer.setSpacing(0, spacingY);
        return this;
    }

    /**
     * 设置数据为空时显示的 UI 组件。
     * @param emptyStateUI 自定义空状态组件 (可为 null)
     * @return 自身实例
     */
    public BaseUIDataList<T> setEmptyState(BaseUIElement<?> emptyStateUI) {
        if (this.emptyStateUI != null) this.removeChild(this.emptyStateUI);
        this.emptyStateUI = emptyStateUI;
        if (this.emptyStateUI != null) {
            this.emptyStateUI.setAnchor(UIAnchor.CENTER);
            this.addChild(this.emptyStateUI);
        }
        checkEmptyState();
        return this;
    }

    /**
     * 根据数据源是否为空，更新空状态 UI 和滚动视口的可见性。
     */
    private void checkEmptyState() {
        if (emptyStateUI != null) {
            boolean isEmpty = this.dataSource.isEmpty();
            emptyStateUI.setVisible(isEmpty);
            scrollView.setVisible(!isEmpty); // 没数据时隐藏滚动视口
        }
    }

    // ==========================================
    // 核心数据查询 API
    // ==========================================

    /** @return 当前数据条目数 */
    public int getItemCount() { return this.dataSource.size(); }

    /**
     * 获取指定索引的数据项。
     * @param index 索引
     * @return 数据项，若索引无效则返回 null
     */
    public T getItem(int index) {
        if (index >= 0 && index < this.dataSource.size()) {
            return this.dataSource.get(index);
        }
        return null;
    }

    /** @return 数据源的浅拷贝副本 */
    public List<T> getData() { return new ArrayList<>(this.dataSource); }

    // ==========================================
    // 核心数据驱动管线
    // ==========================================

    /**
     * 设置行工厂函数，用于将数据模型转换为对应的 UI 组件。
     * @param factory 工厂函数
     * @return 自身实例
     */
    public BaseUIDataList<T> setRowFactory(Function<T, BaseUIElement<?>> factory) {
        this.rowFactory = factory;
        return this;
    }

    /**
     * 完全替换数据源并刷新表格。
     * @param data 新的数据列表
     * @return 自身实例
     */
    public BaseUIDataList<T> setData(List<T> data) {
        this.dataSource = new ArrayList<>(data);
        this.refreshTable();
        return this;
    }

    /**
     * 对数据源进行排序并刷新表格。
     * @param comparator 比较器
     * @return 自身实例
     */
    public BaseUIDataList<T> sort(Comparator<T> comparator) {
        this.dataSource.sort(comparator);
        this.refreshTable();
        return this;
    }

    /**
     * 极速增量：尾部追加单个数据。
     * @param item 新数据项
     * @return 自身实例
     */
    public BaseUIDataList<T> addItem(T item) {
        this.dataSource.add(item);
        if (this.rowFactory != null) {
            BaseUIElement<?> rowUI = this.rowFactory.apply(item);
            if (rowUI != null) this.rowContainer.addChild(rowUI);
        }
        checkEmptyState();
        this.scrollView.refreshLayout();
        return this;
    }

    /**
     * 极速增量：批量尾部追加数据。
     * @param items 新数据项列表
     * @return 自身实例
     */
    public BaseUIDataList<T> addAllItems(List<T> items) {
        this.dataSource.addAll(items);
        if (this.rowFactory != null) {
            List<BaseUIElement<?>> newRows = new ArrayList<>();
            for (T item : items) {
                BaseUIElement<?> rowUI = this.rowFactory.apply(item);
                if (rowUI != null) newRows.add(rowUI);
            }
            this.rowContainer.addChildren(newRows); // 网格底层只执行一次重排
        }
        checkEmptyState();
        this.scrollView.refreshLayout();
        return this;
    }

    /**
     * 安全操作：按索引删除 (强制全量刷新，彻底杜绝 UI 树错位)。
     * @param index 要删除的索引
     * @return 自身实例
     */
    public BaseUIDataList<T> removeItemAt(int index) {
        if (index >= 0 && index < this.dataSource.size()) {
            this.dataSource.remove(index);
            this.refreshTable();
        }
        return this;
    }

    /**
     * 安全操作：按索引更新 (强制全量刷新)。
     * @param index   索引
     * @param newItem 新数据项
     * @return 自身实例
     */
    public BaseUIDataList<T> updateItemAt(int index, T newItem) {
        if (index >= 0 && index < this.dataSource.size()) {
            this.dataSource.set(index, newItem);
            this.refreshTable();
        }
        return this;
    }

    /**
     * 视口控制：滚动到指定行 (默认顶部对齐)。
     * @param index 行索引
     * @return 自身实例
     */
    public BaseUIDataList<T> scrollToRow(int index) {
        return scrollToRow(index, false);
    }

    /**
     * 视口控制：滚动到指定行，可选择是否在视口中居中。
     * @param index            行索引
     * @param centerInViewport 是否居中
     * @return 自身实例
     */
    public BaseUIDataList<T> scrollToRow(int index, boolean centerInViewport) {
        // 调用 V13.2 基类新增的 getChildren() 获取安全的公共只读列表
        List<BaseUIElement<?>> gridChildren = this.rowContainer.getChildren();
        if (index >= 0 && index < gridChildren.size()) {
            BaseUIElement<?> targetRow = gridChildren.get(index);
            int rowY = targetRow.getY();

            if (centerInViewport) {
                int viewportHeight = this.scrollView.getHeight();
                int rowHeight = targetRow.getHeight();
                // 视口底层 setScrollOffset 会自动防御越界
                int centeredOffset = rowY - (viewportHeight - rowHeight) / 2;
                this.scrollView.setScrollOffset(centeredOffset);
            } else {
                this.scrollView.setScrollOffset(rowY);
            }
        }
        return this;
    }

    /**
     * 终极刷新逻辑：清空旧 UI -> 生成新 UI -> 重新排版。
     * 依赖底层的 BaseUIScrollView 自动记忆并钳制滚动进度。
     */
    public void refreshTable() {
        this.rowContainer.clearChildren();

        if (this.rowFactory != null) {
            List<BaseUIElement<?>> newRows = new ArrayList<>();
            for (T item : this.dataSource) {
                BaseUIElement<?> rowUI = this.rowFactory.apply(item);
                if (rowUI != null) newRows.add(rowUI);
            }
            this.rowContainer.addChildren(newRows);
        }

        checkEmptyState();
        this.scrollView.refreshLayout();
    }

    /**
     * 自身绘制方法为空，因为列表本身不需要渲染背景。
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标
     * @param mouseY      鼠标相对于当前组件的 Y 坐标
     * @param partialTick 部分 tick
     * @param finalAlpha  最终透明度
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        // 表格自身作为容器，不需要渲染背景
    }
}