package com.xtjm.fusionstatusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A local preview using the same drawing as the injected edge-back view. */
public final class BackGesturePreviewView extends View {
    private final BackGestureRenderer renderer = new BackGestureRenderer();
    private DesktopConfig config = DesktopConfig.defaults();
    private ValueAnimator animation;
    private float progress = .8f;
    private boolean visible;

    public BackGesturePreviewView(Context context) {
        super(context);
        setContentDescription("侧滑返回动画预览");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setConfig(DesktopConfig value) {
        if (value != null && !value.equals(config)) {
            config = value;
            invalidate();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        invalidate();
    }

    private void updateAnimation() {
        if (!isAttachedToWindow() || !visible || !ValueAnimator.areAnimatorsEnabled()) {
            stopAnimation();
            progress = .8f;
            invalidate();
            return;
        }
        if (animation != null) return;
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(2100L);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(value -> {
            float phase = (float) value.getAnimatedValue();
            progress = phase < .3f ? phase / .3f
                    : phase < .62f ? 1f : phase < .82f ? (1f - (phase - .62f) / .2f) : 0f;
            invalidate();
        });
        animation.start();
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.cancel();
            animation.removeAllUpdateListeners();
            animation = null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int style = config.backGestureStyle == DesktopConfig.BACK_STYLE_NATIVE
                ? DesktopConfig.BACK_STYLE_CLASSIC : config.backGestureStyle;
        float alpha = Math.max(0f, Math.min(1f, (progress - .48f) / .12f));
        float depth = 42f * density * progress;
        float height = Math.min(getHeight() * .9f, 138f * density);
        boolean night = BackGesturePalette.isNight(getResources());
        renderer.drawThemed(canvas, style, config.backGestureBackgroundColor,
                config.backGestureArrowColor, false, 0, getHeight() / 2f, depth, height,
                progress, alpha, density, night);
        renderer.drawThemed(canvas, style, config.backGestureBackgroundColor,
                config.backGestureArrowColor, true, getWidth(), getHeight() / 2f, depth, height,
                progress, alpha, density, night);
    }
}
