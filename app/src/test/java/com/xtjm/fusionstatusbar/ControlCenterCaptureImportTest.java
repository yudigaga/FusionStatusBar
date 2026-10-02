package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterCaptureImportTest {
    @Test public void legacyApplicationAliasesRetainNativeStylesAndThumbnail() throws Exception {
        String full = "custom(com.example.tiles/com.example.tiles.QuickTile)";
        String shortSpec = "custom(com.example.tiles/.quicktile)";
        String encoded = android.util.Base64.encodeToString(full.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
        ControlCenterCapturedStyle style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CAPSULE,
                17, 2, "Legacy native tile", "Connected", -1, null);
        String captured = new JSONObject().put("version", 3).put("iconSampling", 2)
                .put("bounds", "v1|180|120|" + encoded + ",0,0,90,90")
                .put("styles", new JSONObject().put(full, style.toJson())).toString();
        var editor = new ControlCenterActualEditor(RuntimeEnvironment.getApplication(), null);
        Bitmap screenshot = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888);
        screenshot.eraseColor(Color.MAGENTA);
        editor.setPreview(screenshot, captured);
        assertNotNull(editor.capturedStyle(shortSpec));
        assertEquals("Legacy native tile", editor.capturedStyle(shortSpec).label);
        assertEquals(2, editor.capturedStyle(full).state);
        assertNotNull(editor.capturedThumbnail(shortSpec));
        ControlCenterCaptureImport imported = ControlCenterCaptureImport.from(editor.capturedLayout(), 4, 1);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, imported.mode.items.get(0).shape);
        assertEquals(17, imported.mode.items.get(0).cornerRadius);
    }
    private String bounds = "v2|440|900|86|188|290|392|d2lmaQ==,24,120,188,86|Y2VsbA==,228,120,188,86"
            + "|YnQ=,24,222,86,86|YWlycGxhbmU=,126,222,86,86|bXV0ZQ==,228,222,86,86|Zmxhc2hsaWdodA==,330,222,86,86";

    @Test public void styleEnvelopeKeepsLegacyBoundsAndNativeState() throws Exception {
        ControlCenterCapturedStyle style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CAPSULE,
                31, 2, "Native WLAN", "Connected", -1, null);
        String json = new JSONObject().put("version", 3).put("density", 2).put("bounds", bounds)
                .put("styles", new JSONObject().put("wifi", style.toJson())).toString();
        ControlCenterActualEditor.LayoutData parsed = ControlCenterActualEditor.LayoutData.parse(json);
        assertNotNull(parsed);
        assertEquals(6, parsed.tiles.size());
        assertEquals(2, parsed.styles.get("wifi").state);
        assertEquals("Connected", parsed.styles.get("wifi").subtitle);
        ControlCenterCaptureImport imported = ControlCenterCaptureImport.from(parsed, 5, 1);
        assertEquals(4, imported.mode.columns);
        assertEquals(8, imported.spacingDp);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, imported.mode.items.get(0).shape);
        assertEquals(31, imported.mode.items.get(0).cornerRadius);
        assertEquals(2, imported.mode.items.get(0).width);
    }

    @Test public void oldCapturesStillImportAndUseGeometricFallbacks() {
        ControlCenterActualEditor.LayoutData parsed = ControlCenterActualEditor.LayoutData.parse(bounds);
        assertNotNull(parsed);
        ControlCenterCaptureImport imported = ControlCenterCaptureImport.from(parsed, 5, 1);
        assertEquals(16, imported.spacingDp);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE, imported.mode.items.get(0).shape);
        assertEquals(ControlCenterLayoutPlan.Shape.CIRCLE, imported.mode.items.get(2).shape);
        assertTrue(imported.mode.validate().isEmpty());
    }

    @Test public void malformedAndRecursiveEnvelopesAreRejected() throws Exception {
        assertNull(ControlCenterActualEditor.LayoutData.parse("{broken"));
        assertNull(ControlCenterActualEditor.LayoutData.parse(new JSONObject().put("version", 3)
                .put("bounds", new JSONObject().put("version", 3).toString()).toString()));
        assertNull(ControlCenterActualEditor.LayoutData.parse(new JSONObject().put("version", 90)
                .put("bounds", bounds).toString()));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void samplerReadsNativeTextStateIconAndSliderWithoutChangingViews() throws Exception {
        NativeTile view = new NativeTile();
        view.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(view.getContext());
        title.setText("Native WLAN");
        view.addView(title);
        TextView subtitle = new TextView(view.getContext());
        subtitle.setText("Connected");
        view.addView(subtitle);
        ImageView image = new ImageView(view.getContext());
        image.setImageDrawable(new ColorDrawable(Color.BLUE));
        image.getDrawable().setBounds(3, 4, 22, 25);
        view.addView(image);
        SeekBar slider = new SeekBar(view.getContext());
        slider.setMax(200);
        slider.setProgress(120);
        view.addView(slider);
        ControlCenterCapturedStyle result = ControlCenterStyleSampler.read(view, "wifi");
        assertEquals("Native WLAN", result.label);
        assertEquals("Connected", result.subtitle);
        assertEquals(2, result.state);
        assertEquals(0.6f, result.level, 0.001f);
        assertNotNull(result.icon);
        assertEquals(new android.graphics.Rect(3, 4, 22, 25), image.getDrawable().getBounds());
        ControlCenterCapturedStyle decoded = ControlCenterCapturedStyle.parse(result.toJson());
        assertNotNull(decoded.icon);
        assertEquals(ControlCenterStyleSampler.ICON_SIZE, decoded.icon.getWidth());
        assertEquals("Native WLAN", decoded.label);
        assertEquals(120, slider.getProgress());
    }

    @Test public void placementReflowsUnlockedSlotsButPreservesLockedItems() {
        ControlCenterLayoutPlan.Item wifi = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item bt = ControlCenterLayoutPlan.Item.tile("bt", 1, 0, 1, 1);
        ControlCenterLayoutPlan.Item locked = ControlCenterLayoutPlan.Item.tile("airplane", 2, 0, 1, 1).withLocked(true);
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(4, Arrays.asList(wifi, bt, locked));
        ControlCenterLayoutPlan.Mode result = ControlCenterGridPlacement.place(source, wifi.withPosition(1, 0));
        assertNotNull(result);
        assertTrue(result.validate().isEmpty());
        assertEquals(0, result.items.stream().filter(i -> i.id.equals("bt")).findFirst().get().x);
        assertEquals(2, result.items.stream().filter(i -> i.id.equals("airplane")).findFirst().get().x);
        assertNull(ControlCenterGridPlacement.place(source, wifi.withPosition(2, 0)));
        assertNull(ControlCenterGridPlacement.place(source, wifi.withPosition(4, 0)));
        assertNull(ControlCenterGridPlacement.place(source, locked.withPosition(3, 0)));
    }

    @Test public void containerWidthsMustNotEraseGapsVisibleBetweenNativeSliders() {
        java.util.ArrayList<ControlCenterActualEditor.Tile> tiles = new java.util.ArrayList<>();
        tiles.add(new ControlCenterActualEditor.Tile("wifi", 581, 486, 374, 170));
        tiles.add(new ControlCenterActualEditor.Tile("cell", 988, 486, 374, 170));
        // Component dimensions are from the 23:47 capture; small-tile wrappers model OEM margins.
        tiles.add(new ControlCenterActualEditor.Tile("control:media", 581, 690, 374, 374));
        tiles.add(new ControlCenterActualEditor.Tile("control:brightness", 988, 690, 170, 374));
        tiles.add(new ControlCenterActualEditor.Tile("control:volume", 1192, 690, 170, 374));
        tiles.add(new ControlCenterActualEditor.Tile("control:device-center", 581, 1098, 781, 181));
        for (int i = 0; i < 12; i++) {
            tiles.add(new ControlCenterActualEditor.Tile("tile" + i, 564 + i % 4 * 204,
                    1302 + i / 4 * 204, 204, 204));
        }
        ControlCenterActualEditor.LayoutData data = new ControlCenterActualEditor.LayoutData(
                1440, 3200, new int[] {204, 374, 612, 781}, tiles);
        data.density = 2.375f;
        ControlCenterCaptureImport result = ControlCenterCaptureImport.from(data, 4, 3);
        assertEquals(4, result.mode.columns);
        assertEquals(14, result.spacingDp);
        assertEquals(18, result.mode.items.size());
        assertTrue(result.mode.validate().isEmpty());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void fixedSizeNativeLayersAreScaledWithoutCroppingOrMutatingBounds() {
        NativeTile view = new NativeTile();
        ImageView image = new ImageView(view.getContext());
        android.graphics.drawable.Drawable glyph = new FourCornerGlyph();
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] {glyph});
        layer.setLayerSize(0, 192, 96);
        layer.setLayerGravity(0, android.view.Gravity.CENTER);
        image.setImageDrawable(layer);
        layer.setBounds(0, 0, 256, 256);
        view.addView(image);
        android.graphics.Rect originalChild = new android.graphics.Rect(glyph.getBounds());
        ControlCenterCapturedStyle result = ControlCenterStyleSampler.read(view, "wifi");
        assertNotNull(result.icon);
        for (int color : new int[] {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW}) {
            boolean found = false;
            for (int y = 0; y < result.icon.getHeight(); y++) for (int x = 0; x < result.icon.getWidth(); x++) {
                if (result.icon.getPixel(x, y) == color) found = true;
            }
            assertTrue("Every native glyph corner must survive", found);
        }
        assertEquals(new android.graphics.Rect(0, 0, 256, 256), layer.getBounds());
        assertEquals(originalChild, glyph.getBounds());
    }

    private static class FourCornerGlyph extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            paint.setColor(Color.RED); canvas.drawRect(b.left, b.top, b.left + 24, b.top + 24, paint);
            paint.setColor(Color.GREEN); canvas.drawRect(b.right - 24, b.top, b.right, b.top + 24, paint);
            paint.setColor(Color.BLUE); canvas.drawRect(b.left, b.bottom - 24, b.left + 24, b.bottom, paint);
            paint.setColor(Color.YELLOW); canvas.drawRect(b.right - 24, b.bottom - 24, b.right, b.bottom, paint);
        }
        @Override public void setAlpha(int value) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    @Test public void visibleTileSurfaceExcludesWrapperMargins() {
        QSTileItemView wrapper = new QSTileItemView();
        wrapper.layout(0, 0, 204, 204);
        wrapper.icon.layout(17, 17, 187, 187);
        assertSame(wrapper.icon, ControlCenterStyleSampler.surfaceView(wrapper));
        wrapper.icon.layout(0, 0, 0, 0);
        assertSame(wrapper, ControlCenterStyleSampler.surfaceView(wrapper));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void glyphSnapshotDoesNotIncludeNativeBackgroundLayers() {
        NativeTile view = new NativeTile();
        ImageView image = new ImageView(view.getContext());
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] {new ColorDrawable(Color.MAGENTA), new FourCornerGlyph()});
        layers.setLayerSize(1, 192, 96);
        layers.setLayerGravity(1, android.view.Gravity.CENTER);
        image.setImageDrawable(layers);
        layers.setBounds(0, 0, 256, 256);
        view.addView(image);
        Bitmap bitmap = ControlCenterStyleSampler.read(view, "wifi").icon;
        assertNotNull(bitmap);
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)));
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2)));
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            assertNotEquals(Color.MAGENTA, bitmap.getPixel(x, y));
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void oldCroppedIconCacheIsIgnoredButItsTextAndStateRemainUsable() throws Exception {
        Bitmap icon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(icon).drawColor(Color.BLUE);
        JSONObject styles = new JSONObject().put("wifi", new ControlCenterCapturedStyle(
                ControlCenterLayoutPlan.Shape.RECTANGLE, 24, 2, "Native WLAN", "Connected", -1, icon).toJson());
        JSONObject packet = new JSONObject().put("version", 3).put("bounds", bounds).put("styles", styles);
        ControlCenterCapturedStyle old = ControlCenterActualEditor.LayoutData.parse(packet.toString()).styles.get("wifi");
        assertNull(old.icon);
        assertEquals("Native WLAN", old.label);
        assertEquals(2, old.state);
        packet.put("iconSampling", 2);
        assertNotNull(ControlCenterActualEditor.LayoutData.parse(packet.toString()).styles.get("wifi").icon);
    }

    private static final class QSTileItemView extends android.widget.FrameLayout {
        final ImageView icon;
        QSTileItemView() {
            super(RuntimeEnvironment.getApplication());
            icon = new ImageView(getContext());
            icon.setImageDrawable(new ColorDrawable(Color.WHITE));
            addView(icon);
        }
    }

    public static class NativeTile extends LinearLayout {
        public final NativeState state = new NativeState();
        NativeTile() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class NativeState { public int state = 2; }

    @Test public void pullingPublishedGroupDoesNotFlattenItsMembersIntoTheOuterGrid() throws Exception {
        var data = ControlCenterGroupData.empty().add("wifi").add("bt").add("cell");
        var item = ControlCenterLayoutPlan.Item.group("group:test", data, 1, 2, 2, 2);
        String plan = ControlCenterLayoutPlan.blank(4).withItem(false, item).encode();
        String id = android.util.Base64.encodeToString("group:test".getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        String json = new JSONObject().put("version", 3).put("bounds", "v1|440|900|" + id + ",24,120,188,188")
                .put("editablePlan", plan).put("spacingDp", 12).put("styles", new JSONObject().put("wifi",
                        new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 0, 2, "WLAN", "Connected", -1, null).toJson())).toString();
        var parsed = ControlCenterActualEditor.LayoutData.parse(json);
        assertNotNull(parsed);
        var imported = ControlCenterCaptureImport.from(parsed, 5, 1);
        assertEquals(1, imported.mode.items.size());
        assertEquals(12, imported.spacingDp);
        assertEquals(item.id, imported.mode.items.get(0).id);
        assertEquals(item.group, imported.mode.items.get(0).group);
        assertEquals(1, imported.mode.items.get(0).x);
        assertEquals(2, imported.mode.items.get(0).y);
        assertEquals("Connected", parsed.styles.get("wifi").subtitle);
    }
}
