package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BackGestureAppearanceVisualTest {
    @Test public void allPresetsRemainCompactWhenTheNativeBackgroundIsVeryTall() {
        for (int style = 1; style <= DesktopConfig.BACK_STYLE_RAINBOW; style++) {
            Bitmap image = Bitmap.createBitmap(200, 700, Bitmap.Config.ARGB_8888);
            new BackGestureRenderer().drawThemed(new Canvas(image), style, 0xFF3267D6,
                    Color.WHITE, false, 0, 350, 48, 600, .9f, 1, 1, false);
            int top = 700;
            int bottom = -1;
            for (int y = 0; y < 700; y++) for (int x = 0; x < 200; x++) {
                if (Color.alpha(image.getPixel(x, y)) != 0) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            assertTrue("Preset should render", bottom >= top);
            assertTrue("Large native height must not create an oversized slab: " + style,
                    bottom - top < 80);
        }
    }

    @Test public void fullyTransparentSeedsDoNotLeaveShadowsOrRings() {
        for (int style = 1; style <= DesktopConfig.BACK_STYLE_RAINBOW; style++) {
            Bitmap image = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888);
            new BackGestureRenderer().drawThemed(new Canvas(image), style, 0x003267D6,
                    0x00FFFFFF, false, 0, 100, 48, 160, 1f, 1, 1, true);
            for (int y = 0; y < 200; y++) for (int x = 0; x < 160; x++) {
                assertEquals(0, Color.alpha(image.getPixel(x, y)));
            }
        }
    }

    @Test public void renderAdaptiveDayAndNightPresets() throws Exception {
        Bitmap image = Bitmap.createBitmap(1280, 1295, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(0xFFF0F2F6);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFF252A34);
        text.setTextSize(32);
        canvas.drawText("Same seed colors / Adaptive light and dark", 30, 45, text);
        text.setTextSize(18);
        canvas.drawText("Pull progress drives the shape. Native back cleanup is retained.", 30, 75, text);
        String[] labels = {"Fluid leaf", "Floating capsule", "Arc ripple", "Soft neon", "Rainbow gradient"};
        BackGestureRenderer renderer = new BackGestureRenderer();
        for (int style = 1; style <= DesktopConfig.BACK_STYLE_RAINBOW; style++) {
            float row = 100 + (style - 1) * 235;
            text.setTextSize(23);
            text.setColor(0xFF303541);
            canvas.drawText(style + " / " + labels[style - 1], 30, row + 26, text);
            for (int mode = 0; mode < 2; mode++) {
                boolean night = mode == 1;
                float left = 20 + mode * 640;
                float right = left + 600;
                text.setColor(night ? 0xFF171920 : 0xFFFCFCFE);
                canvas.drawRoundRect(left, row + 42, right, row + 218, 18, 18, text);
                text.setColor(night ? 0xFFD9DFEC : 0xFF4E5766);
                text.setTextSize(18);
                canvas.drawText(night ? "NIGHT" : "DAY", left + 20, row + 70, text);
                int save = canvas.save();
                canvas.clipRect(left, row + 42, right, row + 218);
                for (int phase = 0; phase < 3; phase++) {
                    float progress = new float[] {.35f, .7f, 1f}[phase];
                    float edge = left + 130 + phase * 155;
                    renderer.drawThemed(canvas, style,
                            DesktopConfig.backGesturePresetBackground(style),
                            DesktopConfig.backGesturePresetArrow(style), false,
                            edge, row + 126, 48 * progress, 140,
                            progress, phase == 0 ? 0 : 1, 1.25f, night);
                    canvas.drawText(new String[] {"Pull", "Drag", "Ready"}[phase],
                            edge, row + 193, text);
                }
                renderer.drawThemed(canvas, style, DesktopConfig.backGesturePresetBackground(style),
                        DesktopConfig.backGesturePresetArrow(style), true, right, row + 126,
                        48, 140, 1f, 1f, 1.25f, night);
                canvas.restoreToCount(save);
            }
        }
        File output = new File("build/reports/back-gesture-adaptive-presets.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
