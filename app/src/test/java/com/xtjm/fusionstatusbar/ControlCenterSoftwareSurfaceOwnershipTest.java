package com.xtjm.fusionstatusbar;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
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
public class ControlCenterSoftwareSurfaceOwnershipTest {
    private static final ControlCenterLayoutPlan.Shape[] SHAPES = {
            ControlCenterLayoutPlan.Shape.CIRCLE,
            ControlCenterLayoutPlan.Shape.RECTANGLE,
            ControlCenterLayoutPlan.Shape.CAPSULE
    };

    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void softwareTileOwnsEveryNativeSurfaceLayerForEveryShape() throws Exception {
        for (ControlCenterLayoutPlan.Shape shape : SHAPES) {
            Fixture fixture = new Fixture(shape);
            fixture.installNativeRequests();
            assertTrue(ControlCenterMaterials.syncTileView(fixture.tile, fixture.glass));
            fixture.assertNativeSurfaceCleared(shape + " initial ownership");

            fixture.apply("setMiViewBlurMode", 2);
            fixture.apply("setMiBackgroundBlurMode", 3);
            fixture.apply("setMiBackgroundBlurRadius", 240);
            fixture.apply("setMiBackgroundBlendColors", colors(Color.CYAN));
            ControlCenterMaterials.updateNativeSurface(fixture.icon, fixture.glass, false);
            fixture.assertNativeSurfaceCleared(shape + " after OEM animation");
        }
    }

    @Test public void nativeBlendRequiresAnActualClearWhenEmptySetterDoesNotClear() throws Exception {
        Fixture fixture = new Fixture(ControlCenterLayoutPlan.Shape.CIRCLE);
        fixture.installNativeRequests();
        ControlCenterMaterials.syncTileView(fixture.tile, fixture.glass);
        assertTrue("OEM blend must be explicitly cleared when a software surface owns the tile",
                fixture.icon.colors.isEmpty());
        assertTrue("This fake models an OEM where the empty-list setter is a no-op",
                fixture.icon.clearBlendCalls > 0);
    }

    @Test public void leavingSoftwareOwnershipRestoresTheLatestNativeRequests() throws Exception {
        for (Exit exit : Exit.values()) {
            Fixture fixture = new Fixture(ControlCenterLayoutPlan.Shape.CIRCLE);
            fixture.installNativeRequests();
            ControlCenterMaterials.syncTileView(fixture.tile, fixture.glass);
            fixture.apply("setMiViewBlurMode", 2);
            fixture.apply("setMiBackgroundBlurMode", 3);
            fixture.apply("setMiBackgroundBlurRadius", 240);
            fixture.apply("setMiBackgroundBlendColors", colors(Color.CYAN));
            switch (exit) {
                case INACTIVE_DEFAULT -> {
                    fixture.tile.state.state = 1;
                    ControlCenterMaterials.syncTileView(fixture.tile,
                            fixture.glass.withSurface(ControlCenterSurface.DEFAULT));
                }
                case DISABLED -> ControlCenterMaterials.syncTileView(fixture.tile,
                        fixture.glass.withEnabled(false));
                case EDIT -> {
                    fixture.tile.mode = "EDIT";
                    ControlCenterMaterials.syncTileView(fixture.tile, fixture.glass);
                }
                case RELEASE -> ControlCenterMaterials.releaseGridSurface(fixture.tile,
                        fixture.glass.withEnabled(false));
            }
            assertEquals(exit.toString(), 2, fixture.icon.viewMode);
            assertEquals(exit.toString(), 3, fixture.icon.backgroundMode);
            assertEquals(exit.toString(), 240, fixture.icon.radius);
            assertEquals(exit.toString(), Color.CYAN, fixture.icon.colors.get(0).x);
        }
    }

    private enum Exit { INACTIVE_DEFAULT, DISABLED, EDIT, RELEASE }

    private static ArrayList<Point> colors(int color) {
        return new ArrayList<>(List.of(new Point(color, 3)));
    }

    private static final class Fixture {
        final QSTileItemView tile = new QSTileItemView();
        final NativeIcon icon = new NativeIcon();
        final ControlCenterConfig glass = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS);

        Fixture(ControlCenterLayoutPlan.Shape shape) {
            icon.setImageDrawable(new ColorDrawable(Color.BLUE));
            tile.addView(icon);
            ControlCenterMaterials.registerGridTileShape(tile,
                    ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 1).withShape(shape, 16));
        }

        void installNativeRequests() throws Exception {
            apply("setMiViewBlurMode", 1);
            apply("setMiBackgroundBlurMode", 2);
            apply("setMiBackgroundBlurRadius", 180);
            apply("setMiBackgroundBlendColors", colors(Color.WHITE));
        }

        void apply(String operation, Object argument) throws Exception {
            Object[] actual = ControlCenterMaterials.nativeArguments(icon, operation,
                    new Object[] {argument}, glass, false);
            ControlCenterMaterials.invoke(icon, operation, actual);
        }

        void assertNativeSurfaceCleared(String context) {
            assertEquals(context + " view blur", 0, icon.viewMode);
            assertEquals(context + " background blur", 0, icon.backgroundMode);
            assertEquals(context + " blur radius", 0, icon.radius);
            assertTrue(context + " background blend", icon.colors.isEmpty());
        }
    }

    public static class QSTileItemView extends FrameLayout {
        public String mode = "NORMAL";
        final State state = new State();
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }

    public static class State { int state = 2; }

    public static class NativeIcon extends ImageView {
        int viewMode, backgroundMode, radius, clearBlendCalls;
        ArrayList<Point> colors = new ArrayList<>();
        NativeIcon() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) {
            // Some OEM implementations may ignore an empty setter; the device log does not prove this.
            if (!value.isEmpty()) colors = new ArrayList<>(value);
        }
        public void clearMiBackgroundBlendColor() {
            clearBlendCalls++;
            colors.clear();
        }
    }
}
