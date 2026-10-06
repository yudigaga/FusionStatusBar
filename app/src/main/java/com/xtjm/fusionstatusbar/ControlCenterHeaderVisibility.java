package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Applies optional visibility overrides after the native Control Center header updates. */
final class ControlCenterHeaderVisibility {
    private static final String DATE_METHOD = "updateDateVisibility";
    private static final String CARRIER_METHOD = "updateCarrierAndPrivacyVisible";
    private static final WeakHashMap<Object, State> CONTROLLERS = new WeakHashMap<>();

    private ControlCenterHeaderVisibility() { }

    static void afterDateUpdate(Object controller, ControlCenterConfig config) {
        if (controller == null) return;
        synchronized (CONTROLLERS) {
            State state = CONTROLLERS.computeIfAbsent(controller, ignored -> new State());
            state.dateMethod = findMethod(controller.getClass(), DATE_METHOD, 0);
        }
        applyDate(controller, config != null && config.hideDate);
    }

    static void afterCarrierPrivacyUpdate(Object controller, Object[] args,
            ControlCenterConfig config) {
        if (controller == null) return;
        synchronized (CONTROLLERS) {
            State state = CONTROLLERS.computeIfAbsent(controller, ignored -> new State());
            state.carrierMethod = findMethod(controller.getClass(), CARRIER_METHOD,
                    args == null ? 0 : args.length);
            state.carrierArguments = captureArguments(args);
        }
        applyCarrier(controller, config != null && config.hideCarrier);
    }

    /** Re-run native visibility logic so disabling an override restores current system state. */
    static int refresh(ControlCenterConfig config) {
        if (config == null) return 0;
        ArrayList<Invocation> targets = new ArrayList<>();
        synchronized (CONTROLLERS) {
            for (java.util.Map.Entry<Object, State> entry : CONTROLLERS.entrySet()) {
                Object controller = entry.getKey();
                State state = entry.getValue();
                if (controller == null || state == null) continue;
                if (state.dateMethod != null) {
                    targets.add(new Invocation(controller, state.dateMethod, new Object[0]));
                }
                Object[] args = resolveArguments(state.carrierArguments);
                if (state.carrierMethod != null && args != null) {
                    targets.add(new Invocation(controller, state.carrierMethod, args));
                }
            }
        }
        int failures = 0;
        for (Invocation invocation : targets) {
            try {
                invocation.method.invoke(invocation.controller, invocation.arguments);
                if (DATE_METHOD.equals(invocation.method.getName())) {
                    applyDate(invocation.controller, config.hideDate);
                } else if (CARRIER_METHOD.equals(invocation.method.getName())) {
                    applyCarrier(invocation.controller, config.hideCarrier);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                failures++;
            }
        }
        return failures;
    }

    static void clearForTests() {
        synchronized (CONTROLLERS) { CONTROLLERS.clear(); }
    }

    private static void applyDate(Object controller, boolean hidden) {
        if (!hidden) return;
        TextView date = findTextView(controller,
                new String[] {"mDateView", "dateView", "mDateTextView", "dateTextView",
                        "mDateText", "dateText", "mDate"},
                "date");
        if (date != null && date.getVisibility() == View.VISIBLE) date.setVisibility(View.INVISIBLE);
    }

    private static void applyCarrier(Object controller, boolean hidden) {
        if (!hidden) return;
        Object carrierLayout = readField(controller, "carrierLayout");
        if (carrierLayout instanceof View view) {
            if (view.getVisibility() == View.VISIBLE) view.setVisibility(View.INVISIBLE);
            return;
        }
        TextView carrier = findTextView(controller,
                new String[] {"mCarrierText", "carrierText", "mCarrierTextView",
                        "carrierTextView", "mCarrierView", "carrierView", "mCarrierName",
                        "carrierName", "mCarrierLabel", "carrierLabel", "mOperatorName",
                        "operatorName"},
                "carrier");
        if (carrier != null && carrier.getVisibility() == View.VISIBLE) carrier.setVisibility(View.INVISIBLE);
    }

    private static TextView findTextView(Object controller, String[] fieldNames,
            String resourceNamePart) {
        for (String fieldName : fieldNames) {
            Object value = readField(controller, fieldName);
            if (value instanceof TextView text) return text;
            if (value instanceof ViewGroup group) {
                TextView found = findNamedTextView(group, resourceNamePart);
                if (found != null) return found;
            }
        }
        for (View root : rootViews(controller)) {
            TextView found = findNamedTextView(root, resourceNamePart);
            if (found != null) return found;
        }
        return null;
    }

    private static List<View> rootViews(Object controller) {
        ArrayList<View> roots = new ArrayList<>();
        if (controller instanceof View view) roots.add(view);
        for (String name : new String[] {"mRootView", "rootView", "mView", "view",
                "mHeaderView", "headerView"}) {
            Object value = readField(controller, name);
            if (value instanceof View view && !roots.contains(view)) roots.add(view);
        }
        return roots;
    }

    private static TextView findNamedTextView(View view, String namePart) {
        if (view instanceof TextView text && resourceNameContains(view, namePart)) return text;
        if (!(view instanceof ViewGroup group)) return null;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView found = findNamedTextView(group.getChildAt(index), namePart);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean resourceNameContains(View view, String part) {
        if (view.getId() == View.NO_ID) return false;
        try {
            String name = view.getResources().getResourceEntryName(view.getId());
            return name.toLowerCase(java.util.Locale.ROOT).contains(part);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target == null ? null : target.getClass(); type != null;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())) return null;
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through inherited controller fields.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int argumentCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != argumentCount) continue;
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static StoredArgument[] captureArguments(Object[] args) {
        if (args == null) return new StoredArgument[0];
        StoredArgument[] values = new StoredArgument[args.length];
        for (int i = 0; i < args.length; i++) values[i] = new StoredArgument(args[i]);
        return values;
    }

    private static Object[] resolveArguments(StoredArgument[] arguments) {
        if (arguments == null) return null;
        Object[] values = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            values[i] = arguments[i] == null ? null : arguments[i].get();
            if (values[i] == null && arguments[i] != null && !arguments[i].wasNull) return null;
        }
        return values;
    }

    private static final class State {
        Method dateMethod;
        Method carrierMethod;
        StoredArgument[] carrierArguments = new StoredArgument[0];
    }

    private static final class StoredArgument {
        final Object strongValue;
        final WeakReference<Object> weakValue;
        final boolean wasNull;

        StoredArgument(Object value) {
            wasNull = value == null;
            if (value == null || immutable(value)) {
                strongValue = value;
                weakValue = null;
            } else {
                strongValue = null;
                weakValue = new WeakReference<>(value);
            }
        }

        Object get() { return weakValue == null ? strongValue : weakValue.get(); }

        private static boolean immutable(Object value) {
            return value instanceof String || value instanceof Boolean || value instanceof Character
                    || value instanceof Enum<?> || value instanceof Class<?>
                    || value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long || value instanceof Float || value instanceof Double;
        }
    }

    private static final class Invocation {
        final Object controller;
        final Method method;
        final Object[] arguments;
        Invocation(Object controller, Method method, Object[] arguments) {
            this.controller = controller;
            this.method = method;
            this.arguments = arguments;
        }
    }
}
