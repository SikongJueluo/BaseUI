package net.burgerfarm.baseui.client.core;

/**
 * UI渲染状态管理器
 * <p>
 * 负责维护UI框架运行时的全局状态，包括：
 * <ul>
 *   <li>当前获得焦点的元素</li>
 *   <li>当前按压目标的元素</li>
 * </ul>
 * <p>
 * 此类为静态工具类，所有字段均为静态全局状态。
 * 在屏幕切换或GUI关闭时应调用 {@link BaseUIElement#resetAllStates()} 重置状态。
 */
final class BaseUIRenderState {
    /**
     * 当前按压目标的UI元素
     * <p>
     * 当用户按下鼠标时，记录被点击的元素。
     * 用于在鼠标释放时判断是否应该触发该元素的释放事件。
     */
    static BaseUIElement<?> PRESS_TARGET = null;

    /**
     * 当前获得键盘焦点的UI元素
     * <p>
     * 同一时间只有一个元素可以获得焦点。
     * 焦点元素会接收键盘输入事件。
     */
    static BaseUIElement<?> FOCUSED_ELEMENT = null;

    /**
     * 私有构造器，防止实例化
     */
    private BaseUIRenderState() {
    }
}
