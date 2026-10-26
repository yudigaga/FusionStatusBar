package com.xtjm.fusionstatusbar;

import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Announces that the stationary hold is ready, before the finger is released. */
final class LauncherBackHaptics {
    private static final String TAG = "FusionStatusBar";
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();
    private static final Map<Class<?>, Optional<Method>> NATIVE_FEEDBACK = new ConcurrentHashMap<>();

    private static Optional<Method> resolve(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("shockByBackGesture");
                if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
                    return Optional.empty();
                }
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                // The supplied Home declares its private helper on GestureStubView.
            } catch (RuntimeException | LinkageError unavailable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private LauncherBackHaptics() { }

    static void trigger(View stub) {
        if (stub == null) return;
        Optional<Method> nativeFeedback = NATIVE_FEEDBACK.computeIfAbsent(stub.getClass(),
                LauncherBackHaptics::resolve);
        if (nativeFeedback.isPresent()) {
            try {
                nativeFeedback.get().invoke(stub);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                // The helper may already have emitted a pulse before throwing; do not retry it.
                reportFailure(error);
            }
            return;
        }
        try {
            // Default flags preserve the system and View haptic settings.
            stub.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } catch (RuntimeException | LinkageError error) {
            reportFailure(error);
        }
    }

    private static void reportFailure(Throwable error) {
        if (FAILURE_REPORTED.compareAndSet(false, true)) {
            Log.w(TAG, "launcher back readiness feedback unavailable: "
                    + error.getClass().getSimpleName());
        }
    }
}
