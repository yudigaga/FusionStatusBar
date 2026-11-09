package com.xtjm.fusionstatusbar;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Protects just the screenshot preference in the real, system-hosted SettingsProvider. */
final class ScreenshotClipboardHooks {
    static final String SETTING = "mi_screen_shots_write_clipboard_enable";
    private static final String PROVIDER = "com.android.providers.settings.SettingsProvider";
    private static final AtomicBoolean BOOTSTRAP = new AtomicBoolean();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static Context context;
    private static volatile Handler worker;
    private static boolean receiverRegistered;
    private static boolean observerRegistered;
    private static int registrationAttempts;
    private static int applyAttempts;
    private static final Runnable APPLY = ScreenshotClipboardHooks::applyCurrentConfig;

    private ScreenshotClipboardHooks() { }

    // Xposed-only system_server bootstrap; the framework grants its module loader hidden-API access.
    @android.annotation.SuppressLint("SoonBlockedPrivateApi")
    static int install(XposedModule module) throws ReflectiveOperationException {
        if (!BOOTSTRAP.compareAndSet(false, true)) return 0;
        Method attach;
        try {
            // Hook the full body so an inlined two-argument forwarding method cannot bypass capture.
            attach = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class,
                    boolean.class);
        } catch (NoSuchMethodException unavailable) {
            attach = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
        }
        attach.setAccessible(true);
        try { module.deoptimize(attach); } catch (Throwable ignored) { }
        module.hook(attach).setId("fusion-statusbar:screenshot:provider-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Class<?> type = chain.getThisObject().getClass();
                    while (type != null && !PROVIDER.equals(type.getName())) type = type.getSuperclass();
                    if (type == null) return chain.proceed();
                    installProvider(module, type);
                    Object result = chain.proceed();
                    Context source = (Context) chain.getArgs().get(0);
                    try {
                        Context system = "android".equals(source.getPackageName()) ? source
                                : source.createPackageContext("android", 0);
                        MiscSystemConfigExchange.attach(system);
                        if (MiscSystemConfigExchange.current().screenshotClipboardEnabled) requestApply();
                    } catch (Exception error) {
                        module.log(Log.WARN, FusionModule.TAG, "screenshot clipboard system context unavailable", error);
                    }
                    return result;
                });
        return 1;
    }

    private static void installProvider(XposedModule module, Class<?> provider) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        int count = 0;
        try {
            int[] operations = new int[4];
            String[] names = {"INSERT", "DELETE", "UPDATE", "RESET"};
            for (int i = 0; i < names.length; i++) {
                Field field = provider.getDeclaredField("MUTATION_OPERATION_" + names[i]);
                field.setAccessible(true);
                operations[i] = field.getInt(null);
            }
            for (Method method : supportedMethods(provider)) {
                method.setAccessible(true);
                try { module.deoptimize(method); } catch (Throwable ignored) { }
                module.hook(method).setId("fusion-statusbar:screenshot:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                                interceptCurrentMutation(chain, operations,
                                        () -> MiscSystemConfigExchange.current().screenshotClipboardEnabled,
                                        ScreenshotClipboardHooks::requestApply));
                count++;
            }
            // These small private callers can inline the mutation overloads in a compiled provider.
            for (Method method : provider.getDeclaredMethods()) {
                if (java.util.Set.of("insertSecureSetting", "deleteSecureSetting", "updateSecureSetting",
                        "resetSecureSetting").contains(method.getName())) {
                    try { module.deoptimize(method); } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "screenshot clipboard provider hooks unavailable", error);
        }
        module.log(count > 0 ? Log.INFO : Log.WARN, FusionModule.TAG,
                "screenshot clipboard mutation hooks installed=" + count);
    }

    static ArrayList<Method> supportedMethods(Class<?> provider) {
        ArrayList<Method> selected = new ArrayList<>();
        Class<?>[] prefix = {String.class, String.class, String.class, boolean.class,
                int.class, int.class, boolean.class, int.class};
        for (Method method : provider.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"mutateSecureSetting".equals(method.getName()) || method.getReturnType() != boolean.class
                    || parameters.length < 8 || parameters.length > 9) continue;
            boolean supported = parameters.length == 8 || parameters[8] == boolean.class;
            for (int i = 0; i < prefix.length; i++) supported &= parameters[i] == prefix[i];
            if (supported) selected.add(method);
        }
        return selected;
    }

    static Object interceptCurrentMutation(XposedInterface.Chain chain, int[] operations,
            BooleanSupplier enabled, Runnable reconcile) throws Throwable {
        boolean before = enabled.getAsBoolean();
        Object result = interceptMutation(chain, before, operations, () -> {
            if (enabled.getAsBoolean()) reconcile.run();
        });
        if (before != enabled.getAsBoolean()) reconcile.run();
        return result;
    }

    static Object interceptMutation(XposedInterface.Chain chain, boolean enabled,
            int[] operations, Runnable reconcile) throws Throwable {
        if (!enabled) return chain.proceed();
        Object[] args = chain.getArgs().toArray();
        int operation = (Integer) args[5];
        if (SETTING.equals(args[0]) && (operation == operations[0]
                || operation == operations[1] || operation == operations[2])) {
            args[1] = "1";
            args[5] = operations[0];
            // Keep the provider's permission, user and policy checks in the original chain.
            return chain.proceed(args);
        }
        Object result = chain.proceed();
        if (operation == operations[3] && Boolean.TRUE.equals(result)) reconcile.run();
        return result;
    }

    static synchronized void attach(Context system, Handler dispatcher) {
        if (worker != null) return;
        context = system;
        worker = dispatcher;
        worker.post(ScreenshotClipboardHooks::registerObservers);
        if (MiscSystemConfigExchange.current().screenshotClipboardEnabled) requestApply();
    }

    static void configChanged(boolean previousEnabled, boolean enabled) {
        if (enabled || previousEnabled != enabled) requestApply();
    }

    private static void requestApply() {
        Handler dispatcher = worker;
        if (dispatcher == null) return;
        dispatcher.removeCallbacks(APPLY);
        dispatcher.post(APPLY);
    }

    private static void registerObservers() {
        try {
            if (!receiverRegistered) context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context source, Intent intent) {
                    if (MiscSystemConfigExchange.current().screenshotClipboardEnabled) requestApply();
                }
            }, new IntentFilter("android.intent.action.USER_SWITCHED"), null, worker, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
            if (!observerRegistered) {
                ContentResolver.class.getMethod("registerContentObserver", android.net.Uri.class,
                        boolean.class, ContentObserver.class, int.class).invoke(context.getContentResolver(),
                        Settings.Secure.getUriFor(SETTING), false, new ContentObserver(worker) {
                            @Override public void onChange(boolean selfChange) {
                                if (MiscSystemConfigExchange.current().screenshotClipboardEnabled) requestApply();
                            }
                        }, -1);
                observerRegistered = true;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (registrationAttempts == 0)
                MiscSystemConfigExchange.report("screenshot clipboard observers awaiting system services");
        }
        if ((!receiverRegistered || !observerRegistered) && ++registrationAttempts <= 30)
            worker.postDelayed(ScreenshotClipboardHooks::registerObservers, 1000L);
    }

    private static void applyCurrentConfig() {
        boolean enabled = MiscSystemConfigExchange.current().screenshotClipboardEnabled;
        boolean applied = true;
        long identity = Binder.clearCallingIdentity();
        try {
            for (int user : userIds(context)) applied &= applyForUser(context.getContentResolver(), user, enabled);
        } catch (ReflectiveOperationException | RuntimeException error) {
            applied = false;
            if (applyAttempts == 0)
                MiscSystemConfigExchange.report("screenshot clipboard setting awaiting provider: "
                        + error.getClass().getSimpleName());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        if (applied) {
            applyAttempts = 0;
            MiscSystemConfigExchange.report("screenshot clipboard setting applied enabled=" + enabled);
        } else if (++applyAttempts <= 30) {
            worker.removeCallbacks(APPLY);
            worker.postDelayed(APPLY, 1000L);
        } else {
            MiscSystemConfigExchange.report("screenshot clipboard setting could not be applied");
            applyAttempts = 0;
        }
    }

    static boolean applyForUser(ContentResolver resolver, int user, boolean enabled)
            throws ReflectiveOperationException {
        String value = enabled ? "1" : "0";
        Method read = Settings.Secure.class.getMethod("getStringForUser", ContentResolver.class,
                String.class, int.class);
        Object existing = read.invoke(null, resolver, SETTING, user);
        if (value.equals(existing)) return true;
        Settings.Secure.class.getMethod("putStringForUser", ContentResolver.class,
                String.class, String.class, int.class).invoke(null, resolver, SETTING, value, user);
        return value.equals(read.invoke(null, resolver, SETTING, user));
    }

    private static LinkedHashSet<Integer> userIds(Context source) throws ReflectiveOperationException {
        LinkedHashSet<Integer> users = new LinkedHashSet<>();
        try {
            users.add((Integer) ActivityManager.class.getMethod("getCurrentUser").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException error) {
            users.add(0);
        }
        UserManager manager = source.getSystemService(UserManager.class);
        if (manager == null) return users;
        Method identifier = UserHandle.class.getMethod("getIdentifier");
        try {
            Object handles = UserManager.class.getMethod("getUserHandles", boolean.class).invoke(manager, true);
            if (handles instanceof Iterable<?> entries)
                for (Object entry : entries) if (entry instanceof UserHandle user)
                    users.add((Integer) identifier.invoke(user));
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (UserHandle user : manager.getUserProfiles()) users.add((Integer) identifier.invoke(user));
        }
        return users;
    }
}
