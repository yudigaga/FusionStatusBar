package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterHighlightGlassReflectionTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void dayAndNightReflectionsDifferWhilePreservingShapeAndInterior() throws Exception {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
        Bitmap day = draw(surface);
        setNight(surface, true);
        ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
        Bitmap night = draw(surface);
        try {
            assertFalse("The daylight rim must have a distinct light and dark reflection", day.sameAs(night));
            assertEquals("Reflection follows the native rounded outline", Color.TRANSPARENT, day.getPixel(1, 1));
            assertEquals(Color.TRANSPARENT, night.getPixel(1, 1));
            assertEquals("The overlay must not wash over surface content", 0xff353840, day.getPixel(80, 50));
            assertEquals(0xff353840, night.getPixel(80, 50));
            assertNotEquals("The day profile draws the native edge", 0xff353840, day.getPixel(80, 0));
            assertNotEquals("The night profile retains soft glass reflection", 0xff353840, night.getPixel(80, 0));
        } finally {
            day.recycle(); night.recycle();
            ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, false, 0);
        }
        assertNull(state(surface));
        Bitmap restored = draw(surface);
        try {
            assertEquals(0xff353840, restored.getPixel(80, 0));
            assertEquals(Color.TRANSPARENT, restored.getPixel(1, 1));
        } finally { restored.recycle(); }
    }

    @Test public void genericAutomaticCanvasModeRebuildsShaderOnceThenReusesItOnRefresh() throws Exception {
        QSCardItemView surface = measuredSurface();
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(100, 72, 100);
        // Exercise the generic Canvas renderer directly; UNKNOWN QS automatic glass now restores OEM ownership.
        ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
        Drawable overlay = overlay(surface);
        Paint rim = rim(overlay);
        Shader day = rim.getShader();
        assertNotNull(day);
        Drawable.Callback originalCallback = overlay.getCallback();
        Invalidations callback = new Invalidations();
        overlay.setCallback(callback);
        try {
            setNight(surface, true);
            ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
            Shader night = rim.getShader();
            assertNotSame("Mode changes must update the reflection without a size change", day, night);
            assertEquals("A mode change needs one drawable redraw", 1, callback.count);
            assertSame("Mode changes retain the existing overlay and layout listener", overlay, overlay(surface));
            assertEquals(0xff353840,
                    ((GradientDrawable) surface.getBackground()).getColor().getDefaultColor());

            callback.count = 0;
            for (int update = 0; update < 8; update++) ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
            assertSame(night, rim.getShader());
            assertEquals("Unchanged mode refreshes must not invalidate the glass repeatedly", 0, callback.count);

            setNight(surface, false);
            ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
            Shader nextDay = rim.getShader();
            assertNotSame(night, nextDay);
            assertEquals(1, callback.count);
            callback.count = 0;
            for (int update = 0; update < 8; update++) ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
            assertSame(nextDay, rim.getShader());
            assertEquals(0, callback.count);
        } finally { overlay.setCallback(originalCallback); }

        ControlCenterSoftGlass.apply(surface, settings.surface, false, 72);
        assertNull("Disabling must release the overlay and its listener state", state(surface));
        Bitmap restored = draw(surface);
        try { assertEquals(0xff353840, restored.getPixel(80, 0)); }
        finally { restored.recycle(); }
        ControlCenterSoftGlass.apply(surface, settings.surface, true, 72);
        assertNotSame("Re-enabling creates a fresh registered overlay", overlay, overlay(surface));
        assertNotNull(rim(overlay(surface)).getShader());
    }

    @Test public void daytimeBevelFadesAcrossSeveralPixelsWithoutCoveringTheBody() {
        View surface = measuredSurface();
        ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
        Bitmap image = draw(surface);
        try {
            float body = Color.luminance(0xff353840);
            assertTrue("The edge catches light above the background",
                    Color.luminance(image.getPixel(80, 0)) > body);
            assertTrue("The reflection reaches beyond a hairline then fades inward",
                    Color.luminance(image.getPixel(80, 2)) > body
                            && Color.luminance(image.getPixel(80, 2))
                            < Color.luminance(image.getPixel(80, 0)));
            assertEquals("The bevel leaves the surface interior unchanged",
                    0xff353840, image.getPixel(80, 8));
            assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        } finally {
            image.recycle();
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void automaticGlassKeepsItsLightDirectionAcrossWideAndTallSurfaces() {
        for (boolean night : new boolean[] {false, true}) {
            int[] wide = sampleReflections(320, 80, night);
            int[] tall = sampleReflections(80, 320, night);
            for (int[] edge : new int[][] {wide, tall}) {
                assertTrue("The upper-left rim catches a clear reflection", edge[0] >= 48);
                assertTrue("The top rim fades toward the right", edge[0] - edge[1] >= 12);
                assertTrue("The lower-right rim catches a weaker bounce", edge[3] > edge[2] + 4
                        && edge[3] < edge[1]);
                assertTrue("The strongest reflection is close to the contour",
                        edge[4] > 0 && edge[4] < edge[0] / 2);
            }
            for (int index = 0; index < wide.length; index++) {
                assertEquals("Matching positions on wide and tall glass should catch similar light",
                        wide[index], tall[index], 5);
            }
        }
    }

    private static int[] sampleReflections(int width, int height, boolean night) {
        View surface = measuredSurface(width, height, 12f);
        setNight(surface, night);
        ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
        Bitmap image = draw(surface);
        try {
            int left = width / 4, right = width * 3 / 4;
            int bottom = height - 1;
            int body = Color.red(image.getPixel(width / 2, height / 2));
            return new int[] {Color.red(image.getPixel(left, 0)) - body,
                    Color.red(image.getPixel(right, 0)) - body,
                    Color.red(image.getPixel(left, bottom)) - body,
                    Color.red(image.getPixel(right, bottom)) - body,
                    Color.red(image.getPixel(left, 2)) - body};
        } finally {
            image.recycle();
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    @Test public void automaticNightUsesTheSoftEdgeInsteadOfTheLegacySoftGlassLine() {
        View automatic = measuredSurface(), manual = measuredSurface();
        setNight(automatic, true);
        ControlCenterSoftGlass.apply(automatic, ControlCenterSurface.HIGHLIGHT_GLASS, true, 72);
        ControlCenterSoftGlass.apply(manual, true, 72);
        Bitmap autoImage = draw(automatic), manualImage = draw(manual);
        try {
            assertFalse("Automatic night glass has its own soft edge; manual soft glass keeps its legacy reflection",
                    autoImage.sameAs(manualImage));
        } finally {
            autoImage.recycle(); manualImage.recycle();
            ControlCenterSoftGlass.apply(automatic, false, 0);
            ControlCenterSoftGlass.apply(manual, false, 0);
        }
    }

    private static QSCardItemView measuredSurface() {
        return measuredSurface(160, 100, 24f);
    }

    private static QSCardItemView measuredSurface(int width, int height, float radius) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_NO;
        QSCardItemView surface = new QSCardItemView(app.createConfigurationContext(configuration));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(radius);
        surface.setBackground(background);
        surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        surface.layout(0, 0, width, height);
        return surface;
    }
    @SuppressWarnings("deprecation")
    private static void setNight(View view, boolean night) {
        Configuration configuration = new Configuration(view.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        view.getResources().updateConfiguration(configuration, view.getResources().getDisplayMetrics());
    }
    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }
    private static Object state(View view) throws ReflectiveOperationException {
        Method current = ControlCenterSoftGlass.class.getDeclaredMethod("current", View.class);
        current.setAccessible(true);
        return current.invoke(null, view);
    }
    private static Drawable overlay(View view) throws ReflectiveOperationException {
        Object state = state(view);
        assertNotNull(state);
        Field drawable = state.getClass().getDeclaredField("drawable");
        drawable.setAccessible(true);
        return (Drawable) drawable.get(state);
    }
    private static Paint rim(Drawable overlay) throws ReflectiveOperationException {
        Field rim = overlay.getClass().getDeclaredField("rim");
        rim.setAccessible(true);
        return (Paint) rim.get(overlay);
    }
    private static final class Invalidations implements Drawable.Callback {
        int count;
        @Override public void invalidateDrawable(Drawable drawable) { count++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable action, long when) { }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable action) { }
    }
    public static class QSCardItemView extends FrameLayout {
        QSCardItemView(Context context) { super(context); }
    }
}
