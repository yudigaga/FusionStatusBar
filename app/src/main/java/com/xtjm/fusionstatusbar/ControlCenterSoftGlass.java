package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Static reflection above the native blurred surface, independent of the touch target. */
final class ControlCenterSoftGlass {
    private static final Map<View, WeakReference<State>> STATES = new WeakHashMap<>();

    private ControlCenterSoftGlass() { }

    static void apply(View view, boolean enabled, int strength) {
        apply(view, ControlCenterSurface.SOFT_GLASS, enabled, strength);
    }

    static void apply(View view, ControlCenterSurface surface, boolean enabled, int strength) {
        if (view == null) return;
        State state = current(view);
        if (!enabled || strength <= 0) {
            if (state != null) {
                view.removeOnLayoutChangeListener(state);
                view.getOverlay().remove(state.drawable);
            }
            STATES.remove(view);
            return;
        }
        if (state == null) {
            state = new State(view);
            STATES.put(view, new WeakReference<>(state));
            view.addOnLayoutChangeListener(state);
            view.getOverlay().add(state.drawable);
        }
        state.drawable.configure(view, strength, surface.resolve(view.getContext()) == ControlCenterSurface.HIGHLIGHT_GLASS);
    }

    private static State current(View view) {
        WeakReference<State> reference = STATES.get(view);
        return reference == null ? null : reference.get();
    }

    private static final class State implements View.OnLayoutChangeListener {
        final GlassDrawable drawable;

        State(View view) { drawable = new GlassDrawable(view); }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            drawable.updateBounds(view);
        }
    }

    private static final class GlassDrawable extends Drawable {
        private final WeakReference<View> owner;
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ControlCenterSurfaceShape shape = new ControlCenterSurfaceShape();
        private int width = -1, height = -1, strength = -1;
        private int shaderStrength = -1;
        private float shaderDensity = Float.NaN;
        private boolean daylight, shaderDaylight;

        GlassDrawable(View view) {
            owner = new WeakReference<>(view);
            rim.setStyle(Paint.Style.STROKE);
            inner.setStyle(Paint.Style.STROKE);
        }

        void configure(View view, int value, boolean daylight) {
            strength = Math.max(0, Math.min(100, value));
            this.daylight = daylight;
            updateBounds(view);
        }

        void updateBounds(View view) {
            int nextWidth = view.getWidth(), nextHeight = view.getHeight();
            float nextDensity = view.getResources().getDisplayMetrics().density;
            if (nextWidth == width && nextHeight == height && strength == shaderStrength
                    && daylight == shaderDaylight
                    && Float.compare(nextDensity, shaderDensity) == 0
                    && (nextWidth <= 0 || nextHeight <= 0 || rim.getShader() != null)) return;
            width = nextWidth;
            height = nextHeight;
            shaderStrength = strength;
            shaderDensity = nextDensity;
            shaderDaylight = daylight;
            setBounds(0, 0, nextWidth, nextHeight);
            updateShaders(view);
            invalidateSelf();
        }

        private void updateShaders(View view) {
            if (width <= 0 || height <= 0) return;
            float density = view.getResources().getDisplayMetrics().density;
            float factor = strength / 100f;
            rim.setStrokeWidth(Math.max(1f, density * 1.2f));
            rim.setShader(new LinearGradient(0, 0, width * 0.18f, height,
                    daylight
                            ? new int[] {alpha(0xff, factor), alpha(0x24, factor), alpha(0xb0, factor)}
                            : new int[] {alpha(0xf0, factor), alpha(0x50, factor), alpha(0x82, factor)},
                    new float[] {0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
            inner.setStrokeWidth(Math.max(0.5f, density * 0.7f));
            // A restrained dark edge keeps the glass silhouette visible over a bright wallpaper.
            inner.setColor(daylight ? (Math.round(0x38 * factor) << 24) | 0x00384250 : alpha(0x3a, factor));
        }

        private int alpha(int value, float factor) {
            return (Math.round(value * factor) << 24) | 0x00ffffff;
        }

        @Override public void draw(Canvas canvas) {
            View view = owner.get();
            if (view == null || width <= 0 || height <= 0) return;
            if (!shape.resolve(view, rim.getStrokeWidth() / 2f)) return;
            shape.draw(canvas, rim);
            shape.draw(canvas, inner);
        }

        @Override public void setAlpha(int alpha) {
            rim.setAlpha(alpha); inner.setAlpha(alpha); invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            rim.setColorFilter(filter); inner.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
