package net.burgerfarm.baseui.Client.Screens;

import net.burgerfarm.baseui.BaseUIMod;
import net.burgerfarm.baseui.Client.Render.BaseUINineSliceTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ExamplePlaceholderScreen extends Screen {
    private static final ResourceLocation PLACEHOLDER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        BaseUIMod.MODID,
        "textures/gui/example_placeholder.png"
    );
    private static final BaseUINineSliceTexture PLACEHOLDER_PANEL = BaseUINineSliceTexture.of(
        PLACEHOLDER_TEXTURE,
        16,
        16,
        0,
        0,
        16,
        16,
        4
    );

    public ExamplePlaceholderScreen() {
        super(Component.literal("BaseUI Example Placeholder"));
    }

    public static void openOnClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ExamplePlaceholderScreen()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int panelWidth = Math.min(220, this.width - 20);
        int panelHeight = Math.min(120, this.height - 20);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        PLACEHOLDER_PANEL.render(graphics, panelX, panelY, panelWidth, panelHeight, 1.0f);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Press ESC to close"), this.width / 2, panelY + panelHeight - 20, 0xFFE0E0E0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
