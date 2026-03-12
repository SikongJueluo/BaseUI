package net.burgerfarm.baseui.Components;

import com.mojang.blaze3d.platform.InputConstants;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.burgerfarm.baseui.Render.BaseUINineSliceTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * BaseUI 基础交互组件：按钮
 * 集成：防穿透事件吞噬、文字自动排版(AutoSize)、安全的无状态音效触发。
 */
public class BaseUIButton extends BaseUIElement<BaseUIButton> {

    private static final int FALLBACK_BASE_ALPHA = 0x88;

    private Component text = Component.empty();
    private Runnable onClickAction = null;
    private boolean disabled = false;

    // 音效配置
    private boolean playClickSound = true;
    private boolean playHoverSound = false;
    private boolean wasHoveredLastFrame = false;

    // 自动尺寸配置
    private boolean autoSize = false;
    private int autoPadX = 0;
    private int autoPadY = 0;

    private BaseUINineSliceTexture texNormal = null;
    private BaseUINineSliceTexture texHover = null;
    private BaseUINineSliceTexture texPressed = null;
    private BaseUINineSliceTexture texDisabled = null;

    private int colorNormal = 0xFFFFFF;
    private int colorHover = 0xFFFF55;
    private int colorPressed = 0xAAAAAA;
    private int colorDisabled = 0x555555;

    public BaseUIButton() {
        this.width = 100;
        this.height = 20;
        this.focusable = true;
    }

    // ==========================================
    // 链式 API 与 自动排版
    // ==========================================

    public BaseUIButton setAutoSize(boolean autoSize, int paddingX, int paddingY) {
        this.autoSize = autoSize;
        this.autoPadX = paddingX;
        this.autoPadY = paddingY;
        if (autoSize) this.refreshSize();
        return this;
    }

    public BaseUIButton setText(Component text) {
        this.text = text;
        if (autoSize) this.refreshSize();
        return this;
    }

    public BaseUIButton setText(String text) {
        return setText(Component.literal(text));
    }

    /**
     * 内部刷新尺寸，严格保证盒模型，并防止尺寸坍缩为 0
     */
    private void refreshSize() {
        int calcWidth = autoPadX * 2;
        int calcHeight = autoPadY * 2;

        // 叠加文字尺寸 (经典标准盒模型)
        if (this.text != null && !this.text.getString().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            if (font != null) {
                calcWidth += font.width(this.text);
                calcHeight += font.lineHeight;
            }
        }

        // 寻找木桶效应的“最长板”以防止渲染越界
        int minTexW = 0, minTexH = 0;
        BaseUINineSliceTexture[] textures = {texNormal, texHover, texPressed, texDisabled};
        for (BaseUINineSliceTexture tex : textures) {
            if (tex != null) {
                minTexW = Math.max(minTexW, tex.getMinimumWidth());
                minTexH = Math.max(minTexH, tex.getMinimumHeight());
            }
        }

        // 防尺寸坍缩：如果没有贴图也没有文字，提供一个基本的视觉下限
        if (minTexW == 0 && calcWidth == 0) calcWidth = 20;
        if (minTexH == 0 && calcHeight == 0) calcHeight = 20;

        calcWidth = Math.max(calcWidth, minTexW);
        calcHeight = Math.max(calcHeight, minTexH);

        this.setSize(calcWidth, calcHeight);
    }

    public BaseUIButton setOnClick(Runnable action) { this.onClickAction = action; return this; }
    public BaseUIButton setDisabled(boolean disabled) { this.disabled = disabled; return this; }
    public BaseUIButton setPlayClickSound(boolean play) { this.playClickSound = play; return this; }
    public BaseUIButton setPlayHoverSound(boolean play) { this.playHoverSound = play; return this; }

    public BaseUIButton setTexture(BaseUINineSliceTexture texture) {
        return setTextures(texture, texture, texture, texture);
    }

    public BaseUIButton setTextures(BaseUINineSliceTexture normal, BaseUINineSliceTexture hover, BaseUINineSliceTexture pressed, BaseUINineSliceTexture disabled) {
        this.texNormal = normal;
        this.texHover = hover != null ? hover : normal;
        this.texPressed = pressed != null ? pressed : normal;
        this.texDisabled = disabled != null ? disabled : normal;
        if (autoSize) this.refreshSize();
        return this;
    }

    public BaseUIButton setTextColors(int normal, int hover, int pressed, int disabled) {
        this.colorNormal = normal;
        this.colorHover = hover;
        this.colorPressed = pressed;
        this.colorDisabled = disabled;
        return this;
    }

    // ==========================================
    // 视觉与听觉渲染管线
    // ==========================================
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {

        boolean currentHover = isHovered();

        // 声音触发必须留在 render 中，应对 UI 滚动位移导致的被动悬停
        if (currentHover && !wasHoveredLastFrame && !disabled && playHoverSound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.25F));
        }
        this.wasHoveredLastFrame = currentHover;

        BaseUINineSliceTexture currentTex = texNormal;
        int currentTextColor = colorNormal;
        // 经典的 OS 原生 UI 反馈逻辑：按下且鼠标移回按钮范围内才显示按下状态
        boolean visuallyPressed = isPressed() && currentHover;

        if (disabled) {
            currentTex = texDisabled;
            currentTextColor = colorDisabled;
        } else if (visuallyPressed) {
            currentTex = texPressed;
            currentTextColor = colorPressed;
        } else if (currentHover || isFocused()) {
            currentTex = texHover;
            currentTextColor = colorHover;
        }

        if (currentTex != null) {
            currentTex.render(graphics, 0, 0, this.width, this.height, finalAlpha);
        } else {
            int baseAlpha = Math.max(0, Math.min(255, Math.round(FALLBACK_BASE_ALPHA * finalAlpha)));
            int rgb = disabled ? 0x555555 : (visuallyPressed ? 0x222222 : ((currentHover || isFocused()) ? 0x777777 : 0x000000));
            graphics.fill(0, 0, this.width, this.height, (baseAlpha << 24) | rgb);
        }

        if (text != null && !text.getString().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(text);
            int textX = (this.width - textWidth) / 2;
            int textY = (this.height - font.lineHeight) / 2 + 1;

            if (visuallyPressed && !disabled) textY += 1;

            int finalTextColor = applyAlpha(currentTextColor, finalAlpha);
            graphics.drawString(font, text, textX, textY, finalTextColor, false);
        }
    }

    private int applyAlpha(int rgbColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (rgbColor & 0x00FFFFFF) | (a << 24);
    }

    // ==========================================
    // 物理交互管线
    // ==========================================

    @Override
    protected boolean onClicked(double mouseX, double mouseY, int button) {
        if (disabled) {
            if (playClickSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8F, 0.5F));
            }
            return true;
        }
        if (button != 0) return false;
        return true;
    }

    @Override
    protected boolean onReleased(double mouseX, double mouseY, int button) {
        if (disabled || button != 0) return false;

        boolean releasedInside = mouseX >= 0 && mouseX < this.width && mouseY >= 0 && mouseY < this.height;
        if (releasedInside && onClickAction != null) {
            if (playClickSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            onClickAction.run();
        }
        return true;
    }

    @Override
    protected boolean onDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (disabled || button != 0) return false;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (disabled || !isFocused()) return false;

        // 消除魔法数字，使用 Minecraft 原生 InputConstants 映射，并增加小键盘回车支持
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER || keyCode == InputConstants.KEY_SPACE) {
            if (onClickAction != null) {
                if (playClickSound) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                onClickAction.run();
            }
            return true;
        }
        return false;
    }
}