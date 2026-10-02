package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial, generation-checked capture loading. The view borrows the loader's one cached bitmap. */
final class ControlCenterPreviewLoader {
    interface Listener {
        void loaded(long generation, Bitmap bitmap, String layout);
        void failed(long generation, Throwable error);
        default void waiting(long generation) { }
    }
    interface Source {
        Bundle state();
        InputStream open(long generation) throws Exception;
        default long request() { return 0; }
        default void clear() { }
    }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FusionPreviewDecode"); thread.setDaemon(true); return thread;
    });
    private static final long MAX_SOURCE_PIXELS = 64L * 1024 * 1024;
    private final Source source;
    private final java.util.concurrent.Executor worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long epoch;
    private boolean closed;
    private boolean running;
    private Request pending;
    private Request active;
    private Runnable pendingCommand;
    private boolean commandRunning;
    private Bitmap cached;
    private long cachedGeneration;
    private int cachedWidth;
    private String cachedLayout;
    private String cachedCatalog = "";

    private static final class Request {
        final long epoch, minimumGeneration;
        final int width;
        final Listener listener;
        Request(long epoch, long minimumGeneration, int width, Listener listener) {
            this.epoch = epoch; this.minimumGeneration = minimumGeneration;
            this.width = width; this.listener = listener;
        }
    }

    ControlCenterPreviewLoader(Context context) {
        this(new Source() {
            private final Context app = context.getApplicationContext();
            @Override public Bundle state() { return FusionConfig.controlCenterPreviewState(app); }
            @Override public InputStream open(long generation) throws Exception {
                return app.getContentResolver().openInputStream(FusionConfig.controlCenterPreviewUri(generation));
            }
            @Override public long request() { return FusionConfig.requestControlCenterPreview(app); }
            @Override public void clear() { FusionConfig.clearControlCenterPreview(app); }
        }, IO);
    }

    ControlCenterPreviewLoader(Source source, java.util.concurrent.Executor worker) {
        this.source = source; this.worker = worker;
    }

    synchronized void load(long minimumGeneration, int width, Listener listener) {
        if (closed) return;
        int boundedWidth = Math.max(1, Math.min(1440, width));
        if (active != null && active.epoch == epoch && active.minimumGeneration == minimumGeneration
                && active.width == boundedWidth && pending == null) return;
        pending = new Request(++epoch, minimumGeneration, boundedWidth, listener);
        if (!running) {
            running = true;
            worker.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Request request;
            synchronized (this) {
                request = pending; pending = null;
                if (closed || request == null) { running = false; return; }
                active = request;
            }
            load(request);
            synchronized (this) { if (active == request) active = null; }
        }
    }

    void request(java.util.function.LongConsumer callback) {
        final long requestEpoch;
        synchronized (this) {
            if (closed) return;
            requestEpoch = ++epoch; pending = null;
        }
        submitCommand(() -> {
            long requested;
            try { requested = source.request(); }
            catch (RuntimeException error) { requested = 0; }
            long result = requested;
            main.post(() -> {
                synchronized (ControlCenterPreviewLoader.this) {
                    if (closed || requestEpoch != epoch) return;
                }
                callback.accept(result);
            });
        });
    }

    void clearRemote() { submitCommand(() -> {
        try { source.clear(); }
        catch (RuntimeException error) { android.util.Log.w("FusionStatusBar", "Unable to clear preview", error); }
    }); }

    synchronized String nativeTileCatalog() { return cachedCatalog; }

    private synchronized void submitCommand(Runnable command) {
        if (closed) return;
        pendingCommand = command;
        if (commandRunning) return;
        commandRunning = true;
        worker.execute(() -> {
            while (true) {
                Runnable next;
                synchronized (ControlCenterPreviewLoader.this) {
                    next = pendingCommand; pendingCommand = null;
                    if (next == null) { commandRunning = false; return; }
                }
                next.run();
            }
        });
    }

    private void load(Request request) {
        Bitmap bitmap = null;
        long ready = 0;
        boolean borrowed = false;
        try {
            Bundle state = source.state();
            ready = state == null ? 0 : state.getLong(FusionConfig.KEY_PREVIEW_READY);
            long requested = state == null ? 0 : state.getLong(FusionConfig.KEY_PREVIEW_REQUEST);
            String layout = state == null ? "" : state.getString(FusionConfig.KEY_PREVIEW_LAYOUT, "");
            String catalog = state == null ? "" : state.getString(FusionConfig.KEY_PREVIEW_CATALOG, "");
            if (ready <= 0 || ready < requested || ready < request.minimumGeneration || layout.isEmpty()) {
                if (requested > ready) main.post(() -> {
                    synchronized (ControlCenterPreviewLoader.this) {
                        if (closed || request.epoch != epoch) return;
                    }
                    request.listener.waiting(requested);
                });
                return;
            }
            if (!ControlCenterActualEditor.hasSerializedTiles(layout)) throw new IllegalArgumentException("Capture has no editable tiles");
            synchronized (this) {
                if (cached != null && cachedGeneration == ready && cachedWidth == request.width
                        && layout.equals(cachedLayout) && catalog.equals(cachedCatalog)) {
                    bitmap = cached; borrowed = true;
                }
            }
            if (bitmap == null) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = source.open(ready)) { BitmapFactory.decodeStream(input, null, bounds); }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                        || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("Invalid capture dimensions");
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, request.width);
                try (InputStream input = source.open(ready)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
                if (bitmap == null) throw new IllegalArgumentException("Unable to decode capture");
            }
            Bundle current = source.state();
            if (current == null || current.getLong(FusionConfig.KEY_PREVIEW_REQUEST) != requested
                    || current.getLong(FusionConfig.KEY_PREVIEW_READY) != ready
                    || !layout.equals(current.getString(FusionConfig.KEY_PREVIEW_LAYOUT, ""))
                    || !catalog.equals(current.getString(FusionConfig.KEY_PREVIEW_CATALOG, ""))) {
                if (!borrowed) release(bitmap);
                return;
            }
            Bitmap result = bitmap;
            long generation = ready;
            boolean reused = borrowed;
            main.post(() -> {
                Bitmap previous;
                synchronized (ControlCenterPreviewLoader.this) {
                    if (closed || request.epoch != epoch) {
                        if (!reused) release(result);
                        return;
                    }
                    previous = cached;
                    cached = result; cachedGeneration = generation;
                    cachedWidth = request.width; cachedLayout = layout; cachedCatalog = catalog;
                }
                request.listener.loaded(generation, result, layout);
                if (previous != result) release(previous);
            });
        } catch (Exception | OutOfMemoryError error) {
            if (!borrowed) release(bitmap);
            long generation = ready;
            main.post(() -> {
                synchronized (ControlCenterPreviewLoader.this) {
                    if (closed || request.epoch != epoch) return;
                }
                request.listener.failed(generation, error);
            });
        }
    }

    static int sampleSize(int width, int height, int targetWidth) {
        int sample = 1;
        while (width / (sample * 2) >= targetWidth || (long) (width / sample) * (height / sample) > 4L * 1024 * 1024) sample *= 2;
        return sample;
    }

    /** Caller must detach the preview from its view before clearing or closing the loader. */
    synchronized void clear() {
        epoch++; pending = null;
        release(cached); cached = null; cachedGeneration = 0; cachedLayout = null; cachedCatalog = "";
    }

    synchronized void close() { closed = true; clear(); }
    private static void release(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
}
