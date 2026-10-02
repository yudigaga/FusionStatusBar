package com.xtjm.fusionstatusbar;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuntimeHookRegistryTest {
    @Test public void nullHandleAndThrowingInstallRemainRetryableWithoutDuplicatingSuccess() throws Throwable {
        RuntimeHookRegistry<Object> registry = new RuntimeHookRegistry<>();
        Method method = Host.class.getDeclaredMethod("mount");
        AtomicInteger calls = new AtomicInteger();
        assertFalse(registry.install(method, "mount", () -> { calls.incrementAndGet(); return null; }));
        try { registry.install(method, "mount", () -> { throw new IllegalStateException("framework unavailable"); }); fail(); }
        catch (IllegalStateException expected) { assertTrue(registry.hasFailures()); }
        assertFalse(registry.hasHook(Host.class, "mount"));
        assertTrue(registry.install(method, "mount", () -> { calls.incrementAndGet(); return new Object(); }));
        assertTrue(registry.install(method, "mount", () -> { fail("Hook installed twice"); return null; }));
        assertEquals(2, calls.get());
        assertEquals(1, registry.size());
        assertFalse(registry.hasFailures());
        assertTrue(registry.hasHook(Host.class, "mount"));
    }

    @Test public void globalMaterialHandleDoesNotCreatePluginCapability() throws Throwable {
        RuntimeHookRegistry<Object> registry = new RuntimeHookRegistry<>();
        registry.install(Material.class.getDeclaredMethod("setAlpha"), "alpha", Object::new);
        assertFalse(registry.hasHook(Host.class, "mount"));
        registry.install(Host.class.getDeclaredMethod("mount"), "mount", Object::new);
        registry.install(OtherHost.class.getDeclaredMethod("mount"), "mount", Object::new);
        assertEquals(3, registry.size());
    }

    static class Host { void mount() { } }
    static class OtherHost { void mount() { } }
    static class Material { void setAlpha() { } }
}
