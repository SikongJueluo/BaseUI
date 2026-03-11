package net.burgerfarm.baseui.Core;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

/**
 * CS UI 框架的核心组件基类 (V13.1 锚点超进化与安全防卫版)
 * <p>
 * 本类是构建 UI 组件的基石，提供了响应式锚点布局引擎、防重入死循环锁、
 * 防并发修改崩溃、精确事件路由等高级特性。所有自定义 UI 组件都应继承此类。
 * <p>
 * 主要功能：
 * - 锚点定位：通过 {@link UIAnchor} 枚举定义组件相对于父容器的对齐方式，
 *   并支持偏移量，自动计算并更新坐标。
 * - 树形结构管理：支持添加、移除子组件，并根据 Z 轴深度自动排序。
 * - 渲染管线：集成剪裁（Scissor）栈，支持裁剪到边界、透明度叠加。
 * - 事件处理：精确的鼠标（点击、释放、拖拽、移动、滚轮）和键盘事件路由，
 *   考虑裁剪区域和可见性，并维护全局按压目标和焦点元素。
 * - 安全机制：布局更新时使用防重入锁避免递归死循环；渲染和事件处理时
 *   通过创建子组件列表的副本来防止并发修改异常。
 * - 全局状态管理：通过静态内部类 {@link UIState} 维护剪裁栈、按压目标
 *   和焦点元素，并提供重置方法。
 *
 * @param <T> 子类自身的类型，用于实现链式调用（CRTP 模式）
 */
public abstract class BaseUIElement<T extends BaseUIElement<T>> {

    /**
     * 锚点枚举，定义组件在父容器中的对齐位置。
     * 例如：TOP_LEFT 表示左上角对齐，CENTER 表示中心对齐。
     */
    public enum UIAnchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    /**
     * 静态内部类，持有 UI 系统的全局状态。
     * 使用栈来管理剪裁区域，以便嵌套裁剪正确恢复；
     * 保存当前按压的组件和拥有焦点的组件。
     */
    private static final class UIState {
        /** 剪裁区域栈，每个元素为 int[4] 表示 {left, top, right, bottom} */
        static final Stack<int[]> SCISSOR_STACK = new Stack<>();
        /** 当前被鼠标按下的组件（用于拖拽和释放事件） */
        static BaseUIElement<?> PRESS_TARGET = null;
        /** 当前拥有键盘焦点的组件 */
        static BaseUIElement<?> FOCUSED_ELEMENT = null;
    }

    // ==========================================
    // 锚点与位置属性
    // ==========================================

    /** 当前组件的锚点类型，默认为 TOP_LEFT */
    protected UIAnchor anchor = UIAnchor.TOP_LEFT;
    /** 相对于锚点的 X 轴偏移量（像素） */
    protected int anchorOffsetX = 0;
    /** 相对于锚点的 Y 轴偏移量（像素） */
    protected int anchorOffsetY = 0;

    /** 计算后的实际 X 坐标（相对于父组件） */
    protected int x, y;
    /** 组件的 Z 轴深度，值越大越靠上 */
    protected int z;
    /** 组件宽度（像素） */
    protected int width;
    /** 组件高度（像素） */
    protected int height;

    // ==========================================
    // 视觉与行为属性
    // ==========================================

    /** 透明度，0.0 完全透明，1.0 完全不透明 */
    protected float alpha = 1.0f;
    /** 是否可见，不可见的组件不参与渲染和事件处理 */
    protected boolean visible = true;
    /** 是否裁剪子组件到边界内（启用剪裁） */
    protected boolean clipToBounds = false;
    /** 是否可获得键盘焦点 */
    protected boolean focusable = false;
    /**
     * 当前渲染周期中鼠标是否悬停在此组件上。
     * 由 render() 方法更新，供子类绘制时使用。
     */
    protected boolean isHoveredForRender = false;

    // ==========================================
    // 布局安全机制
    // ==========================================

    /**
     * 【新增】防重入锁，防止 layout 更新时因递归调用陷入死循环。
     * 在 {@link #updateLayout()} 开始时设为 true，结束时设为 false。
     */
    private boolean isUpdatingLayout = false;

    // ==========================================
    // 树形结构
    // ==========================================

    /** 父组件引用，若为 null 则表示根组件 */
    protected BaseUIElement<?> parent;
    /** 子组件列表，按 Z 值排序 */
    protected final List<BaseUIElement<?>> children = new ArrayList<>();
    /** 用于按 Z 值升序排序的比较器（Z 越小越靠下） */
    private static final Comparator<BaseUIElement<?>> Z_SORTER = Comparator.comparingInt(BaseUIElement::getZ);

