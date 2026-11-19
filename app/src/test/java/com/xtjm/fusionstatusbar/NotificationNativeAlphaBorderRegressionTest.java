package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** The center tests native alpha once; the empty top band isolates the optional border. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationNativeAlphaBorderRegressionTest {
    private static final int SIZE = 160;

    @Test public void borderAndBodyFollowLiveHostAlphaAtEveryPositiveStrengthWithoutReapplying() {
        for (int strength : new int[] {1, 30, 100}) {
            for (boolean border : new boolean[] {false, true}) {
                try (Fixture fixture = new Fixture(strength, border, false)) {
                    for (int index = 0; index < 4; index++) {
                        int hostAlpha = new int[] {255, 128, 0, 255}[index];
                        int expectedBorder = border ? new int[] {128, 64, 0, 128}[index] : 0;
                        fixture.original.setAlpha(hostAlpha);
                        Bitmap image = fixture.draw();
                        try {
                            assertEquals("The native body must be faded once at strength " + strength,
                                    hostAlpha, Color.alpha(image.getPixel(80, 32)));
                            assertEquals("The independent border must follow the same live host fade",
                                    expectedBorder, borderPeak(image));
                            if (hostAlpha == 0) assertEquals(0, maximumAlpha(image));
                        } finally { image.recycle(); }
                        fixture.assertOriginalState(fixture.original, hostAlpha);
                    }
                    fixture.disable();
                    assertSame(fixture.original, fixture.background.getBackground());
                    fixture.assertOriginalState(fixture.original, 255);
                }
            }
        }
    }

    @Test public void wrapperAlphaMultipliesNativeBodyAndBorderOnceEach() {
        for (int strength : new int[] {30, 100}) {
            for (boolean border : new boolean[] {false, true}) {
                try (Fixture fixture = new Fixture(strength, border, false)) {
                    fixture.original.setAlpha(128);
                    fixture.background.getBackground().setAlpha(128);
                    Bitmap image = fixture.draw();
                    try {
                        assertEquals("Wrapper fade must not apply native alpha to the body twice", 64,
                                Color.alpha(image.getPixel(80, 32)));
                        assertEquals("Wrapper and host fade both apply to the added border", border ? 32 : 0,
                                borderPeak(image));
                    } finally { image.recycle(); }
                    assertEquals(128, fixture.background.getBackground().getAlpha());
                    fixture.assertOriginalState(fixture.original, 128);
                    fixture.original.setAlpha(0);
                    Bitmap hidden = fixture.draw();
                    try { assertEquals(0, maximumAlpha(hidden)); }
                    finally { hidden.recycle(); }
                    fixture.disable();
                    assertSame(fixture.original, fixture.background.getBackground());
                    fixture.assertOriginalState(fixture.original, 0);
                }
            }
        }
    }

    @Test public void anInternalRebindSuppliesTheLiveBorderFadeWithoutAnotherApply() {
        try (Fixture fixture = new Fixture(100, true, false)) {
            CenterDrawable replacement = new CenterDrawable();
            replacement.setBounds(fixture.originalBounds);
            replacement.setAlpha(128);
            fixture.background.mBackground = replacement;
            Bitmap image = fixture.draw();
            try {
                assertEquals(128, Color.alpha(image.getPixel(80, 32)));
                assertEquals(64, borderPeak(image));
            } finally { image.recycle(); }
            fixture.assertOriginalState(replacement, 128);
            fixture.assertOriginalState(fixture.original, 255);
            replacement.setAlpha(0);
            Bitmap hidden = fixture.draw();
            try { assertEquals(0, maximumAlpha(hidden)); }
            finally { hidden.recycle(); }
            fixture.disable();
            assertSame(replacement, fixture.background.getBackground());
            fixture.assertOriginalState(replacement, 0);
        }
    }

    @Test public void anExternalOriginalSuppliesFadeEvenWhenThePrivateDrawableHasAnotherAlpha() {
        try (Fixture fixture = new Fixture(30, true, true)) {
            fixture.original.setAlpha(128);
            Bitmap image = fixture.draw();
            try {
                assertEquals(128, Color.alpha(image.getPixel(80, 32)));
                assertEquals(64, borderPeak(image));
            } finally { image.recycle(); }
            assertEquals("The private drawable is deliberately stale", 255,
                    fixture.background.mBackground.getAlpha());
            fixture.original.setAlpha(0);
            Bitmap hidden = fixture.draw();
            try { assertEquals(0, maximumAlpha(hidden)); }
            finally { hidden.recycle(); }
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
            fixture.assertOriginalState(fixture.original, 0);
        }
    }

    @Test public void disablingAndReenablingUsesTheCurrentHostAlphaAndRestoresHostState() {
        try (Fixture fixture = new Fixture(100, true, false)) {
            fixture.original.setAlpha(0);
            Bitmap hidden = fixture.draw();
            try { assertEquals(0, maximumAlpha(hidden)); }
            finally { hidden.recycle(); }
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
            fixture.assertOriginalState(fixture.original, 0);
            fixture.original.setAlpha(128);
            NotificationSurfaceHooks.applyRow(fixture.row, fixture.settings);
            Bitmap visible = fixture.draw();
            try {
                assertEquals(128, Color.alpha(visible.getPixel(80, 32)));
                assertEquals(64, borderPeak(visible));
            } finally { visible.recycle(); }
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
            fixture.assertOriginalState(fixture.original, 128);
        }
    }

    private static int borderPeak(Bitmap image) {
        int peak = 0;
        for (int y = 0; y < 16; y++) for (int x = 24; x < SIZE - 24; x++)
            peak = Math.max(peak, Color.alpha(image.getPixel(x, y)));
        return peak;
    }

    private static int maximumAlpha(Bitmap image) {
        int peak = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            peak = Math.max(peak, Color.alpha(image.getPixel(x, y)));
        return peak;
    }

    private static final class Fixture implements AutoCloseable {
        final NotificationBlurFallbackTest.NotificationRow row;
        final NotificationBlurFallbackTest.NativeNotificationBackground background;
        final CenterDrawable original = new CenterDrawable();
        final Rect originalBounds = new Rect(7, 9, 31, 39);
        final FusionConfig settings;

        Fixture(int strength, boolean border, boolean externalOriginal) {
            Context context = RuntimeEnvironment.getApplication();
            row = new NotificationBlurFallbackTest.NotificationRow(context);
            background = row.mBackgroundNormal;
            original.setBounds(originalBounds);
            background.mBackground = externalOriginal ? new CenterDrawable() : original;
            background.setBackground(original);
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, SIZE);
            // Layout may size the host View.background before module ownership begins.
            original.setBounds(originalBounds);
            settings = FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(100, strength, 100)
                    .withCornerRadius(12).withGlobalBorder(border, 2));
            NotificationSurfaceHooks.applyRow(row, settings);
        }

        Bitmap draw() {
            Bitmap image = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Drawable mounted = background.getBackground();
            mounted.setBounds(0, 0, SIZE, SIZE);
            NotificationSurfaceHooks.BoundedBackground bounded = (NotificationSurfaceHooks.BoundedBackground) mounted;
            int savedAlpha = bounded.content.getAlpha();
            Rect savedBounds = new Rect(bounded.content.getBounds());
            mounted.draw(new Canvas(image));
            assertEquals("Drawing must restore material alpha", savedAlpha, bounded.content.getAlpha());
            assertEquals("Drawing must restore material bounds", savedBounds, bounded.content.getBounds());
            return image;
        }

        void assertOriginalState(Drawable drawable, int alpha) {
            assertEquals(alpha, drawable.getAlpha());
            assertEquals(originalBounds, drawable.getBounds());
        }

        void disable() {
            NotificationSurfaceHooks.applyRow(row, settings.withControlCenter(settings.controlCenter
                    .withSystemSurfaces(settings.controlCenter.systemSurfaces
                            .withNotifications(false).withHeadsUp(false))));
        }

        @Override public void close() { disable(); }
    }

    /** Empty edges isolate border alpha; opaque-center pixels independently catch double fading. */
    private static final class CenterDrawable extends Drawable {
        private final Paint paint = new Paint();
        private int alpha = 255;

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            paint.setColor(Color.RED);
            paint.setAlpha(alpha);
            canvas.drawRect(bounds.left + 24, bounds.top + 24, bounds.right - 24, bounds.bottom - 24, paint);
        }
        @Override public void setAlpha(int value) { alpha = value; }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
