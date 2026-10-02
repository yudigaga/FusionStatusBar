package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterCardRenderingTest {
    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private Bitmap render(View view, int width, int height) {
        layout(view, width, height);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }
    @Test public void sliderFillIsFullWidthButClippedAtRoundedCorners() {
        ControlCenterCardView view = new ControlCenterCardView(RuntimeEnvironment.getApplication());
        view.bind(new ControlCenterCardView.Model(ControlCenterComponentSpec.BRIGHTNESS,
                "", "亮度", null, false, false, false, false,
                ControlCenterLayoutPlan.Shape.RECTANGLE, 24, 100));
        Bitmap image = render(view, 100, 200);
        assertEquals(0, Color.alpha(image.getPixel(0, 199)));
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, image.getPixel(10, 120));
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, image.getPixel(90, 120));
        assertTrue(Color.alpha(image.getPixel(50, 20)) > 0);
        assertTrue(Color.alpha(image.getPixel(50, 20)) < 255);
    }
    @Test public void circleLeavesTransparentSidesInAWideSlot() {
        ControlCenterCardView view = new ControlCenterCardView(RuntimeEnvironment.getApplication());
        view.bind(new ControlCenterCardView.Model("bt", "", "蓝牙", null,
                false, true, false, false, ControlCenterLayoutPlan.Shape.CIRCLE, 24, 100));
        Bitmap image = render(view, 200, 100);
        assertEquals(0, Color.alpha(image.getPixel(10, 50)));
        assertEquals(0, Color.alpha(image.getPixel(190, 50)));
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, image.getPixel(100, 10));
    }
    @Test public void exportSampleLayoutForVisualReview() throws Exception {
        ControlCenterGridEditor editor = new ControlCenterGridEditor(RuntimeEnvironment.getApplication(),
                new ControlCenterGridEditor.Listener() {
                    public void onItemClicked(String id) {}
                    public void onItemMoved(String id, int x, int y) {}
                });
        editor.setGeometry(12, 24);
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        cells.add(cell("wifi", "WLAN", 0, 0, 2, 1, false));
        cells.add(cell("cell", "中国联通", 2, 0, 2, 1, false));
        cells.add(cell(ControlCenterComponentSpec.MEDIA, "媒体", 0, 1, 2, 2, false));
        cells.add(cell(ControlCenterComponentSpec.BRIGHTNESS, "亮度", 2, 1, 1, 2, false));
        cells.add(cell(ControlCenterComponentSpec.VOLUME, "音量", 3, 1, 1, 2, false));
        cells.add(cell(ControlCenterComponentSpec.DEVICE_CENTER, "融合设备中心", 0, 3, 4, 1, false));
        String[] specs = {"bt", "airplane", "mute", "flashlight", "screenshot", "batterysaver", "rotation", "wifi"};
        for (int i = 0; i < specs.length; i++) cells.add(cell(specs[i], specs[i], i % 4, 4 + i / 4, 1, 1, true));
        editor.setCells(4, cells);
        editor.measure(View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        editor.layout(0, 0, 440, editor.getMeasuredHeight());
        Bitmap image = Bitmap.createBitmap(472, editor.getHeight() + 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.rgb(60, 66, 73));
        canvas.translate(16, 16);
        editor.draw(canvas);
        File target = new File("build/reports/control-center-editor/step2-sample.png");
        assertTrue(target.getParentFile().isDirectory() || target.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(target)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
    @Test public void exportSelectedPairForVisualReview() throws Exception {
        ControlCenterGridEditor editor = new ControlCenterGridEditor(RuntimeEnvironment.getApplication(),
                new ControlCenterGridEditor.Listener() {
                    public void onItemClicked(String id) {}
                    public void onItemMoved(String id, int x, int y) {}
                });
        editor.setGeometry(12, 24);
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        cells.add(new ControlCenterGridEditor.Cell("pair", "wifi", "bt", "WLAN", "蓝牙",
                0, 0, 2, 1, true, null, null, ControlCenterLayoutPlan.Shape.RECTANGLE,
                24, false, false, 0, 100));
        cells.add(cell(ControlCenterComponentSpec.BRIGHTNESS, "亮度", 2, 0, 1, 2, false));
        cells.add(cell(ControlCenterComponentSpec.MEDIA, "媒体", 0, 2, 3, 2, false));
        editor.setCells(3, cells);
        int width = 320;
        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        editor.layout(0, 0, width, editor.getMeasuredHeight());
        assertTrue(editor.getChildAt(0).performLongClick());
        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        editor.layout(0, 0, width, editor.getMeasuredHeight());
        assertEquals(4, editor.getChildCount());
        Bitmap image = Bitmap.createBitmap(width + 32, editor.getHeight() + 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.rgb(60, 66, 73));
        canvas.translate(16, 16);
        editor.draw(canvas);
        File target = new File("build/reports/control-center-editor/step3-selected-pair.png");
        assertTrue(target.getParentFile().isDirectory() || target.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(target)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
    private ControlCenterGridEditor.Cell cell(String spec, String label,
            int x, int y, int width, int height, boolean circle) {
        return new ControlCenterGridEditor.Cell(spec + x + y, spec, "", label, null,
                x, y, width, height, false, null, null,
                circle ? ControlCenterLayoutPlan.Shape.CIRCLE : ControlCenterLayoutPlan.Shape.RECTANGLE,
                24, false, false, 0, 100);
    }
}
