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

    @Test public void changingSystemModeRebuildsShaderOnceThenReusesItOnMaterialRefresh() throws Exception {
        QSCardItemView surface = measuredSurface();
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(100, 72, 100);
        ControlCenterMaterials.registerRoot(surface, settings);
        Drawable overlay = overlay(surface);
        Paint rim = rim(overlay);
        Shader day = rim.getShader();
        assertNotNull(day);
        Drawable.Callback originalCallback = overlay.getCallback();
        Invalidations callback = new Invalidations();
        overlay.setCallback(callback);
        try {
            setNight(surface, true);
            ControlCenterMaterials.refresh(settings);
            Shader night = rim.getShader();
            assertNotSame("Mode changes must update the reflection without a size change", day, night);
            assertEquals("A mode change needs one drawable redraw", 1, callback.count);
            assertSame("Mode changes retain the existing overlay and layout listener", overlay, overlay(surface));
            assertEquals(ControlCenterSurface.SOFT_GLASS.color,
                    ((GradientDrawable) surface.getBackground()).getColor().getDefaultColor());

            callback.count = 0;
            for (int update = 0; update < 8; update++) ControlCenterMaterials.refresh(settings);
            assertSame(night, rim.getShader());
            assertEquals("Unchanged mode refreshes must not invalidate the glass repeatedly", 0, callback.count);

            setNight(surface, false);
            ControlCenterMaterials.refresh(settings);
            Shader nextDay = rim.getShader();
            assertNotSame(night, nextDay);
            assertEquals(1, callback.count);
            callback.count = 0;
            for (int update = 0; update < 8; update++) ControlCenterMaterials.refresh(settings);
            assertSame(nextDay, rim.getShader());
            assertEquals(0, callback.count);
        } finally { overlay.setCallback(originalCallback); }

        ControlCenterMaterials.refresh(settings.withEnabled(false));
        assertNull("Disabling must release the overlay and its listener state", state(surface));
        Bitmap restored = draw(surface);
        try { assertEquals(0xff353840, restored.getPixel(80, 0)); }
        finally { restored.recycle(); }
        ControlCenterMaterials.registerRoot(surface, settings);
        assertNotSame("Re-enabling creates a fresh registered overlay", overlay, overlay(surface));
        assertNotNull(rim(overlay(surface)).getShader());
    }

    @Test public void automaticNightReflectionMatchesExistingSoftGlass() {
        View automatic = measuredSurface(), manual = measuredSurface();
        setNight(automatic, true);
        ControlCenterSoftGlass.apply(automatic, ControlCenterSurface.HIGHLIGHT_GLASS, true, 72);
        ControlCenterSoftGlass.apply(manual, true, 72);
        Bitmap autoImage = draw(automatic), manualImage = draw(manual);
        try {
            assertTrue("Automatic night mode must preserve the established soft glass rendering",
                    autoImage.sameAs(manualImage));
        } finally {
            autoImage.recycle(); manualImage.recycle();
            ControlCenterSoftGlass.apply(automatic, false, 0);
            ControlCenterSoftGlass.apply(manual, false, 0);
        }
    }

    private static QSCardItemView measuredSurface() {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_NO;
        QSCardItemView surface = new QSCardItemView(app.createConfigurationContext(configuration));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24f);
        surface.setBackground(background);
        surface.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        surface.layout(0, 0, 160, 100);
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
