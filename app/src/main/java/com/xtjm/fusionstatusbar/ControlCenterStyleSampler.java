package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;

/** Reads bounded native visual metadata. Never serializes a foreign View or changes tile state. */
final class ControlCenterStyleSampler {
    static final int ICON_SIZE = 128;
    private ControlCenterStyleSampler() { }

    static ControlCenterCapturedStyle read(View view, String spec) {
        boolean small = view.getClass().getSimpleName().contains("QSTileItemView");
        ControlCenterLayoutPlan.Shape shape = small ? ControlCenterLayoutPlan.Shape.CIRCLE : ControlCenterLayoutPlan.Shape.RECTANGLE;
        int radius = shape == ControlCenterLayoutPlan.Shape.CIRCLE ? 0 : 24;
        try {
            Outline outline = new Outline();
            View surface = surfaceView(view);
            if (surface.getOutlineProvider() != null) surface.getOutlineProvider().getOutline(surface, outline);
            if (outline.getRadius() > 0 && Float.isFinite(outline.getRadius())) {
                radius = Math.round(outline.getRadius() / view.getResources().getDisplayMetrics().density);
            }
        } catch (RuntimeException ignored) { }
        Object state = field(view, "state");
        if (state == null) state = field(view, "customizeState");
        Object active = field(state, "state");
        int stateCode = active instanceof Number ? ((Number) active).intValue() : -1;
        ArrayList<String> labels = new ArrayList<>();
        collectLabels(view, labels, 0);
        String label = labels.isEmpty() ? string(field(state, "label")) : labels.get(0);
        String subtitle = labels.size() < 2 ? string(field(state, "secondaryLabel")) : labels.get(1);
        float level = progress(view, 0);
        ImageView image = primaryIcon(view);
        Bitmap icon = null;
        if (!ControlCenterComponentSpec.isSpecial(spec) && image != null && image.getDrawable() != null) {
            icon = snapshotIcon(image.getDrawable());
        }
        return new ControlCenterCapturedStyle(shape, radius, stateCode, label, subtitle, level, icon);
    }

    /** A tile's clickable wrapper includes OEM margins; its ImageView owns the visible surface. */
    static View surfaceView(View view) {
        if (view.getClass().getSimpleName().contains("QSTileItemView")) {
            ImageView image = primaryIcon(view);
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0
                    && image.getWidth() >= view.getWidth() * 0.5f
                    && image.getWidth() <= view.getWidth() && image.getHeight() <= view.getHeight()) return image;
        }
        return view;
    }

    private static ImageView primaryIcon(View view) {
        Object icon = field(view, "icon");
        ImageView image = icon instanceof View ? findIcon((View) icon, 0) : null;
        return image == null ? findIcon(view, 0) : image;
    }

    private static Bitmap snapshotIcon(Drawable source) {
        Drawable glyph = foreground(source);
        if (glyph == null) return null;
        Rect bounds = new Rect(glyph.getBounds());
        if (bounds.isEmpty()) return null;
        Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        try {
            // Fixed LayerDrawable child sizes are physical pixels. Resizing the parent clips
            // them instead of scaling them. Transform the canvas, never the live Drawable.
            float scale = ICON_SIZE / (float) Math.max(bounds.width(), bounds.height());
            Canvas canvas = new Canvas(bitmap);
            canvas.translate((ICON_SIZE - bounds.width() * scale) / 2f,
                    (ICON_SIZE - bounds.height() * scale) / 2f);
            canvas.scale(scale, scale);
            canvas.translate(-bounds.left, -bounds.top);
            glyph.draw(canvas);
            for (int y = 0; y < ICON_SIZE; y++) for (int x = 0; x < ICON_SIZE; x++) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) return bitmap;
            }
        } catch (RuntimeException ignored) { }
        bitmap.recycle();
        return null;
    }

    private static Drawable foreground(Drawable source) {
        Drawable current = source;
        for (int depth = 0; depth < 16 && current != null; depth++) {
            if (current instanceof android.graphics.drawable.DrawableWrapper wrapper) {
                current = wrapper.getDrawable();
                continue;
            }
            Drawable selectedState = current.getCurrent();
            if (selectedState != null && selectedState != current) {
                current = selectedState;
                continue;
            }
            if (!(current instanceof LayerDrawable layers)) return current;
            int selected = -1;
            // HyperOS places a fixed-size glyph above one or two state background layers.
            for (int i = layers.getNumberOfLayers() - 1; i >= 0; i--) {
                if (layers.getLayerWidth(i) > 0 && layers.getLayerHeight(i) > 0) { selected = i; break; }
            }
            if (selected < 0 && layers.getNumberOfLayers() == 1) selected = 0;
            if (selected < 0) return null; // Unknown multi-layer artwork must not import its background.
            current = layers.getDrawable(selected);
        }
        return null;
    }

    private static Object field(Object object, String name) {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static String string(Object value) { return value instanceof CharSequence ? value.toString() : ""; }

    private static void collectLabels(View view, ArrayList<String> labels, int depth) {
        if (depth > 8 || view.getVisibility() != View.VISIBLE || labels.size() >= 2) return;
        if (view instanceof TextView text && text.getText().length() > 0) {
            String label = text.getText().toString();
            labels.add(label.substring(0, Math.min(160, label.length())));
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            collectLabels(group.getChildAt(i), labels, depth + 1);
        }
    }

    private static ImageView findIcon(View view, int depth) {
        if (depth > 8 || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof ImageView image && image.getDrawable() != null) return image;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ImageView found = findIcon(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static float progress(View view, int depth) {
        if (depth > 8) return -1;
        if (view instanceof ProgressBar bar && bar.getMax() > bar.getMin()) {
            return (bar.getProgress() - bar.getMin()) / (float) (bar.getMax() - bar.getMin());
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            float value = progress(group.getChildAt(i), depth + 1);
            if (value >= 0) return value;
        }
        return -1;
    }
}
