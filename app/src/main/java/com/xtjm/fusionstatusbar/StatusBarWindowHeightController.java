package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Applies the configured total status-bar height to the window and its inset source. */
final class StatusBarWindowHeightController {
    private static final Map<Object, Boolean> CONTROLLERS = new WeakHashMap<>();

    private StatusBarWindowHeightController() { }

    static int heightPx(Context context, int heightDp) {
        return Math.max(1, Math.round(heightDp * context.getResources().getDisplayMetrics().density));
    }

    static int waterfallInsetTop(View root) {
        if (root == null) return 0;
        try {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets == null || insets.getDisplayCutout() == null) return 0;
            return Math.max(0, insets.getDisplayCutout().getWaterfallInsets().top);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    static Object adjustLayoutParams(Object layoutParams, int totalHeightPx) {
        if (!(layoutParams instanceof WindowManager.LayoutParams params)) return layoutParams;
        configure(params, totalHeightPx, true);
        return params;
    }

    static boolean apply(Object controller, FusionConfig config) {
        if (controller == null || config == null) return false;
        synchronized (CONTROLLERS) { CONTROLLERS.put(controller, Boolean.TRUE); }
        Object contextValue = field(controller, "mContext");
        if (!(contextValue instanceof Context context)) return false;
        int totalHeight = heightPx(context, config.statusBarHeight);
        writeField(controller, "mBarHeight", totalHeight);

        Object changedValue = field(controller, "mLpChanged");
        if (changedValue instanceof WindowManager.LayoutParams changed) {
            boolean launching = Boolean.TRUE.equals(field(field(controller, "mCurrentState"),
                    "mIsLaunchAnimationRunning"));
            configure(changed, launching ? ViewGroup.LayoutParams.MATCH_PARENT : totalHeight, false);
            Object rotations = field(changed, "paramsForRotation");
            if (rotations != null && rotations.getClass().isArray()) {
                int length = Array.getLength(rotations);
                for (int i = 0; i < length; i++) {
                    Object value = Array.get(rotations, i);
                    if (value instanceof WindowManager.LayoutParams rotation) {
                        configure(rotation, launching ? ViewGroup.LayoutParams.MATCH_PARENT
                                : totalHeight, false);
                    }
                }
            }
            updateAttachedWindow(controller, changed);
        }
        return true;
    }

    static int refreshAll(FusionConfig config) {
        ArrayList<Object> controllers;
        synchronized (CONTROLLERS) { controllers = new ArrayList<>(CONTROLLERS.keySet()); }
        int failures = 0;
        for (Object controller : controllers) {
            try {
                if (!apply(controller, config)) failures++;
            } catch (RuntimeException error) {
                failures++;
            }
        }
        return failures;
    }

    private static void updateAttachedWindow(Object controller,
            WindowManager.LayoutParams changed) {
        if (!Boolean.TRUE.equals(field(controller, "mIsAttached"))) return;
        Object layoutParamsValue = field(controller, "mLp");
        Object windowManagerValue = field(controller, "mWindowManager");
        Object windowValue = field(controller, "mStatusBarWindowView");
        if (!(layoutParamsValue instanceof WindowManager.LayoutParams layoutParams)
                || !(windowManagerValue instanceof WindowManager windowManager)
                || !(windowValue instanceof View window)) return;
        try {
            if (layoutParams.copyFrom(changed) == 0) return;
            windowManager.updateViewLayout(window, layoutParams);
        } catch (RuntimeException ignored) {
            // The native update remains in place if the host window is mid-detach.
        }
    }

    private static void configure(WindowManager.LayoutParams params, int heightPx,
            boolean onlyStatusAndTappableInsets) {
        params.height = heightPx;
        Object providersValue = field(params, "providedInsets");
        if (providersValue == null || !providersValue.getClass().isArray()) return;
        int length = Array.getLength(providersValue);
        for (int index = 0; index < length; index++) {
            Object provider = Array.get(providersValue, index);
            if (provider == null || onlyStatusAndTappableInsets && index > 1) continue;
            setInsetSize(provider, heightPx);
        }
    }

    private static void setInsetSize(Object provider, int heightPx) {
        try {
            Method method = provider.getClass().getMethod("setInsetsSize", Insets.class);
            method.invoke(provider, Insets.of(0, heightPx, 0, 0));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older ROMs may not expose the mutable inset-provider method.
        }
    }

    private static Object field(Object target, String name) {
        for (Class<?> type = target == null ? null : target.getClass(); type != null;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) return null;
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue up the controller hierarchy.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean writeField(Object target, String name, Object value) {
        for (Class<?> type = target == null ? null : target.getClass(); type != null;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) return false;
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // Continue up the controller hierarchy.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }
}
