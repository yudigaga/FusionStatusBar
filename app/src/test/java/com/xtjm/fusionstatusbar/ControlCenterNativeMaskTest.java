package com.xtjm.fusionstatusbar;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterNativeMaskTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void customCircleRemovesIndependentNativeMasksAndLateAnimationCannotRestoreThem() throws Exception {
        QSTileItemView tile = new QSTileItemView();
        NativeIcon image = new NativeIcon();
        tile.addView(image);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {new ColorDrawable(Color.BLUE)});
        layers.setLayerSize(0, 32, 32);
        image.setImageDrawable(layers);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS).withGlobalBorder(true, 1);
        float[] bloom = new float[21];
        bloom[0] = 4f;
        apply(image, "setMiBloomStroke", new Object[] {bloom}, settings);
        apply(image, "setMiShadow", new Object[] {Color.WHITE, 2f, 3f, 6f, 1f}, settings);
        apply(image, "setMixEffectEnabled", new Object[] {true}, settings);
        apply(image, "setMiViewBlurMode", new Object[] {1}, settings);
        apply(image, "setMiBackgroundBlendColors", new Object[] {new ArrayList<>(List.of(new Point(Color.WHITE, 3)))}, settings);
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        ControlCenterMaterials.syncTileView(tile, settings);
        assertEquals("Native bloom must not overlay the software surface", 0f, image.bloom[0], 0f);
        assertEquals(0, image.shadowColor);
        assertFalse(image.mix);
        assertEquals("The software-backed icon must not retain a second hardware color mask", 0, image.viewMode);
        assertTrue(image.colors.isEmpty());
        bloom[0] = 7f;
        apply(image, "setMiBloomStroke", new Object[] {bloom}, settings);
        apply(image, "setMiViewBlurMode", new Object[] {1}, settings);
        assertEquals(0f, image.bloom[0], 0f);
        assertEquals(0, image.viewMode);
        ControlCenterMaterials.refresh(settings.withEnabled(false));
        assertEquals("Disable restores the latest native request", 7f, image.bloom[0], 0f);
        assertEquals(Color.WHITE, image.shadowColor);
        assertTrue(image.mix);
        assertEquals(1, image.viewMode);
    }

    @Test public void sliderKeepsProgressAndBlurWhileCustomBorderSuppressesNativeRim() throws Exception {
        ToggleSliderView slider = new ToggleSliderView();
        NativeIcon track = new NativeIcon();
        slider.addView(track);
        var settings = ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.SOFT_GLASS);
        float[] bloom = new float[21]; bloom[0] = 3f;
        apply(track, "setMiViewBlurMode", new Object[] {1}, settings);
        apply(track, "setMiBloomStroke", new Object[] {bloom}, settings);
        ControlCenterMaterials.registerRoot(slider, settings);
        assertEquals("Native slider rendering is retained", 1, track.viewMode);
        assertEquals("Its old independent rim is removed", 0f, track.bloom[0], 0f);
        ControlCenterMaterials.refresh(settings.withSurface(ControlCenterSurface.DEFAULT));
        assertEquals(3f, track.bloom[0], 0f);
    }

    @Test public void aTileReboundOutsideTheCustomGridRestoresNativeComposition() throws Exception {
        QSTileItemView tile = new QSTileItemView();
        NativeIcon image = new NativeIcon();
        tile.addView(image);
        LayerDrawable original = new LayerDrawable(new Drawable[] {new ColorDrawable(Color.BLUE)});
        original.setLayerSize(0, 32, 32);
        image.setImageDrawable(original);
        var nativeSettings = ControlCenterConfig.defaults().withEnabled(true);
        apply(image, "setMiViewBlurMode", new Object[] {1}, nativeSettings);
        var glass = nativeSettings.withSurface(ControlCenterSurface.SOFT_GLASS);
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        var outline = image.getOutlineProvider();
        ControlCenterMaterials.syncTileView(tile, glass);
        assertEquals(0, image.viewMode);
        ControlCenterMaterials.nativeTileUpdated(image, nativeSettings);
        assertEquals("A recycled native holder must not retain software-only composition", 1, image.viewMode);
        assertNull(image.getBackground());
        assertSame(outline, image.getOutlineProvider());
    }

    @Test public void enteringNativeEditModeReleasesTheCustomFillAndClip() throws Exception {
        QSTileItemView tile = new QSTileItemView();
        NativeIcon image = new NativeIcon();
        tile.addView(image);
        image.setImageDrawable(new ColorDrawable(Color.BLUE));
        var settings = ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.SOFT_GLASS);
        apply(image, "setMiViewBlurMode", new Object[] {1}, settings);
        var originalOutline = image.getOutlineProvider();
        ControlCenterMaterials.syncTileView(tile, settings);
        assertNotNull(image.getBackground());
        tile.mode = "EDIT";
        ControlCenterMaterials.syncTileView(tile, settings);
        assertNull("Native editing must not keep the custom material", image.getBackground());
        assertSame(originalOutline, image.getOutlineProvider());
        assertEquals(1, image.viewMode);
    }

    @Test public void softwareTileSurfaceDisablesNativeBackgroundBlurForEveryShape() throws Exception {
        for (boolean external : new boolean[] {false, true}) {
            QSTileItemView tile = new QSTileItemView();
            NativeIcon image = new NativeIcon();
            tile.addView(image);
            image.setImageDrawable(new ColorDrawable(Color.BLUE));
            image.backgroundMode = 2;
            image.radius = 90;
            var settings = ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.SOFT_GLASS);
            apply(image, "setMiViewBlurMode", new Object[] {1}, settings);
            ControlCenterMaterials.registerGridTileShape(tile,
                    ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 1).withShape(
                            external ? ControlCenterLayoutPlan.Shape.RECTANGLE
                                    : ControlCenterLayoutPlan.Shape.CIRCLE, 0));
            ControlCenterMaterials.syncTileView(tile, settings, external);
            assertEquals("A software tile must have no native blur mask", 0, image.backgroundMode);
            assertEquals(0, image.radius);
            assertEquals(0, image.viewMode);
            assertEquals(0, ControlCenterMaterials.nativeArguments(image,
                    "setMiBackgroundBlurMode", new Object[] {1}, settings, false)[0]);
            assertEquals(0, ControlCenterMaterials.nativeArguments(image,
                    "setMiBackgroundBlurRadius", new Object[] {180}, settings, false)[0]);
            ControlCenterMaterials.refresh(settings.withEnabled(false));
            assertEquals("The latest OEM blur mode must be restored", 1, image.backgroundMode);
            assertEquals(180, image.radius);
        }
    }

    private static void apply(NativeIcon view, String operation, Object[] args, ControlCenterConfig settings)
            throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation, args, settings, false);
        if (operation.equals("setMixEffectEnabled")) view.setMixEffectEnabled((Boolean) actual[0]);
        else ControlCenterMaterials.invoke(view, operation, actual);
    }

    public static class QSTileItemView extends FrameLayout {
        public String mode = "NORMAL";
        final State state = new State();
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class State { int state = 2; }
    public static class ToggleSliderView extends FrameLayout {
        ToggleSliderView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class NativeIcon extends ImageView {
        float[] bloom = new float[21];
        int shadowColor, viewMode, backgroundMode, radius;
        boolean mix;
        ArrayList<Point> colors = new ArrayList<>();
        NativeIcon() { super(RuntimeEnvironment.getApplication()); }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); }
        public void setMiShadow(int color, float x, float y, float radius, float dispersion) { shadowColor = color; }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }
}
