package net.burgerfarm.baseui.client.example;

import net.burgerfarm.baseui.client.components.BaseUIButton;
import net.burgerfarm.baseui.client.components.BaseUIText;
import net.burgerfarm.baseui.client.components.BaseUITextInput;
import net.burgerfarm.baseui.client.core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;

public final class ExampleRootElement extends BaseUIElement {
    private final BaseUIText statusText;

    public ExampleRootElement() {
        BaseUIButton actionButton = new BaseUIButton();
        actionButton.setText("Click Me");
        actionButton.setPos(20, 20);
        actionButton.setSize(140, 24);

        statusText = new BaseUIText();
        statusText.setText("Demo text: waiting for interaction");
        statusText.setPos(20, 56);
        statusText.setSize(300, 18);

        BaseUITextInput textInput = new BaseUITextInput();
        textInput.setPlaceholder("Type something...");
        textInput.setPos(20, 84);
        textInput.setSize(220, 22);
        textInput.setOnTextChanged(text -> statusText.setText("Input: " + text));

        actionButton.setOnClick(() -> statusText.setText("Button clicked"));

        this.addChild(actionButton);
        this.addChild(statusText);
        this.addChild(textInput);
    }

    @Override
    protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
    }
}
