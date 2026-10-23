package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterSoftGlassTest {
    @After public void cleanup() { ControlCenterMaterials.clearForTest(); }

    @Test public void reflectionDoesNotInventASurfaceForNativeLayoutHolders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View holder = new View(activity);
        holder.layout(0, 0, 100, 60);
        ControlCenterSoftGlass.apply(holder, true, 100);
        Bitmap image = draw(holder);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 30));
        image.recycle();
    }

    @Test public void reflectionUsesTheWrappedOvalSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xff353840);
        surface.setBackground(new InsetDrawable(oval, 10));
        surface.layout(0, 0, 120, 80);
        ControlCenterSoftGlass.apply(surface, true, 100);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(60, 1));
        assertNotEquals(0xff353840, image.getPixel(60, 10));
        image.recycle();
    }

    @Test public void runtimeGlassHighlightsOnlyOuterSurfaceAndRestoresOnRefresh() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24f);
        card.setBackground(background);
        QSTileItemIconView icon = new QSTileItemIconView(activity);
        icon.setBackgroundColor(Color.BLUE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(30, 30);
        iconParams.leftMargin = 22;
        iconParams.topMargin = 50;
        card.addView(icon, iconParams);
        layout(card, 160, 100);

        ControlCenterConfig glass = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS);
        ControlCenterMaterials.registerRoot(card, glass);
        Bitmap withGlass = draw(card);
        assertTrue("Glass rim pixels: " + Integer.toHexString(withGlass.getPixel(80, 1))
                        + " vs " + Integer.toHexString(withGlass.getPixel(80, 8)),
                Color.luminance(withGlass.getPixel(80, 1))
                        > Color.luminance(withGlass.getPixel(80, 8)) + 0.08f);
        assertEquals("Inner icon remains unobstructed below the reflection", Color.BLUE,
                withGlass.getPixel(35, 70));
        withGlass.recycle();

        ControlCenterMaterials.refresh(glass.withSurface(ControlCenterSurface.DEFAULT));
        Bitmap restored = draw(card);
        assertEquals(0xff353840, restored.getPixel(80, 1));
        assertEquals(Color.BLUE, restored.getPixel(35, 70));
        restored.recycle();
    }

    @Test public void repeatedApplyOnUnchangedMeasuredSurfaceRetainsRimShader() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, true, 72);
        Drawable glass = glassDrawable(surface);
        Paint rim = rimPaint(glass);
        Shader initialShader = rim.getShader();
        assertNotNull(initialShader);
        try {
            for (int update = 0; update < 8; update++) {
                ControlCenterSoftGlass.apply(surface, true, 72);
            }
            assertSame("Repeated material updates with unchanged size, strength and density"
                    + " must retain the existing gradient", initialShader, rim.getShader());
        } finally {
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void repeatedApplyOnUnchangedMeasuredSurfaceDoesNotInvalidate() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, true, 72);
        Drawable glass = glassDrawable(surface);
        Drawable.Callback originalCallback = glass.getCallback();
        Invalidations callback = new Invalidations();
        glass.setCallback(callback);
        try {
            for (int update = 0; update < 8; update++) {
                ControlCenterSoftGlass.apply(surface, true, 72);
            }
            assertEquals("Unchanged material updates must not request eight extra redraws",
                    0, callback.count);
        } finally {
            glass.setCallback(originalCallback);
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void changingSurfaceSizeOrStrengthUpdatesShaderAndInvalidates() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, true, 72);
        Drawable glass = glassDrawable(surface);
        Paint rim = rimPaint(glass);
        Shader initialShader = rim.getShader();
        assertNotNull(initialShader);
        Drawable.Callback originalCallback = glass.getCallback();
        Invalidations callback = new Invalidations();
        glass.setCallback(callback);
        try {
            layout(surface, 200, 140);
            Shader resizedShader = rim.getShader();
            assertNotSame("A new surface size must update the gradient geometry",
                    initialShader, resizedShader);
            assertEquals(200, glass.getBounds().width());
            assertEquals(140, glass.getBounds().height());
            assertTrue("A resized surface must request a redraw", callback.count > 0);

            callback.count = 0;
            ControlCenterSoftGlass.apply(surface, true, 34);
            assertNotSame("A new strength must update the gradient colors",
                    resizedShader, rim.getShader());
            assertTrue("A changed strength must request a redraw", callback.count > 0);
        } finally {
            glass.setCallback(originalCallback);
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void changingDensityUpdatesShaderAndStrokeWithoutResizing() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, true, 72);
        Drawable glass = glassDrawable(surface);
        Paint rim = rimPaint(glass);
        Shader initialShader = rim.getShader();
        float initialStrokeWidth = rim.getStrokeWidth();
        assertNotNull(initialShader);
        DisplayMetrics metrics = surface.getResources().getDisplayMetrics();
        float originalDensity = metrics.density;
        Drawable.Callback originalCallback = glass.getCallback();
        Invalidations callback = new Invalidations();
        glass.setCallback(callback);
        try {
            metrics.density = originalDensity * 2f;
            ControlCenterSoftGlass.apply(surface, true, 72);
            assertNotSame("A density change must refresh the glass shader even at the same size",
                    initialShader, rim.getShader());
            assertEquals("Doubling density must scale the rim's pixel width",
                    initialStrokeWidth * 2f, rim.getStrokeWidth(), 0.001f);
            assertEquals(160, glass.getBounds().width());
            assertEquals(100, glass.getBounds().height());
            assertTrue("A density change must request a redraw", callback.count > 0);
        } finally {
            metrics.density = originalDensity;
            glass.setCallback(originalCallback);
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void zeroSizeClearsOldBoundsAndRestoredSizeCanDraw() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, true, 72);
        Drawable glass = glassDrawable(surface);
        Paint rim = rimPaint(glass);
        Shader initialShader = rim.getShader();
        assertNotNull(initialShader);
        Bitmap image = Bitmap.createBitmap(200, 140, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        try {
            layout(surface, 0, 0);
            ControlCenterSoftGlass.apply(surface, true, 72);
            assertEquals("A collapsed surface must not retain its old drawable width",
                    0, glass.getBounds().width());
            assertEquals("A collapsed surface must not retain its old drawable height",
                    0, glass.getBounds().height());
            assertTrue(glass.getBounds().isEmpty());
            glass.draw(canvas);

            layout(surface, 200, 140);
            ControlCenterSoftGlass.apply(surface, true, 72);
            assertSame("Resizing reuses the overlay registered on the view",
                    glass, glassDrawable(surface));
            assertEquals(200, glass.getBounds().width());
            assertEquals(140, glass.getBounds().height());
            assertNotNull(rim.getShader());
            assertNotSame("The restored surface needs a shader for its new geometry",
                    initialShader, rim.getShader());
            glass.draw(canvas);
        } finally {
            image.recycle();
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Activity activity) { super(activity); }
    }
    public static final class QSTileItemIconView extends FrameLayout {
        QSTileItemIconView(Activity activity) { super(activity); }
    }
    private static View measuredSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24f);
        surface.setBackground(background);
        layout(surface, 160, 100);
        return surface;
    }
    private static Drawable glassDrawable(View view) throws ReflectiveOperationException {
        Method current = ControlCenterSoftGlass.class.getDeclaredMethod("current", View.class);
        current.setAccessible(true);
        Object state = current.invoke(null, view);
        assertNotNull("A configured surface must have a glass overlay", state);
        Field drawable = state.getClass().getDeclaredField("drawable");
        drawable.setAccessible(true);
        return (Drawable) drawable.get(state);
    }
    private static Paint rimPaint(Drawable glass) throws ReflectiveOperationException {
        Field rim = glass.getClass().getDeclaredField("rim");
        rim.setAccessible(true);
        return (Paint) rim.get(glass);
    }
    private static final class Invalidations implements Drawable.Callback {
        int count;
        @Override public void invalidateDrawable(Drawable drawable) { count++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable action, long when) { }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable action) { }
    }
    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }
}
