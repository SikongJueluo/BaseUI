package net.burgerfarm.baseui.Components;

import com.mojang.blaze3d.platform.InputConstants;
import net.burgerfarm.baseui.core.BaseUIElement;
import net.burgerfarm.baseui.Render.BaseUINineSliceTexture;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * BaseUI 高级交互组件：文本输入框
 * 特性：选区渲染、剪贴板、防密码拷贝、上限反馈音效、自定义掩码、极速打断优化。
 * <p>
 * 该组件提供了完整的文本输入功能，包括：
 * - 光标移动（鼠标点击/方向键/Home/End）
 * - 文本选区（鼠标拖拽/Shift+方向键）
 * - 剪贴板操作（Ctrl+A/C/X/V），密码模式下禁止复制/剪切
 * - 输入验证（长度限制、自定义验证器）
 * - 视觉反馈（焦点高亮、选区半透明蓝、闪烁光标）
 * - 音效反馈（点击、错误）
 * - 支持占位符、密码掩码
 */
public class BaseUITextInput extends BaseUIElement<BaseUITextInput> {

    // ==========================================
    // 核心文本数据
    // ==========================================

    /** 当前输入的文本内容 */
    private String text = "";
    /** 最大允许输入的字符数 */
    private int maxLength = 32;
    /** 占位符文本，在输入框为空且未聚焦时显示 */
    private String placeholder = "";

    // ==========================================
    // 扩展特性
    // ==========================================

    /** 是否为密码模式（显示掩码） */
    private boolean isPassword = false;
    /** 密码掩码字符，默认 '*' */
    private char passwordChar = '*';

    // ==========================================
    // 回调与验证
    // ==========================================

    /** 文本内容变化时的回调，参数为变化后的完整文本 */
    private Consumer<String> onTextChanged = null;
    /** 按下回车键时的回调，参数为当前文本 */
    private Consumer<String> onEnterPressed = null;
    /** 输入验证器，在文本即将改变时调用，返回 true 表示允许改变 */
    private Predicate<String> validator = s -> true;

    // ==========================================
    // 光标引擎变量
    // ==========================================

    /** 光标闪烁计数器，每 tick 增加 1，用于控制光标闪烁频率 */
    private int cursorBlinkCounter = 0;
    /** 当前光标位置（插入点索引），范围 0 到 text.length() */
    private int cursorPos = 0;
    /** 选区锚点位置，与 cursorPos 不同时表示存在选区 */
    private int selectionPos = 0;

    // ==========================================
    // 视觉配置
    // ==========================================

    /** 文本左侧内边距（像素） */
    private int paddingX = 4;
    /** 正常状态下的文本颜色（ARGB） */
    private int textColorNormal = 0xFFFFFF;
    /** 禁用状态下的文本颜色（ARGB） */
    private int textColorDisabled = 0x888888;
    /** 占位符文本颜色（ARGB） */
    private int textColorPlaceholder = 0x777777;

    /** 正常状态的九宫格背景纹理 */
    private BaseUINineSliceTexture texNormal = null;
    /** 聚焦状态的九宫格背景纹理 */
    private BaseUINineSliceTexture texFocused = null;
    /** 禁用状态的九宫格背景纹理 */
    private BaseUINineSliceTexture texDisabled = null;

    /** 禁用状态的九宫格背景纹理 */
    private boolean disabled = false;
    /** 是否播放点击音效（包括正常点击和错误音效） */
    private boolean playClickSound = true;

    /**
     * 构造一个默认的文本输入框，初始尺寸 120x20，可聚焦。
     */
    public BaseUITextInput() {
        this.width = 120;
        this.height = 20;
        this.focusable = true;
    }

    // ==========================================
    // 链式 API
    // ==========================================

    /**
     * 设置输入框的文本内容。
     * <p>
     * 如果新文本超过最大长度，将被截断。只有通过验证器的新文本才会被接受，
     * 同时光标和选区将被重置到文本末尾。
     *
     * @param text 新文本，若为 null 则视为空字符串
     * @return 当前实例，用于链式调用
     */
    public BaseUITextInput setText(String text) {
        String newText = text != null ? text : "";
        if (newText.length() > this.maxLength) newText = newText.substring(0, this.maxLength);
        if (this.validator.test(newText)) {
            this.text = newText;
            this.cursorPos = Math.min(this.cursorPos, this.text.length());
            this.selectionPos = this.cursorPos;
        }
        return this;
    }

    /** @return 当前文本内容 */
    public String getText() { return this.text; }

