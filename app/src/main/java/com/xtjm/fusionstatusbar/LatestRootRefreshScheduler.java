package com.xtjm.fusionstatusbar;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/** Coalesces refresh requests by root and drains only the newest live root. */
final class LatestRootRefreshScheduler<R> {
    interface Poster { boolean post(Runnable task); }

    private final Poster poster;
    private final Consumer<R> apply;
    private WeakReference<R> pending = new WeakReference<>(null);
    private boolean posted;

    LatestRootRefreshScheduler(Poster poster, Consumer<R> apply) {
        if (poster == null || apply == null)
            throw new IllegalArgumentException("missing_refresh_scheduler_dependency");
        this.poster = poster;
        this.apply = apply;
    }

    boolean request(R root) {
        if (root == null) return false;
        synchronized (this) {
            pending = new WeakReference<>(root);
            if (posted) return true;
            posted = true;
        }
        return enqueueDrain();
    }

    private boolean enqueueDrain() {
        boolean accepted;
        try {
            accepted = poster.post(this::drain);
        } catch (Throwable error) {
            accepted = false;
        }
        if (!accepted) {
            synchronized (this) {
                posted = false;
            }
        }
        return accepted;
    }

    private void drain() {
        R target;
        synchronized (this) {
            target = pending.get();
            pending = new WeakReference<>(null);
        }
        try {
            if (target != null) apply.accept(target);
        } catch (Throwable ignored) {
            // A failed apply must not leave future refresh requests permanently blocked.
        } finally {
            boolean reschedule;
            synchronized (this) {
                R next = pending.get();
                reschedule = next != null;
                if (!reschedule) {
                    posted = false;
                }
            }
            if (reschedule) enqueueDrain();
        }
    }
}
