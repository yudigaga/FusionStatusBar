package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Check rendered ink so network offsets cannot silently resize or move other artwork. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionNetworkOffsetTest {
    private static final int WIDTH = 271;
    private static final int HEIGHT = 282;

    @Test public void wifiAndMobileOffsetsAreIndependentAndLeaveOtherArtworkUnchanged() {
        FusionConfig config = middleConfig(100);
        for (boolean wifi : new boolean[] {true, false}) {
            FusionConfig moved = offset(config, wifi, 5);
            FusionConfig inactive = offset(config, !wifi, 5);
            FusionIconView reused = icon(config, wifi, true);
            Bitmap original = render(reused);
            Bitmap inactiveChanged = render(inactive, wifi, true);
            Bitmap noNetwork = render(config, wifi, false);
            Bitmap noNetworkMoved = render(moved, wifi, false);
            reused.setPreviewConfig(moved);
            Bitmap movedArtwork = render(reused);
            reused.setPreviewConfig(config);
            Bitmap restored = render(reused);
            try {
                String mode = wifi ? "Wi-Fi" : "5G";
                assertTrue(mode + " ignores the other mode's offset",
                        original.sameAs(inactiveChanged));
                assertFalse(mode + " responds immediately on the existing preview view",
                        original.sameAs(movedArtwork));
                assertTrue("Battery text, outer ring and both signal rows stay pixel-identical",
                        noNetwork.sameAs(noNetworkMoved));
                assertTrue(mode + " returns to its original pixels when reset to zero",
                        original.sameAs(restored));
            } finally {
                recycle(original, inactiveChanged, noNetwork, noNetworkMoved,
                        movedArtwork, restored);
            }
        }
    }

    @Test public void middleOffsetsMoveVisibleInkWithoutResizingAtEveryNetworkScale()
            throws Exception {
        for (boolean wifi : new boolean[] {true, false}) {
            for (int scale : new int[] {50, 100, 150}) {
                FusionConfig config = middleConfig(scale);
                Bitmap baseline = render(config, wifi, false);
                Bitmap original = render(config, wifi, true);
                try {
                    Rect originalBounds = networkBounds(original, baseline);
                    for (int percent : new int[] {-5, 5}) {
                        Bitmap moved = render(offset(config, wifi, percent), wifi, true);
                        try {
                            Rect movedBounds = networkBounds(moved, baseline);
                            String sample = (wifi ? "Wi-Fi" : "5G") + ", scale=" + scale
                                    + ", offset=" + percent + "%";
                            // At this view size, 5% of the whole icon is 14.1 rendered pixels.
                            assertEquals(sample + " vertical displacement", percent * 2.82f,
                                    movedBounds.exactCenterY() - originalBounds.exactCenterY(), 1f);
                            assertEquals(sample + " horizontal anchor",
                                    originalBounds.exactCenterX(), movedBounds.exactCenterX(), 1f);
                            assertEquals(sample + " visible width", originalBounds.width(),
                                    movedBounds.width(), 1f);
                            assertEquals(sample + " visible height", originalBounds.height(),
                                    movedBounds.height(), 1f);
                        } finally { moved.recycle(); }
                    }
                } finally { recycle(baseline, original); }
            }
        }
        saveComparison();
    }

    @Test public void extremeOffsetsKeepThickLargeNetworksInsideEverySlot() {
        for (int position : new int[] {FusionContentConfig.TOP,
                FusionContentConfig.MIDDLE, FusionContentConfig.BOTTOM}) {
            FusionConfig config = FusionConfig.defaults().withStrokeScale(150)
                    .withFusionContentPosition(FusionContentConfig.NETWORK, position)
                    .withFusionContentScale(FusionContentConfig.BATTERY, 50)
                    .withFusionContentScale(FusionContentConfig.NETWORK, 150);
            for (boolean wifi : new boolean[] {true, false}) {
                for (int percent : new int[] {-20, 20}) {
                    Bitmap artwork = render(offset(config, wifi, percent), wifi, true);
                    try {
                        String sample = (wifi ? "Wi-Fi" : "5G") + ", slot=" + position
                                + ", offset=" + percent + "%";
                        for (int x = 0; x < WIDTH; x++) {
                            assertEquals(sample + " top clipping", 0,
                                    Color.alpha(artwork.getPixel(x, 0)));
                            assertEquals(sample + " bottom clipping", 0,
                                    Color.alpha(artwork.getPixel(x, HEIGHT - 1)));
                        }
                        for (int y = 0; y < HEIGHT; y++) {
                            assertEquals(sample + " left clipping", 0,
                                    Color.alpha(artwork.getPixel(0, y)));
                            assertEquals(sample + " right clipping", 0,
                                    Color.alpha(artwork.getPixel(WIDTH - 1, y)));
                        }
                    } finally { artwork.recycle(); }
                }
            }
        }
    }

    private static FusionConfig middleConfig(int networkScale) {
        return FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.NETWORK, FusionContentConfig.MIDDLE)
                .withFusionContentScale(FusionContentConfig.BATTERY, 50)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 50)
                .withFusionContentScale(FusionContentConfig.NETWORK, networkScale);
    }

    private static FusionConfig offset(FusionConfig config, boolean wifi, int percent) {
        return wifi ? config.withFusionWifiOffsetYPercent(percent)
                : config.withFusionMobileOffsetYPercent(percent);
    }

    private static FusionIconView icon(FusionConfig config, boolean wifi, boolean network) {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        view.showSample(64, wifi, !wifi && network ? "5G" : "", wifi ? 4 : 0,
                3, 2, Color.BLACK);
        view.setPreviewConfig(config.withWifiIcon(network));
        return view;
    }

    private static Bitmap render(FusionConfig config, boolean wifi, boolean network) {
        return render(icon(config, wifi, network));
    }

    private static Bitmap render(FusionIconView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Rect networkBounds(Bitmap artwork, Bitmap baseline) {
        Rect bounds = new Rect(WIDTH, HEIGHT, 0, 0);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int pixel = artwork.getPixel(x, y);
                if (Color.alpha(pixel) >= 192 && pixel != baseline.getPixel(x, y)) {
                    bounds.left = Math.min(bounds.left, x);
                    bounds.top = Math.min(bounds.top, y);
                    bounds.right = Math.max(bounds.right, x + 1);
                    bounds.bottom = Math.max(bounds.bottom, y + 1);
                }
            }
        }
        assertFalse("Network artwork must contain visible native pixels", bounds.isEmpty());
        return bounds;
    }

    private static void saveComparison() throws Exception {
        int padding = 16;
        int cellWidth = WIDTH + padding;
        int cellHeight = HEIGHT + 35;
        Bitmap comparison = Bitmap.createBitmap(cellWidth * 3 + padding,
                cellHeight * 2 + padding, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(comparison);
            canvas.drawColor(Color.rgb(245, 245, 248));
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(Color.BLACK);
            label.setTextSize(18f);
            for (int row = 0; row < 2; row++) {
                boolean wifi = row == 0;
                for (int col = 0; col < 3; col++) {
                    int percent = (col - 1) * 5;
                    Bitmap artwork = render(offset(middleConfig(100), wifi, percent), wifi, true);
                    try {
                        int x = padding + col * cellWidth;
                        int y = padding + row * cellHeight;
                        canvas.drawText((wifi ? "Wi-Fi" : "5G") + ": "
                                + (percent > 0 ? "+" : "") + percent + "%", x, y + 20, label);
                        canvas.drawBitmap(artwork, x, y + 30, null);
                    } finally { artwork.recycle(); }
                }
            }
            File directory = new File("build/reports/fusion-icon");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(
                    new File(directory, "network-offset-middle.png"))) {
                assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
        } finally { comparison.recycle(); }
    }

    private static void recycle(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) bitmap.recycle();
    }
}
