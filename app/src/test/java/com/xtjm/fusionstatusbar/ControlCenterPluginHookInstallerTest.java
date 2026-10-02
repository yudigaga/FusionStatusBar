package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class ControlCenterPluginHookInstallerTest {
    private static final XposedInterface.Hooker NO_OP = chain -> chain.proceed();

    @Test public void requiredLayoutHooksAreIndependentPerClassLoaderAndRepeatSafely()
            throws Exception {
        ClassLoader loaderA = new ClassLoader(getClass().getClassLoader()) { };
        ClassLoader loaderB = new ClassLoader(getClass().getClassLoader()) { };
        Resolver resolver = new Resolver();
        resolver.bind(loaderA, ListFixtureA.class, AdapterFixtureA.class);
        resolver.bind(loaderB, ListFixtureB.class, AdapterFixtureB.class);
        Registrar registrar = new Registrar();
        AtomicInteger materialCalls = new AtomicInteger();
        ControlCenterPluginHookInstaller installer = installer(resolver, registrar, materialCalls);

        ControlCenterPluginHookInstaller.Result resultA = installer.install(loaderA);
        ControlCenterPluginHookInstaller.Result resultB = installer.install(loaderB);

        assertTrue(resultA.layoutAvailable);
        assertTrue(resultB.layoutAvailable);
        assertEquals(1, resultA.count(ControlCenterPluginHookInstaller.Group.LIST));
        assertEquals(1, resultB.count(ControlCenterPluginHookInstaller.Group.LIST));
        assertTrue(registrar.contains("QSListController#distributeTiles/1",
                ListFixtureA.class.getDeclaredMethod("distributeTiles", boolean.class)));
        assertTrue(registrar.contains("QSListController#distributeTiles/1",
                ListFixtureB.class.getDeclaredMethod("distributeTiles", boolean.class)));
        assertEquals("Global material hooks are attempted once per plugin-install call", 2,
                materialCalls.get());

        int retainedHandles = registrar.handles.size();
        ControlCenterPluginHookInstaller.Result repeatedA = installer.install(loaderA);
        assertTrue(repeatedA.layoutAvailable);
        assertEquals(retainedHandles, registrar.handles.size());
        assertTrue(resolver.seenLoaders.contains(loaderA));
        assertTrue(resolver.seenLoaders.contains(loaderB));
    }

    @Test public void missingRequiredClassOrMethodDoesNotStopOtherPluginHooks() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };
        Resolver resolver = new Resolver();
        resolver.bind(loader, PartialListFixture.class, AdapterWithoutComponentBind.class);
        Registrar registrar = new Registrar();
        ControlCenterPluginHookInstaller installer = installer(resolver, registrar,
                new AtomicInteger());

        ControlCenterPluginHookInstaller.Result result = installer.install(loader);

        assertFalse(result.layoutAvailable);
        assertTrue(result.missingClasses.contains(
                "miui.systemui.controlcenter.panel.main.qs.QSRecord"));
        assertTrue(result.missingMethods.contains(
                "miui.systemui.controlcenter.panel.main.qs.QSListController#onBindViewHolder"));
        assertTrue(result.missingMethods.contains(
                "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter#onBindViewHolder"));
        assertTrue(registrar.contains("QSListController#distributeTiles/1",
                PartialListFixture.class.getDeclaredMethod("distributeTiles", boolean.class)));
        assertTrue(registrar.contains("MainPanelAdapter#distributeContent/1",
                AdapterWithoutComponentBind.class.getDeclaredMethod("distributeContent", boolean.class)));
    }

    @Test public void onlyFailedRequiredRegistrationMustSucceedBeforeLayoutIsAvailable()
            throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };
        Resolver resolver = new Resolver();
        resolver.bind(loader, ListFixtureA.class, AdapterFixtureA.class);
        Registrar registrar = new Registrar();
        Method failedMethod = AdapterFixtureA.class.getDeclaredMethod(
                "distributeContent", boolean.class);
        registrar.failOnce.add(new Key("MainPanelAdapter#distributeContent/1", failedMethod));
        ControlCenterPluginHookInstaller installer = installer(resolver, registrar,
                new AtomicInteger());

        ControlCenterPluginHookInstaller.Result first = installer.install(loader);
        assertFalse(first.layoutAvailable);
        assertTrue(registrar.contains("QSListController#distributeTiles/1",
                ListFixtureA.class.getDeclaredMethod("distributeTiles", boolean.class)));
        assertFalse(registrar.contains("MainPanelAdapter#distributeContent/1", failedMethod));

        ControlCenterPluginHookInstaller.Result retry = installer.install(loader);
        assertTrue(retry.layoutAvailable);
        assertEquals(Integer.valueOf(2), registrar.calls.get(
                new Key("MainPanelAdapter#distributeContent/1", failedMethod)));
        assertEquals("A successful registration is retained only once", Integer.valueOf(1),
                registrar.installs.get(new Key("QSListController#distributeTiles/1",
                        ListFixtureA.class.getDeclaredMethod("distributeTiles", boolean.class))));
        assertEquals(Integer.valueOf(1), registrar.installs.get(new Key("MainPanelAdapter#distributeContent/1",
                failedMethod)));
    }

    @Test public void globalMaterialHookRemainsBetweenCoreAndVisualPluginHooks()
            throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };
        ArrayList<String> events = new ArrayList<>();
        Resolver resolver = new Resolver();
        resolver.bind(loader, ListFixtureA.class, AdapterFixtureA.class);
        resolver.bindClass(loader,
                "miui.systemui.controlcenter.windowview.ControlCenterWindowViewController",
                WindowFixture.class);
        resolver.events = events;
        Registrar registrar = new Registrar();
        registrar.events = events;
        ControlCenterPluginHookInstaller installer = new ControlCenterPluginHookInstaller(
                resolver, registrar, callback -> NO_OP, () -> events.add("material"));

        ControlCenterPluginHookInstaller.Result result = installer.install(loader);

        int coreHook = events.indexOf("hook:QSListController#distributeTiles/1");
        int material = events.indexOf("material");
        int visualResolve = events.indexOf(
                "resolve:miui.systemui.controlcenter.windowview.ControlCenterWindowViewController");
        assertTrue(coreHook >= 0 && coreHook < material);
        assertTrue(material < visualResolve);
        assertEquals(1, result.count(ControlCenterPluginHookInstaller.Group.VISUAL));
        assertTrue(registrar.contains("ControlCenterWindowViewController#setBlurRatio/1",
                WindowFixture.class.getDeclaredMethod("setBlurRatio", float.class)));
        assertFalse(registrar.contains("ControlCenterWindowViewController#setBlurRatio/1",
                WindowFixture.class.getDeclaredMethod("setBlurRatio", double.class)));
    }

    private static ControlCenterPluginHookInstaller installer(Resolver resolver,
            Registrar registrar, AtomicInteger materialCalls) {
        return new ControlCenterPluginHookInstaller(resolver, registrar, callback -> NO_OP,
                materialCalls::incrementAndGet);
    }

    private static final class Resolver implements ControlCenterPluginHookInstaller.ClassResolver {
        final Map<ClassLoader, Map<String, Class<?>>> classes = new HashMap<>();
        final Set<ClassLoader> seenLoaders = new HashSet<>();
        java.util.List<String> events = new ArrayList<>();

        void bind(ClassLoader loader, Class<?> listType, Class<?> adapterType) {
            Map<String, Class<?>> byName = new HashMap<>();
            byName.put("miui.systemui.controlcenter.panel.main.qs.QSListController", listType);
            byName.put("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter",
                    adapterType);
            classes.put(loader, byName);
        }

        void bindClass(ClassLoader loader, String name, Class<?> type) {
            classes.computeIfAbsent(loader, ignored -> new HashMap<>()).put(name, type);
        }

        @Override public Class<?> resolve(String className, ClassLoader targetLoader) {
            events.add("resolve:" + className);
            seenLoaders.add(targetLoader);
            return classes.getOrDefault(targetLoader, Map.of()).get(className);
        }
    }

    private static final class Key {
        final String id;
        final Method method;

        Key(String id, Method method) { this.id = id; this.method = method; }

        @Override public boolean equals(Object other) {
            return other instanceof Key key && id.equals(key.id) && method.equals(key.method);
        }

        @Override public int hashCode() { return 31 * id.hashCode() + method.hashCode(); }
    }

    private static final class Registrar implements ControlCenterPluginHookInstaller.Registrar {
        final Set<Key> handles = new HashSet<>();
        final Set<Key> failOnce = new HashSet<>();
        final Map<Key, Integer> calls = new HashMap<>();
        final Map<Key, Integer> installs = new HashMap<>();
        java.util.List<String> events = new ArrayList<>();

        @Override public boolean install(String id, Method method, XposedInterface.Hooker hooker) {
            Key key = new Key(id, method);
            calls.merge(key, 1, Integer::sum);
            if (handles.contains(key)) return true;
            if (failOnce.remove(key)) return false;
            handles.add(key);
            installs.merge(key, 1, Integer::sum);
            events.add("hook:" + id);
            return true;
        }

        boolean contains(String id, Method method) { return handles.contains(new Key(id, method)); }
    }

    public static class ListFixtureA {
        void distributeTiles(boolean value) { }
        void onBindViewHolder(Object holder, Object model) { }
    }

    public static class ListFixtureB {
        void distributeTiles(boolean value) { }
        void onBindViewHolder(Object holder, Object model) { }
    }

    public static class PartialListFixture {
        void distributeTiles(boolean value) { }
    }

    public static class AdapterFixtureA {
        void distributeContent(boolean value) { }
        void onBindViewHolder(Object holder, Object model) { }
    }

    public static class AdapterFixtureB {
        void distributeContent(boolean value) { }
        void onBindViewHolder(Object holder, Object model) { }
    }

    public static class AdapterWithoutComponentBind {
        void distributeContent(boolean value) { }
    }

    public static class WindowFixture {
        void setBlurRatio(float ratio) { }
        void setBlurRatio(double ratio) { }
    }
}
