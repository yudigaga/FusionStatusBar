package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

/** API doubles observe requests only; these tests do not establish compositor blur support. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationBlurFallbackTest {
    private static final int OEM_COLOR = 0xffeef2f6;
    private static final int OEM_TEXT = 0xff202124;
    private static final int HOST_DRAW_COLOR = 0xffddeeff;

    @Test public void unknownBlurUsesTheHostDrawableAndHostTextWithoutChangingTheSavedChoice() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            assertEquals("Unknown OEM blur must not replace the host body with glass tint",
                    OEM_COLOR, fixture.centerPixel());
            assertEquals("Native drawing retains the matching host text color", OEM_TEXT,
                    fixture.label.getCurrentTextColor());
            assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS, fixture.settings.controlCenter.surface);
            assertEquals("Actual-height clipping remains owned by the notification wrapper", 0,
                    fixture.pixel(80, 120));
        }
    }

    @Test public void everyPositiveAutomaticStrengthKeepsTheCompleteNativeBackground() {
        try (Fixture fixture = new Fixture()) {
            fixture.original.setAlpha(128);
            for (int strength : new int[] {100, 30, 100, 30}) {
                fixture.apply(fixture.settings.withControlCenter(
                        fixture.settings.controlCenter.withBlur(100, strength, 100)));
                assertEquals("Positive UNKNOWN fallback must not fade the host background a second time",
                        128, Color.alpha(fixture.centerPixel()));
                assertEquals("Drawing must restore the host drawable alpha", 128, fixture.original.getAlpha());
                assertEquals("A compatibility fallback must preserve the original blur request", 180,
                        fixture.background.radius);
            }
        }
    }

    @Test public void fallbackPreservesNativeBlurAndEffectsAndDisablingRestoresTheLatestRequests()
            throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            NativeNotificationBackground background = fixture.background;
            assertEquals(3, background.viewMode);
            assertEquals(2, background.backgroundMode);
            assertEquals(180, background.radius);
            assertEquals(.75f, background.scaleRatio, 0f);

            float[] bloom = new float[21];
            bloom[0] = 7f;
            bloom[13] = .4f;
            ArrayList<Point> colors = new ArrayList<>(List.of(new Point(Color.CYAN, 3)));
            fixture.request("setMiViewBlurMode", 2);
            fixture.request("setMiBackgroundBlurMode", 4);
            fixture.request("setMiBackgroundBlurRadius", 240);
            fixture.request("setMiBackgroundBlurScaleRatio", .65f);
            fixture.request("setMiBackgroundBlendColors", colors);
            fixture.request("setMixEffectEnabled", true);
            fixture.request("setMiBloomStroke", bloom);
            fixture.request("setMiShadow", Color.MAGENTA, 2f, 3f, 6f, .8f);
            assertEquals(2, background.viewMode);
            assertEquals(4, background.backgroundMode);
            assertEquals(240, background.radius);
            assertEquals(.65f, background.scaleRatio, 0f);
            assertEquals(colors, background.colors);
            assertTrue(background.mix);
            assertArrayEquals(bloom, background.bloom, 0f);
            assertArrayEquals(new Object[] {Color.MAGENTA, 2f, 3f, 6f, .8f}, background.shadow);

            fixture.apply(fixture.settings);
            fixture.disable();
            assertSame(fixture.original, background.getBackground());
            assertEquals(2, background.viewMode);
            assertEquals(4, background.backgroundMode);
            assertEquals(240, background.radius);
            assertEquals(.65f, background.scaleRatio, 0f);
            assertEquals(colors, background.colors);
            assertTrue(background.mix);
            assertArrayEquals(bloom, background.bloom, 0f);
            assertArrayEquals(new Object[] {Color.MAGENTA, 2f, 3f, 6f, .8f}, background.shadow);
        }
    }

    @Test public void automaticZeroPreservesTheHostBodyWhileExplicitClearRemainsTransparent() throws Exception {
        for (boolean clear : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                ControlCenterConfig chosen = clear
                        ? fixture.settings.controlCenter.withSurface(ControlCenterSurface.CLEAR)
                        : fixture.settings.controlCenter.withBlur(100, 0, 100);
                fixture.apply(fixture.settings.withControlCenter(chosen));
                fixture.request("setMixEffectEnabled", true);
                fixture.request("setMiBackgroundBlendColors",
                        new ArrayList<>(List.of(new Point(Color.CYAN, 3))));
                if (clear) {
                    assertEquals("Explicit CLEAR remains a transparent endpoint", 0,
                            Color.alpha(fixture.centerPixel()));
                    assertEquals(0, fixture.background.viewMode);
                    assertEquals(0, fixture.background.backgroundMode);
                    assertEquals(0, fixture.background.radius);
                    assertFalse(fixture.background.mix);
                    assertTrue(fixture.background.colors.isEmpty());
                } else {
                    assertEquals("Automatic glass keeps the native host body at configured zero", OEM_COLOR,
                            fixture.centerPixel());
                    assertEquals(3, fixture.background.viewMode);
                    assertEquals(2, fixture.background.backgroundMode);
                    assertEquals(180, fixture.background.radius);
                    assertTrue(fixture.background.mix);
                    assertEquals(Color.CYAN, fixture.background.colors.get(0).x);
                }
                assertEquals(1f, fixture.label.getAlpha(), 0f);
                assertSame(fixture.row, fixture.label.getParent());

                fixture.apply(fixture.settings);
                assertEquals("Returning to positive strength restores the complete host body", OEM_COLOR,
                        fixture.centerPixel());
                assertEquals(3, fixture.background.viewMode);
                assertEquals(2, fixture.background.backgroundMode);
                assertEquals(180, fixture.background.radius);
                assertTrue(fixture.background.mix);
                assertEquals(Color.CYAN, fixture.background.colors.get(0).x);
            }
        }
    }

    @Test public void manualSoftGlassRetainsItsExistingCanvasMaterial() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings.withControlCenter(fixture.settings.controlCenter
                    .withSurface(ControlCenterSurface.SOFT_GLASS)));
            int pixel = fixture.centerPixel();
            assertEquals(192, Color.alpha(pixel));
            assertEquals(60, Color.red(pixel), 1);
            assertEquals(65, Color.green(pixel), 1);
            assertEquals(73, Color.blue(pixel), 1);
            assertEquals("Manual soft glass continues suppressing the OEM view composition", 0,
                    fixture.background.viewMode);
            fixture.apply(fixture.settings);
            assertEquals(OEM_COLOR, fixture.centerPixel());
            assertEquals(3, fixture.background.viewMode);
        }
    }

    @Test public void themeCallbacksRetainNotificationFallbackAndTheOwnedBoundsWrapper() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            Drawable bounded = fixture.background.getBackground();
            for (boolean night : new boolean[] {true, false, true}) {
                fixture.theme(night);
                assertSame("A theme callback must retain actual-height clipping", bounded,
                        fixture.background.getBackground());
                assertEquals("The theme callback must not lose the notification fallback flag", OEM_COLOR,
                        fixture.centerPixel());
                assertEquals(OEM_TEXT, fixture.label.getCurrentTextColor());
                assertEquals(3, fixture.background.viewMode);
                assertEquals(180, fixture.background.radius);
            }
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
            assertEquals(OEM_TEXT, fixture.label.getCurrentTextColor());
        }
    }

    @Test public void anInternalHostDrawableRebindIsVisibleWithoutAnotherApplyAndRestoresOnDisable() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            GradientDrawable replacement = background(0xffd6e8ce);
            replacement.setAlpha(128);
            fixture.background.mBackground = replacement;
            assertEquals("Host rebinding may happen without a layout or uiMode change", 128,
                    Color.alpha(fixture.centerPixel()));
            assertEquals(214, Color.red(fixture.centerPixel()), 1);
            assertEquals(232, Color.green(fixture.centerPixel()), 1);
            assertEquals(206, Color.blue(fixture.centerPixel()), 1);
            assertEquals("The displaced drawable must not be mutated by future draws", 255,
                    fixture.original.getAlpha());
            fixture.theme(true);
            fixture.disable();
            assertSame("Disabling restores the current host drawable, not its obsolete predecessor",
                    replacement, fixture.background.getBackground());
            assertEquals(128, replacement.getAlpha());
        }
    }

    @Test public void aHostBackgroundReplacementAfterThemeChangeRemainsTheRestoredBackground() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            fixture.theme(true);
            GradientDrawable replacement = background(0xffb9cee8);
            fixture.background.mBackground = replacement;
            fixture.background.setBackground(replacement);
            fixture.apply(fixture.settings);
            assertEquals(0xffb9cee8, fixture.centerPixel());
            fixture.disable();
            assertSame(replacement, fixture.background.getBackground());
        }
    }

    @Test public void missingHostDrawableLeavesNativeOnDrawAndTextInControl() {
        try (Fixture fixture = new Fixture(false)) {
            fixture.apply(fixture.settings);
            assertNull("Without an original drawable the onDraw hook must not see an active bounds wrapper",
                    fixture.background.getBackground());
            assertFalse("Unknown native drawing must not be claimed by a replacement surface",
                    SystemSurfaceStyle.isApplied(fixture.background));
            assertEquals(HOST_DRAW_COLOR, fixture.centerPixel());
            assertEquals(OEM_TEXT, fixture.label.getCurrentTextColor());
            fixture.theme(true);
            assertNull(fixture.background.getBackground());
            assertEquals(HOST_DRAW_COLOR, fixture.centerPixel());
            fixture.disable();
            assertNull(fixture.background.getBackground());
        }
    }

    @Test public void anExternalBackgroundReplacementImmediatelyBeforeDisableWinsOverOldInternalState() {
        try (Fixture fixture = new Fixture()) {
            fixture.apply(fixture.settings);
            GradientDrawable replacement = background(0xffb9cee8);
            fixture.background.setBackground(replacement);
            fixture.disable();
            assertSame("Turning off must not overwrite a newer external host drawable",
                    replacement, fixture.background.getBackground());
            assertSame("The fixture exercises a stale private field", fixture.original,
                    fixture.background.mBackground);
        }
    }

    @Test public void anInternalOnlyHostDrawableRestoresTheInitiallyNullViewBackground() {
        try (Fixture fixture = new Fixture()) {
            fixture.background.setBackground(null);
            fixture.apply(fixture.settings);
            assertEquals(OEM_COLOR, fixture.centerPixel());
            assertTrue(SystemSurfaceStyle.isApplied(fixture.background));
            fixture.disable();
            assertNull("The host draws its internal drawable in onDraw after ownership is released",
                    fixture.background.getBackground());
            assertSame(fixture.original, fixture.background.mBackground);
            assertEquals(255, fixture.original.getAlpha());
        }
    }

    private static GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12);
        return drawable;
    }

    private static final class Fixture implements AutoCloseable {
        final Context context;
        final NotificationRow row;
        final NativeNotificationBackground background;
        final GradientDrawable original = background(OEM_COLOR);
        final TextView label;
        final FusionConfig settings = FusionConfig.defaults().withControlCenter(
                ControlCenterConfig.defaults().withEnabled(true)
                        .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                        .withBlur(100, 100, 100));

        Fixture() { this(true); }

        Fixture(boolean hasHostDrawable) {
            Context app = RuntimeEnvironment.getApplication();
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
            context = app.createConfigurationContext(configuration);
            row = new NotificationRow(context);
            background = row.mBackgroundNormal;
            if (hasHostDrawable) {
                background.mBackground = original;
                background.setBackground(original);
            }
            label = new TextView(context);
            label.setText("Notification title");
            label.setTextColor(OEM_TEXT);
            row.addView(label, new FrameLayout.LayoutParams(100, 30));
            row.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, 160, 160);
        }

        void apply(FusionConfig chosen) { NotificationSurfaceHooks.applyRow(row, chosen); }

        void request(String operation, Object... requested) throws Exception {
            Object[] actual = SystemSurfaceStyle.nativeArguments(background, operation, requested);
            ControlCenterMaterials.invoke(background, operation, actual);
        }

        int centerPixel() { return pixel(80, 32); }

        int pixel(int x, int y) {
            Bitmap image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
            try {
                background.draw(new Canvas(image));
                return image.getPixel(x, y);
            } finally { image.recycle(); }
        }

        @SuppressWarnings("deprecation")
        void theme(boolean night) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
            background.getViewTreeObserver().dispatchOnPreDraw();
            row.getViewTreeObserver().dispatchOnPreDraw();
        }

        void disable() {
            apply(settings.withControlCenter(settings.controlCenter.withSystemSurfaces(
                    settings.controlCenter.systemSurfaces.withNotifications(false))));
        }

        @Override public void close() { disable(); }
    }

    public static final class NotificationRow extends FrameLayout {
        final NativeNotificationBackground mBackgroundNormal;
        NotificationRow(Context context) {
            super(context);
            mBackgroundNormal = new NativeNotificationBackground(context);
            addView(mBackgroundNormal, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    public static final class NativeNotificationBackground extends View {
        int mActualHeight = 72, mActualWidth = -1;
        int mClipTopAmount, mClipBottomAmount, mDrawableAlpha = 255;
        boolean mExpandAnimationRunning, mBottomIsRounded, mBottomAmountClips = true;
        Drawable mBackground;
        int viewMode = 3, backgroundMode = 2, radius = 180;
        float scaleRatio = .75f;
        boolean mix;
        float[] bloom = new float[21];
        Object[] shadow;
        ArrayList<Point> colors = new ArrayList<>();
        final Paint hostPaint = new Paint();

        NativeNotificationBackground(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            if (getBackground() != null) return;
            hostPaint.setColor(HOST_DRAW_COLOR);
            canvas.drawRect(0, 0, getWidth(), mActualHeight, hostPaint);
        }

        public int getMiViewBlurMode() { return viewMode; }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public float getMiBackgroundBlurScaleRatio() { return scaleRatio; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlurScaleRatio(float value) { scaleRatio = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = new ArrayList<>(value); }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); }
        public void setMiShadow(int color, float x, float y, float radius, float dispersion) {
            shadow = new Object[] {color, x, y, radius, dispersion};
        }
    }
}