    // ==========================================
    // 链式调用辅助方法
    // ==========================================

    /**
     * 返回当前实例，类型转换为 T，用于实现链式调用。
     * 警告：若 T 不是实际类型，转换可能不安全，但在此框架中约定子类正确实现。
     *
     * @return 当前对象，类型为 T
     */
    @SuppressWarnings("unchecked")
    protected T self() { return (T) this; }

    // ==========================================
    // 全局状态重置
    // ==========================================

    /**
     * 重置所有全局 UI 状态。
     * 丢失焦点通知、清空按压目标和剪裁栈。
     * 通常在屏幕切换或关闭 GUI 时调用。
     */
    public static void resetAllStates() {
        if (UIState.FOCUSED_ELEMENT != null) UIState.FOCUSED_ELEMENT.onFocusLost();
        UIState.FOCUSED_ELEMENT = null;
        UIState.PRESS_TARGET = null;
        UIState.SCISSOR_STACK.clear();
    }

    /**
     * 检查并释放可能悬挂的焦点/按压状态。
     * 当组件从树中被移除或隐藏时，若它或其子组件持有全局状态，
     * 则清除这些状态并触发相应回调。
     * 此方法会在 {@link #setVisible(boolean)} 和 {@link #removeChild(BaseUIElement)} 等地方被调用。
     */
    protected void checkAndReleaseZombieStates() {
        if (UIState.FOCUSED_ELEMENT != null && UIState.FOCUSED_ELEMENT.isInSubtreeOf(this)) {
            UIState.FOCUSED_ELEMENT.onFocusLost();
            UIState.FOCUSED_ELEMENT = null;
        }
        if (UIState.PRESS_TARGET != null && UIState.PRESS_TARGET.isInSubtreeOf(this)) {
            UIState.PRESS_TARGET = null;
        }
    }

    /**
     * 判断当前组件是否在指定根组件的子树中。
     * 向上遍历父链，同时检测循环引用以避免无限循环。
     *
     * @param root 可能的根组件
     * @return 如果在子树中则返回 true
     */
    protected boolean isInSubtreeOf(BaseUIElement<?> root) {
        BaseUIElement<?> curr = this;
        List<BaseUIElement<?>> visited = new ArrayList<>();
        while (curr != null) {
            if (curr == root) return true;
            if (visited.contains(curr)) return false; // 循环检测
            visited.add(curr);
            curr = curr.parent;
        }
        return false;
    }

    // ==========================================
    // 锚点与响应式排版引擎
    // ==========================================

    /**
     * 设置组件的锚点类型。
     *
     * @param anchor 新的锚点
     * @return 当前实例，用于链式调用
     */
    public T setAnchor(UIAnchor anchor) {
        if (this.anchor != anchor) {
            this.anchor = anchor;
            this.updateLayout();
        }
        return self();
    }

    /**
     * 设置组件的锚点类型及偏移量。
     *
     * @param anchor  新的锚点
     * @param offsetX X 轴偏移量
     * @param offsetY Y 轴偏移量
     * @return 当前实例，用于链式调用
     */
    public T setAnchor(UIAnchor anchor, int offsetX, int offsetY) {
        boolean changed = (this.anchor != anchor || this.anchorOffsetX != offsetX || this.anchorOffsetY != offsetY);
        if (changed) {
            this.anchor = anchor;
            this.anchorOffsetX = offsetX;
            this.anchorOffsetY = offsetY;
            this.updateLayout();
        }
        return self();
    }

