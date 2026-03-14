package net.burgerfarm.baseui.client.core;

import java.util.List;

public class UIEventDispatcher {

    private final BaseUIElement root;
    private BaseUIRenderCommandBuffer snapshot;
    private int snapshotCommandCount = 0;

    public UIEventDispatcher(BaseUIElement root) {
        this.root = root;
    }

    public void updateSnapshot(BaseUIRenderCommandBuffer buffer, int commandCount) {
        this.snapshot = buffer;
        this.snapshotCommandCount = commandCount;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (snapshot != null && snapshotCommandCount > 0) {
            if (mouseClickedFromSnapshot(mouseX, mouseY, button)) {
                return true;
            }
        }
        return routeMouseClicked(root, mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (BaseUIRenderState.PRESS_TARGET != null) {
            BaseUIElement target = BaseUIRenderState.PRESS_TARGET;
            BaseUIRenderState.PRESS_TARGET = null;
            return target.onReleased(mouseX - target.getAbsoluteX(), mouseY - target.getAbsoluteY(), button);
        }
        return routeMouseReleased(root, mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (BaseUIRenderState.PRESS_TARGET != null) {
            BaseUIElement target = BaseUIRenderState.PRESS_TARGET;
            return target.onDragged(mouseX - target.getAbsoluteX(), mouseY - target.getAbsoluteY(), button, dragX, dragY);
        }
        return routeMouseDragged(root, mouseX, mouseY, button, dragX, dragY);
    }

    public void mouseMoved(double mouseX, double mouseY) {
        routeMouseMoved(root, mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (snapshot != null && snapshotCommandCount > 0) {
            if (mouseScrolledFromSnapshot(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return routeMouseScrolled(root, mouseX, mouseY, delta);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return routeKeyPressed(root, keyCode, scanCode, modifiers);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return routeKeyReleased(root, keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return routeCharTyped(root, codePoint, modifiers);
    }

    private boolean routeMouseClicked(BaseUIElement element, double parentMouseX, double parentMouseY, int button) {
        if (!element.isVisible() || element.isCulledByScissor()) return false;
        
        boolean hit = (parentMouseX >= element.getX() && parentMouseX < element.getX() + element.getWidth() &&
            parentMouseY >= element.getY() && parentMouseY < element.getY() + element.getHeight());

        if (element.clipToBounds && !hit) return false;

        double internalX = parentMouseX - element.getX();
        double internalY = parentMouseY - element.getY();

        List<BaseUIElement> safeChildren = element.getChildren();
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (routeMouseClicked(safeChildren.get(i), internalX, internalY, button)) return true;
        }

        if (hit) {
            if (element.focusable) element.requestFocus();
            BaseUIRenderState.PRESS_TARGET = element;
            return element.onClicked(internalX, internalY, button);
        }
        return false;
    }

    private boolean routeMouseReleased(BaseUIElement element, double parentMouseX, double parentMouseY, int button) {
        if (!element.isVisible()) return false;

        double internalX = parentMouseX - element.getX();
        double internalY = parentMouseY - element.getY();
        List<BaseUIElement> safeChildren = element.getChildren();
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (routeMouseReleased(safeChildren.get(i), internalX, internalY, button)) return true;
        }
        return element.onReleased(internalX, internalY, button);
    }

    private boolean routeMouseDragged(BaseUIElement element, double parentMouseX, double parentMouseY, int button, double dragX, double dragY) {
        if (!element.isVisible()) return false;

        double internalX = parentMouseX - element.getX();
        double internalY = parentMouseY - element.getY();
        List<BaseUIElement> safeChildren = element.getChildren();
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (routeMouseDragged(safeChildren.get(i), internalX, internalY, button, dragX, dragY)) return true;
        }
        return element.onDragged(internalX, internalY, button, dragX, dragY);
    }

    private void routeMouseMoved(BaseUIElement element, double parentMouseX, double parentMouseY) {
        if (!element.isVisible() || element.isCulledByScissor()) return;
        
        boolean hit = (parentMouseX >= element.getX() && parentMouseX < element.getX() + element.getWidth() &&
            parentMouseY >= element.getY() && parentMouseY < element.getY() + element.getHeight());
            
        if (element.clipToBounds && !hit) return;

        element.isHoveredForRender = hit;

        double internalX = parentMouseX - element.getX();
        double internalY = parentMouseY - element.getY();
        List<BaseUIElement> safeChildren = element.getChildren();
        for (int i = 0; i < safeChildren.size(); i++) {
            routeMouseMoved(safeChildren.get(i), internalX, internalY);
        }
        element.onMouseMoved(internalX, internalY);
    }

    private boolean routeMouseScrolled(BaseUIElement element, double parentMouseX, double parentMouseY, double delta) {
        if (!element.isVisible() || element.isCulledByScissor()) return false;
        
        boolean hit = (parentMouseX >= element.getX() && parentMouseX < element.getX() + element.getWidth() &&
            parentMouseY >= element.getY() && parentMouseY < element.getY() + element.getHeight());
            
        if (element.clipToBounds && !hit) return false;

        double internalX = parentMouseX - element.getX();
        double internalY = parentMouseY - element.getY();
        List<BaseUIElement> safeChildren = element.getChildren();
        for (int i = safeChildren.size() - 1; i >= 0; i--) {
            if (routeMouseScrolled(safeChildren.get(i), internalX, internalY, delta)) return true;
        }
        if (hit) return element.onScrolled(internalX, internalY, delta);
        return false;
    }

    private boolean routeKeyPressed(BaseUIElement element, int keyCode, int scanCode, int modifiers) {
        if (!element.isVisible()) return false;
        
        if (BaseUIRenderState.FOCUSED_ELEMENT != null && BaseUIRenderState.FOCUSED_ELEMENT.isInSubtreeOf(element)) {
            List<BaseUIElement> safeChildren = element.getChildren();
            for (int i = safeChildren.size() - 1; i >= 0; i--) {
                if (routeKeyPressed(safeChildren.get(i), keyCode, scanCode, modifiers)) return true;
            }
        }

        if (element.isFocused()) {
            return element.onKeyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    private boolean routeKeyReleased(BaseUIElement element, int keyCode, int scanCode, int modifiers) {
        if (!element.isVisible()) return false;
        
        if (BaseUIRenderState.FOCUSED_ELEMENT != null && BaseUIRenderState.FOCUSED_ELEMENT.isInSubtreeOf(element)) {
            List<BaseUIElement> safeChildren = element.getChildren();
            for (int i = safeChildren.size() - 1; i >= 0; i--) {
                if (routeKeyReleased(safeChildren.get(i), keyCode, scanCode, modifiers)) return true;
            }
        }
        return element.onKeyReleased(keyCode, scanCode, modifiers);
    }

    private boolean routeCharTyped(BaseUIElement element, char codePoint, int modifiers) {
        if (!element.isVisible()) return false;
        
        if (BaseUIRenderState.FOCUSED_ELEMENT != null && BaseUIRenderState.FOCUSED_ELEMENT.isInSubtreeOf(element)) {
            List<BaseUIElement> safeChildren = element.getChildren();
            for (int i = safeChildren.size() - 1; i >= 0; i--) {
                if (routeCharTyped(safeChildren.get(i), codePoint, modifiers)) return true;
            }
        }
        
        if (element.isFocused()) {
            return element.onCharTyped(codePoint, modifiers);
        }
        return false;
    }

    private boolean mouseClickedFromSnapshot(double mouseX, double mouseY, int button) {
        for (int ordered = snapshotCommandCount - 1; ordered >= 0; ordered--) {
            int commandIndex = snapshot.orderedIndexAt(ordered);
            BaseUIElement element = snapshot.elementAt(commandIndex);
            if (!element.isVisible() || element.isCulledByScissor()) {
                continue;
            }
            if (!isHitInCommandBounds(commandIndex, mouseX, mouseY)) {
                continue;
            }

            double localX = mouseX - snapshot.absXAt(commandIndex);
            double localY = mouseY - snapshot.absYAt(commandIndex);
            if (element.focusable) {
                element.requestFocus();
            }
            BaseUIRenderState.PRESS_TARGET = element;
            return element.onClicked(localX, localY, button);
        }
        return false;
    }

    private boolean mouseScrolledFromSnapshot(double mouseX, double mouseY, double delta) {
        for (int ordered = snapshotCommandCount - 1; ordered >= 0; ordered--) {
            int commandIndex = snapshot.orderedIndexAt(ordered);
            BaseUIElement element = snapshot.elementAt(commandIndex);
            if (!element.isVisible() || element.isCulledByScissor()) {
                continue;
            }
            if (!isHitInCommandBounds(commandIndex, mouseX, mouseY)) {
                continue;
            }
            double localX = mouseX - snapshot.absXAt(commandIndex);
            double localY = mouseY - snapshot.absYAt(commandIndex);
            if (element.onScrolled(localX, localY, delta)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHitInCommandBounds(int commandIndex, double mouseX, double mouseY) {
        BaseUIElement element = snapshot.elementAt(commandIndex);
        int left = snapshot.absXAt(commandIndex);
        int top = snapshot.absYAt(commandIndex);
        int right = left + element.getWidth();
        int bottom = top + element.getHeight();
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
            return false;
        }

        if (!snapshot.hasClipAt(commandIndex)) {
            return true;
        }
        return mouseX >= snapshot.clipLAt(commandIndex)
            && mouseY >= snapshot.clipTAt(commandIndex)
            && mouseX < snapshot.clipRAt(commandIndex)
            && mouseY < snapshot.clipBAt(commandIndex);
    }
}