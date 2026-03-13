package net.burgerfarm.baseui.Client.Screens;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.function.Supplier;
import net.burgerfarm.baseui.Client.Render.BaseUIRender;
import net.burgerfarm.baseui.Client.Render.BaseUIRenderContext;
import net.burgerfarm.baseui.Client.Render.BaseUIRenderImpl;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class BaseUIScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Supplier<BaseUIElement<?>> rootFactory;
    private final BaseUIScreenOptions options;
    private BaseUIElement<?> rootElement;
    private BaseUIRender render;
    private Throwable fallbackError;
    private boolean closed;

    private BaseUIScreen(Supplier<BaseUIElement<?>> rootFactory, BaseUIScreenOptions options) {
        super(Component.literal("BaseUI Screen"));
        this.rootFactory = Objects.requireNonNull(rootFactory, "rootFactory cannot be null");
        this.options = options == null ? BaseUIScreenOptions.defaults() : options;
    }

    public static void open(Supplier<BaseUIElement<?>> rootFactory) {
        open(rootFactory, BaseUIScreenOptions.defaults());
    }

    public static void open(Supplier<BaseUIElement<?>> rootFactory, BaseUIScreenOptions options) {
        Objects.requireNonNull(rootFactory, "rootFactory cannot be null");
        Minecraft minecraft = Minecraft.getInstance();
        Runnable openTask = () -> minecraft.setScreen(new BaseUIScreen(rootFactory, options));
        if (minecraft.isSameThread()) {
            openTask.run();
            return;
        }
        minecraft.execute(openTask);
    }

    @Override
    protected void init() {
        if (render != null || fallbackError != null) {
            return;
        }
        try {
            rootElement = Objects.requireNonNull(rootFactory.get(), "rootFactory produced null rootElement");
            BaseUIRender customRender = options.createRender(rootElement);
            render = customRender == null ? new BaseUIRenderImpl(rootElement) : customRender;
            render.initialize(this.width, this.height);
        } catch (RuntimeException ex) {
            enterFallback("init", ex);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (render == null || fallbackError != null) {
            return;
        }
        try {
            render.resize(width, height);
        } catch (RuntimeException ex) {
            enterFallback("resize", ex);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options.renderBackground()) {
            renderBackground(graphics);
        }

        if (fallbackError != null) {
            renderFallbackOverlay(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (render == null) {
            renderFallbackOverlay(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        try {
            BaseUIRenderContext context = new BaseUIRenderContext(
                graphics,
                mouseX,
                mouseY,
                partialTick,
                this.width,
                this.height,
                options.debugEnabled());
            render.renderFrame(context);
        } catch (RuntimeException ex) {
            enterFallback("render", ex);
            renderFallbackOverlay(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (render != null && fallbackError == null) {
            render.forwardMouseMoved(nullContext(), mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (render != null && fallbackError == null) {
            if (render.forwardMouseClicked(nullContext(), mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (render != null && fallbackError == null) {
            if (render.forwardMouseReleased(nullContext(), mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (render != null && fallbackError == null) {
            if (render.forwardMouseDragged(nullContext(), mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (render != null && fallbackError == null) {
            if (render.forwardMouseScrolled(nullContext(), mouseX, mouseY, scrollDelta)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (render != null && fallbackError == null) {
            if (render.forwardKeyPressed(nullContext(), keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (render != null && fallbackError == null) {
            if (render.forwardKeyReleased(nullContext(), keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (render != null && fallbackError == null) {
            if (render.forwardCharTyped(nullContext(), codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return options.pauseScreen();
    }

    @Override
    public void onClose() {
        if (closed) {
            super.onClose();
            return;
        }
        closed = true;
        closeRenderIfNeeded();
        super.onClose();
    }

    @Override
    public void removed() {
        closeRenderIfNeeded();
        super.removed();
    }

    private void closeRenderIfNeeded() {
        if (render == null) {
            return;
        }
        try {
            render.onClose();
        } catch (RuntimeException ex) {
            LOGGER.error("BaseUIScreen close/dispose failed", ex);
        } finally {
            render = null;
            rootElement = null;
        }
    }

    private BaseUIRenderContext nullContext() {
        return new BaseUIRenderContext(
            null,
            0,
            0,
            0,
            this.width,
            this.height,
            options.debugEnabled());
    }

    private void enterFallback(String stage, RuntimeException ex) {
        fallbackError = ex;
        LOGGER.error(
            "BaseUIScreen fallback triggered at stage={} width={} height={} rootElementInitialized={} renderInitialized={}",
            stage,
            this.width,
            this.height,
            rootElement != null,
            render != null,
            ex);
    }

    private void renderFallbackOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xAA1E1E1E);
        graphics.drawCenteredString(this.font, Component.literal("BaseUI Screen Error"), this.width / 2, this.height / 2 - 20, 0xFFFF8080);
        graphics.drawCenteredString(this.font, Component.literal("Press ESC to close"), this.width / 2, this.height / 2, 0xFFE0E0E0);
        if (fallbackError != null) {
            String message = fallbackError.getClass().getSimpleName();
            if (fallbackError.getMessage() != null && !fallbackError.getMessage().isBlank()) {
                message += ": " + fallbackError.getMessage();
            }
            graphics.drawCenteredString(this.font, Component.literal(message), this.width / 2, this.height / 2 + 16, 0xFFFFFFFF);
        }
    }
}
