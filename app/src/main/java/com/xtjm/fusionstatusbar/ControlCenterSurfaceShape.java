package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Resolves only confirmed surface geometry for both borders and glass reflections. */
final class ControlCenterSurfaceShape {
    private final Path path = new Path();
    private final Path strokePath = new Path();
    private final Paint strokePaint = new Paint();
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private boolean clipStroke;

    boolean resolve(View view, float inset) {
        path.reset();
        clipStroke = false;
        if (view.getWidth() <= inset * 2 || view.getHeight() <= inset * 2) return false;
        ViewOutlineProvider provider = view.getOutlineProvider();
        // Default providers describe layout bounds even when no material is present.
        if (provider != null && provider != ViewOutlineProvider.BACKGROUND
                && provider != ViewOutlineProvider.BOUNDS
                && provider != ViewOutlineProvider.PADDED_BOUNDS) {
            outline.setEmpty();
            try { provider.getOutline(view, outline); }
            catch (RuntimeException | LinkageError ignored) { outline.setEmpty(); }
            if (readOutline(inset, true)) return true;
        }
        return readDrawable(view.getBackground(), inset, 0);
    }

    void draw(Canvas canvas, Paint paint) {
        draw(canvas, paint, 0f);
    }

    void roundRect(float width, float height, float radius) {
        path.rewind();
        rect.set(0, 0, width, height);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        clipStroke = false;
    }

    /** Draw a band inward from the same exact contour, without re-resolving OEM geometry. */
    void drawInside(Canvas canvas, Paint paint, float offset) {
        boolean previous = clipStroke;
        clipStroke = true;
        try { draw(canvas, paint, offset); }
        finally { clipStroke = previous; }
    }

    /** Native paths retain their exact contour; offset their stroke with a clipped edge band. */
    void draw(Canvas canvas, Paint paint, float innerOffset) {
        if (path.isEmpty()) return;
        if (!clipStroke) {
            drawPath(canvas, paint);
            return;
        }
        int saved = canvas.save();
        float width = paint.getStrokeWidth();
        try {
            // Preserve the exact OEM path and keep the entire stroke inside its surface.
            canvas.clipPath(path);
            float offset = Math.max(0f, innerOffset);
            if (offset > 0f && paint.getStyle() == Paint.Style.STROKE) {
                strokePaint.set(paint);
                strokePaint.setStrokeWidth(offset * 2f);
                strokePath.rewind();
                if (!strokePaint.getFillPath(path, strokePath)) return;
                canvas.clipOutPath(strokePath);
            }
            paint.setStrokeWidth((width + offset) * 2f);
            drawPath(canvas, paint);
        } finally {
            paint.setStrokeWidth(width);
            canvas.restoreToCount(saved);
        }
    }

    private void drawPath(Canvas canvas, Paint paint) {
        if (paint.getStyle() != Paint.Style.STROKE) {
            canvas.drawPath(path, paint);
            return;
        }
        // Keep the center geometrically empty even if an OEM smooth-corner
        // renderer rewrites round-rect stroke operations as filled shapes.
        strokePaint.set(paint);
        strokePath.rewind();
        if (!strokePaint.getFillPath(path, strokePath)) return;
        strokePaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(strokePath, strokePaint);
    }

    private boolean readOutline(float inset, boolean explicitProvider) {
        if (outline.isEmpty()) return false;
        if (outline.getRect(outlineRect)) {
            // Drawable's default outline is rectangular even when its drawing is not.
            if (!explicitProvider && outline.getRadius() <= 0f) return false;
            rect.set(outlineRect.left + inset, outlineRect.top + inset,
                    outlineRect.right - inset, outlineRect.bottom - inset);
            if (rect.width() <= 0 || rect.height() <= 0) return false;
            float radius = Math.max(0f, outline.getRadius() - inset);
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            return true;
        }
        return copyNativePath(outline, "mPath");
    }

    private boolean readDrawable(Drawable drawable, float inset, int depth) {
        if (drawable == null || depth > 8) return false;
        if (drawable instanceof GradientDrawable gradient) {
            Rect bounds = drawable.getBounds();
            rect.set(bounds.left + inset, bounds.top + inset,
                    bounds.right - inset, bounds.bottom - inset);
            if (rect.width() <= 0 || rect.height() <= 0) return false;
            if (gradient.getShape() == GradientDrawable.OVAL) {
                path.addOval(rect, Path.Direction.CW);
                return true;
            }
            if (gradient.getShape() != GradientDrawable.RECTANGLE) return false;
            float[] corners = gradient.getCornerRadii();
            if (corners != null && corners.length == 8) {
                for (int i = 0; i < radii.length; i++)
                    radii[i] = Math.max(0f, corners[i] - inset);
                path.addRoundRect(rect, radii, Path.Direction.CW);
                return true;
            }
            float radius = Math.max(0f, gradient.getCornerRadius() - inset);
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            return true;
        }
        if (drawable instanceof DrawableWrapper wrapper
                && readDrawable(wrapper.getDrawable(), inset, depth + 1)) return true;
        Drawable current = drawable.getCurrent();
        if (current != drawable && readDrawable(current, inset, depth + 1)) return true;
        // The plugin's SmoothRoundDrawable draws this path without overriding getOutline().
        if (drawable.getClass().getSimpleName().equals("SmoothRoundDrawable")
                && copyNativePath(drawable, "path")) return true;
        outline.setEmpty();
        try { drawable.getOutline(outline); }
        catch (RuntimeException | LinkageError ignored) { outline.setEmpty(); }
        return readOutline(inset, false);
    }

    private boolean copyNativePath(Object source, String name) {
        try {
            java.lang.reflect.Field field = source.getClass().getDeclaredField(name);
            field.setAccessible(true);
            if (!(field.get(source) instanceof Path nativePath) || nativePath.isEmpty()) return false;
            path.set(nativePath);
            clipStroke = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