    /** 设置最大输入长度 */
    public BaseUITextInput setMaxLength(int maxLength) { this.maxLength = maxLength; return this; }

    /** 设置占位符文本 */
    public BaseUITextInput setPlaceholder(String placeholder) { this.placeholder = placeholder != null ? placeholder : ""; return this; }

    /** 设置是否启用密码模式 */
    public BaseUITextInput setPasswordMode(boolean isPassword) { this.isPassword = isPassword; return this; }
    /** 设置密码掩码字符（仅在密码模式下生效） */
    public BaseUITextInput setPasswordChar(char passwordChar) { this.passwordChar = passwordChar; return this; }

    /** 设置文本变化回调 */
    public BaseUITextInput setOnTextChanged(Consumer<String> callback) { this.onTextChanged = callback; return this; }
    /** 设置回车按下回调 */
    public BaseUITextInput setOnEnterPressed(Consumer<String> callback) { this.onEnterPressed = callback; return this; }
    /** 设置输入验证器，默认始终允许 */
    public BaseUITextInput setValidator(Predicate<String> validator) { this.validator = validator != null ? validator : s -> true; return this; }

    /**
     * 设置禁用状态。
     * <p>
     * 禁用时会剥夺焦点（如果当前拥有焦点），并设置 focusable = false。
     *
     * @param disabled true 为禁用
     * @return 当前实例
     */
    public BaseUITextInput setDisabled(boolean disabled) {
        this.disabled = disabled;
        this.focusable = !disabled;
        if (disabled && this.isFocused()) BaseUIElement.resetAllStates();
        return this;
    }

    /**
     * 设置三种状态下的九宫格纹理。
     *
     * @param normal   正常状态纹理
     * @param focused  聚焦状态纹理（若为 null 则复用 normal）
     * @param disabled 禁用状态纹理（若为 null 则复用 normal）
     * @return 当前实例
     */
    public BaseUITextInput setTextures(BaseUINineSliceTexture normal, BaseUINineSliceTexture focused, BaseUINineSliceTexture disabled) {
        this.texNormal = normal;
        this.texFocused = focused != null ? focused : normal;
        this.texDisabled = disabled != null ? disabled : normal;
        return this;
    }

    // ==========================================
    // 视觉与音频配置 API
    // ==========================================

    /** 设置文本左侧缩进边距 */
    public BaseUITextInput setPaddingX(int paddingX) {
        this.paddingX = paddingX;
        return this;
    }

    /** 批量设置不同状态下的文本颜色 (ARGB格式) */
    public BaseUITextInput setTextColors(int normalColor, int disabledColor, int placeholderColor) {
        this.textColorNormal = normalColor;
        this.textColorDisabled = disabledColor;
        this.textColorPlaceholder = placeholderColor;
        return this;
    }

    /** 开关输入框的点击交互音效 */
    public BaseUITextInput setPlayClickSound(boolean play) {
        this.playClickSound = play;
        return this;
    }

    // ==========================================
    // Tick 与 渲染管线
    // ==========================================

    /**
     * 每帧调用一次，用于更新光标闪烁计数器。
     * 应在所属屏幕的 tick() 方法中调用此方法。
     */
    public void tick() {
        onTick();
    }

    @Override
    protected void onTick() {
        if (this.isFocused()) {
            this.cursorBlinkCounter++;
        } else {
            this.cursorBlinkCounter = 0;
        }
    }

    /**
     * 绘制输入框自身。
     *
     * @param graphics    绘图对象
     * @param mouseX      鼠标相对于当前组件的 X 坐标
     * @param mouseY      鼠标相对于当前组件的 Y 坐标
     * @param partialTick 部分 tick（用于动画，此处未使用）
     * @param finalAlpha  最终透明度（已乘父级透明度）
     */
    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        // 1. 绘制背景（九宫格纹理或纯色回退）
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
        String displayText = isPassword ? String.valueOf(passwordChar).repeat(this.text.length()) : this.text;

        // 2. 绘制选区（遮罩放在文字下方）
        if (this.isFocused() && this.cursorPos != this.selectionPos) {
            int start = Math.min(this.cursorPos, this.selectionPos);
            int end = Math.max(this.cursorPos, this.selectionPos);
            int startX = paddingX + font.width(displayText.substring(0, start));
            int endX = paddingX + font.width(displayText.substring(0, end));

            graphics.fill(startX, textY - 1, endX, textY + font.lineHeight, applyAlpha(0x880000FF, finalAlpha));
        }

