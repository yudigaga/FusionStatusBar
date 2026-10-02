package com.xtjm.fusionstatusbar;

import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves and installs the independent SystemUI status-bar hook groups. */
final class StatusBarHookInstaller {
    static final String BATTERY_CLASS =
            "com.android.systemui.statusbar.views.MiuiBatteryMeterView";
    static final String CONTAINER_CLASS =
            "com.android.systemui.statusbar.views.MiuiStatusIconContainer";
    static final String BATTERY_CONTAINER_CLASS =
            "com.android.systemui.statusbar.views.MiuiStatusBatteryContainer";
    static final String PHONE_CLASS =
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    static final String PHONE_BASE_CLASS =
            "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    static final String KEYGUARD_CLASS =
            "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView";

    enum Callback {
        BATTERY_MEASURE,
        BATTERY_LAYOUT,
        BATTERY_FINISH_INFLATE,
        BATTERY_ATTACHED,
        AFTER_UPDATE,
        AFTER_TINT,
        INTERCEPT_CHILD_VISIBILITY,
        CONTAINER_MEASURE,
        CONTAINER_LAYOUT,
        AFTER_SET_IGNORED,
        AFTER_VIEW_ADDED,
        BATTERY_CONTAINER_MEASURE,
        BATTERY_CONTAINER_LAYOUT,
        PHONE_FINISH_INFLATE,
        PHONE_ATTACHED,
        PHONE_LAYOUT,
        PHONE_CUTOUT_UPDATE,
        PHONE_MEASURE,
        KEYGUARD_ATTACHED,
        KEYGUARD_LAYOUT
    }

    interface ClassResolver {
        Class<?> resolve(String name, ClassLoader targetLoader);
    }

    /** Returns the number of newly retained handles (zero for a miss or an existing handle). */
    interface Registrar {
        int install(String id, Method method, XposedInterface.Hooker hooker);
    }

    interface Hookers {
        XposedInterface.Hooker get(Callback callback);
    }

    static final class Targets {
        final Class<?> battery;
        final Class<?> container;
        final Class<?> batteryContainer;
        final Class<?> phone;
        final Class<?> phoneBase;
        final Class<?> keyguard;
        final List<String> missingClasses;

        Targets(Class<?> battery, Class<?> container, Class<?> batteryContainer,
                Class<?> phone, Class<?> phoneBase, Class<?> keyguard,
                List<String> missingClasses) {
            this.battery = battery;
            this.container = container;
            this.batteryContainer = batteryContainer;
            this.phone = phone;
            this.phoneBase = phoneBase;
            this.keyguard = keyguard;
            this.missingClasses = Collections.unmodifiableList(new ArrayList<>(missingClasses));
        }
    }

    static final class Result {
        final Class<?> batteryClass;
        final Class<?> batteryContainerClass;
        final int batteryHooks;
        final int containerHooks;
        final int batteryContainerHooks;
        final int layoutHooks;
        final List<String> missingClasses;
        final List<String> missingMethods;

        Result(Class<?> batteryClass, Class<?> batteryContainerClass,
                int batteryHooks, int containerHooks, int batteryContainerHooks, int layoutHooks,
                List<String> missingClasses, List<String> missingMethods) {
            this.batteryClass = batteryClass;
            this.batteryContainerClass = batteryContainerClass;
            this.batteryHooks = batteryHooks;
            this.containerHooks = containerHooks;
            this.batteryContainerHooks = batteryContainerHooks;
            this.layoutHooks = layoutHooks;
            this.missingClasses = Collections.unmodifiableList(new ArrayList<>(missingClasses));
            this.missingMethods = Collections.unmodifiableList(new ArrayList<>(missingMethods));
        }
    }

    private final ClassResolver classResolver;
    private final Registrar registrar;
    private final Hookers hookers;
    private final List<String> missingClasses = new ArrayList<>();
    private final List<String> missingMethods = new ArrayList<>();

    StatusBarHookInstaller(ClassResolver classResolver, Registrar registrar, Hookers hookers) {
        if (classResolver == null || registrar == null || hookers == null)
            throw new IllegalArgumentException("missing_status_bar_installer_dependency");
        this.classResolver = classResolver;
        this.registrar = registrar;
        this.hookers = hookers;
    }

    Targets resolve(ClassLoader targetLoader) {
        Class<?> battery = resolve(BATTERY_CLASS, targetLoader);
        Class<?> container = resolve(CONTAINER_CLASS, targetLoader);
        Class<?> batteryContainer = resolve(BATTERY_CONTAINER_CLASS, targetLoader);
        Class<?> phone = resolve(PHONE_CLASS, targetLoader);
        Class<?> phoneBase = resolve(PHONE_BASE_CLASS, targetLoader);
        Class<?> keyguard = resolve(KEYGUARD_CLASS, targetLoader);
        return new Targets(battery, container, batteryContainer, phone, phoneBase, keyguard,
                missingClasses);
    }

