package net.burgerfarm.baseui.client.core;

import net.burgerfarm.baseui.client.core.BaseUIElement.UIAnchor;

public class UILayoutEngine {

    /**
     * 更新组件及其子树的布局。
     */
    public static void updateLayout(BaseUIElement root) {
        updateLayoutInternal(root, false);
    }

    private static void updateLayoutInternal(BaseUIElement root, boolean force) {
        if (root.isUpdatingLayout()) return;
        boolean forcePerFrameLayout = root.requiresPerFrameLayoutPass();
        if (!force && !forcePerFrameLayout && !root.isLayoutDirty() && !root.isSubtreeLayoutDirty()) return;
        
        root.setUpdatingLayout(true);

        try {
            root.beforeLayoutPass();

            boolean recalcSelf = root.isLayoutDirty() || force || forcePerFrameLayout;
            if (recalcSelf) {
                int pw = 0;
                int ph = 0;
                if (root.getParent() != null) {
                    pw = root.getParent().getWidth();
                    ph = root.getParent().getHeight();
                }
                
                int targetX = root.getAnchorOffsetX();
                int targetY = root.getAnchorOffsetY();
                UIAnchor anchor = root.getAnchor();
                
                if (anchor != null && root.getParent() != null) {
                    switch (anchor) {
                        case TOP_CENTER:    targetX += pw / 2 - root.getWidth() / 2; break;
                        case TOP_RIGHT:     targetX += pw - root.getWidth(); break;
                        case MIDDLE_LEFT:   targetY += ph / 2 - root.getHeight() / 2; break;
                        case CENTER:        targetX += pw / 2 - root.getWidth() / 2; targetY += ph / 2 - root.getHeight() / 2; break;
                        case MIDDLE_RIGHT:  targetX += pw - root.getWidth(); targetY += ph / 2 - root.getHeight() / 2; break;
                        case BOTTOM_LEFT:   targetY += ph - root.getHeight(); break;
                        case BOTTOM_CENTER: targetX += pw / 2 - root.getWidth() / 2; targetY += ph - root.getHeight(); break;
                        case BOTTOM_RIGHT:  targetX += pw - root.getWidth(); targetY += ph - root.getHeight(); break;
                        case TOP_LEFT:
                        default:
                            break;
                    }
                }
                
                root.setComputedX(targetX);
                root.setComputedY(targetY);
            }

            boolean propagateAllChildren = recalcSelf;
            for (BaseUIElement child : root.getChildren()) {
                if (propagateAllChildren || child.isLayoutDirty() || child.isSubtreeLayoutDirty() || child.requiresPerFrameLayoutPass()) {
                    updateLayoutInternal(child, propagateAllChildren);
                }
            }

            root.afterLayoutPass();

            root.clearLayoutDirty();
            
        } finally {
            root.setUpdatingLayout(false);
        }
    }
    
    /**
     * 标记节点及其祖先为需要重新布局
     */
    public static void markLayoutDirty(BaseUIElement element) {
        element.setLayoutDirty(true);
        BaseUIElement current = element.getParent();
        while (current != null) {
            current.setSubtreeLayoutDirty(true);
            current = current.getParent();
        }
    }
}
