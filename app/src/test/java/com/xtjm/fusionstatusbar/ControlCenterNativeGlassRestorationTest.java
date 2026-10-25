package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
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
public class ControlCenterNativeGlassRestorationTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void observedBlendDoesNotPromoteLegacyGlassAndTheLatestOemBaselineRestores() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true);
        ControlCenterConfig highlight = original.withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12f);
        panel.setBackground(background);
        record(panel, "setMiViewBlurMode", 1, original);
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                context, 4, 8, 12, 0, 2);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, highlight);
            assertTrue("A native background with an unknown blend baseline must retain its Canvas fill",
                    backgroundAlpha(panel) > 0);
            assertEquals("Glass must not install a blend which it cannot restore", 0, panel.blendWrites);
            assertTrue(panel.colors.isEmpty());

            record(panel, "setMiBackgroundBlendColors", colors(Color.CYAN), highlight);
            apply(grid, highlight);
            assertEquals("An observed blend does not prove the full native glass ABI", 0, panel.viewMode);
            assertEquals(0, panel.backgroundMode);
            assertEquals(0, panel.radius);
            assertFalse(panel.mix);
            assertTrue("A restorable legacy blend retains compatibility rendering", backgroundAlpha(panel) > 0);

            ArrayList<Point> latest = colors(Color.YELLOW);
            record(panel, "setMiBackgroundBlendColors", latest, highlight);
            apply(grid, highlight.withEnabled(false));
            assertEquals("Disabling must restore the latest real OEM blend, not the glass tint",
                    latest, panel.colors);
            assertEquals(1, panel.viewMode);
            assertEquals(2, panel.backgroundMode);
            assertEquals(180, panel.radius);
            assertFalse(panel.mix);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void restoringPositiveStrengthReplaysTheLatestObservedNativeScaleRatio() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true);
        ControlCenterConfig highlight = original.withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        ControlCenterConfig transparent = highlight.withBlur(100, 0, 0);
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12f);
        panel.setBackground(background);
        record(panel, "setMiViewBlurMode", 1, original);
        record(panel, "setMiBackgroundBlendColors", colors(Color.CYAN), original);
        record(panel, "setMiBackgroundBlurScaleRatio", 1f, original);
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                context, 4, 8, 12, 0, 2);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, transparent);
            // The native expansion callback still publishes its unscaled ratio while the
            // configured material endpoint is transparent.
            record(panel, "setMiBackgroundBlurScaleRatio", 1f, transparent);
            assertEquals("The transparent endpoint scales the native callback to zero",
                    0f, panel.scaleRatio, 0f);
            apply(grid, highlight);
            assertEquals("Restoring glass must not leave its native sampling scale stuck at zero",
                    1f, panel.scaleRatio, 0f);
            assertEquals("Canvas compatibility retains ownership independently of the OEM sampling ratio", 0,
                    panel.backgroundMode);
            assertEquals(0, panel.radius);
            assertTrue(backgroundAlpha(panel) > 0);

            record(panel, "setMiBackgroundBlurScaleRatio", .7f, highlight);
            apply(grid, highlight.withEnabled(false));
            assertEquals("Disabling restores the latest unscaled OEM ratio", .7f, panel.scaleRatio, 0f);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void lowPositiveStrengthRoundTripKeepsTheCompatibilityBackgroundRestorable() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true);
        ControlCenterConfig highlight = original.withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12f);
        panel.setBackground(background);
        record(panel, "setMiViewBlurMode", 1, original);
        record(panel, "setMiBackgroundBlurMode", 2, original);
        record(panel, "setMiBackgroundBlurRadius", 20, original);
        record(panel, "setMiBackgroundBlendColors", colors(Color.CYAN), original);
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                context, 4, 8, 12, 0, 2);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        try {
            apply(grid, highlight);
            int fullAlpha = backgroundAlpha(panel);
            assertTrue(fullAlpha > 0);
            apply(grid, highlight.withBlur(100, 1, 1));
            assertEquals("Low strength must not activate the retired native takeover", 0, panel.viewMode);
            assertTrue("Very low alpha can round down but must never increase opacity", backgroundAlpha(panel) <= fullAlpha);
            apply(grid, highlight);
            assertEquals("Full strength restores the compatibility opacity after the low endpoint", fullAlpha,
                    backgroundAlpha(panel));
            assertEquals(0, panel.viewMode);
            assertFalse(panel.mix);
            apply(grid, highlight.withEnabled(false));
            assertEquals("Disabling restores the real OEM radius", 20, panel.radius);
            assertEquals(1, panel.viewMode);
            assertEquals(255, backgroundAlpha(panel));
        } finally { grid.releaseMaterials(); }
    }

    private static void record(View view, String operation, Object argument,
            ControlCenterConfig config) throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation,
                new Object[] {argument}, config, false);
        ControlCenterMaterials.invoke(view, operation, actual);
    }

    private static ArrayList<Point> colors(int color) {
        return new ArrayList<>(List.of(new Point(color, 3)));
    }

    private static void apply(ControlCenterRuntimeGrid.GridView grid, ControlCenterConfig config) {
        // Runtime reload refreshes retained OEM requests before updating the mounted grid.
        ControlCenterMaterials.refresh(config);
        grid.applyMaterials(config);
        grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 400, 240);
        Bitmap image = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
        try { grid.draw(new Canvas(image)); }
        finally { image.recycle(); }
    }

    private static int backgroundAlpha(View view) {
        Drawable background = view.getBackground();
        assertNotNull(background);
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        try {
            background.setBounds(0, 0, 100, 100);
            background.draw(new Canvas(image));
            return Color.alpha(image.getPixel(50, 50));
        } finally { image.recycle(); }
    }

    /** Directly tracked owner exposes setters but has no unobserved-effect getters. */
    public static final class MediaPlayerPanel extends FrameLayout {
        int viewMode, backgroundMode = 2, radius = 180, blendWrites;
        float scaleRatio = 1f;
        boolean mix;
        float[] bloom = new float[21];
        ArrayList<Point> colors = new ArrayList<>();
        MediaPlayerPanel(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlurScaleRatio(float value) { scaleRatio = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) {
            colors = new ArrayList<>(value);
            blendWrites++;
        }
        public void clearMiBackgroundBlendColor() { colors.clear(); }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); }
    }
}
