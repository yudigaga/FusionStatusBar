package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class KeyguardStatusBarHeightControllerTest {
    @Test public void nestedBranchSubtractsPaddingAndMarginsWithoutChangingItsPositionOrSiblings() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);
        FrameLayout container = new FrameLayout(context);
        FrameLayout battery = new FrameLayout(context);
        FrameLayout sibling = new FrameLayout(context);
        FrameLayout.LayoutParams rootParams = params(400, 24, Gravity.BOTTOM, 12, 14);
        FrameLayout.LayoutParams containerParams = params(187, 24, Gravity.START, 7, 11);
        FrameLayout.LayoutParams batteryParams = params(91, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL, 3, 5);
        FrameLayout.LayoutParams siblingParams = params(21, 19, Gravity.END, 2, 4);
        root.setLayoutParams(rootParams);
        root.setPadding(1, 3, 2, 5);
        container.setPadding(3, 2, 4, 4);
        battery.setPadding(5, 1, 6, 2);
        sibling.setPadding(7, 8, 9, 10);
        root.addView(container, containerParams);
        container.addView(battery, batteryParams);
        root.addView(sibling, siblingParams);

        KeyguardStatusBarHeightController.apply(root, battery, config());

        assertSame(rootParams, root.getLayoutParams());
        assertSame(containerParams, container.getLayoutParams());
        assertSame(batteryParams, battery.getLayoutParams());
        assertEquals(72, rootParams.height);
        assertEquals(46, containerParams.height); // 72 - root padding 8 - margins 18.
        assertEquals(32, batteryParams.height); // 46 - container padding 6 - margins 8.
        assertPosition(rootParams, 400, Gravity.BOTTOM, 12, 14);
        assertPosition(containerParams, 187, Gravity.START, 7, 11);
        assertPosition(batteryParams, 91, Gravity.CENTER_VERTICAL, 3, 5);
        assertPadding(root, 1, 3, 2, 5);
        assertPadding(container, 3, 2, 4, 4);
        assertPadding(battery, 5, 1, 6, 2);
        for (FrameLayout group : new FrameLayout[] {root, container, battery}) {
            assertFalse(group.getClipChildren());
            assertFalse(group.getClipToPadding());
        }
        assertSame(siblingParams, sibling.getLayoutParams());
        assertEquals(19, siblingParams.height);
        assertPosition(siblingParams, 21, Gravity.END, 2, 4);
        assertPadding(sibling, 7, 8, 9, 10);
        assertTrue(sibling.getClipChildren());
        assertTrue(sibling.getClipToPadding());
    }

    @Test public void applyingTheSameHeightAgainDoesNotRequestAnotherLayout() {
        Context context = RuntimeEnvironment.getApplication();
        CountingFrameLayout root = new CountingFrameLayout(context);
        CountingFrameLayout container = new CountingFrameLayout(context);
        CountingFrameLayout battery = new CountingFrameLayout(context);
        root.setLayoutParams(new FrameLayout.LayoutParams(400, 24));
        root.addView(container, new FrameLayout.LayoutParams(400, 24));
        container.addView(battery, new FrameLayout.LayoutParams(80,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.layoutRequests = container.layoutRequests = battery.layoutRequests = 0;

        KeyguardStatusBarHeightController.apply(root, battery, config());

        assertTrue(root.layoutRequests > 0);
        assertTrue(container.layoutRequests > 0);
        assertTrue(battery.layoutRequests > 0);
        int rootRequests = root.layoutRequests;
        int containerRequests = container.layoutRequests;
        int batteryRequests = battery.layoutRequests;

        KeyguardStatusBarHeightController.apply(root, battery, config());

        assertEquals(rootRequests, root.layoutRequests);
        assertEquals(containerRequests, container.layoutRequests);
        assertEquals(batteryRequests, battery.layoutRequests);
    }

    @Test public void staleBatteryLeavesItsExternalTreeUntouchedAndDoesNotInventRootParams() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);
        CountingFrameLayout external = new CountingFrameLayout(context);
        CountingFrameLayout battery = new CountingFrameLayout(context);
        FrameLayout.LayoutParams externalParams = params(200, 24, Gravity.TOP, 2, 3);
        FrameLayout.LayoutParams batteryParams = params(80, 24, Gravity.END, 4, 5);
        external.setLayoutParams(externalParams);
        external.addView(battery, batteryParams);
        external.layoutRequests = battery.layoutRequests = 0;

        KeyguardStatusBarHeightController.apply(root, battery, config());

        assertNull(root.getLayoutParams());
        assertSame(externalParams, external.getLayoutParams());
        assertSame(batteryParams, battery.getLayoutParams());
        assertEquals(24, externalParams.height);
        assertEquals(24, batteryParams.height);
        assertPosition(externalParams, 200, Gravity.TOP, 2, 3);
        assertPosition(batteryParams, 80, Gravity.END, 4, 5);
        assertTrue(external.getClipChildren());
        assertTrue(external.getClipToPadding());
        assertTrue(battery.getClipChildren());
        assertTrue(battery.getClipToPadding());
        assertEquals(0, external.layoutRequests);
        assertEquals(0, battery.layoutRequests);
    }

    private static FusionConfig config() {
        return FusionConfig.defaults().withStatusBarHeight(72);
    }

    private static FrameLayout.LayoutParams params(int width, int height, int gravity,
            int topMargin, int bottomMargin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
        params.setMargins(6, topMargin, 8, bottomMargin);
        return params;
    }

    private static void assertPosition(FrameLayout.LayoutParams params, int width, int gravity,
            int topMargin, int bottomMargin) {
        assertEquals(width, params.width);
        assertEquals(gravity, params.gravity);
        assertEquals(6, params.leftMargin);
        assertEquals(topMargin, params.topMargin);
        assertEquals(8, params.rightMargin);
        assertEquals(bottomMargin, params.bottomMargin);
    }

    private static void assertPadding(FrameLayout view, int left, int top, int right, int bottom) {
        assertEquals(left, view.getPaddingLeft());
        assertEquals(top, view.getPaddingTop());
        assertEquals(right, view.getPaddingRight());
        assertEquals(bottom, view.getPaddingBottom());
    }

    private static final class CountingFrameLayout extends FrameLayout {
        int layoutRequests;

        CountingFrameLayout(Context context) { super(context); }

        @Override public void requestLayout() {
            layoutRequests++;
            super.requestLayout();
        }
    }
}
