package com.xtjm.fusionstatusbar;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LatestRootRefreshSchedulerTest {
    @Test public void multipleRequestsCoalesceToLatestRoot() {
        Poster poster = new Poster();
        List<String> applied = new ArrayList<>();
        LatestRootRefreshScheduler<String> scheduler = new LatestRootRefreshScheduler<>(poster,
                applied::add);

        assertTrue(scheduler.request("root-a"));
        assertTrue(scheduler.request("root-b"));
        assertTrue(scheduler.request("root-c"));
        assertEquals(1, poster.tasks.size());
        poster.runNext();

        assertEquals(List.of("root-c"), applied);
        assertTrue(poster.tasks.isEmpty());
    }

    @Test public void requestsDuringRefreshScheduleOneMoreDrainForLatestRoot() {
        Poster poster = new Poster();
        List<String> applied = new ArrayList<>();
        @SuppressWarnings("unchecked") LatestRootRefreshScheduler<String>[] holder = new LatestRootRefreshScheduler[1];
        holder[0] = new LatestRootRefreshScheduler<>(poster, root -> {
            applied.add(root);
            if ("root-a".equals(root)) {
                assertTrue(holder[0].request("root-b"));
                assertTrue(holder[0].request("root-c"));
            }
        });

        holder[0].request("root-a");
        poster.runNext();
        assertEquals(1, poster.tasks.size());
        poster.runNext();

        assertEquals(List.of("root-a", "root-c"), applied);
        assertTrue(poster.tasks.isEmpty());
    }

    @Test public void scheduledTaskDoesNotKeepRootAlive() throws Exception {
        Poster poster = new Poster();
        List<Object> applied = new ArrayList<>();
        LatestRootRefreshScheduler<Object> scheduler = new LatestRootRefreshScheduler<>(poster,
                applied::add);
        Object root = new Object();
        scheduler.request(root);

        Field pendingField = LatestRootRefreshScheduler.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked") WeakReference<Object> pending =
                (WeakReference<Object>) pendingField.get(scheduler);
        pending.clear();
        root = null;
        poster.runNext();

        assertTrue(applied.isEmpty());
    }

    @Test public void rejectedOrThrowingPostRestoresIdleStateAndLaterRequestCanRetry() {
        Poster poster = new Poster();
        List<String> applied = new ArrayList<>();
        LatestRootRefreshScheduler<String> scheduler = new LatestRootRefreshScheduler<>(poster,
                applied::add);
        poster.reject = true;

        assertFalse(scheduler.request("root-a"));
        assertTrue(poster.tasks.isEmpty());
        poster.reject = false;
        assertTrue(scheduler.request("root-b"));
        poster.runNext();
        assertEquals(List.of("root-b"), applied);

        poster.throwOnPost = true;
        assertFalse(scheduler.request("root-c"));
        poster.throwOnPost = false;
        assertTrue(scheduler.request("root-d"));
        poster.runNext();
        assertEquals(List.of("root-b", "root-d"), applied);
    }

    @Test public void throwingRefreshDoesNotBlockLaterRequests() {
        Poster poster = new Poster();
        List<String> applied = new ArrayList<>();
        LatestRootRefreshScheduler<String> scheduler = new LatestRootRefreshScheduler<>(poster,
                root -> {
                    if ("bad-root".equals(root)) throw new IllegalStateException("refresh failed");
                    applied.add(root);
                });

        scheduler.request("bad-root");
        poster.runNext();
        scheduler.request("good-root");
        poster.runNext();

        assertEquals(List.of("good-root"), applied);
        assertTrue(poster.tasks.isEmpty());
    }

    private static final class Poster implements LatestRootRefreshScheduler.Poster {
        final List<Runnable> tasks = new ArrayList<>();
        boolean reject;
        boolean throwOnPost;
        @Override public boolean post(Runnable task) {
            if (throwOnPost) throw new IllegalStateException("looper unavailable");
            if (reject) return false;
            tasks.add(task);
            return true;
        }
        void runNext() { tasks.remove(0).run(); }
    }
}
