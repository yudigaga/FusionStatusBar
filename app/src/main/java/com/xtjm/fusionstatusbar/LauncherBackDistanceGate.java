package com.xtjm.fusionstatusbar;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the current Home's ready-arrow distance rule when its contract is available. */
final class LauncherBackDistanceGate {
    private static final float FALLBACK_DISTANCE_PX = 180f;
    private final Method nativePredicate;
    private final AtomicBoolean failureReported = new AtomicBoolean();
    private volatile boolean nativeUsable;

    private LauncherBackDistanceGate(Method nativePredicate) {
        this.nativePredicate = nativePredicate;
        nativeUsable = nativePredicate != null;
    }

    static LauncherBackDistanceGate resolve(Class<?> processor) {
        for (Class<?> current = processor; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("isShowBackArrow", float.class);
                if (method.getReturnType() != boolean.class || !Modifier.isStatic(method.getModifiers())) {
                    return new LauncherBackDistanceGate(null);
                }
                method.setAccessible(true);
                return new LauncherBackDistanceGate(method);
            } catch (NoSuchMethodException ignored) {
                // Some host versions may move the static rule onto a processor base class.
            } catch (RuntimeException | LinkageError unavailable) {
                return new LauncherBackDistanceGate(null);
            }
        }
        return new LauncherBackDistanceGate(null);
    }

    boolean isReady(float inwardDistance) {
        if (!Float.isFinite(inwardDistance) || inwardDistance < 0f) return false;
        if (nativeUsable) {
            try {
                return (Boolean) nativePredicate.invoke(null, inwardDistance);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                // Do not allocate another exception on every MOVE after an incompatible rule.
                nativeUsable = false;
                if (failureReported.compareAndSet(false, true)) {
                    Log.w("FusionStatusBar", "launcher native back distance rule unavailable; "
                            + "using provided-host fallback");
                }
            }
        }
        return inwardDistance > FALLBACK_DISTANCE_PX;
    }

    Method nativePredicate() { return nativePredicate; }
}
