package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterComponentMaterialTest {
    private final ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true);
    private final ControlCenterConfig glass = original.withSurface(ControlCenterSurface.SOFT_GLASS);

    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void mediaAndDevicesMatchInactiveTileWithoutASecondNativeComposition() throws Exception {
        for (NativePanel panel : panels()) {
            installNativeMaterial(panel);
            NativePanel glyph = new NativePanel();
            glyph.setBackgroundColor(Color.BLUE);
            panel.addView(glyph, new FrameLayout.LayoutParams(20, 20));
            apply(glyph, "setMiViewBlurMode", 3, original);
            apply(glyph, "setMixEffectEnabled", true, original);
            for (ControlCenterSurface style : ControlCenterSurface.values()) {
                if (style == ControlCenterSurface.DEFAULT) continue;
                ControlCenterConfig settings = original.withSurface(style);
                ControlCenterMaterials.registerRoot(panel, settings);
                assertEquals(panel.getClass().getSimpleName(), 0, panel.viewMode);
                assertEquals(0, panel.backgroundMode);
                assertEquals(0, panel.radius);
                assertFalse(panel.mix);
                assertTrue(panel.colors.isEmpty());

                QSTileItemView tile = new QSTileItemView();
                ImageView image = new ImageView(panel.getContext());
                image.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                tile.addView(image, new FrameLayout.LayoutParams(100, 100));
                ControlCenterMaterials.syncTileView(tile, settings);
                layout(tile);
                layout(panel);
                Bitmap tilePixels = draw(tile);
                Bitmap panelPixels = draw(panel);
                assertEquals("Component background must match the inactive tile for " + style,
                        tilePixels.getPixel(50, 50), panelPixels.getPixel(50, 50));
                assertEquals("Inner controls retain their own color", Color.BLUE, panelPixels.getPixel(10, 10));
                assertEquals(3, glyph.viewMode);
                assertTrue(glyph.mix);
                tilePixels.recycle();
                panelPixels.recycle();
            }
        }
    }

    @Test public void lateOemEffectsStaySuppressedAndLatestValuesRestore() throws Exception {
        for (NativePanel panel : panels()) {
            installNativeMaterial(panel);
            ControlCenterMaterials.registerRoot(panel, glass);
            apply(panel, "setMiViewBlurMode", 2, glass);
            apply(panel, "setMiBackgroundBlurMode", 3, glass);
            apply(panel, "setMiBackgroundBlurRadius", 240, glass);
            apply(panel, "setMixEffectEnabled", true, glass);
            apply(panel, "setMiBackgroundBlendColors", colors(Color.CYAN), glass);
            ControlCenterMaterials.updateNativeSurface(panel, glass, false);
            ControlCenterMaterials.refresh(glass);
            assertEquals(0, panel.viewMode);
            assertEquals(0, panel.backgroundMode);
            assertEquals(0, panel.radius);
            assertFalse(panel.mix);
            assertTrue(panel.colors.isEmpty());
            ControlCenterMaterials.refresh(glass.withEnabled(false));
            assertEquals(2, panel.viewMode);
            assertEquals(3, panel.backgroundMode);
            assertEquals(240, panel.radius);
            assertTrue(panel.mix);
            assertEquals(Color.CYAN, panel.colors.get(0).x);
            assertEquals(0xff9fa1a4, ((GradientDrawable) panel.getBackground()).getColor().getDefaultColor());
        }
    }

    @Test public void materialRefreshRecreatedBackgroundAndNativeEditingRespectOwnership() throws Exception {
        for (NativePanel panel : panels()) {
            installNativeMaterial(panel);
            ControlCenterMaterials.registerRoot(panel, original);
            ControlCenterMaterials.refresh(glass);
            assertEquals("Changing only material must take ownership", 0, panel.viewMode);
            panel.setBackground(background(Color.MAGENTA));
            ControlCenterMaterials.registerRoot(panel, glass);
            assertEquals(ControlCenterSurface.SOFT_GLASS.stateColor(false),
                    ((GradientDrawable) panel.getBackground()).getColor().getDefaultColor());
            panel.mode = "EDIT";
            ControlCenterMaterials.registerRoot(panel, glass);
            assertEquals(1, panel.viewMode);
            assertEquals(2, panel.backgroundMode);
            assertEquals(90, panel.radius);
            assertTrue(panel.mix);
            assertEquals(Color.MAGENTA, ((GradientDrawable) panel.getBackground()).getColor().getDefaultColor());
        }
    }

    private NativePanel[] panels() { return new NativePanel[] {new MediaPlayerPanel(), new DeviceCenterEntryFrameLayout()}; }

    private void installNativeMaterial(NativePanel panel) throws Exception {
        panel.setBackground(background(0xff9fa1a4));
        apply(panel, "setMiViewBlurMode", 1, original);
        apply(panel, "setMiBackgroundBlurMode", 2, original);
        apply(panel, "setMiBackgroundBlurRadius", 90, original);
        apply(panel, "setMixEffectEnabled", true, original);
        apply(panel, "setMiBackgroundBlendColors", colors(Color.WHITE), original);
    }

    private static GradientDrawable background(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(12f);
        return background;
    }

    private static ArrayList<Point> colors(int color) { return new ArrayList<>(List.of(new Point(color, 3))); }

    private static void apply(NativePanel view, String operation, Object argument, ControlCenterConfig settings) throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation, new Object[] {argument}, settings, false);
        ControlCenterMaterials.invoke(view, operation, actual);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 100, 100);
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    public static class NativePanel extends FrameLayout {
        public String mode = "NORMAL";
        int viewMode, backgroundMode, radius;
        boolean mix;
        ArrayList<Point> colors = new ArrayList<>();
        NativePanel() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = new ArrayList<>(value); }
        public void clearMiBackgroundBlendColor() { colors.clear(); }
    }

    public static final class MediaPlayerPanel extends NativePanel { }
    public static final class DeviceCenterEntryFrameLayout extends NativePanel { }
    public static final class QSTileItemView extends FrameLayout {
        final State state = new State();
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static final class State { int state = 1; }
}
