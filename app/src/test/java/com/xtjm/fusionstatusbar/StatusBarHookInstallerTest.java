package com.xtjm.fusionstatusbar;

import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class StatusBarHookInstallerTest {
    private static final XposedInterface.Hooker NO_OP = chain -> chain.proceed();

    @Test public void installsIndependentGroupsWithStableIdsAndMethodSignatures() throws Exception {
        Resolver resolver = completeResolver();
        Registrar registrar = new Registrar();
        StatusBarHookInstaller installer = new StatusBarHookInstaller(resolver, registrar,
                callback -> NO_OP);

        StatusBarHookInstaller.Targets targets = installer.resolve(getClass().getClassLoader());
        assertEquals(6, resolver.resolvedNames.size());
        StatusBarHookInstaller.Result result = installer.install(targets);

        assertEquals(13, result.batteryHooks);
        assertEquals(4, result.containerHooks);
        assertEquals(2, result.batteryContainerHooks);
        assertEquals(7, result.layoutHooks);
        assertEquals(26, registrar.handles.size());
        assertTrue(registrar.contains("BatteryFixture#onMeasure",
                BatteryFixture.class.getDeclaredMethod("onMeasure", int.class, int.class)));
        Method layout = BatteryFixture.class.getDeclaredMethod("onLayout", boolean.class,
                int.class, int.class, int.class, int.class);
        assertTrue(registrar.contains("BatteryFixture#onLayout", layout));
        assertTrue(registrar.contains("PhoneBaseFixture#onMeasure",
                PhoneBaseFixture.class.getDeclaredMethod("onMeasure", int.class, int.class)));
        assertTrue(result.missingClasses.isEmpty());
        assertTrue(result.missingMethods.isEmpty());

        StatusBarHookInstaller.Result repeated = installer.install(targets);
        assertEquals(0, repeated.batteryHooks + repeated.containerHooks
                + repeated.batteryContainerHooks + repeated.layoutHooks);
        assertEquals("Repeated installation retains the existing registrations", 26,
                registrar.handles.size());
    }

    @Test public void missingBatteryDoesNotBlockContainerOrLayoutGroups() {
        Resolver resolver = completeResolver();
        resolver.classes.remove(StatusBarHookInstaller.BATTERY_CLASS);
        Registrar registrar = new Registrar();
        StatusBarHookInstaller installer = new StatusBarHookInstaller(resolver, registrar,
                callback -> NO_OP);

        StatusBarHookInstaller.Targets targets = installer.resolve(getClass().getClassLoader());
        StatusBarHookInstaller.Result result = installer.install(targets);

        assertNull(result.batteryClass);
        assertTrue(result.missingClasses.contains(StatusBarHookInstaller.BATTERY_CLASS));
        assertEquals(0, result.batteryHooks);
        assertEquals(4, result.containerHooks);
        assertEquals(7, result.layoutHooks);
        assertEquals(13, registrar.handles.size());
    }

    @Test public void missingMethodDoesNotPreventOtherMethodsInItsGroup() throws Exception {
        Resolver resolver = completeResolver();
        resolver.classes.put(StatusBarHookInstaller.BATTERY_CLASS, PartialBatteryFixture.class);
        Registrar registrar = new Registrar();
        StatusBarHookInstaller installer = new StatusBarHookInstaller(resolver, registrar,
                callback -> NO_OP);

        StatusBarHookInstaller.Result result = installer.install(
                installer.resolve(getClass().getClassLoader()));

        assertEquals(1, result.batteryHooks);
        assertTrue(result.missingMethods.contains("PartialBatteryFixture#onMeasure"));
        assertTrue(registrar.contains("PartialBatteryFixture#onAttachedToWindow",
                PartialBatteryFixture.class.getDeclaredMethod("onAttachedToWindow")));
        assertEquals(14, registrar.handles.size());
    }

    @Test public void retriesOnlyTheFailedRegistrationAndKeepsClassLoaderResultsSeparate()
            throws Exception {
        ClassLoader loaderA = new ClassLoader(getClass().getClassLoader()) { };
        ClassLoader loaderB = new ClassLoader(getClass().getClassLoader()) { };
        Resolver resolver = completeResolver();
        resolver.byLoader.put(loaderA, BatteryLoaderAFixture.class);
        resolver.byLoader.put(loaderB, BatteryLoaderBFixture.class);
        Registrar registrar = new Registrar();
        registrar.failOnce.add("BatteryLoaderAFixture#onMeasure");
        StatusBarHookInstaller installerA = new StatusBarHookInstaller(resolver, registrar,
                callback -> NO_OP);
        StatusBarHookInstaller installerB = new StatusBarHookInstaller(resolver, registrar,
                callback -> NO_OP);

        StatusBarHookInstaller.Targets targetsA = installerA.resolve(loaderA);
        assertSame(BatteryLoaderAFixture.class, targetsA.battery);
        StatusBarHookInstaller.Result firstA = installerA.install(targetsA);
        StatusBarHookInstaller.Targets targetsB = installerB.resolve(loaderB);
        assertSame(BatteryLoaderBFixture.class, targetsB.battery);
        StatusBarHookInstaller.Result resultB = installerB.install(targetsB);
        StatusBarHookInstaller.Result retryA = installerA.install(targetsA);

        assertEquals(12, firstA.batteryHooks);
        assertEquals(1, retryA.batteryHooks);
        assertEquals(13, resultB.batteryHooks);
        assertEquals(Integer.valueOf(2), registrar.attempts.get("BatteryLoaderAFixture#onMeasure"));
        assertEquals(Integer.valueOf(1), registrar.attempts.get("BatteryLoaderAFixture#onAttachedToWindow"));
        assertTrue(registrar.contains("BatteryLoaderAFixture#onMeasure",
                BatteryLoaderAFixture.class.getDeclaredMethod("onMeasure", int.class, int.class)));
        assertTrue(registrar.contains("BatteryLoaderBFixture#onMeasure",
                BatteryLoaderBFixture.class.getDeclaredMethod("onMeasure", int.class, int.class)));
        assertTrue(resolver.seenLoaders.contains(loaderA));
        assertTrue(resolver.seenLoaders.contains(loaderB));
    }

    private static Resolver completeResolver() {
        Resolver resolver = new Resolver();
        resolver.classes.put(StatusBarHookInstaller.BATTERY_CLASS, BatteryFixture.class);
        resolver.classes.put(StatusBarHookInstaller.CONTAINER_CLASS, ContainerFixture.class);
        resolver.classes.put(StatusBarHookInstaller.BATTERY_CONTAINER_CLASS, BatteryContainerFixture.class);
        resolver.classes.put(StatusBarHookInstaller.PHONE_CLASS, PhoneFixture.class);
        resolver.classes.put(StatusBarHookInstaller.PHONE_BASE_CLASS, PhoneBaseFixture.class);
        resolver.classes.put(StatusBarHookInstaller.KEYGUARD_CLASS, KeyguardFixture.class);
        return resolver;
    }

    private static class Resolver implements StatusBarHookInstaller.ClassResolver {
        final Map<String, Class<?>> classes = new HashMap<>();
        final Map<ClassLoader, Class<?>> byLoader = new HashMap<>();
        final Set<ClassLoader> seenLoaders = new HashSet<>();
        final Set<String> resolvedNames = new HashSet<>();

        @Override public Class<?> resolve(String name, ClassLoader targetLoader) {
            seenLoaders.add(targetLoader);
            resolvedNames.add(name);
            if (StatusBarHookInstaller.BATTERY_CLASS.equals(name) && byLoader.containsKey(targetLoader))
                return byLoader.get(targetLoader);
            return classes.get(name);
        }
    }

    private static class Registrar implements StatusBarHookInstaller.Registrar {
        final Set<String> handles = new HashSet<>();
        final Set<String> failOnce = new HashSet<>();
        final Map<String, Integer> attempts = new HashMap<>();

        @Override public int install(String id, Method method, XposedInterface.Hooker hooker) {
            String key = id + "@" + method.toGenericString();
            if (handles.contains(key)) return 0;
            attempts.merge(id, 1, Integer::sum);
            if (failOnce.remove(id)) return 0;
            return handles.add(key) ? 1 : 0;
        }

        boolean contains(String id, Method method) {
            return handles.contains(id + "@" + method.toGenericString());
        }
    }

    public static class BatteryFixture {
        void onMeasure(int a, int b) { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
        void onFinishInflate() { }
        void onAttachedToWindow() { }
        void updateAll() { }
        void updateChargeAndText() { }
        void onBatteryStyleChanged(int style) { }
        void onDarkChanged(ArrayList<?> areas, float intensity, int tint) { }
        void onLightDarkTintChanged(int light, int dark, boolean useTint) { }
        static void showView(Object owner, View view, String tag) { }
        static void hideView(Object owner, View view, String tag) { }
        static void animateShowView(Object owner, View view, String tag) { }
        static void animateHideView(Object owner, View view, String tag) { }
    }

    public static class PartialBatteryFixture {
        void onAttachedToWindow() { }
    }

    public static class BatteryLoaderAFixture extends BatteryFixture {
        @Override void onMeasure(int a, int b) { }
        @Override void onAttachedToWindow() { }
        static void showView(Object owner, View view, String tag) { }
        static void hideView(Object owner, View view, String tag) { }
        static void animateShowView(Object owner, View view, String tag) { }
        static void animateHideView(Object owner, View view, String tag) { }
    }
    public static class BatteryLoaderBFixture extends BatteryFixture {
        @Override void onMeasure(int a, int b) { }
        @Override void onAttachedToWindow() { }
        static void showView(Object owner, View view, String tag) { }
        static void hideView(Object owner, View view, String tag) { }
        static void animateShowView(Object owner, View view, String tag) { }
        static void animateHideView(Object owner, View view, String tag) { }
    }

    public static class ContainerFixture {
        void onMeasure(int a, int b) { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
        void setIgnoredSlots(java.util.List<String> slots) { }
        void onViewAdded(View view) { }
    }

    public static class BatteryContainerFixture {
        void onMeasure(int a, int b) { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
    }

    public static class PhoneFixture {
        void onFinishInflate() { }
        void onAttachedToWindow() { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
        void updateCutoutLocation() { }
    }

    public static class PhoneBaseFixture {
        void onMeasure(int a, int b) { }
    }

    public static class KeyguardFixture {
        void miuiOnAttachedToWindow() { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
    }
}
