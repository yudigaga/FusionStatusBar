package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35}, manifest = Config.NONE)
public class RuntimeAcknowledgementsTest {
    private static final String STATUS = FusionActivationStatus.FEATURE_STATUS_BAR;
    private static final String CONTROL = FusionActivationStatus.FEATURE_CONTROL_CENTER;

    @Test public void failedProviderAutomaticallyRetriesWithoutFurtherViewEvents() {
        Fixture fixture = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(revision + ":" + state);
            return attempts.incrementAndGet() == 3;
        });
        reports.report(null, 5, STATUS, "applied", "ready");
        fixture.drain();
        fixture.retry(); fixture.drain();
        fixture.retry(); fixture.drain();
        assertEquals(List.of("5:applied", "5:applied", "5:applied"), fixture.sent);
        assertEquals(List.of(1_000L, 2_000L), fixture.timer.delays);
        assertTrue(fixture.timer.tasks.isEmpty());
        reports.report(null, 5, STATUS, "applied", "ready");
        assertTrue(fixture.worker.isEmpty());
    }

    @Test public void newestStateCancelsObsoleteRetryIncludingAlreadyDequeuedCallback() {
        Fixture fixture = new Fixture();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(state);
            return state.equals("applied");
        });
        reports.report(null, 5, STATUS, "waiting", "mount");
        fixture.drain();
        Runnable obsolete = fixture.timer.tasks.get(0);
        reports.report(null, 5, STATUS, "applied", "ready");
        assertTrue(fixture.timer.tasks.isEmpty());
        obsolete.run();
        fixture.drain();
        assertEquals(List.of("waiting", "applied"), fixture.sent);
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void newerRevisionDiscardsOtherFeaturesAndRegressingReports() {
        Fixture fixture = new Fixture();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(revision + ":" + feature);
            return revision == 6;
        });
        reports.report(null, 5, STATUS, "waiting", "mount");
        fixture.drain();
        Runnable obsolete = fixture.timer.tasks.get(0);
        reports.advanceRevision(6);
        reports.report(null, 5, CONTROL, "applied", "old");
        reports.report(null, 6, CONTROL, "applied", "ready");
        obsolete.run();
        fixture.drain();
        assertEquals(List.of("5:" + STATUS, "6:" + CONTROL), fixture.sent);
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void rapidReportsKeepLatestFeatureStateInOneWorker() {
        Fixture fixture = new Fixture();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(reason);
            return true;
        });
        for (int value = 0; value < 1_000; value++) {
            reports.report(null, 7, STATUS, "waiting", "mount-" + value);
        }
        reports.report(null, 7, CONTROL, "applied", "control");
        assertEquals(1, fixture.worker.size());
        fixture.drain();
        assertEquals(List.of("mount-999", "control"), fixture.sent);
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void fullWorkQueueRetriesWhenCapacityBecomesAvailable() {
        Fixture fixture = new Fixture(1);
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(state);
            return true;
        });
        fixture.queue.execute("busy", () -> { });
        reports.report(null, 5, STATUS, "applied", "ready");
        fixture.drain();
        assertTrue(fixture.sent.isEmpty());
        fixture.retry(); fixture.drain();
        assertEquals(List.of("applied"), fixture.sent);
    }

    @Test public void exhaustedRetriesStopWithoutRepeatedUiReportsResettingBudget() {
        Fixture fixture = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("provider down");
        });
        reports.report(null, 5, STATUS, "applied", "ready");
        fixture.drain();
        while (!fixture.timer.tasks.isEmpty()) { fixture.retry(); fixture.drain(); }
        assertEquals(7, attempts.get());
        assertEquals(List.of(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L), fixture.timer.delays);
        reports.report(null, 5, STATUS, "applied", "ready");
        assertTrue(fixture.worker.isEmpty());
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void rejectedTimerDoesNotPermanentlySuppressIdenticalLaterReport() {
        Fixture fixture = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) ->
                attempts.incrementAndGet() > 1);
        fixture.timer.reject = true;
        reports.report(null, 5, STATUS, "applied", "ready"); fixture.drain();
        fixture.timer.reject = false;
        reports.report(null, 5, STATUS, "applied", "ready"); fixture.drain();
        assertEquals(2, attempts.get());
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void blockedProviderDoesNotSpawnWorkersOrAccumulateTimers() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        RuntimeAcknowledgements reports = fixture.reports((context, revision, feature, state, reason) -> {
            fixture.sent.add(revision + ":" + reason);
            if (revision == 1) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test release missing");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return false;
            }
            return true;
        });
        reports.report(null, 1, STATUS, "waiting", "old");
        Thread blocked = new Thread(fixture.worker.remove(0), "test-provider");
        blocked.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int value = 0; value < 1_000; value++) {
                reports.report(null, 2, STATUS, "applied", "latest-" + value);
            }
            assertTrue("IPC must keep the single existing worker", fixture.worker.isEmpty());
            assertTrue("Blocked IPC must not create timeout replacement workers", fixture.timer.tasks.isEmpty());
        } finally {
            release.countDown();
            blocked.join(5_000L);
        }
        assertFalse(blocked.isAlive());
        assertEquals(List.of("1:old", "2:latest-999"), fixture.sent);
        assertTrue(fixture.timer.tasks.isEmpty());
    }

    @Test public void rejectedWorkerCanRecoverThroughTheTimer() {
        List<Runnable> worker = new ArrayList<>();
        AtomicInteger submissions = new AtomicInteger(), attempts = new AtomicInteger();
        Timer timer = new Timer();
        RuntimeWorkQueue queue = new RuntimeWorkQueue(task -> {
            if (submissions.incrementAndGet() == 1) throw new RejectedExecutionException("busy");
            worker.add(task);
        }, 4);
        RuntimeAcknowledgements reports = new RuntimeAcknowledgements(queue, timer,
                (context, revision, feature, state, reason) -> { attempts.incrementAndGet(); return true; });
        reports.report(null, 5, STATUS, "applied", "ready");
        assertEquals(1, timer.tasks.size());
        timer.tasks.remove(0).run(); worker.remove(0).run();
        assertEquals(1, attempts.get());
        assertTrue(timer.tasks.isEmpty());
    }

    private static final class Fixture {
        final List<Runnable> worker = new ArrayList<>();
        final List<String> sent = new ArrayList<>();
        final Timer timer = new Timer();
        final RuntimeWorkQueue queue;
        Fixture() { this(4); }
        Fixture(int capacity) { queue = new RuntimeWorkQueue(worker::add, capacity); }
        RuntimeAcknowledgements reports(RuntimeAcknowledgements.Sender sender) {
            return new RuntimeAcknowledgements(queue, timer, sender);
        }
        void drain() { worker.remove(0).run(); }
        void retry() { timer.tasks.remove(0).run(); }
    }

    private static final class Timer implements RuntimeAcknowledgements.Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        boolean reject;
        @Override public boolean post(Runnable task, long delayMillis) {
            if (reject) return false;
            tasks.add(task); delays.add(delayMillis); return true;
        }
        @Override public void remove(Runnable task) { tasks.remove(task); }
    }
}
