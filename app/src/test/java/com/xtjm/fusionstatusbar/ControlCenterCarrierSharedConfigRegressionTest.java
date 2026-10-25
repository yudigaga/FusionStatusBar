package com.xtjm.fusionstatusbar;

import android.view.View;
import com.xtjm.fusionstatusbar.ControlCenterCarrierMotionRegressionTest.AnimConfig;
import com.xtjm.fusionstatusbar.ControlCenterCarrierMotionRegressionTest.EaseManager;
import com.xtjm.fusionstatusbar.ControlCenterCarrierMotionRegressionTest.EaseStyle;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterCarrierSharedConfigRegressionTest {
    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void secondHolderInstallationUsesTheFirstRetainedSharedSetterHooks() throws Throwable {
        Dispatch dispatch = new Dispatch();
        dispatch.install(HolderA.class, AnimConfig.class, EaseStyle.class, EaseManager.class);
        Method setter = AnimConfig.class.getDeclaredMethod("setEase", EaseStyle.class);
        XposedInterface.Hooker firstSetter = dispatch.hooks.get(setter);
        assertNotNull(firstSetter);
        HolderA first = new HolderA(dispatch);
        ControlCenterMaterials.setGridCarrier(first.itemView, true);
        dispatch.visible(first, true, true, false);
        assertRetimed(first.config);

        dispatch.install(HolderB.class, AnimConfig.class, EaseStyle.class, EaseManager.class);
        assertSame("Repeated framework registration retains the first shared setter hook",
                firstSetter, dispatch.hooks.get(setter));
        Method firstVisible = HolderA.class.getDeclaredMethod("changeVisible",
                boolean.class, boolean.class, boolean.class);
        Method secondVisible = HolderB.class.getDeclaredMethod("changeVisible",
                boolean.class, boolean.class, boolean.class);
        assertNotEquals("The two holder boundaries must not share a declaring class",
                firstVisible.getDeclaringClass(), secondVisible.getDeclaringClass());

        HolderB second = new HolderB(dispatch);
        ControlCenterMaterials.setGridCarrier(second.itemView, true);
        dispatch.visible(second, true, true, false);
        assertEquals("The second native holder method executes once", 1, second.nativeCalls);
        assertRetimed(second.config);
        assertArrayEquals("The native shared profile is not modified", new float[] {.95f, .6f},
                second.nativeEase.factors, 0f);
    }

    @Test public void unrelatedConfigurationTypeInsideACarrierBoundaryKeepsItsNativeTiming() throws Throwable {
        Dispatch dispatch = new Dispatch();
        dispatch.install(HolderA.class, AnimConfig.class, EaseStyle.class, EaseManager.class);
        dispatch.install(HolderB.class, AnimConfig.class, EaseStyle.class, EaseManager.class);
        dispatch.install(OtherHolder.class, OtherConfig.class, OtherEase.class, OtherFactory.class);
        HolderB carrier = new HolderB(dispatch);
        ControlCenterMaterials.setGridCarrier(carrier.itemView, true);
        OtherConfig unrelated = new OtherConfig();
        OtherEase nativeEase = new OtherEase(-2, .7f, .65f);
        carrier.nested = () -> {
            dispatch.call(OtherConfig.class.getDeclaredMethod("setDelay", long.class), unrelated, 75L);
            dispatch.call(OtherConfig.class.getDeclaredMethod("setEase", OtherEase.class), unrelated, nativeEase);
        };
        dispatch.visible(carrier, true, true, false);
        assertEquals("An unrelated config class must not inherit another installation's delay cap", 75L,
                unrelated.delay);
        assertSame("An unrelated ease factory is not part of the active carrier context", nativeEase,
                unrelated.ease);
        assertArrayEquals(new float[] {.7f, .65f}, unrelated.ease.factors, 0f);
    }

    private static void assertRetimed(AnimConfig config) {
        assertNotNull(config);
        assertEquals("The active carrier removes the native stagger through the retained setter", 0L,
                config.delay);
        assertEquals(-2, config.ease.style);
        assertTrue("The active carrier's shared setter caps the spring response at .3",
                config.ease.factors[1] <= .30001f);
    }

    private static ControlCenterConfig settings() {
        ControlCenterLayoutPlan.Mode regular = new ControlCenterLayoutPlan.Mode(4,
                List.of(ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)));
        return ControlCenterConfig.defaults().withEnabled(true).withLayoutPlan(ControlCenterLayoutPlan.of(
                regular, new ControlCenterLayoutPlan.Mode(4, List.of())).encode());
    }

    /** Keeps the first Method/id hook exactly as RuntimeHookRegistry does in production. */
    private static final class Dispatch implements ControlCenterPluginHookInstaller.Registrar {
        final RuntimeHookRegistry<XposedInterface.Hooker> retained = new RuntimeHookRegistry<>();
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final ControlCenterConfig current = settings();

        void install(Class<?> holder, Class<?> config, Class<?> ease, Class<?> factory) {
            ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };
            ControlCenterCarrierMotionHooks.Result result = ControlCenterCarrierMotionHooks.install(loader,
                    (name, owner) -> switch (name) {
                        case ControlCenterCarrierMotionHooks.HOLDER_CLASS -> holder;
                        case "miuix.animation.base.AnimConfig" -> config;
                        case "miuix.animation.utils.EaseManager$EaseStyle" -> ease;
                        case "miuix.animation.utils.EaseManager" -> factory;
                        default -> null;
                    }, this, () -> current);
            assertTrue("The fixture installs both setters and its own holder boundaries: " + result.unavailable,
                    result.installed >= 4 && result.unavailable.isEmpty());
        }

        @Override public boolean install(String id, Method method, XposedInterface.Hooker hook) {
            try {
                return retained.install(method, id, () -> {
                    hooks.put(method, hook);
                    return hook;
                });
            } catch (Throwable failure) { throw new AssertionError(failure); }
        }

        void visible(Object holder, boolean shown, boolean animated, boolean mirror) throws Throwable {
            call(holder.getClass().getDeclaredMethod("changeVisible",
                    boolean.class, boolean.class, boolean.class), holder, shown, animated, mirror);
        }

        void configure(AnimConfig config, EaseStyle ease) throws Throwable {
            call(AnimConfig.class.getDeclaredMethod("setDelay", long.class), config, 90L);
            call(AnimConfig.class.getDeclaredMethod("setEase", EaseStyle.class), config, ease);
        }

        Object call(Method method, Object owner, Object... arguments) throws Throwable {
            XposedInterface.Hooker hook = hooks.get(method);
            if (hook == null) return original(method, owner, arguments);
            List<?> args = List.of(arguments);
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, values) -> switch (called.getName()) {
                        case "getThisObject" -> owner;
                        case "getArgs" -> args;
                        case "proceed" -> original(method, owner,
                                values != null && values.length > 0 && values[0] instanceof Object[] replacement
                                        ? replacement : arguments);
                        default -> null;
                    });
            return hook.intercept(chain);
        }

        private static Object original(Method method, Object owner, Object[] args) throws Throwable {
            try { return method.invoke(owner, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }

    public static final class HolderA {
        public final View itemView = new View(RuntimeEnvironment.getApplication());
        public boolean mirrorShowing;
        final Dispatch dispatch;
        final EaseStyle nativeEase = new EaseStyle(-2, .95f, .6f);
        AnimConfig config;
        HolderA(Dispatch dispatch) { this.dispatch = dispatch; }
        public void changeVisible(boolean shown, boolean animated, boolean mirror) throws Throwable {
            mirrorShowing = mirror;
            if (!animated) return;
            config = new AnimConfig();
            dispatch.configure(config, nativeEase);
        }
        public void changeExpand(float first, float second, float speed, boolean released, boolean animated) { }
    }

    /** A separate declaration, rather than a subclass inheriting HolderA's Method objects. */
    public static final class HolderB {
        public final View itemView = new View(RuntimeEnvironment.getApplication());
        public boolean mirrorShowing;
        final Dispatch dispatch;
        final EaseStyle nativeEase = new EaseStyle(-2, .95f, .6f);
        AnimConfig config;
        int nativeCalls;
        Nested nested;
        HolderB(Dispatch dispatch) { this.dispatch = dispatch; }
        public void changeVisible(boolean shown, boolean animated, boolean mirror) throws Throwable {
            nativeCalls++;
            mirrorShowing = mirror;
            if (!animated) return;
            if (nested != null) nested.run();
            config = new AnimConfig();
            dispatch.configure(config, nativeEase);
        }
        public void changeExpand(float first, float second, float speed, boolean released, boolean animated) { }
    }

    public static final class OtherEase {
        public final int style;
        public final float[] factors;
        OtherEase(int style, float... factors) { this.style = style; this.factors = factors.clone(); }
    }

    public static final class OtherFactory {
        public static OtherEase getStyle(int style, float... factors) { return new OtherEase(style, factors); }
    }

    public static final class OtherConfig {
        long delay;
        OtherEase ease;
        public OtherConfig setDelay(long value) { delay = value; return this; }
        public OtherConfig setEase(OtherEase value) { ease = value; return this; }
    }

    public static final class OtherHolder {
        public final View itemView = new View(RuntimeEnvironment.getApplication());
        public boolean mirrorShowing;
        public void changeVisible(boolean shown, boolean animated, boolean mirror) { }
        public void changeExpand(float first, float second, float speed, boolean released, boolean animated) { }
    }

    @FunctionalInterface private interface Nested { void run() throws Throwable; }
}
