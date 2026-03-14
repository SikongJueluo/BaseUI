package net.burgerfarm.baseui.client.RenderBridge;

import net.burgerfarm.baseui.core.BaseUIContext;

/**
 * BaseUIRender 只负责 Screen/Forge 帧级协调。
 * MUST NOT 接管 BaseUIElement 树职责：布局、事件冒泡、焦点树、press target、递归渲染。
 */
public interface BaseUIRenderBridge
        extends BaseUIRenderFrameEntry,
                BaseUIRenderInputForwarding,
                BaseUIRenderLifecycle,
                BaseUIRenderStateFinalizer,
                BaseUIRenderPreflightChecklist {

    // TODO(theme-skin): 在后续阶段引入主题/皮肤入口契约（仅占位，不在当前里程碑实现）。
    // TODO(primitives-provider): 在后续阶段定义统一渲染原语 provider 聚合边界（仅占位）。
    // TODO(debug-hook): 在后续阶段增加调试可视化挂载点（边界框、scissor、focus、press target）。
    // TODO(plugin-bridge): 在后续阶段定义插件桥接与扩展生命周期契约（仅占位）。
    // TODO(reconcile): 在后续阶段定义 DSL -> retained tree 的增量 reconcile 协作边界（仅占位）。
    // TODO(cache-batching): 在后续阶段定义缓存/批处理与脏区刷新能力入口（仅占位）。
}

interface BaseUIRenderFrameEntry {
    void renderFrame(BaseUIContext context);
}

interface BaseUIRenderInputForwarding {
    void forwardMouseMoved(BaseUIContext context, double mouseX, double mouseY);

    boolean forwardMouseClicked(BaseUIContext context, double mouseX, double mouseY, int button);

    boolean forwardMouseReleased(BaseUIContext context, double mouseX, double mouseY, int button);

    boolean forwardMouseDragged(
            BaseUIContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragDeltaX,
            double dragDeltaY);

    boolean forwardMouseScrolled(BaseUIContext context, double mouseX, double mouseY, double scrollDelta);

    boolean forwardKeyPressed(BaseUIContext context, int keyCode, int scanCode, int modifiers);

    boolean forwardKeyReleased(BaseUIContext context, int keyCode, int scanCode, int modifiers);

    boolean forwardCharTyped(BaseUIContext context, char codePoint, int modifiers);
}

interface BaseUIRenderLifecycle {
    void initialize(int screenWidth, int screenHeight);

    void resize(int screenWidth, int screenHeight);

    void onClose();
}

interface BaseUIRenderStateFinalizer {
    void resetGlobalUIStates();

    void dispose();
}

interface BaseUIRenderPreflightChecklist {
    boolean verifyFrameBridgeBoundary();

    boolean verifySharedContextUsage();

    boolean verifyTodoOnlyForFutureCapabilities();

    boolean verifySafeGlobalStateReleaseOnClose();
}
