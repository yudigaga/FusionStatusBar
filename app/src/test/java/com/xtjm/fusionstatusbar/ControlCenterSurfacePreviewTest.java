package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterSurfacePreviewTest {
    @Test public void stylesApplyToTilesSlidersAndGroupAndRestoreTheirOriginalColors() {
        var context = RuntimeEnvironment.getApplication();
        for (String spec : List.of("wifi", ControlCenterComponentSpec.VOLUME)) {
            ControlCenterCardView card = new ControlCenterCardView(context);
            card.bind(new ControlCenterCardView.Model(spec, "", "WLAN", null, false,
                    true, false, false, ControlCenterLayoutPlan.Shape.RECTANGLE, 8, 100));
            card.setSurface(ControlCenterSurface.CLEAR);
            var bitmap = render(card, 100, 200);
            assertEquals(0, Color.alpha(bitmap.getPixel(50, 8)));
            if (spec.equals(ControlCenterComponentSpec.VOLUME))
                assertTrue("Slider fill remains visible", Color.alpha(bitmap.getPixel(50, 190)) > 200);
            bitmap.recycle();
            card.setSurface(ControlCenterSurface.DARK);
            bitmap = render(card, 100, 200);
            assertColorNear(spec.equals("wifi") ? ControlCenterCardView.ACTIVE_SURFACE
                    : ControlCenterSurface.DARK.color, bitmap.getPixel(50, 8));
            bitmap.recycle();
            card.setSurface(ControlCenterSurface.DEFAULT);
            bitmap = render(card, 100, 200);
            assertEquals(ControlCenterCardView.ACTIVE_SURFACE, bitmap.getPixel(50, 8));
            bitmap.recycle();
        }
        var group = ControlCenterLayoutPlan.Item.group("group:tools",
                ControlCenterGroupData.empty().add("wifi").withAppearance(ControlCenterGroupData.Surface.CLEAR, false),
                0, 0, 1, 3);
        var card = new ControlCenterCardView(context);
        card.bindGroup(group, Map.of(), Map.of());
        card.setSurface(ControlCenterSurface.DARK);
        var bitmap = render(card, 100, 280);
        assertColorNear(ControlCenterSurface.DARK.color, bitmap.getPixel(50, 8));
        bitmap.recycle();
        card.setSurface(ControlCenterSurface.DEFAULT);
        bitmap = render(card, 100, 280);
        assertColorNear(ControlCenterSurface.defaultGroupColor(), bitmap.getPixel(50, 8));
        bitmap.recycle();
        assertEquals(ControlCenterGroupData.Surface.CLEAR, group.group.surface);
    }

    @Test public void exportGlobalSurfaceAndMemberSizePreviewsAtTwoWidths() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        for (int width : new int[] {360, 440}) for (ControlCenterSurface surface : ControlCenterSurface.values()) {
            var root = new FrameLayout(context);
            root.setBackgroundColor(0xff495c75);
            var group = new ControlCenterGroupData(ControlCenterGroupData.Arrangement.VERTICAL,
                    ControlCenterGroupData.Surface.CLEAR, true, List.of(
                    new ControlCenterGroupData.Member("rotation", 0, 0, 12, 4, false, 150),
                    new ControlCenterGroupData.Member("camera", 0, 4, 12, 4, false, 75),
                    new ControlCenterGroupData.Member("search", 0, 8, 12, 4, false, 100)));
            ControlCenterCardView wifi = card("wifi", "WLAN", true, true);
            ControlCenterCardView bt = card("bt", "蓝牙", true, true);
            ControlCenterCardView media = card(ControlCenterComponentSpec.MEDIA, "音乐", false, false);
            ControlCenterCardView slider = card(ControlCenterComponentSpec.VOLUME, "音量", false, false);
            ControlCenterCardView tools = new ControlCenterCardView(context);
            tools.bindGroup(ControlCenterLayoutPlan.Item.group("group:tools", group, 0, 0, 1, 3), Map.of(), Map.of());
            int unit = (width - 40) / 4;
            place(root, wifi, 12, 12, unit * 2, unit);
            place(root, bt, 20 + unit * 2, 12, unit * 2, unit);
            place(root, media, 12, 20 + unit, unit * 2, unit * 2 + 8);
            place(root, slider, 20 + unit * 2, 20 + unit, unit, unit * 2 + 8);
            place(root, tools, 28 + unit * 3, 20 + unit, unit, unit * 3 + 16);
            for (var card : List.of(wifi, bt, media, slider, tools)) card.setSurface(surface);
            var bitmap = render(root, width, unit * 4 + 52);
            assertTrue(wifi.getMeasuredWidth() > bt.getMeasuredHeight());
            assertTrue(tools.getBottom() <= root.getHeight());
            File file = new File("build/reports/surface-previews/" + surface.name().toLowerCase() + "-" + width + ".png");
            file.getParentFile().mkdirs();
            try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
            bitmap.recycle();
            for (var card : List.of(wifi, bt, media, slider, tools)) card.setGlobalBorder(true, 2);
            bitmap = render(root, width, unit * 4 + 52);
            File bordered = new File("build/reports/surface-previews/border-"
                    + surface.name().toLowerCase() + "-" + width + ".png");
            try (FileOutputStream output = new FileOutputStream(bordered)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            bitmap.recycle();
        }
    }

    @Test public void previewGlobalBorderFollowsStyleAndRestoresWithoutChangingContent() {
        var card = card("wifi", "WLAN", true, true);
        card.setSurface(ControlCenterSurface.LIGHT);
        var original = render(card, 160, 80);
        int originalEdge = original.getPixel(1, 40);
        int center = original.getPixel(80, 40);
        original.recycle();
        card.setGlobalBorder(true, 2);
        var bordered = render(card, 160, 80);
        assertNotEquals(originalEdge, bordered.getPixel(1, 40));
        assertEquals(center, bordered.getPixel(80, 40));
        bordered.recycle();
        card.setGlobalBorder(false, 2);
        var restored = render(card, 160, 80);
        assertEquals(originalEdge, restored.getPixel(1, 40));
        restored.recycle();
    }
    @Test public void softGlassReflectionAppearsInPreviewAndRestoresOnStyleChange() {
        var card = card("wifi", "WLAN", false, true);
        card.setSurface(ControlCenterSurface.SOFT_GLASS);
        var glass = render(card, 160, 80);
        int highlight = glass.getPixel(80, 1);
        int interior = glass.getPixel(80, 8);
        assertTrue("Glass rim should be brighter than its nearby surface: "
                        + Integer.toHexString(highlight) + " vs " + Integer.toHexString(interior),
                Color.luminance(highlight) > Color.luminance(interior) + 0.06f);
        glass.recycle();
        card.setSurface(ControlCenterSurface.FROSTED);
        var restored = render(card, 160, 80);
        assertEquals("Changing style should remove the glass reflection",
                restored.getPixel(80, 1), restored.getPixel(80, 8));
        restored.recycle();
    }
    @Test public void softGlassPreviewStaysDarkWithLowCardBlurAndSliderFillReadable() {
        var context = RuntimeEnvironment.getApplication();
        var root = new FrameLayout(context);
        root.setBackgroundColor(Color.WHITE);
        var card = card("wifi", "WLAN", false, true);
        root.addView(card, new FrameLayout.LayoutParams(160, 80));
        card.setSurface(ControlCenterSurface.SOFT_GLASS);
        card.setMaterialStrength(20, 20);
        var image = render(root, 160, 80);
        assertTrue("Glass should remain dark over a light backdrop",
                Color.luminance(image.getPixel(80, 8)) < 0.25f);
        image.recycle();

        var slider = card(ControlCenterComponentSpec.BRIGHTNESS, "亮度", false, false);
        slider.setSurface(ControlCenterSurface.SOFT_GLASS);
        slider.setMaterialStrength(20, 20);
        image = render(slider, 100, 200);
        assertTrue("The native-style fill should contrast with the track",
                Color.luminance(image.getPixel(50, 180))
                        > Color.luminance(image.getPixel(50, 20)) + 0.30f);
        image.recycle();
    }
    private ControlCenterCardView card(String spec, String label, boolean active, boolean large) {
        var card = new ControlCenterCardView(RuntimeEnvironment.getApplication());
        card.bind(new ControlCenterCardView.Model(spec, "", label, null, false, active,
                false, large, ControlCenterLayoutPlan.Shape.RECTANGLE, 16, 100));
        return card;
    }
    private void place(FrameLayout parent, View child, int x, int y, int width, int height) {
        var params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = x; params.topMargin = y; parent.addView(child, params);
    }
    private Bitmap render(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); return bitmap;
    }
    private void assertColorNear(int expected, int actual) {
        assertEquals(Color.alpha(expected), Color.alpha(actual));
        assertEquals(Color.red(expected), Color.red(actual), 1);
        assertEquals(Color.green(expected), Color.green(actual), 1);
        assertEquals(Color.blue(expected), Color.blue(actual), 1);
    }
}
