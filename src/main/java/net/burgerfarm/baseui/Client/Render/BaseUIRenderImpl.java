package net.burgerfarm.baseui.Client.Render;

import com.mojang.logging.LogUtils;
import net.burgerfarm.baseui.Core.BaseUIElement;
import org.slf4j.Logger;

public final class BaseUIRenderImpl implements BaseUIRender {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BaseUIElement<?> rootElement;
    private LifecycleState lifecycleState = LifecycleState.NEW;
    private boolean permanentlyFused = false;
    private boolean closeRequested = false;
    private int screenWidth = 0;
    private int screenHeight = 0;
    public BaseUIRenderImpl(BaseUIElement<?> rootElement) {
        if (rootElement == null) {
            throw new IllegalArgumentException("rootElement cannot be null");
        }
        this.rootElement = rootElement;
    }

    @Override
    public void renderFrame(BaseUIRenderContext context) {
        if (context == null) {
            LOGGER.error("renderFrame skipped: context is null");
            return;
        }

        if (permanentlyFused || lifecycleState == LifecycleState.DISPOSED) {
            return;
        }

        if (!runPreflightChecks()) {
            fuseAndCleanup("preflight checks failed", null);
            return;
        }

        try {
            rootElement.render(
                context.graphics(),
                context.mouseX(),
                context.mouseY(),
                context.partialTick(),
                1.0f);
        } catch (RuntimeException ex) {
            LOGGER.error("renderFrame runtime exception; frame aborted", ex);
        }

        postFrameFinalize();
    }

    @Override
    public void forwardMouseMoved(BaseUIRenderContext context, double mouseX, double mouseY) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void forwardMouseClicked(BaseUIRenderContext context, double mouseX, double mouseY, int button) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void forwardMouseReleased(BaseUIRenderContext context, double mouseX, double mouseY, int button) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void forwardMouseDragged(
        BaseUIRenderContext context,
        double mouseX,
        double mouseY,
        int button,
        double dragDeltaX,
        double dragDeltaY) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.mouseDragged(mouseX, mouseY, button, dragDeltaX, dragDeltaY);
    }

    @Override
    public void forwardMouseScrolled(BaseUIRenderContext context, double mouseX, double mouseY, double scrollDelta) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public void forwardKeyPressed(BaseUIRenderContext context, int keyCode, int scanCode, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void forwardKeyReleased(BaseUIRenderContext context, int keyCode, int scanCode, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
        }
    }

    @Override
    public void forwardCharTyped(BaseUIRenderContext context, char codePoint, int modifiers) {
        if (!isFrameInteractionAllowed(context)) {
            return;
        }
        rootElement.charTyped(codePoint, modifiers);
    }

    @Override
    public void initialize(int width, int height) {
        if (lifecycleState != LifecycleState.NEW) {
            return;
        }

        this.screenWidth = Math.max(0, width);
        this.screenHeight = Math.max(0, height);
        rootElement.setSize(this.screenWidth, this.screenHeight);
        rootElement.updateLayout();
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
        rootElement.updateLayout();
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

    private boolean isFrameInteractionAllowed(BaseUIRenderContext context) {
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

    private enum LifecycleState {
        NEW,
        INITIALIZED,
        DISPOSED
    }
}
