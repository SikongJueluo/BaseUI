package net.burgerfarm.baseui.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseUIElementTest {
    @BeforeEach
    void resetStates() {
        BaseUIElement.resetAllStates();
    }

    /**
     * Verifies click/scroll hit order respects Z-order (topmost/highest Z element gets priority)
     * and that elements are visited in reverse order (children with higher Z are checked first).
     */
    @Test
    void testClickHitOrder() {
        RootElement root = new RootElement();
        ContainerElement branchA = new ContainerElement();
        ContainerElement branchB = new ContainerElement();
        ClickElement elementBack = new ClickElement("back");
        ClickElement elementTop = new ClickElement("top");

        // Setup layout
        root.setSize(120, 120);
        branchA.setPos(0, 0).setSize(120, 120).setZ(0);
        branchB.setPos(0, 0).setSize(120, 120).setZ(0);
        elementBack.setPos(10, 10).setSize(20, 20).setZ(0);
        elementTop.setPos(10, 10).setSize(20, 20).setZ(5);

        branchA.addChild(elementBack);
        branchB.addChild(elementTop);
        root.addChild(branchA);
        root.addChild(branchB);

        // Render frame to initialize command buffer (required for click routing)
        buildAndRenderContext(root, 12.0, 12.0);
        UIEventDispatcher dispatcher = new UIEventDispatcher(root);
        dispatcher.updateSnapshot(root.getRenderCommandBuffer(), root.getLastFrameCommandCount());

        // Click at position (12, 12) which hits both elements
        assertTrue(dispatcher.mouseClicked(12.0, 12.0, 0));

        // Verify topmost element (higher Z value) was clicked, not the back one
        assertEquals("top", root.lastClickedId);
        assertTrue(elementTop.isFocused());
        assertFalse(elementBack.isFocused());
    }

    /**
     * Verifies that scroll also respects Z-order using visual order from frame snapshot.
     */
    @Test
    void testScrollHitOrder() {
        RootElement root = new RootElement();
        ContainerElement branchA = new ContainerElement();
        ContainerElement branchB = new ContainerElement();
        ClickElement elementBack = new ClickElement("back");
        ClickElement elementTop = new ClickElement("top");

        root.setSize(120, 120);
        branchA.setPos(0, 0).setSize(120, 120).setZ(0);
        branchB.setPos(0, 0).setSize(120, 120).setZ(0);
        elementBack.setPos(10, 10).setSize(20, 20).setZ(0);
        elementTop.setPos(10, 10).setSize(20, 20).setZ(5);

        branchA.addChild(elementBack);
        branchB.addChild(elementTop);
        root.addChild(branchA);
        root.addChild(branchB);

        buildAndRenderContext(root, 12.0, 12.0);
        UIEventDispatcher dispatcher = new UIEventDispatcher(root);
        dispatcher.updateSnapshot(root.getRenderCommandBuffer(), root.getLastFrameCommandCount());

        // Scroll at position (12, 12)
        assertTrue(dispatcher.mouseScrolled(12.0, 12.0, 1.0));

        // Verify topmost element received scroll
        assertEquals("top", root.lastScrolledId);
    }

    /**
     * Verifies focus acquisition and press state ownership across events.
     * Confirms that:
     * - Clicking sets focus on focusable elements
     * - Press target owns subsequent drag and release events
     * - Press target is cleared after release
     */
    @Test
    void testFocusAndPressedState() {
        RootElement root = new RootElement();
        ClickElement button = new ClickElement("button");

        root.setSize(120, 120);
        button.setPos(10, 10).setSize(20, 20);
        root.addChild(button);

        buildAndRenderContext(root, 15.0, 15.0);
        UIEventDispatcher dispatcher = new UIEventDispatcher(root);
        dispatcher.updateSnapshot(root.getRenderCommandBuffer(), root.getLastFrameCommandCount());

        // Click on button
        assertTrue(dispatcher.mouseClicked(15.0, 15.0, 0));
        assertEquals("button", root.lastClickedId);
        assertTrue(button.isFocused());
        assertTrue(button.isPressed());
        assertNotNull(root.lastPressedId);

        // Drag away from button - press target still owns the drag
        assertTrue(dispatcher.mouseDragged(80.0, 80.0, 0, 65.0, 65.0));
        assertEquals("button", root.lastDraggedId);
        assertTrue(button.isPressed());

        // Release away from button - press target still owns the release
        assertTrue(dispatcher.mouseReleased(80.0, 80.0, 0));
        assertEquals("button", root.lastReleasedId);
        assertFalse(button.isPressed());
        assertNull(root.lastPressedId);
    }

    /**
     * Verifies nested clipping consistency.
     * Confirms that:
     * - Parent clip bounds are correctly computed
     * - Child clip bounds are correctly intersected with parent
     * - Scissors are applied with correct rectangle coordinates
     */
    @Test
    void testNestedClippingConsistency() {
        RootElement root = new RootElement();
        ContainerElement parent = new ContainerElement();
        ContainerElement child = new ContainerElement();

        root.setSize(200, 200);
        // Parent at (20, 30) with size (40, 40)
        parent.setPos(20, 30).setSize(40, 40).setClipToBounds(true);
        // Child at (10, 10) relative to parent, size (50, 50)
        // Absolute position: (30, 40)
        child.setPos(10, 10).setSize(50, 50).setClipToBounds(true);
        parent.addChild(child);
        root.addChild(parent);

        GuiGraphics graphics = Mockito.mock(GuiGraphics.class, Mockito.RETURNS_DEEP_STUBS);
        buildAndRenderWithGraphics(root, graphics, 1.0, 1.0);

        // Parent clip bounds: (20, 30) to (60, 70)
        // Child absolute: (30, 40) to (80, 90)
        // Intersection: (30, 40) to (60, 70)
        verify(graphics, atLeastOnce()).enableScissor(30, 40, 60, 70);
    }

    /**
     * Verifies that visible elements participate in events while invisible ones don't.
     */
    @Test
    void testVisibilityBlocksEvents() {
        RootElement root = new RootElement();
        ClickElement button = new ClickElement("button");

        root.setSize(120, 120);
        button.setPos(10, 10).setSize(20, 20);
        root.addChild(button);

        buildAndRenderContext(root, 15.0, 15.0);
        UIEventDispatcher dispatcher = new UIEventDispatcher(root);
        dispatcher.updateSnapshot(root.getRenderCommandBuffer(), root.getLastFrameCommandCount());

        // Make button invisible
        button.setVisible(false);

        // Click should not reach invisible button
        assertFalse(dispatcher.mouseClicked(15.0, 15.0, 0));
        assertNull(root.lastClickedId);
        assertFalse(button.isFocused());
    }

    /**
     * Verifies that focusable flag controls whether elements can gain focus.
     */
    @Test
    void testFocusableFlag() {
        RootElement root = new RootElement();
        ClickElement focusableButton = new ClickElement("focusable");
        ContainerElement nonFocusable = new ContainerElement();

        root.setSize(120, 120);
        focusableButton.setPos(10, 10).setSize(20, 20);
        nonFocusable.setPos(40, 40).setSize(20, 20);
        root.addChild(focusableButton);
        root.addChild(nonFocusable);

        // focusableButton is created with focusable=true
        // nonFocusable has focusable=false by default

        buildAndRenderContext(root, 15.0, 15.0);
        UIEventDispatcher dispatcher = new UIEventDispatcher(root);
        dispatcher.updateSnapshot(root.getRenderCommandBuffer(), root.getLastFrameCommandCount());

        // Click on focusable button
        assertTrue(dispatcher.mouseClicked(15.0, 15.0, 0));
        assertTrue(focusableButton.isFocused());

        // Request focus on non-focusable element (should fail)
        nonFocusable.requestFocus();
        assertFalse(nonFocusable.isFocused());
        // Focus should remain on focusable button
        assertTrue(focusableButton.isFocused());
    }

    // ========================
    // Helper Classes
    // ========================

    private static BaseUIContext buildAndRenderContext(BaseUIElement root, double mouseX, double mouseY) {
        GuiGraphics graphics = Mockito.mock(GuiGraphics.class, Mockito.RETURNS_DEEP_STUBS);
        return buildAndRenderWithGraphics(root, graphics, mouseX, mouseY);
    }

    private static BaseUIContext buildAndRenderWithGraphics(
        BaseUIElement root,
        GuiGraphics graphics,
        double mouseX,
        double mouseY) {
        BaseUIContext context = new BaseUIContext(graphics, mouseX, mouseY, 0.5f, 200, 200, false);
        root.render(graphics, mouseX, mouseY, 0.5f, 1.0f);
        return context;
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

    private static class RootElement extends BaseUIElement {
        String lastClickedId;
        String lastPressedId;
        String lastDraggedId;
        String lastReleasedId;
        String lastScrolledId;

        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            // Root element just renders
        }
    }

    private static class ContainerElement extends BaseUIElement {
        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
            // Container element just renders its children
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
            // Element renders itself
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
