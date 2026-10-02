package com.xtjm.fusionstatusbar;

import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
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
public class NotificationHookInstallerTest {
    private static final XposedInterface.Hooker NO_OP = chain -> chain.proceed();

    @Test public void installsAllNotificationGroupsAndReturnsReflectionState() throws Exception {
        Resolver resolver = completeResolver();
        Registrar registrar = new Registrar();
        NotificationHookInstaller installer = installer(resolver, registrar);

        NotificationHookInstaller.Result result = installer.install(getClass().getClassLoader());

        assertNotNull(result.headerClass);
        assertEquals("missing=" + result.missingMembers, 11, result.installed);
        assertNotNull(result.bigTimeSizeField);
        assertNotNull(result.expandContextField);
        assertNotNull(result.expandProgressField);
        assertNotNull(result.stackStateField);
        assertNotNull(result.stackAmbientStateField);
        assertNotNull(result.ambientTopPaddingField);
        assertNotNull(result.qsExpansionFractionField);
        assertNotNull(result.stackInjectorField);
        assertNotNull(result.controllerInjectorField);
        assertNotNull(result.useControlCenterMethod);
        assertNotNull(result.topPaddingMethod);
        assertTrue(registrar.contains("HeaderFixture#onFinishInflate",
                HeaderFixture.class.getDeclaredMethod("onFinishInflate")));
        assertTrue("handles=" + registrar.handles,
                registrar.contains("NotificationHeaderExpandController#updateTranslationY",
                ExpandFixture.class.getDeclaredMethod("updateTranslationY")));
        assertTrue(registrar.contains("NetworkSpeedView#setVisibilityByController",
                SpeedFixture.class.getDeclaredMethod("setVisibilityByController", boolean.class)));
        assertTrue(result.missingClasses.isEmpty());
        assertTrue(result.missingMembers.isEmpty());

        NotificationHookInstaller.Result repeated = installer.install(getClass().getClassLoader());
        assertEquals(11, repeated.installed);
        assertEquals(11, registrar.handles.size());
    }

    @Test public void missingHeaderDoesNotBlockExpandStackOrSpeed() throws Exception {
        Resolver resolver = completeResolver();
        resolver.classes.remove(NotificationHookInstaller.HEADER_CLASS);
        Registrar registrar = new Registrar();

        NotificationHookInstaller.Result result = installer(resolver, registrar)
                .install(getClass().getClassLoader());

        assertTrue(result.missingClasses.contains(NotificationHookInstaller.HEADER_CLASS));
        assertEquals("missing=" + result.missingMembers, 5, result.installed);
        assertTrue(registrar.contains("NetworkSpeedView#setVisibilityByController",
                SpeedFixture.class.getDeclaredMethod("setVisibilityByController", boolean.class)));
    }

    @Test public void wrongOverloadIsNotRegisteredAndOtherMethodsContinue() throws Exception {
        Resolver resolver = completeResolver();
        resolver.classes.put(NotificationHookInstaller.NETWORK_SPEED_CLASS, WrongSpeedFixture.class);
        Registrar registrar = new Registrar();

        NotificationHookInstaller.Result result = installer(resolver, registrar)
                .install(getClass().getClassLoader());

        assertTrue(result.missingMembers.contains(
                NotificationHookInstaller.NETWORK_SPEED_CLASS + "#setVisibilityByController"));
        assertFalse(registrar.contains("WrongSpeedFixture#setVisibilityByController",
                WrongSpeedFixture.class.getDeclaredMethod("setVisibilityByController", int.class)));
        assertTrue(registrar.contains("HeaderFixture#updateLayout",
                HeaderFixture.class.getDeclaredMethod("updateLayout")));
    }

    @Test public void failedHookCanRetryWithoutDuplicatingSuccessfulHook() throws Exception {
        Resolver resolver = completeResolver();
        Registrar registrar = new Registrar();
        Method failed = StackFixture.class.getDeclaredMethod("onLayout",
                boolean.class, int.class, int.class, int.class, int.class);
        registrar.failOnce.add(new Key("NotificationStackScrollLayout#onLayout", failed));
        NotificationHookInstaller installer = installer(resolver, registrar);

        NotificationHookInstaller.Result first = installer.install(getClass().getClassLoader());
        NotificationHookInstaller.Result retry = installer.install(getClass().getClassLoader());

        assertEquals(10, first.installed);
        assertEquals(11, retry.installed);
        assertEquals(Integer.valueOf(2), registrar.calls.get(
                new Key("NotificationStackScrollLayout#onLayout", failed)));
        assertEquals(11, registrar.handles.size());
        assertTrue(registrar.contains("NotificationStackScrollLayout#onLayout", failed));
    }

