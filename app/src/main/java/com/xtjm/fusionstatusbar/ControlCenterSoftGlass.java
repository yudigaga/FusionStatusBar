package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
        state.drawable.configure(view, strength, surface);
    }

    private static State current(View view) {
        WeakReference<State> reference = STATES.get(view);
        return reference == null ? null : reference.get();
    }

    /** Keep reflection above a newly added solid border without rebuilding its shader. */
    static void bringToFront(View view) {
        State state = current(view);
        if (state == null) return;
        view.getOverlay().remove(state.drawable);
        view.getOverlay().add(state.drawable);
    }

    private static final class State implements View.OnLayoutChangeListener {
        final GlassDrawable drawable;

        State(View view) { drawable = new GlassDrawable(view); }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            drawable.updateBounds(view);
        }
    }

    static final class GlassDrawable extends Drawable {
        private static final float[] BAND_WIDTHS = {1f, .67f, .40f, .22f};
        private static final float[] BAND_OPACITY = {.08f, .10f, .14f, .16f};
        private final WeakReference<View> owner;
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ControlCenterSurfaceShape shape = new ControlCenterSurfaceShape();
        private int width = -1, height = -1, strength = -1;
        private int shaderStrength = -1;
        private float shaderDensity = Float.NaN;
        private boolean highlight, daylight, shaderHighlight, shaderDaylight;
        private int drawableAlpha = 255;
        private float edgeWidth;

        GlassDrawable(View view) {
            owner = new WeakReference<>(view);
            rim.setStyle(Paint.Style.STROKE);
            inner.setStyle(Paint.Style.STROKE);
        }

        void configure(View view, int value, ControlCenterSurface surface) {
            configure(view, value, surface, true);
        }

        void configureBackground(View view, int value, ControlCenterSurface surface) {
            configure(view, value, surface, false);
        }

        private void configure(View view, int value, ControlCenterSurface surface, boolean viewBounds) {
            strength = Math.max(0, Math.min(100, value));
            highlight = surface == ControlCenterSurface.HIGHLIGHT_GLASS;
            daylight = highlight && surface.resolve(view.getContext()) == ControlCenterSurface.HIGHLIGHT_GLASS;
            if (viewBounds) updateBounds(view);
            else updateSize(view, Math.max(0, width), Math.max(0, height));
        }

        void updateBounds(View view) {
            updateSize(view, view.getWidth(), view.getHeight());
        }

        private void updateSize(View view, int nextWidth, int nextHeight) {
            float nextDensity = view.getResources().getDisplayMetrics().density;
            if (nextWidth == width && nextHeight == height && strength == shaderStrength
                    && daylight == shaderDaylight && highlight == shaderHighlight
                    && Float.compare(nextDensity, shaderDensity) == 0
                    && (nextWidth <= 0 || nextHeight <= 0 || rim.getShader() != null)) return;
            width = nextWidth;
            height = nextHeight;
            shaderStrength = strength;
            shaderDensity = nextDensity;
            shaderDaylight = daylight;
            shaderHighlight = highlight;
            setBounds(0, 0, nextWidth, nextHeight);
            updateShaders(view);
            invalidateSelf();
        }

        private void updateShaders(View view) {
            if (width <= 0 || height <= 0) return;
            float density = view.getResources().getDisplayMetrics().density;
            float factor = strength / 100f;
            if (highlight) {
                // Several low-opacity bands create a continuous falloff, rather than a
                // bright hairline paired with a second dark outline. The middle of the
                // directional reflection fades out to avoid a uniformly lit closed ring.
                edgeWidth = Math.min(density * 6f, Math.min(width, height) * .14f);
                rim.setStrokeWidth(edgeWidth);
                rim.setShader(new LinearGradient(0, 0, width * .35f, height,
                        daylight
                                ? new int[] {alpha(0xb0, factor), alpha(0x28, factor), alpha(0x00, factor), alpha(0x70, factor)}
                                : new int[] {alpha(0xb0, factor), alpha(0x38, factor), alpha(0x00, factor), alpha(0x70, factor)},
                        new float[] {0f, .28f, .62f, 1f}, Shader.TileMode.CLAMP));
                inner.setStrokeWidth(edgeWidth * .75f);
                inner.setShader(new LinearGradient(0, 0, width, height,
                        new int[] {0x00131a20, (Math.round((daylight ? 0x24 : 0x18) * factor) << 24) | 0x00131a20,
                                0x00131a20}, new float[] {0f, .58f, 1f}, Shader.TileMode.CLAMP));
                return;
            }
            rim.setStrokeWidth(Math.max(1f, density * 1.2f));
            rim.setShader(new LinearGradient(0, 0, width * 0.18f, height,
                    daylight
                            ? new int[] {alpha(0xd8, factor), alpha(0x20, factor), alpha(0x90, factor)}
                            : new int[] {alpha(0xf0, factor), alpha(0x50, factor), alpha(0x82, factor)},
                    new float[] {0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
            inner.setStrokeWidth(Math.max(0.5f, density * 0.7f));
            inner.setShader(null);
            // Separate the inner shade from the bright rim to give the edge a shallow bevel.
            inner.setColor(daylight ? (Math.round(0x48 * factor) << 24) | 0x00222529 : alpha(0x3a, factor));
        }

        private int alpha(int value, float factor) {
            return (Math.round(value * factor) << 24) | 0x00ffffff;
        }

        @Override public void draw(Canvas canvas) {
            View view = owner.get();
            if (view == null || width <= 0 || height <= 0) return;
            if (highlight) {
                if (shape.resolve(view, 0f)) drawSoftEdge(canvas);
                return;
            }
            if (!shape.resolve(view, rim.getStrokeWidth() / 2f)) return;
            shape.draw(canvas, rim);
            if (daylight && !shape.resolve(view,
                    rim.getStrokeWidth() + inner.getStrokeWidth() / 2f)) return;
            shape.draw(canvas, inner, daylight ? rim.getStrokeWidth() : 0f);
        }

        /** Background-owned rendering inherits notification bounds, clipping and native fade. */
        void drawRoundRect(Canvas canvas, Rect bounds, float radius) {
            View view = owner.get();
            if (view == null || bounds.isEmpty() || strength <= 0) return;
            updateSize(view, bounds.width(), bounds.height());
            shape.roundRect(width, height, radius);
            int saved = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            try { drawSoftEdge(canvas); }
            finally { canvas.restoreToCount(saved); }
        }

        private void drawSoftEdge(Canvas canvas) {
            if (drawableAlpha <= 0 || strength <= 0) return;
            inner.setAlpha(Math.round(drawableAlpha * .38f));
            shape.drawInside(canvas, inner, edgeWidth * .18f);
            try {
                for (int band = 0; band < BAND_WIDTHS.length; band++) {
                    rim.setStrokeWidth(edgeWidth * BAND_WIDTHS[band]);
                    rim.setAlpha(Math.round(drawableAlpha * BAND_OPACITY[band]));
                    shape.drawInside(canvas, rim, 0f);
                }
            } finally {
                rim.setStrokeWidth(edgeWidth);
                rim.setAlpha(drawableAlpha);
                inner.setAlpha(drawableAlpha);
            }
        }

        @Override public void setAlpha(int alpha) {
            if (drawableAlpha == alpha) return;
            drawableAlpha = alpha;
            rim.setAlpha(alpha); inner.setAlpha(alpha); invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            if (rim.getColorFilter() == filter && inner.getColorFilter() == filter) return;
            rim.setColorFilter(filter); inner.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
