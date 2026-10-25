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

/** Bounds custom panel spring settling while retaining native targets and completion callbacks. */
final class ControlCenterAnimationHooks {
    static final String EXPANSION_CLASS =
            "miui.systemui.controlcenter.windowview.ControlCenterExpandController$expansionAnimator$1";
    static final String BLUR_CLASS =
            "miui.systemui.controlcenter.windowview.BlurController";
    static final float OPEN_RESPONSE_SECONDS = 0.30f;
    static final float CLOSE_RESPONSE_SECONDS = 0.20f;
    private static volatile PlanGate lastPlan;

    private record PlanGate(String encoded, boolean usable) { }

    interface Proceed { Object run() throws Throwable; }

    static final class Result {
        final int installed;
        final List<String> unavailable;
        final int carrierInstalled;
        final List<String> carrierUnavailable;

        Result(int installed, List<String> unavailable) {
            this(installed, unavailable, 0, List.of());
        }

        Result(int installed, List<String> unavailable, int carrierInstalled,
                List<String> carrierUnavailable) {
            this.installed = installed + carrierInstalled;
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
            this.carrierInstalled = carrierInstalled;
            this.carrierUnavailable = Collections.unmodifiableList(new ArrayList<>(carrierUnavailable));
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
                    if (!Boolean.TRUE.equals(args.get(2)) || !enabled(settings.get())) return chain.proceed();
                    float target = ((Number) args.get(1)).floatValue();
                    // Intermediate progress belongs to the native tracking spring.
                    // Only completed gesture targets need the faster settling profile.
                    if (target != 0f && target != 1f || expansionMirror(owner)) return chain.proceed();
                    return withFastConfig(owner, target <= 0f ? CLOSE_RESPONSE_SECONDS
                            : OPEN_RESPONSE_SECONDS, chain::proceed);
                }, unavailable);
        installed += install(loader, resolver, registrar, BLUR_CLASS,
                "showBlur", new Class<?>[] {boolean.class, boolean.class, boolean.class,
                        boolean.class}, chain -> {
                    List<?> args = chain.getArgs();
                    if (!Boolean.TRUE.equals(args.get(1))
                            || !Boolean.TRUE.equals(args.get(2))
                            || Boolean.TRUE.equals(args.get(3))
                            || !enabled(settings.get())) return chain.proceed();
                    return withFastConfig(chain.getThisObject(), Boolean.TRUE.equals(args.get(0))
                            ? OPEN_RESPONSE_SECONDS : CLOSE_RESPONSE_SECONDS, chain::proceed);
                }, unavailable);
        ControlCenterCarrierMotionHooks.Result carrier = ControlCenterCarrierMotionHooks.install(
                loader, resolver, registrar, settings);
        return new Result(installed, unavailable, carrier.installed, carrier.unavailable);
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
        PlanGate cached = lastPlan;
        if (cached != null && cached.encoded.equals(settings.layoutPlan)) return cached.usable;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        boolean usable = plan != null && (!plan.regular.items.isEmpty() || !plan.compact.items.isEmpty());
        // Gesture progress can arrive every frame; the encoded plan is immutable.
        lastPlan = new PlanGate(settings.layoutPlan, usable);
        return usable;
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
        return withFastConfig(owner, OPEN_RESPONSE_SECONDS, proceed);
    }

    private static Object withFastConfig(Object owner, float response, Proceed proceed) throws Throwable {
        if (owner == null) return proceed.run();
        Field configField;
        Object original;
        Object replacement;
        try {
            configField = field(owner.getClass(), "animConfig");
            original = configField.get(owner);
            replacement = original == null ? null : fastConfig(original, response);
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

    private static Object fastConfig(Object original, float response) throws ReflectiveOperationException {
        Class<?> type = original.getClass();
        Object ease = field(type, "ease").get(original);
        if (ease == null) ease = type.getField("sDefEase").get(null);
        float[] factors = fastFactors(ease, response);
        if (factors == null) return original;
        Constructor<?> copy = type.getConstructor(type);
        Object replacement = copy.newInstance(original);
        // FolmeState.to posts its arguments. Give it a stable copy, retaining native listeners.
        type.getMethod("setEase", int.class, float[].class).invoke(replacement, -2, factors);
        return replacement;
    }

    static float[] fastFactors(Object ease, float response) throws ReflectiveOperationException {
        if (ease == null || field(ease.getClass(), "style").getInt(ease) != -2) return null;
        Object values = field(ease.getClass(), "factors").get(ease);
        float damping, nativeResponse;
        int length;
        if (values instanceof double[] doubles) {
            if (doubles.length < 2) return null;
            length = doubles.length;
            damping = (float) doubles[0]; nativeResponse = (float) doubles[1];
        } else if (values instanceof float[] floats) {
            if (floats.length < 2) return null;
            length = floats.length;
            damping = floats[0]; nativeResponse = floats[1];
        } else return null;
        if (!Float.isFinite(damping) || damping <= 0f || !Float.isFinite(nativeResponse)
                || nativeResponse <= 0f || nativeResponse < response
                || nativeResponse == response && damping >= 0.9f) return null;
        float[] factors = new float[length];
        if (values instanceof double[] doubles) {
            for (int i = 0; i < length; i++) factors[i] = (float) doubles[i];
        } else System.arraycopy(values, 0, factors, 0, length);
        factors[0] = Math.max(0.9f, damping);
        factors[1] = Math.min(response, nativeResponse);
        return factors;
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
