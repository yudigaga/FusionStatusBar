package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** The device-protected app setting and its injected-process subscription. */
final class ModuleLoggingConfig {
    static final String METHOD_GET = "get_module_logging";
    static final String KEY_ENABLED = "enabled";
    static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".config/module_logging");
    private static final long RESYNC_MS = 60_000L;
    private static Handler worker;

    private ModuleLoggingConfig() { }

    static synchronized Boolean read(Context source) {
        try {
            File path = file(source);
            byte[] data;
            try {
                data = new BoundedAtomicDocument(path, 1).read();
            } catch (FileNotFoundException missing) {
                return path.exists() ? null : Boolean.TRUE;
            }
            return data.length == 1 && (data[0] == 0 || data[0] == 1) ? data[0] == 1 : null;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    static synchronized boolean save(Context source, boolean enabled) {
        try {
            new BoundedAtomicDocument(file(source), 1).write(new byte[] { (byte) (enabled ? 1 : 0) });
        } catch (IOException | RuntimeException failure) {
            return false;
        }
        ModuleLog.configure(enabled);
        try {
            source.getContentResolver().notifyChange(URI, null, 0);
        } catch (RuntimeException unavailable) {
            // The durable setting is valid even if Android cannot dispatch this notification.
        }
        return true;
    }

    private static File file(Context source) {
        Context device = source.createDeviceProtectedStorageContext();
        return new File(device.getFilesDir(), "module_logging_v1");
    }

    static synchronized void attachInjected(Context source) {
        if (!ModuleLog.isInjected() || source == null || worker != null) return;
        Context application = source.getApplicationContext();
        HandlerThread thread = new HandlerThread("FusionModuleLogging");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(new Subscription(application != null ? application : source, worker)::subscribe);
    }

    static boolean applySnapshot(Bundle snapshot) {
        if (snapshot == null || !snapshot.containsKey(KEY_ENABLED)) return false;
        // A malformed response cannot turn a previously muted process back on.
        Object value = snapshot.get(KEY_ENABLED);
        if (!(value instanceof Boolean enabled)) return false;
        ModuleLog.configure(enabled);
        return true;
    }

    static final class Subscription extends ContentObserver {
        private final Context source;
        private final Handler handler;
        private final Runnable retry = this::subscribe;
        private boolean registered;
        private int failures;

        Subscription(Context source, Handler handler) {
            super(handler);
            this.source = source;
            this.handler = handler;
        }

        void subscribe() {
            handler.removeCallbacks(retry);
            try {
                var resolver = source.getContentResolver();
                if (!registered) {
                    try {
                        resolver.registerContentObserver(URI, false, this);
                        registered = true;
                    } catch (RuntimeException unavailable) {
                        // A usable provider can still be read when observation is unavailable.
                    }
                }
                if (applySnapshot(resolver.call(URI, METHOD_GET, null, null))) {
                    failures = 0;
                    // Recover a missed notification without reading preferences on log calls.
                    handler.postDelayed(retry, RESYNC_MS);
                    return;
                }
            } catch (RuntimeException failure) {
                // Device-protected state or system services may not be ready at boot.
            }
            handler.postDelayed(retry, 1_000L << failures);
            failures = Math.min(failures + 1, 5);
        }

        @Override public void onChange(boolean selfChange) { subscribe(); }
    }
}
