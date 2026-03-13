package net.burgerfarm.baseui.Client.Render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.burgerfarm.baseui.Core.BaseUIContext;
import net.burgerfarm.baseui.Client.RenderBridge.BaseUIClientRenderBridge;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseUIClientRenderBridgeTest {
    @Test
    void initializeOnlyWorksFromNewAndIgnoresRepeatedCalls() {
        TestElement root = new TestElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);

        render.initialize(320, 240);
        assertEquals(320, root.getWidth());
        assertEquals(240, root.getHeight());

        render.initialize(640, 480);
        assertEquals(320, root.getWidth());
        assertEquals(240, root.getHeight());
    }

    @Test
    void resizeOnlyWorksWhenInitialized() {
        TestElement root = new TestElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);

        render.resize(500, 500);
        assertEquals(0, root.getWidth());
        assertEquals(0, root.getHeight());

        render.initialize(300, 200);
        render.resize(640, 360);
        assertEquals(640, root.getWidth());
        assertEquals(360, root.getHeight());
    }

    @Test
    void onCloseImmediatelyDisposesAndIsIdempotent() {
        TestElement root = new TestElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(320, 240);

        render.onClose();
        assertFalse(root.isVisible());

        render.onClose();
        assertFalse(root.isVisible());
    }

    @Test
    void preflightFailurePermanentlyFusesAndBlocksFutureFrames() {
        TestElement root = new TestElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);

        BaseUIContext context = buildContext();
        render.renderFrame(context);
        render.renderFrame(context);

        assertEquals(0, root.renderCount);
        assertFalse(root.isVisible());
    }

    @Test
    void singleFrameExceptionRethrowsAndDoesNotBlockNextFrame() {
        ThrowOnceElement root = new ThrowOnceElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(320, 240);

        BaseUIContext context = buildContext();
        assertThrows(IllegalStateException.class, () -> render.renderFrame(context));
        render.renderFrame(context);

        assertEquals(2, root.renderAttempts);
        assertEquals(1, root.successfulRenders);
        assertTrue(root.isVisible());
    }

    private static BaseUIContext buildContext() {
        GuiGraphics graphics = Mockito.mock(GuiGraphics.class, Mockito.RETURNS_DEEP_STUBS);
        return new BaseUIContext(graphics, 12.0, 8.0, 0.5f, 320, 240, false);
    }

    private static class TestElement extends BaseUIElement<TestElement> {
        int renderCount = 0;

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            renderCount++;
        }
    }

    private static final class ThrowOnceElement extends TestElement {
        int renderAttempts = 0;
        int successfulRenders = 0;

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            renderAttempts++;
            if (renderAttempts == 1) {
                throw new IllegalStateException("boom");
            }
            successfulRenders++;
        }
    }
}
