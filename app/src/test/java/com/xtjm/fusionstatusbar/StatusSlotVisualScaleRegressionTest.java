package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

/** Synthetic slot geometry; it does not replay the device's native icon hierarchy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusSlotVisualScaleRegressionTest {
    @Test public void rightSlotKeepsReadoutAdjacentToScaledNativeContentLtr() throws Exception {
        assertScaledGeometry(FusionConfig.SIDE_RIGHT, View.LAYOUT_DIRECTION_LTR);
    }

    @Test public void rightSlotKeepsReadoutAdjacentToScaledNativeContentRtl() throws Exception {
        assertScaledGeometry(FusionConfig.SIDE_RIGHT, View.LAYOUT_DIRECTION_RTL);
    }

    @Test public void leftSlotKeepsReadoutAdjacentToScaledNativeContentLtr() throws Exception {
        assertScaledGeometry(FusionConfig.SIDE_LEFT, View.LAYOUT_DIRECTION_LTR);
    }

    @Test public void leftSlotKeepsReadoutAdjacentToScaledNativeContentRtl() throws Exception {
        assertScaledGeometry(FusionConfig.SIDE_LEFT, View.LAYOUT_DIRECTION_RTL);
    }

    @Test public void nestedScalingReservesOverflowWithoutCompressingTheNativeParent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (int scale : new int[] {60, 100, 150}) {
            LinearLayout slot = newSlot(context, FusionConfig.SIDE_RIGHT, View.LAYOUT_DIRECTION_LTR);
            FrameLayout nativeParent = new FrameLayout(context);
            nativeParent.setClipChildren(false);
            View nativeIcon = new View(context);
            nativeParent.addView(nativeIcon, new FrameLayout.LayoutParams(80, 48));
            StatusPairView readout = new StatusPairView(context);
            readout.setLines("1KB/s\n2KB/s");
            readout.setPreferredWidth(160);
            slot.addView(readout, new LinearLayout.LayoutParams(0, 48));
            slot.addView(nativeParent, new LinearLayout.LayoutParams(160, 48));
            slot.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY));
            applyScale(nativeIcon, scale, FusionConfig.SIDE_RIGHT);
            for (int pass = 0; pass < 3; pass++) {
                slot.requestLayout();
                slot.layout(0, 0, 320, 48);
                assertEquals("Native parent measurement is retained", 160, nativeParent.getMeasuredWidth());
                assertEquals("Native parent layout width is retained", 160, nativeParent.getWidth());
                assertEquals("Only actual overflow reduces the readout budget", scale == 150 ? 120 : 160,
                        readout.getWidth());
            }
        }
    }

    @Test public void existingInwardMarginDoesNotCountTranslatedContentTwice() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LinearLayout slot = newSlot(context, FusionConfig.SIDE_RIGHT, View.LAYOUT_DIRECTION_LTR);
        FrameLayout nativeParent = new FrameLayout(context);
        nativeParent.setClipChildren(false);
        View translatedContent = new View(context);
        translatedContent.setTranslationX(-10f);
        nativeParent.addView(translatedContent, new FrameLayout.LayoutParams(80, 48));
        StatusPairView readout = new StatusPairView(context);
        readout.setPreferredWidth(160);
        slot.addView(readout, new LinearLayout.LayoutParams(0, 48));
        LinearLayout.LayoutParams nativeParams = new LinearLayout.LayoutParams(80, 48);
        nativeParams.leftMargin = 10;
        slot.addView(nativeParent, nativeParams);
        slot.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY));
        slot.layout(0, 0, 250, 48);
        assertEquals("The existing margin covers the ten translated pixels", 160, readout.getWidth());
        assertEquals("Native parent layout width stays unchanged", 80, nativeParent.getWidth());
    }

    private static void assertScaledGeometry(int side, int direction) throws Exception {
        StringBuilder failures = new StringBuilder();
        for (int width : new int[] {120, 320}) {
            for (int scale : new int[] {60, 100, 150}) {
                Fixture fixture = new Fixture(side, direction, width, scale);
                RectF nativeBounds = visualBounds(fixture.nativeContent);
                RectF readoutBounds = visualBounds(fixture.readout);
                String label = "side=" + side + " direction=" + direction
                        + " width=" + width + " scale=" + scale;
                if (nativeBounds.left < -1f || nativeBounds.right > width + 1f) {
                    failures.append(label).append(" native content crossed the slot: ")
                            .append(nativeBounds).append('\n');
                }
                if (readoutBounds.left < -1f || readoutBounds.right > width + 1f) {
                    failures.append(label).append(" readout crossed the slot: ")
                            .append(readoutBounds).append('\n');
                }
                float gap = fixture.nativeContent.getLeft() < fixture.readout.getLeft()
                        ? readoutBounds.left - nativeBounds.right
                        : nativeBounds.left - readoutBounds.right;
                if (Math.abs(gap) > 1f) {
                    failures.append(label).append(gap < 0f ? " overlap=" : " extra gap=")
                            .append(Math.abs(gap)).append('\n');
                }
            }
        }
        assertEquals(failures.toString(), "", failures.toString());
    }

    private static RectF visualBounds(View view) {
        RectF bounds = new RectF(0f, 0f, view.getWidth(), view.getHeight());
        view.getMatrix().mapRect(bounds);
        bounds.offset(view.getLeft(), view.getTop());
        return bounds;
    }

    private static LinearLayout newSlot(Context context, int side, int direction) throws Exception {
        Class<?> slotClass = Class.forName(DualRowStatusBarLayout.class.getName() + "$StatusSlotLayout");
        Constructor<?> constructor = slotClass.getDeclaredConstructor(Context.class, int.class);
        constructor.setAccessible(true);
        LinearLayout slot = (LinearLayout) constructor.newInstance(context,
                side == FusionConfig.SIDE_LEFT ? Gravity.START : Gravity.END);
        slot.setLayoutDirection(direction);
        return slot;
    }

    private static void applyScale(View view, int scale, int side) throws Exception {
        Class<?> stateClass = Class.forName(DualRowStatusBarLayout.class.getName() + "$State");
        Method applyScale = stateClass.getDeclaredMethod("applyVisualScale", View.class, int.class, int.class);
        applyScale.setAccessible(true);
        applyScale.invoke(null, view, scale, side);
    }

    private static final class Fixture {
        final View nativeContent;
        final StatusPairView readout;

        Fixture(int side, int direction, int width, int scale) throws Exception {
            Context context = RuntimeEnvironment.getApplication();
            LinearLayout slot = newSlot(context, side, direction);
            nativeContent = new View(context);
            nativeContent.setBackgroundColor(0xffffffff);
            readout = new StatusPairView(context);
            readout.setLines("\u21911KB/s\n\u21932KB/s");
            readout.setPreferredWidth(160);
            if (side == FusionConfig.SIDE_LEFT) {
                slot.addView(nativeContent, nativeParams());
                slot.addView(readout, readoutParams());
            } else {
                slot.addView(readout, readoutParams());
                slot.addView(nativeContent, nativeParams());
            }
            slot.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY));
            // Invoke the production transform after measurement to model its steady state.
            applyScale(nativeContent, scale, side);
            slot.layout(0, 0, width, 48);
        }

        private static LinearLayout.LayoutParams nativeParams() {
            return new LinearLayout.LayoutParams(80, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        private static LinearLayout.LayoutParams readoutParams() {
            return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
