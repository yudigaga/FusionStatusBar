package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Draws a reversible border above a Control Center surface without replacing its material. */
final class ControlCenterGlobalBorder {
    private static final Map<View, WeakReference<Border>> BORDERS = new WeakHashMap<>();

    private ControlCenterGlobalBorder() { }

    static void apply(View view, boolean enabled, int widthDp, boolean lightSurface) {
        if (view == null) return;
        Border border = current(view);
        if (!enabled) {
            if (border != null) {
                view.removeOnLayoutChangeListener(border);
                view.getOverlay().remove(border.drawable);
            }
            BORDERS.remove(view);
            return;
        }
        if (border == null) {
            border = new Border(view);
            BORDERS.put(view, new WeakReference<>(border));
            view.addOnLayoutChangeListener(border);
            view.getOverlay().add(border.drawable);
        }
        border.drawable.configure(view, widthDp, lightSurface);
    }

    static void refresh(View view) {
        Border border = current(view);
        if (border != null) border.drawable.updateBounds(view);
    }

    private static Border current(View view) {
        WeakReference<Border> reference = BORDERS.get(view);
        return reference == null ? null : reference.get();
    }

    private static final class Border implements View.OnLayoutChangeListener {
        final BorderDrawable drawable;

        Border(View view) { drawable = new BorderDrawable(view); }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            drawable.updateBounds(view);
        }
    }

    private static final class BorderDrawable extends Drawable {
        private static final int LIGHT_STROKE = 0x66000000;
        private static final int DARK_STROKE = 0x80ffffff;
        private final WeakReference<View> owner;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ControlCenterSurfaceShape shape = new ControlCenterSurfaceShape();

        BorderDrawable(View view) {
            owner = new WeakReference<>(view);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void configure(View view, int widthDp, boolean lightSurface) {
            int clampedDp = Math.max(1, Math.min(4, widthDp));
            float width = clampedDp * view.getResources().getDisplayMetrics().density;
            paint.setStrokeWidth(Math.max(1f, width));
            paint.setColor(lightSurface ? LIGHT_STROKE : DARK_STROKE);
            updateBounds(view);
        }

        void updateBounds(View view) {
            setBounds(0, 0, view.getWidth(), view.getHeight());
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            View view = owner.get();
            if (view == null || getBounds().isEmpty()) return;
            if (shape.resolve(view, paint.getStrokeWidth() / 2f)) shape.draw(canvas, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }

        @Override public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
