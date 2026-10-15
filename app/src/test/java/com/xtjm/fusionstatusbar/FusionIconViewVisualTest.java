package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionIconViewVisualTest {
    private static final int WIDTH = 271;
    private static final int HEIGHT = 282;
    private static final int GREEN = Color.rgb(68, 141, 70);
    private static final int GOLD = Color.rgb(246, 195, 75);
    private static final int AMBER = Color.rgb(191, 155, 39);
    private static final int RED = Color.rgb(240, 68, 61);

    @Test public void sixReferenceStatesKeepNetworkAndSignalsInTheSystemTint() throws Exception {
        StatusMonitor.Snapshot[] states = {
                state(91, true, "", true, false, false),
                state(91, true, "", true, true, false),
                state(64, true, "", false, false, false),
                state(79, false, "5G", false, false, false),
                state(24, true, "", false, false, true),
                state(12, true, "", false, false, false)
        };
        int[] ringColors = {GREEN, GREEN, Color.BLACK, Color.BLACK, AMBER, RED};
        for (int tint : new int[] {Color.BLACK, Color.WHITE}) {
            Bitmap[] icons = new Bitmap[states.length];
            try {
                for (int i = 0; i < states.length; i++) {
                    icons[i] = render(icon(states[i], tint, 100));
                    int ring = ringColors[i] == Color.BLACK ? tint : ringColors[i];
                    assertTrue("Battery state remains visible for sample " + i,
                            pixels(icons[i], 20, 160, 70, 212, ring) > 30);
                    assertTrue("Signal dots keep the system tint for sample " + i,
                            pixels(icons[i], 60, 205, 210, 281, tint) > 200);
                    if (states[i].showWifi) {
                        assertTrue("Wi-Fi keeps the system tint for sample " + i,
                                pixels(icons[i], 80, 15, 191, 88, tint) > 100);
                    }
                    if (!states[i].charging) {
                        assertTrue("Battery or network text keeps the system tint",
                                pixels(icons[i], 70, 100, 200, 198, tint) > 200);
                    }
                }
                assertEquals("Ordinary charging uses a monochrome bolt", 0,
                        pixels(icons[0], 80, 90, 190, 205, GOLD));
                assertTrue("Only fast charging adds the gold bolt",
                        pixels(icons[1], 80, 90, 190, 205, GOLD) > 400);
                saveMontage(icons, tint == Color.BLACK ? "reference-states-light.png"
                        : "reference-states-dark.png", tint);
            } finally {
                for (Bitmap bitmap : icons) if (bitmap != null) bitmap.recycle();
            }
        }
    }

    @Test public void lowBatteryProgressStartsAtTheBottomOfTheLeftArc() {
        for (StatusMonitor.Snapshot state : new StatusMonitor.Snapshot[] {
                state(12, true, "", false, false, false),
                state(24, true, "", false, false, true)}) {
            Bitmap bitmap = render(icon(state, Color.BLACK, 100));
            try {
                int color = state.powerSave ? AMBER : RED;
                assertTrue("Low charge must fill the lower left arc",
                        pixels(bitmap, 20, 165, 70, 214, color) > 50);
                assertEquals("The upper left arc remains unfilled at low charge", 0,
                        pixels(bitmap, 20, 65, 75, 110, color));
            } finally { bitmap.recycle(); }
        }
    }

    @Test public void mobileModeMovesBatteryAboveTheNetworkType() {
        Bitmap wifi = render(icon(state(79, true, "", false, false, false), Color.BLACK, 100));
        Bitmap mobile = render(icon(state(79, false, "5G", false, false, false), Color.BLACK, 100));
        try {
            assertEquals("The connected Wi-Fi icon leaves space above its arcs", 0,
                    pixels(wifi, 95, 5, 176, 22, Color.BLACK));
            assertTrue("Without Wi-Fi, the battery number occupies the header",
                    pixels(mobile, 95, 5, 176, 22, Color.BLACK) > 20);
            assertTrue("The mobile network type occupies the center",
                    pixels(mobile, 70, 110, 201, 195, Color.BLACK) > 300);
        } finally { wifi.recycle(); mobile.recycle(); }
    }

    @Test public void legacySamplesClearChargingAndPowerSaveState() {
        FusionIconView reused = icon(state(24, true, "", true, true, true), Color.BLACK, 100);
        reused.showSample(64, true, "", 4, 3, Color.BLACK);
        Bitmap actual = render(reused);
        Bitmap ordinary = render(icon(state(64, true, "", false, false, false), Color.BLACK, 100));
        try {
            assertTrue("The existing settings sample API restores ordinary rendering",
                    actual.sameAs(ordinary));
            assertFalse(reused.getContentDescription().toString().contains("充电"));
            assertFalse(reused.getContentDescription().toString().contains("快充"));
            assertFalse(reused.getContentDescription().toString().contains("省电"));
        } finally { actual.recycle(); ordinary.recycle(); }

        reused.showSample(state(24, true, "", false, false, true), Color.BLACK);
        reused.showSample(64, true, "", 4, Color.BLACK);
        actual = render(reused);
        ordinary = render(icon(new StatusMonitor.Snapshot(64, true, "", 4, 4, 4, false),
                Color.BLACK, 100));
        try {
            assertTrue("The single-signal overload also resets power save", actual.sameAs(ordinary));
            assertFalse(reused.getContentDescription().toString().contains("省电"));
        } finally { actual.recycle(); ordinary.recycle(); }
    }

    @Test public void maximumStrokeKeepsTheArtworkInsideItsViewAndBoltClearOfSignals() throws Exception {
        for (StatusMonitor.Snapshot state : new StatusMonitor.Snapshot[] {
                state(100, true, "", false, false, false),
                state(91, true, "", true, true, false),
                state(100, false, "5G", false, false, false)}) {
            Bitmap bitmap = render(icon(state, Color.BLACK, 150));
            try {
                for (int x = 0; x < WIDTH; x++) {
                    assertEquals("Artwork is clipped at the top", 0, Color.alpha(bitmap.getPixel(x, 0)));
                    assertEquals("Artwork is clipped at the bottom", 0, Color.alpha(bitmap.getPixel(x, HEIGHT - 1)));
                }
                for (int y = 0; y < HEIGHT; y++) {
                    assertEquals("Artwork is clipped at the left", 0, Color.alpha(bitmap.getPixel(0, y)));
                    assertEquals("Artwork is clipped at the right", 0, Color.alpha(bitmap.getPixel(WIDTH - 1, y)));
                }
                if (state.showWifi) {
                    assertEquals("Thick Wi-Fi arcs remain separate", 0,
                            opaquePixels(bitmap, 134, 40, 137, 41));
                    assertEquals("The Wi-Fi dot remains separate from its inner arc", 0,
                            opaquePixels(bitmap, 134, 61, 137, 62));
                }
                if (state.charging) {
                    assertEquals("The thick charging bolt leaves a gap above the signal dots", 0,
                            opaquePixels(bitmap, 108, 188, 162, 194));
                    Bitmap check = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                    try {
                        Canvas canvas = new Canvas(check);
                        canvas.drawColor(Color.rgb(245, 245, 248));
                        canvas.drawBitmap(bitmap, 0, 0, null);
                        save(check, "maximum-stroke-fast-charging.png");
                    } finally { check.recycle(); }
                }
            } finally { bitmap.recycle(); }
        }
    }

    private static StatusMonitor.Snapshot state(int battery, boolean wifi, String label,
            boolean charging, boolean fast, boolean powerSave) {
        return new StatusMonitor.Snapshot(battery, wifi, label, 4, 3, wifi ? 4 : 0,
                charging, fast, powerSave);
    }

    private static FusionIconView icon(StatusMonitor.Snapshot state, int tint, int stroke) {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        view.showSample(state, tint);
        FusionConfig config = FusionConfig.defaults().withStrokeScale(stroke);
        if (state.showWifi) {
            config = config.withFusionContentPosition(FusionContentConfig.NETWORK,
                    FusionContentConfig.TOP);
        }
        view.setPreviewConfig(config);
        return view;
    }

    private static Bitmap render(FusionIconView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int pixels(Bitmap bitmap, int left, int top, int right, int bottom, int color) {
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            int pixel = bitmap.getPixel(x, y);
            if (Color.alpha(pixel) >= 240 && (pixel & 0x00ffffff) == (color & 0x00ffffff)) count++;
        }
        return count;
    }

    private static int opaquePixels(Bitmap bitmap, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 16) count++;
        }
        return count;
    }

    private static void saveMontage(Bitmap[] icons, String name, int tint) throws Exception {
        int padding = 16, cellWidth = WIDTH + padding;
        Bitmap montage = Bitmap.createBitmap(cellWidth * icons.length + padding,
                HEIGHT + 68, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(montage);
            canvas.drawColor(tint == Color.BLACK ? Color.rgb(245, 245, 248) : Color.rgb(28, 36, 44));
            String[] labels = {"Charging", "Fast charging", "Normal", "No Wi-Fi", "Power save", "Danger"};
            Paint labelsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelsPaint.setColor(tint); labelsPaint.setTextSize(20f); labelsPaint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < icons.length; i++) {
                int x = padding + i * cellWidth;
                canvas.drawBitmap(icons[i], x, padding, null);
                canvas.drawText(labels[i], x + WIDTH / 2f, HEIGHT + 47, labelsPaint);
            }
            save(montage, name);
        } finally { montage.recycle(); }
    }

    private static void save(Bitmap bitmap, String name) throws Exception {
        File directory = new File("build/reports/fusion-icon");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
}
