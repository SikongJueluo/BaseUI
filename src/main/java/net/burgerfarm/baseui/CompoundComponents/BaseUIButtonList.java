package net.burgerfarm.baseui.CompoundComponents;

import net.burgerfarm.baseui.Components.BaseUIButton;
import net.burgerfarm.baseui.core.BaseUIElement;
import net.burgerfarm.baseui.Layouts.BaseUIGrid;
import net.burgerfarm.baseui.Render.BaseUINineSliceTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CS UI 复合组件：按钮列表 (ButtonList)
 * <p>
 * 提供一个可滚动的按钮列表，支持单选/多选模式，并允许自定义按钮的样式（颜色、纹理）。
 * 内部使用 {@link BaseUIScrollView} 和 {@link BaseUIGrid} 实现滚动和自动排版。
 * 适用于少量选项的表单场景，例如设置界面、选项菜单等。
 * 对于大量数据或复杂模型，请使用 {@link BaseUIDataList}。
 * <p>
 * 特性：
 * - 单选（Radio）和多选（Checkbox）模式无缝切换
 * - 支持必选约束（至少选中一项）
 * - 按钮的四种状态（正常、悬停、按下、禁用）均可独立配置颜色和纹理
 * - 内置滚动，支持动态增删按钮
 * - 禁用按钮时自动处理选中状态的回退（单选模式下自动选择下一个可用项）
 * - 提供选中项变更回调
 * - 支持程序化选中和滚动到选中项
 */
public class BaseUIButtonList extends BaseUIElement<BaseUIButtonList> {

    /**
     * 选择模式枚举(单选/多选)
     */
    public enum SelectionMode { SINGLE, MULTIPLE }

    /** 当前选择模式，默认为单选 */
    private SelectionMode mode = SelectionMode.SINGLE;
    /** 是否强制至少选中一项（仅对单选模式有效），默认为 true */
    private boolean requireSelection = true;

    /** 内部滚动视口组件 */
    private final BaseUIScrollView scrollView;
    /** 内部网格布局，用于排列按钮 */
    private final BaseUIGrid grid;

    /** 存储所有按钮对象的列表，顺序与显示一致 */
    private final List<BaseUIButton> buttons = new ArrayList<>();
    /** 存储所有按钮对象的列表，顺序与显示一致 */
    private final Set<Integer> selectedIndices = new HashSet<>();

    /** 存储所有按钮对象的列表，顺序与显示一致 */
    private Consumer<Set<Integer>> onSelectionChanged = null;

    // 索引顺序: 0=Normal, 1=Hover, 2=Pressed, 3=Disabled
    private int[] unselectedColors = {0xFFFFFF, 0xFFFF55, 0xAAAAAA, 0x555555};
    private int[] selectedColors = {0x55FF55, 0xAAFFAA, 0x22AA22, 0x555555};

    /** 纹理配置数组，索引顺序同上 */
    private BaseUINineSliceTexture[] unselectedTexs = new BaseUINineSliceTexture[4];
    private BaseUINineSliceTexture[] selectedTexs = new BaseUINineSliceTexture[4];

    /** 按钮高度（像素），默认为20 */
    private int buttonHeight = 20;

    /**
     * 构造一个新的按钮列表，默认垂直滚动、单列网格、行间距2像素。
     */
    public BaseUIButtonList() {
        this.clipToBounds = true;

        this.scrollView = new BaseUIScrollView()
                .setDirection(BaseUIScrollView.ScrollDirection.VERTICAL);

        this.grid = new BaseUIGrid()
                .setColumns(1)
                .setSpacing(0, 2)
                .setAutoHeight(true);

        this.scrollView.addContent(grid);
        this.addChild(scrollView);
    }

    /**
     * 重写设置尺寸方法，同步更新内部滚动视口和网格的尺寸，并调整所有按钮的宽度/高度。
     *
     * @param width  新宽度
     * @param height 新高度
     * @return 自身实例，用于链式调用
     */
    @Override
    public BaseUIButtonList setSize(int width, int height) {
        super.setSize(width, height);
        this.scrollView.setSize(width, height);

        int scrollThickness = this.scrollView.getScrollBarThickness();
        int safeWidth = Math.max(1, width - scrollThickness - 2);

        this.grid.setWidth(safeWidth);

        // 遍历网格中的所有子组件，将按钮的宽度同步为安全宽度，高度保持 buttonHeight
        List<BaseUIElement<?>> safeChildren = this.grid.getChildren();
        for(BaseUIElement<?> child : safeChildren) {
            if (child instanceof BaseUIButton) {
                child.setSize(safeWidth, this.buttonHeight);
            }
        }

        this.scrollView.refreshLayout();
        return this;
    }