    Result install(Targets targets) {
        if (targets == null) throw new IllegalArgumentException("missing_status_bar_targets");
        int batteryHooks = targets.battery == null ? 0 : installBattery(targets.battery);
        int containerHooks = targets.container == null ? 0 : installContainer(targets.container);
        int batteryContainerHooks = targets.batteryContainer == null
                ? 0 : installBatteryContainer(targets.batteryContainer);
        int layoutHooks = (targets.phone == null ? 0 : installPhone(targets.phone));
        if (targets.phoneBase != null) {
            layoutHooks += exact(targets.phoneBase, "onMeasure", new Class<?>[] {int.class, int.class},
                    Callback.PHONE_MEASURE);
        }
        if (targets.keyguard != null) layoutHooks += installKeyguard(targets.keyguard);

        return new Result(targets.battery, targets.batteryContainer, batteryHooks, containerHooks,
                batteryContainerHooks, layoutHooks, targets.missingClasses, missingMethods);
    }

    private Class<?> resolve(String name, ClassLoader targetLoader) {
        Class<?> type = classResolver.resolve(name, targetLoader);
        if (type == null) missingClasses.add(name);
        return type;
    }

    private int installBattery(Class<?> type) {
        int count = 0;
        count += exact(type, "onMeasure", new Class<?>[] {int.class, int.class},
                Callback.BATTERY_MEASURE);
        count += exact(type, "onLayout",
                new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class},
                Callback.BATTERY_LAYOUT);
        count += exact(type, "onFinishInflate", new Class<?>[0], Callback.BATTERY_FINISH_INFLATE);
        count += exact(type, "onAttachedToWindow", new Class<?>[0], Callback.BATTERY_ATTACHED);
        count += exact(type, "updateAll", new Class<?>[0], Callback.AFTER_UPDATE);
        count += exact(type, "updateChargeAndText", new Class<?>[0], Callback.AFTER_UPDATE);
        count += exact(type, "onBatteryStyleChanged", new Class<?>[] {int.class}, Callback.AFTER_UPDATE);
        count += exact(type, "onDarkChanged", new Class<?>[] {ArrayList.class, float.class, int.class},
                Callback.AFTER_TINT);
        count += exact(type, "onLightDarkTintChanged",
                new Class<?>[] {int.class, int.class, boolean.class}, Callback.AFTER_TINT);
        for (String name : new String[] {"showView", "hideView", "animateShowView", "animateHideView"}) {
            count += matchingViewMethods(type, name, Callback.INTERCEPT_CHILD_VISIBILITY);
        }
        return count;
    }

    private int installContainer(Class<?> type) {
        int count = 0;
        count += exact(type, "onMeasure", new Class<?>[] {int.class, int.class},
                Callback.CONTAINER_MEASURE);
        count += exact(type, "onLayout",
                new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class},
                Callback.CONTAINER_LAYOUT);
        count += exact(type, "setIgnoredSlots", new Class<?>[] {List.class}, Callback.AFTER_SET_IGNORED);
        count += exact(type, "onViewAdded", new Class<?>[] {View.class}, Callback.AFTER_VIEW_ADDED);
        return count;
    }

    private int installBatteryContainer(Class<?> type) {
        int count = 0;
        count += exact(type, "onMeasure", new Class<?>[] {int.class, int.class},
                Callback.BATTERY_CONTAINER_MEASURE);
        count += exact(type, "onLayout",
                new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class},
                Callback.BATTERY_CONTAINER_LAYOUT);
        return count;
    }

    private int installPhone(Class<?> type) {
        int count = 0;
        count += exact(type, "onFinishInflate", new Class<?>[0], Callback.PHONE_FINISH_INFLATE);
        count += exact(type, "onAttachedToWindow", new Class<?>[0], Callback.PHONE_ATTACHED);
        count += exact(type, "onLayout",
                new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class},
                Callback.PHONE_LAYOUT);
        count += exact(type, "updateCutoutLocation", new Class<?>[0], Callback.PHONE_CUTOUT_UPDATE);
        return count;
    }

    private int installKeyguard(Class<?> type) {
        int count = 0;
        count += exact(type, "miuiOnAttachedToWindow", new Class<?>[0], Callback.KEYGUARD_ATTACHED);
        count += exact(type, "onLayout",
                new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class},
                Callback.KEYGUARD_LAYOUT);
        return count;
    }

    private int exact(Class<?> type, String methodName, Class<?>[] parameters, Callback callback) {
        Method method = RuntimeReflection.exact(type, methodName, parameters, true);
        if (method == null) {
            missingMethods.add(type.getSimpleName() + "#" + methodName);
            return 0;
        }
        return install(type, methodName, method, callback);
    }

    private int matchingViewMethods(Class<?> type, String methodName, Callback callback) {
        List<Method> matches = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())
                    && parameters.length == 3 && parameters[1] == View.class
                    && parameters[2] == String.class) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        if (matches.isEmpty()) {
            missingMethods.add(type.getSimpleName() + "#" + methodName);
            return 0;
        }
        int count = 0;
        for (Method method : matches) count += install(type, methodName, method, callback);
        return count;
    }

    private int install(Class<?> type, String methodName, Method method, Callback callback) {
        return registrar.install(type.getSimpleName() + "#" + methodName,
                method, hookers.get(callback));
    }
}
