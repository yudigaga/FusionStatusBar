package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import android.os.SystemClock;

import java.util.function.BooleanSupplier;

/** Defers RecyclerView carrier height changes until its native expansion transform settles. */
final class ControlCenterOuterHeightReconciler {
    private static final long MAX_SETTLE_MS = 3_000L;

    interface Scheduler {
        void post(View view, Runnable task);
        void remove(View view, Runnable task);
    }

    static final Scheduler VIEW_SCHEDULER = new Scheduler() {
        @Override public void post(View view, Runnable task) { view.postDelayed(task, 16L); }
        @Override public void remove(View view, Runnable task) { view.removeCallbacks(task); }
    };

    private final ViewGroup carrier;
    private final BooleanSupplier active;
    private final Runnable onSettled;
    private final Scheduler scheduler;
    private Runnable pending;
    private long settleDeadline;

    ControlCenterOuterHeightReconciler(ViewGroup carrier, BooleanSupplier active,
            Runnable onSettled, Scheduler scheduler) {
        this.carrier = carrier;
        this.active = active;
        this.onSettled = onSettled;
        this.scheduler = scheduler;
    }

    void reconcile() {
        if (!active.getAsBoolean()) {
            cancel();
            return;
        }
        if (!isSettled(carrier)) {
            schedule();
            return;
        }
        cancel();
        onSettled.run();
    }

    void cancel() {
        Runnable task = pending;
        pending = null;
        settleDeadline = 0L;
        if (task != null) scheduler.remove(carrier, task);
    }

    private void schedule() {
        if (pending != null) return;
        settleDeadline = SystemClock.elapsedRealtime() + MAX_SETTLE_MS;
        Runnable[] task = new Runnable[1];
        task[0] = () -> {
            if (pending != task[0]) return;
            pending = null;
            if (!active.getAsBoolean()) {
                settleDeadline = 0L;
                return;
            }
            if (isSettled(carrier)) {
                settleDeadline = 0L;
                onSettled.run();
                return;
            }
            if (SystemClock.elapsedRealtime() >= settleDeadline) {
                settleDeadline = 0L;
                return;
            }
            pending = task[0];
            scheduler.post(carrier, task[0]);
        };
        pending = task[0];
        scheduler.post(carrier, task[0]);
    }

    private static boolean isSettled(View view) {
        return view.getVisibility() == View.VISIBLE
                && view.getAlpha() >= 0.999f
                && Math.abs(view.getScaleX() - 1f) <= 0.001f
                && Math.abs(view.getScaleY() - 1f) <= 0.001f;
    }
}
