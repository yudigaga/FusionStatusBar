package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;

final class SecurityCenterConfigRuntime {
    static final String PACKAGE = "com.miui.securitycenter";
    static final long VERSION_CODE = 40001380L;
    private static final String CACHE = "fusion_statusbar_security_config_v1.json";
    private static final int MAX_REFRESH_RETRIES = 5;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile MiscConfig snapshot = MiscConfig.defaults();
    private static volatile boolean supported;
    private static FusionConfigStore.SnapshotFile cache;
    private static String cachePath;
    private static WeakReference<Context> context = new WeakReference<>(null);
    private static Handler worker;
    private static int retries;
    private static int registrationRetries;
    private static boolean receiverRegistered;
    private static boolean observerRegistered;
    private static final Runnable REFRESH = SecurityCenterConfigRuntime::refresh;
    private SecurityCenterConfigRuntime() { }

    static synchronized boolean prepare(ApplicationInfo info) {
        try {
            supported = info != null && PACKAGE.equals(info.packageName)
                    && ApplicationInfo.class.getField("longVersionCode").getLong(info) == VERSION_CODE;
            if (!supported) { snapshot = MiscConfig.defaults(); return false; }
            String directory = info.deviceProtectedDataDir != null ? info.deviceProtectedDataDir : info.dataDir;
            if (directory == null) return true;
            String path = new File(new File(directory, "files"), CACHE).getAbsolutePath();
            if (!path.equals(cachePath)) {
                cachePath = path;
                cache = new SecurityCenterConfigCache(new File(path));
                snapshot = MiscConfigStore.read(cache);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            supported = false;
            snapshot = MiscConfig.defaults();
            Log.w(FusionModule.TAG, "phone manager version unavailable", error);
            return false;
        }
    }

    static MiscConfig current() { return supported ? snapshot : MiscConfig.defaults(); }

    static void loadInitialConfig(Context source) {
        if (!supported || source == null || !PACKAGE.equals(source.getPackageName())
                || RuntimeConfigCache.settingsPackageDisabled(source)) return;
        MiscConfig loaded = readProvider(source);
        if (loaded != null) accept(loaded);
    }

    static int install(XposedModule module, ClassLoader loader) {
        if (!supported || !INSTALLED.compareAndSet(false, true)) return 0;
        int count = 0;
        try {
            Class<?> application = Class.forName("com.miui.securitycenter.Application", false, loader);
            Method attach = application.getDeclaredMethod("attachBaseContext", Context.class);
            count += SecurityCenterHookSupport.hook(module, attach, "application-attach", chain -> {
                Object result = chain.proceed();
                attach((Context) chain.getThisObject());
                return result;
            });
            Method create = application.getDeclaredMethod("onCreate");
            count += SecurityCenterHookSupport.hook(module, create, "application-create", chain -> {
                attach((Context) chain.getThisObject());
                return chain.proceed();
            });
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "phone manager config context capture unavailable", error);
        }
        return count;
    }

    private static synchronized void attach(Context source) {
        if (!supported || source == null || !PACKAGE.equals(source.getPackageName()) || worker != null) return;
        Context app = source.getApplicationContext();
        context = new WeakReference<>(app == null ? source : app);
        if (cache == null) {
            File file = new File(source.createDeviceProtectedStorageContext().getFilesDir(), CACHE);
            cachePath = file.getAbsolutePath();
            cache = new SecurityCenterConfigCache(file);
            snapshot = MiscConfigStore.read(cache);
        }
        // Host initializers may cache capability results in onCreate. Prefer one fresh authoritative
        // snapshot before they run, while leaving the DP snapshot intact if the provider is unavailable.
        loadInitialConfig(source);
        HandlerThread thread = new HandlerThread("FusionSecurityConfig");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(SecurityCenterConfigRuntime::registerEndpoints);
        worker.post(REFRESH);
    }

    private static void registerEndpoints() {
        Context source = context.get();
        if (source == null) return;
        try {
            if (!receiverRegistered) source.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context host, Intent intent) { receive(host, intent); }
            }, new IntentFilter(MiscSystemConfigExchange.ACTION_PUBLISH), RuntimeConfigExchange.PUBLISH_PERMISSION,
                    worker, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
            if (!observerRegistered) source.getContentResolver().registerContentObserver(MiscConfig.contentUri(),
                    false, new ContentObserver(worker) {
                        @Override public void onChange(boolean selfChange) {
                            retries = 0;
                            worker.removeCallbacks(REFRESH);
                            worker.post(REFRESH);
                        }
                    });
            observerRegistered = true;
        } catch (RuntimeException error) {
            if (registrationRetries == 0) Log.w(FusionModule.TAG, "phone manager config registration pending", error);
        }
        if ((!receiverRegistered || !observerRegistered) && ++registrationRetries <= 10)
            worker.postDelayed(SecurityCenterConfigRuntime::registerEndpoints, 1000L);
    }

    private static void refresh() {
        Context source = context.get();
        if (source == null || RuntimeConfigCache.settingsPackageDisabled(source)) return;
        MiscConfig loaded = readProvider(source);
        if (loaded == null) {
            if (++retries <= MAX_REFRESH_RETRIES)
                worker.postDelayed(REFRESH, 1000L << (retries - 1));
            return;
        }
        accept(loaded);
        retries = 0;
    }

    private static MiscConfig readProvider(Context source) {
        try {
            Bundle values = source.getContentResolver().call(MiscConfig.contentUri(), MiscConfig.METHOD_GET,
                    null, null);
            return MiscConfig.validUpdate(values) ? MiscConfig.fromBundle(values) : null;
        } catch (RuntimeException error) {
            Log.w(FusionModule.TAG, "phone manager config provider unavailable: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    static void addListener(Runnable changed) { LISTENERS.addIfAbsent(changed); }

    static boolean receive(Context source, Intent intent) {
        if (!supported || source == null || !PACKAGE.equals(source.getPackageName()) || intent == null
                || !MiscSystemConfigExchange.ACTION_PUBLISH.equals(intent.getAction())
                || !PACKAGE.equals(intent.getPackage()) || !MiscConfig.validUpdate(intent.getExtras())) return false;
        accept(MiscConfig.fromBundle(intent.getExtras()));
        return true;
    }

    private static void accept(MiscConfig loaded) {
        snapshot = loaded;
        if (cache != null && !MiscConfigStore.write(cache, loaded))
            Log.w(FusionModule.TAG, "phone manager runtime cache save failed");
        for (Runnable changed : LISTENERS) {
            try { changed.run(); }
            catch (RuntimeException error) { Log.w(FusionModule.TAG, "phone manager config listener failed", error); }
        }
    }
}
