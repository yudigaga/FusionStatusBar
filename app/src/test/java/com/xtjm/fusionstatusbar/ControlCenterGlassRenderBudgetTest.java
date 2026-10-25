package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterGlassRenderBudgetTest {
    private static final int FRAMES = 30, BODY = 0xff353840;
    private final List<Fixture> fixtures = new ArrayList<>();

    @After public void clear() {
        for (Fixture fixture : fixtures) fixture.close();
        ControlCenterMaterials.clearForTest();
    }

    @Test public void stableRoundedSurfaceReusesStrokeGeometryAcrossRealDrawFrames() throws Exception {
        Fixture fixture = rounded(160, 100, 24f);
        fixture.draw();
        Bitmap first = fixture.snapshot();
        fixture.counter.calls = 0;
        try {
            for (int frame = 0; frame < FRAMES; frame++) fixture.draw();
            assertTrue("Repeated draws must retain the same glass pixels, content, and clipping",
                    first.sameAs(fixture.image));
            assertEquals("A warmed static round-rect must reuse its stroke geometry instead of rebuilding it per frame",
                    0, fixture.counter.calls);
        } finally { first.recycle(); }
    }

    @Test public void stableOemSmoothPathReusesStrokeGeometryAcrossRealDrawFrames() throws Exception {
        Fixture fixture = smooth(160, 100, 24f);
        fixture.draw();
        Bitmap first = fixture.snapshot();
        fixture.counter.calls = 0;
        try {
            for (int frame = 0; frame < FRAMES; frame++) fixture.draw();
            assertTrue(first.sameAs(fixture.image));
            assertEquals("An unchanged OEM path must not regenerate every soft-edge band in every frame",
                    0, fixture.counter.calls);
        } finally { first.recycle(); }
    }

    @Test public void sizeAndSameDrawableCornerChangesRefreshTheActualRenderedContour() throws Exception {
        Fixture fixture = rounded(160, 100, 24f);
        fixture.draw();
        fixture.resize(200, 130);
        fixture.draw();
        assertSamePixels("Resize must invalidate cached geometry", rounded(200, 130, 24f), fixture);

        GradientDrawable sameDrawable = (GradientDrawable) fixture.view.getBackground();
        sameDrawable.setCornerRadius(6f);
        fixture.draw();
        assertSamePixels("Changing a radius on the same Drawable must update the contour", rounded(200, 130, 6f), fixture);

        sameDrawable.setShape(GradientDrawable.OVAL);
        fixture.draw();
        Fixture coldOval = rounded(200, 130, 6f);
        ((GradientDrawable) coldOval.view.getBackground()).setShape(GradientDrawable.OVAL);
        assertSamePixels("Changing the same Drawable to an oval must update every reflection band", coldOval, fixture);
    }

    @Test public void mutatingTheSameNativePathRefreshesTheActualRenderedContour() throws Exception {
        assertNativePathMutation();
    }

    @Test public void replacingAnInverseNativePathWithARoundedDrawableUsesTheKnownContourFill() throws Exception {
        Fixture fixture = smooth(160, 100, 24f);
        fixture.draw();
        ((SmoothRoundDrawable) fixture.view.getBackground()).path.setFillType(Path.FillType.INVERSE_WINDING);
        fixture.draw();
        GradientDrawable replacement = new GradientDrawable();
        replacement.setColor(BODY);
        replacement.setCornerRadius(24f);
        fixture.view.setBackground(replacement);
        fixture.draw();
        assertSamePixels("A known round-rect must not inherit an inverse native fill type", rounded(160, 100, 24f), fixture);
    }

    @Test @Config(sdk = 33)
    public void nativePathMutationRemainsSafeOnApi33WithoutGenerationId() throws Exception {
        assertNativePathMutation();
    }

    @Test public void paintOnlyAndViewTranslationChangesReuseTheSameStrokeGeometry() throws Exception {
        Fixture fixture = rounded(160, 100, 24f);
        fixture.draw();
        fixture.counter.calls = 0;
        ControlCenterSoftGlass.apply(fixture.view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 45);
        fixture.draw();
        setNight(fixture.view, true);
        ControlCenterSoftGlass.apply(fixture.view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 45);
        fixture.draw();
        fixture.glass.setAlpha(96);
        fixture.draw();
        fixture.view.setTranslationY(32f);
        fixture.draw();
        assertEquals("Color, opacity, theme, and translation do not change local stroke geometry", 0,
                fixture.counter.calls);
    }

    @Test public void densityChangesRefreshStrokeGeometryEvenWhenViewSizeIsUnchanged() throws Exception {
        Fixture fixture = rounded(160, 100, 24f);
        fixture.draw();
        Fixture cold = rounded(160, 100, 24f);
        float oldDensity = fixture.view.getResources().getDisplayMetrics().density;
        float coldDensity = cold.view.getResources().getDisplayMetrics().density;
        try {
            fixture.view.getResources().getDisplayMetrics().density = oldDensity * 2f;
            ControlCenterSoftGlass.apply(fixture.view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
            fixture.draw();
            cold.view.getResources().getDisplayMetrics().density = oldDensity * 2f;
            ControlCenterSoftGlass.apply(cold.view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
            assertSamePixels("Density changes must refresh band widths without requiring a resize", cold, fixture);
        } finally {
            cold.view.getResources().getDisplayMetrics().density = coldDensity;
            fixture.view.getResources().getDisplayMetrics().density = oldDensity;
        }
    }

    @Test public void dynamicOutlineIsReadAtMostOncePerDrawAndStableDrawingDoesNotInvalidate() throws Exception {
        Fixture fixture = rounded(160, 100, 24f);
        CountingOutline outline = new CountingOutline(160, 100);
        fixture.view.setOutlineProvider(outline);
        fixture.draw();
        outline.calls = 0;
        fixture.counter.calls = 0;
        Drawable.Callback original = fixture.glass.getCallback();
        Invalidations invalidations = new Invalidations();
        fixture.glass.setCallback(invalidations);
        try {
            for (int frame = 0; frame < FRAMES; frame++) {
                ControlCenterSoftGlass.apply(fixture.view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
                fixture.draw();
            }
            assertTrue("Checking an OEM contour must not multiply outline queries per reflection band",
                    outline.calls <= FRAMES);
            assertEquals("A stable path supplied through Outline also reuses its stroke geometry", 0,
                    fixture.counter.calls);
            assertEquals("Stable drawing and material refresh must not schedule extra redraws", 0, invalidations.calls);
        } finally { fixture.glass.setCallback(original); }
    }

    private void assertNativePathMutation() throws Exception {
        Fixture fixture = smooth(160, 100, 24f);
        fixture.draw();
        SmoothRoundDrawable sameDrawable = (SmoothRoundDrawable) fixture.view.getBackground();
        Path samePath = sameDrawable.path;
        sameDrawable.setRadius(0f);
        assertSame("The mutation must retain the OEM Path identity", samePath, sameDrawable.path);
        fixture.draw();
        assertSamePixels("A native Path mutated in place must invalidate its cached stroke bands",
                smooth(160, 100, 0f), fixture);
    }

    private Fixture rounded(int width, int height, float radius) throws Exception {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(BODY);
        drawable.setCornerRadius(radius);
        return fixture(drawable, width, height);
    }

    private Fixture smooth(int width, int height, float radius) throws Exception {
        return fixture(new SmoothRoundDrawable(radius), width, height);
    }

    private Fixture fixture(Drawable drawable, int width, int height) throws Exception {
        Fixture fixture = new Fixture(drawable, width, height);
        fixtures.add(fixture);
        return fixture;
    }

    private static void assertSamePixels(String message, Fixture cold, Fixture actual) {
        cold.draw();
        assertTrue(message, cold.image.sameAs(actual.image));
    }

    private static final class Fixture {
        final FrameLayout view;
        final Drawable glass;
        final CountingPaint counter;
        Bitmap image;
        Canvas canvas;
        Fixture(Drawable background, int width, int height) throws Exception {
            Context app = RuntimeEnvironment.getApplication();
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
            view = new FrameLayout(app.createConfigurationContext(configuration));
            view.setBackground(background);
            View content = new View(view.getContext());
            content.setBackgroundColor(Color.BLUE);
            view.addView(content, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
            resize(width, height);
            ControlCenterSoftGlass.apply(view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
            Method current = ControlCenterSoftGlass.class.getDeclaredMethod("current", View.class);
            current.setAccessible(true);
            Object state = current.invoke(null, view);
            glass = (Drawable) field(state, "drawable").get(state);
            Object shape = field(glass, "shape").get(glass);
            Field strokePaint = field(shape, "strokePaint");
            counter = new CountingPaint((Paint) strokePaint.get(shape));
            // Count real Native getFillPath work without depending on cache implementation fields.
            strokePaint.set(shape, counter);
        }
        void resize(int width, int height) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            if (image != null) image.recycle();
            image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(image);
        }
        void draw() {
            image.eraseColor(Color.TRANSPARENT);
            view.draw(canvas);
        }
        Bitmap snapshot() { return image.copy(Bitmap.Config.ARGB_8888, false); }
        void close() {
            ControlCenterSoftGlass.apply(view, false, 0);
            image.recycle();
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CountingPaint extends Paint {
        int calls;
        CountingPaint(Paint original) { super(original); }
        @Override public boolean getFillPath(Path source, Path destination) {
            calls++;
            return super.getFillPath(source, destination);
        }
    }

    private static final class CountingOutline extends ViewOutlineProvider {
        final Path contour = new Path();
        int calls;
        CountingOutline(int width, int height) {
            contour.addRoundRect(new RectF(0, 0, width, height),
                    new float[] {24, 24, 24, 24, 0, 0, 0, 0}, Path.Direction.CW);
        }
        @Override public void getOutline(View view, Outline outline) {
            calls++;
            outline.setPath(contour);
        }
    }

    private static final class Invalidations implements Drawable.Callback {
        int calls;
        @Override public void invalidateDrawable(Drawable drawable) { calls++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable action, long time) { }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable action) { }
    }

    private static final class SmoothRoundDrawable extends Drawable {
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float radius;
        SmoothRoundDrawable(float radius) { this.radius = radius; paint.setColor(BODY); }
        void setRadius(float radius) {
            this.radius = radius;
            updatePath();
            invalidateSelf();
        }
        private void updatePath() {
            path.rewind();
            path.addRoundRect(new RectF(getBounds()), radius, radius, Path.Direction.CW);
        }
        @Override protected void onBoundsChange(Rect bounds) { updatePath(); }
        @Override public void draw(Canvas canvas) { canvas.drawPath(path, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    @SuppressWarnings("deprecation")
    private static void setNight(View view, boolean night) {
        Configuration configuration = new Configuration(view.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        view.getResources().updateConfiguration(configuration, view.getResources().getDisplayMetrics());
    }
}