    private static NotificationHookInstaller installer(Resolver resolver, Registrar registrar) {
        return new NotificationHookInstaller(resolver, new Members(), registrar,
                callback -> NO_OP);
    }

    private static Resolver completeResolver() {
        Resolver resolver = new Resolver();
        resolver.classes.put(NotificationHookInstaller.HEADER_CLASS, HeaderFixture.class);
        resolver.classes.put(NotificationHookInstaller.EXPAND_CLASS, ExpandFixture.class);
        resolver.classes.put(NotificationHookInstaller.EXPAND_CALLBACK_CLASS, ExpandCallbackFixture.class);
        resolver.classes.put(NotificationHookInstaller.STACK_CLASS, StackFixture.class);
        resolver.classes.put(NotificationHookInstaller.NETWORK_SPEED_CLASS, SpeedFixture.class);
        return resolver;
    }

    private static final class Resolver implements NotificationHookInstaller.ClassResolver {
        final Map<String, Class<?>> classes = new HashMap<>();
        @Override public Class<?> resolve(String name, ClassLoader targetLoader) {
            return classes.get(name);
        }
    }

    private static final class Members implements NotificationHookInstaller.MemberResolver {
        @Override public Field field(Class<?> type, String name) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
                catch (NoSuchFieldException ignored) { }
            }
            return null;
        }
        @Override public Method exact(Class<?> type, String name, Class<?>[] parameters,
                boolean searchSuper) {
            try {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        Method method = current.getDeclaredMethod(name, parameters);
                        method.setAccessible(true);
                        return method;
                    } catch (NoSuchMethodException ignored) {
                        if (!searchSuper) break;
                    }
                }
                return null;
            } catch (Throwable ignored) { return null; }
        }
        @Override public Method[] declaredMethods(Class<?> type) { return type.getDeclaredMethods(); }
    }

    private static final class Key {
        final String id; final Method method;
        Key(String id, Method method) { this.id = id; this.method = method; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && id.equals(key.id) && method.equals(key.method);
        }
        @Override public int hashCode() { return 31 * id.hashCode() + method.hashCode(); }
        @Override public String toString() { return id + "@" + method.getName(); }
    }

    private static final class Registrar implements NotificationHookInstaller.Registrar {
        final Set<Key> handles = new HashSet<>();
        final Set<Key> failOnce = new HashSet<>();
        final Map<Key, Integer> calls = new HashMap<>();
        @Override public boolean install(String id, Method method, XposedInterface.Hooker hooker) {
            Key key = new Key(id, method); calls.merge(key, 1, Integer::sum);
            if (handles.contains(key)) return true;
            if (failOnce.remove(key)) return false;
            handles.add(key); return true;
        }
        boolean contains(String id, Method method) { return handles.contains(new Key(id, method)); }
    }

    public static class HeaderFixture {
        void onFinishInflate() { } void onAttachedToWindow() { }
        void updateHeaderResources() { } void updateLayout() { }
        void updateFlipResources() { } void updateResources$8() { }
        void onLayout() { }
    }
    public static class ExpandFixture {
        int bigTimeSize; Object context; float progress;
        void updateTranslationY() { }
    }
    public static class ExpandCallbackFixture { void onExpansionChanged(float value) { } }
    public static class Injector { ControllerInjector nsslControllerInjector = new ControllerInjector(); }
    public static class ControllerInjector { boolean isUseControlCenter() { return false; } }
    public static class StackFixture {
        int mStatusBarState; Ambient mAmbientState = new Ambient(); float mQsExpansionFraction;
        Injector mNsslInjector = new Injector();
        void updateTopPadding(float padding, boolean animate) { }
        void onLayout(boolean changed, int l, int t, int r, int b) { }
    }
    public static class Ambient { float mTopPadding; }
    public static class SpeedFixture { void setVisibilityByController(boolean visible) { } }
    public static class WrongSpeedFixture { void setVisibilityByController(int visible) { } }
}
