package net.burgerfarm.baseui.Components;

import com.mojang.blaze3d.platform.InputConstants;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.burgerfarm.baseui.Render.BaseUINineSliceTexture;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * CS UI 高级交互组件：文本输入框 (阶段一：基础打字、焦点防御、密码模式、回调)
 */
public class BaseUITextInput extends BaseUIElement<BaseUITextInput> {

    // 核心文本数据
    private String text = "";
    private int maxLength = 32;
    private String placeholder = "";

    // 扩展特性
    private boolean isPassword = false;
    private char passwordChar = '*';

    // 回调与验证
    private Consumer<String> onTextChanged = null;
    private Consumer<String> onEnterPressed = null;
    private Predicate<String> validator = s -> true; // 默认允许所有合法字符组合

    // 光标引擎
    private int cursorBlinkCounter = 0;

    // 视觉配置
    private int paddingX = 4;
    private int textColorNormal = 0xFFFFFF;
    private int textColorDisabled = 0x888888;
    private int textColorPlaceholder = 0x777777;

    private BaseUINineSliceTexture texNormal = null;
    private BaseUINineSliceTexture texFocused = null;
    private BaseUINineSliceTexture texDisabled = null;

    private boolean disabled = false;
    private boolean playClickSound = true;

    public BaseUITextInput() {
        this.width = 120;
        this.height = 20;
        this.focusable = true;
    }

    // ==========================================
    // 链式 API
    // ==========================================

    public BaseUITextInput setText(String text) {
        String newText = text != null ? text : "";
        if (newText.length() > this.maxLength) newText = newText.substring(0, this.maxLength);
        if (this.validator.test(newText)) {
            this.text = newText;
        }
        return this;
    }

    public String getText() { return this.text; }

    public BaseUITextInput setMaxLength(int maxLength) { this.maxLength = maxLength; return this; }
    public BaseUITextInput setPlaceholder(String placeholder) { this.placeholder = placeholder != null ? placeholder : ""; return this; }
    public BaseUITextInput setPasswordMode(boolean isPassword) { this.isPassword = isPassword; return this; }

    public BaseUITextInput setOnTextChanged(Consumer<String> callback) { this.onTextChanged = callback; return this; }
    public BaseUITextInput setOnEnterPressed(Consumer<String> callback) { this.onEnterPressed = callback; return this; }
    public BaseUITextInput setValidator(Predicate<String> validator) { this.validator = validator != null ? validator : s -> true; return this; }

    public BaseUITextInput setDisabled(boolean disabled) {
        this.disabled = disabled;
        this.focusable = !disabled; // 禁用时剥夺获取焦点的物理权利
        if (disabled && this.isFocused()) {
            BaseUIElement.resetAllStates(); // 如果正在输入时被突然禁用，强制踢掉焦点
        }
        return this;
    }

    public BaseUITextInput setTextures(BaseUINineSliceTexture normal, BaseUINineSliceTexture focused, BaseUINineSliceTexture disabled) {
        this.texNormal = normal;
        this.texFocused = focused != null ? focused : normal;
        this.texDisabled = disabled != null ? disabled : normal;
        return this;
    }

    // ==========================================
    // Tick 与 渲染
    // ==========================================

    public void tick() {
        if (this.isFocused()) this.cursorBlinkCounter++;
        else this.cursorBlinkCounter = 0;
    }

    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {

        // 1. 背景
        BaseUINineSliceTexture currentTex = disabled ? texDisabled : (isFocused() ? texFocused : texNormal);
        if (currentTex != null) {
            currentTex.render(graphics, 0, 0, this.width, this.height, finalAlpha);
        } else {
            int bgColor = disabled ? 0x55222222 : (isFocused() ? 0xAA000000 : 0x77000000);
            graphics.fill(0, 0, this.width, this.height, bgColor);
            if (isFocused()) graphics.renderOutline(0, 0, this.width, this.height, 0xFF00FF00);
        }

        Font font = Minecraft.getInstance().font;
        int textY = (this.height - font.lineHeight) / 2 + 1;

        // 密码掩码处理
        String displayText = isPassword ? String.valueOf(passwordChar).repeat(this.text.length()) : this.text;

        // 2. 文本与占位符
        if (this.text.isEmpty() && !this.placeholder.isEmpty() && !this.isFocused()) {
            int color = applyAlpha(textColorPlaceholder, finalAlpha);
            graphics.drawString(font, this.placeholder, paddingX, textY, color, false);
        } else {
            int color = applyAlpha(disabled ? textColorDisabled : textColorNormal, finalAlpha);
            graphics.drawString(font, displayText, paddingX, textY, color, true);
        }

        // 3. 闪烁光标 (基于 displayText 测宽)
        if (this.isFocused() && (this.cursorBlinkCounter / 10) % 2 == 0) {
            int textWidth = font.width(displayText);
            int cursorX = paddingX + textWidth;
            graphics.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight, applyAlpha(0xFFDDDDDD, finalAlpha));
        }
    }

    private int applyAlpha(int rgbColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (rgbColor & 0x00FFFFFF) | (a << 24);
    }

    // ==========================================
    // 底层事件
    // ==========================================

    @Override
    protected boolean onClicked(double mouseX, double mouseY, int button) {
        if (disabled) {
            if (playClickSound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8F, 0.5F));
            return true;
        }
        if (button == 0) {
            if (playClickSound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCharTyped(char codePoint, int modifiers) {
        if (!isFocused() || disabled) return false;

        if (SharedConstants.isAllowedChatCharacter(codePoint)) {
            if (this.text.length() < this.maxLength) {
                String proposedText = this.text + codePoint;
                if (this.validator.test(proposedText)) {
                    this.text = proposedText;
                    if (this.onTextChanged != null) this.onTextChanged.accept(this.text);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || disabled) return false;

        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (!this.text.isEmpty()) {
                this.text = this.text.substring(0, this.text.length() - 1);
                if (this.onTextChanged != null) this.onTextChanged.accept(this.text);
                return true;
            }
        }

        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (this.onEnterPressed != null) this.onEnterPressed.accept(this.text);
            BaseUIElement.resetAllStates();
            return true;
        }

        return false;
    }
}