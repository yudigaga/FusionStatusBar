package com.xtjm.fusionstatusbar;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class FusionModule extends XposedModule {
    static final String TAG = "FusionStatusBar";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private final AtomicBoolean loggingContextHooked = new AtomicBoolean();

    public FusionModule() {
        super();
    }

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        ModuleLog.startInjected();
    }

    static void logModule(XposedModule module, int priority, String tag, String message) {
        logModule(module, priority, tag, message, null);
    }

    static void logModule(XposedModule module, int priority, String tag, String message, Throwable error) {
        if (!ModuleLog.acceptsDiagnostics()) return;
        ModuleLog.write(() -> {
            try {
                if (error == null) module.log(priority, tag, message);
                else module.log(priority, tag, message, error);
            } catch (RuntimeException | LinkageError unavailable) {
                // A missing framework sink must not affect the host or the gate.
            }
        });
    }

    private void captureLoggingContext(String hostPackage) {
        if (!loggingContextHooked.compareAndSet(false, true)) return;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setId("fusion-statusbar:module-logging-app")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getArg(0) instanceof Context base
                                && hostPackage.equals(base.getPackageName())
                                && chain.getThisObject() instanceof Application app) {
                            ModuleLoggingConfig.attachInjected(app);
                        }
                        return result;
                    });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            loggingContextHooked.set(false);
        }
    }

    private void captureSystemLoggingContext(ClassLoader loader) {
        // Some frameworks call onSystemServerStarting after createSystemContext.
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread", false, loader);
            Method current = thread.getDeclaredMethod("currentActivityThread");
            Field systemContext = thread.getDeclaredField("mSystemContext");
            current.setAccessible(true);
            systemContext.setAccessible(true);
            Object instance = current.invoke(null);
            if (instance != null && systemContext.get(instance) instanceof Context context) {
                ModuleLoggingConfig.attachInjected(context);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Context may not exist yet. Try the creation callback below.
        }
        try {
            Class<?> server = Class.forName("com.android.server.SystemServer", false, loader);
            Method create = server.getDeclaredMethod("createSystemContext");
            Field field = server.getDeclaredField("mSystemContext");
            field.setAccessible(true);
            hook(create).setId("fusion-statusbar:module-logging-system")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            if (field.get(chain.getThisObject()) instanceof Context context)
                                ModuleLoggingConfig.attachInjected(context);
                        } catch (IllegalAccessException | RuntimeException unavailable) {
                            // Fall back to existing system-context capture.
                        }
                        return result;
                    });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Existing system-context capture can start the same subscription later.
        }
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        captureSystemLoggingContext(param.getClassLoader());
        try {
            int installed = ScreenshotClipboardHooks.install(this);
            logModule(this, Log.INFO, TAG, "screenshot clipboard provider bootstrap installed=" + installed);
        } catch (Throwable error) {
            logModule(this, Log.ERROR, TAG, "screenshot clipboard provider bootstrap failed", error);
        }
        try {
            int installed = SystemStartConfirmationHooks.install(this, param.getClassLoader());
            logModule(this, Log.INFO, TAG, "system start confirmation hooks version=" + BuildConfig.VERSION_NAME
                    + " installed=" + installed);
        } catch (Throwable error) {
            logModule(this, Log.ERROR, TAG, "system start confirmation hooks failed", error);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String hostPackage = param.getPackageName();
        if ("com.android.systemui".equals(hostPackage) || "com.miui.home".equals(hostPackage)
                || SecurityCenterConfigRuntime.PACKAGE.equals(hostPackage)) {
            captureLoggingContext(hostPackage);
        }
        if (SecurityCenterConfigRuntime.PACKAGE.equals(param.getPackageName())) {
            SecurityCenterConfigRuntime.prepare(param.getApplicationInfo());
            return;
        }
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        installSystemUi(param.getDefaultClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String hostPackage = param.getPackageName();
        if ("com.android.systemui".equals(hostPackage) || "com.miui.home".equals(hostPackage)
                || SecurityCenterConfigRuntime.PACKAGE.equals(hostPackage)) {
            captureLoggingContext(hostPackage);
        }
        if ("com.miui.securitycenter".equals(param.getPackageName())) {
            try {
                if (SecurityCenterConfigRuntime.prepare(param.getApplicationInfo()))
                    SecurityCenterHooks.install(this, param.getClassLoader());
                else logModule(this, Log.WARN, TAG, "phone manager extensions unsupported host version; expected=40001380");
            } catch (Throwable error) {
                logModule(this, Log.ERROR, TAG, "phone manager extensions load failed", error);
            }
            try {
                int installed = SidebarHooks.install(this, param.getClassLoader());
                logModule(this, Log.INFO, TAG, "sidebar hooks loaded count=" + installed);
            } catch (Throwable error) {
                ModuleLog.e(TAG, "sidebar module load failed", error);
                logModule(this, Log.ERROR, TAG, "sidebar module load failed", error);
            }
            return;
        }
        if ("com.miui.home".equals(param.getPackageName())) {
            installLauncher(param.getClassLoader());
            return;
        }
        if ("com.android.systemui".equals(param.getPackageName())) {
            installSystemUi(param.getClassLoader());
            return;
        }
        if (!"miui.systemui.plugin".equals(param.getPackageName())) {
            return;
        }
        // The plugin also has isolated processes (for example :flashlight). Their
        // class loaders do not contain SystemUI's QS interfaces; the main SystemUI
        // process already captures the shared plugin loader through PluginFactory.
        if (!STARTED.get()) {
            return;
        }
        try {
            SystemUiHooks.installPluginHooks(this, param.getClassLoader());
            logModule(this, Log.INFO, TAG, "systemui plugin hooks loaded");
        } catch (Throwable error) {
            ModuleLog.e(TAG, "systemui plugin hooks failed", error);
            logModule(this, Log.ERROR, TAG, "systemui plugin hooks failed", error);
        }
    }

    private void installLauncher(ClassLoader classLoader) {
        try {
            int installed = LauncherHooks.install(this, classLoader);
            logModule(this, Log.INFO, TAG, "launcher hooks loaded count=" + installed);
        } catch (Throwable error) {
            ModuleLog.e(TAG, "launcher module load failed", error);
            logModule(this, Log.ERROR, TAG, "launcher module load failed", error);
        }
    }

    private void installSystemUi(ClassLoader classLoader) {
        try {
            logModule(this, Log.INFO, TAG, "systemui module version=" + BuildConfig.VERSION_NAME
                    + " versionCode=" + BuildConfig.VERSION_CODE);
            SystemUiHooks.install(this, classLoader);
            STARTED.set(classLoader != null);
            logModule(this, Log.INFO, TAG, "systemui module loaded");
        } catch (Throwable error) {
            ModuleLog.e(TAG, "systemui module load failed", error);
            logModule(this, Log.ERROR, TAG, "systemui module load failed", error);
        }
    }
}
