package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterHighlightGlassNativePathRuntimeTest {
    private final ControlCenterConfig nativeStyle = ControlCenterConfig.defaults().withEnabled(true);
    private final ControlCenterConfig highlight = nativeStyle.withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
            .withBlur(100, 100, 100).withGlobalBorder(false, 1);

    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void runtimeMediaAndDeviceCenterUseCanvasWithoutAssumingLegacyApisProveFullGlass() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (NativePanel panel : new NativePanel[] {new MediaPlayerPanel(context),
                new DeviceCenterEntryFrameLayout(context)}) {
            panel.setBackground(background());
            installNativeRequests(panel);
            assertEquals("The fixture starts with a working native surface", 1, panel.viewMode);
            ControlCenterRuntimeGrid.GridView grid = grid(context);
            String spec = panel instanceof MediaPlayerPanel ? ControlCenterComponentSpec.MEDIA
                    : ControlCenterComponentSpec.DEVICE_CENTER;
            grid.addTile(panel, ControlCenterLayoutPlan.Item.component(spec, 0, 0, 2, 2), -1);
            try {
                apply(grid, highlight);
                assertEquals("The established Canvas surface owns the component fill", 0, panel.viewMode);
                assertEquals(0, panel.backgroundMode);
                assertEquals(0, panel.radius);
                assertFalse("Legacy mix availability must not enable an unverified glass material", panel.mix);
                assertFalse("Legacy bloom availability must not invent a glass profile", positive(panel.bloom));
                assertTrue("Compatibility rendering retains a visible component body", backgroundAlpha(panel) > 0);
            } finally { grid.releaseMaterials(); }
        }
    }

    @Test public void runtimeTileImageRetainsSoftwareOwnershipAndAnIndependentGlyph() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        QSTileItemView tile = new QSTileItemView(context);
        QSTileItemIconView icon = new QSTileItemIconView(context);
        icon.setImageDrawable(new ColorDrawable(Color.BLUE));
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        installNativeRequests(icon);
        assertEquals("The fixture starts with a working native surface", 1, icon.viewMode);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("rotation", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
        try {
            apply(grid, highlight);
            assertEquals(0, icon.viewMode);
            assertEquals(0, icon.backgroundMode);
            assertEquals(0, icon.radius);
            assertFalse(icon.mix);
            assertFalse(positive(icon.bloom));
            assertTrue("The tile keeps a visible Canvas backing", backgroundAlpha(icon) > 0);
            assertEquals("The glyph remains independent of the background", 255, icon.getDrawable().getAlpha());
        } finally { grid.releaseMaterials(); }
    }

    @Test public void observedModeAndBlendAloneDoNotPromoteLegacyOwnersToAFullGlassBackend() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (int mode : new int[] {1, 3}) {
            MediaPlayerPanel panel = new MediaPlayerPanel(context);
            panel.setBackground(background());
            panel.backgroundMode = 2; panel.radius = 180;
            record(panel, "setMiViewBlurMode", mode, nativeStyle);
            record(panel, "setMiBackgroundBlendColors", colors(Color.WHITE), nativeStyle);
            ControlCenterRuntimeGrid.GridView grid = grid(context);
            grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                    0, 0, 2, 2), -1);
            try {
                apply(grid, highlight);
                assertEquals("A Canvas-owned component must not be forced to the experimental native mode1", 0, panel.viewMode);
                assertEquals(0, panel.backgroundMode);
                assertEquals(0, panel.radius);
                assertFalse("No native mix request must mean no invented native mix effect", panel.mix);
                assertFalse("No native bloom request must mean no invented native glass profile", positive(panel.bloom));
                assertTrue(backgroundAlpha(panel) > 0);
            } finally { grid.releaseMaterials(); }
        }

        QSTileItemView tile = new QSTileItemView(context);
        QSTileItemIconView icon = new QSTileItemIconView(context);
        icon.setImageDrawable(new ColorDrawable(Color.BLUE));
        icon.backgroundMode = 2; icon.radius = 180;
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        record(icon, "setMiViewBlurMode", 1, nativeStyle);
        record(icon, "setMiBackgroundBlendColors", colors(Color.WHITE), nativeStyle);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("rotation", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
        try {
            apply(grid, highlight);
            assertEquals(0, icon.viewMode);
            assertFalse(icon.mix);
            assertFalse(positive(icon.bloom));
            assertTrue(backgroundAlpha(icon) > 0);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void compatibilityStrengthClearsOnlyTheBackgroundAndRestoresWithoutCumulativeScaling() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        panel.setBackground(background());
        View control = new View(context);
        control.setBackgroundColor(Color.BLUE);
        panel.addView(control, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
        installNativeRequests(panel);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, highlight);
            int fullAlpha = backgroundAlpha(panel);
            assertTrue(fullAlpha > 0);
            apply(grid, highlight.withBlur(100, 50, 100));
            assertEquals("Half strength scales the compatibility background once", Math.round(fullAlpha * .5f),
                    backgroundAlpha(panel), 1);
            apply(grid, highlight.withBlur(100, 0, 0));
            assertEquals(0, panel.viewMode);
            assertEquals(0, panel.backgroundMode);
            assertEquals(0, panel.radius);
            assertFalse(panel.mix);
            assertFalse(positive(panel.bloom));
            assertTrue("Zero strength clears the native blend", panel.colors.isEmpty());
            assertTransparentBackground(panel);
            Bitmap pixels = draw(panel);
            try { assertEquals("Foreground controls remain opaque at zero background strength", Color.BLUE,
                    pixels.getPixel(panel.getWidth() / 2, panel.getHeight() / 2)); }
            finally { pixels.recycle(); }
            apply(grid, highlight);
            assertEquals("Returning to full strength restores the compatibility background", fullAlpha, backgroundAlpha(panel));
            assertEquals(0, panel.radius);
            assertFalse(panel.mix);
            assertFalse(positive(panel.bloom));
        } finally { grid.releaseMaterials(); }
    }

    @Test public void sliderTrackKeepsCompatibilityRenderingWhileProgressRemainsNativeAndOpaque() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ToggleSliderView slider = new ToggleSliderView(context);
        NativeTrack track = new NativeTrack(context);
        track.setBackground(background());
        slider.addView(track, new FrameLayout.LayoutParams(-1, -1));
        NativePanel progress = new NativePanel(context);
        progress.setId(android.R.id.progress);
        progress.setBackgroundColor(Color.WHITE);
        slider.addView(progress, new FrameLayout.LayoutParams(-1, 30, Gravity.BOTTOM));
        installNativeRequests(track);
        installNativeRequests(progress);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(slider, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.BRIGHTNESS,
                0, 0, 1, 2), -1);
        try {
            apply(grid, highlight);
            assertFalse("The slider track must not synthesize an unverified native mix effect", track.mix);
            assertFalse(positive(track.bloom));
            assertTrue("The track keeps its compatibility body", backgroundAlpha(track) > 0);
            apply(grid, highlight.withBlur(100, 0, 0));
            assertEquals(0, track.viewMode);
            assertEquals(0, track.radius);
            assertFalse(track.mix);
            assertEquals("Foreground progress retains its original native mode", 1, progress.viewMode);
            assertEquals(2, progress.backgroundMode);
            assertEquals(180, progress.radius);
            assertEquals(Color.WHITE, ((ColorDrawable) progress.getBackground()).getColor());
            assertEquals(1f, progress.getAlpha(), 0f);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void leavingCompatibilityGlassRestoresLatestOemRequestsAndManualSoftGlassKeepsSoftwareOwnership()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (boolean disabled : new boolean[] {false, true}) {
            MediaPlayerPanel panel = new MediaPlayerPanel(context);
            panel.setBackground(background());
            installNativeRequests(panel);
            ControlCenterRuntimeGrid.GridView grid = grid(context);
            grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                    0, 0, 2, 2), -1);
            try {
                apply(grid, highlight);
                ControlCenterConfig soft = highlight.withSurface(ControlCenterSurface.SOFT_GLASS);
                apply(grid, soft);
                assertEquals("The existing manual soft-glass style retains software ownership", 0, panel.viewMode);
                assertEquals(0, panel.backgroundMode);
                assertEquals(0, panel.radius);
                assertFalse(panel.mix);
                assertFalse(positive(panel.bloom));
                assertTrue("Manual soft glass continues to paint its Canvas material", backgroundAlpha(panel) > 0);
                apply(grid, highlight);
                assertFalse("Returning to highlight compatibility must not activate a native glass profile", positive(panel.bloom));
                float[] latestBloom = new float[21];
                latestBloom[0] = 3f; latestBloom[13] = .4f;
                record(panel, "setMiViewBlurMode", 2, highlight);
                record(panel, "setMiBackgroundBlurMode", 3, highlight);
                record(panel, "setMiBackgroundBlurRadius", 240, highlight);
                record(panel, "setMiBackgroundBlendColors", colors(Color.CYAN), highlight);
                record(panel, "setMixEffectEnabled", true, highlight);
                record(panel, "setMiBloomStroke", latestBloom, highlight);
                ControlCenterMaterials.updateNativeSurface(panel, highlight, false);
                apply(grid, disabled ? highlight.withEnabled(false) : nativeStyle);
                assertEquals(2, panel.viewMode);
                assertEquals(3, panel.backgroundMode);
                assertEquals(240, panel.radius);
                assertEquals(colors(Color.CYAN), panel.colors);
                assertTrue(panel.mix);
                assertArrayEquals(latestBloom, panel.bloom, 0f);
            } finally { grid.releaseMaterials(); }
        }
    }

    @Test public void missingNativeApiFallsBackToVisibleCanvasWithoutHidingContent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        MissingBloom.MediaPlayerPanel panel = new MissingBloom.MediaPlayerPanel(context);
        panel.setBackground(background());
        View control = new View(context);
        control.setBackgroundColor(Color.BLUE);
        panel.addView(control, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
        record(panel, "setMiViewBlurMode", 1, nativeStyle);
        record(panel, "setMiBackgroundBlendColors", colors(Color.WHITE), nativeStyle);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, highlight);
            assertTrue("An incomplete native API set must retain a visible fallback", backgroundAlpha(panel) > 0);
            Bitmap image = draw(panel);
            try { assertEquals(Color.BLUE, image.getPixel(panel.getWidth() / 2, panel.getHeight() / 2)); }
            finally { image.recycle(); }
        } finally { grid.releaseMaterials(); }
    }

    @Test public void stableGridFramesDoNotReplayCompatibilityOwnershipSetters() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        panel.setBackground(background());
        installNativeRequests(panel);
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, highlight);
            int calls = panel.nativeCalls;
            for (int frame = 0; frame < 8; frame++) {
                draw(grid).recycle();
            }
            assertEquals("Unchanged grid frames retain ownership without native setter replay", calls, panel.nativeCalls);
        } finally { grid.releaseMaterials(); }
    }

    private void installNativeRequests(View view) throws Exception {
        record(view, "setMiViewBlurMode", 1, nativeStyle);
        record(view, "setMiBackgroundBlurMode", 2, nativeStyle);
        record(view, "setMiBackgroundBlurRadius", 180, nativeStyle);
        record(view, "setMiBackgroundBlendColors", colors(Color.WHITE), nativeStyle);
        record(view, "setMixEffectEnabled", false, nativeStyle);
        record(view, "setMiBloomStroke", new float[21], nativeStyle);
    }

    private static void record(View view, String operation, Object argument, ControlCenterConfig config)
            throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation, new Object[] {argument}, config, false);
        ControlCenterMaterials.invoke(view, operation, actual);
    }

    private static ArrayList<Point> colors(int color) {
        return new ArrayList<>(List.of(new Point(color, 3)));
    }

    private static boolean positive(float[] bloom) {
        return bloom != null && bloom.length == 21 && bloom[0] > 0f && bloom[13] > 0f;
    }

    private static void assertTransparentBackground(View view) {
        assertEquals("Zero strength must remove the compatibility background fill", 0, backgroundAlpha(view));
    }

    private static int backgroundAlpha(View view) {
        Drawable background = view.getBackground();
        if (background == null) return 0;
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        try {
            background.setBounds(0, 0, 100, 100);
            background.draw(new Canvas(image));
            return Color.alpha(image.getPixel(50, 50));
        } finally { image.recycle(); }
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static GradientDrawable background() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12);
        return background;
    }

    private static ControlCenterRuntimeGrid.GridView grid(Context context) {
        return new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 2);
    }

    private static void apply(ControlCenterRuntimeGrid.GridView grid, ControlCenterConfig config) {
        grid.applyMaterials(config);
        grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 400, 240);
        Bitmap image = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
        try { grid.draw(new Canvas(image)); }
        finally { image.recycle(); }
    }

    public static class NativePanel extends FrameLayout {
        int viewMode, backgroundMode, radius, nativeCalls;
        boolean mix;
        float[] bloom = new float[21];
        ArrayList<Point> colors = new ArrayList<>();
        NativePanel(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; nativeCalls++; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; nativeCalls++; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; nativeCalls++; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = new ArrayList<>(value); nativeCalls++; }
        public void clearMiBackgroundBlendColor() { colors.clear(); nativeCalls++; }
        public void setMixEffectEnabled(boolean value) { mix = value; nativeCalls++; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); nativeCalls++; }
    }

    public static final class MediaPlayerPanel extends NativePanel {
        MediaPlayerPanel(Context context) { super(context); }
    }
    public static final class DeviceCenterEntryFrameLayout extends NativePanel {
        DeviceCenterEntryFrameLayout(Context context) { super(context); }
    }
    public static final class QSTileItemView extends FrameLayout {
        int state = 1;
        QSTileItemView(Context context) { super(context); }
    }
    public static final class ToggleSliderView extends FrameLayout {
        ToggleSliderView(Context context) { super(context); }
    }

    public static final class NativeTrack extends NativePanel {
        private static final int TRACK_ID = 0x4567091;
        private Resources hostResources;
        @SuppressWarnings("deprecation")
        NativeTrack(Context context) {
            super(context);
            setId(TRACK_ID);
            Resources resources = super.getResources();
            hostResources = new Resources(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration()) {
                @Override public String getResourceEntryName(int id) {
                    return id == TRACK_ID ? "progress_bg" : super.getResourceEntryName(id);
                }
            };
        }
        @Override public Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
    }

    public static final class MissingBloom {
        public static final class MediaPlayerPanel extends FrameLayout {
            int mode = 2, radius = 180;
            MediaPlayerPanel(Context context) { super(context); }
            public int getMiBackgroundBlurMode() { return mode; }
            public int getMiBackgroundBlurRadius() { return radius; }
            public void setMiViewBlurMode(int value) { }
            public void setMiBackgroundBlurMode(int value) { mode = value; }
            public void setMiBackgroundBlurRadius(int value) { radius = value; }
            public void setMiBackgroundBlendColors(ArrayList<Point> value) { }
            public void clearMiBackgroundBlendColor() { }
            public void setMixEffectEnabled(boolean value) { }
        }
    }
    public static final class QSTileItemIconView extends ImageView {
        int viewMode, backgroundMode, radius;
        boolean mix;
        float[] bloom = new float[21];
        ArrayList<Point> colors = new ArrayList<>();
        QSTileItemIconView(Context context) { super(context); }
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
}