    /**
     * 核心排版计算，根据父组件大小、锚点和偏移量重新计算当前组件的实际位置 (x, y)。
     * 加入防死循环机制，若已在布局更新中则直接返回。
     * 计算完成后递归调用所有子组件的 {@link #updateLayout()}。
     */
    public void updateLayout() {
        if (isUpdatingLayout) return; // 拦截递归循环
        isUpdatingLayout = true;

        try {
            if (parent != null) {
                int pw = parent.getWidth();
                int ph = parent.getHeight();
                // 根据锚点类型计算坐标，居中公式经过优化
                switch (anchor) {
                    case TOP_LEFT:      this.x = anchorOffsetX; this.y = anchorOffsetY; break;
                    case TOP_CENTER:    this.x = (pw - this.width) / 2 + anchorOffsetX; this.y = anchorOffsetY; break;
                    case TOP_RIGHT:     this.x = pw - this.width + anchorOffsetX; this.y = anchorOffsetY; break;
                    case MIDDLE_LEFT:   this.x = anchorOffsetX; this.y = (ph - this.height) / 2 + anchorOffsetY; break;
                    case CENTER:        this.x = (pw - this.width) / 2 + anchorOffsetX; this.y = (ph - this.height) / 2 + anchorOffsetY; break;
                    case MIDDLE_RIGHT:  this.x = pw - this.width + anchorOffsetX; this.y = (ph - this.height) / 2 + anchorOffsetY; break;
                    case BOTTOM_LEFT:   this.x = anchorOffsetX; this.y = ph - this.height + anchorOffsetY; break;
                    case BOTTOM_CENTER: this.x = (pw - this.width) / 2 + anchorOffsetX; this.y = ph - this.height + anchorOffsetY; break;
                    case BOTTOM_RIGHT:  this.x = pw - this.width + anchorOffsetX; this.y = ph - this.height + anchorOffsetY; break;
                }
            } else {
                // 没有父组件时，偏移量即为绝对坐标
                this.x = anchorOffsetX;
                this.y = anchorOffsetY;
            }

            // 级联通知子组件重新计算位置
            for (BaseUIElement<?> child : children) {
                child.updateLayout();
            }
        } finally {
            isUpdatingLayout = false; // 确保锁被释放
        }
    }

    // ====== 链式 Setter (加入数值脏检查，避免不必要的布局刷新) ======

    /**
     * 设置 X 轴偏移量（相对于锚点）。
     *
     * @param x 新的偏移量
     * @return 当前实例
     */
    public T setX(int x) {
        if (this.anchorOffsetX != x) { this.anchorOffsetX = x; this.updateLayout(); }
        return self();
    }

    /**
     * 设置 Y 轴偏移量（相对于锚点）。
     *
     * @param y 新的偏移量
     * @return 当前实例
     */
    public T setY(int y) {
        if (this.anchorOffsetY != y) { this.anchorOffsetY = y; this.updateLayout(); }
        return self();
    }

    /**
     * 同时设置 X、Y 轴偏移量。
     *
     * @param x X 偏移量
     * @param y Y 偏移量
     * @return 当前实例
     */
    public T setPos(int x, int y) {
        if (this.anchorOffsetX != x || this.anchorOffsetY != y) {
            this.anchorOffsetX = x; this.anchorOffsetY = y; this.updateLayout();
        }
        return self();
    }

    /**
     * 设置组件宽度。
     *
     * @param width 新宽度
     * @return 当前实例
     */
    public T setWidth(int width) {
        if (this.width != width) { this.width = width; this.updateLayout(); }
        return self();
    }

    /**
     * 设置组件高度。
     *
     * @param height 新高度
     * @return 当前实例
     */
    public T setHeight(int height) {
        if (this.height != height) { this.height = height; this.updateLayout(); }
        return self();
    }

    /**
     * 同时设置宽度和高度。
     *
     * @param width  新宽度
     * @param height 新高度
     * @return 当前实例
     */
    public T setSize(int width, int height) {
        if (this.width != width || this.height != height) {
            this.width = width; this.height = height; this.updateLayout();
        }
        return self();
    }

    /**
     * 设置是否裁剪子组件到边界内。
     *
     * @param clip true 启用裁剪
     * @return 当前实例
     */
    public T setClipToBounds(boolean clip) { this.clipToBounds = clip; return self(); }

    /**
     * 设置是否可获得键盘焦点。
     *
     * @param focusable true 可获得焦点
     * @return 当前实例
     */
    public T setFocusable(boolean focusable) { this.focusable = focusable; return self(); }

    /**
     * 设置透明度，值将被限制在 0.0 到 1.0 之间。
     *
     * @param alpha 透明度值
     * @return 当前实例
     */
    public T setAlpha(float alpha) { this.alpha = Math.max(0f, Math.min(1f, alpha)); return self(); }

    /**
     * 设置 Z 轴深度，并通知父组件重新排序子列表。
     *
     * @param z 新的 Z 值
     * @return 当前实例
     */
    public T setZ(int z) {
        this.z = z;
        if (parent != null) parent.sortChildren();
        return self();
    }

    /**
     * 设置可见性。
     * 如果从可见变为不可见，会检查并释放可能悬挂的焦点/按压状态。
     *
     * @param visible true 可见
     * @return 当前实例
     */
    public T setVisible(boolean visible) {
        if (this.visible && !visible) this.checkAndReleaseZombieStates();
        this.visible = visible;
        return self();
    }

