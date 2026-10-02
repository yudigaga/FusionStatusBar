package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Coalesced serial IO with bounded subscribers and lifecycle-independent submitted writes. */
final class FusionConfigRepository {
    static final int MAX_SESSIONS = 32;
    static final String SUPERSEDED = "superseded";
    interface Storage {
        FusionConfig read();
        FusionConfigStore.WriteResult write(FusionConfig value);
        default FusionConfigStore.WriteResult restore(FusionConfig value) {
            return new FusionConfigStore.WriteResult(false, read(), "restore_unavailable");
        }
        default FusionConfigStore.WriteResult undoRestore() {
            return new FusionConfigStore.WriteResult(false, read(), "restore_unavailable");
        }
    }

    private static FusionConfigRepository application;
    private final Object lock = new Object();
    private final Storage storage;
    private final Executor worker;
    private final Executor callbacks;
    private final Set<Session> sessions = new LinkedHashSet<>();
    private final Map<Session, Request> pendingReads = new LinkedHashMap<>();
    private final Map<Session, ReadDelivery> readDeliveries = new LinkedHashMap<>();
    private final Map<Session, WriteDelivery> writeDeliveries = new LinkedHashMap<>();
    private boolean deliveryPosted;
    private Request pendingWrite;
    private Request pendingRestore;
    private boolean restoring;
    private boolean running;
    private long sequence;
    private long latestWrite;
    private long completedWrite;
    private volatile FusionConfig lastKnown = FusionConfig.defaults();

