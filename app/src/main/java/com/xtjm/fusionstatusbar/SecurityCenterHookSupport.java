package com.xtjm.fusionstatusbar;

import java.lang.reflect.Method;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class SecurityCenterHookSupport {
    private SecurityCenterHookSupport() { }
    static int forceBoolean(XposedModule module, ClassLoader loader, SecurityCenterFeature feature,
            String owner, String name, Class<?>... parameters) {
        return forceBooleanAny(module, loader, new SecurityCenterFeature[] {feature}, owner, name, parameters);
    }
    static int forceBooleanAny(XposedModule module, ClassLoader loader, SecurityCenterFeature[] features,
            String owner, String name, Class<?>... parameters) {
        return installValue(module, loader, features, owner, name, boolean.class, Boolean.TRUE, parameters);
    }
    static int forceValue(XposedModule module, ClassLoader loader, SecurityCenterFeature feature,
            String owner, String name, Class<?> returnType, Object value, Class<?>... parameters) {
        return installValue(module, loader, new SecurityCenterFeature[] {feature}, owner, name,
                returnType, value, parameters);
    }
    private static int installValue(XposedModule module, ClassLoader loader, SecurityCenterFeature[] features,
            String owner, String name, Class<?> returnType, Object value, Class<?>[] parameters) {
        try {
            if (!valueMatchesType(returnType, value)) throw new IllegalArgumentException("invalid_hook_value");
            Method method = Class.forName(owner, false, loader).getDeclaredMethod(name, parameters);
            if (method.getReturnType() != returnType) throw new NoSuchMethodException("host_return_type_changed");
            return hook(module, method, method.toGenericString(), chain -> interceptValue(chain,
                    anyEnabled(SecurityCenterConfigRuntime.current(), features), value));
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "phone manager gate unavailable: " + owner + "." + name, error);
            return 0;
        }
    }
    static int hook(XposedModule module, Method method, String id, XposedInterface.Hooker hooker) {
        try {
            method.setAccessible(true);
            try { module.deoptimize(method); } catch (Throwable ignored) { }
            module.hook(method).setId("fusion-statusbar:security-center:" + id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
            return 1;
        } catch (Throwable error) {
            module.log(Log.WARN, FusionModule.TAG, "phone manager hook unavailable: " + id, error);
            return 0;
        }
    }
    static boolean anyEnabled(MiscConfig config, SecurityCenterFeature... features) {
        for (SecurityCenterFeature feature : features) if (config.isEnabled(feature)) return true;
        return false;
    }
    static Object interceptValue(XposedInterface.Chain chain, boolean enabled, Object value) throws Throwable {
        return enabled ? value : chain.proceed();
    }
    static boolean valueMatchesType(Class<?> type, Object value) {
        if (type == void.class) return value == null;
        if (type == boolean.class) return value instanceof Boolean;
        if (type == int.class) return value instanceof Integer;
        if (type == long.class) return value instanceof Long;
        if (type == float.class) return value instanceof Float;
        if (type == double.class) return value instanceof Double;
        return !type.isPrimitive() && (value == null || type.isInstance(value));
    }
}
