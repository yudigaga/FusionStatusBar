package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Canvas material reference only; this does not simulate the OEM blur or native bloom. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterHighlightGlassVisualTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void inactivePreviewTransmitsWallpaperAndKeepsWhiteContentInBothModes() throws Exception {
        ControlCenterCardView day = card(context(false), ControlCenterComponentSpec.FIVE_G,
                "5G", ControlCenterLayoutPlan.Shape.CIRCLE, false, false);
        ControlCenterCardView night = card(context(true), ControlCenterComponentSpec.FIVE_G,
                "5G", ControlCenterLayoutPlan.Shape.CIRCLE, false, false);
        TextView dayLabel = (TextView) ((ViewGroup) day.getChildAt(0)).getChildAt(0);
        TextView nightLabel = (TextView) ((ViewGroup) night.getChildAt(0)).getChildAt(0);
        assertEquals(Color.WHITE, dayLabel.getCurrentTextColor());
        assertEquals(Color.WHITE, nightLabel.getCurrentTextColor());

        Bitmap transparent = render(day, 96, 96, Color.TRANSPARENT);
        Bitmap dayRed = render(day, 96, 96, Color.RED);
        Bitmap dayBlue = render(day, 96, 96, Color.BLUE);
        Bitmap dayWhite = render(day, 96, 96, Color.WHITE);
        Bitmap nightWhite = render(night, 96, 96, Color.WHITE);
        try {
            assertEquals("The circle must not paint into its outer corners", 0,
                    Color.alpha(transparent.getPixel(1, 1)));
            int bodyAlpha = Color.alpha(transparent.getPixel(48, 12));
            assertTrue("Day glass should leave the wallpaper almost fully visible", bodyAlpha > 0 && bodyAlpha < 51);
            assertTrue("Colored wallpaper must survive the glass without a gray wash",
                    Color.red(dayRed.getPixel(48, 12)) - Color.red(dayBlue.getPixel(48, 12)) > 204);
            assertTrue(Color.blue(dayBlue.getPixel(48, 12)) - Color.blue(dayRed.getPixel(48, 12)) > 204);
            assertTrue("A neutral light wallpaper must stay light",
                    Color.red(dayWhite.getPixel(48, 12)) >= 245);
            assertTrue("Night mode darkens the same backdrop while retaining the white content",
                    Color.luminance(nightWhite.getPixel(48, 12))
                            < Color.luminance(dayWhite.getPixel(48, 12)) - 0.3f);
        } finally {
            transparent.recycle(); dayRed.recycle(); dayBlue.recycle();
            dayWhite.recycle(); nightWhite.recycle();
        }
        writeComparison();
    }

    private static void writeComparison() throws Exception {
        Bitmap preview = Bitmap.createBitmap(1180, 900, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(preview);
            canvas.drawColor(0xff17191d);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.WHITE);
            text.setTextSize(25f);
            canvas.drawText("Transparent day glass", 30, 43, text);
            canvas.drawText("Dark night glass", 620, 43, text);
            drawScene(canvas, context(false), 20, 68);
            drawScene(canvas, context(true), 610, 68);
            text.setTextSize(17f);
            text.setColor(0xffb9bfc9);
            canvas.drawText("Canvas preview with a synthetic soft backdrop; OEM blur and bloom require a device.",
                    30, 878, text);
            File base = new File(System.getProperty("user.dir"));
            File app = base.getName().equals("app") ? base : new File(base, "app");
            File directory = new File(app, "build/reports/highlight-glass");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            File target = new File(directory, "day-night.png");
            try (FileOutputStream output = new FileOutputStream(target)) {
                assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
        } finally { preview.recycle(); }
    }

    private static void drawScene(Canvas canvas, Context context, int left, int top) {
        int saved = canvas.save();
        canvas.translate(left, top);
        canvas.clipRect(0, 0, 550, 770);
        Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG);
        backdrop.setShader(new LinearGradient(0, 0, 550, 770,
                new int[] {0xff626970, 0xff465054, 0xff343940}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 550, 770, backdrop);
        softColor(canvas, 70, 170, 260, 0xff7b894f);
        softColor(canvas, 495, 295, 260, 0xff9a775c);
        softColor(canvas, 220, 710, 250, 0xff4b8191);
        softColor(canvas, 425, 605, 210, 0xff776588);
        Paint heading = new Paint(Paint.ANTI_ALIAS_FLAG);
        heading.setColor(Color.WHITE);
        heading.setTextSize(18f);
        canvas.drawText("10/25  Sunday", 30, 39, heading);
        heading.setTextSize(40f);
        canvas.drawText("16:38", 30, 96, heading);

        drawCard(canvas, card(context, "wifi", "WLAN", ControlCenterLayoutPlan.Shape.CAPSULE, false, true),
                28, 132, 226, 100);
        drawCard(canvas, card(context, "bt", "Bluetooth", ControlCenterLayoutPlan.Shape.CAPSULE, false, true),
                28, 256, 226, 100);
        drawCard(canvas, card(context, ControlCenterComponentSpec.MEDIA, "Media",
                ControlCenterLayoutPlan.Shape.RECTANGLE, false, true), 278, 132, 244, 224);
        drawCard(canvas, card(context, "cell", "Mobile data", ControlCenterLayoutPlan.Shape.CAPSULE, false, true),
                28, 380, 226, 100);
        drawCard(canvas, card(context, ControlCenterComponentSpec.BRIGHTNESS, "Brightness",
                ControlCenterLayoutPlan.Shape.CAPSULE, false, true), 286, 380, 102, 224);
        drawCard(canvas, card(context, ControlCenterComponentSpec.VOLUME, "Volume",
                ControlCenterLayoutPlan.Shape.CAPSULE, false, true), 414, 380, 102, 224);
        drawCard(canvas, card(context, "dnd", "DND", ControlCenterLayoutPlan.Shape.CIRCLE, false, false),
                30, 516, 100, 100);
        drawCard(canvas, card(context, "flashlight", "Flashlight", ControlCenterLayoutPlan.Shape.CIRCLE, false, false),
                154, 516, 100, 100);
        drawCard(canvas, card(context, "location", "Location", ControlCenterLayoutPlan.Shape.CIRCLE, true, false),
                30, 640, 100, 100);
        drawCard(canvas, card(context, ControlCenterComponentSpec.FIVE_G, "5G",
                ControlCenterLayoutPlan.Shape.CIRCLE, false, false), 154, 640, 100, 100);
        drawCard(canvas, card(context, "airplane", "Airplane", ControlCenterLayoutPlan.Shape.CIRCLE, false, false),
                286, 640, 100, 100);
        drawCard(canvas, card(context, "camera", "Camera", ControlCenterLayoutPlan.Shape.CIRCLE, false, false),
                414, 640, 100, 100);
        canvas.restoreToCount(saved);
    }

    private static void softColor(Canvas canvas, float x, float y, float radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(x, y, radius,
                new int[] {color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
    }

    private static ControlCenterCardView card(Context context, String spec, String label,
            ControlCenterLayoutPlan.Shape shape, boolean active, boolean large) {
        ControlCenterCardView view = new ControlCenterCardView(context);
        view.bind(new ControlCenterCardView.Model(spec, "", label, null, false, active, false,
                large, shape, 28, 100));
        view.setSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        return view;
    }

    private static void drawCard(Canvas canvas, ControlCenterCardView view, int x, int y, int width, int height) {
        measure(view, width, height);
        int saved = canvas.save();
        canvas.translate(x, y);
        view.draw(canvas);
        canvas.restoreToCount(saved);
    }

    private static Bitmap render(View view, int width, int height, int backdrop) {
        measure(view, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(backdrop);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(configuration);
    }
}
