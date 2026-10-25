package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterAnimationHooksTest {
    @Test public void queuedAnimationRetainsItsCopyAndNativeCompletionListener() throws Throwable {
        Owner owner = new Owner();
        NativeConfig original = owner.animConfig;
        original.ease = new Ease(-2, 0.95, 0.45, 1.0);
        AtomicInteger completed = new AtomicInteger();
        Runnable listener = completed::incrementAndGet;
        original.listener = listener;
        NativeConfig[] queued = new NativeConfig[1];
        Object result = ControlCenterAnimationHooks.withFastConfig(owner, () -> {
            queued[0] = owner.animConfig;
            return listener;
        });
        assertSame(listener, result);
        assertSame(original, owner.animConfig);
        assertNotSame(original, queued[0]);
        assertSame(listener, queued[0].listener);
        assertEquals("The copied config is consumed after the owner field has been restored", 0,
                completed.get());
        assertEquals(0.30, queued[0].ease.factors[1], 0.0001);
        assertEquals(0.45, original.ease.factors[1], 0.0001);
        assertEquals(original.ease.factors[0], queued[0].ease.factors[0], 0.000001);
        assertEquals(original.ease.factors[2], queued[0].ease.factors[2], 0.000001);
        ((Runnable) queued[0].listener).run();
        assertEquals(1, completed.get());
    }

    @Test public void restoresConfigurationAndPropagatesNativeFailureExactlyOnce() throws Throwable {
        Owner owner = new Owner();
        NativeConfig original = owner.animConfig;
        IllegalStateException nativeError = new IllegalStateException("native_failure");
        AtomicInteger calls = new AtomicInteger();
        try {
            ControlCenterAnimationHooks.withFastConfig(owner, () -> {
                calls.incrementAndGet();
                throw nativeError;
            });
            fail();
        } catch (IllegalStateException error) {
            assertSame(nativeError, error);
        }
        assertEquals(1, calls.get());
        assertSame(original, owner.animConfig);
    }

    @Test public void preservesFastAndNonSpringNativeTimings() throws Throwable {
        Owner owner = new Owner();
        owner.animConfig.ease = new Ease(-2, 0.7, 0.16, 9.0);
        NativeConfig fast = owner.animConfig;
        Ease originalEase = fast.ease;
        ControlCenterAnimationHooks.withFastConfig(owner, () -> {
            assertSame(fast, owner.animConfig);
            assertSame("An already faster native spring keeps its damping and extra factors", originalEase, owner.animConfig.ease);
            return null;
        });
        assertArrayEquals(new double[] {.7, .16, 9.0}, fast.ease.factors, 0);
        owner.animConfig.ease = new Ease(2, 120);
        ControlCenterAnimationHooks.withFastConfig(owner, () -> {
            assertSame(fast, owner.animConfig);
            return null;
        });
    }

    @Test public void missingNativeConfigFallsBackAndDoesNotDoubleInvoke() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        assertEquals("native", ControlCenterAnimationHooks.withFastConfig(new Object(), () -> {
            calls.incrementAndGet();
            return "native";
        }));
        assertEquals(1, calls.get());
    }

    @Test public void untunedNativeFailureIsNotCaughtByReflectionFallback() throws Throwable {
        Owner owner = new Owner();
        owner.animConfig.ease = new Ease(-2, 0.9, 0.16);
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException nativeError = new IllegalStateException("native_failure");
        try {
            ControlCenterAnimationHooks.withFastConfig(owner, () -> {
                calls.incrementAndGet();
                throw nativeError;
            });
            fail();
        } catch (IllegalStateException error) {
            assertSame(nativeError, error);
        }
        assertEquals(1, calls.get());
    }

    @Test public void nullEaseQueuesAnExplicitOpenProfileWithoutChangingTheGlobalNativeDefault() throws Throwable {
        Owner owner = new Owner();
        owner.animConfig.ease = null;
        NativeConfig original = owner.animConfig;
        Ease nativeDefault = NativeConfig.sDefEase;
        double[] originalFactors = nativeDefault.factors.clone();
        NativeConfig[] queued = new NativeConfig[1];
        ControlCenterAnimationHooks.withFastConfig(owner, () -> {
            queued[0] = owner.animConfig;
            return null;
        });
        assertSame(original, owner.animConfig);
        assertNotSame("The queue must receive a stable copy when the native field uses the default ease", original, queued[0]);
        assertNotNull(queued[0].ease);
        assertEquals(.30, queued[0].ease.factors[1], .0001);
        assertNull(original.ease);
        assertSame(nativeDefault, NativeConfig.sDefEase);
        assertArrayEquals(originalFactors, NativeConfig.sDefEase.factors, 0);
        assertEquals(0.35, NativeConfig.sDefEase.factors[1], 0.0001);
    }

    @Test public void onlyAnimatedCustomPanelCallsAreChangedAndMirrorIsExcluded() throws Throwable {
        Registry registry = new Registry();
        ControlCenterConfig[] current = {customSettings()};
        ControlCenterAnimationHooks.Result result = ControlCenterAnimationHooks.install(
                getClass().getClassLoader(), (name, loader) -> switch (name) {
                    case ControlCenterAnimationHooks.EXPANSION_CLASS -> Expansion.class;
                    case ControlCenterAnimationHooks.BLUR_CLASS -> Blur.class;
                    default -> null;
                }, registry, () -> current[0]);
        assertEquals(2, result.installed);
        assertTrue(result.unavailable.isEmpty());
        assertEquals("Missing optional carrier classes do not change the two base-hook results", 0, result.carrierInstalled);
        Expansion expansion = new Expansion();
        // The target value, rather than the legacy boolean, determines opening versus closing.
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, 1f, true), 0.30);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(true, 0f, true), 0.20);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, .4f, true), 0.35);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, 0f, false), 0.35);
        expansion.this$0.mirror = true;
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, 0f, true), 0.35);
        Blur blur = new Blur();
        assertPeriod(registry, blur, "showBlur", List.of(true, true, true, false), 0.30);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), 0.20);
        assertPeriod(registry, blur, "showBlur", List.of(false, false, true, false), 0.35);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, true), 0.35);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, false, false), 0.35);
        current[0] = current[0].withEnabled(false);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), 0.35);
        current[0] = ControlCenterConfig.defaults().withEnabled(true);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), 0.35);
        current[0] = current[0].withLayoutPlan("invalid");
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), 0.35);
    }

    @Test public void installedHooksConsumeOpeningAndClosingCopiesAfterRestoringTheNativeField() throws Throwable {
        Registry registry = new Registry();
        ControlCenterAnimationHooks.install(getClass().getClassLoader(), (name, loader) -> switch (name) {
            case ControlCenterAnimationHooks.EXPANSION_CLASS -> Expansion.class;
            case ControlCenterAnimationHooks.BLUR_CLASS -> Blur.class;
            default -> null;
        }, registry, ControlCenterAnimationHooksTest::customSettings);

        Expansion expansion = new Expansion();
        expansion.animConfig.ease = null;
        AtomicInteger completed = new AtomicInteger();
        expansion.animConfig.listener = (Runnable) completed::incrementAndGet;
        assertPeriod(registry, expansion, "setExpandProgress", List.of(true, 1f, true), 0.30);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, 0f, true), 0.20);
        assertNull("A default-ease owner remains null between native requests", expansion.animConfig.ease);
        assertEquals("Both original native completion callbacks survive deferred consumption", 2, completed.get());
        Blur blur = new Blur();
        assertPeriod(registry, blur, "showBlur", List.of(true, true, true, false), 0.30);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), 0.20);
    }

    @Test public void excludedInstalledPathsKeepAnActuallySlowerOemProfile() throws Throwable {
        Registry registry = new Registry();
        ControlCenterConfig[] current = {customSettings()};
        ControlCenterAnimationHooks.install(getClass().getClassLoader(), (name, loader) -> switch (name) {
            case ControlCenterAnimationHooks.EXPANSION_CLASS -> Expansion.class;
            case ControlCenterAnimationHooks.BLUR_CLASS -> Blur.class;
            default -> null;
        }, registry, () -> current[0]);
        Expansion expansion = new Expansion();
        expansion.animConfig.ease = new Ease(-2, .95, .48);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, .6f, false), .48);
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, .6f, true), .48);
        expansion.this$0.mirror = true;
        assertPeriod(registry, expansion, "setExpandProgress", List.of(false, 0f, true), .48);
        Blur blur = new Blur();
        blur.animConfig.ease = new Ease(-2, .95, .48);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, true), .48);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, false, false), .48);
        assertPeriod(registry, blur, "showBlur", List.of(false, false, true, false), .48);
        current[0] = current[0].withEnabled(false);
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), .48);
        current[0] = customSettings().withLayoutPlan("invalid");
        assertPeriod(registry, blur, "showBlur", List.of(false, true, true, false), .48);
    }

    @Test public void nativeReplacementDuringProceedIsNotOverwrittenByTheScopeCleanup() throws Throwable {
        Owner owner = new Owner();
        NativeConfig original = owner.animConfig;
        NativeConfig latest = new NativeConfig();
        latest.ease = new Ease(-2, .95, .16);
        NativeConfig[] queued = new NativeConfig[1];
        ControlCenterAnimationHooks.withFastConfig(owner, () -> {
            queued[0] = owner.animConfig;
            owner.animConfig = latest;
            return null;
        });
        assertSame("A native settings replacement must survive module cleanup", latest, owner.animConfig);
        assertEquals(.30, queued[0].ease.factors[1], .0001);
        assertEquals(.35, original.ease.factors[1], .0001);
    }

    @Test public void unavailableOemMethodDoesNotPreventOtherLocalHook() {
        Registry registry = new Registry();
        ControlCenterAnimationHooks.Result result = ControlCenterAnimationHooks.install(
                getClass().getClassLoader(), (name, loader) ->
                        ControlCenterAnimationHooks.BLUR_CLASS.equals(name) ? Blur.class : Object.class,
                registry, ControlCenterAnimationHooksTest::customSettings);
        assertEquals(1, result.installed);
        assertEquals(1, result.unavailable.size());
    }

    private static ControlCenterConfig customSettings() {
        ControlCenterLayoutPlan.Mode regular = new ControlCenterLayoutPlan.Mode(4,
                List.of(ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)));
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(regular,
                new ControlCenterLayoutPlan.Mode(4, List.of()));
        return ControlCenterConfig.defaults().withEnabled(true).withLayoutPlan(plan.encode());
    }

    private static void assertPeriod(Registry registry, Owner owner, String method,
            List<?> args, double expected) throws Throwable {
        NativeConfig original = owner.animConfig;
        Ease originalEase = original.ease;
        double originalResponse = (originalEase == null ? NativeConfig.sDefEase : originalEase).factors[1];
        AtomicInteger calls = new AtomicInteger();
        registry.call(owner, method, args, () -> {
            calls.incrementAndGet();
            invokeNative(owner, method, args);
            return null;
        });
        assertSame(original, owner.animConfig);
        assertSame("The owner's original ease field must be restored, including null", originalEase, original.ease);
        assertEquals(1, calls.get());
        NativeRequest queued = owner.pending.poll();
        boolean animated = method.equals("setExpandProgress") ? Boolean.TRUE.equals(args.get(2))
                : Boolean.TRUE.equals(args.get(1));
        NativeRequest consumed = animated ? queued : owner.direct;
        assertNotNull("The fixture must consume the config passed by the actual native method body", consumed);
        if (Math.abs(expected - originalResponse) > .0001) assertNotSame(original, consumed.config);
        else assertSame("Untuned paths retain the actual native configuration object", original, consumed.config);
        assertSame(original.listener, consumed.config.listener);
        // Folme reads the queued config after the hook has returned and restored the owner.
        consumed.consume();
        assertEquals(expected, consumed.response, .0001);
        assertEquals(method.equals("setExpandProgress") ? ((Number) args.get(1)).floatValue()
                : Boolean.TRUE.equals(args.get(0)) ? 1f : 0f, consumed.target, 0f);
    }

    private static void invokeNative(Owner owner, String name, List<?> arguments) throws Throwable {
        Method method = owner instanceof Expansion
                ? Expansion.class.getDeclaredMethod(name, boolean.class, float.class, boolean.class)
                : Blur.class.getDeclaredMethod(name, boolean.class, boolean.class, boolean.class, boolean.class);
        try { method.invoke(owner, arguments.toArray()); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    public static class Ease {
        public final int style;
        public final double[] factors;
        Ease(int style, double... factors) { this.style = style; this.factors = factors; }
    }

    public static class NativeConfig {
        public static final Ease sDefEase = new Ease(-2, 0.95, 0.35, 1.0);
        public Ease ease = sDefEase;
        public Object listener;
        public NativeConfig() { }
        public NativeConfig(NativeConfig source) { ease = source.ease; listener = source.listener; }
        public NativeConfig setEase(int style, float... values) {
            double[] factors = new double[values.length];
            for (int i = 0; i < values.length; i++) factors[i] = values[i];
            ease = new Ease(style, factors);
            return this;
        }
    }

    public static class Owner {
        public NativeConfig animConfig = new NativeConfig();
        final ArrayDeque<NativeRequest> pending = new ArrayDeque<>();
        NativeRequest direct;
        void submit(float target, boolean animated) {
            NativeRequest request = new NativeRequest(animConfig, target);
            if (animated) pending.add(request);
            else direct = request;
        }
    }
    public static class Parent {
        boolean mirror;
        public boolean getInMirror() { return mirror; }
    }
    public static class Expansion extends Owner {
        public final Parent this$0 = new Parent();
        public void setExpandProgress(boolean expanded, float value, boolean animated) { submit(value, animated); }
    }
    public static class Blur extends Owner {
        public void showBlur(boolean shown, boolean animated, boolean withBlur, boolean mirror) {
            submit(shown ? 1f : 0f, animated);
        }
    }
    private static final class NativeRequest {
        final NativeConfig config;
        final float target;
        double response;
        NativeRequest(NativeConfig config, float target) { this.config = config; this.target = target; }
        void consume() {
            response = (config.ease == null ? NativeConfig.sDefEase : config.ease).factors[1];
            if (config.listener instanceof Runnable listener) listener.run();
        }
    }
    private static class Registry implements ControlCenterPluginHookInstaller.Registrar {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        @Override public boolean install(String id, Method method, XposedInterface.Hooker hook) {
            hooks.put(method, hook);
            return true;
        }
        Object call(Object target, String name, List<?> arguments,
                ControlCenterAnimationHooks.Proceed proceed) throws Throwable {
            Method method = hooks.keySet().stream().filter(key -> key.getName().equals(name))
                    .findFirst().orElseThrow();
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getThisObject" -> target;
                        case "getArgs" -> arguments;
                        case "proceed" -> proceed.run();
                        default -> null;
                    });
            return hooks.get(method).intercept(chain);
        }
    }
}
