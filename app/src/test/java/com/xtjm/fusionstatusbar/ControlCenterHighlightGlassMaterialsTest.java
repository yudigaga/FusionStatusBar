package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
public class ControlCenterHighlightGlassMaterialsTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void oneSavedConfigUsesEachViewModeForBackgroundBlendAndNeutralText() throws Exception {
        ControlCenterConfig settings = settings();
        QSCardItemView day = panel(false), night = panel(true);
        TextView dayLabel = label(day, 0xcc000000), nightLabel = label(night, 0xcc000000);
        TextView dayAccent = label(day, Color.BLUE), nightAccent = label(night, Color.BLUE);
        ControlCenterMaterials.registerRoot(day, settings);
        ControlCenterMaterials.registerRoot(night, settings);
        record(day, "setMiBackgroundBlendColors", new Object[] {nativeBlend()}, settings);
        record(night, "setMiBackgroundBlendColors", new Object[] {nativeBlend()}, settings);

        assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS.color, background(day).getColor().getDefaultColor());
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, background(night).getColor().getDefaultColor());
        assertEquals("Glass background opacity follows card strength", 153, background(day).getAlpha());
        assertDaylightBlend(day.colors);
        assertEquals(1, night.colors.size());
        assertEquals(ControlCenterMaterials.color(ControlCenterSurface.SOFT_GLASS.color, 60), night.colors.get(0).x);
        assertEquals(3, night.colors.get(0).y);
        assertTrue(Color.luminance(dayLabel.getCurrentTextColor()) > 0.9f);
        assertTrue(Color.luminance(nightLabel.getCurrentTextColor()) > 0.9f);
        assertEquals(0xcc, Color.alpha(dayLabel.getCurrentTextColor()));
        assertEquals(0xcc, Color.alpha(nightLabel.getCurrentTextColor()));
        assertEquals(Color.BLUE, dayAccent.getCurrentTextColor());
        assertEquals(Color.BLUE, nightAccent.getCurrentTextColor());
        assertSame(ControlCenterSurface.HIGHLIGHT_GLASS, settings.surface);

        ControlCenterMaterials.refresh(settings.withEnabled(false));
        for (QSCardItemView panel : new QSCardItemView[] {day, night}) {
            assertEquals(0xff353840, background(panel).getColor().getDefaultColor());
            assertEquals(nativeBlend(), panel.colors);
        }
        assertEquals(0xcc000000, dayLabel.getCurrentTextColor());
        assertEquals(0xcc000000, nightLabel.getCurrentTextColor());
    }

    @Test public void existingNativeSurfaceTracksModeAndRestoresLatestOemEffects() throws Exception {
        QSCardItemView panel = panel(false);
        ControlCenterConfig settings = settings();
        ControlCenterMaterials.registerRoot(panel, settings);
        float[] oemBloom = new float[21];
        oemBloom[0] = 4f; oemBloom[5] = 0.7f; oemBloom[20] = 0.2f;
        Object[] oemShadow = {Color.MAGENTA, 2f, 3f, 6f, 0.8f};
        record(panel, "setMiViewBlurMode", new Object[] {1}, settings);
        record(panel, "setMiBackgroundBlendColors", new Object[] {nativeBlend()}, settings);
        record(panel, "setMiBloomStroke", new Object[] {oemBloom}, settings);
        record(panel, "setMiShadow", oemShadow, settings);
        ControlCenterMaterials.updateNativeSurface(panel, settings, false);

        assertDaylightBlend(panel.colors);
        assertEquals(1, panel.viewMode);
        assertEquals(1, panel.backgroundMode);
        assertEquals(165, panel.radius);
        assertArrayEquals("The compatibility edge must not activate an unverified native bloom profile",
                new float[21], panel.bloom, 0f);
        assertEquals("The old native shadow must not double the new rim", 0, panel.shadow[0]);
        float[] daylight = panel.bloom.clone();

        setNight(panel, true);
        ControlCenterMaterials.refresh(settings);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, background(panel).getColor().getDefaultColor());
        assertEquals(1, panel.colors.size());
        assertEquals(ControlCenterMaterials.color(ControlCenterSurface.SOFT_GLASS.color, 60), panel.colors.get(0).x);
        assertArrayEquals(new float[21], panel.bloom, 0f);
        assertEquals(165, panel.radius);

        setNight(panel, false);
        ControlCenterMaterials.refresh(settings);
        assertDaylightBlend(panel.colors);
        assertArrayEquals(daylight, panel.bloom, 0f);
        oemBloom[0] = 7f;
        record(panel, "setMiBloomStroke", new Object[] {oemBloom}, settings);
        assertArrayEquals("A late OEM redraw must keep the daylight profile", daylight, panel.bloom, 0f);
        ArrayList<Point> latestBlend = new ArrayList<>(List.of(new Point(0x88345678, 5),
                new Point(0x20776655, 3)));
        record(panel, "setMiBackgroundBlendColors", new Object[] {latestBlend}, settings);
        assertDaylightBlend(panel.colors);
        ControlCenterMaterials.refresh(settings.withSurface(ControlCenterSurface.DEFAULT).withBlur(100, 100, 100));
        assertArrayEquals("Disabling the material restores the latest native bloom", oemBloom, panel.bloom, 0f);
        assertArrayEquals(oemShadow, panel.shadow);
        assertEquals("Disabling must restore the most recent OEM blend", latestBlend, panel.colors);
        assertEquals(2, panel.backgroundMode);
        assertEquals(90, panel.radius);
        assertEquals(1, panel.viewMode);
        assertEquals(0xff353840, background(panel).getColor().getDefaultColor());
    }

    @Test public void activeControlsAndSliderFillKeepTheirStateAcrossDayAndNight() throws Exception {
        ControlCenterConfig settings = settings();
        QSCardItemView panel = panel(false);
        panel.state = 2;
        TextView label = label(panel, Color.WHITE);
        ControlCenterMaterials.registerRoot(panel, settings);
        record(panel, "setMiBackgroundBlendColors", new Object[] {nativeBlend()}, settings);

        ToggleSliderView slider = new ToggleSliderView(panel.getContext());
        NativeProgressView fill = new NativeProgressView(panel.getContext());
        fill.setId(android.R.id.progress);
        ColorDrawable white = new ColorDrawable(Color.WHITE);
        fill.setBackground(white);
        slider.addView(fill);
        ControlCenterMaterials.registerRoot(slider, settings);
        Object[] fillRequest = {nativeBlend()};
        assertSame(fillRequest, ControlCenterMaterials.nativeArguments(fill, "setMiBackgroundBlendColors",
                fillRequest, settings, false));
        fill.setMiBackgroundBlendColors((ArrayList<Point>) fillRequest[0]);
        ControlCenterMaterials.nativeArguments(fill, "setMiViewBlurMode", new Object[] {1}, settings, false);

        for (boolean night : new boolean[] {false, true, false}) {
            setNight(panel, night);
            ControlCenterMaterials.refresh(settings);
            assertEquals(ControlCenterCardView.ACTIVE_SURFACE, background(panel).getColor().getDefaultColor());
            assertEquals(1, panel.colors.size());
            assertEquals(ControlCenterMaterials.color(ControlCenterCardView.ACTIVE_SURFACE, 60), panel.colors.get(0).x);
            assertEquals(3, panel.colors.get(0).y);
            assertTrue("Active controls retain dark text on their highlighted state",
                    Color.luminance(label.getCurrentTextColor()) < 0.04f);
            assertEquals(Color.WHITE, white.getColor());
            assertEquals(nativeBlend(), fill.colors);
            assertEquals("Foreground fill keeps its native blur radius", 90, fill.radius);
        }
    }

    @Test public void attachedCardsShareOneThemeRefreshAndStopObservingWhenAutomaticStyleIsRemoved()
            throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            Activity activity = controller.get();
            FrameLayout content = new FrameLayout(activity);
            QSCardItemView first = panel(activity), second = panel(activity);
            TextView firstLabel = label(first, 0xcc000000), secondLabel = label(second, 0xcc000000);
            content.addView(first, new FrameLayout.LayoutParams(160, 100));
            content.addView(second, new FrameLayout.LayoutParams(160, 100));
            activity.setContentView(content);
            controller.visible();
            View windowRoot = activity.getWindow().getDecorView();
            assertTrue("This fixture must exercise an attached window", first.isAttachedToWindow());
            assertSame(windowRoot, first.getRootView());
            assertSame(windowRoot, second.getRootView());
            setNight(windowRoot, false);

            ControlCenterConfig settings = settings();
            ControlCenterConfig softGlass = settings.withSurface(ControlCenterSurface.SOFT_GLASS);
            for (QSCardItemView card : new QSCardItemView[] {first, second}) {
                ControlCenterMaterials.registerRoot(card, softGlass);
                record(card, "setMiBackgroundBlendColors", new Object[] {nativeBlend()}, softGlass);
                record(card, "setMiBloomStroke", new Object[] {new float[21]}, softGlass);
                record(card, "setMixEffectEnabled", new Object[] {false}, softGlass);
                ControlCenterMaterials.updateNativeSurface(card, softGlass, false);
            }
            assertEquals("An existing manual style needs no theme observer", 0, themeRootCount());
            ControlCenterMaterials.refresh(settings);
            assertEquals("Both attached cards share a single observer for their window", 1, themeRootCount());
            first.resetCalls(); second.resetCalls();

            setNight(windowRoot, true);
            windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            for (QSCardItemView card : new QSCardItemView[] {first, second}) {
                assertEquals(ControlCenterSurface.SOFT_GLASS.color, background(card).getColor().getDefaultColor());
                assertEquals("One mode change must replay each card's native blend once", 1, card.blendCalls);
                assertArrayEquals(new float[21], card.bloom, 0f);
                assertFalse(card.mix);
            }
            assertTrue(Color.luminance(firstLabel.getCurrentTextColor()) > 0.9f);
            assertTrue(Color.luminance(secondLabel.getCurrentTextColor()) > 0.9f);
            int firstCalls = first.nativeCalls, secondCalls = second.nativeCalls;
            for (int frame = 0; frame < 8; frame++) windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals("Stable frames must not replay native setters", firstCalls, first.nativeCalls);
            assertEquals(secondCalls, second.nativeCalls);

            first.resetCalls(); second.resetCalls();
            setNight(windowRoot, false);
            windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            for (QSCardItemView card : new QSCardItemView[] {first, second}) {
                assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS.color, background(card).getColor().getDefaultColor());
                assertEquals(1, card.blendCalls);
                assertFalse("Day compatibility must not force native mix on", card.mix);
            }
            assertTrue(Color.luminance(firstLabel.getCurrentTextColor()) > 0.9f);
            assertTrue(Color.luminance(secondLabel.getCurrentTextColor()) > 0.9f);

            ControlCenterMaterials.refresh(softGlass);
            assertEquals("Leaving the automatic style releases its window observer", 0, themeRootCount());
            first.resetCalls(); second.resetCalls();
            setNight(windowRoot, true);
            for (int frame = 0; frame < 8; frame++) windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(0, first.nativeCalls);
            assertEquals(0, second.nativeCalls);
            assertEquals(ControlCenterSurface.SOFT_GLASS.color, background(first).getColor().getDefaultColor());
            assertEquals(ControlCenterSurface.SOFT_GLASS.color, background(second).getColor().getDefaultColor());

            ControlCenterMaterials.refresh(settings);
            assertEquals("Re-enabling automatic style rebuilds one shared observer", 1, themeRootCount());
            first.resetCalls(); second.resetCalls();
            setNight(windowRoot, false);
            windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            for (QSCardItemView card : new QSCardItemView[] {first, second}) {
                assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS.color, background(card).getColor().getDefaultColor());
                assertEquals(1, card.blendCalls);
                assertFalse("Re-enabling compatibility must not force native mix on", card.mix);
            }
        } finally {
            ControlCenterMaterials.clearForTest();
            controller.pause().stop().destroy();
        }
    }

    @Test public void compatibilityGlassKeepsTheFalseOemMixRequestAcrossDayAndNight() throws Exception {
        QSCardItemView panel = panel(false);
        ControlCenterConfig settings = settings();
        ControlCenterMaterials.registerRoot(panel, settings);
        record(panel, "setMixEffectEnabled", new Object[] {false}, settings);
        ControlCenterMaterials.updateNativeSurface(panel, settings, false);
        assertFalse("The compatibility renderer must not infer a full glass backend from the mix setter", panel.mix);

        setNight(panel, true);
        ControlCenterMaterials.refresh(settings);
        assertFalse("Soft glass restores the native mix preference", panel.mix);
        setNight(panel, false);
        ControlCenterMaterials.refresh(settings);
        assertFalse(panel.mix);
        ControlCenterMaterials.refresh(settings.withSurface(ControlCenterSurface.DEFAULT).withBlur(100, 100, 100));
        assertFalse("Removing the custom style restores the original false request", panel.mix);
    }

    @Test public void compatibilityCardDoesNotInventBloomAndLaterRestoresTheLatestOemRequest()
            throws Exception {
        QSCardItemView panel = panel(false);
        ControlCenterConfig settings = settings();
        record(panel, "setMiViewBlurMode", new Object[] {1}, settings);
        ControlCenterMaterials.updateNativeSurface(panel, settings, false);
        assertArrayEquals("A missing OEM bloom request must not create a synthetic native glass profile",
                new float[21], panel.bloom, 0f);
        float[] daylight = panel.bloom.clone();

        setNight(panel, true);
        ControlCenterMaterials.refresh(settings);
        assertArrayEquals("Compatibility drawing must not create a native rim at night",
                new float[21], panel.bloom, 0f);
        ControlCenterConfig original = settings.withSurface(ControlCenterSurface.DEFAULT).withBlur(100, 100, 100);
        ControlCenterMaterials.refresh(original);
        assertArrayEquals(new float[21], panel.bloom, 0f);

        setNight(panel, false);
        ControlCenterMaterials.refresh(settings);
        assertArrayEquals(daylight, panel.bloom, 0f);
        float[] latestOem = new float[21];
        latestOem[0] = 7f; latestOem[13] = 0.3f; latestOem[20] = 0.2f;
        record(panel, "setMiBloomStroke", new Object[] {latestOem}, settings);
        assertArrayEquals("Canvas decorations suppress the late OEM bloom without substituting another native profile",
                daylight, panel.bloom, 0f);
        setNight(panel, true);
        ControlCenterMaterials.refresh(settings);
        assertArrayEquals(new float[21], panel.bloom, 0f);
        ControlCenterMaterials.refresh(original);
        assertArrayEquals("Disabling restores the actual latest OEM bloom request",
                latestOem, panel.bloom, 0f);
    }

    private static ControlCenterConfig settings() {
        return ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(35, 60, 85);
    }
    private static QSCardItemView panel(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return panel(app.createConfigurationContext(configuration));
    }
    private static QSCardItemView panel(Context context) {
        QSCardItemView panel = new QSCardItemView(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24f);
        panel.setBackground(background);
        return panel;
    }
    private static int themeRootCount() throws ReflectiveOperationException {
        Field roots = ControlCenterMaterials.class.getDeclaredField("THEME_ROOTS");
        roots.setAccessible(true);
        return ((Map<?, ?>) roots.get(null)).size();
    }
    private static GradientDrawable background(View view) { return (GradientDrawable) view.getBackground(); }
    private static TextView label(FrameLayout panel, int color) {
        TextView text = new TextView(panel.getContext());
        text.setTextColor(color);
        panel.addView(text);
        return text;
    }
    @SuppressWarnings("deprecation")
    private static void setNight(View view, boolean night) {
        Configuration configuration = new Configuration(view.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        view.getResources().updateConfiguration(configuration, view.getResources().getDisplayMetrics());
    }
    private static ArrayList<Point> nativeBlend() {
        return new ArrayList<>(List.of(new Point(0xccffffff, 14), new Point(0x80112233, 5)));
    }
    private static void assertDaylightBlend(ArrayList<Point> colors) {
        assertEquals(List.of(new Point(ControlCenterMaterials.color(ControlCenterSurface.HIGHLIGHT_GLASS.color, 60), 3)), colors);
    }
    private static void record(View view, String operation, Object[] args, ControlCenterConfig settings)
            throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation, args, settings, false);
        ControlCenterMaterials.invoke(view, operation, actual);
    }

    public static class NativeSurfaceView extends FrameLayout {
        int viewMode = 1, backgroundMode = 2, radius = 90;
        int blendCalls, nativeCalls;
        boolean mix;
        float[] bloom = new float[21];
        Object[] shadow;
        ArrayList<Point> colors = new ArrayList<>();
        NativeSurfaceView(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        void resetCalls() { blendCalls = nativeCalls = 0; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; nativeCalls++; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; nativeCalls++; }
        public void setMiViewBlurMode(int value) { viewMode = value; nativeCalls++; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; blendCalls++; nativeCalls++; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); nativeCalls++; }
        public void setMixEffectEnabled(boolean value) { mix = value; nativeCalls++; }
        public void setMiShadow(int color, float x, float y, float radius, float dispersion) {
            shadow = new Object[] {color, x, y, radius, dispersion};
            nativeCalls++;
        }
    }
    public static class QSCardItemView extends NativeSurfaceView {
        int state = 1;
        QSCardItemView(Context context) { super(context); }
    }
    public static class ToggleSliderView extends FrameLayout {
        ToggleSliderView(Context context) { super(context); }
    }
    public static class NativeProgressView extends NativeSurfaceView {
        NativeProgressView(Context context) { super(context); }
    }
}
