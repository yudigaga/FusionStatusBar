package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import com.materialkolor.hct.Hct;

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
public class BackGestureRainbowTest {
    @Test public void spectrumHasSixDifferentHuesAndASeamlessEndWithSeedAlpha() {
        for (boolean night : new boolean[] {false, true}) {
            int[] colors = BackGesturePalette.rainbow(0x804F6BEA, night, false);
            assertEquals(7, colors.length);
            assertEquals(colors[0], colors[6]);
            Set<Integer> hues = new HashSet<>();
            for (int index = 0; index < 6; index++) {
                assertEquals(128, Color.alpha(colors[index]));
                hues.add((int) Math.round(Hct.Companion.fromInt(colors[index]).getHue() / 15));
            }
            assertEquals(6, hues.size());
        }
    }

    @Test public void correspondingColorsFollowTheSameFamilyAndKeepOpaqueArrowReadable() {
        int[] day = BackGesturePalette.rainbow(0xFF4F6BEA, false, false);
        int[] night = BackGesturePalette.rainbow(0xFF4F6BEA, true, false);
        BackGesturePalette dayPalette = BackGesturePalette.resolve(0xFF4F6BEA, Color.WHITE, false);
        BackGesturePalette nightPalette = BackGesturePalette.resolve(0xFF4F6BEA, Color.WHITE, true);
        for (int index = 0; index < 6; index++) {
            double delta = Math.abs(Hct.Companion.fromInt(day[index]).getHue()
                    - Hct.Companion.fromInt(night[index]).getHue());
            assertTrue(Math.min(delta, 360 - delta) < 8);
            assertTrue(ColorUtils.calculateContrast(dayPalette.arrow, day[index]) >= 7);
            assertTrue(ColorUtils.calculateContrast(nightPalette.arrow, night[index]) >= 7);
        }
    }

    @Test public void actualFillVariesSmoothlyAndArrowKeepsItsOwnColor() {
        Bitmap image = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888);
        new BackGestureRenderer().drawThemed(new Canvas(image), DesktopConfig.BACK_STYLE_RAINBOW,
                0xFF4F6BEA, Color.RED, false, 0, 100, 48, 160, 1, 1, 1, false);
        int glyph = BackGesturePalette.resolve(0xFF4F6BEA, Color.RED, false).arrow;
        Set<Integer> colors = new HashSet<>();
        int glyphPixels = 0;
        for (int y = 0; y < 200; y++) for (int x = 0; x < 160; x++) {
            int color = image.getPixel(x, y);
            if (Color.alpha(color) > 100) colors.add(color & 0xFFFFFF);
            if (color == glyph) glyphPixels++;
        }
        assertTrue("Gradient must contain many actual colors", colors.size() > 24);
        assertTrue("The arrow must not inherit the background shader", glyphPixels > 5);
        Bitmap noArrow = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888);
        new BackGestureRenderer().drawThemed(new Canvas(noArrow), DesktopConfig.BACK_STYLE_RAINBOW,
                0xFF4F6BEA, Color.RED, false, 0, 100, 48, 160, 1, 0, 1, false);
        for (int x = 16; x < 46; x++) {
            int first = noArrow.getPixel(x, 100);
            int second = noArrow.getPixel(x + 1, 100);
            assertTrue(Math.abs(Color.red(first) - Color.red(second)) < 24);
            assertTrue(Math.abs(Color.green(first) - Color.green(second)) < 24);
            assertTrue(Math.abs(Color.blue(first) - Color.blue(second)) < 24);
        }
    }

    @Test public void rawGradientAppliesAlphaOnceAndDoesNotLeakIntoTheNextPreset() {
        Bitmap image = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888);
        BackGestureRenderer renderer = new BackGestureRenderer();
        renderer.draw(new Canvas(image), DesktopConfig.BACK_STYLE_RAINBOW,
                0x804F6BEA, Color.WHITE, false, 0, 100, 48, 160, 1, 0, 1);
        int expectedAlphaPixels = 0;
        for (int y = 0; y < 200; y++) for (int x = 0; x < 160; x++) {
            if (Color.alpha(image.getPixel(x, y)) == 128) expectedAlphaPixels++;
        }
        assertTrue(expectedAlphaPixels > 20);
        image.eraseColor(Color.TRANSPARENT);
        renderer.drawThemed(new Canvas(image), DesktopConfig.BACK_STYLE_CAPSULE,
                0xFF4F6BEA, Color.WHITE, false, 0, 100, 48, 160, 1, 0, 1, false);
        int solid = BackGesturePalette.resolve(0xFF4F6BEA, Color.WHITE, false).background;
        assertEquals(solid, image.getPixel(25, 100));
        assertEquals(solid, image.getPixel(35, 100));
    }
}
