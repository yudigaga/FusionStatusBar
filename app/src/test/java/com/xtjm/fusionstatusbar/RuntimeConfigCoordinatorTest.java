package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35}, manifest = Config.NONE)
public class RuntimeConfigCoordinatorTest {
    @Test public void slowMainKeepsOnlyNewestPendingDelivery() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        FusionConfig[] source = {FusionConfig.defaults()};
        RuntimeConfigCoordinator coordinator = coordinator(worker, main, source, applied, new int[1]);
        for (int revision = 1; revision <= 100; revision++) {
            source[0] = FusionConfig.defaults().withRevision(revision);
            coordinator.reload();
            worker.remove(0).run();
        }
        assertEquals("Slow main must hold a single delivery", 1, main.size());
        main.remove(0).run();
        assertEquals(List.of(100L), applied);
    }

    @Test public void lateCallbackCannotOverwriteNewRequestAndReadsCoalesce() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        FusionConfig[] source = {FusionConfig.defaults().withRevision(4)};
        RuntimeConfigCoordinator coordinator = coordinator(worker, main, source, applied, new int[1]);
        coordinator.reload();
        worker.remove(0).run();
        source[0] = FusionConfig.defaults().withRevision(7);
        coordinator.reload(); coordinator.reload(); coordinator.reload();
        assertEquals("Only one worker drain is queued", 1, worker.size());
        main.remove(0).run();
        assertTrue(applied.isEmpty());
        worker.remove(0).run();
        assertEquals(1, main.size());
        main.remove(0).run();
        assertEquals(List.of(7L), applied);
    }

    @Test public void failureAndRegressingRevisionKeepLastAppliedSnapshot() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        int[] unavailable = {0};
        FusionConfig[] source = {FusionConfig.defaults().withRevision(8)};
        RuntimeConfigCoordinator coordinator = coordinator(worker, main, source, applied, unavailable);
        coordinator.reload(); worker.remove(0).run(); main.remove(0).run();
        source[0] = null;
        coordinator.reload(); worker.remove(0).run(); main.remove(0).run();
        source[0] = FusionConfig.defaults().withRevision(3);
        coordinator.reload(); worker.remove(0).run(); main.remove(0).run();
        assertEquals(List.of(8L), applied);
        assertEquals(2, unavailable[0]);
    }

    @Test public void rejectedReadNotifiesFailureAndLaterRequestCanRecover() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        int[] unavailable = {0};
        RuntimeWorkQueue queue = new RuntimeWorkQueue(worker::add, 1);
        queue.execute("busy", () -> { });
        RuntimeConfigCoordinator coordinator = new RuntimeConfigCoordinator(queue, main::add,
                () -> FusionConfig.defaults().withRevision(8), listener(applied, unavailable));
        coordinator.reload();
        main.remove(0).run();
        assertEquals(1, unavailable[0]);
        worker.remove(0).run();
        coordinator.reload(); worker.remove(0).run(); main.remove(0).run();
        assertEquals(List.of(8L), applied);
    }

    @Test public void rejectedMainPostDoesNotPoisonLaterDelivery() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        boolean[] rejecting = {true};
        RuntimeConfigCoordinator coordinator = new RuntimeConfigCoordinator(
                new RuntimeWorkQueue(worker::add, 4), task -> {
                    if (rejecting[0]) throw new RejectedExecutionException("stopping");
                    main.add(task);
                }, () -> FusionConfig.defaults().withRevision(9), listener(applied, new int[1]));
        coordinator.reload(); worker.remove(0).run();
        rejecting[0] = false;
        coordinator.reload(); worker.remove(0).run(); main.remove(0).run();
        assertEquals(List.of(9L), applied);
    }

    @Test public void callbackCanRequestAnotherReadWithoutLosingTheNextDelivery() {
        List<Runnable> worker = new ArrayList<>(), main = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        RuntimeConfigCoordinator[] holder = new RuntimeConfigCoordinator[1];
        long[] revision = {10};
        holder[0] = new RuntimeConfigCoordinator(new RuntimeWorkQueue(worker::add, 4), main::add,
                () -> FusionConfig.defaults().withRevision(revision[0]),
                new RuntimeConfigCoordinator.Listener() {
                    @Override public void loaded(FusionConfig config) {
                        applied.add(config.revision);
                        if (config.revision == 10) {
                            revision[0]++;
                            holder[0].reload();
                        }
                    }
                    @Override public void unavailable() { fail("Read should succeed"); }
                });
        holder[0].reload(); worker.remove(0).run(); main.remove(0).run();
        worker.remove(0).run(); main.remove(0).run();
        assertEquals(List.of(10L, 11L), applied);
    }

    private RuntimeConfigCoordinator coordinator(List<Runnable> worker, List<Runnable> main,
            FusionConfig[] source, List<Long> applied, int[] unavailable) {
        return new RuntimeConfigCoordinator(new RuntimeWorkQueue(worker::add, 4), main::add,
                () -> source[0], listener(applied, unavailable));
    }

    private RuntimeConfigCoordinator.Listener listener(List<Long> applied, int[] unavailable) {
        return new RuntimeConfigCoordinator.Listener() {
            @Override public void loaded(FusionConfig value) { applied.add(value.revision); }
            @Override public void unavailable() { unavailable[0]++; }
        };
    }
}
