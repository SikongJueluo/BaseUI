package net.burgerfarm.baseui.Components;

import com.mojang.blaze3d.platform.InputConstants;
import net.burgerfarm.baseui.core.BaseUIElement;
import net.burgerfarm.baseui.Render.BaseUINineSliceTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * BaseUI 基础交互组件：拖拽滑块
 * 集成：水平/垂直双向支持、绝对安全的焦点隔离、Home/End/PageUp/Down 极速跳转、消除浮点精度溢出。
 */
public class BaseUISlider extends BaseUIElement<BaseUISlider> {

    public enum Direction { HORIZONTAL, VERTICAL }

    private Direction direction = Direction.HORIZONTAL;

    private double min = 0.0;
    private double max = 1.0;
    private double value = 0.5;
    private double step = 0.0;

    // 沿主轴方向的滑块尺寸（水平时为宽，垂直时为高）
    private int thumbSize = 10;
    private boolean disabled = false;
    private boolean playClickSound = true;

    private Consumer<Double> onValueChanged = null;
    private Function<Double, Component> labelFormatter = null;

    private BaseUINineSliceTexture trackTex = null;
    private BaseUINineSliceTexture thumbNormal = null;
    private BaseUINineSliceTexture thumbHover = null;
    private BaseUINineSliceTexture thumbPressed = null;
    private BaseUINineSliceTexture thumbDisabled = null;

    private int textColorNormal = 0xFFFFFF;
    private int textColorDisabled = 0x888888;

    public BaseUISlider() {
        this.width = 100;
        this.height = 20;
        this.focusable = true;
    }

    // ==========================================
    // 链式 API 与 属性设置
    // ==========================================
    public BaseUISlider setDirection(Direction direction) { this.direction = direction; return this; }

    public BaseUISlider setRange(double min, double max) {
        this.min = min;
        this.max = max;
        this.setValue(this.value);
        return this;
    }

    public BaseUISlider setStep(double step) { this.step = step; return this; }
    public BaseUISlider setThumbSize(int thumbSize) { this.thumbSize = thumbSize; return this; }
    public BaseUISlider setDisabled(boolean disabled) { this.disabled = disabled; return this; }
    public BaseUISlider setPlayClickSound(boolean play) { this.playClickSound = play; return this; }
    public BaseUISlider setOnValueChanged(Consumer<Double> callback) { this.onValueChanged = callback; return this; }
    public BaseUISlider setLabelFormatter(Function<Double, Component> formatter) { this.labelFormatter = formatter; return this; }

