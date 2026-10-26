package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BackGestureRendererTest {
    @Test public void allPresetsHaveDistinctShapesAndMotionCurves() {
        Set<Integer> shapes = new HashSet<>();
        Set<Float> responses = new HashSet<>();
        for (int style = 1; style <= 4; style++) {
            Bitmap bitmap = draw(style, Color.BLUE, Color.WHITE, false, .65f);
            shapes.add(Arrays.hashCode(pixels(bitmap)));
            responses.add(BackGestureRenderer.response(style, .45f));
            assertTrue(nonTransparent(bitmap) > 100);
            assertTrue(nonTransparent(bitmap) < bitmap.getWidth() * bitmap.getHeight() / 2);
        }
        assertEquals(4, shapes.size());
        assertEquals(4, responses.size());
    }

    @Test public void sidesMirrorWithTheArrowPointingInward() {
        for (int style = 1; style <= 4; style++) {
            Bitmap left = draw(style, Color.BLUE, Color.WHITE, false, .8f);
            Bitmap right = draw(style, Color.BLUE, Color.WHITE, true, .8f);
            assertMirrored("style=" + style, pixels(left), right);
        }
    }

    @Test public void customColorsAndAlphaReachRenderedPixels() {
        Bitmap bitmap = draw(DesktopConfig.BACK_STYLE_CAPSULE, 0x803366CC, 0xFFE04060, false, 1f);
        boolean background = false;
        boolean arrow = false;
        for (int pixel : pixels(bitmap)) {
            if (Color.alpha(pixel) == 128 && Math.abs(Color.red(pixel) - 51) <= 1
                    && Math.abs(Color.green(pixel) - 102) <= 1
                    && Math.abs(Color.blue(pixel) - 204) <= 1) background = true;
            if (pixel == 0xFFE04060) arrow = true;
        }
        assertTrue("Background keeps its chosen alpha", background);
        assertTrue("Arrow uses its independent color", arrow);
        assertEquals(0, nonTransparent(draw(DesktopConfig.BACK_STYLE_CAPSULE,
                Color.TRANSPARENT, Color.TRANSPARENT, false, 1f)));
    }

    @Test public void zeroAndInvalidProgressLeaveCanvasUntouched() {
        assertEquals(0, nonTransparent(draw(1, Color.BLUE, Color.WHITE, false, 0f)));
        assertEquals(0, nonTransparent(draw(1, Color.BLUE, Color.WHITE, false, Float.NaN)));
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect clip = canvas.getClipBounds();
        int saves = canvas.getSaveCount();
        new BackGestureRenderer().draw(canvas, 2, Color.BLUE, Color.WHITE, true,
                200, 100, 40, 160, 1f, 1f, 1f);
        assertEquals(saves, canvas.getSaveCount());
        assertEquals(clip, canvas.getClipBounds());
    }

    @Test public void renderPresetContactSheet() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFF3F5F9);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFF252830);
        text.setTextSize(28);
        canvas.drawText("Side-back presets", 34, 46, text);
        String[] names = {"Classic droplet", "Floating capsule", "Soft ripple", "Neon halo"};
        BackGestureRenderer renderer = new BackGestureRenderer();
        for (int style = 1; style <= 4; style++) {
            float top = 70 + (style - 1) * 180;
            text.setColor(Color.WHITE);
            canvas.drawRoundRect(22, top, 978, top + 166, 20, 20, text);
            text.setColor(0xFF252830);
            text.setTextSize(24);
            canvas.drawText(names[style - 1], 120, top + 38, text);
            int background = DesktopConfig.backGesturePresetBackground(style);
            int arrow = DesktopConfig.backGesturePresetArrow(style);
            text.setTextSize(18);
            text.setColor(0xFF78808E);
            canvas.drawText("Pull 35%", 250, top + 136, text);
            canvas.drawText("Pull 70%", 520, top + 136, text);
            canvas.drawText("Ready", 800, top + 136, text);
            float[] progress = {.35f, .7f, 1f};
            for (int i = 0; i < progress.length; i++) {
                float p = progress[i];
                float edge = 245 + i * 270;
                renderer.draw(canvas, style, background, arrow, false,
                        edge, top + 85, 46 * p, 138, p, i == 0 ? 0 : 1, 1.2f);
            }
            renderer.draw(canvas, style, background, arrow, false,
                    22, top + 85, 55, 150, 1f, 1f, 1.2f);
            renderer.draw(canvas, style, background, arrow, true,
                    978, top + 85, 55, 150, 1f, 1f, 1.2f);
        }
        File output = new File("build/reports/back-gesture-presets.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }

    private static Bitmap draw(int style, int background, int arrow, boolean right, float scale) {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        new BackGestureRenderer().draw(new Canvas(bitmap), style, background, arrow, right,
                right ? 200 : 0, 100, 44 * scale, 160, scale, 1f, 1f);
        return bitmap;
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] values = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(values, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return values;
    }

    private static int nonTransparent(Bitmap bitmap) {
        int count = 0;
        for (int pixel : pixels(bitmap)) if (Color.alpha(pixel) != 0) count++;
        return count;
    }

    static void assertMirrored(String label, int[] expected, Bitmap actual) {
        double error = 0;
        long firstCoverage = 0;
        long secondCoverage = 0;
        int occupied = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int first = expected[y * actual.getWidth() + x];
                int second = actual.getPixel(actual.getWidth() - x - 1, y);
                int firstAlpha = Color.alpha(first);
                int secondAlpha = Color.alpha(second);
                if (firstAlpha == 0 && secondAlpha == 0) continue;
                occupied++;
                firstCoverage += firstAlpha;
                secondCoverage += secondAlpha;
                error += Math.abs(firstAlpha - secondAlpha);
                for (int shift : new int[] {0, 8, 16}) {
                    error += Math.abs(((first >> shift) & 255) * firstAlpha
                            - ((second >> shift) & 255) * secondAlpha) / 255.0;
                }
            }
        }
        // Reflected Bezier edges use different Skia tessellation and coverage rounding.
        // Compare the occupied shape, rather than demanding identical individual edge pixels.
        double mean = error / Math.max(1, occupied * 4);
        double coverageDifference = Math.abs(firstCoverage - secondCoverage)
                / (double) Math.max(1, firstCoverage);
        System.out.println(label + " mirrored mean pixel error=" + mean
                + " coverage difference=" + coverageDifference);
        assertTrue(label + " mirrored pixel error=" + mean, mean <= 3.0);
        assertTrue(label + " mirrored coverage=" + coverageDifference, coverageDifference < .03);
    }
}
