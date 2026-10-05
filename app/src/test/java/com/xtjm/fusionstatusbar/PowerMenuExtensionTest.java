package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
public class PowerMenuExtensionTest {
    @Test public void portraitAndLandscapeKeepAllThreeTracksAndLabelsWithinWindow() {
        for (int[] size : new int[][] {{320, 640}, {640, 320}, {1440, 3200}}) {
            Fixture dialog = new Fixture();
            layout(dialog.mRoot, size[0], size[1]);
            assertTrue(PowerMenuExtension.update(dialog, dialog.mSliderView, 1, settings()));
            layout(dialog.mRoot, size[0], size[1]);
            layout(dialog.mRoot, size[0], size[1]);
            View left = dialog.mRoot.findViewWithTag(PowerMenuExtension.LEFT_TAG);
            View right = dialog.mRoot.findViewWithTag(PowerMenuExtension.RIGHT_TAG);
            assertNotNull(left);
            assertNotNull(right);
            assertTrue(rightEdge(left) < leftEdge(dialog.mSliderView));
            assertTrue(rightEdge(dialog.mSliderView) < leftEdge(right));
            assertTrue(leftEdge(left) >= 0);
            assertTrue(rightEdge(right) <= size[0]);
            for (View side : new View[] {left, right}) {
                float cy = side.getTop() + side.getHeight() / 2f;
                assertTrue(cy - side.getHeight() * side.getScaleY() / 2f >= 0);
                assertTrue(cy + side.getHeight() * side.getScaleY() / 2f <= size[1]);
            }
        }
    }

    @Test public void recreationAndDisablingReleaseThePreviousViews() {
        Fixture dialog = new Fixture();
        FrameLayout old = dialog.mRoot;
        assertTrue(PowerMenuExtension.update(dialog, dialog.mSliderView, 1, settings()));
        assertEquals(3, old.getChildCount());
        dialog.reset();
        assertTrue(PowerMenuExtension.update(dialog, dialog.mSliderView, 1, settings()));
        assertEquals(1, old.getChildCount());
        assertTrue(old.getClipChildren());
        assertEquals(3, dialog.mRoot.getChildCount());
        assertFalse(PowerMenuExtension.update(dialog, dialog.mSliderView, 1,
                settings().withSystemSurfaces(SystemSurfaceConfig.defaults().withPowerMenu(false))));
        assertEquals(1, dialog.mRoot.getChildCount());
        assertEquals(1f, dialog.mSliderView.getScaleX(), 0f);
        assertTrue(dialog.mRoot.getClipChildren());
    }

    @Test public void existingNativeThreeTracksDoNotReceiveAnotherPair() {
        Fixture dialog = new Fixture();
        assertFalse(PowerMenuExtension.update(dialog, dialog.mSliderView, 3, settings()));
        assertEquals(1, dialog.mRoot.getChildCount());
    }

    private static float leftEdge(View view) { return view.getLeft() + view.getWidth() * (1 - view.getScaleX()) / 2f; }
    private static float rightEdge(View view) { return leftEdge(view) + view.getWidth() * view.getScaleX(); }
    private static ControlCenterConfig settings() {
        return ControlCenterConfig.defaults().withTileScale(130).withSystemSurfaces(
                SystemSurfaceConfig.defaults().withExtendedPowerMenu(true));
    }
    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private static final class Fixture {
        final Context mContext = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout mRoot;
        View mSliderView;
        Fixture() { reset(); }
        void reset() {
            mRoot = new FrameLayout(mContext);
            mSliderView = new View(mContext);
            mRoot.addView(mSliderView, new FrameLayout.LayoutParams(64, 288, Gravity.CENTER));
        }
    }
}