        // 3. 绘制文本或占位符
        if (this.text.isEmpty() && !this.placeholder.isEmpty() && !this.isFocused()) {
            int color = applyAlpha(textColorPlaceholder, finalAlpha);
            graphics.drawString(font, this.placeholder, paddingX, textY, color, false);
        } else {
            int color = applyAlpha(disabled ? textColorDisabled : textColorNormal, finalAlpha);
            graphics.drawString(font, displayText, paddingX, textY, color, true);
        }

        // 4. 绘制闪烁光标（仅在聚焦时）
        if (this.isFocused() && (this.cursorBlinkCounter / 10) % 2 == 0) {
            String beforeCursor = displayText.substring(0, Math.min(this.cursorPos, displayText.length()));
            int cursorX = paddingX + font.width(beforeCursor);
            graphics.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight, applyAlpha(0xFFDDDDDD, finalAlpha));
        }
    }

    /**
     * 将 RGB 颜色与透明度混合，返回 ARGB 颜色值。
     *
     * @param rgbColor 原始 RGB 颜色（忽略高位）
     * @param alpha    透明度 0.0~1.0
     * @return 混合后的 ARGB 颜色
     */
    private int applyAlpha(int rgbColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (rgbColor & 0x00FFFFFF) | (a << 24);
    }

    // ==========================================
    // 选区数学与数据处理引擎
    // ==========================================

    /**
     * 根据鼠标点击位置（相对于组件左上角的 X 坐标）计算出对应的字符索引。
     *
     * @param mouseX 鼠标相对于组件左上角的 X 坐标
     * @return 最接近点击位置的字符索引
     */
    private int getIndexAtMouse(double mouseX) {
        Font font = Minecraft.getInstance().font;
        String displayText = isPassword ? String.valueOf(passwordChar).repeat(this.text.length()) : this.text;
        int clickX = (int) mouseX - paddingX;

        if (clickX <= 0) return 0;
        int currentW = 0;
        for (int i = 0; i < displayText.length(); i++) {
            int charW = font.width(displayText.substring(i, i + 1));
            if (clickX < currentW + charW / 2) return i;
            currentW += charW;
        }
        return this.text.length();
    }

    /**
     * 插入文本到当前光标/选区位置。
     * <p>
     * 如果存在选区，则用新文本替换选中的部分；否则在光标处插入。
     * 自动过滤非法字符、截断长度、播放超限音效，并通过验证器。
     *
     * @param input 要插入的原始文本（可能包含非法字符）
     */
    private void insertText(String input) {
        int start = Math.min(this.cursorPos, this.selectionPos);
        int end = Math.max(this.cursorPos, this.selectionPos);

        // 过滤掉不允许的聊天字符
        StringBuilder filtered = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (SharedConstants.isAllowedChatCharacter(c)) filtered.append(c);
        }
        input = filtered.toString();

        // 如果输入为空且没有选区，直接返回，避免不必要的容量计算
        if (input.isEmpty() && start == end) return;

        int availableSpace = this.maxLength - (this.text.length() - (end - start));

        // 如果无剩余空间且有输入，播放失败音效并返回
        if (availableSpace <= 0 && !input.isEmpty()) {
            if (playClickSound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8F, 0.5F));
            return;
        }

        // 截断输入以适应当前可用空间
        if (input.length() > availableSpace) {
            input = input.substring(0, availableSpace);
        }

        String proposedText = this.text.substring(0, start) + input + this.text.substring(end);

        if (this.validator.test(proposedText)) {
            this.text = proposedText;
            this.cursorPos = start + input.length();
            this.selectionPos = this.cursorPos;
            if (this.onTextChanged != null) this.onTextChanged.accept(this.text);
        }
    }

    /** @return 当前选中的文本，若无选区则返回空字符串 */
    private String getSelectedText() {
        int start = Math.min(this.cursorPos, this.selectionPos);
        int end = Math.max(this.cursorPos, this.selectionPos);
        return this.text.substring(start, end);
    }

    // ==========================================
    // 底层物理事件接管
    // ==========================================

    /**
     * 鼠标点击事件处理。
     * <p>
     * 点击时定位光标到对应位置，清空选区，重置闪烁计数器，并播放音效。
     *
     * @param mouseX 鼠标相对于当前组件的 X 坐标
     * @param mouseY 鼠标相对于当前组件的 Y 坐标
     * @param button 鼠标按键（0=左键）
     * @return true 表示事件已消费
     */
    @Override
    protected boolean onClicked(double mouseX, double mouseY, int button) {
        if (disabled) {
            if (playClickSound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 0.8F, 0.5F));
            return true;
        }
        if (button == 0) {
            if (playClickSound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.cursorPos = getIndexAtMouse(mouseX);
            this.selectionPos = this.cursorPos;
            this.cursorBlinkCounter = 0;
            return true;
        }
        return false;
    }

    /**
     * 鼠标拖拽事件处理。
     * <p>
     * 拖拽时移动光标（cursorPos）而保留选区锚点（selectionPos），从而形成选区。
     *
     * @param mouseX 鼠标相对于当前组件的 X 坐标
     * @param mouseY 鼠标相对于当前组件的 Y 坐标
     * @param button 鼠标按键
     * @param dragX  X 轴拖拽增量（此处未使用）
     * @param dragY  Y 轴拖拽增量（此处未使用）
     * @return true 表示事件已消费
     */
    @Override
    protected boolean onDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isFocused() || disabled || button != 0) return false;

        this.cursorPos = getIndexAtMouse(mouseX);
        this.cursorBlinkCounter = 0;
        return true;
    }

    /**
     * 字符输入事件处理。
     * <p>
     * 仅当聚焦且未禁用时处理。如果字符合法，则插入到当前光标/选区位置。
     *
     * @param codePoint 输入的字符
     * @param modifiers 修饰键
     * @return true 表示事件已消费
     */
    @Override
    protected boolean onCharTyped(char codePoint, int modifiers) {
        if (!isFocused() || disabled) return false;
        if (SharedConstants.isAllowedChatCharacter(codePoint)) {
            insertText(String.valueOf(codePoint));
            this.cursorBlinkCounter = 0;
            return true;
        }
        return false;
    }

    /**
     * 键盘按键事件处理。
     * <p>
     * 支持方向键、Home/End、退格、Delete、回车以及 Ctrl+A/C/X/V 剪贴板操作。
     * 配合 Shift 键可扩展选区。
     *
     * @param keyCode   按键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键
     * @return true 表示事件已消费
     */
    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || disabled) return false;

        this.cursorBlinkCounter = 0;

        boolean isShiftDown = Screen.hasShiftDown();
        boolean isCtrlDown = Screen.hasControlDown();

        // 剪贴板操作
        if (isCtrlDown) {
            if (keyCode == InputConstants.KEY_A) {
                this.selectionPos = 0;
                this.cursorPos = this.text.length();
                return true;
            }
            else if (keyCode == InputConstants.KEY_C && this.cursorPos != this.selectionPos && !this.isPassword) {
                Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                return true;
            }
            else if (keyCode == InputConstants.KEY_X && this.cursorPos != this.selectionPos && !this.isPassword) {
                Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                insertText("");
                return true;
            }
            else if (keyCode == InputConstants.KEY_V) {
                insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
        }

        // 方向键微操
        if (keyCode == InputConstants.KEY_LEFT) {
            if (this.cursorPos > 0) this.cursorPos--;
            if (!isShiftDown) this.selectionPos = this.cursorPos;
            return true;
        }
        else if (keyCode == InputConstants.KEY_RIGHT) {
            if (this.cursorPos < this.text.length()) this.cursorPos++;
            if (!isShiftDown) this.selectionPos = this.cursorPos;
            return true;
        }
        else if (keyCode == InputConstants.KEY_HOME) {
            this.cursorPos = 0;
            if (!isShiftDown) this.selectionPos = this.cursorPos;
            return true;
        }
        else if (keyCode == InputConstants.KEY_END) {
            this.cursorPos = this.text.length();
            if (!isShiftDown) this.selectionPos = this.cursorPos;
            return true;
        }

        // 退格与删除
        else if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (this.cursorPos != this.selectionPos) {
                insertText("");
            } else if (this.cursorPos > 0 && !this.text.isEmpty()) {
                this.selectionPos = this.cursorPos - 1;
                insertText("");
            }
            return true;
        }
        else if (keyCode == InputConstants.KEY_DELETE) {
            if (this.cursorPos != this.selectionPos) {
                insertText("");
            } else if (this.cursorPos < this.text.length()) {
                this.selectionPos = this.cursorPos + 1;
                insertText("");
            }
            return true;
        }

        // 回车键
        else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (this.onEnterPressed != null) this.onEnterPressed.accept(this.text);
            BaseUIElement.resetAllStates();
            return true;
        }

        return false;
    }
}
