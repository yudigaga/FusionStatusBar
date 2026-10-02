package com.xtjm.fusionstatusbar;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cached accessors, not cached mode values: recycled holders can change mode between callbacks. */
final class ControlCenterNativeMode {
    private ControlCenterNativeMode() { }
    private static final java.util.Map<Class<?>, Access> ACCESS = new java.util.LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Class<?>, Access> entry) { return size() > 128; }
    };
    private static synchronized Access access(Class<?> type) { return ACCESS.computeIfAbsent(type, Access::new); }
    static boolean isEditing(Object target) {
        Object current = target;
        for (int i = 0; current != null && i < 16; i++) {
            String mode = mode(current, 0);
            if (mode != null) return "EDIT".equals(mode);
            current = current instanceof View view ? view.getParent() : null;
        }
        return false;
    }
    private static String mode(Object target, int depth) {
        if (target == null || depth > 3) return null;
        Access access = access(target.getClass());
        try {
            if (access.controller != null) {
                Object controller = access.controller.get(target);
                if (controller != null && controller != target) {
                    Access provider = access(controller.getClass());
                    if (provider.get != null) controller = provider.get.invoke(controller);
                    String value = mode(controller, depth + 1);
                    if (value != null) return value;
                }
            }
            Object value = access.getMode != null ? access.getMode.invoke(target)
                    : access.mode == null ? null : access.mode.get(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
    private static final class Access {
        final Field mode, controller;
        final Method getMode, get;
        Access(Class<?> type) {
            mode = field(type, "mode"); controller = field(type, "mainPanelController");
            getMode = method(type, "getMode"); get = method(type, "get");
        }
        private Field field(Class<?> type, String name) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return null;
        }
        private Method method(Class<?> type, String name) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try { Method m = c.getDeclaredMethod(name); m.setAccessible(true); return m; }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return null;
        }
    }
}