    // ====== 树结构管理 ======

    /**
     * 添加一个子组件。
     * 如果子组件已有父组件，会先从其原父组件移除。
     * 添加后更新子组件布局，并按 Z 值对子列表排序。
     *
     * @param child 要添加的子组件
     * @return 当前实例
     */
    public T addChild(BaseUIElement<?> child) {
        if (child.parent != null) child.parent.removeChild(child);
        child.parent = this;
        this.children.add(child);
        child.updateLayout();
        sortChildren();
        return self();
    }

    /**
     * 移除一个子组件。
     * 移除后清空其父引用，并检查释放可能的状态。
     *
     * @param child 要移除的子组件
     * @return 当前实例
     */
    public T removeChild(BaseUIElement<?> child) {
        if (this.children.remove(child)) {
            child.parent = null;
            child.checkAndReleaseZombieStates();
        }
        return self();
    }

    /**
     * 移除所有子组件。
     * 对每个子组件清空父引用并检查释放状态。
     *
     * @return 当前实例
     */
    public T clearChildren() {
        for (BaseUIElement<?> child : children) {
            child.parent = null;
            child.checkAndReleaseZombieStates();
        }
        this.children.clear();
        return self();
    }

    /**
     * 对子组件列表按 Z 值升序排序。
     * 在添加子组件或修改 Z 值时自动调用。
     */
    protected void sortChildren() { this.children.sort(Z_SORTER); }

    // ====== 焦点与按压状态 ======

    /**
     * 请求将键盘焦点设置到当前组件。
     * 只有可见且可聚焦的组件才能获得焦点。
     * 若已有焦点组件，会先通知其失去焦点。
     */
    public void requestFocus() {
        if (!visible || !focusable) return;
        if (UIState.FOCUSED_ELEMENT != this) {
            if (UIState.FOCUSED_ELEMENT != null) UIState.FOCUSED_ELEMENT.onFocusLost();
            UIState.FOCUSED_ELEMENT = this;
            this.onFocusGained();
        }
    }

    /** @return 当前组件是否拥有键盘焦点 */
    public boolean isFocused() { return UIState.FOCUSED_ELEMENT == this; }

    /** @return 当前组件是否被鼠标按下（作为按压目标） */
    public boolean isPressed() { return UIState.PRESS_TARGET == this; }

    /** @return 当前渲染周期中鼠标是否悬停在此组件上 */
    public boolean isHovered() { return isHoveredForRender; }

    /** 当组件获得焦点时回调，子类可重写以改变外观等 */
    protected void onFocusGained() {}

    /** 当组件失去焦点时回调，子类可重写 */
    protected void onFocusLost() {}

    // ==========================================
    // 渲染管线与鼠标/键盘管线
    // ==========================================

    /**
     * 渲染组件的入口方法。不可被重写，它处理了剪裁、坐标变换、状态传递和安全迭代。
     * 子类应实现 {@link #drawSelf(GuiGraphics, int, int, float, float)} 来绘制自身内容。
     *
     * @param graphics     Minecraft 的绘图对象
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     * @param partialTick  部分 tick，用于平滑动画
     * @param parentAlpha  父组件传递下来的累积透明度
     */
    public final void render(GuiGraphics graphics, double parentMouseX, double parentMouseY, float partialTick, float parentAlpha) {
        if (!visible || this.alpha <= 0.0f) return;

        // 转换为相对于当前组件的鼠标坐标
        double myInternalMouseX = parentMouseX - this.x;
        double myInternalMouseY = parentMouseY - this.y;
        this.isHoveredForRender = (parentMouseX >= this.x && parentMouseX < this.x + this.width &&
                parentMouseY >= this.y && parentMouseY < this.y + this.height);

        float finalAlpha = this.alpha * parentAlpha;

        graphics.pose().pushPose();
        graphics.pose().translate(this.x, this.y, this.z);

        boolean pushedScissor = false;
        try {
            if (clipToBounds) {
                int absX = getAbsoluteX(), absY = getAbsoluteY();
                int[] currentRect = {absX, absY, absX + width, absY + height};
                // 与父级剪裁区域求交集
                if (!UIState.SCISSOR_STACK.isEmpty()) {
                    int[] p = UIState.SCISSOR_STACK.peek();
                    currentRect[0] = Math.max(currentRect[0], p[0]);
                    currentRect[1] = Math.max(currentRect[1], p[1]);
                    currentRect[2] = Math.min(currentRect[2], p[2]);
                    currentRect[3] = Math.min(currentRect[3], p[3]);
                }
                UIState.SCISSOR_STACK.push(currentRect);
                graphics.enableScissor(currentRect[0], currentRect[1], currentRect[2], currentRect[3]);
                pushedScissor = true;
            }

            // 绘制自身内容
            drawSelf(graphics, (int)myInternalMouseX, (int)myInternalMouseY, partialTick, finalAlpha);

            // 保持安全迭代，拒绝 ConcurrentModificationException 崩溃！
            // 创建副本以避免在渲染过程中子列表被修改（例如事件处理中添加/移除子组件）
            List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
            for (BaseUIElement<?> child : safeChildren) {
                child.render(graphics, myInternalMouseX, myInternalMouseY, partialTick, finalAlpha);
            }
        } finally {
            if (pushedScissor) {
                UIState.SCISSOR_STACK.pop();
                if (UIState.SCISSOR_STACK.isEmpty()) graphics.disableScissor();
                else {
                    int[] r = UIState.SCISSOR_STACK.peek();
                    graphics.enableScissor(r[0], r[1], r[2], r[3]);
                }
            }
            graphics.pose().popPose();
        }
    }

