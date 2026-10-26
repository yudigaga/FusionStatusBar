package com.xtjm.fusionstatusbar;

import android.animation.Animator;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Suppresses native ready pulses after the custom-ready cue for the same physical gesture. */
final class LauncherBackReadyFeedbackGuard {
    private static final String TAG = "FusionStatusBar";
    private static final String CALLBACK = LauncherBackAppearance.ARROW + "$1";
    private static final Map<View, Boolean> READY_ARROWS = new WeakHashMap<>();
    private static final Map<Class<?>, Optional<Field>> STUB_ARROWS = new ConcurrentHashMap<>();
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();

    interface ClassResolver { Class<?> resolve(String name, ClassLoader loader) throws Exception; }
    interface Registrar { boolean install(Method method, XposedInterface.Hooker hook); }

    private final Field callbackOwner;
    private final Method setFeedbackDone;

    private LauncherBackReadyFeedbackGuard(Field callbackOwner, Method setFeedbackDone) {
        this.callbackOwner = callbackOwner;
        this.setFeedbackDone = setFeedbackDone;
    }

    static int install(XposedModule module, ClassLoader loader) {
        if (module == null || loader == null) return 0;
        return install(loader, (name, source) -> Class.forName(name, false, source), (method, hook) -> {
            try {
                try { module.deoptimize(method); }
                catch (Throwable ignored) { }
                module.hook(method).setId("fusion-statusbar:launcher-back-ready-feedback")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hook);
                return true;
            } catch (Throwable error) {
                reportFailure(error);
                return false;
            }
        });
    }

    static int install(ClassLoader loader, ClassResolver resolver, Registrar registrar) {
        if (loader == null || resolver == null || registrar == null) return 0;
        try {
            Class<?> arrow = resolver.resolve(LauncherBackAppearance.ARROW, loader);
            Class<?> callback = resolver.resolve(CALLBACK, loader);
            if (arrow == null || callback == null || !View.class.isAssignableFrom(arrow)) return 0;
            Method success = callback.getDeclaredMethod("onAnimationSuccess", Animator.class);
            if (success.getReturnType() != void.class || Modifier.isStatic(success.getModifiers())) {
                return 0;
            }
            Field owner = ownerField(callback, arrow);
            Method setter = feedbackSetter(arrow);
            success.setAccessible(true);
            owner.setAccessible(true);
            setter.setAccessible(true);
            LauncherBackReadyFeedbackGuard guard = new LauncherBackReadyFeedbackGuard(owner, setter);
            return registrar.install(success, guard::animationSuccess) ? 1 : 0;
        } catch (Exception | LinkageError error) {
            reportFailure(error);
            return 0;
        }
    }

    /** Call immediately before requesting this gesture's custom-ready haptic. */
    static void markReady(View stub) {
        View arrow = arrowFor(stub);
        if (arrow == null) return;
        synchronized (READY_ARROWS) { READY_ARROWS.put(arrow, Boolean.TRUE); }
    }

    /**
     * Call at the next ACTION_DOWN. Keep the mark through UP/CANCEL and native cleanup, since
     * an animation-success callback from that completed gesture can still arrive afterwards.
     */
    static void reset(View stub) {
        View arrow = arrowFor(stub);
        if (arrow == null) return;
        synchronized (READY_ARROWS) { READY_ARROWS.remove(arrow); }
    }

    private Object animationSuccess(XposedInterface.Chain chain) throws Throwable {
        try {
            Object owner = callbackOwner.get(chain.getThisObject());
            if (owner instanceof View arrow) {
                boolean marked;
                synchronized (READY_ARROWS) { marked = READY_ARROWS.containsKey(arrow); }
                if (marked) setFeedbackDone.invoke(arrow, true);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            // The setter may already have applied its side effect. Do not retry or replay it.
            reportFailure(error);
        }
        // The supplied listener's own feedback-done guard skips its pulse. Preserve its callback,
        // and keep original failures outside the catch so native work is never replayed.
        return chain.proceed();
    }

    private static View arrowFor(View stub) {
        if (stub == null) return null;
        try {
            Optional<Field> field = STUB_ARROWS.computeIfAbsent(stub.getClass(),
                    LauncherBackReadyFeedbackGuard::stubArrowField);
            Object arrow = field.isPresent() ? field.get().get(stub) : null;
            return arrow instanceof View view ? view : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            reportFailure(error);
            return null;
        }
    }

    private static Optional<Field> stubArrowField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("mGestureBackArrowView");
                if (Modifier.isStatic(field.getModifiers())
                        || !View.class.isAssignableFrom(field.getType())) return Optional.empty();
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                // Home may keep its private arrow on a GestureStubView base class.
            } catch (RuntimeException | LinkageError unavailable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Field ownerField(Class<?> callback, Class<?> arrow) throws NoSuchFieldException {
        Field owner = null;
        for (Class<?> current = callback; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !arrow.isAssignableFrom(field.getType())) continue;
                if (owner != null) throw new NoSuchFieldException("ambiguous_arrow_owner");
                owner = field;
            }
        }
        if (owner == null) throw new NoSuchFieldException("missing_arrow_owner");
        return owner;
    }

    private static Method feedbackSetter(Class<?> arrow) throws NoSuchMethodException {
        for (Class<?> current = arrow; current != null; current = current.getSuperclass()) {
            Method method;
            try {
                method = current.getDeclaredMethod("setArrowFeedBackDone", boolean.class);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
                throw new NoSuchMethodException("invalid_arrow_feedback_setter");
            }
            return method;
        }
        throw new NoSuchMethodException("missing_arrow_feedback_setter");
    }

    private static void reportFailure(Throwable error) {
        if (FAILURE_REPORTED.compareAndSet(false, true)) {
            Log.w(TAG, "launcher ready feedback guard unavailable: "
                    + error.getClass().getSimpleName());
        }
    }
}
