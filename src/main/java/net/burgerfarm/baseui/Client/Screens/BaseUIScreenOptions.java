package net.burgerfarm.baseui.Client.Screens;

import java.util.Objects;
import java.util.function.Function;
import net.burgerfarm.baseui.Client.Render.BaseUIRender;
import net.burgerfarm.baseui.Core.BaseUIElement;

public final class BaseUIScreenOptions {
    private static final BaseUIScreenOptions DEFAULTS = new BaseUIScreenOptions(
        false,
        true,
        false,
        null);

    private final boolean pauseScreen;
    private final boolean renderBackground;
    private final boolean debugEnabled;
    private final Function<BaseUIElement<?>, BaseUIRender> renderFactory;

    private BaseUIScreenOptions(
        boolean pauseScreen,
        boolean renderBackground,
        boolean debugEnabled,
        Function<BaseUIElement<?>, BaseUIRender> renderFactory) {
        this.pauseScreen = pauseScreen;
        this.renderBackground = renderBackground;
        this.debugEnabled = debugEnabled;
        this.renderFactory = renderFactory;
    }

    public static BaseUIScreenOptions defaults() {
        return DEFAULTS;
    }

    public boolean pauseScreen() {
        return pauseScreen;
    }

    public boolean renderBackground() {
        return renderBackground;
    }

    public boolean debugEnabled() {
        return debugEnabled;
    }

    public Function<BaseUIElement<?>, BaseUIRender> renderFactory() {
        return renderFactory;
    }

    public BaseUIScreenOptions withPauseScreen(boolean nextPauseScreen) {
        return new BaseUIScreenOptions(nextPauseScreen, renderBackground, debugEnabled, renderFactory);
    }

    public BaseUIScreenOptions withRenderBackground(boolean nextRenderBackground) {
        return new BaseUIScreenOptions(pauseScreen, nextRenderBackground, debugEnabled, renderFactory);
    }

    public BaseUIScreenOptions withDebugEnabled(boolean nextDebugEnabled) {
        return new BaseUIScreenOptions(pauseScreen, renderBackground, nextDebugEnabled, renderFactory);
    }

    public BaseUIScreenOptions withRenderFactory(Function<BaseUIElement<?>, BaseUIRender> nextRenderFactory) {
        return new BaseUIScreenOptions(pauseScreen, renderBackground, debugEnabled, nextRenderFactory);
    }

    public BaseUIRender createRender(BaseUIElement<?> rootElement) {
        Objects.requireNonNull(rootElement, "rootElement cannot be null");
        if (renderFactory == null) {
            return null;
        }
        return renderFactory.apply(rootElement);
    }
}