    public BaseUISlider setValue(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        if (step > 0) {
            clamped = Math.round(clamped / step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        this.value = clamped;
        return this;
    }

    public double getValue() { return this.value; }

    public BaseUISlider setTrackTexture(BaseUINineSliceTexture texture) {
        this.trackTex = texture;
        return this;
    }

    public BaseUISlider setThumbTexture(BaseUINineSliceTexture texture) {
        return setTextures(this.trackTex, texture, texture, texture, texture);
    }

    public BaseUISlider setTextures(BaseUINineSliceTexture track, BaseUINineSliceTexture thumbNormal, BaseUINineSliceTexture thumbHover, BaseUINineSliceTexture thumbPressed, BaseUINineSliceTexture thumbDisabled) {
        this.trackTex = track;
        this.thumbNormal = thumbNormal;
        this.thumbHover = thumbHover != null ? thumbHover : thumbNormal;
        this.thumbPressed = thumbPressed != null ? thumbPressed : thumbNormal;
        this.thumbDisabled = thumbDisabled != null ? thumbDisabled : thumbNormal;
        return this;
    }

    public BaseUISlider setTextColors(int normal, int disabled) {
        this.textColorNormal = normal;
        this.textColorDisabled = disabled;
        return this;
    }

    // ==========================================
    // 视觉渲染管线 (双向适配)
    // ==========================================
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        // 1. 绘制轨道
        if (trackTex != null) {
            trackTex.render(graphics, 0, 0, this.width, this.height, finalAlpha);
        } else {
            int baseAlpha = Math.max(0, Math.min(255, Math.round(0x88 * finalAlpha)));
            int color = (baseAlpha << 24);
            if (direction == Direction.HORIZONTAL) {
                graphics.fill(0, this.height / 2 - 2, this.width, this.height / 2 + 2, color);
            } else {
                graphics.fill(this.width / 2 - 2, 0, this.width / 2 + 2, this.height, color);
            }
        }

        // 2. 计算滑块位置与尺寸
        double progress = (this.max <= this.min) ? 0.0 : (this.value - this.min) / (this.max - this.min);
        int thumbX = 0, thumbY = 0;
        int tWidth, tHeight;

        if (direction == Direction.HORIZONTAL) {
            thumbX = (int) (progress * (this.width - this.thumbSize));
            tWidth = this.thumbSize;
            tHeight = this.height;
        } else {
            thumbY = (int) (progress * (this.height - this.thumbSize));
            tWidth = this.width;
            tHeight = this.thumbSize;
        }

        // 3. 确定当前贴图与颜色
        BaseUINineSliceTexture currentThumb = thumbNormal;
        boolean visuallyPressed = isPressed();
        boolean visuallyHovered = isHovered() || isFocused();

        if (disabled) currentThumb = thumbDisabled;
        else if (visuallyPressed) currentThumb = thumbPressed;
        else if (visuallyHovered) currentThumb = thumbHover;

        // 4. 渲染滑块
        if (currentThumb != null) {
            currentThumb.render(graphics, thumbX, thumbY, tWidth, tHeight, finalAlpha);
        } else {
            int baseAlpha = Math.max(0, Math.min(255, Math.round(0xFF * finalAlpha)));
            int rgb = disabled ? 0x555555 : (visuallyPressed ? 0xAAAAAA : (visuallyHovered ? 0xFFFFFF : 0xDDDDDD));
            graphics.fill(thumbX, thumbY, thumbX + tWidth, thumbY + tHeight, (baseAlpha << 24) | rgb);
        }

        // 5. 渲染格式化文本 (具备方向感知)
        if (labelFormatter != null) {
            Component text = labelFormatter.apply(this.value);
            if (text != null) {
                Font font = Minecraft.getInstance().font;
                int textWidth = font.width(text);
                int textX = (this.width - textWidth) / 2;

                int textY;
                if (direction == Direction.HORIZONTAL) {
                    textY = (this.height - font.lineHeight) / 2 + 1; // 水平模式居中
                } else {
                    textY = this.height - font.lineHeight - 2; // 垂直模式靠底，防遮挡
                }

                int targetColor = disabled ? textColorDisabled : textColorNormal;
                int finalColor = applyAlpha(targetColor, finalAlpha);

                graphics.drawString(font, text, textX, textY, finalColor, true);
            }
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
        if (disabled || button != 0) {
            if (disabled && playClickSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8F, 0.5F));
            }
            return disabled;
        }
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    protected boolean onDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (disabled || button != 0) return false;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    protected boolean onScrolled(double mouseX, double mouseY, double delta) {
        if (disabled || !isHovered()) return false;

        int scrollDir = (int) Math.signum(delta);
        double stepAmount = (step > 0) ? step : (max - min) / 20.0;

        double oldValue = this.value;
        setValue(this.value + (scrollDir * stepAmount));

        if (this.value != oldValue) {
            if (onValueChanged != null) onValueChanged.accept(this.value);
            if (playClickSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.5F));
            }
        }
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (disabled || !isFocused()) return false;

        double stepAmount = (step > 0) ? step : (max - min) / 20.0;
        double oldValue = this.value;

        // 使用Minecraft 原生 InputConstants 常量，彻底告别魔法数字
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_DOWN) {
            setValue(this.value - stepAmount);
        } else if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_UP) {
            setValue(this.value + stepAmount);
        } else if (keyCode == InputConstants.KEY_HOME) {
            setValue(this.min);
        } else if (keyCode == InputConstants.KEY_END) {
            setValue(this.max);
        } else if (keyCode == InputConstants.KEY_PAGEUP) { // PageUp 大步进
            setValue(this.value + stepAmount * 5);
        } else if (keyCode == InputConstants.KEY_PAGEDOWN) { // PageDown 大步进
            setValue(this.value - stepAmount * 5);
        } else {
            return false;
        }

        if (this.value != oldValue) {
            if (onValueChanged != null) onValueChanged.accept(this.value);
            if (playClickSound) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.5F));
            }
        }
        return true;
    }

    /**
     * 核心数学：根据轴向换算鼠标的投影百分比，并处理浮点精度
     */
    private void updateValueFromMouse(double mouseX, double mouseY) {
        if (this.max <= this.min) return;

        double progress;
        if (direction == Direction.HORIZONTAL) {
            if (this.width <= this.thumbSize) return;
            progress = (mouseX - this.thumbSize / 2.0) / (this.width - this.thumbSize);
        } else {
            if (this.height <= this.thumbSize) return;
            progress = (mouseY - this.thumbSize / 2.0) / (this.height - this.thumbSize);
        }

        progress = Math.max(0.0, Math.min(1.0, progress));

        double rawValue = this.min + progress * (this.max - this.min);
        if (this.step > 0) {
            // 完美处理 IEEE-754 浮点数精度溢出，防止出现 0.300000000004 这种脏数据
            rawValue = Math.round(rawValue / this.step) * this.step;
            rawValue = Math.round(rawValue * 100000.0) / 100000.0;
            rawValue = Math.max(this.min, Math.min(this.max, rawValue));
        }

        if (this.value != rawValue) {
            this.value = rawValue;
            if (this.onValueChanged != null) this.onValueChanged.accept(this.value);

            if (playClickSound && this.step > 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.25F));
            }
        }
    }
}