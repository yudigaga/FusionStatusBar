package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Bounds custom panel spring settling without changing native gestures or transforms. */
final class ControlCenterAnimationHooks {
    static final String EXPANSION_CLASS =
            "miui.systemui.controlcenter.windowview.ControlCenterExpandController$expansionAnimator$1";
    static final String BLUR_CLASS =
            "miui.systemui.controlcenter.windowview.BlurController";
    private static final float MAX_RESPONSE_SECONDS = 0.35f;

    interface Proceed { Object run() throws Throwable; }

    static final class Result {
        final int installed;
        final List<String> unavailable;

        Result(int installed, List<String> unavailable) {
            this.installed = installed;
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
        }
    }

    private ControlCenterAnimationHooks() { }

    static Result install(ClassLoader loader,
            ControlCenterPluginHookInstaller.ClassResolver resolver,
            ControlCenterPluginHookInstaller.Registrar registrar,
            Supplier<ControlCenterConfig> settings) {
        ArrayList<String> unavailable = new ArrayList<>();
        if (loader == null) return new Result(0, unavailable);
        int installed = install(loader, resolver, registrar, EXPANSION_CLASS,
                "setExpandProgress", new Class<?>[] {boolean.class, float.class, boolean.class},
                chain -> {
                    List<?> args = chain.getArgs();
                    Object owner = chain.getThisObject();
                    if (!Boolean.TRUE.equals(args.get(2)) || !enabled(settings.get())
                            || expansionMirror(owner)) return chain.proceed();
                    return withFastConfig(owner, chain::proceed);
                }, unavailable);
        installed += install(loader, resolver, registrar, BLUR_CLASS,
                "showBlur", new Class<?>[] {boolean.class, boolean.class, boolean.class,
                        boolean.class}, chain -> {
                    List<?> args = chain.getArgs();
                    if (!Boolean.TRUE.equals(args.get(1))
                            || !Boolean.TRUE.equals(args.get(2))
                            || Boolean.TRUE.equals(args.get(3))
                            || !enabled(settings.get())) return chain.proceed();
                    return withFastConfig(chain.getThisObject(), chain::proceed);
                }, unavailable);
        return new Result(installed, unavailable);
    }

    private static int install(ClassLoader loader,
            ControlCenterPluginHookInstaller.ClassResolver resolver,
            ControlCenterPluginHookInstaller.Registrar registrar, String className,
            String methodName, Class<?>[] parameters, XposedInterface.Hooker hook,
            List<String> unavailable) {
        String id = "ControlCenterAnimation#" + methodName;
        try {
            Class<?> type = resolver.resolve(className, loader);
            if (type == null) {
                unavailable.add(className);
                return 0;
            }
            Method method = type.getDeclaredMethod(methodName, parameters);
            if (method.getReturnType() != void.class) {
                unavailable.add(id);
                return 0;
            }
            method.setAccessible(true);
            if (registrar.install(id, method, hook)) return 1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A different OEM animation implementation continues with its native timings.
        }
        unavailable.add(id);
        return 0;
    }

    static boolean enabled(ControlCenterConfig settings) {
        if (settings == null || !settings.enabled || settings.layoutPlan.isEmpty()) return false;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        return plan != null && (!plan.regular.items.isEmpty() || !plan.compact.items.isEmpty());
    }

    private static boolean expansionMirror(Object animator) {
        try {
            Field parent = field(animator.getClass(), "this$0");
            Object owner = parent.get(animator);
            if (owner == null) return true;
            Method mirror = owner.getClass().getMethod("getInMirror");
            return !Boolean.FALSE.equals(mirror.invoke(owner));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    static Object withFastConfig(Object owner, Proceed proceed) throws Throwable {
        if (owner == null) return proceed.run();
        Field configField;
        Object original;
        Object replacement;
        try {
            configField = field(owner.getClass(), "animConfig");
            original = configField.get(owner);
            replacement = original == null ? null : fastConfig(original);
            if (replacement != original) configField.set(owner, replacement);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return proceed.run();
        }
        if (replacement == original) return proceed.run();
        try {
            return proceed.run();
        } finally {
            RuntimeCleanup.run("restore native control-center animation config", () -> {
                if (configField.get(owner) == replacement) configField.set(owner, original);
            });
        }
    }

    private static Object fastConfig(Object original) throws ReflectiveOperationException {
        Class<?> type = original.getClass();
        Object ease = field(type, "ease").get(original);
        if (ease == null) ease = type.getField("sDefEase").get(null);
        if (ease == null || field(ease.getClass(), "style").getInt(ease) != -2) return original;
        Object values = field(ease.getClass(), "factors").get(ease);
        float[] factors;
        if (values instanceof double[] doubles) {
            factors = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) factors[i] = (float) doubles[i];
        } else if (values instanceof float[] floats) {
            factors = floats.clone();
        } else return original;
        if (factors.length < 2 || !Float.isFinite(factors[0]) || factors[0] <= 0f
                || !Float.isFinite(factors[1]) || factors[1] <= MAX_RESPONSE_SECONDS) return original;
        factors[1] = MAX_RESPONSE_SECONDS;
        Constructor<?> copy = type.getConstructor(type);
        Object replacement = copy.newInstance(original);
        // FolmeState.to posts its arguments. Give it a stable copy, retaining native listeners.
        type.getMethod("setEase", int.class, float[].class).invoke(replacement, -2, factors);
        return replacement;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) throw new NoSuchFieldException(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Search inherited native configuration fields.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
