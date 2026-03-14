package net.burgerfarm.baseui.client.screens;

public final class BaseUIClientScreenOptions {
    private static final BaseUIClientScreenOptions DEFAULTS = new BaseUIClientScreenOptions(
            false,
            true,
            false);

    private final boolean pauseScreen;
    private final boolean renderBackground;
    private final boolean debugEnabled;

    private BaseUIClientScreenOptions(
            boolean pauseScreen,
            boolean renderBackground,
            boolean debugEnabled) {
        this.pauseScreen = pauseScreen;
        this.renderBackground = renderBackground;
        this.debugEnabled = debugEnabled;
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

    public BaseUIClientScreenOptions withPauseScreen(boolean nextPauseScreen) {
        return new BaseUIClientScreenOptions(nextPauseScreen, renderBackground, debugEnabled);
    }

    public BaseUIClientScreenOptions withRenderBackground(boolean nextRenderBackground) {
        return new BaseUIClientScreenOptions(pauseScreen, nextRenderBackground, debugEnabled);
    }

    public BaseUIClientScreenOptions withDebugEnabled(boolean nextDebugEnabled) {
        return new BaseUIClientScreenOptions(pauseScreen, renderBackground, nextDebugEnabled);
    }
}