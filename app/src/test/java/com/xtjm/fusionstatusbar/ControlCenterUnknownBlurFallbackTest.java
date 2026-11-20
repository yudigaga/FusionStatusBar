package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLog;
import static org.junit.Assert.*;

/** UNKNOWN uses reversible Canvas compatibility; API presence alone does not authorize native glass. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterUnknownBlurFallbackTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void positiveAutomaticAmountsApplyCanvasTintAndKeepTextUpToDateInEitherTheme() {
        for (boolean night : new boolean[] {false, true}) for (int amount : new int[] {30, 100}) {
            QSCardItemView card = new QSCardItemView(context(night));
            GradientDrawable original = new GradientDrawable();
            original.setColor(0xff353840); original.setAlpha(143);
            PorterDuffColorFilter filter = new PorterDuffColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
            original.setColorFilter(filter); card.setBackground(original);
            TextView label = new TextView(card.getContext());
            ColorStateList colors = new ColorStateList(new int[][] {{android.R.attr.state_pressed}, {}},
                    new int[] {Color.BLUE, 0xcc000000});
            label.setTextColor(colors); card.addView(label);
            ControlCenterConfig automatic = automatic(amount);
            ControlCenterMaterials.registerRoot(card, automatic);
            assertEquals(night ? ControlCenterSurface.SOFT_GLASS.color
                    : ControlCenterSurface.HIGHLIGHT_GLASS.color,
                    original.getColor().getDefaultColor());
            assertEquals(Math.round(143f * amount / 100f), original.getAlpha());
            assertEquals(null, original.getColorFilter());
            assertNotSame(colors, label.getTextColors());
            ColorStateList latest = ColorStateList.valueOf(Color.MAGENTA);
            label.setTextColor(ControlCenterMaterials.textColor(label, latest, automatic));
            ControlCenterMaterials.refresh(automatic);
            assertSame(latest, label.getTextColors());
            assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS, automatic.surface);
        }
    }

    @Test public void mixedZeroAndPositiveAmountsAreDecidedBeforeNightResolution() {
        ControlCenterConfig mixed = automatic(30).withBlur(0, 0, 30);
        assertEquals(0, ControlCenterMaterials.percent(mixed, ControlCenterMaterials.Layer.CARD, false));
        assertEquals(30, ControlCenterMaterials.percent(mixed, ControlCenterMaterials.Layer.TILE, false));
        QSCardItemView card = new QSCardItemView(context(true));
        GradientDrawable original = new GradientDrawable(); original.setColor(Color.GRAY); card.setBackground(original);
        ControlCenterMaterials.registerRoot(card, mixed);
        assertEquals(0, original.getAlpha());
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, original.getColor().getDefaultColor());
        ControlCenterConfig manual = mixed.withSurface(ControlCenterSurface.SOFT_GLASS).withBlur(30, 30, 30);
        ControlCenterMaterials.refresh(manual);
        assertEquals(77, original.getAlpha());
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, original.getColor().getDefaultColor());
        ControlCenterMaterials.refresh(manual.withSurface(ControlCenterSurface.CLEAR));
        assertEquals(Color.TRANSPARENT, original.getColor().getDefaultColor());
    }

    @Test public void canvasCircleKeepsLatestNativeRequestsAndItsUserGeometry() throws Exception {
        for (int active : new int[] {1, 2}) {
            QSTileItemView tile = new QSTileItemView(context(true)); tile.state = active;
            NativeIcon icon = new NativeIcon(tile.getContext());
            ColorDrawable nativeBackground = new ColorDrawable(0xff353840); icon.setBackground(nativeBackground);
            ColorDrawable glyph = new ColorDrawable(Color.BLUE); icon.setImageDrawable(glyph); tile.addView(icon);
            ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 17));
            ControlCenterConfig manual = automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS);
            install(icon, manual.withEnabled(false));
            ControlCenterMaterials.syncTileView(tile, manual);
            ViewOutlineProvider shape = icon.getOutlineProvider();
            assertEquals(0, icon.radius);
            record(icon, "setMiViewBlurMode", 2, manual);
            record(icon, "setMiBackgroundBlurMode", 3, manual);
            record(icon, "setMiBackgroundBlurRadius", 240, manual);
            record(icon, "setMiBackgroundBlendColors", colors(Color.CYAN), manual);
            record(icon, "setMixEffectEnabled", true, manual);
            float[] bloom = new float[21]; bloom[0] = 3; bloom[13] = .4f;
            record(icon, "setMiBloomStroke", bloom, manual);
            ControlCenterMaterials.syncTileView(tile, automatic(30));
            assertNotSame(nativeBackground, icon.getBackground());
            assertSame(glyph, icon.getDrawable());
            assertSame("Canvas compatibility retains geometry ownership", shape, icon.getOutlineProvider());
            assertEquals(2, icon.viewMode); assertEquals(3, icon.backgroundMode); assertEquals(240, icon.radius);
            assertEquals(colors(Color.CYAN), icon.colors); assertTrue(icon.mix); assertArrayEquals(bloom, icon.bloom, 0f);
            ControlCenterMaterials.refresh(automatic(100));
            assertEquals(240, icon.radius); assertTrue(icon.mix);
            tile.mode = "EDIT"; ControlCenterMaterials.syncTileView(tile, automatic(30));
            tile.mode = "NORMAL";
            ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 17));
            ControlCenterMaterials.syncTileView(tile, automatic(30).withEnabled(false));
            ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 17));
            ControlCenterMaterials.syncTileView(tile, automatic(30));
            assertNotSame(nativeBackground, icon.getBackground()); assertEquals(240, icon.radius);
        }
    }

    @Test public void externalRectangleKeepsSoftwareOwnershipAndHighlightBacking() throws Exception {
        QSTileItemView tile = new QSTileItemView(context(false));
        NativeIcon icon = new NativeIcon(tile.getContext()); icon.setImageDrawable(new ColorDrawable(Color.BLUE));
        tile.addView(icon); install(icon, automatic(30).withEnabled(false));
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 14));
        View backing = new View(tile.getContext()); GradientDrawable fill = new GradientDrawable(); backing.setBackground(fill);
        for (int state : new int[] {1, 2}) {
            tile.state = state;
            ControlCenterMaterials.syncTileView(tile, automatic(30), true);
            ControlCenterMaterials.applyTileBacking(backing, tile, automatic(30));
            assertEquals(1, icon.viewMode); assertEquals(2, icon.backgroundMode); assertEquals(180, icon.radius);
            assertEquals(state == 2 ? 0x4df8f9fb : 0x07ffffff,
                    fill.getColor().getDefaultColor());
            assertEquals(255, icon.getDrawable().getAlpha());
        }
    }

    @Test public void disablingManualGlassRestoresTheOriginalDrawablesAndOutline() {
        QSTileItemView tile = new QSTileItemView(context(false)); NativeIcon icon = new NativeIcon(tile.getContext());
        ColorDrawable nativeFill = new ColorDrawable(Color.GRAY), glyph = new ColorDrawable(Color.BLUE);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {nativeFill, glyph});
        layers.setLayerSize(1, 20, 20); icon.setImageDrawable(layers); tile.addView(icon);
        ViewOutlineProvider originalOutline = icon.getOutlineProvider();
        boolean originalClipping = icon.getClipToOutline();
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1));
        ControlCenterMaterials.syncTileView(tile, automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertNotSame(nativeFill, layers.getDrawable(0));
        assertNotSame(originalOutline, icon.getOutlineProvider());
        ControlCenterMaterials.syncTileView(tile, ControlCenterConfig.defaults());
        assertSame(nativeFill, layers.getDrawable(0)); assertSame(glyph, layers.getDrawable(1));
        assertSame(originalOutline, icon.getOutlineProvider());
        assertEquals(originalClipping, icon.getClipToOutline());
    }

    @Test public void canvasLayerRestoresTheOemCircleWhenDisabled() throws Exception {
        QSTileItemView tile = new QSTileItemView(context(false));
        NativeIcon icon = new NativeIcon(tile.getContext());
        ColorDrawable nativeFill = new ColorDrawable(Color.GRAY), glyph = new ColorDrawable(Color.BLUE);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {nativeFill, glyph});
        layers.setLayerSize(1, 20, 20);
        icon.setImageDrawable(layers);
        tile.addView(icon);
        ViewOutlineProvider originalOutline = icon.getOutlineProvider();
        boolean originalClipping = icon.getClipToOutline();
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        ShadowLog.clear();
        ControlCenterMaterials.syncTileView(tile, automatic(30));
        Drawable compatibilityFill = layers.getDrawable(0);
        assertNotSame(nativeFill, compatibilityFill);
        assertTrue(icon.getClipToOutline());
        assertEquals(0, materialRestoreCount("circle"));

        ControlCenterMaterials.syncTileView(tile, automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertSame(compatibilityFill, layers.getDrawable(0));
        ControlCenterMaterials.syncTileView(tile, ControlCenterConfig.defaults());
        assertSame(nativeFill, layers.getDrawable(0));
        assertSame(glyph, layers.getDrawable(1));
        assertSame(originalOutline, icon.getOutlineProvider());
        assertEquals(originalClipping, icon.getClipToOutline());
        assertEquals(0, materialRestoreCount("circle"));
    }

    @Test public void aMediaRestoreRecordsTheDrawableReplacedDuringMaterialRefresh() {
        MediaPlayerPanel panel = new MediaPlayerPanel(context(false));
        Drawable nativeBackground = new ColorDrawable(Color.CYAN);
        panel.setBackground(nativeBackground);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.MEDIA, 0, 0, 2, 1);
        ControlCenterMaterials.syncComponentView(panel, item, automatic(30));
        ShadowLog.clear();
        assertEquals(0, materialRestoreCount("media"));

        ControlCenterMaterials.syncComponentView(panel, item,
                automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertNotSame(nativeBackground, panel.getBackground());
        ControlCenterMaterials.refresh(automatic(30));
        ControlCenterMaterials.syncComponentView(panel, item, ControlCenterConfig.defaults());
        assertSame(nativeBackground, panel.getBackground());
        assertEquals(1, materialRestoreCount("media"));
    }

    @Test public void removingACircleWrapperWithNoOemBackgroundDoesNotReportAFalseRestore() {
        QSTileItemView tile = new QSTileItemView(context(false));
        NativeIcon icon = new NativeIcon(tile.getContext());
        icon.setImageDrawable(new ColorDrawable(Color.BLUE));
        tile.addView(icon);
        assertNull(icon.getBackground());
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        ControlCenterMaterials.syncTileView(tile, automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertNotNull(icon.getBackground());
        ShadowLog.clear();
        ControlCenterMaterials.syncTileView(tile, ControlCenterConfig.defaults());
        assertNull(icon.getBackground());
        assertEquals(0, materialRestoreCount("circle"));
    }

    @Test public void removingAMediaWrapperWithNoOemBackgroundDoesNotReportAFalseRestore() {
        MediaPlayerPanel panel = new MediaPlayerPanel(context(false));
        assertNull(panel.getBackground());
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.MEDIA, 0, 0, 2, 1);
        ControlCenterMaterials.syncComponentView(panel, item,
                automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertNotNull(panel.getBackground());
        ShadowLog.clear();
        ControlCenterMaterials.refresh(ControlCenterConfig.defaults());
        assertNull(panel.getBackground());
        assertEquals(0, materialRestoreCount("media"));
    }

    @Test public void externalCapsuleSnapshotIdentifiesItsMeasuredSoftwareBacking() throws Exception {
        QSTileItemView tile = new QSTileItemView(context(false));
        NativeIcon icon = new NativeIcon(tile.getContext());
        icon.setImageDrawable(new ColorDrawable(Color.BLUE));
        tile.addView(icon);
        install(icon, automatic(30).withEnabled(false));
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                tile.getContext(), 4, 8, 24, 0, 1);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 14), 0);
        ShadowLog.clear();
        grid.applyMaterials(automatic(30));
        grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 400, 200);
        Bitmap image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
        try {
            grid.draw(new Canvas(image));
            grid.draw(new Canvas(image));
            assertEquals(1, ShadowLog.getLogsForTag("FusionStatusBar").stream()
                    .filter(log -> log.msg.startsWith("control-center material snapshot role=external_backing ")
                            && log.msg.contains("shape=CAPSULE")
                            && log.msg.contains("backing=GradientDrawable")
                            && log.msg.contains("softwareSurfaceFlag=true"))
                    .count());
            assertEquals(0, materialRestoreCount("circle"));
        } finally {
            image.recycle();
            grid.releaseMaterials();
        }
    }

    private long materialRestoreCount(String role) {
        return ShadowLog.getLogsForTag("FusionStatusBar").stream()
                .filter(log -> log.msg.startsWith("control-center material drawable restored role=" + role + " "))
                .count();
    }

    @Test public void sessionRefreshKeepsCanvasSurfaceAndRestoresTheLatestComponentBackground() {
        MediaPlayerPanel panel = new MediaPlayerPanel(context(true));
        ColorDrawable first = new ColorDrawable(Color.GRAY); panel.setBackground(first);
        ControlCenterMaterialSession session = new ControlCenterMaterialSession(panel);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 0, 0, 2, 1);
        session.applyComponentBackground(item, automatic(0));
        assertEquals(0, centerAlpha(panel.getBackground()));
        session.refresh(automatic(30));
        assertNotSame(first, panel.getBackground());
        ColorDrawable latest = new ColorDrawable(Color.CYAN); panel.setBackground(latest);
        session.applyComponentBackground(item, automatic(0)); session.refresh(automatic(100));
        assertNotSame(latest, panel.getBackground());
        session.refresh(ControlCenterConfig.defaults());
        assertSame(latest, panel.getBackground());
        session.applyComponentBackground(item, automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertEquals(58, centerAlpha(panel.getBackground()));
    }

    @Test public void privatePanelRatioUsesLatestRawRequestAndPositiveFallbackNeverCompoundsIt() {
        WindowController owner = new WindowController();
        assertEquals(.6f, ControlCenterMaterials.windowRatio(owner, .6f, automatic(30), false), 0f);
        ControlCenterMaterials.refresh(automatic(100)); assertEquals(.6f, owner.ratio, 0f);
        assertEquals(0f, ControlCenterMaterials.windowRatio(owner, .7f, automatic(0), false), 0f);
        ControlCenterMaterials.refresh(automatic(30)); assertEquals(.7f, owner.ratio, 0f);
        assertEquals(.21f, ControlCenterMaterials.windowRatio(owner, .7f,
                automatic(30).withSurface(ControlCenterSurface.SOFT_GLASS), false), .0001f);
    }

    private static ControlCenterConfig automatic(int amount) {
        return ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(false, 1)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(amount, amount, amount);
    }
    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication(); Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(config);
    }
    private static int centerAlpha(Drawable background) {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        try { background.setBounds(0, 0, 100, 100); background.draw(new Canvas(bitmap)); return Color.alpha(bitmap.getPixel(50, 50)); }
        finally { bitmap.recycle(); }
    }
    private static ArrayList<Point> colors(int color) { return new ArrayList<>(List.of(new Point(color, 3))); }
    private static void install(NativeIcon icon, ControlCenterConfig config) throws Exception {
        record(icon, "setMiViewBlurMode", 1, config); record(icon, "setMiBackgroundBlurMode", 2, config);
        record(icon, "setMiBackgroundBlurRadius", 180, config); record(icon, "setMiBackgroundBlendColors", colors(Color.WHITE), config);
        record(icon, "setMixEffectEnabled", false, config); record(icon, "setMiBloomStroke", new float[21], config);
    }
    private static void record(View owner, String operation, Object argument, ControlCenterConfig config) throws Exception {
        Object[] args = ControlCenterMaterials.nativeArguments(owner, operation, new Object[] {argument}, config, false);
        ControlCenterMaterials.invoke(owner, operation, args);
    }
    public static final class QSCardItemView extends FrameLayout { QSCardItemView(Context context) { super(context); } }
    public static final class MediaPlayerPanel extends FrameLayout { MediaPlayerPanel(Context context) { super(context); } }
    public static final class QSTileItemView extends FrameLayout {
        int state = 1; String mode = "NORMAL"; QSTileItemView(Context context) { super(context); }
    }
    public static final class NativeIcon extends ImageView {
        int viewMode, backgroundMode, radius; boolean mix; float[] bloom = new float[21]; ArrayList<Point> colors = new ArrayList<>();
        NativeIcon(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = new ArrayList<>(value); }
        public void clearMiBackgroundBlendColor() { colors.clear(); }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); }
    }
    public static final class WindowController { float ratio; public void setBlurRatio(float value) { ratio = value; } }
}
