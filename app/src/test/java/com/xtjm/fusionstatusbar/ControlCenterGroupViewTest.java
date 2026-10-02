package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterGroupViewTest {
    @Test public void stackedAndMixedGroupsRenderWithoutOverlapsAtNarrowAndWideSizes() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterGroupData triple = ControlCenterGroupData.empty().add("wifi").add("cell").add("bt");
        ControlCenterGroupData mixed = ControlCenterGroupData.empty();
        for (String spec : List.of("airplane", "quietmode", "rotation", "nfc", "hotspot", "mute", "gps")) mixed = mixed.add(spec);
        mixed = mixed.arrange(ControlCenterGroupData.Arrangement.MIXED);
        HashMap<String, String> labels = new HashMap<>();
        labels.put("wifi", "WLAN"); labels.put("cell", "移动网络"); labels.put("bt", "蓝牙");
        for (int width : new int[] {160, 200, 320}) {
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundResource(R.drawable.control_center_canvas_background);
            for (int i = 0; i < 2; i++) {
                ControlCenterCardView card = new ControlCenterCardView(activity);
                card.bindGroup(ControlCenterLayoutPlan.Item.group("group:" + i, i == 0 ? triple : mixed, 0, 0, 2, 2),
                        new HashMap<>(), labels);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, width);
                lp.topMargin = i * (width + 16); lp.leftMargin = 12;
                root.addView(card, lp);
            }
            layout(root, width + 24, width * 2 + 16);
            for (int i = 0; i < 2; i++) {
                ViewGroup surface = (ViewGroup) ((ViewGroup) root.getChildAt(i)).getChildAt(0);
                ViewGroup group = (ViewGroup) surface.getChildAt(0);
                assertEquals(i == 0 ? 3 : 7, group.getChildCount());
                for (int a = 0; a < group.getChildCount(); a++) {
                    View child = group.getChildAt(a);
                    assertTrue(child.getWidth() > 0 && child.getHeight() > 0);
                    assertTrue(child.getLeft() >= 0 && child.getTop() >= 0);
                    assertTrue(child.getRight() <= width && child.getBottom() <= width);
                    for (int b = a + 1; b < group.getChildCount(); b++) {
                        View other = group.getChildAt(b);
                        assertFalse(android.graphics.Rect.intersects(new android.graphics.Rect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom()),
                                new android.graphics.Rect(other.getLeft(), other.getTop(), other.getRight(), other.getBottom())));
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            int white = 0;
            for (int y = 0; y < bitmap.getHeight(); y += 2) for (int x = 0; x < bitmap.getWidth(); x += 2) {
                assertEquals(255, Color.alpha(bitmap.getPixel(x, y)));
                if (Color.red(bitmap.getPixel(x, y)) > 220) white++;
            }
            assertTrue(white > 100);
            File directory = new File("build/reports/control-center-groups");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream out = new FileOutputStream(new File(directory, "groups-" + width + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            bitmap.recycle();
        }
    }

    @Test public void nativeEntryForwardsClicksAndRestoresVisibleLocalContent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View tile = new View(activity);
        int[] clicks = {0, 0};
        tile.setOnClickListener(v -> clicks[0]++);
        tile.setOnLongClickListener(v -> { clicks[1]++; return true; });
        ControlCenterGroupEntry entry = new ControlCenterGroupEntry(activity, tile, true, true, () -> new String[] {"WLAN", "已连接"});
        tile.setAlpha(0); tile.setTranslationY(100);
        entry.normalizeContent();
        layout(entry, 180, 52);
        assertEquals(1f, tile.getAlpha(), 0f);
        assertEquals(0f, tile.getTranslationY(), 0f);
        assertTrue(tile.getScaleX() <= 1);
        entry.performClick(); entry.performLongClick();
        assertArrayEquals(new int[] {1, 1}, clicks);
        assertEquals(3, entry.getChildCount());
        assertTrue(entry.getChildAt(1).getLeft() >= tile.getLeft() + tile.getWidth() * tile.getScaleX());
    }

    @Test public void emptyGroupIsRenderedWithOneBackgroundAndNoNativePlaceholderTile() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var item = ControlCenterLayoutPlan.Item.group("group:empty", ControlCenterGroupData.empty(), 0, 0, 2, 2);
        ControlCenterGroupView group = new ControlCenterGroupView(activity, item, member -> { fail("No native tile for an empty group"); return null; });
        var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 8, 24, 0, 2);
        grid.addTile(group, item, -1);
        layout(grid, 400, 300);
        assertEquals(1, grid.getChildCount());
        assertEquals(0, group.getChildCount());
        assertNotNull(group.getBackground());
    }

    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @Test public void groupPropertiesRenderInBothThemesAndValidateManualEdits() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (boolean dark : new boolean[] {false, true}) {
            final ControlCenterLayoutPlan.Item[] item = {ControlCenterLayoutPlan.Item.group("group:test",
                    ControlCenterGroupData.empty().add("wifi").add("cell").add("bt"), 0, 0, 2, 2)};
            ControlCenterGroupEditor properties = new ControlCenterGroupEditor(activity, () -> item[0], () -> List.of("airplane"),
                    data -> item[0] = item[0].withGroup(data), spec -> spec.equals("wifi") ? "WLAN" : spec.equals("cell") ? "移动数据" : "蓝牙",
                    () -> {}, dark ? Color.WHITE : Color.BLACK, (specs, selected) -> {});
            properties.setPadding(16, 16, 16, 16);
            properties.setBackgroundColor(dark ? 0xff242528 : Color.WHITE);
            properties.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            properties.layout(0, 0, 320, properties.getMeasuredHeight());
            Bitmap bitmap = Bitmap.createBitmap(320, properties.getHeight(), Bitmap.Config.ARGB_8888);
            properties.draw(new Canvas(bitmap));
            File directory = new File("build/reports/control-center-groups");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream out = new FileOutputStream(new File(directory, dark ? "properties-dark.png" : "properties-light.png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            bitmap.recycle();
            description(properties, "编辑 WLAN").performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            android.widget.EditText width = (android.widget.EditText) description(dialog.getWindow().getDecorView(), "内部宽度");
            width.setText("13");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertTrue(dialog.isShowing());
            assertEquals(12, item[0].group.members.get(0).width);
            width.setText("6");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertFalse(dialog.isShowing());
            assertEquals(6, item[0].group.members.get(0).width);
            assertEquals(ControlCenterGroupData.Arrangement.CUSTOM, item[0].group.arrangement);
            item[0] = item[0].withLocked(true); properties.refresh();
            assertFalse(description(properties, "编辑 WLAN").isEnabled());
        }
    }

    private View description(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View match = description(group.getChildAt(i), text);
            if (match != null) return match;
        }
        return null;
    }

    @Test public void groupDefaultRadiusAndCircleBoundsMatchEditorAndRuntime() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var item = ControlCenterLayoutPlan.Item.group("group:test", ControlCenterGroupData.empty(), 0, 0, 3, 1)
                .withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 0);
        ControlCenterGroupView square = new ControlCenterGroupView(activity, item, 0, m -> new View(activity));
        layout(square, 180, 90);
        assertEquals(0f, ((android.graphics.drawable.GradientDrawable) square.getBackground()).getCornerRadius(), 0f);
        var circleItem = item.withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        var group = new ControlCenterGroupView(activity, circleItem, m -> new View(activity));
        var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 8, 0, 0, 1);
        grid.addTile(group, circleItem, -1);
        layout(grid, 400, 100);
        assertEquals(group.getWidth(), group.getHeight());
        assertTrue(group.getLeft() > 0);
    }
}
