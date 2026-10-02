package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoundedHookRetryTest {
    @Test public void oneFixedDelayRetryIsPendingAndAttemptsStopAtLimit() {
        Timer timer = new Timer();
        BoundedHookRetry retry = new BoundedHookRetry(timer, 3000L, 3);
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean needed = new AtomicBoolean(true);
        Runnable attempt = attempts::incrementAndGet;

        assertTrue(retry.scheduleIfNeeded(needed::get, attempt));
        assertFalse(retry.scheduleIfNeeded(needed::get, attempt));
        assertEquals(List.of(3000L), timer.delays);
        timer.runNext();
        assertEquals(1, attempts.get());
        assertEquals(List.of(3000L, 3000L), timer.delays);
        timer.runNext();
        assertEquals(2, attempts.get());
        assertEquals(List.of(3000L, 3000L, 3000L), timer.delays);
        timer.runNext();
        assertEquals(3, attempts.get());
        assertEquals(3, timer.delays.size());
    }

    @Test public void retryStopsWhenConditionIsSatisfied() {
        Timer timer = new Timer();
        BoundedHookRetry retry = new BoundedHookRetry(timer, 3000L, 4);
        AtomicBoolean needed = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();

        retry.scheduleIfNeeded(needed::get, () -> {
            attempts.incrementAndGet();
            needed.set(false);
        });
        timer.runNext();

        assertEquals(1, attempts.get());
        assertTrue(timer.tasks.isEmpty());
    }

    @Test public void postRejectionOrExceptionRestoresSchedulingSlot() {
        Timer timer = new Timer();
        timer.reject = true;
        BoundedHookRetry retry = new BoundedHookRetry(timer, 3000L, 2);
        AtomicInteger attempts = new AtomicInteger();

        assertFalse(retry.scheduleIfNeeded(() -> true, attempts::incrementAndGet));
        timer.reject = false;
        assertTrue(retry.scheduleIfNeeded(() -> true, attempts::incrementAndGet));
        timer.throwOnPost = true;
        timer.runNext();
        assertEquals(1, attempts.get());
        assertEquals(3, timer.delays.size());
        timer.throwOnPost = false;
        assertTrue(retry.scheduleIfNeeded(() -> true, attempts::incrementAndGet));
        timer.runNext();
        assertEquals(2, attempts.get());
    }

    @Test public void differentPoliciesKeepIndependentAttemptBudgets() {
        Timer timer = new Timer();
        BoundedHookRetry systemUi = new BoundedHookRetry(timer, 3000L, 4);
        BoundedHookRetry plugin = new BoundedHookRetry(timer, 3000L, 8);
        AtomicInteger systemAttempts = new AtomicInteger();
        AtomicInteger pluginAttempts = new AtomicInteger();

        for (int i = 0; i < 4; i++) {
            systemUi.scheduleIfNeeded(() -> true, systemAttempts::incrementAndGet);
            timer.runNext();
        }
        for (int i = 0; i < 8; i++) {
            plugin.scheduleIfNeeded(() -> true, pluginAttempts::incrementAndGet);
            timer.runNext();
        }
        assertFalse(systemUi.scheduleIfNeeded(() -> true, systemAttempts::incrementAndGet));
        assertFalse(plugin.scheduleIfNeeded(() -> true, pluginAttempts::incrementAndGet));
        assertEquals(4, systemAttempts.get());
        assertEquals(8, pluginAttempts.get());
    }

    private static final class Timer implements BoundedHookRetry.Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        boolean reject;
        boolean throwOnPost;
        @Override public boolean postDelayed(Runnable task, long delayMs) {
            delays.add(delayMs);
            if (throwOnPost) throw new IllegalStateException("handler failed");
            if (reject) return false;
            tasks.add(task);
            return true;
        }
        void runNext() { tasks.remove(0).run(); }
    }
}
