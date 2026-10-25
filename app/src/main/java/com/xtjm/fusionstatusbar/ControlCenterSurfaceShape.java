package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import java.lang.reflect.Field;

/** Resolves only confirmed surface geometry for both borders and glass reflections. */
final class ControlCenterSurfaceShape {
    private static final int NONE = 0, ROUND_RECT = 1, CORNER_RADII = 2, OVAL = 3, NATIVE_PATH = 4;
    private static final int STROKE_CACHE_SIZE = 8;
    private final Path path = new Path();
    private final Paint strokePaint = new Paint();
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final float[] geometryRadii = new float[8];
    private final StrokeGeometry[] strokes = new StrokeGeometry[STROKE_CACHE_SIZE];
    private final Class<?>[] pathFieldOwners = new Class<?>[2];
    private final Field[] pathFields = new Field[2];
    private int geometryKind, geometryVersion, nextStroke;
    private float geometryLeft, geometryTop, geometryRight, geometryBottom, geometryRadius;
    private Path nativeSource;
    private int nativeGeneration;
    private Path.FillType nativeFillType;
    private boolean clipStroke;

    boolean resolve(View view, float inset) {
        clipStroke = false;
        if (view.getWidth() <= inset * 2 || view.getHeight() <= inset * 2) return clearGeometry();
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
        if (readDrawable(view.getBackground(), inset, 0)) return true;
        return clearGeometry();
    }

    void draw(Canvas canvas, Paint paint) {
        draw(canvas, paint, 0f);
    }

    void roundRect(float width, float height, float radius) {
        rect.set(0, 0, width, height);
        clipStroke = false;
        if (width <= 0 || height <= 0) clearGeometry();
        else setRoundRect(radius, null);
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
                StrokeGeometry excluded = filledStroke();
                if (!excluded.filled) return;
                canvas.clipOutPath(excluded.path);
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
        StrokeGeometry stroke = filledStroke();
        if (!stroke.filled) return;
        strokePaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(stroke.path, strokePaint);
    }

    /** Colors and shaders affect painting, not the stroke-to-fill geometry. */
    private StrokeGeometry filledStroke() {
        float width = strokePaint.getStrokeWidth(), miter = strokePaint.getStrokeMiter();
        Paint.Cap cap = strokePaint.getStrokeCap();
        Paint.Join join = strokePaint.getStrokeJoin();
        PathEffect effect = strokePaint.getPathEffect();
        for (StrokeGeometry entry : strokes) {
            if (entry != null && entry.version == geometryVersion
                    && Float.compare(entry.width, width) == 0 && Float.compare(entry.miter, miter) == 0
                    && entry.cap == cap && entry.join == join && entry.effect == effect) return entry;
        }
        int slot = nextStroke;
        nextStroke = (nextStroke + 1) % STROKE_CACHE_SIZE;
        StrokeGeometry entry = strokes[slot];
        if (entry == null) strokes[slot] = entry = new StrokeGeometry();
        entry.path.rewind();
        entry.filled = strokePaint.getFillPath(path, entry.path);
        entry.version = geometryVersion;
        entry.width = width;
        entry.miter = miter;
        entry.cap = cap;
        entry.join = join;
        entry.effect = effect;
        return entry;
    }

    private boolean sameRect(int kind) {
        return geometryKind == kind && Float.compare(geometryLeft, rect.left) == 0
                && Float.compare(geometryTop, rect.top) == 0
                && Float.compare(geometryRight, rect.right) == 0
                && Float.compare(geometryBottom, rect.bottom) == 0;
    }

    private void setRoundRect(float radius, float[] corners) {
        int kind = corners == null ? ROUND_RECT : CORNER_RADII;
        boolean unchanged = sameRect(kind);
        if (corners == null) unchanged &= Float.compare(geometryRadius, radius) == 0;
        else for (int i = 0; unchanged && i < geometryRadii.length; i++)
            unchanged = Float.compare(geometryRadii[i], corners[i]) == 0;
        if (unchanged) return;
        rememberGeometry(kind);
        geometryRadius = radius;
        if (corners == null) path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        else {
            System.arraycopy(corners, 0, geometryRadii, 0, geometryRadii.length);
            path.addRoundRect(rect, geometryRadii, Path.Direction.CW);
        }
    }

    private void setOval() {
        if (sameRect(OVAL)) return;
        rememberGeometry(OVAL);
        path.addOval(rect, Path.Direction.CW);
    }

    private void rememberGeometry(int kind) {
        geometryKind = kind;
        geometryLeft = rect.left;
        geometryTop = rect.top;
        geometryRight = rect.right;
        geometryBottom = rect.bottom;
        nativeSource = null;
        nativeFillType = null;
        path.rewind();
        path.setFillType(Path.FillType.WINDING);
        invalidateStrokes();
    }

    private void invalidateStrokes() {
        geometryVersion++;
        nextStroke = 0;
        for (StrokeGeometry stroke : strokes) if (stroke != null) stroke.version = -1;
    }

    private boolean clearGeometry() {
        if (geometryKind != NONE || !path.isEmpty()) {
            geometryKind = NONE;
            nativeSource = null;
            nativeFillType = null;
            path.rewind();
            invalidateStrokes();
        }
        clipStroke = false;
        return false;
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
            setRoundRect(radius, null);
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
                setOval();
                return true;
            }
            if (gradient.getShape() != GradientDrawable.RECTANGLE) return false;
            float[] corners = gradient.getCornerRadii();
            if (corners != null && corners.length == 8) {
                for (int i = 0; i < radii.length; i++)
                    radii[i] = Math.max(0f, corners[i] - inset);
                setRoundRect(0f, radii);
                return true;
            }
            float radius = Math.max(0f, gradient.getCornerRadius() - inset);
            setRoundRect(radius, null);
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
            int slot = name.equals("mPath") ? 0 : 1;
            Class<?> type = source.getClass();
            if (pathFieldOwners[slot] != type) {
                pathFieldOwners[slot] = type;
                pathFields[slot] = null;
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    pathFields[slot] = field;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            Field field = pathFields[slot];
            if (field == null) return false;
            if (!(field.get(source) instanceof Path nativePath) || nativePath.isEmpty()) return false;
            Path.FillType fillType = nativePath.getFillType();
            // API 33 has no public generation identifier. Rebuild unknown OEM paths
            // there so an in-place contour mutation cannot leave a stale outline.
            int generation = Build.VERSION.SDK_INT >= 34 ? nativePath.getGenerationId() : 0;
            if (Build.VERSION.SDK_INT >= 34 && geometryKind == NATIVE_PATH
                    && nativeSource == nativePath && nativeGeneration == generation
                    && nativeFillType == fillType) {
                clipStroke = true;
                return true;
            }
            path.set(nativePath);
            geometryKind = NATIVE_PATH;
            nativeSource = nativePath;
            nativeGeneration = generation;
            nativeFillType = fillType;
            invalidateStrokes();
            clipStroke = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static final class StrokeGeometry {
        final Path path = new Path();
        int version = -1;
        float width, miter;
        Paint.Cap cap;
        Paint.Join join;
        PathEffect effect;
        boolean filled;
    }
}
