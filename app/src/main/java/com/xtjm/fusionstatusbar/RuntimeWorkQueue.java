package com.xtjm.fusionstatusbar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** One worker and one pending operation per key; stalled IPC cannot grow the queue. */
final class RuntimeWorkQueue {
    private final Executor worker;
    private final int capacity;
    private final LinkedHashMap<String, Runnable> pending = new LinkedHashMap<>();
    private boolean running;

    RuntimeWorkQueue(String name, int capacity) {
        this(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        }), capacity);
    }

    RuntimeWorkQueue(Executor worker, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Queue capacity must be positive");
        this.worker = worker;
        this.capacity = capacity;
    }

    synchronized boolean execute(String key, Runnable task) {
        if (!pending.containsKey(key) && pending.size() >= capacity) return false;
        pending.put(key, task);
        if (!running) {
            running = true;
            try {
                worker.execute(this::drain);
            } catch (RejectedExecutionException rejected) {
                running = false;
                pending.remove(key);
                return false;
            }
        }
        return true;
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (this) {
                if (pending.isEmpty()) {
                    running = false;
                    return;
                }
                Map.Entry<String, Runnable> next = pending.entrySet().iterator().next();
                task = next.getValue();
                pending.remove(next.getKey());
            }
            RuntimeCleanup.run("background operation", task::run);
        }
    }
}
