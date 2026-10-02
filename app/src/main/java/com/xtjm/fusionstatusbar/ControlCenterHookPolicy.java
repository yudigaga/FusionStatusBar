package com.xtjm.fusionstatusbar;

import java.lang.reflect.Method;

final class ControlCenterHookPolicy {
    private ControlCenterHookPolicy() {
    }

    static boolean matchesMethod(Method method, String methodName, int parameterCount,
            Class<?> parameterType, Class<?> returnType) {
        if (method == null || !methodName.equals(method.getName())
                || method.getParameterCount() != parameterCount) {
            return false;
        }
        if (parameterType != null
                && (parameterCount == 0 || method.getParameterTypes()[0] != parameterType)) {
            return false;
        }
        return returnType == null || method.getReturnType() == returnType;
    }

    static boolean matchesMethod(Method method, String methodName, Class<?>[] parameterTypes,
            Class<?> returnType) {
        if (method == null || !methodName.equals(method.getName())
                || parameterTypes == null
                || method.getParameterCount() != parameterTypes.length) {
            return false;
        }
        Class<?>[] actual = method.getParameterTypes();
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != parameterTypes[i]) return false;
        }
        return returnType == null || method.getReturnType() == returnType;
    }
}
