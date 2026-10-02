package com.xtjm.fusionstatusbar;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** A method is installed only after the framework returns an actual handle. */
final class RuntimeHookRegistry<H> {
    interface Installer<H> { H install() throws Throwable; }
    private final Map<Method, Map<String, H>> handles = new HashMap<>();
    private final Map<Method, Set<String>> failures = new HashMap<>();

    synchronized boolean install(Method method, String id, Installer<H> installer)
            throws Throwable {
        Map<String, H> byId = handles.get(method);
        if (byId != null && byId.containsKey(id)) return true;
        failures.computeIfAbsent(method, ignored -> new HashSet<>()).add(id);
        H handle = installer.install();
        if (handle == null) return false;
        handles.computeIfAbsent(method, ignored -> new HashMap<>()).put(id, handle);
        Set<String> pending = failures.get(method);
        pending.remove(id);
        if (pending.isEmpty()) failures.remove(method);
        return true;
    }

    synchronized boolean hasFailures() { return !failures.isEmpty(); }

    synchronized int size() {
        int count = 0;
        for (Map<String, H> byId : handles.values()) count += byId.size();
        return count;
    }

    synchronized boolean hasHandle(Method method, String id) {
        Map<String, H> byId = handles.get(method);
        return byId != null && byId.containsKey(id);
    }

    synchronized boolean hasHook(Class<?> type, String methodName) {
        if (type == null) return false;
        for (Method method : handles.keySet()) {
            if (method.getDeclaringClass().isAssignableFrom(type)
                    && method.getName().equals(methodName)) return true;
        }
        return false;
    }
}
