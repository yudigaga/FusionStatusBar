package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Paint;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionIconViewTest {
    @Test public void spanningStatusBarIconKeepsItsConfiguredSizeWhenHostMeasuresOneRow() {
        FusionIconView icon = new FusionIconView(RuntimeEnvironment.getApplication());
        icon.setDisplayConfig(FusionConfig.defaults().withDoubleRow(true).withSpanRows(true)
                .withStatusBarHeight(48).withIconScale(150));

        icon.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(24, View.MeasureSpec.EXACTLY));

        assertEquals("the status-bar size setting keeps control of the spanning icon",
                48, icon.getMeasuredHeight());
    }

    @Test public void controlCenterIconFitsItsHostWithoutUsingTheStatusBarScale() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        android.view.ViewGroup[] hosts = {
                new com.android.systemui.controlcenter.phone.widget.ControlCenterStatusBarIcon(context),
                new com.android.systemui.controlcenter.phone.widget.ControlCenterFakeStatusIcons(context)
        };
        for (android.view.ViewGroup host : hosts) {
            FusionIconView icon = new FusionIconView(context);
            host.addView(icon);
            icon.setDisplayConfig(FusionConfig.defaults().withDoubleRow(true).withSpanRows(true)
                    .withStatusBarHeight(48).withIconScale(200));

            icon.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(24, View.MeasureSpec.EXACTLY));

            assertEquals("control-center artwork uses an independent fit scale in "
                            + host.getClass().getSimpleName(),
                    FusionIconPlacement.iconHeight(24,
                            FusionIconPlacement.CONTROL_CENTER_ICON_SCALE),
                    icon.getMeasuredHeight());
        }
    }

    @Test public void allContentOrdersRenderAndProduceAVisualContactSheet() throws Exception {
        int panelWidth = 320;
        int panelHeight = 350;
        Bitmap sheet = Bitmap.createBitmap(panelWidth * 3, panelHeight * 3, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xff303034);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(15f);
        int panel = 0;
        for (int battery = 0; battery < 3; battery++) {
            for (int network = 0; network < 3; network++) {
                if (battery == network) continue;
                FusionConfig config = FusionConfig.defaults()
                        .withFusionContentPosition(FusionContentConfig.BATTERY, battery)
                        .withFusionContentPosition(FusionContentConfig.NETWORK, network);
                renderPanel(canvas, label, panel++, config,
                        "B:" + battery + " N:" + network + " S:" + config.fusionContent.signalPosition,
                        panelWidth, panelHeight);
            }
        }
        renderPanel(canvas, label, panel++, FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP)
                .withFusionContentScale(FusionContentConfig.BATTERY, 75)
                .withFusionContentScale(FusionContentConfig.NETWORK, 150)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 75),
                "Signal top: B75 N150 S75", panelWidth, panelHeight);
        renderPanel(canvas, label, panel++, FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.NETWORK, FusionContentConfig.BOTTOM)
                .withFusionContentScale(FusionContentConfig.BATTERY, 100)
                .withFusionContentScale(FusionContentConfig.NETWORK, 150)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 75),
                "Network bottom: B100 N150 S75", panelWidth, panelHeight);
        renderPanel(canvas, label, panel, FusionConfig.defaults()
                .withFusionContentScale(FusionContentConfig.BATTERY, 150)
                .withFusionContentScale(FusionContentConfig.NETWORK, 150)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 150),
                "B150 N150 S150", panelWidth, panelHeight);
        File output = new File("build/reports/fusion-icon-content-layouts.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }

    private static void renderPanel(Canvas canvas, Paint label, int panel, FusionConfig config,
            String title, int panelWidth, int panelHeight) {
        int left = panel % 3 * panelWidth;
        int top = panel / 3 * panelHeight;
        Bitmap icon = draw(config, 80, true, 4, 2, 271, 282);
        assertTrue(opaquePixels(icon, 0, 0, icon.getWidth(), icon.getHeight()) > 2000);
        canvas.drawBitmap(icon, left + 24, top + 48, null);
        canvas.drawText(title, left + 16, top + 25, label);
    }

    @Test public void networkContentCanMoveToTheTopOfTheRenderedIcon() {
        Bitmap original = draw(FusionConfig.defaults(), -1, true, 0, 0, 271, 282);
        Bitmap swapped = draw(FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.NETWORK, FusionContentConfig.TOP),
                -1, true, 0, 0, 271, 282);
        int originalDotY = Math.round(FusionIconGeometry.WIFI_ANCHOR_Y
                + FusionIconGeometry.MIDDLE_CONTENT_Y - FusionIconGeometry.WIFI_CONTENT_Y);
        int swappedDotY = Math.round(FusionIconGeometry.WIFI_ANCHOR_Y);
        assertTrue(Color.alpha(original.getPixel(135, originalDotY)) > 200);
        assertEquals(0, Color.alpha(original.getPixel(135, swappedDotY)));
        assertTrue(Color.alpha(swapped.getPixel(135, swappedDotY)) > 200);
        assertEquals(0, Color.alpha(swapped.getPixel(135, originalDotY)));
    }

    @Test public void wifiArcsShowWifiStrengthWithoutChangingCellularSignalRows() throws Exception {
        Bitmap none = drawWifiStrength(0, 1, 3);
        Bitmap minimum = drawWifiStrength(1, 1, 3);
        Bitmap weak = drawWifiStrength(2, 1, 3);
        Bitmap medium = drawWifiStrength(3, 1, 3);
        Bitmap strong = drawWifiStrength(4, 1, 3);

        assertTrue("The weakest connected Wi-Fi level activates only the dot",
                Color.alpha(minimum.getPixel(135, 173)) > Color.alpha(none.getPixel(135, 173)));
        assertEquals(Color.alpha(none.getPixel(135, 148)),
                Color.alpha(minimum.getPixel(135, 148)));
        assertTrue("Moderate Wi-Fi activates the inner arc",
                Color.alpha(weak.getPixel(135, 148)) > Color.alpha(none.getPixel(135, 148)));
        assertTrue("Good Wi-Fi lights the outer arc partially",
                Color.alpha(medium.getPixel(135, 127)) > Color.alpha(none.getPixel(135, 127)));
        assertTrue("Strong Wi-Fi lights the outer arc fully",
                Color.alpha(strong.getPixel(135, 127)) > Color.alpha(medium.getPixel(135, 127)));
        assertSamePixels(none, strong, 0, 200, 271, 282);

        Bitmap sheet = Bitmap.createBitmap(1500, 334, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xff303034);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(16);
        Bitmap[] levels = {none, minimum, weak, medium, strong};
        for (int level = 0; level < levels.length; level++) {
            canvas.drawText("Wi-Fi " + level + "/4, SIM 1/3", level * 300 + 16, 24, label);
            canvas.drawBitmap(levels[level], level * 300 + 14, 44, null);
        }
        File output = new File("build/reports/wifi-and-cellular-signal-levels.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        sheet.recycle();
        none.recycle();
        minimum.recycle();
        weak.recycle();
        medium.recycle();
        strong.recycle();
    }

    @Test public void cellularRowsCanChangeWhileTheWifiGlyphKeepsItsOwnStrength() {
        Bitmap first = drawWifiStrength(2, 1, 3);
        Bitmap next = drawWifiStrength(2, 4, 0);
        assertSamePixels(first, next, 75, 112, 199, 188);
        assertTrue("SIM one changes its own signal row",
                Color.alpha(next.getPixel(119, 225)) > Color.alpha(first.getPixel(119, 225)));
        assertTrue("SIM two changes its own signal row",
                Color.alpha(next.getPixel(119, 259)) < Color.alpha(first.getPixel(119, 259)));
        first.recycle();
        next.recycle();
    }

    @Test public void runtimeSnapshotSuppliesWifiStrengthAndBothCellularRows() throws Exception {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        var render = FusionIconView.class.getDeclaredMethod("render", StatusMonitor.Snapshot.class);
        render.setAccessible(true);
        render.invoke(view, new StatusMonitor.Snapshot(80, true, "", 1, 3, 2, false));
        view.layout(0, 0, 271, 282);
        Bitmap runtime = Bitmap.createBitmap(271, 282, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(runtime));
        Bitmap expected = drawWifiStrength(2, 1, 3);
        assertSamePixels(expected, runtime, 0, 0, 271, 282);
        assertTrue(view.getContentDescription().toString().contains("Wi-Fi 2/4"));
        expected.recycle();
        runtime.recycle();
    }

    @Test public void topSignalBlockFlipsTheCurvesAndKeepsTheInnerSimRowFacingTheCenter() {
        Bitmap swapped = draw(FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP),
                -1, false, 4, 0, 271, 282);
        assertTrue("The active inner SIM row faces the center", Color.alpha(swapped.getPixel(119, 51)) > 200);
        assertTrue("The curve flips vertically at the top", Color.alpha(swapped.getPixel(87, 69)) > 200);
        int outerAlpha = Color.alpha(swapped.getPixel(119, 17));
        assertTrue("The inactive outer SIM row stays outside", outerAlpha > 0 && outerAlpha < 100);
    }

    @Test public void shrinkingNetworkContentLeavesTheBatteryAndSignalsUnchanged() {
        Bitmap original = draw(FusionConfig.defaults(), 80, true, 4, 3, 271, 282);
        Bitmap smaller = draw(FusionConfig.defaults()
                .withFusionContentScale(FusionContentConfig.NETWORK, 50), 80, true, 4, 3, 271, 282);
        assertSamePixels(original, smaller, 78, 5, 193, 83);
        assertSamePixels(original, smaller, 65, 205, 207, 280);
        assertTrue(opaquePixels(original, 75, 112, 199, 190)
                > opaquePixels(smaller, 75, 112, 199, 190) * 2);
    }

    @Test public void growingNetworkContentDoesNotShrinkTheOuterRingOrChangeOtherContent() {
        Bitmap original = draw(FusionConfig.defaults(), 80, true, 4, 3, 271, 282);
        Bitmap bigger = draw(FusionConfig.defaults()
                .withFusionContentScale(FusionContentConfig.NETWORK, 150), 80, true, 4, 3, 271, 282);
        assertSamePixels(original, bigger, 0, 20, 43, 260);
        assertSamePixels(original, bigger, 78, 5, 193, 83);
        assertSamePixels(original, bigger, 65, 205, 207, 280);
    }

    @Test public void oneOuterTransformScalesEveryPartAndPreservesItsArrangement() {
        FusionConfig config = FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP)
                .withFusionContentScale(FusionContentConfig.NETWORK, 80);
        Bitmap normal = draw(config, 80, true, 4, 3, 271, 282);
        Bitmap doubled = draw(config.withIconScale(150), 80, true, 4, 3, 542, 564);
        int compared = 0;
        for (int y = 1; y < normal.getHeight() - 1; y++) {
            for (int x = 1; x < normal.getWidth() - 1; x++) {
                int expected = Color.alpha(normal.getPixel(x, y));
                boolean interior = expected == 0 || expected == 255;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    interior &= Color.alpha(normal.getPixel(x + dx, y + dy)) == expected;
                }
                if (interior) {
                    assertEquals("Scaled composition at " + x + "," + y, expected,
                            Color.alpha(doubled.getPixel(x * 2 + 1, y * 2 + 1)), 2);
                    if (expected == 255) compared++;
                }
            }
        }
        assertTrue("The comparison includes solid pixels from the icon", compared > 1000);
        FusionIconView small = new FusionIconView(RuntimeEnvironment.getApplication());
        small.setDisplayConfig(config.withIconScale(50));
        small.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.AT_MOST));
        int height = small.getMeasuredHeight();
        int width = small.getMeasuredWidth();
        small.setDisplayConfig(config.withIconScale(100));
        small.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.AT_MOST));
        assertEquals(height * 2, small.getMeasuredHeight(), 1);
        assertEquals(width * 2, small.getMeasuredWidth(), 2);
    }

    private static Bitmap draw(FusionConfig config, int battery, boolean wifi,
            int signal1, int signal2, int width, int height) {
        return draw(config, battery, wifi, wifi ? 4 : 0, signal1, signal2, width, height);
    }

    private static Bitmap drawWifiStrength(int wifiLevel, int signal1, int signal2) {
        return draw(FusionConfig.defaults(), 80, true, wifiLevel, signal1, signal2, 271, 282);
    }

    private static Bitmap draw(FusionConfig config, int battery, boolean wifi, int wifiLevel,
            int signal1, int signal2, int width, int height) {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        view.showSample(battery, wifi, "", wifiLevel, signal1, signal2, Color.WHITE);
        view.setPreviewConfig(config);
        view.layout(0, 0, width, height);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(result));
        return result;
    }

    private static void assertSamePixels(Bitmap a, Bitmap b, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            assertEquals("Unchanged content at " + x + "," + y, a.getPixel(x, y), b.getPixel(x, y));
        }
    }

    private static int opaquePixels(Bitmap image, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (Color.alpha(image.getPixel(x, y)) > 200) count++;
        }
        return count;
    }
}
