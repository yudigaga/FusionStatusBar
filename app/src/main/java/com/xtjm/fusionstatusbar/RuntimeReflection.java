package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Cache belongs to the actual loaded Class, including unsupported OEM members. */
final class RuntimeReflection {
    // ClassValue is unavailable on Android 13. Weak values also break the member-to-class cycle.
    private static final Map<Class<?>, WeakReference<Members>> CACHE = new WeakHashMap<>();
    private static final class Members {
        final Map<String, Field> fields = new HashMap<>();
        final Map<Signature, Method> methods = new HashMap<>();
    }
    private record Signature(String name, List<Class<?>> arguments, int count, boolean oemOnly) { }
    private RuntimeReflection() { }

    private static synchronized Members members(Class<?> type) {
        WeakReference<Members> reference = CACHE.get(type);
        Members cached = reference == null ? null : reference.get();
        if (cached == null) {
            cached = new Members();
            CACHE.put(type, new WeakReference<>(cached));
        }
        return cached;
    }

    static Field field(Class<?> type, String name) {
        if (type == null) return null;
        Members cache = members(type);
        synchronized (cache) {
            if (cache.fields.containsKey(name)) return cache.fields.get(name);
            Field found = null;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    found = current.getDeclaredField(name);
                    found.setAccessible(true);
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            cache.fields.put(name, found);
            return found;
        }
    }

    static Method exact(Class<?> type, String name, Class<?>[] arguments, boolean oemOnly) {
        return resolve(type, new Signature(name, Arrays.asList(arguments.clone()), -1, oemOnly), null);
    }

    static Method withCount(Class<?> type, String name, int count) {
        return resolve(type, new Signature(name, List.of(), count, false), null);
    }

    static Method compatible(Class<?> type, String name, Object[] arguments) {
        Class<?>[] classes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++)
            classes[i] = arguments[i] == null ? null : arguments[i].getClass();
        return resolve(type, new Signature(name, Arrays.asList(classes), -2, false), arguments);
    }

    private static Method resolve(Class<?> type, Signature key, Object[] values) {
        if (type == null) return null;
        Members cache = members(type);
        synchronized (cache) {
            if (cache.methods.containsKey(key)) return cache.methods.get(key);
            Method found = null;
            search: for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (key.oemOnly && (current == View.class || current == ViewGroup.class)) break;
                for (Method candidate : current.getDeclaredMethods()) {
                    if (!candidate.getName().equals(key.name)) continue;
                    Class<?>[] parameters = candidate.getParameterTypes();
                    boolean matches = key.count >= 0 ? parameters.length == key.count
                            : key.count == -1 ? Arrays.asList(parameters).equals(key.arguments)
                            : accepts(parameters, values);
                    if (!matches) continue;
                    candidate.setAccessible(true);
                    found = candidate;
                    break search;
                }
            }
            cache.methods.put(key, found);
            return found;
        }
    }

    private static boolean accepts(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) return false;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i];
            Object value = arguments[i];
            if (value == null) {
                if (type.isPrimitive()) return false;
            } else if (type.isPrimitive()) {
                if (!acceptsPrimitive(type, value)) return false;
            } else if (!type.isInstance(value)) return false;
        }
        return true;
    }

    private static boolean acceptsPrimitive(Class<?> type, Object value) {
        if (boxed(type).isInstance(value)) return true;
        if (value instanceof Byte) return type == short.class || type == int.class || type == long.class
                || type == float.class || type == double.class;
        if (value instanceof Short || value instanceof Character) return type == int.class || type == long.class
                || type == float.class || type == double.class;
        if (value instanceof Integer) return type == long.class || type == float.class || type == double.class;
        if (value instanceof Long) return type == float.class || type == double.class;
        return value instanceof Float && type == double.class;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }
}
