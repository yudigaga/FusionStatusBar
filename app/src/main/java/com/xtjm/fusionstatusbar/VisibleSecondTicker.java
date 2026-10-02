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

/** Owns second ticks only while a watched clock can actually be seen. Main-thread only. */
final class VisibleSecondTicker {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable render;
    private final LongSupplier wallTime;
    private final List<Watch> watches = new ArrayList<>();
    private Context receiverContext;
    private boolean scheduled;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reconcile(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            scheduled = false;
            if (canRun()) render.run();
            reconcile();
        }
    };

    VisibleSecondTicker(Runnable render) { this(render, System::currentTimeMillis); }

    VisibleSecondTicker(Runnable render, LongSupplier wallTime) {
        this.render = render;
        this.wallTime = wallTime;
    }

    void update(boolean enabled, View... views) {
        ArrayList<View> next = new ArrayList<>();
        if (enabled) {
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

    private void reconcile() {
        Context context = null;
        for (Watch watch : watches) {
            View view = watch.view.get();
            if (view != null && view.isAttachedToWindow()) {
                context = view.getContext().getApplicationContext();
                if (context != null) break;
            }
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
        if (!canRun()) {
            handler.removeCallbacks(tick);
            scheduled = false;
        } else if (!scheduled) {
            scheduled = true;
            handler.postDelayed(tick, 1000L - wallTime.getAsLong() % 1000L);
        }
    }

    private boolean canRun() {
        for (Watch watch : watches) {
            View view = watch.view.get();
            if (view == null || !view.isAttachedToWindow() || !view.isShown()
                    || view.getWindowVisibility() != View.VISIBLE) continue;
            PowerManager power = view.getContext().getSystemService(PowerManager.class);
            if (power == null || power.isInteractive()) return true;
        }
        return false;
    }

    private final class Watch implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<View> view;
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
        @Override public void onViewDetachedFromWindow(View target) { unobserve(); handler.post(VisibleSecondTicker.this::reconcile); }
        @Override public void onGlobalLayout() { reconcile(); }

        void close() {
            unobserve();
            View target = view.get();
            if (target != null) target.removeOnAttachStateChangeListener(this);
            view.clear();
        }
    }
}
