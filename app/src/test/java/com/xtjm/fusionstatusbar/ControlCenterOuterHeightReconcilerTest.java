package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterOuterHeightReconcilerTest {
    @Test public void nativeHeightResetDuringExpansionIsRepairedAfterSettle() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        FrameLayout carrier = new FrameLayout(activity);
        View editRow = new View(activity);
        carrier.setLayoutParams(new LinearLayout.LayoutParams(-1, 160));
        editRow.setLayoutParams(new LinearLayout.LayoutParams(-1, 32));
        panel.addView(carrier);
        panel.addView(editRow);
        layout(panel);

        FrameScheduler scheduler = new FrameScheduler();
        boolean[] active = {true};
        ControlCenterOuterHeightReconciler reconciler = new ControlCenterOuterHeightReconciler(
                carrier, () -> active[0], () -> {
                    carrier.getLayoutParams().height = 160;
                    carrier.setMinimumHeight(160);
                    carrier.setLayoutParams(carrier.getLayoutParams());
                }, scheduler);

        carrier.setScaleY(0.8f);
        carrier.getLayoutParams().height = 48;
        carrier.setLayoutParams(carrier.getLayoutParams());
        layout(panel);
        assertEquals("The native edit item is temporarily laid out after the native row height",
                48, editRow.getTop());

        reconciler.reconcile();
        assertTrue("A settle check must remain pending through the expansion", scheduler.hasPending());
        scheduler.advanceFrames(4);
        assertEquals(48, carrier.getLayoutParams().height);

        carrier.setScaleY(1f);
        scheduler.advanceFrames(1);
        layout(panel);

        assertEquals(160, carrier.getLayoutParams().height);
        assertEquals("The edit row must follow the complete custom grid", 160, editRow.getTop());
        assertFalse(scheduler.hasPending());
    }

    @Test public void cancellationStopsTheSettleCheckAfterUnbind() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout carrier = new FrameLayout(activity);
        carrier.setLayoutParams(new FrameLayout.LayoutParams(-1, 48));
        carrier.setScaleY(0.8f);
        FrameScheduler scheduler = new FrameScheduler();
        boolean[] applied = {false};
        ControlCenterOuterHeightReconciler reconciler = new ControlCenterOuterHeightReconciler(
                carrier, () -> true, () -> applied[0] = true, scheduler);

        reconciler.reconcile();
        assertTrue(scheduler.hasPending());
        reconciler.cancel();
        carrier.setScaleY(1f);
        scheduler.advanceFrames(2);

        assertFalse(applied[0]);
        assertFalse(scheduler.hasPending());
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static final class FrameScheduler
            implements ControlCenterOuterHeightReconciler.Scheduler {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void post(View view, Runnable task) { tasks.add(task); }
        @Override public void remove(View view, Runnable task) { tasks.remove(task); }
        boolean hasPending() { return !tasks.isEmpty(); }
        void advanceFrames(int count) {
            for (int i = 0; i < count && !tasks.isEmpty(); i++) tasks.remove().run();
        }
    }
}