    /**
     * 子类实现此方法以绘制自身内容。
     * 注意：坐标已经平移至组件原点，鼠标坐标已转换为相对于组件的内部坐标。
     *
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标
     * @param mouseY      鼠标相对于当前组件的 Y 坐标
     * @param partialTick 部分 tick
     * @param finalAlpha  最终透明度（已乘父级透明度）
     */
    protected abstract void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha);

    /**
     * 鼠标点击事件处理入口。
     * 从最上层子组件向下传递，直到有组件消费事件。
     * 若点击位置命中且可聚焦，则请求焦点；同时将当前组件设为按压目标。
     *
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     * @param button       鼠标按键（0=左键，1=右键，2=中键等）
     * @return true 表示事件已消费，停止传播
     */
    public final boolean mouseClicked(double parentMouseX, double parentMouseY, int button) {
        if (!visible) return false;
        boolean hit = (parentMouseX >= this.x && parentMouseX < this.x + this.width &&
                parentMouseY >= this.y && parentMouseY < this.y + this.height);

        if (clipToBounds && !hit) return false;

        double internalX = parentMouseX - this.x;
        double internalY = parentMouseY - this.y;

        // 逆序遍历子组件（Z 值大的在上层）
        List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (safeChildren.get(i).mouseClicked(internalX, internalY, button)) return true;
        }

        if (hit) {
            if (focusable) requestFocus();
            UIState.PRESS_TARGET = this;
            return onClicked(internalX, internalY, button);
        }
        return false;
    }

    /**
     * 鼠标释放事件处理入口。
     * 优先处理当前按压目标（若为自己），否则向下传播。
     *
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     * @param button       鼠标按键
     * @return true 表示事件已消费
     */
    public final boolean mouseReleased(double parentMouseX, double parentMouseY, int button) {
        if (UIState.PRESS_TARGET == this) {
            UIState.PRESS_TARGET = null;
            return onReleased(parentMouseX - this.x, parentMouseY - this.y, button);
        }
        if (!visible) return false;

        double internalX = parentMouseX - this.x;
        double internalY = parentMouseY - this.y;
        List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (safeChildren.get(i).mouseReleased(internalX, internalY, button)) return true;
        }
        return onReleased(internalX, internalY, button);
    }

    /**
     * 鼠标拖拽事件处理入口。
     * 优先发送给按压目标，否则向下传播。
     *
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     * @param button       鼠标按键
     * @param dragX        X 轴拖拽增量
     * @param dragY        Y 轴拖拽增量
     * @return true 表示事件已消费
     */
    public final boolean mouseDragged(double parentMouseX, double parentMouseY, int button, double dragX, double dragY) {
        if (UIState.PRESS_TARGET == this) {
            return onDragged(parentMouseX - this.x, parentMouseY - this.y, button, dragX, dragY);
        }
        if (!visible) return false;

        double internalX = parentMouseX - this.x;
        double internalY = parentMouseY - this.y;
        List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (safeChildren.get(i).mouseDragged(internalX, internalY, button, dragX, dragY)) return true;
        }
        return onDragged(internalX, internalY, button, dragX, dragY);
    }

    /**
     * 鼠标移动事件处理入口。
     * 更新当前组件的悬停状态，并向所有子组件传播。
     *
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     */
    public final void mouseMoved(double parentMouseX, double parentMouseY) {
        if (!visible) return;
        boolean hit = (parentMouseX >= this.x && parentMouseX < this.x + this.width &&
                parentMouseY >= this.y && parentMouseY < this.y + this.height);
        if (clipToBounds && !hit) return;

        this.isHoveredForRender = hit;

        double internalX = parentMouseX - this.x;
        double internalY = parentMouseY - this.y;
        List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
        for (BaseUIElement<?> child : safeChildren) child.mouseMoved(internalX, internalY);
        onMouseMoved(internalX, internalY);
    }

    /**
     * 鼠标滚轮事件处理入口。
     *
     * @param parentMouseX 鼠标相对于父组件的 X 坐标
     * @param parentMouseY 鼠标相对于父组件的 Y 坐标
     * @param delta        滚动增量
     * @return true 表示事件已消费
     */
    public final boolean mouseScrolled(double parentMouseX, double parentMouseY, double delta) {
        if (!visible) return false;
        boolean hit = (parentMouseX >= this.x && parentMouseX < this.x + this.width &&
                parentMouseY >= this.y && parentMouseY < this.y + this.height);
        if (clipToBounds && !hit) return false;

        double internalX = parentMouseX - this.x;
        double internalY = parentMouseY - this.y;
        List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (safeChildren.get(i).mouseScrolled(internalX, internalY, delta)) return true;
        }
        if (hit) return onScrolled(internalX, internalY, delta);
        return false;
    }

    /**
     * 键盘按键按下事件处理入口。
     * 仅当焦点在此组件或其子树中时才会传播。
     *
     * @param keyCode   按键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键（如 Shift、Ctrl）
     * @return true 表示事件已消费
     */
    public final boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (UIState.FOCUSED_ELEMENT != null && UIState.FOCUSED_ELEMENT.isInSubtreeOf(this)) {
            List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
            for (int i = safeChildren.size() - 1; i >= 0; i--) {
                if (safeChildren.get(i).keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        return onKeyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 字符输入事件处理入口。
     * 仅当焦点在此组件或其子树中时才会传播。
     *
     * @param codePoint 输入的字符
     * @param modifiers 修饰键
     * @return true 表示事件已消费
     */
    public final boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (UIState.FOCUSED_ELEMENT != null && UIState.FOCUSED_ELEMENT.isInSubtreeOf(this)) {
            List<BaseUIElement<?>> safeChildren = new ArrayList<>(children);
            for (int i = safeChildren.size() - 1; i >= 0; i--) {
                if (safeChildren.get(i).charTyped(codePoint, modifiers)) return true;
            }
        }
        return onCharTyped(codePoint, modifiers);
    }

    // 以下为事件回调的默认实现，子类可选择性重写

    /** 鼠标点击回调 */
    protected boolean onClicked(double mouseX, double mouseY, int button) { return false; }

    /** 鼠标释放回调 */
    protected boolean onReleased(double mouseX, double mouseY, int button) { return false; }

    /** 鼠标拖拽回调 */
    protected boolean onDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    /** 鼠标移动回调 */
    protected void onMouseMoved(double mouseX, double mouseY) {}

    /** 鼠标滚轮回调 */
    protected boolean onScrolled(double mouseX, double mouseY, double delta) { return false; }

    /** 按键按下回调 */
    protected boolean onKeyPressed(int k, int s, int m) { return false; }

    /** 字符输入回调 */
    protected boolean onCharTyped(char c, int m) { return false; }

    // ==========================================
    // 坐标与属性访问器
    // ==========================================

    /** 获取组件在屏幕上的绝对 X 坐标（通过向上累加父组件坐标） */
    public int getAbsoluteX() { return parent == null ? x : parent.getAbsoluteX() + x; }

    /** 获取组件在屏幕上的绝对 Y 坐标 */
    public int getAbsoluteY() { return parent == null ? y : parent.getAbsoluteY() + y; }

    /** 获取相对于父组件的 X 坐标 */
    public int getX() { return x; }

    /** 获取相对于父组件的 Y 坐标 */
    public int getY() { return y; }

    /** 获取 Z 轴深度 */
    public int getZ() { return z; }

    /** 获取组件宽度 */
    public int getWidth() { return width; }

    /** 获取组件高度 */
    public int getHeight() { return height; }

    /** 获取可见性 */
    public boolean isVisible() { return visible; }
}