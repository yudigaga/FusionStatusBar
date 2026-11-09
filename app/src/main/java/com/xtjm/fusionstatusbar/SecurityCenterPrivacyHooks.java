package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Optional privacy and display changes within the supported Security Center host. */
final class SecurityCenterPrivacyHooks {
    private static final Map<TextView, String> SCORE_TEXT = new WeakHashMap<>();
    private static final Runnable SCORE_CHANGED = SecurityCenterPrivacyHooks::onConfigChanged;

    private SecurityCenterPrivacyHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        int count = SecurityCenterHookSupport.forceBoolean(module, loader,
                SecurityCenterFeature.SMART_PRIVACY, "t3.g", "k");
        count += SecurityCenterHookSupport.forceValue(module, loader, SecurityCenterFeature.SKIP_TEN_SECONDS,
                "com.miui.permcenter.privacymanager.InterceptPermissionFragment", "g0", int.class, 0);
        count += contextual(module, loader, SecurityCenterFeature.MOTION_SICKNESS, "r6.b", "h",
                boolean.class, chain -> motionSensorsAvailable(argumentContext(chain)));
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SecurityCenterFeature.MOTION_SICKNESS,
                "r6.b", "i", Context.class);
        count += contextual(module, loader, SecurityCenterFeature.MOTION_SICKNESS, "r6.b", "a",
                int.class, chain -> reminderTime(argumentContext(chain)));
        count += contextual(module, loader, SecurityCenterFeature.MOTION_SICKNESS, "r6.b", "g",
                boolean.class, chain -> motionEnabled(argumentContext(chain)));
        count += contextual(module, loader, SecurityCenterFeature.CAR_CRASH_DETECTION,
                "com.miui.warningcenter.caraccident.CarAccidentUtils", "isSupportCarAccidentDetect",
                boolean.class, chain -> crashSensorAvailable(argumentContext(chain)));
        count += installScore(module, loader);
        SecurityCenterConfigRuntime.addListener(SCORE_CHANGED);
        return count;
    }

    private static int installScore(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.miui.common.customview.ScoreTextView", false, loader);
            // Native setters and the animation callbacks can inline the small private text renderer.
            for (Method method : owner.getDeclaredMethods()) {
                try { module.deoptimize(method); } catch (Throwable ignored) { }
            }
            try {
                Class<?> listener = Class.forName(owner.getName() + "$a", false, loader);
                for (Method method : listener.getDeclaredMethods()) {
                    try { module.deoptimize(method); } catch (Throwable ignored) { }
                }
            } catch (ClassNotFoundException ignored) { }
            int count = 0;
            for (Method method : scoreMethods(owner)) {
                count += SecurityCenterHookSupport.hook(module, method, "privacy-score:" + method.toGenericString(),
                        chain -> {
                            if (method.getName().equals("setNumber")) return interceptNumber(chain);
                            boolean enabled = SecurityCenterConfigRuntime.current()
                                    .isEnabled(SecurityCenterFeature.SCORE_666);
                            return method.getName().equals("setScore") ? interceptScore(chain, enabled)
                                    : interceptRender(chain, enabled);
                        });
            }
            return count;
        } catch (ReflectiveOperationException | LinkageError error) {
            module.log(Log.WARN, FusionModule.TAG, "security score display unavailable", error);
            return 0;
        }
    }

    private static Context argumentContext(XposedInterface.Chain chain) {
        return chain.getArgs().get(0) instanceof Context source ? source : null;
    }

    private static int contextual(XposedModule module, ClassLoader loader, SecurityCenterFeature feature,
            String owner, String name, Class<?> result, XposedInterface.Hooker enabled) {
        try {
            Method method = Class.forName(owner, false, loader).getDeclaredMethod(name, Context.class);
            if (method.getReturnType() != result) throw new NoSuchMethodException("Unexpected return type");
            return SecurityCenterHookSupport.hook(module, method,
                    "privacy:" + method.toGenericString(), chain ->
                            SecurityCenterConfigRuntime.current().isEnabled(feature)
                                    ? enabled.intercept(chain) : chain.proceed());
        } catch (ReflectiveOperationException | LinkageError error) {
            module.log(Log.WARN, FusionModule.TAG, "security privacy method unavailable: "
                    + owner + "." + name, error);
            return 0;
        }
    }

    static ArrayList<Method> scoreMethods(Class<?> owner) {
        ArrayList<Method> methods = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() != void.class || parameters.length != 1) continue;
            if (((method.getName().equals("setScore") || method.getName().equals("setNumber"))
                    && parameters[0] == int.class)
                    || (method.getName().equals("d") && parameters[0] == CharSequence.class))
                methods.add(method);
        }
        return methods;
    }

    static Object interceptScore(XposedInterface.Chain chain, boolean enabled) throws Throwable {
        TextView view = chain.getThisObject() instanceof TextView text ? text : null;
        if (view != null) synchronized (SCORE_TEXT) {
            if (!SCORE_TEXT.containsKey(view)) SCORE_TEXT.put(view, view.getText().toString());
        }
        Object result = chain.proceed();
        if (view != null) applyScoreText(view, enabled);
        return result;
    }

    static Object interceptNumber(XposedInterface.Chain chain) throws Throwable {
        if (chain.getThisObject() instanceof TextView view) {
            String nativeText;
            synchronized (SCORE_TEXT) { nativeText = SCORE_TEXT.remove(view); }
            if (nativeText != null && !TextUtils.equals(view.getText(), nativeText)) view.setText(nativeText);
        }
        return chain.proceed();
    }

    static Object interceptRender(XposedInterface.Chain chain, boolean enabled) throws Throwable {
        if (!(chain.getThisObject() instanceof TextView view)) return chain.proceed();
        Object original = chain.getArgs().get(0);
        boolean tracked;
        synchronized (SCORE_TEXT) {
            tracked = SCORE_TEXT.containsKey(view);
            if (tracked) SCORE_TEXT.put(view, original == null ? "" : original.toString());
        }
        if (!tracked || !enabled) return chain.proceed();
        Object[] args = chain.getArgs().toArray();
        args[0] = "666";
        return chain.proceed(args);
    }

    static void onConfigChanged() {
        Runnable changed = () -> refreshScores(SecurityCenterConfigRuntime.current()
                .isEnabled(SecurityCenterFeature.SCORE_666));
        if (Looper.myLooper() == Looper.getMainLooper()) changed.run();
        else new Handler(Looper.getMainLooper()).post(changed);
    }

    static void refreshScores(boolean enabled) {
        ArrayList<TextView> views;
        synchronized (SCORE_TEXT) { views = new ArrayList<>(SCORE_TEXT.keySet()); }
        for (TextView view : views) if (view != null) applyScoreText(view, enabled);
    }

    private static void applyScoreText(TextView view, boolean enabled) {
        String nativeText;
        synchronized (SCORE_TEXT) { nativeText = SCORE_TEXT.get(view); }
        if (nativeText == null) return;
        String display = enabled ? "666" : nativeText;
        if (!TextUtils.equals(view.getText(), display)) view.setText(display);
    }

    static boolean motionSensorsAvailable(Context context) {
        return sensorsAvailable(context, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GYROSCOPE);
    }

    static boolean crashSensorAvailable(Context context) { return sensorsAvailable(context, 33171623); }

    private static boolean sensorsAvailable(Context context, int... types) {
        SensorManager manager = context == null ? null : context.getSystemService(SensorManager.class);
        if (manager == null) return false;
        for (int type : types) if (manager.getDefaultSensor(type) == null) return false;
        return true;
    }

    static boolean motionEnabled(Context context) {
        return context != null && Settings.System.getInt(context.getContentResolver(),
                "settings_car_sickness_mode", 0) == 1;
    }

    static int reminderTime(Context context) {
        int value = context == null ? 1 : Settings.System.getInt(context.getContentResolver(),
                "motion_sickness_relief_remind_time", 1);
        return value == 0 ? 0 : 1;
    }
}
