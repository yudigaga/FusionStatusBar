package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uses the notification shade alpha easing for the control-center page switch only. */
final class ShadeSwitchAnimationHooks {
    static final String SWITCH_LISTENER_CLASS =
            "com.miui.systemui.shade.ShadeSwitchControllerImpl$notification$1";
    static final String SWITCH_CONTROLLER_CLASS =
            "com.miui.systemui.shade.ShadeSwitchControllerImpl";
    static final String ANIM_CONFIG_CLASS = "miuix.animation.base.AnimConfig";

    interface ClassResolver {
        Class<?> resolve(String name, ClassLoader loader);
    }

    interface Registrar {
        boolean install(String id, Method method, XposedInterface.Hooker hook);
    }

    static final class Result {
        final int installed;
        final List<String> unavailable;

        Result(int installed, List<String> unavailable) {
            this.installed = installed;
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
        }
    }

    private static final class Override {
        final Object config;
        final Object ease;

        Override(Object config, Object ease) {
            this.config = config;
            this.ease = ease;
        }
    }

    private static final ThreadLocal<Override> ACTIVE_OVERRIDE = new ThreadLocal<>();

    private ShadeSwitchAnimationHooks() { }

    static Result install(ClassLoader loader, ClassResolver resolver, Registrar registrar) {
        ArrayList<String> unavailable = new ArrayList<>();
        if (loader == null) return new Result(0, unavailable);
        Class<?> listener = resolve(SWITCH_LISTENER_CLASS, loader, resolver, unavailable);
        Class<?> controller = resolve(SWITCH_CONTROLLER_CLASS, loader, resolver, unavailable);
        Class<?> animationConfig = resolve(ANIM_CONFIG_CLASS, loader, resolver, unavailable);
        if (listener == null || controller == null || animationConfig == null) {
            return new Result(0, unavailable);
        }

        try {
            Method setTriggerAppeared = listener.getDeclaredMethod(
                    "setTriggerAppeared", boolean.class, boolean.class);
            setTriggerAppeared.setAccessible(true);
            Object showEase = staticField(controller, "ALPHA_SHOW_EASE").get(null);
            Object hideEase = staticField(controller, "ALPHA_HIDE_EASE").get(null);
            if (showEase == null || hideEase == null) {
                unavailable.add(SWITCH_CONTROLLER_CLASS + "#ALPHA_SHOW_EASE/ALPHA_HIDE_EASE");
                return new Result(0, unavailable);
            }

            Method setEase = easeSetter(animationConfig, showEase.getClass());
            if (setEase == null) {
                unavailable.add(ANIM_CONFIG_CLASS + "#setEase(EaseStyle)");
                return new Result(0, unavailable);
            }
            setEase.setAccessible(true);
            int installed = 0;
            if (registrar.install("ShadeSwitchAnimation#setEase", setEase,
                    ShadeSwitchAnimationHooks::replaceControlCenterAlphaEase)) {
                installed++;
            } else {
                unavailable.add(ANIM_CONFIG_CLASS + "#setEase(EaseStyle)");
            }
            if (registrar.install("ShadeSwitchAnimation#setTriggerAppeared", setTriggerAppeared,
                    chain -> {
                        Object target = chain.getThisObject();
                        List<?> args = chain.getArgs();
                        if (!Boolean.TRUE.equals(args.get(1)) || switchId(target) != 1) {
                            return chain.proceed();
                        }
                        Object config;
                        try {
                            config = field(target.getClass(), "alphaConfig").get(target);
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            return chain.proceed();
                        }
                        Object ease = Boolean.TRUE.equals(args.get(0)) ? showEase : hideEase;
                        if (config == null || ease == null) return chain.proceed();
                        Override previous = ACTIVE_OVERRIDE.get();
                        ACTIVE_OVERRIDE.set(new Override(config, ease));
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) ACTIVE_OVERRIDE.remove();
                            else ACTIVE_OVERRIDE.set(previous);
                        }
                    })) {
                installed++;
            } else {
                unavailable.add(SWITCH_LISTENER_CLASS + "#setTriggerAppeared");
            }
            return new Result(installed, unavailable);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            unavailable.add(SWITCH_LISTENER_CLASS + "#setTriggerAppeared");
            return new Result(0, unavailable);
        }
    }

    private static Object replaceControlCenterAlphaEase(XposedInterface.Chain chain)
            throws Throwable {
        Override current = ACTIVE_OVERRIDE.get();
        if (current == null || current.config != chain.getThisObject()) return chain.proceed();
        Object[] args = chain.getArgs().toArray();
        args[0] = current.ease;
        return chain.proceed(args);
    }

    private static Class<?> resolve(String name, ClassLoader loader, ClassResolver resolver,
            List<String> unavailable) {
        Class<?> type = resolver.resolve(name, loader);
        if (type == null) unavailable.add(name);
        return type;
    }

    private static int switchId(Object listener) {
        try {
            return field(listener.getClass(), "$r8$classId").getInt(listener);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static Field staticField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = field(type, name);
        if (!Modifier.isStatic(field.getModifiers())) throw new NoSuchFieldException(name);
        return field;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // OEM versions may move these fields to a superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method easeSetter(Class<?> config, Class<?> easeType) {
        for (Method method : config.getDeclaredMethods()) {
            if (method.getName().equals("setEase") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(easeType)) {
                return method;
            }
        }
        return null;
    }
}