    static synchronized FusionConfigRepository forApplication(Context context) {
        if (application == null) {
            Context app = context.getApplicationContext();
            Context retained = app == null ? context : app;
            application = new FusionConfigRepository(new Storage() {
                @Override public FusionConfig read() { return FusionConfigStore.read(retained); }
                @Override public FusionConfigStore.WriteResult write(FusionConfig value) {
                    return FusionConfigStore.write(retained, value);
                }
                @Override public FusionConfigStore.WriteResult restore(FusionConfig value) {
                    return FusionConfigStore.restore(retained, value);
                }
                @Override public FusionConfigStore.WriteResult undoRestore() {
                    return FusionConfigStore.undoRestore(retained);
                }
            }, Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "FusionConfigStore");
                thread.setDaemon(true);
                return thread;
            }), task -> new Handler(Looper.getMainLooper()).post(task));
        }
        return application;
    }

    FusionConfigRepository(Storage storage, Executor worker, Executor callbacks) {
        this.storage = storage;
        this.worker = worker;
        this.callbacks = callbacks;
    }

    Session newSession() {
        synchronized (lock) {
            if (sessions.size() >= MAX_SESSIONS) throw new IllegalStateException("too_many_config_sessions");
            Session session = new Session();
            sessions.add(session);
            return session;
        }
    }

    FusionConfig lastKnown() { return lastKnown; }

    final class Session implements AutoCloseable {
        private boolean closed;
        private long readGeneration;
        private long writeGeneration;
        private Consumer<FusionConfig> readCallback;
        private Consumer<FusionConfigStore.WriteResult> writeCallback;

        void read(Consumer<FusionConfig> callback) {
            synchronized (lock) {
                if (closed) return;
                readCallback = callback;
                pendingReads.put(this, new Request(this, ++readGeneration, ++sequence, null));
                start();
            }
        }

        void write(FusionConfig config, Consumer<FusionConfigStore.WriteResult> callback) {
            if (config == null) throw new IllegalArgumentException("config");
            synchronized (lock) {
                if (closed) return;
                writeCallback = callback;
                if (restoring) {
                    postWrite(new Request(this, ++writeGeneration, ++sequence, config),
                            new FusionConfigStore.WriteResult(false, lastKnown, "restore_in_progress"));
                    return;
                }
                latestWrite = ++sequence;
                if (pendingWrite != null && pendingWrite.session != this) {
                    postWrite(pendingWrite, new FusionConfigStore.WriteResult(false, lastKnown, SUPERSEDED));
                }
                pendingWrite = new Request(this, ++writeGeneration, latestWrite, config);
                start();
            }
        }

        void restore(FusionConfig config, Consumer<FusionConfigStore.WriteResult> callback) {
            if (config == null) throw new IllegalArgumentException("config");
            restoreRequest(config, false, callback);
        }

        void undoRestore(Consumer<FusionConfigStore.WriteResult> callback) {
            restoreRequest(null, true, callback);
        }

        private void restoreRequest(FusionConfig config, boolean undo, Consumer<FusionConfigStore.WriteResult> callback) {
            synchronized (lock) {
                if (closed) return;
                writeCallback = callback;
                Request request = new Request(this, ++writeGeneration, ++sequence, config, undo ? 2 : 1);
                if (restoring) {
                    postWrite(request, new FusionConfigStore.WriteResult(false, lastKnown, "restore_in_progress"));
                    return;
                }
                restoring = true;
                latestWrite = request.sequence;
                pendingRestore = request;
                start();
            }
        }

        @Override public void close() {
            synchronized (lock) {
                closed = true;
                readCallback = null;
                writeCallback = null;
                pendingReads.remove(this);
                readDeliveries.remove(this);
                writeDeliveries.remove(this);
                sessions.remove(this);
                // A submitted write remains durable even when its screen disappears.
            }
        }
    }

    private void start() {
        if (!running) {
            running = true;
            worker.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Request request;
            List<Request> reads = null;
            synchronized (lock) {
                if (pendingWrite != null) {
                    request = pendingWrite;
                    pendingWrite = null;
                } else if (pendingRestore != null) {
                    request = pendingRestore;
                    pendingRestore = null;
                } else if (!pendingReads.isEmpty()) {
                    request = null;
                    reads = new ArrayList<>(pendingReads.values());
                    pendingReads.clear();
                } else {
                    running = false;
                    return;
                }
            }
            if (reads != null) {
                FusionConfig result;
                try { result = storage.read(); }
                catch (RuntimeException error) { result = lastKnown; }
                if (result != null) lastKnown = result;
                synchronized (lock) {
                    for (Request read : reads) {
                        if (!read.session.closed && read.session.readGeneration == read.generation) readDeliveries.put(read.session,
                                new ReadDelivery(read, lastKnown));
                    }
                    postDeliveries();
                }
            } else {
                FusionConfigStore.WriteResult result;
                try {
                    result = request.operation == 1 ? storage.restore(request.config)
                            : request.operation == 2 ? storage.undoRestore() : storage.write(request.config);
                }
                catch (RuntimeException error) {
                    result = new FusionConfigStore.WriteResult(false, lastKnown,
                            error.getClass().getSimpleName());
                }
                if (result.success) lastKnown = result.config;
                synchronized (lock) {
                    completedWrite = request.sequence;
                    if (request.operation != 0) restoring = false;
                }
                synchronized (lock) { postWrite(request, result); }
            }
        }
    }

    private void deliverRead(Request request, FusionConfig value) {
        synchronized (lock) {
            Session session = request.session;
            if (!session.closed && session.readGeneration == request.generation
                    && session.readCallback != null) {
                if (completedWrite < latestWrite) {
                    pendingReads.put(session, new Request(session, request.generation, ++sequence, null));
                    start();
                    return;
                }
                Consumer<FusionConfig> callback = session.readCallback;
                session.readCallback = null;
                callback.accept(lastKnown.revision >= value.revision ? lastKnown : value);
            }
        }
    }

    private void deliverWrite(Request request, FusionConfigStore.WriteResult value) {
        synchronized (lock) {
            Session session = request.session;
            if (!session.closed && session.writeGeneration == request.generation
                    && session.writeCallback != null) {
                Consumer<FusionConfigStore.WriteResult> callback = session.writeCallback;
                session.writeCallback = null;
                callback.accept(value);
            }
        }
    }

    private void postWrite(Request request, FusionConfigStore.WriteResult value) {
        if (!request.session.closed && request.session.writeGeneration == request.generation) {
            writeDeliveries.put(request.session, new WriteDelivery(request, value));
            postDeliveries();
        }
    }

    private void postDeliveries() {
        if (!deliveryPosted && (!readDeliveries.isEmpty() || !writeDeliveries.isEmpty())) {
            deliveryPosted = true;
            callbacks.execute(this::deliverPending);
        }
    }

    private void deliverPending() {
        List<ReadDelivery> reads;
        List<WriteDelivery> writes;
        synchronized (lock) {
            reads = new ArrayList<>(readDeliveries.values());
            writes = new ArrayList<>(writeDeliveries.values());
            readDeliveries.clear();
            writeDeliveries.clear();
            deliveryPosted = false;
        }
        for (WriteDelivery write : writes) deliverWrite(write.request, write.value);
        for (ReadDelivery read : reads) deliverRead(read.request, read.value);
    }

    private static final class ReadDelivery {
        final Request request;
        final FusionConfig value;
        ReadDelivery(Request request, FusionConfig value) { this.request = request; this.value = value; }
    }

    private static final class WriteDelivery {
        final Request request;
        final FusionConfigStore.WriteResult value;
        WriteDelivery(Request request, FusionConfigStore.WriteResult value) { this.request = request; this.value = value; }
    }

    private static final class Request {
        final Session session;
        final long generation;
        final long sequence;
        final FusionConfig config;
        final int operation;

        Request(Session session, long generation, long sequence, FusionConfig config) {
            this(session, generation, sequence, config, 0);
        }

        Request(Session session, long generation, long sequence, FusionConfig config, int operation) {
            this.session = session;
            this.generation = generation;
            this.sequence = sequence;
            this.config = config;
            this.operation = operation;
        }
    }
}
