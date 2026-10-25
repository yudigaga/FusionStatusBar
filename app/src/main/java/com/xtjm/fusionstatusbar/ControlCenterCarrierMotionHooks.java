package com.xtjm.fusionstatusbar;

import android.view.View;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Retimes the native grid carrier while preserving its targets and completion callbacks. */
final class ControlCenterCarrierMotionHooks {
    static final String HOLDER_CLASS =
            "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder";
    private static final String CONFIG_CLASS = "miuix.animation.base.AnimConfig";
    private static final String EASE_CLASS = "miuix.animation.utils.EaseManager$EaseStyle";
    private static final String EASE_MANAGER_CLASS = "miuix.animation.utils.EaseManager";
    // Setter methods can be shared by plugin class loaders. Their first installed hook
    // must consume the active holder's context rather than its installation's context.
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static int reported;

    static final class Result {
        final int installed;
        final List<String> unavailable;

        Result(int installed, List<String> unavailable) {
            this.installed = installed;
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
        }
    }

    private ControlCenterCarrierMotionHooks() { }

    static Result install(ClassLoader loader,
            ControlCenterPluginHookInstaller.ClassResolver resolver,
            ControlCenterPluginHookInstaller.Registrar registrar,
            Supplier<ControlCenterConfig> settings) {
        ArrayList<String> unavailable = new ArrayList<>();
        if (loader == null) return new Result(0, unavailable);
        Class<?> holder;
        Class<?> config;
        Class<?> ease;
        Method factory;
        Field itemView;
        Method setEase;
        Method setDelay;
        try {
            holder = resolve(resolver, loader, HOLDER_CLASS);
            config = resolve(resolver, loader, CONFIG_CLASS);
            ease = resolve(resolver, loader, EASE_CLASS);
            Class<?> manager = resolve(resolver, loader, EASE_MANAGER_CLASS);
            factory = method(manager, "getStyle", int.class, float[].class);
            if (!Modifier.isStatic(factory.getModifiers())
                    || !ease.isAssignableFrom(factory.getReturnType())) {
                throw new NoSuchMethodException(EASE_MANAGER_CLASS + "#getStyle");
            }
            itemView = field(holder, "itemView");
            if (!View.class.isAssignableFrom(itemView.getType())) {
                throw new NoSuchFieldException(HOLDER_CLASS + "#itemView");
            }
            setEase = method(config, "setEase", ease);
            setDelay = method(config, "setDelay", long.class);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            unavailable.add("ControlCenterCarrierMotion#prepare:" + failure.getMessage());
            return new Result(0, unavailable);
        }

        MirrorState mirror = mirrorState(holder);
        if (mirror == null) unavailable.add("ControlCenterCarrierMotion#mirrorState");
        Scope scope = new Scope(itemView, mirror, config, factory, ease, settings);
        int installed = 0;
        boolean settersReady = install(registrar, "setEase", setEase,
                chain -> ease(chain, 0), unavailable);
        if (settersReady) installed++;
        boolean delayReady = install(registrar, "setDelay", setDelay,
                ControlCenterCarrierMotionHooks::delay, unavailable);
        if (delayReady) installed++;
        settersReady &= delayReady;

        // Native property-specific profiles can override the main config's ease.
        // Hook each existing overload, including hosts that store the ease directly.
        Set<Method> specials = specialMethods(config, ease);
        for (Method special : specials) {
            boolean ready = install(registrar, "setSpecial:" + special.getParameterTypes()[0].getName(),
                    special, chain -> ease(chain, 1), unavailable);
            if (ready) installed++;
            settersReady &= ready;
        }
        if (!settersReady) return new Result(installed, unavailable);

        installed += installBoundary(holder, registrar, "changeVisible",
                new Class<?>[] {boolean.class, boolean.class, boolean.class},
                chain -> scope.holder(chain, true), unavailable);
        installed += installBoundary(holder, registrar, "changeExpand",
                new Class<?>[] {float.class, float.class, float.class, boolean.class, boolean.class},
                chain -> scope.holder(chain, false), unavailable);
        return new Result(installed, unavailable);
    }

    private static final class Scope {
        private final Field itemView;
        private final MirrorState mirror;
        private final Context opening;
        private final Context closing;
        private final Supplier<ControlCenterConfig> settings;

        Scope(Field itemView, MirrorState mirror, Class<?> configType, Method factory, Class<?> easeType,
                Supplier<ControlCenterConfig> settings) {
            this.itemView = itemView;
            this.mirror = mirror;
            opening = new Context(ControlCenterAnimationHooks.OPEN_RESPONSE_SECONDS,
                    configType, easeType, factory);
            closing = new Context(ControlCenterAnimationHooks.CLOSE_RESPONSE_SECONDS,
                    configType, easeType, factory);
            this.settings = settings;
        }

