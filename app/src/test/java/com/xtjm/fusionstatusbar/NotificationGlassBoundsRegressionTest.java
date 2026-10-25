package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
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
public class NotificationGlassBoundsRegressionTest {
    private static final int SIZE = 160;

    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void collapsedNotificationClipsTheCompleteGlassMaterialToActualHeightAndNativeInsets() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.mActualHeight = 64;
            fixture.background.mClipTopAmount = 8;
            fixture.background.mClipBottomAmount = 10;
            try {
                fixture.apply();
                Bitmap image = draw(fixture.background);
                try {
                    assertTrue("The fixture must contain a visible collapsed material", Color.alpha(image.getPixel(80, 30)) > 0);
                    assertNoPixelsOutside("Collapsed glass must not draw an unclipped reflection", image,
                            new Rect(0, 8, SIZE, 54));
                } finally { image.recycle(); }
            } finally { fixture.restore(); }
        }
    }

    @Test public void rtlActualWidthClipsTheReflectionTogetherWithTheNotificationBody() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            fixture.background.mActualWidth = 90;
            fixture.background.mActualHeight = 80;
            try {
                fixture.apply();
                Bitmap image = draw(fixture.background);
                try {
                    assertTrue(Color.alpha(image.getPixel(110, 40)) > 0);
                    assertNoPixelsOutside("RTL glass follows the native actual width", image,
                            new Rect(70, 0, SIZE, 80));
                } finally { image.recycle(); }
            } finally { fixture.restore(); }
        }
    }

    @Test public void expansionUsesTheAnimatedCenteredBoundsForBothBodyAndReflection() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.mActualHeight = 40;
            fixture.background.mClipTopAmount = 30;
            fixture.background.mClipBottomAmount = 30;
            fixture.background.mExpandAnimationRunning = true;
            fixture.background.mExpandAnimationWidth = 70;
            fixture.background.mExpandAnimationHeight = 100;
            try {
                fixture.apply();
                Bitmap image = draw(fixture.background);
                try {
                    assertTrue("Expansion intentionally uses the animated height instead of collapsed clips",
                            Color.alpha(image.getPixel(80, 70)) > 0);
                    assertNoPixelsOutside("Animated glass must not leave a second full-width outline", image,
                            new Rect(45, 0, 115, 100));
                } finally { image.recycle(); }
            } finally { fixture.restore(); }
        }
    }

    @Test public void nativeFadeAppliesToTheReflectionAndReachesZeroWithTheBody() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.mActualHeight = 90;
            try {
                fixture.apply();
                Bitmap full = draw(fixture.background);
                try {
                    int fullPeak = maximumAlpha(full);
                    assertTrue("The fixture must have a visible material", fullPeak > 0);
                    fixture.background.mBackground.setAlpha(128);
                    NotificationSurfaceHooks.BoundedBackground bounded =
                            (NotificationSurfaceHooks.BoundedBackground) fixture.background.getBackground();
                    int contentAlpha = bounded.content.getAlpha();
                    Rect contentBounds = new Rect(bounded.content.getBounds());
                    Bitmap half = draw(fixture.background);
                    try {
                        assertTrue("Native fade must soften the complete glass edge, not leave an opaque reflection",
                                maximumAlpha(half) <= Math.round(fullPeak * .7f) + 1);
                    } finally { half.recycle(); }
                    assertEquals("Drawing restores the styled drawable alpha", contentAlpha, bounded.content.getAlpha());
                    assertEquals("Drawing restores the styled drawable bounds", contentBounds, bounded.content.getBounds());
                    assertEquals("Drawing never changes the native fade request", 128, fixture.background.mBackground.getAlpha());
                    fixture.background.mBackground.setAlpha(0);
                    Bitmap hidden = draw(fixture.background);
                    try { assertEquals("A fully faded notification must not retain a glass outline", 0, maximumAlpha(hidden)); }
                    finally { hidden.recycle(); }
                } finally { full.recycle(); }
            } finally { fixture.restore(); }
        }
    }

    @Test public void fullyClippedNotificationLeavesNoIndependentGlassOutline() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.mActualHeight = 40;
            fixture.background.mClipTopAmount = 25;
            fixture.background.mClipBottomAmount = 15;
            try {
                fixture.apply();
                Bitmap image = draw(fixture.background);
                try { assertEquals("Fully clipped glass must draw nothing, including its reflection", 0, maximumAlpha(image)); }
                finally { image.recycle(); }
            } finally { fixture.restore(); }
        }
    }

    @Test public void zeroStrengthAndDisablingRestoreNotificationBackgroundWithoutMovingItsContent() {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(night);
            fixture.background.mActualHeight = 90;
            try {
                fixture.apply();
                NotificationSurfaceHooks.applyRow(fixture.row, fixture.config.withControlCenter(
                        fixture.config.controlCenter.withBlur(100, 0, 100)));
                Bitmap transparent = draw(fixture.background);
                try { assertEquals("Zero card strength clears body and reflection together", 0, maximumAlpha(transparent)); }
                finally { transparent.recycle(); }
                assertSame(fixture.row, fixture.label.getParent());
                assertEquals("Notification title", fixture.label.getText().toString());
                assertEquals(1f, fixture.label.getAlpha(), 0f);
                assertEquals(20, fixture.label.getTop());
                fixture.restore();
                assertSame("Disabling recovers the original native background", fixture.original, fixture.background.getBackground());
                assertSame(fixture.original, fixture.background.mBackground);
                assertEquals("Original foreground styling restores", Color.GREEN, fixture.label.getCurrentTextColor());
            } finally { fixture.restore(); }
        }
    }

    private static void assertNoPixelsOutside(String message, Bitmap image, Rect allowed) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!allowed.contains(x, y)) assertEquals(message + " at " + x + "," + y, 0,
                        Color.alpha(image.getPixel(x, y)));
            }
        }
    }

    private static int maximumAlpha(Bitmap image) {
        int maximum = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) maximum = Math.max(maximum, Color.alpha(image.getPixel(x, y)));
        }
        return maximum;
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static final class Fixture {
        final NotificationRow row;
        final NotificationBackgroundView background;
        final GradientDrawable original;
        final TextView label;
        final FusionConfig config;
        Fixture(boolean night) {
            Context app = RuntimeEnvironment.getApplication();
            Configuration mode = new Configuration(app.getResources().getConfiguration());
            mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context context = app.createConfigurationContext(mode);
            row = new NotificationRow(context);
            background = row.mBackgroundNormal;
            original = new GradientDrawable();
            original.setColor(0xff353840);
            original.setCornerRadius(12f);
            background.mBackground = original;
            background.setBackground(original);
            label = new TextView(context);
            label.setText("Notification title");
            label.setTextColor(Color.GREEN);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(120, 30);
            labelParams.leftMargin = 20; labelParams.topMargin = 20;
            row.addView(label, labelParams);
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, SIZE);
            ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withCornerRadius(12)
                    .withBlur(100, 100, 100).withGlobalBorder(false, 1)
                    .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(true));
            config = FusionConfig.defaults().withControlCenter(settings);
        }
        void apply() { NotificationSurfaceHooks.applyRow(row, config); }
        void restore() {
            NotificationSurfaceHooks.applyRow(row, config.withControlCenter(config.controlCenter
                    .withSystemSurfaces(config.controlCenter.systemSurfaces.withNotifications(false))));
        }
    }

    public static final class NotificationRow extends FrameLayout {
        public final NotificationBackgroundView mBackgroundNormal;
        NotificationRow(Context context) {
            super(context);
            mBackgroundNormal = new NotificationBackgroundView(context);
            addView(mBackgroundNormal, new FrameLayout.LayoutParams(-1, -1));
        }
        public boolean isHeadsUpState() { return false; }
        public boolean isPinned() { return false; }
    }

    public static final class NotificationBackgroundView extends View {
        public int mActualHeight = -1, mActualWidth = -1;
        public int mExpandAnimationWidth = -1, mExpandAnimationHeight = -1;
        public int mClipTopAmount, mClipBottomAmount, mDrawableAlpha = 255;
        public boolean mExpandAnimationRunning, mBottomIsRounded, mBottomAmountClips = true;
        public Drawable mBackground;
        NotificationBackgroundView(Context context) { super(context); }
    }
}
