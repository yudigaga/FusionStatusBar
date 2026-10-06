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
    private static final ThreadLocal<LastFieldLookup> LAST_FIELD =
            ThreadLocal.withInitial(LastFieldLookup::new);
    private static final ThreadLocal<LastMethodLookup> LAST_EXACT =
            ThreadLocal.withInitial(LastMethodLookup::new);
    private static final ThreadLocal<LastCountLookup> LAST_COUNT =
            ThreadLocal.withInitial(LastCountLookup::new);
    private static final ThreadLocal<LastMethodLookup> LAST_COMPATIBLE =
            ThreadLocal.withInitial(LastMethodLookup::new);
    private static final class Members {
        final Map<String, Field> fields = new HashMap<>();
        final Map<Signature, Method> methods = new HashMap<>();
    }
    private record Signature(String name, List<Class<?>> arguments, int count, boolean oemOnly) { }
    private RuntimeReflection() { }

    private static final class LastFieldLookup {
        WeakReference<Class<?>> type;
        String name;
        WeakReference<Field> field;
        boolean negative;

        boolean matches(Class<?> requestedType, String requestedName) {
            if (type == null || type.get() != requestedType || !requestedName.equals(name)) {
                return false;
            }
            return negative || field.get() != null;
        }

        Field result() {
            return negative ? null : field.get();
        }

        void record(Class<?> requestedType, String requestedName, Field resolved) {
            type = new WeakReference<>(requestedType);
            name = requestedName;
            field = resolved == null ? new WeakReference<>(null) : new WeakReference<>(resolved);
            negative = resolved == null;
        }
    }

    private static final class LastMethodLookup {
        WeakReference<Class<?>> type;
        String name;
        Class<?>[] arguments;
        boolean oemOnly;
        WeakReference<Method> method;
        boolean negative;

        boolean matches(Class<?> requestedType, String requestedName, Class<?>[] requestedArguments,
                boolean requestedOemOnly) {
            if (type == null || type.get() != requestedType || oemOnly != requestedOemOnly
                    || !requestedName.equals(name) || !Arrays.equals(arguments, requestedArguments)) {
                return false;
            }
            return negative || method.get() != null;
        }

        Method result() {
            return negative ? null : method.get();
        }

        void record(Class<?> requestedType, String requestedName, Class<?>[] requestedArguments,
                boolean requestedOemOnly, Method resolved) {
            type = new WeakReference<>(requestedType);
            name = requestedName;
            arguments = requestedArguments.clone();
            oemOnly = requestedOemOnly;
            method = resolved == null ? new WeakReference<>(null) : new WeakReference<>(resolved);
            negative = resolved == null;
        }
    }

    private static final class LastCountLookup {
        WeakReference<Class<?>> type;
        String name;
        int count;
        WeakReference<Method> method;
        boolean negative;

        boolean matches(Class<?> requestedType, String requestedName, int requestedCount) {
            return type != null && type.get() == requestedType && count == requestedCount
                    && requestedName.equals(name) && (negative || method.get() != null);
        }

        Method result() {
            return negative ? null : method.get();
        }

        void record(Class<?> requestedType, String requestedName, int requestedCount,
                Method resolved) {
            type = new WeakReference<>(requestedType);
            name = requestedName;
            count = requestedCount;
            method = resolved == null ? new WeakReference<>(null) : new WeakReference<>(resolved);
            negative = resolved == null;
        }
    }

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
        LastFieldLookup last = LAST_FIELD.get();
        if (last.matches(type, name)) return last.result();
        Members cache = members(type);
        synchronized (cache) {
            if (cache.fields.containsKey(name)) {
                Field result = cache.fields.get(name);
                last.record(type, name, result);
                return result;
            }
            Field found = null;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    found = current.getDeclaredField(name);
                    found.setAccessible(true);
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            cache.fields.put(name, found);
            last.record(type, name, found);
            return found;
        }
    }

    static Method exact(Class<?> type, String name, Class<?>[] arguments, boolean oemOnly) {
        if (type == null) return null;
        LastMethodLookup last = LAST_EXACT.get();
        if (last.matches(type, name, arguments, oemOnly)) return last.result();
        Method result = resolve(type, new Signature(name, Arrays.asList(arguments.clone()), -1, oemOnly), null);
        last.record(type, name, arguments, oemOnly, result);
        return result;
    }

    static Method withCount(Class<?> type, String name, int count) {
        if (type == null) return null;
        LastCountLookup last = LAST_COUNT.get();
        if (last.matches(type, name, count)) return last.result();
        Method result = resolve(type, new Signature(name, List.of(), count, false), null);
        last.record(type, name, count, result);
        return result;
    }

    static Method compatible(Class<?> type, String name, Object[] arguments) {
        if (type == null) return null;
        Class<?>[] classes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++)
            classes[i] = arguments[i] == null ? null : arguments[i].getClass();
        LastMethodLookup last = LAST_COMPATIBLE.get();
        if (last.matches(type, name, classes, false)) return last.result();
        Method result = resolve(type, new Signature(name, Arrays.asList(classes), -2, false), arguments);
        last.record(type, name, classes, false, result);
        return result;
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