        Object holder(XposedInterface.Chain chain, boolean visibility) throws Throwable {
            Context active = null;
            try {
                Object owner = chain.getThisObject();
                List<?> args = chain.getArgs();
                if (owner != null && itemView.get(owner) instanceof View view
                        && ControlCenterMaterials.isGridCarrier(view)
                        && ControlCenterAnimationHooks.enabled(settings.get())) {
                    if (visibility && Boolean.TRUE.equals(args.get(1))
                            && Boolean.FALSE.equals(args.get(2))) {
                        active = Boolean.TRUE.equals(args.get(0)) ? opening : closing;
                    } else if (!visibility && Boolean.TRUE.equals(args.get(4))
                            && Boolean.TRUE.equals(args.get(3))
                            && mirror != null && Boolean.FALSE.equals(mirror.read(owner))) {
                        active = args.get(0) instanceof Number target && target.floatValue() <= 0f
                                ? closing : opening;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // An unknown holder state retains the complete native animation.
            }
            Context previous = ACTIVE.get();
            setContext(active);
            try {
                return chain.proceed();
            } finally {
                setContext(previous);
            }
        }
    }

    private static final class Context {
        final float response;
        final Class<?> configType;
        final Class<?> easeType;
        final Method factory;

        Context(float response, Class<?> configType, Class<?> easeType, Method factory) {
            this.response = response;
            this.configType = configType;
            this.easeType = easeType;
            this.factory = factory;
        }
    }

    private static Object ease(XposedInterface.Chain chain, int argument) throws Throwable {
        Object[] replacement = null;
        Context active = ACTIVE.get();
        if (active != null && active.configType.isInstance(chain.getThisObject())) {
            try {
                Object original = chain.getArgs().get(argument);
                if (active.easeType.isInstance(original)) {
                    float[] factors = ControlCenterAnimationHooks.fastFactors(original, active.response);
                    if (factors != null) {
                        Object faster = active.factory.invoke(null, -2, factors);
                        if (active.easeType.isInstance(faster)) {
                            replacement = chain.getArgs().toArray();
                            replacement[argument] = faster;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Preparation failed; invoke the original setter once with its original ease.
            }
        }
        if (replacement == null) return chain.proceed();
        Object result = chain.proceed(replacement);
        int flag = (argument == 0 ? 1 : 4)
                << (active.response == ControlCenterAnimationHooks.CLOSE_RESPONSE_SECONDS ? 1 : 0);
        reportOnce(flag, "control-center carrier spring applied kind="
                + (argument == 0 ? "main" : "special") + " responseCap=" + active.response);
        return result;
    }

    private static Object delay(XposedInterface.Chain chain) throws Throwable {
        Context active = ACTIVE.get();
        if (active != null && active.configType.isInstance(chain.getThisObject())
                && chain.getArgs().get(0) instanceof Number value && value.longValue() != 0L) {
            Object[] replacement = chain.getArgs().toArray();
            replacement[0] = 0L;
            Object result = chain.proceed(replacement);
            reportOnce(16, "control-center carrier stagger removed nativeDelay=" + value.longValue() + "ms");
            return result;
        }
        return chain.proceed();
    }

    private static void setContext(Context value) {
        if (value == null) ACTIVE.remove();
        else ACTIVE.set(value);
    }

    private static void reportOnce(int flag, String message) {
        synchronized (ControlCenterCarrierMotionHooks.class) {
            if ((reported & flag) != 0) return;
            reported |= flag;
        }
        try { SystemUiHooks.runtimeGridLog(Log.INFO, message, null); }
        catch (RuntimeException | LinkageError ignored) { /* Diagnostics must not change delivery. */ }
    }

    private interface MirrorState {
        Object read(Object owner) throws ReflectiveOperationException;
    }

    private static MirrorState mirrorState(Class<?> holder) {
        try {
            Field field = field(holder, "mirrorShowing");
            if (field.getType() == boolean.class || field.getType() == Boolean.class) return field::get;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Some host variants expose the same state through a getter.
        }
        for (String name : new String[] {"getMirrorShowing", "isMirrorShowing"}) {
            try {
                Method getter = method(holder, name);
                if (!Modifier.isStatic(getter.getModifiers())
                        && (getter.getReturnType() == boolean.class
                        || getter.getReturnType() == Boolean.class)) return getter::invoke;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Unknown mirror state is never treated as a normal panel.
            }
        }
        return null;
    }

    private static Class<?> resolve(ControlCenterPluginHookInstaller.ClassResolver resolver,
            ClassLoader loader, String name) throws ClassNotFoundException {
        Class<?> result = resolver.resolve(name, loader);
        if (result == null) throw new ClassNotFoundException(name);
        return result;
    }

    private static Set<Method> specialMethods(Class<?> config, Class<?> ease) {
        Set<Method> result = new LinkedHashSet<>();
        for (Class<?> current = config; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getName().equals("setSpecial") && parameters.length == 3
                        && parameters[1] == ease && parameters[2] == float[].class
                        && !Modifier.isStatic(candidate.getModifiers())
                        && !candidate.isBridge() && !candidate.isSynthetic()) result.add(candidate);
            }
        }
        return result;
    }

    private static int installBoundary(Class<?> holder,
            ControlCenterPluginHookInstaller.Registrar registrar, String name, Class<?>[] parameters,
            XposedInterface.Hooker hook, List<String> unavailable) {
        try {
            Method target = method(holder, name, parameters);
            if (target.getReturnType() == void.class
                    && !Modifier.isStatic(target.getModifiers())
                    && install(registrar, name, target, hook, unavailable)) return 1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A different host holder contract keeps its original timings.
        }
        String id = "ControlCenterCarrierMotion#" + name;
        if (!unavailable.contains(id)) unavailable.add(id);
        return 0;
    }

    private static boolean install(ControlCenterPluginHookInstaller.Registrar registrar,
            String name, Method method, XposedInterface.Hooker hook, List<String> unavailable) {
        String id = "ControlCenterCarrierMotion#" + name;
        try {
            method.setAccessible(true);
            if (registrar.install(id, method, hook)) return true;
        } catch (RuntimeException ignored) {
            // Do not activate holder scopes after a partial setter installation.
        }
        unavailable.add(id);
        return false;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name, parameters);
                result.setAccessible(true);
                return result;
            } catch (NoSuchMethodException ignored) {
                // Search inherited host contracts once during installation.
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                if (Modifier.isStatic(result.getModifiers())) throw new NoSuchFieldException(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {
                // RecyclerView owns itemView; OEM holder subclasses own mirrorShowing.
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }
}
