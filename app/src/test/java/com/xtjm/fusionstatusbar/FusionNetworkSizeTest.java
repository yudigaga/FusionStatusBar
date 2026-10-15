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

import static org.junit.Assert.assertTrue;

/** Compare the actual visible artwork, rather than font sizes or layout allocation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionNetworkSizeTest {
    private static final int WIDTH = 271;
    private static final int HEIGHT = 282;
    private static final int TOLERANCE = 2;

    @Test public void default5GAndWifiHaveTheSameVisibleSize() throws Exception {
        String failure = compare(FusionContentConfig.MIDDLE, 100, 100, true);
        assertTrue(failure, failure.isEmpty());
    }

    @Test public void everyNetworkPositionAndStrokeKeeps5GTheSameSizeAsWifi() throws Exception {
        StringBuilder failures = new StringBuilder();
        for (int position : new int[] {FusionContentConfig.TOP,
                FusionContentConfig.MIDDLE, FusionContentConfig.BOTTOM}) {
            for (int stroke : new int[] {50, 100, 150}) {
                failures.append(compare(position, stroke, 100, stroke == 100));
            }
        }
        assertTrue(failures.toString(), failures.length() == 0);
    }

    @Test public void networkScalePreservesMatchedVisibleSizeInEverySlot() throws Exception {
        StringBuilder failures = new StringBuilder();
        for (int position : new int[] {FusionContentConfig.TOP,
                FusionContentConfig.MIDDLE, FusionContentConfig.BOTTOM}) {
            for (int stroke : new int[] {50, 100, 150}) {
                for (int scale : new int[] {50, 150}) {
                    failures.append(compare(position, stroke, scale, false));
                }
            }
        }
        assertTrue(failures.toString(), failures.length() == 0);
    }

    @Test public void reusedNetworkLabelsRefreshWithoutChangingOtherArtworkOrTint() {
        FusionIconView reused = new FusionIconView(RuntimeEnvironment.getApplication());
        String[] labels = {"5G", "4G+", "", "5G", "5G", "5G", "5G"};
        int[] strokes = {100, 100, 100, 100, 150, 100, 100};
        int previousStroke = -1;
        for (int i = 0; i < labels.length; i++) {
            int tint = i == labels.length - 1 ? Color.argb(128, 255, 255, 255) : Color.BLACK;
            FusionConfig config = FusionConfig.defaults().withStrokeScale(strokes[i]);
            // Leave config unchanged during label/tint-only changes, exercising cache invalidation.
            if (previousStroke != strokes[i]) reused.setPreviewConfig(config);
            previousStroke = strokes[i];
            reused.showSample(64, false, labels[i], 0, 3, 2, tint);
            FusionIconView fresh = new FusionIconView(RuntimeEnvironment.getApplication());
            fresh.setPreviewConfig(config);
            fresh.showSample(64, false, labels[i], 0, 3, 2, tint);
            Bitmap actual = render(reused);
            Bitmap expected = render(fresh);
            try {
                assertTrue("Reused view differs at step " + i + ": label=" + labels[i]
                                + ", stroke=" + strokes[i] + ", tint=" + tint,
                        actual.sameAs(expected));
            } finally { actual.recycle(); expected.recycle(); }
        }

        FusionConfig config = FusionConfig.defaults()
                .withFusionContentScale(FusionContentConfig.BATTERY, 50);
        Bitmap baseline = render(config, false, "");
        Bitmap fiveG = render(config, false, "5G");
        Bitmap shortLabel = render(config, false, "E");
        Bitmap longLabel = render(config, false, "4G+");
        try {
            int referenceWidth = visibleDifference(fiveG, baseline).width();
            assertTrue("A short network label keeps its naturally narrower shape",
                    visibleDifference(shortLabel, baseline).width() < referenceWidth);
            assertTrue("A long network label stays inside the same visible network frame",
                    visibleDifference(longLabel, baseline).width() <= referenceWidth + TOLERANCE);
        } finally {
            baseline.recycle();
            fiveG.recycle();
            shortLabel.recycle();
            longLabel.recycle();
        }
    }

    private static String compare(int position, int stroke, int scale, boolean save)
            throws Exception {
        FusionConfig config = FusionConfig.defaults()
                .withStrokeScale(stroke)
                .withFusionContentPosition(FusionContentConfig.NETWORK, position)
                .withFusionContentScale(FusionContentConfig.BATTERY, 50)
                .withFusionContentScale(FusionContentConfig.NETWORK, scale);
        Bitmap wifi = render(config.withWifiIcon(true), true, "");
        Bitmap wifiBaseline = render(config.withWifiIcon(false), true, "");
        Bitmap mobile = render(config, false, "5G");
        Bitmap mobileBaseline = render(config, false, "");
        try {
            Rect wifiBounds = visibleDifference(wifi, wifiBaseline);
            Rect mobileBounds = visibleDifference(mobile, mobileBaseline);
            String measurements = "slot=" + position + ", stroke=" + stroke + ", scale=" + scale
                    + ": Wi-Fi=" + describe(wifiBounds) + ", 5G=" + describe(mobileBounds);
            System.out.println(measurements);
            if (save) saveComparison(wifi, mobile, wifiBounds, mobileBounds, position);
            if (Math.abs(wifiBounds.width() - mobileBounds.width()) <= TOLERANCE
                    && Math.abs(wifiBounds.height() - mobileBounds.height()) <= TOLERANCE) {
                return "";
            }
            return measurements + " (visible width and height must differ by at most "
                    + TOLERANCE + " px)\n";
        } finally {
            wifi.recycle();
            wifiBaseline.recycle();
            mobile.recycle();
            mobileBaseline.recycle();
        }
    }

    private static Bitmap render(FusionConfig config, boolean wifi, String label) {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        // Keep shared battery/signal artwork faint and out of the network measurement.
        view.showSample(0, wifi, label, wifi ? 4 : 0, 0, 0, Color.BLACK);
        view.setPreviewConfig(config);
        return render(view);
    }

    private static Bitmap render(FusionIconView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Rect visibleDifference(Bitmap artwork, Bitmap baseline) {
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
        assertTrue("Network artwork must contain visible native pixels", !bounds.isEmpty());
        return bounds;
    }

    private static String describe(Rect bounds) {
        return bounds.width() + "x" + bounds.height() + " [" + bounds.left + "," + bounds.top
                + ".." + bounds.right + "," + bounds.bottom + "]";
    }

    private static void saveComparison(Bitmap wifi, Bitmap mobile, Rect wifiBounds,
            Rect mobileBounds, int position) throws Exception {
        int padding = 20;
        int cellWidth = WIDTH + padding;
        Bitmap comparison = Bitmap.createBitmap(cellWidth * 2 + padding, HEIGHT + 90,
                Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(comparison);
            canvas.drawColor(Color.rgb(245, 245, 248));
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setTextSize(17f);
            label.setColor(Color.BLACK);
            Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
            outline.setColor(Color.rgb(0, 105, 205));
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(1f);
            Bitmap[] artwork = {wifi, mobile};
            Rect[] bounds = {wifiBounds, mobileBounds};
            String[] names = {"Wi-Fi", "5G"};
            for (int i = 0; i < artwork.length; i++) {
                int left = padding + i * cellWidth;
                canvas.drawText(names[i] + " / slot " + position, left, 25, label);
                canvas.drawBitmap(artwork[i], left, 40, null);
                canvas.save();
                canvas.translate(left, 40);
                canvas.drawRect(bounds[i], outline);
                canvas.restore();
                canvas.drawText(describe(bounds[i]), left, HEIGHT + 70, label);
            }
            File directory = new File("build/reports/fusion-icon");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            File output = new File(directory, "network-size-slot-" + position + ".png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
        } finally { comparison.recycle(); }
    }
}
