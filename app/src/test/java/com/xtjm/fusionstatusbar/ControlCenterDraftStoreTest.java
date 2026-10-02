package com.xtjm.fusionstatusbar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCenterDraftStoreTest {
    static final class Storage implements ControlCenterDraftStore.Storage {
        boolean succeeds;
        boolean throwsException;
        String saved = "";
        final List<String> attempts = new ArrayList<>();
        public String read() { return saved; }
        public boolean write(String value) {
            attempts.add(value);
            if (throwsException) throw new IllegalStateException("disk unavailable");
            if (succeeds) saved = value;
            return succeeds;
        }
    }

    @Test public void failedDraftRemainsRetryableAfterBoundedAutomaticAttempts() {
        Storage storage = new Storage();
        ArrayDeque<Runnable> delayed = new ArrayDeque<>();
        List<ControlCenterDraftStore.Result> results = new ArrayList<>();
        ControlCenterDraftStore store = new ControlCenterDraftStore(storage, Runnable::run,
                Runnable::run, (task, delay) -> delayed.add(task));
        store.write("latest", results::add);
        while (!delayed.isEmpty()) delayed.remove().run();
        assertEquals(3, storage.attempts.size());
        assertFalse(results.get(2).success);
        assertFalse(results.get(2).retrying);
        store.write("latest", results::add);
        assertEquals(3, storage.attempts.size());
        storage.succeeds = true;
        store.retry();
        assertEquals("latest", storage.saved);
        assertTrue(results.get(3).success);
    }

    @Test public void newerEditReplacesFailedValueBeforeRetry() {
        Storage storage = new Storage();
        storage.throwsException = true;
        ArrayDeque<Runnable> delayed = new ArrayDeque<>();
        List<ControlCenterDraftStore.Result> results = new ArrayList<>();
        ControlCenterDraftStore store = new ControlCenterDraftStore(storage, Runnable::run,
                Runnable::run, (task, delay) -> delayed.add(task));
        store.write("old", results::add);
        store.write("new", results::add);
        storage.throwsException = false;
        storage.succeeds = true;
        delayed.remove().run();
        assertEquals(java.util.Arrays.asList("old", "new"), storage.attempts);
        assertEquals("new", storage.saved);
        assertTrue(results.get(results.size() - 1).success);
    }

    @Test public void closeSuppressesCallbacksButCompletesSubmittedDurableWrite() {
        Storage storage = new Storage();
        storage.succeeds = true;
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        List<ControlCenterDraftStore.Result> results = new ArrayList<>();
        ControlCenterDraftStore store = new ControlCenterDraftStore(storage, worker::add,
                Runnable::run, (task, delay) -> worker.add(task));
        store.write("one", results::add);
        store.write("two", results::add);
        store.close();
        assertEquals(1, worker.size());
        worker.remove().run();
        assertEquals("two", storage.saved);
        assertTrue(results.isEmpty());
    }

    @Test public void slowMainThreadReceivesOnlyLatestResultWithoutCallbackBacklog() {
        Storage storage = new Storage();
        storage.succeeds = true;
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        List<ControlCenterDraftStore.Result> results = new ArrayList<>();
        ControlCenterDraftStore store = new ControlCenterDraftStore(storage, Runnable::run,
                callbacks::add, (task, delay) -> {});
        for (int i = 0; i < 1000; i++) store.write("draft-" + i, results::add);
        assertEquals(1, callbacks.size());
        assertEquals("draft-999", storage.saved);
        callbacks.remove().run();
        assertEquals(1, results.size());
        assertTrue(results.get(0).success);
    }

    @Test public void retryFromDestroyedScreenCannotOverwriteNewerScreenDraft() {
        Storage storage = new Storage();
        ArrayDeque<Runnable> delayed = new ArrayDeque<>();
        java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
        ControlCenterDraftStore previous = new ControlCenterDraftStore(storage, Runnable::run,
                Runnable::run, (task, delay) -> delayed.add(task), sequence);
        previous.write("older-screen", result -> {});
        previous.close();
        storage.succeeds = true;
        ControlCenterDraftStore current = new ControlCenterDraftStore(storage, Runnable::run,
                Runnable::run, (task, delay) -> delayed.add(task), sequence);
        current.write("latest-screen", result -> {});
        delayed.remove().run();
        assertEquals("latest-screen", storage.saved);
        assertEquals(java.util.Arrays.asList("older-screen", "latest-screen"), storage.attempts);
    }
}
