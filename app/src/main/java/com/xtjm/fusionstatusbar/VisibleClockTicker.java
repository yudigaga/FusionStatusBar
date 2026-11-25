package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Schedules wall-clock-aligned updates while at least one watched clock is visible. Main thread only. */
final class VisibleClockTicker {
    private static final long SECOND_MS = 1_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable render;
    private final LongSupplier wallTime;
    private final boolean refreshOnVisible;
    private final List<Watch> watches = new ArrayList<>();
    private Context receiverContext;
    private long intervalMs;
    private boolean scheduled;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reconcile(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            scheduled = false;
            reconcile(true);
        }
    };

    VisibleClockTicker(Runnable render) { this(render, System::currentTimeMillis); }

    VisibleClockTicker(Runnable render, LongSupplier wallTime) {
        this(render, wallTime, false);
    }

    VisibleClockTicker(Runnable render, LongSupplier wallTime, boolean refreshOnVisible) {
        this.render = render;
        this.wallTime = wallTime;
        this.refreshOnVisible = refreshOnVisible;
    }

    void update(boolean enabled, View... views) {
        update(enabled ? SECOND_MS : 0, views);
    }

    void update(long intervalMs, View... views) {
        if (this.intervalMs != intervalMs) {
            this.intervalMs = intervalMs;
            cancelTick();
        }
        ArrayList<View> next = new ArrayList<>();
        if (intervalMs > 0) {
            for (View view : views) if (view != null && !next.contains(view)) next.add(view);
        }
        boolean same = next.size() == watches.size();
        for (int i = 0; same && i < next.size(); i++) same = next.get(i) == watches.get(i).view.get();
        if (!same) {
            for (Watch watch : watches) watch.close();
            watches.clear();
            for (View view : next) watches.add(new Watch(view));
        }
        reconcile();
    }

    void close() { update(false); }
    boolean isScheduled() { return scheduled; }

    private void reconcile() { reconcile(false); }

    private void reconcile(boolean tickDue) {
        Context context = null;
        boolean visible = false;
        boolean becameVisible = false;
        for (Watch watch : watches) {
            View view = watch.view.get();
            if (context == null && view != null && view.isAttachedToWindow()) {
                context = view.getContext().getApplicationContext();
            }
            boolean nowVisible = isVisible(view);
            becameVisible |= nowVisible && !watch.visible;
            watch.visible = nowVisible;
            visible |= nowVisible;
        }
        if (context == null && receiverContext != null) {
            try { receiverContext.unregisterReceiver(screenReceiver); }
            catch (RuntimeException ignored) { }
            receiverContext = null;
        } else if (context != null && receiverContext == null) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            try {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                receiverContext = context;
            } catch (RuntimeException ignored) { }
        }
        if (!visible) {
            cancelTick();
            return;
        }
        if (tickDue || refreshOnVisible && becameVisible) render.run();
        if (intervalMs > 0 && !scheduled) {
            scheduled = true;
            handler.postDelayed(tick, intervalMs - Math.floorMod(wallTime.getAsLong(), intervalMs));
        }
    }

    private void cancelTick() {
        if (!scheduled) return;
        handler.removeCallbacks(tick);
        scheduled = false;
    }

    static boolean isVisible(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown()
                || view.getWindowVisibility() != View.VISIBLE) return false;
        PowerManager power = view.getContext().getSystemService(PowerManager.class);
        return power == null || power.isInteractive();
    }

    private final class Watch implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<View> view;
        boolean visible;
        private WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);

        Watch(View target) {
            view = new WeakReference<>(target);
            target.addOnAttachStateChangeListener(this);
            if (target.isAttachedToWindow()) observe(target);
        }

        private void observe(View target) {
            unobserve();
            ViewTreeObserver current = target.getViewTreeObserver();
            current.addOnGlobalLayoutListener(this);
            observer = new WeakReference<>(current);
        }

        private void unobserve() {
            ViewTreeObserver current = observer.get();
            if (current != null && current.isAlive()) current.removeOnGlobalLayoutListener(this);
            observer.clear();
        }

        @Override public void onViewAttachedToWindow(View target) { observe(target); reconcile(); }
        @Override public void onViewDetachedFromWindow(View target) {
            unobserve();
            handler.post(VisibleClockTicker.this::reconcile);
        }
        @Override public void onGlobalLayout() { reconcile(); }

        void close() {
            unobserve();
            View target = view.get();
            if (target != null) target.removeOnAttachStateChangeListener(this);
            view.clear();
        }
    }
}
