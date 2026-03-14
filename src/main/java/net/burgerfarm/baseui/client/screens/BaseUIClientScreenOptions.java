package net.burgerfarm.baseui.client.screens;

import java.util.Objects;
import java.util.function.Function;
import net.burgerfarm.baseui.client.bridge.BaseUIRenderBridge;
import net.burgerfarm.baseui.client.core.BaseUIElement;

public final class BaseUIClientScreenOptions {
    private static final BaseUIClientScreenOptions DEFAULTS = new BaseUIClientScreenOptions(
        false,
        true,
        false,
        null);

    private final boolean pauseScreen;
    private final boolean renderBackground;
    private final boolean debugEnabled;
    private final Function<BaseUIElement<?>, BaseUIRenderBridge> renderFactory;

    private BaseUIClientScreenOptions(
        boolean pauseScreen,
        boolean renderBackground,
        boolean debugEnabled,
        Function<BaseUIElement<?>, BaseUIRenderBridge> renderFactory) {
        this.pauseScreen = pauseScreen;
        this.renderBackground = renderBackground;
        this.debugEnabled = debugEnabled;
        this.renderFactory = renderFactory;
    }

    public static BaseUIClientScreenOptions defaults() {
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

    public Function<BaseUIElement<?>, BaseUIRenderBridge> renderFactory() {
        return renderFactory;
    }

    public BaseUIClientScreenOptions withPauseScreen(boolean nextPauseScreen) {
        return new BaseUIClientScreenOptions(nextPauseScreen, renderBackground, debugEnabled, renderFactory);
    }

    public BaseUIClientScreenOptions withRenderBackground(boolean nextRenderBackground) {
        return new BaseUIClientScreenOptions(pauseScreen, nextRenderBackground, debugEnabled, renderFactory);
    }

    public BaseUIClientScreenOptions withDebugEnabled(boolean nextDebugEnabled) {
        return new BaseUIClientScreenOptions(pauseScreen, renderBackground, nextDebugEnabled, renderFactory);
    }

    public BaseUIClientScreenOptions withRenderFactory(Function<BaseUIElement<?>, BaseUIRenderBridge> nextRenderFactory) {
        return new BaseUIClientScreenOptions(pauseScreen, renderBackground, debugEnabled, nextRenderFactory);
    }

    public BaseUIRenderBridge createRender(BaseUIElement<?> rootElement) {
        Objects.requireNonNull(rootElement, "rootElement cannot be null");
        if (renderFactory == null) {
            return null;
        }
        return renderFactory.apply(rootElement);
    }
}
