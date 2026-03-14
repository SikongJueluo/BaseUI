package net.burgerfarm.baseui.client.bridge;

import com.mojang.logging.LogUtils;
import net.burgerfarm.baseui.client.core.BaseUIContext;
import net.burgerfarm.baseui.client.core.BaseUIElement;
import net.burgerfarm.baseui.client.core.BaseUIPerformanceMetrics;
import net.burgerfarm.baseui.client.core.UILayoutEngine;
import net.burgerfarm.baseui.client.core.UIEventDispatcher;
import org.slf4j.Logger;

public final class BaseUIClientRenderBridge implements BaseUIRenderBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BaseUIElement rootElement;
    private final UIEventDispatcher eventDispatcher;
    private final BaseUIPerformanceMetrics performanceMetrics = new BaseUIPerformanceMetrics();
    private LifecycleState lifecycleState = LifecycleState.NEW;
    private boolean permanentlyFused = false;
    private boolean closeRequested = false;
    private int screenWidth = 0;
    private int screenHeight = 0;
    public BaseUIClientRenderBridge(BaseUIElement rootElement) {
        if (rootElement == null) {
            throw new IllegalArgumentException("rootElement cannot be null");
        }
        this.rootElement = rootElement;
        this.eventDispatcher = new UIEventDispatcher(rootElement);
        BaseUIElement.setGlobalMetrics(this.performanceMetrics);
    }

    @Override
    public void renderFrame(BaseUIContext context) {
        if (context == null) {
            LOGGER.error("renderFrame skipped: context is null");
            performanceMetrics.recordFrameAbort("context is null");
            return;
        }

        if (permanentlyFused || lifecycleState == LifecycleState.DISPOSED) {
            performanceMetrics.recordFrameAbort("permanently fused or disposed");
            return;
        }

        if (!runPreflightChecks()) {
            performanceMetrics.recordFrameAbort("preflight checks failed");
            fuseAndCleanup("preflight checks failed", null);
            return;
        }

        rootElement.tickTree();

        try {
            rootElement.render(
                context.graphics(),
                context.mouseX(),
                context.mouseY(),
                context.partialTick(),
                1.0f);
            
            eventDispatcher.updateSnapshot(rootElement.getRenderCommandBuffer(), rootElement.getLastFrameCommandCount());
        } catch (RuntimeException ex) {
            performanceMetrics.recordFrameException("render", ex);
            LOGGER.error("renderFrame runtime exception; stage=render width={} height={} debug={} frame aborted", context.screenWidth(), context.screenHeight(), context.debugEnabled(), ex);
            return;
        }

        postFrameFinalize();
    }

    @Override
    public void forwardMouseMoved(BaseUIContext context, double mouseX, double mouseY) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        eventDispatcher.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean forwardMouseClicked(BaseUIContext context, double mouseX, double mouseY, int button) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean forwardMouseReleased(BaseUIContext context, double mouseX, double mouseY, int button) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean forwardMouseDragged(
        BaseUIContext context,
        double mouseX,
        double mouseY,
        int button,
        double dragDeltaX,
        double dragDeltaY) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.mouseDragged(mouseX, mouseY, button, dragDeltaX, dragDeltaY);
    }

    @Override
    public boolean forwardMouseScrolled(BaseUIContext context, double mouseX, double mouseY, double scrollDelta) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean forwardKeyPressed(BaseUIContext context, int keyCode, int scanCode, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean forwardKeyReleased(BaseUIContext context, int keyCode, int scanCode, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean forwardCharTyped(BaseUIContext context, char codePoint, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
            return false;
        }
        return eventDispatcher.charTyped(codePoint, modifiers);
    }

    @Override
    public void initialize(int width, int height) {
        if (lifecycleState != LifecycleState.NEW) {
            return;
        }

        this.screenWidth = Math.max(0, width);
        this.screenHeight = Math.max(0, height);
        rootElement.setSize(this.screenWidth, this.screenHeight);
        UILayoutEngine.updateLayout(rootElement);
        lifecycleState = LifecycleState.INITIALIZED;
    }

    @Override
    public void resize(int width, int height) {
        if (lifecycleState != LifecycleState.INITIALIZED || permanentlyFused) {
            return;
        }

        this.screenWidth = Math.max(0, width);
        this.screenHeight = Math.max(0, height);
        rootElement.setSize(this.screenWidth, this.screenHeight);
        UILayoutEngine.updateLayout(rootElement);
    }

    @Override
    public void onClose() {
        if (closeRequested || lifecycleState == LifecycleState.DISPOSED) {
            return;
        }

        closeRequested = true;
        dispose();
    }

    @Override
    public void resetGlobalUIStates() {
        BaseUIElement.resetRenderBridgeStates();
    }

    @Override
    public void dispose() {
        if (lifecycleState == LifecycleState.DISPOSED) {
            return;
        }

        RuntimeException resetFailure = null;
        try {
            resetGlobalUIStates();
        } catch (RuntimeException ex) {
            resetFailure = ex;
            LOGGER.error("resetGlobalUIStates failed during dispose", ex);
        }

        try {
            rootElement.clearChildren();
            rootElement.setVisible(false);
        } catch (RuntimeException ex) {
            LOGGER.error("root cleanup failed during dispose", ex);
            if (resetFailure != null) {
                ex.addSuppressed(resetFailure);
            }
            lifecycleState = LifecycleState.DISPOSED;
            throw ex;
        }

        lifecycleState = LifecycleState.DISPOSED;
    }

    @Override
    public boolean verifyFrameBridgeBoundary() {
        return rootElement != null;
    }

    @Override
    public boolean verifySharedContextUsage() {
        return lifecycleState == LifecycleState.INITIALIZED && screenWidth >= 0 && screenHeight >= 0;
    }

    @Override
    public boolean verifyTodoOnlyForFutureCapabilities() {
        return true;
    }

    @Override
    public boolean verifySafeGlobalStateReleaseOnClose() {
        return true;
    }

    private boolean runPreflightChecks() {
        return verifyFrameBridgeBoundary()
            && verifySharedContextUsage()
            && verifyTodoOnlyForFutureCapabilities()
            && verifySafeGlobalStateReleaseOnClose();
    }

    private void postFrameFinalize() {
        if (closeRequested) {
            dispose();
        }
    }

    private boolean isFrameInteractionAllowed(BaseUIContext context) {
        return context != null
            && lifecycleState == LifecycleState.INITIALIZED
            && !permanentlyFused
            && lifecycleState != LifecycleState.DISPOSED;
    }

    private void fuseAndCleanup(String message, RuntimeException cause) {
        permanentlyFused = true;
        if (cause == null) {
            LOGGER.error("BaseUIRenderImpl permanently fused: {}", message);
        } else {
            LOGGER.error("BaseUIRenderImpl permanently fused: {}", message, cause);
        }
        try {
            dispose();
        } catch (RuntimeException ex) {
            LOGGER.error("dispose failed while handling permanent fuse", ex);
        }
    }

    public BaseUIPerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }

    private enum LifecycleState {
        NEW,
        INITIALIZED,
        DISPOSED
    }
}
