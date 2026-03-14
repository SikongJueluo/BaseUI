package net.burgerfarm.baseui.client.Render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import net.burgerfarm.baseui.client.core.BaseUIContext;
import net.burgerfarm.baseui.client.bridge.BaseUIClientRenderBridge;
import net.burgerfarm.baseui.client.core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseUIClientRenderBridgeTest {
    @BeforeEach
    void resetStates() {
        BaseUIElement.resetAllStates();
    }

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

        render.renderFrame(buildContext());
        assertEquals(1, root.tickCount);
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
    void singleFrameExceptionIsIsolatedAndDoesNotBlockNextFrame() {
        ThrowOnceElement root = new ThrowOnceElement();
        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(320, 240);

        BaseUIContext context = buildContext();
        render.renderFrame(context);
        render.renderFrame(context);

        assertEquals(2, root.renderAttempts);
        assertEquals(1, root.successfulRenders);
        assertTrue(root.isVisible());
    }

    @Test
    void clickUsesVisualOrderFromGlobalZAcrossBranches() {
        RootElement root = new RootElement();
        ContainerElement branchA = new ContainerElement();
        ContainerElement branchB = new ContainerElement();
        ClickElement back = new ClickElement("back");
        ClickElement top = new ClickElement("top");

        root.setSize(120, 120);
        branchA.setPos(0, 0).setSize(120, 120).setZ(0);
        branchB.setPos(0, 0).setSize(120, 120).setZ(0);
        back.setPos(10, 10).setSize(20, 20).setZ(0);
        top.setPos(10, 10).setSize(20, 20).setZ(5);

        branchA.addChild(back);
        branchB.addChild(top);
        root.addChild(branchA);
        root.addChild(branchB);

        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(120, 120);
        render.renderFrame(buildContext(12.0, 12.0));

        assertTrue(render.forwardMouseClicked(buildContext(12.0, 12.0), 12.0, 12.0, 0));
        assertEquals("top", root.lastClickedId);
        assertTrue(top.isFocused());
    }

    @Test
    void stableOrderKeepsDfsOrderInsideSameLayer() {
        RootElement root = new RootElement();
        DrawTraceElement first = new DrawTraceElement("first", root.drawTrace);
        DrawTraceElement second = new DrawTraceElement("second", root.drawTrace);

        root.setSize(120, 120);
        first.setPos(0, 0).setSize(10, 10).setZ(0);
        second.setPos(0, 0).setSize(10, 10).setZ(0);
        root.addChild(first);
        root.addChild(second);

        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(120, 120);
        render.renderFrame(buildContext(1.0, 1.0));

        assertEquals(List.of("root", "first", "second"), root.drawTrace);
    }

    @Test
    void nestedClipUsesIntersectionRectDuringExecution() {
        RootElement root = new RootElement();
        ContainerElement parent = new ContainerElement();
        ContainerElement child = new ContainerElement();

        root.setSize(200, 200);
        parent.setPos(20, 30).setSize(40, 40).setClipToBounds(true);
        child.setPos(10, 10).setSize(50, 50).setClipToBounds(true);
        parent.addChild(child);
        root.addChild(parent);

        GuiGraphics graphics = Mockito.mock(GuiGraphics.class, Mockito.RETURNS_DEEP_STUBS);
        BaseUIContext context = new BaseUIContext(graphics, 0, 0, 0.5f, 200, 200, false);

        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(200, 200);
        render.renderFrame(context);

        verify(graphics, atLeastOnce()).enableScissor(30, 40, 60, 70);
    }

    @Test
    void pressTargetKeepsDragAndReleaseOwnership() {
        RootElement root = new RootElement();
        ClickElement button = new ClickElement("button");
        root.setSize(120, 120);
        button.setPos(10, 10).setSize(20, 20);
        root.addChild(button);

        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(120, 120);
        render.renderFrame(buildContext(15.0, 15.0));

        assertTrue(render.forwardMouseClicked(buildContext(15.0, 15.0), 15.0, 15.0, 0));
        assertNotNull(root.lastPressedId);

        assertTrue(render.forwardMouseDragged(buildContext(80.0, 80.0), 80.0, 80.0, 0, 1.0, 1.0));
        assertEquals("button", root.lastDraggedId);

        assertTrue(render.forwardMouseReleased(buildContext(80.0, 80.0), 80.0, 80.0, 0));
        assertEquals("button", root.lastReleasedId);
        assertNull(root.lastPressedId);
    }

    @Test
    void scrollAlsoUsesVisualOrderFromFrameSnapshot() {
        RootElement root = new RootElement();
        ContainerElement branchA = new ContainerElement();
        ContainerElement branchB = new ContainerElement();
        ClickElement back = new ClickElement("back");
        ClickElement top = new ClickElement("top");

        root.setSize(120, 120);
        branchA.setPos(0, 0).setSize(120, 120).setZ(0);
        branchB.setPos(0, 0).setSize(120, 120).setZ(0);
        back.setPos(10, 10).setSize(20, 20).setZ(0);
        top.setPos(10, 10).setSize(20, 20).setZ(5);

        branchA.addChild(back);
        branchB.addChild(top);
        root.addChild(branchA);
        root.addChild(branchB);

        BaseUIClientRenderBridge render = new BaseUIClientRenderBridge(root);
        render.initialize(120, 120);
        render.renderFrame(buildContext(12.0, 12.0));

        assertTrue(render.forwardMouseScrolled(buildContext(12.0, 12.0), 12.0, 12.0, 1.0));
        assertEquals("top", root.lastScrolledId);
    }

    private static BaseUIContext buildContext() {
        return buildContext(12.0, 8.0);
    }

    private static BaseUIContext buildContext(double mouseX, double mouseY) {
        GuiGraphics graphics = Mockito.mock(GuiGraphics.class, Mockito.RETURNS_DEEP_STUBS);
        return new BaseUIContext(graphics, mouseX, mouseY, 0.5f, 320, 240, false);
    }

    private static class TestElement extends BaseUIElement {
        int renderCount = 0;
        int tickCount = 0;

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            renderCount++;
        }

        @Override
        protected void onTick() {
            tickCount++;
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

    private static final class RootElement extends BaseUIElement {
        final List<String> drawTrace = new ArrayList<>();
        String lastClickedId;
        String lastPressedId;
        String lastDraggedId;
        String lastReleasedId;
        String lastScrolledId;

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            drawTrace.add("root");
        }
    }

    private static class ContainerElement extends BaseUIElement {
        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        }
    }

    private static final class DrawTraceElement extends BaseUIElement {
        private final String id;
        private final List<String> trace;

        private DrawTraceElement(String id, List<String> trace) {
            this.id = id;
            this.trace = trace;
        }

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            trace.add(id);
        }
    }

    private static final class ClickElement extends BaseUIElement {
        private final String id;

        private ClickElement(String id) {
            this.id = id;
            this.setFocusable(true);
        }

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        }

        @Override
        protected boolean onClicked(double mouseX, double mouseY, int button) {
            RootElement root = findRoot();
            root.lastClickedId = id;
            root.lastPressedId = id;
            return true;
        }

        @Override
        protected boolean onDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            RootElement root = findRoot();
            root.lastDraggedId = id;
            return true;
        }

        @Override
        protected boolean onReleased(double mouseX, double mouseY, int button) {
            RootElement root = findRoot();
            root.lastReleasedId = id;
            root.lastPressedId = null;
            return true;
        }

        @Override
        protected boolean onScrolled(double mouseX, double mouseY, double delta) {
            RootElement root = findRoot();
            root.lastScrolledId = id;
            return true;
        }

        private RootElement findRoot() {
            BaseUIElement node = this;
            while (node.getParent() != null) {
                node = node.getParent();
            }
            return (RootElement) node;
        }
    }
}