    /**
     * 设置选择模式及是否强制选中。
     *
     * @param mode            选择模式
     * @param requireSelection 是否强制至少选中一项（仅在单选模式下有意义）
     * @return 自身实例
     */
    public BaseUIButtonList setSelectionMode(SelectionMode mode, boolean requireSelection) {
        this.mode = mode;
        this.requireSelection = requireSelection;

        // 如果从多选切换为单选且当前选中项多于一个，则只保留第一个
        if (mode == SelectionMode.SINGLE && selectedIndices.size() > 1) {
            int first = selectedIndices.iterator().next();
            selectedIndices.clear();
            selectedIndices.add(first);
            refreshAllButtonStyles();
            fireSelectionEvent();
        }
        return this;
    }

    /**
     * 设置选中状态变更回调。
     *
     * @param callback 回调函数，接收当前选中索引的不可变集合
     * @return 自身实例
     */
    public BaseUIButtonList setOnSelectionChanged(Consumer<Set<Integer>> callback) {
        this.onSelectionChanged = callback;
        return this;
    }

    /**
     * 设置按钮的统一高度，并触发布局更新。
     *
     * @param height 新高度（像素）
     * @return 自身实例
     */
    public BaseUIButtonList setButtonHeight(int height) {
        this.buttonHeight = height;
        if (this.width > 0) this.setSize(this.width, this.height);
        return this;
    }

    /**
     * 设置未选中和选中状态下的颜色及纹理数组。
     * 数组长度必须为4，分别对应 Normal, Hover, Pressed, Disabled 四种状态。
     * 传入的数组会被克隆，防止外部修改。
     *
     * @param unselectedColors  未选中状态的颜色数组（可为 null）
     * @param selectedColors    选中状态的颜色数组（可为 null）
     * @param unselectedTexs    未选中状态的纹理数组（可为 null）
     * @param selectedTexs      选中状态的纹理数组（可为 null）
     * @return 自身实例
     * @throws IllegalArgumentException 如果任一非空数组长度不为4
     */
    public BaseUIButtonList setStyles(int[] unselectedColors, int[] selectedColors,
                                      BaseUINineSliceTexture[] unselectedTexs, BaseUINineSliceTexture[] selectedTexs) {
        if (unselectedColors != null) {
            if (unselectedColors.length != 4) throw new IllegalArgumentException("unselectedColors 必须包含4个状态");
            this.unselectedColors = unselectedColors.clone();
        }
        if (selectedColors != null) {
            if (selectedColors.length != 4) throw new IllegalArgumentException("selectedColors 必须包含4个状态");
            this.selectedColors = selectedColors.clone();
        }
        if (unselectedTexs != null) {
            if (unselectedTexs.length != 4) throw new IllegalArgumentException("unselectedTexs 必须包含4个状态");
            this.unselectedTexs = unselectedTexs.clone();
        }
        if (selectedTexs != null) {
            if (selectedTexs.length != 4) throw new IllegalArgumentException("selectedTexs 必须包含4个状态");
            this.selectedTexs = selectedTexs.clone();
        }

        refreshAllButtonStyles();
        return this;
    }

    /**
     * 添加一个文本按钮（使用纯文本创建 Component）。
     *
     * @param text 按钮文本
     * @return 自身实例
     */
    public BaseUIButtonList addButton(String text) {
        return addButton(Component.literal(text));
    }

    /**
     * 添加一个按钮（使用 Component 对象）。
     *
     * @param text 按钮文本组件
     * @return 自身实例
     */
    public BaseUIButtonList addButton(Component text) {
        final int index = buttons.size();

        int scrollThickness = this.scrollView.getScrollBarThickness();
        int safeWidth = Math.max(1, this.width - scrollThickness - 2);

        BaseUIButton btn = new BaseUIButton()
                .setText(text)
                .setSize(safeWidth, buttonHeight)
                .setOnClick(() -> onButtonClicked(index));

        buttons.add(btn);
        grid.addChild(btn);

        applyButtonStyle(btn, selectedIndices.contains(index));
        scrollView.refreshLayout();

        return this;
    }

    /**
     * 移除指定索引的按钮。
     * 同时会更新剩余按钮的索引和点击回调，并处理选中状态的迁移。
     *
     * @param index 要移除的按钮索引
     * @return 自身实例
     */
    public BaseUIButtonList removeButtonAt(int index) {
        if (index >= 0 && index < buttons.size()) {
            BaseUIButton btn = buttons.remove(index);
            grid.removeChild(btn);

            boolean selectionChanged = selectedIndices.remove(index);
            Set<Integer> newSelected = new HashSet<>();
            for (Integer sel : selectedIndices) {
                if (sel > index) newSelected.add(sel - 1);
                else newSelected.add(sel);
            }
            this.selectedIndices.clear();
            this.selectedIndices.addAll(newSelected);

            // 重新绑定被删除索引及之后的按钮的点击回调，确保闭包内的索引正确
            for (int i = index; i < buttons.size(); i++) {
                final int newIndex = i;
                buttons.get(i).setOnClick(() -> onButtonClicked(newIndex));
            }

            refreshAllButtonStyles();
            scrollView.refreshLayout();
            if (selectionChanged) fireSelectionEvent();
        }
        return this;
    }

