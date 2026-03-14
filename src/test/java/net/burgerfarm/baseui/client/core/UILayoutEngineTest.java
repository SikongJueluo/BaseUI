package net.burgerfarm.baseui.client.core;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphics;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UILayoutEngineTest {

    static class TestElement extends BaseUIElement {
        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {}
    }

    @Test
    void testAnchorNinePatch() {
        TestElement parent = new TestElement();
        parent.setSize(100, 100);
        
        TestElement child = new TestElement();
        child.setSize(20, 20);
        parent.addChild(child);

        child.setAnchor(BaseUIElement.UIAnchor.TOP_LEFT);
        UILayoutEngine.updateLayout(parent);
        assertEquals(0, child.getX());
        assertEquals(0, child.getY());

        child.setAnchor(BaseUIElement.UIAnchor.CENTER);
        UILayoutEngine.updateLayout(parent);
        assertEquals(40, child.getX());
        assertEquals(40, child.getY());

        child.setAnchor(BaseUIElement.UIAnchor.BOTTOM_RIGHT);
        UILayoutEngine.updateLayout(parent);
        assertEquals(80, child.getX());
        assertEquals(80, child.getY());
    }

    @Test
    void testSizeChangePropagation() {
        TestElement parent = new TestElement();
        parent.setSize(100, 100);
        TestElement child = new TestElement();
        child.setSize(20, 20);
        child.setAnchor(BaseUIElement.UIAnchor.CENTER);
        parent.addChild(child);
        
        // Use the old way to update Layout to see if the test passes
        // wait, child is package-private in the old code?
        UILayoutEngine.updateLayout(parent);
        assertEquals(40, child.getX());
        
        parent.setSize(200, 200);
        UILayoutEngine.updateLayout(parent);
        assertEquals(90, child.getX());
    }
}
