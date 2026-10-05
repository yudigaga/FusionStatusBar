package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.Gravity;
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

    @Test public void changingMemberIconScaleResizesOnlyVisualContentAndReservesLabels() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (boolean nativeTile : new boolean[] {false, true}) for (boolean labels : new boolean[] {false, true}) {
            float previousSize = 0;
            for (int scale : new int[] {25, 100, 200}) {
                View icon = new View(activity);
                var entry = new ControlCenterGroupEntry(activity, icon, labels, nativeTile, scale,
                        () -> new String[] {"无线局域网络", "已连接"});
                layout(entry, 180, 80);
                assertEquals(180, entry.getWidth()); assertEquals(80, entry.getHeight());
                float size = icon.getWidth() * icon.getScaleX();
                assertTrue(size >= previousSize);
                assertTrue(size <= 80);
                assertTrue(icon.getLeft() >= 0 && icon.getTop() >= 0);
                assertTrue(icon.getLeft() + size <= entry.getWidth());
                assertTrue(icon.getTop() + size <= entry.getHeight());
                if (labels) {
                    View title = entry.getChildAt(1), subtitle = entry.getChildAt(2);
                    assertTrue(title.getLeft() >= icon.getLeft() + size);
                    assertTrue(title.getWidth() >= 90);
                    assertEquals(entry.getWidth(), title.getRight());
                    assertTrue(subtitle.getBottom() <= entry.getHeight());
                    if (scale == 100) assertEquals(42, size, 0f);
                } else if (scale == 100 || scale == 200) assertEquals(80, size, 0f);
                previousSize = size;
            }
        }
    }

    @Test public void smallMemberIconsRemainCenteredInsideUnchangedTouchTargets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View tile = new View(activity);
        int[] clicks = {0}; tile.setOnClickListener(v -> clicks[0]++);
        var entry = new ControlCenterGroupEntry(activity, tile, false, true, 25, () -> new String[] {"蓝牙", "已开启"});
        layout(entry, 100, 140);
        float size = tile.getWidth() * tile.getScaleX();
        assertEquals(25, size, 0f);
        assertEquals((100 - 25) / 2, tile.getLeft());
        assertEquals((140 - 25) / 2, tile.getTop());
        assertEquals(100, entry.getWidth()); assertEquals(140, entry.getHeight());
        entry.performClick(); assertEquals(1, clicks[0]);
    }

    @Test public void tallGroupEnlargesNativeAndPreviewGlyphsAboveDefaultWithoutMovingSlots() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (boolean nativeTile : new boolean[] {false, true}) {
            int[] defaultPixels = new int[3];
            android.graphics.Rect[] originalSlots = new android.graphics.Rect[3];
            for (int scale : new int[] {100, 150}) {
                var members = List.of(
                        new ControlCenterGroupData.Member("wifi", 0, 0, 12, 4, false, scale),
                        new ControlCenterGroupData.Member("bt", 0, 4, 12, 4, false, 100),
                        new ControlCenterGroupData.Member("cell", 0, 8, 12, 4, false, 100));
                var data = new ControlCenterGroupData(ControlCenterGroupData.Arrangement.VERTICAL,
                        ControlCenterGroupData.Surface.CLEAR, false, members);
                var item = ControlCenterLayoutPlan.Item.group("group:tall", data, 0, 0, 1, 3);
                var group = new ControlCenterGroupView(activity, item, member -> {
                    View tile = nativeTile ? new NativeGroupTile(activity)
                            : new ControlCenterPreviewTile(activity, member.spec, "", "", ControlCenterPreviewTile.MODE_CIRCLE,
                                    false, 32, 100, false);
                    return new ControlCenterGroupEntry(activity, tile, false, nativeTile, member.iconScale,
                            () -> new String[] {member.spec, ""});
                });
                group.setBackground(null);
                layout(group, 120, 360);
                for (int i = 0; i < group.getChildCount(); i++) {
                    View slot = group.getChildAt(i);
                    var bounds = new android.graphics.Rect(slot.getLeft(), slot.getTop(), slot.getRight(), slot.getBottom());
                    if (scale == 100) originalSlots[i] = bounds;
                    else assertEquals(originalSlots[i], bounds);
                }
                Bitmap bitmap = Bitmap.createBitmap(120, 360, Bitmap.Config.ARGB_8888);
                group.draw(new Canvas(bitmap));
                for (int i = 0; i < originalSlots.length; i++) {
                    int pixels = 0;
                    var slot = originalSlots[i];
                    for (int y = slot.top; y < slot.bottom; y++) for (int x = slot.left; x < slot.right; x++)
                        if (Color.alpha(bitmap.getPixel(x, y)) > 0) pixels++;
                    assertTrue(pixels > 100);
                    if (scale == 100) defaultPixels[i] = pixels;
                    else if (i == 0) assertTrue("150% must enlarge painted glyph pixels", pixels > defaultPixels[i] * 1.5f);
                    else assertEquals("Other selected icons keep their size", defaultPixels[i], pixels);
                }
                for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                    if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue;
                    boolean inside = false;
                    for (var slot : originalSlots) if (slot.contains(x, y)) { inside = true; break; }
                    assertTrue("Painted glyphs must stay inside their member slots", inside);
                }
                bitmap.recycle();
            }
        }
    }

    @Test public void scaledNativeGlyphsReserveLabelSpaceAtMaximumScale() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var tile = new NativeGroupTile(activity);
        var entry = new ControlCenterGroupEntry(activity, tile, true, true, 200, () -> new String[] {"无线局域网络", "已连接"});
        layout(entry, 180, 80);
        var glyph = tile.icon.icon;
        assertEquals(42f, tile.getWidth() * tile.getScaleX(), 0f);
        assertEquals(2f, glyph.getScaleX(), 0f);
        float glyphCenter = (tile.icon.getLeft() + glyph.getLeft() + glyph.getWidth() / 2f) * tile.getScaleX();
        float glyphRight = glyphCenter + glyph.getWidth() * glyph.getScaleX() * tile.getScaleX() / 2f;
        assertTrue(glyphRight <= entry.getChildAt(1).getLeft());
        assertTrue(entry.getChildAt(1).getWidth() >= 90);
    }

    private static final class NativeGroupTile extends FrameLayout {
        final NativeGroupIcon icon;
        NativeGroupTile(Activity activity) {
            super(activity);
            icon = new NativeGroupIcon(activity);
            addView(icon, new LayoutParams(-1, -1));
        }
    }

    private static final class NativeGroupIcon extends FrameLayout {
        final ImageView icon;
        NativeGroupIcon(Activity activity) {
            super(activity);
            icon = new ImageView(activity);
            icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
            addView(icon, new LayoutParams(32, 32, Gravity.CENTER));
        }
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

    @Test public void legacyGroupSurfaceAndBorderDoNotOverrideGlobalDefaults() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Bitmap baseline = null;
        for (int version : new int[] {5, 6}) for (var oldSurface : ControlCenterGroupData.Surface.values())
                for (boolean oldBorder : new boolean[] {false, true}) {
            var plan = ControlCenterLayoutPlan.decode(legacyGroupPacket(version, oldSurface, oldBorder));
            assertNotNull(plan);
            var item = plan.regular.items.get(0);
            assertEquals(oldSurface, item.group.surface);
            assertEquals(oldBorder, item.group.border);
            assertEquals(item.group, ControlCenterLayoutPlan.decode(plan.encode()).regular.items.get(0).group);
            var group = new ControlCenterGroupView(activity, item, member -> new View(activity));
            layout(group, 120, 180);
            var drawable = (android.graphics.drawable.GradientDrawable) group.getBackground();
            assertEquals(ControlCenterSurface.defaultGroupColor(), drawable.getColor().getDefaultColor());
            Bitmap bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888);
            group.draw(new Canvas(bitmap));
            if (baseline == null) baseline = bitmap;
            else {
                assertTrue("Legacy surface and outline must not change rendering", baseline.sameAs(bitmap));
                bitmap.recycle();
            }
        }
        baseline.recycle();
    }

    private String legacyGroupPacket(int version, ControlCenterGroupData.Surface surface, boolean border) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x43434c50); out.writeByte(version);
        out.writeByte(4); out.writeShort(1);
        out.writeByte(3); out.writeByte(0); out.writeByte(0); out.writeByte(0);
        out.writeByte(0); out.writeShort(0); out.writeShort(0); out.writeShort(0);
        out.writeByte(1); out.writeByte(3); out.writeUTF("group:legacy"); out.writeUTF("");
        out.writeByte(ControlCenterGroupData.Arrangement.VERTICAL.ordinal());
        out.writeByte(surface.ordinal()); out.writeBoolean(border); out.writeByte(1);
        out.writeUTF("wifi"); out.writeByte(0); out.writeByte(0); out.writeByte(12); out.writeByte(12); out.writeBoolean(true);
        if (version >= 6) out.writeByte(100);
        out.writeByte(3); out.writeShort(0); out.writeByte(0);
        return "CCLP2:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
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
