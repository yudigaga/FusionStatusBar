package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w440dp-h960dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterCardVisualTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void referenceCompositionRendersAtTwoWidthsWithoutChangingItsItems() throws Exception {
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        cells.add(cell("wifi", "WLAN", 0, 0, 2, 1, ControlCenterCardStyle.SOFT));
        cells.add(cell("cell", "中国联通", 2, 0, 2, 1, ControlCenterCardStyle.SOFT));
        cells.add(cell(ControlCenterComponentSpec.MEDIA, "音乐", 0, 1, 2, 2, ControlCenterCardStyle.SOFT));
        cells.add(cell(ControlCenterComponentSpec.BRIGHTNESS, "亮度", 2, 1, 1, 2, ControlCenterCardStyle.SOFT));
        cells.add(cell(ControlCenterComponentSpec.VOLUME, "音量", 3, 1, 1, 2, ControlCenterCardStyle.SOFT));
        cells.add(cell(ControlCenterComponentSpec.DEVICE_CENTER, "融合设备中心", 0, 3, 4, 1, ControlCenterCardStyle.SOFT));
        String[] specs = {"bt", "airplane", "mute", "flashlight", "screenshot", "batterysaver", "nfc", "rotation",
                "custom(com.miui.screenrecorder/.service.QuickService)", "cast", "hotspot", "search"};
        for (int i = 0; i < specs.length; i++) cells.add(cell(specs[i], "", i % 4, 4 + i / 4, 1, 1, ControlCenterCardStyle.CIRCLE));
        for (int width : new int[] {440, 360}) {
            ControlCenterGridEditor editor = editor(cells);
            FrameLayout root = root(editor);
            Bitmap bitmap = render(root, width);
            assertEquals(18, editor.getChildCount());
            assertOpaqueAndNonblank(bitmap);
            View media = editor.getChildAt(2), brightness = editor.getChildAt(3), volume = editor.getChildAt(4);
            assertEquals(media.getHeight(), brightness.getHeight());
            assertEquals(brightness.getHeight(), volume.getHeight());
            int sampleX = root.getPaddingLeft() + brightness.getLeft() + brightness.getWidth() / 2;
            int sampleY = root.getPaddingTop() + brightness.getTop() + brightness.getHeight() / 2;
            assertTrue(Color.red(bitmap.getPixel(sampleX, sampleY)) > 240);
            sampleX = root.getPaddingLeft() + volume.getLeft() + volume.getWidth() / 2;
            assertTrue(Color.red(bitmap.getPixel(sampleX, sampleY)) < 80);
            export(bitmap, "reference-" + width);
        }
    }

    @Test public void allPresetsHaveRenderableDistinctOutlines() throws Exception {
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        for (ControlCenterCardStyle style : ControlCenterCardStyle.values()) {
            if (style == ControlCenterCardStyle.CUSTOM) continue;
            int index = style.ordinal();
            cells.add(cell("style-" + index, style.label, index % 2 * 2, index / 2, 2, 1, style));
        }
        Bitmap bitmap = render(root(editor(cells)), 440);
        assertOpaqueAndNonblank(bitmap);
        export(bitmap, "style-presets");
    }

    @Test public void largeAndCompactLabelsStaySeparateFromTheirIconsAtSmallWidths() {
        for (int mode : new int[] {ControlCenterPreviewTile.MODE_COMPACT, ControlCenterPreviewTile.MODE_LARGE}) {
            for (int scale : new int[] {70, 100, 130}) {
                ControlCenterPreviewTile tile = new ControlCenterPreviewTile(context, "cell", "中国联通移动网络",
                        "已开启", mode, true, 24, scale, false);
                tile.measure(View.MeasureSpec.makeMeasureSpec(mode == ControlCenterPreviewTile.MODE_LARGE ? 140 : 80,
                                View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.EXACTLY));
                tile.layout(0, 0, tile.getMeasuredWidth(), tile.getMeasuredHeight());
                LinearLayout row = (LinearLayout) tile.getChildAt(0);
                LinearLayout labels = (LinearLayout) row.getChildAt(1);
                assertTrue(row.getChildAt(0).getRight() <= labels.getLeft());
                assertTrue(labels.getRight() <= row.getWidth());
                for (int i = 0; i < labels.getChildCount(); i++) {
                    TextView text = (TextView) labels.getChildAt(i);
                    assertEquals(1, text.getMaxLines());
                    assertTrue(text.getBottom() <= labels.getHeight());
                }
            }
        }
    }

    @Test public void keyIconsAreDistinctPackagedAssetsAndKeepStateColors() {
        java.util.Set<Integer> resources = new java.util.HashSet<>();
        for (String spec : Arrays.asList("wifi", "bt", "cell", "mute", "flashlight", "screenshot", "screenrecord", "rotation")) {
            int id = ControlCenterIcons.resource(spec);
            assertTrue(resources.add(id));
            Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            ControlCenterIcons.draw(new Canvas(bitmap), ControlCenterIcons.load(context, id, Color.WHITE), 24, 24, 40);
            int painted = 0;
            for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) if (Color.alpha(bitmap.getPixel(x, y)) > 0) painted++;
            assertTrue(spec, painted > 50);
            bitmap.recycle();
        }
        assertEquals(ControlCenterIcons.resource("screenrecord"), ControlCenterIcons.resource(
                "custom(com.miui.screenrecorder/.service.QuickService)"));
        assertEquals(Color.rgb(36, 200, 66), ControlCenterIcons.color("cell", true));
        assertEquals(Color.rgb(255, 79, 70), ControlCenterIcons.color("mute", true));
        assertEquals(Color.rgb(248, 249, 251), ControlCenterIcons.color("mute", false));
    }

    @Test public void pickerAndMenuRenderInLightAndDarkPalettesWithoutEditingOnOpen() throws Exception {
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 17);
        for (boolean dark : new boolean[] {false, true}) {
            int text = dark ? Color.WHITE : Color.rgb(32, 33, 36);
            int background = dark ? Color.rgb(28, 29, 31) : Color.WHITE;
            ControlCenterCardStylePicker picker = new ControlCenterCardStylePicker(context, item, text, background,
                    proposed -> { fail("Opening styles cannot change the draft"); return proposed; });
            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundColor(background);
            panel.setPadding(16, 16, 16, 16);
            panel.addView(picker, new LinearLayout.LayoutParams(-1, -2));
            SettingsChoiceSelector selector = (SettingsChoiceSelector) picker.getChildAt(1);
            assertEquals(ControlCenterCardStyle.CUSTOM.ordinal(), selector.selectedIndex());
            selector.performClick();
            assertFalse(selector.optionView(ControlCenterCardStyle.CUSTOM.ordinal()).isEnabled());
            for (int i = 0; i < 7; i++) {
                ViewGroup row = (ViewGroup) selector.optionView(i);
                TextView label = null;
                for (int child = 0; child < row.getChildCount(); child++) {
                    if (row.getChildAt(child) instanceof TextView
                            && ((TextView) row.getChildAt(child)).getText().toString().equals(
                                    ControlCenterCardStyle.values()[i].label)) {
                        label = (TextView) row.getChildAt(child); break;
                    }
                }
                assertNotNull(label);
                assertEquals(text, label.getCurrentTextColor());
            }
            export(render(panel, 320), dark ? "picker-dark" : "picker-light");
        }
    }

    @Test public void capturedStateOverridesSampleTilesAndSliderFill() throws Exception {
        Bitmap icon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        new Canvas(icon).drawColor(Color.BLUE);
        ControlCenterCapturedStyle wifi = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.RECTANGLE,
                28, 2, "Native WLAN", "Connected", -1, icon);
        ControlCenterCapturedStyle bluetooth = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE,
                0, 1, "Bluetooth", "Off", -1, null);
        ControlCenterCapturedStyle slider = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.RECTANGLE,
                24, -1, "", "", 0.9f, null);
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        cells.add(cell("wifi", "WLAN", 0, 0, 2, 1, ControlCenterCardStyle.SOFT).withCapturedStyles(wifi, null));
        cells.add(cell("bt", "Bluetooth", 2, 0, 1, 1, ControlCenterCardStyle.CIRCLE).withCapturedStyles(bluetooth, null));
        cells.add(cell(ControlCenterComponentSpec.VOLUME, "Volume", 3, 0, 1, 2,
                ControlCenterCardStyle.SOFT).withCapturedStyles(slider, null));
        ControlCenterGridEditor editor = editor(cells);
        Bitmap bitmap = render(root(editor), 440);
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, surfaceColor(editor.getChildAt(0)));
        assertEquals(ControlCenterCardView.INACTIVE_SURFACE, surfaceColor(editor.getChildAt(1)));
        assertTrue(containsText(editor, "Native WLAN"));
        assertTrue(containsText(editor, "Connected"));
        View volume = editor.getChildAt(2);
        assertTrue(Color.red(bitmap.getPixel(24 + volume.getLeft() + volume.getWidth() / 2,
                24 + volume.getTop() + volume.getHeight() / 2)) > 240);
        export(bitmap, "captured-state");
    }

    private int surfaceColor(View host) {
        ViewGroup renderer = (ViewGroup) ((ViewGroup) host).getChildAt(0);
        return ((android.graphics.drawable.GradientDrawable) renderer.getChildAt(0).getBackground()).getColor().getDefaultColor();
    }

    @Test public void nativeFixedSizeLayersRemainCompleteInTheRenderedCanvas() throws Exception {
        ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
        String[] specs = {"wifi", "cell", "bt", "airplane", "mute", "flashlight", "screenshot", "batterysaver"};
        for (int i = 0; i < specs.length; i++) {
            boolean active = i == 0 || i == 4;
            android.graphics.drawable.Drawable glyph = ControlCenterIcons.load(context,
                    ControlCenterIcons.resource(specs[i]), ControlCenterIcons.color(specs[i], active));
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[] {new android.graphics.drawable.ColorDrawable(Color.MAGENTA), glyph});
            layers.setLayerSize(1, 192, 192);
            layers.setLayerGravity(1, android.view.Gravity.CENTER);
            android.widget.ImageView image = new android.widget.ImageView(context);
            image.setImageDrawable(layers);
            layers.setBounds(0, 0, 256, 256);
            FrameLayout nativeView = new FrameLayout(context);
            nativeView.addView(image);
            ControlCenterCapturedStyle captured = ControlCenterStyleSampler.read(nativeView, specs[i]);
            assertNotNull(specs[i], captured.icon);
            ControlCenterCapturedStyle style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE,
                    0, active ? 2 : 1, "", "", -1, captured.icon);
            cells.add(cell(specs[i], "", i % 4, i / 4, 1, 1, ControlCenterCardStyle.CIRCLE)
                    .withCapturedStyles(style, null));
        }
        for (int width : new int[] {360, 440}) {
            ControlCenterGridEditor editor = editor(cells);
            Bitmap bitmap = render(root(editor), width);
            assertOpaqueAndNonblank(bitmap);
            assertTrue(editor.getChildAt(1).getLeft() - editor.getChildAt(0).getRight() >= 14);
            export(bitmap, "native-layer-icons-" + width);
        }
    }
    private boolean containsText(View view, String expected) {
        if (view instanceof TextView text && expected.contentEquals(text.getText())) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }

    private ControlCenterGridEditor.Cell cell(String spec, String label, int x, int y, int w, int h, ControlCenterCardStyle style) {
        return new ControlCenterGridEditor.Cell(spec, spec, "", label, null, x, y, w, h, false,
                null, null, style.shape, style.radius, false, false, 0, 100);
    }
    private ControlCenterGridEditor editor(ArrayList<ControlCenterGridEditor.Cell> cells) {
        ControlCenterGridEditor editor = new ControlCenterGridEditor(context, new ControlCenterGridEditor.Listener() {
            @Override public void onItemClicked(String id) { }
            @Override public void onItemMoved(String id, int x, int y) { fail("Rendering cannot edit a draft"); }
        });
        editor.setGeometry(16, 24);
        editor.setCells(4, cells);
        return editor;
    }
    private FrameLayout root(ControlCenterGridEditor editor) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundResource(R.drawable.control_center_canvas_background);
        root.setPadding(24, 24, 24, 24);
        root.addView(editor, new FrameLayout.LayoutParams(-1, -2));
        return root;
    }
    private Bitmap render(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
    private void assertOpaqueAndNonblank(Bitmap bitmap) {
        int bright = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 4) for (int x = 0; x < bitmap.getWidth(); x += 4) {
            int pixel = bitmap.getPixel(x, y);
            assertEquals(255, Color.alpha(pixel));
            if (Color.red(pixel) > 200) bright++;
        }
        assertTrue(bright > 20);
    }
    private void export(Bitmap bitmap, String name) throws Exception {
        File directory = new File("build/reports/control-center-styles");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }
}
