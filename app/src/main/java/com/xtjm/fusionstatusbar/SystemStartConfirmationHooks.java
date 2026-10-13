package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Controls MIUI's activity-start confirmation gate inside system_server. */
final class SystemStartConfirmationHooks {
    static final String LOCAL_SERVICE = "com.miui.server.SecurityManagerService$LocalService";
    static final String CHECK_START = "checkAllowStartActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean ALLOW_REPORTED = new AtomicBoolean();

    private SystemStartConfirmationHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        if (module == null || loader == null || !INSTALLED.compareAndSet(false, true)) return 0;
        MiscSystemConfigExchange.setReporter(module);
        Class<?> service;
        try {
            service = Class.forName(LOCAL_SERVICE, false, loader);
        } catch (ClassNotFoundException error) {
            module.log(Log.WARN, FusionModule.TAG, "system start confirmation class unavailable: " + LOCAL_SERVICE);
            return 0;
        }
        int installed = 0;
        for (Method method : supportedMethods(service)) {
            try {
                method.setAccessible(true);
                try { module.deoptimize(method); }
                catch (Throwable ignored) { }
                module.hook(method).setId("fusion-statusbar:system-start:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            MiscConfig snapshot = MiscSystemConfigExchange.current();
                            boolean enabled = snapshot.removeChainStartDialog;
                            if (enabled && ALLOW_REPORTED.compareAndSet(false, true))
                                module.log(Log.INFO, FusionModule.TAG, "system start confirmation gate bypass active");
                            if (!enabled) ALLOW_REPORTED.set(false);
                            return interceptCheck(chain, snapshot);
                        });
                installed++;
            } catch (Throwable error) {
                module.log(Log.WARN, FusionModule.TAG, "system start confirmation method unavailable", error);
            }
        }
        if (installed == 0) {
            module.log(Log.WARN, FusionModule.TAG, "system start confirmation boolean gate unavailable");
            return 0;
        }
        installContextCapture(module, loader);
        attachSystemContext(loader);
        return installed;
    }

    static ArrayList<Method> supportedMethods(Class<?> service) {
        ArrayList<Method> methods = new ArrayList<>();
        for (Method method : service.getDeclaredMethods()) {
            if (CHECK_START.equals(method.getName()) && method.getReturnType() == boolean.class)
                methods.add(method);
        }
        return methods;
    }

    static Object interceptCheck(XposedInterface.Chain chain, MiscConfig snapshot) throws Throwable {
        return snapshot.removeChainStartDialog ? Boolean.TRUE : chain.proceed();
    }

    private static void installContextCapture(XposedModule module, ClassLoader loader) {
        try {
            Class<?> server = Class.forName("com.android.server.SystemServer", false, loader);
            Method createContext = server.getDeclaredMethod("createSystemContext");
            createContext.setAccessible(true);
            module.hook(createContext).setId("fusion-statusbar:system-start:system-context")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        attachSystemContext(loader);
                        return result;
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "system start confirmation context hook unavailable", error);
        }
        // OEM service constructors expose the same system Context when server setup differs.
        try {
            Class<?> owner = Class.forName("com.miui.server.SecurityManagerService", false, loader);
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                boolean hasContext = false;
                for (Class<?> parameter : constructor.getParameterTypes())
                    hasContext |= Context.class.isAssignableFrom(parameter);
                if (!hasContext) continue;
                constructor.setAccessible(true);
                module.hook(constructor).setId("fusion-statusbar:system-start:context:" + constructor.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            for (Object argument : chain.getArgs())
                                if (argument instanceof Context context) MiscSystemConfigExchange.attach(context);
                            return result;
                        });
            }
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "system start confirmation OEM context hook unavailable", error);
        }
    }

    private static void attachSystemContext(ClassLoader loader) {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread", false, loader);
            Method current = thread.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object instance = current.invoke(null);
            if (instance == null) return;
            Method context = thread.getDeclaredMethod("getSystemContext");
            context.setAccessible(true);
            MiscSystemConfigExchange.attach((Context) context.invoke(instance));
        } catch (ReflectiveOperationException | RuntimeException error) {
            MiscSystemConfigExchange.report("system start confirmation context not ready");
        }
    }
}
