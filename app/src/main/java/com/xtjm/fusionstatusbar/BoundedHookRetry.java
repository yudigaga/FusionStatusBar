package com.xtjm.fusionstatusbar;

import java.util.function.BooleanSupplier;

/** Schedules one fixed-delay retry at a time and stops after a bounded number of attempts. */
final class BoundedHookRetry {
    interface Scheduler { boolean postDelayed(Runnable task, long delayMs); }

    private final Scheduler scheduler;
    private final long delayMs;
    private final int maxAttempts;
    private int attempts;
    private boolean posted;

    BoundedHookRetry(Scheduler scheduler, long delayMs, int maxAttempts) {
        if (scheduler == null || delayMs < 0 || maxAttempts < 0)
            throw new IllegalArgumentException("invalid_hook_retry_policy");
        this.scheduler = scheduler;
        this.delayMs = delayMs;
        this.maxAttempts = maxAttempts;
    }

    boolean scheduleIfNeeded(BooleanSupplier needed, Runnable attempt) {
        if (needed == null || attempt == null || !needed.getAsBoolean()) return false;
        synchronized (this) {
            if (posted || attempts >= maxAttempts) return false;
            posted = true;
            attempts++;
        }
        boolean accepted;
        try {
            accepted = scheduler.postDelayed(() -> runAttempt(needed, attempt), delayMs);
        } catch (Throwable error) {
            accepted = false;
        }
        if (!accepted) {
            synchronized (this) {
                posted = false;
                attempts--;
            }
        }
        return accepted;
    }

    private void runAttempt(BooleanSupplier needed, Runnable attempt) {
        synchronized (this) { posted = false; }
        try {
            attempt.run();
        } finally {
            scheduleIfNeeded(needed, attempt);
        }
    }
}
