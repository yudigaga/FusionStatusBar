package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ShadeSwitchAnimationHooksTest {
    @Test public void controlCenterUsesTheNotificationShowAndHideAlphaEases() throws Throwable {
        Registry registry = install();
        SwitchListener controlCenter = new SwitchListener(registry, 1);

        registry.trigger(controlCenter, true, true);
        assertSame(ShadeSwitchController.ALPHA_SHOW_EASE, controlCenter.alphaConfig.ease);
        assertSame(SwitchListener.CONTROL_CENTER_TRANSLATION_EASE,
                controlCenter.translationConfig.ease);

        registry.trigger(controlCenter, false, true);
        assertSame(ShadeSwitchController.ALPHA_HIDE_EASE, controlCenter.alphaConfig.ease);
        assertSame(SwitchListener.CONTROL_CENTER_TRANSLATION_EASE,
                controlCenter.translationConfig.ease);
    }

    @Test public void notificationListenerAndNonAnimatedSwitchKeepNativeBehavior() throws Throwable {
        Registry registry = install();
        SwitchListener notification = new SwitchListener(registry, 0);
        registry.trigger(notification, true, true);
        assertSame(SwitchListener.CONTROL_CENTER_ALPHA_SHOW_EASE,
                notification.alphaConfig.ease);

        SwitchListener controlCenter = new SwitchListener(registry, 1);
        registry.trigger(controlCenter, true, false);
        assertNull(controlCenter.alphaConfig.ease);
    }

    @Test public void failedNativeSwitchRestoresTheScopedEaseOverride() throws Throwable {
        Registry registry = install();
        SwitchListener controlCenter = new SwitchListener(registry, 1);
        controlCenter.failAfterEase = true;
        try {
            registry.trigger(controlCenter, true, true);
            fail("native failure should propagate");
        } catch (IllegalStateException expected) {
            assertEquals("native_failure", expected.getMessage());
        }

        SwitchListener notification = new SwitchListener(registry, 0);
        registry.trigger(notification, true, true);
        assertSame(SwitchListener.CONTROL_CENTER_ALPHA_SHOW_EASE,
                notification.alphaConfig.ease);
    }

    private static Registry install() {
        Registry registry = new Registry();
        ShadeSwitchAnimationHooks.Result result = ShadeSwitchAnimationHooks.install(
                ShadeSwitchAnimationHooksTest.class.getClassLoader(), (name, loader) -> switch (name) {
                    case ShadeSwitchAnimationHooks.SWITCH_LISTENER_CLASS -> SwitchListener.class;
                    case ShadeSwitchAnimationHooks.SWITCH_CONTROLLER_CLASS -> ShadeSwitchController.class;
                    case ShadeSwitchAnimationHooks.ANIM_CONFIG_CLASS -> AnimConfig.class;
                    default -> null;
                }, registry);
        assertEquals(2, result.installed);
        assertTrue(result.unavailable.toString(), result.unavailable.isEmpty());
        return registry;
    }

    public static final class EaseStyle { }

    public static final class ShadeSwitchController {
        public static final EaseStyle ALPHA_SHOW_EASE = new EaseStyle();
        public static final EaseStyle ALPHA_HIDE_EASE = new EaseStyle();
    }

    public static final class AnimConfig {
        EaseStyle ease;
        public AnimConfig setEase(EaseStyle value) { ease = value; return this; }
    }

    public static final class SwitchListener {
        static final EaseStyle CONTROL_CENTER_ALPHA_SHOW_EASE = new EaseStyle();
        static final EaseStyle CONTROL_CENTER_ALPHA_HIDE_EASE = new EaseStyle();
        static final EaseStyle CONTROL_CENTER_TRANSLATION_EASE = new EaseStyle();
        final int $r8$classId;
        final AnimConfig alphaConfig = new AnimConfig();
        final AnimConfig translationConfig = new AnimConfig();
        private final Registry registry;
        boolean appeared;
        boolean failAfterEase;

        SwitchListener(Registry registry, int switchId) {
            this.registry = registry;
            $r8$classId = switchId;
        }

        public void setTriggerAppeared(boolean value, boolean animated) throws Throwable {
            appeared = value;
            if (animated) {
                registry.ease(alphaConfig, value ? CONTROL_CENTER_ALPHA_SHOW_EASE
                        : CONTROL_CENTER_ALPHA_HIDE_EASE);
                registry.ease(translationConfig, CONTROL_CENTER_TRANSLATION_EASE);
            }
            if (failAfterEase) throw new IllegalStateException("native_failure");
        }
    }

    private static final class Registry implements ShadeSwitchAnimationHooks.Registrar {
        private final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();

        @Override public boolean install(String id, Method method, XposedInterface.Hooker hook) {
            hooks.put(method, hook);
            return true;
        }

        void trigger(SwitchListener listener, boolean appeared, boolean animated) throws Throwable {
            Method method = method("setTriggerAppeared");
            call(method, listener, List.of(appeared, animated), args -> {
                listener.setTriggerAppeared((Boolean) args[0], (Boolean) args[1]);
                return null;
            });
        }

        void ease(AnimConfig config, EaseStyle ease) throws Throwable {
            call(method("setEase"), config, List.of(ease), args -> {
                config.setEase((EaseStyle) args[0]);
                return config;
            });
        }

        private Method method(String name) {
            return hooks.keySet().stream().filter(candidate -> candidate.getName().equals(name))
                    .findFirst().orElseThrow();
        }

        private Object call(Method method, Object target, List<?> values,
                NativeCall proceed) throws Throwable {
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getThisObject" -> target;
                        case "getArgs" -> values;
                        case "proceed" -> proceed.run(
                                args != null && args.length > 0 && args[0] instanceof Object[] override
                                        ? override : values.toArray());
                        default -> null;
                    });
            return hooks.get(method).intercept(chain);
        }
    }

    @FunctionalInterface private interface NativeCall {
        Object run(Object[] args) throws Throwable;
    }
}