    /**
     * 设置指定索引按钮的禁用状态。
     * 如果禁用的是当前选中的按钮，且处于单选必选模式，则会自动选择下一个可用按钮。
     *
     * @param index    按钮索引
     * @param disabled 是否禁用
     * @return 自身实例
     */
    public BaseUIButtonList setButtonDisabled(int index, boolean disabled) {
        if (index >= 0 && index < buttons.size()) {
            buttons.get(index).setDisabled(disabled);

            // 如果按钮被禁用，且它之前被选中，则强制取消选中
            if (disabled && selectedIndices.contains(index)) {
                selectedIndices.remove(index);

                // 单选必选模式的自动回退保护
                // 如果当前没有任何选中项了，自动寻找下一个可用按钮并选中
                if (mode == SelectionMode.SINGLE && requireSelection && selectedIndices.isEmpty()) {
                    for (int i = 0; i < buttons.size(); i++) {
                        if (!buttons.get(i).isDisabled()) {
                            selectedIndices.add(i);
                            break;
                        }
                    }
                }

                refreshAllButtonStyles();
                fireSelectionEvent();
            }
        }
        return this;
    }

    /**
     * 程序化选中指定索引的按钮。
     * 如果按钮处于禁用状态，则操作被忽略。
     *
     * @param index 要选中的按钮索引
     * @return 自身实例
     */
    public BaseUIButtonList selectButtonAt(int index) {
        if (index >= 0 && index < buttons.size() && !buttons.get(index).isDisabled()) {
            onButtonClicked(index);
        }
        return this;
    }

    /**
     * 滚动列表，使当前选中的第一个项在视口中垂直居中。
     * 如果无选中项，则不做任何操作。
     *
     * @return 自身实例
     */
    public BaseUIButtonList scrollToSelected() {
        if (!selectedIndices.isEmpty()) {
            int firstSelected = selectedIndices.iterator().next();
            if (firstSelected >= 0 && firstSelected < buttons.size()) {
                BaseUIButton target = buttons.get(firstSelected);
                int offset = target.getY() - (this.scrollView.getHeight() - target.getHeight()) / 2;
                this.scrollView.setScrollOffset(offset);
            }
        }
        return this;
    }

    /**
     * 清空所有按钮，重置选中状态。
     *
     * @return 自身实例
     */
    public BaseUIButtonList clearButtons() {
        this.buttons.clear();
        this.grid.clearChildren();
        this.selectedIndices.clear();
        this.scrollView.refreshLayout();
        fireSelectionEvent();
        return this;
    }

    /**
     * 处理按钮点击的内部方法。
     * 根据当前模式和选中状态更新选中集合，并触发样式刷新和回调。
     *
     * @param index 被点击的按钮索引
     */
    private void onButtonClicked(int index) {
        // 禁止操作被禁用的按钮
        if (index < 0 || index >= buttons.size() || buttons.get(index).isDisabled()) return;

        boolean isSelected = selectedIndices.contains(index);

        if (mode == SelectionMode.SINGLE) {
            if (!isSelected) {
                selectedIndices.clear();
                selectedIndices.add(index);
            } else if (requireSelection) {
                return;
            } else {
                selectedIndices.remove(index);
            }
        } else {
            if (isSelected) selectedIndices.remove(index);
            else selectedIndices.add(index);
        }

        refreshAllButtonStyles();
        fireSelectionEvent();
    }

    /**
     * 触发选中状态变更回调，传递当前选中索引的副本。
     */
    private void fireSelectionEvent() {
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(new HashSet<>(selectedIndices));
        }
    }

    /**
     * 刷新所有按钮的样式（颜色和纹理），根据它们当前的选中状态。
     */
    private void refreshAllButtonStyles() {
        for (int i = 0; i < buttons.size(); i++) {
            applyButtonStyle(buttons.get(i), selectedIndices.contains(i));
        }
    }

    /**
     * 为单个按钮应用样式（颜色和纹理）。
     *
     * @param btn      目标按钮
     * @param selected 是否选中
     */
    private void applyButtonStyle(BaseUIButton btn, boolean selected) {
        int[] colors = selected ? selectedColors : unselectedColors;
        BaseUINineSliceTexture[] texs = selected ? selectedTexs : unselectedTexs;

        btn.setTextColors(colors[0], colors[1], colors[2], colors[3]);
        btn.setTextures(texs[0], texs[1], texs[2], texs[3]);
    }

    /**
     * 获取当前选中索引的副本集合。
     *
     * @return 包含所有选中索引的 Set（不可修改）
     */
    public Set<Integer> getSelectedIndices() {
        return new HashSet<>(selectedIndices);
    }

    /**
     * 自身绘制方法为空，因为所有视觉内容均由子组件（按钮、滚动条）提供。
     *
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标
     * @param mouseY      鼠标相对于当前组件的 Y 坐标
     * @param partialTick 部分 tick
     * @param finalAlpha  最终透明度
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {}
}