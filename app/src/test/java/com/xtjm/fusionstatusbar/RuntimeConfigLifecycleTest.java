package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35}, manifest = Config.NONE)
public class RuntimeConfigLifecycleTest {
    @Test public void lazyStartWiresObserverReloadAndPreviewExactlyOnce() {
        FakeFactory factory = new FakeFactory();
        FakeHost host = new FakeHost();
        RuntimeConfigLifecycle lifecycle = lifecycle(factory, host, new Scheduler());
        Context context = RuntimeEnvironment.getApplication();

        lifecycle.ensureStarted(context);
        lifecycle.ensureStarted(context);

        assertEquals(1, factory.configCreations);
        assertEquals(1, factory.observerCreations);
        assertEquals(1, factory.config.reloads);
        assertEquals(2, factory.observers.ensureCalls);
        assertSame(context.getApplicationContext(), lifecycle.context());

        factory.observers.reload.run();
        factory.observers.preview.run();
        assertEquals(2, factory.config.reloads);
        assertEquals(1, host.previewCalls);
        assertSame(context.getApplicationContext(), host.previewContext);
    }

    @Test public void concurrentFirstUseCreatesOneConfigRuntimeAndOneObserverSet()
            throws Exception {
        FakeFactory factory = new FakeFactory();
        RuntimeConfigLifecycle lifecycle = lifecycle(factory, new FakeHost(), new Scheduler());
        Context context = RuntimeEnvironment.getApplication();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable use = () -> {
            ready.countDown();
            try { start.await(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            lifecycle.ensureStarted(context);
        };
        Thread first = new Thread(use, "config-lifecycle-test-1");
        Thread second = new Thread(use, "config-lifecycle-test-2");
        first.start(); second.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        first.join(2_000L); second.join(2_000L);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, factory.configCreations);
        assertEquals(1, factory.observerCreations);
        assertEquals(1, factory.config.reloads);
    }

    @Test public void unavailableAndApplyFailureUseBoundedBackoffAndSuccessResetsIt() {
        FakeFactory factory = new FakeFactory();
        FakeHost host = new FakeHost();
        Scheduler scheduler = new Scheduler();
        RuntimeConfigLifecycle lifecycle = lifecycle(factory, host, scheduler);
        lifecycle.ensureStarted(RuntimeEnvironment.getApplication());

        factory.listener.unavailable();
        factory.listener.unavailable();
        assertEquals(List.of(2_000L), scheduler.delays);
        scheduler.runNext();
        factory.listener.unavailable();
        assertEquals(List.of(2_000L, 4_000L), scheduler.delays);

        factory.listener.loaded(FusionConfig.defaults().withRevision(5));
        assertEquals(1, host.recoveredCalls);
        assertEquals(1, host.applyCalls);
        scheduler.runNext();
        factory.listener.unavailable();
        assertEquals(List.of(2_000L, 4_000L, 2_000L), scheduler.delays);
        scheduler.runNext();

        host.applyError = new IllegalStateException("apply failed");
        factory.listener.loaded(FusionConfig.defaults().withRevision(6));
        assertEquals(1, host.applyFailures);
        assertEquals(2, host.applyCalls);
        assertEquals(List.of(2_000L, 4_000L, 2_000L, 2_000L), scheduler.delays);

        factory.listener.unavailable();
        assertEquals("Only one retry may be pending", 4, scheduler.delays.size());
    }

    @Test public void reportUsesApplicationContextAndCurrentConfigRuntime() {
        FakeFactory factory = new FakeFactory();
        RuntimeConfigLifecycle lifecycle = lifecycle(factory, new FakeHost(), new Scheduler());
        Context context = RuntimeEnvironment.getApplication();
        lifecycle.ensureStarted(context);

        lifecycle.report(17, FusionActivationStatus.FEATURE_STATUS_BAR,
                FusionActivationStatus.APPLIED, "test");

        assertSame(context.getApplicationContext(), factory.config.reportContext);
        assertEquals(17L, factory.config.reportRevision);
        assertEquals("test", factory.config.reportReason);
    }

    @Test public void rejectedRetryPostDoesNotLeaveLifecyclePermanentlyPosted() {
        FakeFactory factory = new FakeFactory();
        Scheduler scheduler = new Scheduler();
        scheduler.accept = false;
        RuntimeConfigLifecycle lifecycle = lifecycle(factory, new FakeHost(), scheduler);
        lifecycle.ensureStarted(RuntimeEnvironment.getApplication());

        factory.listener.unavailable();
        scheduler.accept = true;
        factory.listener.unavailable();

        assertEquals(List.of(2_000L, 4_000L), scheduler.delays);
        assertEquals(1, scheduler.tasks.size());
    }

    private static RuntimeConfigLifecycle lifecycle(FakeFactory factory, FakeHost host,
            Scheduler scheduler) {
        return new RuntimeConfigLifecycle(new Handler(Looper.getMainLooper()), scheduler,
                factory, host);
    }

    private static final class Scheduler implements RuntimeConfigLifecycle.Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        boolean accept = true;
        @Override public boolean postDelayed(Runnable task, long delayMs) {
            delays.add(delayMs);
            if (accept) tasks.add(task);
            return accept;
        }
        void runNext() { tasks.remove(0).run(); }
    }

    private static final class FakeFactory implements RuntimeConfigLifecycle.Factory {
        int configCreations;
        int observerCreations;
        FakeConfig config;
        FakeObservers observers;
        RuntimeConfigCoordinator.Listener listener;

        @Override public RuntimeConfigLifecycle.ConfigAccess createConfig(Context context,
                RuntimeConfigCoordinator.Listener listener) {
            configCreations++;
            this.listener = listener;
            config = new FakeConfig();
            return config;
        }

        @Override public RuntimeConfigLifecycle.ObserverAccess createObservers(Context context,
                Handler main, Runnable reload, Runnable preview) {
            observerCreations++;
            observers = new FakeObservers(reload, preview);
            return observers;
        }
    }

    private static final class FakeConfig implements RuntimeConfigLifecycle.ConfigAccess {
        int reloads;
        Context reportContext;
        long reportRevision;
        String reportReason;
        @Override public void reload() { reloads++; }
        @Override public void report(Context context, long revision, String feature,
                String state, String reason) {
            reportContext = context;
            reportRevision = revision;
            reportReason = reason;
        }
    }

    private static final class FakeObservers implements RuntimeConfigLifecycle.ObserverAccess {
        final Runnable reload;
        final Runnable preview;
        int ensureCalls;
        FakeObservers(Runnable reload, Runnable preview) {
            this.reload = reload;
            this.preview = preview;
        }
        @Override public void ensureRegistered() { ensureCalls++; }
    }

    private static final class FakeHost implements RuntimeConfigLifecycle.Host {
        int applyCalls;
        int applyFailures;
        int recoveredCalls;
        int previewCalls;
        Context previewContext;
        Throwable applyError;
        @Override public void apply(FusionConfig config) throws Throwable {
            applyCalls++;
            if (applyError != null) throw applyError;
        }
        @Override public void onApplyFailure(FusionConfig config, Throwable error) {
            applyFailures++;
        }
        @Override public void onRecovered() { recoveredCalls++; }
        @Override public void requestPreview(Context context) {
            previewCalls++;
            previewContext = context;
        }
    }
}
