package com.xtjm.fusionstatusbar;

import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class ControlCenterCarrierMotionRegressionTest {
    private static final String HOLDER_CLASS =
            "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder";
    private static final EaseStyle NOTIFICATION_FOLLOW = new EaseStyle(-2, .9f, .3f);
    private static final float NOTIFICATION_HIDE_RESPONSE = .2f;
    private static final EaseStyle NATIVE_SHOW = new EaseStyle(-2, .95f, .6f);
    private static final EaseStyle NATIVE_HIDE = new EaseStyle(-2, .9f, .25f);
    private static final EaseStyle NATIVE_RELEASE = new EaseStyle(-2, .7f, .45f);
    private static final EaseStyle NATIVE_MIRROR = new EaseStyle(-2, .95f, .45f);
    private static final FloatProperty ALPHA = new FloatProperty("expand_alpha");
    private static final FloatProperty SCALE = new FloatProperty("expand_scale");
    private static final FloatProperty SHRINK_X = new FloatProperty("shrink_x");
    private static final FloatProperty SHRINK_Y = new FloatProperty("shrink_y");
    private static final FloatProperty TRANS_Y = new FloatProperty("expand_trans_y");
    private static final FloatProperty SHRINK_TRANS_Y = new FloatProperty("shrink_expand_trans_y");

    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void queuedCarrierOpeningHasNoTileStaggerOrSlowerPropertySpecials() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        environment.visible(carrier, true, true, false);
        assertEquals("The host method must run exactly once", 1, carrier.nativeCalls);
        assertTrue("Folme consumption must occur after the hook scope returns", environment.queue.consumed.isEmpty());
        environment.queue.drain();
        Consumed queued = environment.queue.consumed.get(0);
        assertEquals("The grid carrier must start as one surface without per-tile stagger", 0L, queued.delay);
        assertNoSlowerThan(queued, NOTIFICATION_FOLLOW.factors[1]);
        assertEquals(1f, queued.targets.get(ALPHA.getName()), 0f);
        assertEquals(1f, queued.targets.get(SCALE.getName()), 0f);
    }

    @Test public void queuedCarrierCollapseAndReleaseDoNotKeepTheSlowerNativeTail() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        environment.visible(carrier, false, true, false);
        environment.expand(carrier, 80f, 120f, 35f, true, true);
        environment.queue.drain();
        Consumed close = environment.queue.consumed.get(0), release = environment.queue.consumed.get(1);
        assertEquals(0L, close.delay);
        assertNoSlowerThan(close, NOTIFICATION_HIDE_RESPONSE);
        assertNoSlowerThan(release, NOTIFICATION_FOLLOW.factors[1]);
        assertEquals(0f, close.targets.get(ALPHA.getName()), 0f);
        assertEquals(80f, release.targets.get(TRANS_Y.getName()), 0f);
        assertEquals(120f, release.targets.get(SHRINK_TRANS_Y.getName()), 0f);
    }

    @Test public void ordinaryHoldersAndDisabledOrInvalidCustomPlansKeepTheirNativeProfiles() throws Throwable {
        Environment environment = new Environment();
        environment.visible(environment.holder(false), true, true, false);
        Holder carrier = environment.holder(true);
        environment.current = environment.current.withEnabled(false);
        environment.visible(carrier, true, true, false);
        environment.current = customSettings().withLayoutPlan("invalid");
        environment.visible(carrier, true, true, false);
        environment.current = ControlCenterConfig.defaults().withEnabled(true);
        environment.visible(carrier, true, true, false);
        environment.queue.drain();
        assertEquals(4, environment.queue.consumed.size());
        for (Consumed request : environment.queue.consumed) assertNativeShow(request);
    }

    @Test public void directDragAndMirrorAnimationsKeepNativeTargetsAndTimings() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        environment.visible(carrier, true, false, false);
        assertEquals(1, environment.queue.direct.size());
        assertEquals(1f, environment.queue.direct.get(0).get(ALPHA.getName()), 0f);
        environment.expand(carrier, 23f, 45f, 12f, true, false);
        assertEquals(2, environment.queue.direct.size());
        assertEquals(23f, environment.queue.direct.get(1).get(TRANS_Y.getName()), 0f);
        assertTrue("Direct gesture updates must not create a delayed animation", environment.queue.pending.isEmpty());
        environment.visible(carrier, true, true, true);
        carrier.mirrorShowing = true;
        environment.expand(carrier, 9f, 14f, 0f, true, true);
        environment.queue.drain();
        Consumed mirror = environment.queue.consumed.get(0), mirrorRelease = environment.queue.consumed.get(1);
        assertEquals(100L, mirror.delay);
        assertArrayEquals(NATIVE_MIRROR.factors, mirror.ease.factors, 0f);
        assertArrayEquals(NATIVE_RELEASE.factors, mirrorRelease.ease.factors, 0f);
    }

    @Test public void animatedTrackingKeepsTheNativeFollowProfileWhileClosedReleaseSettlesFaster() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        carrier.followEase = new EaseStyle(-2, .7f, .48f);
        environment.expand(carrier, 50f, 80f, 12f, false, true);
        environment.expand(carrier, 0f, 0f, 12f, true, true);
        environment.queue.drain();
        assertSame("Animated tracking is still controlled by the OEM follow profile",
                carrier.followEase, environment.queue.consumed.get(0).ease);
        assertNoSlowerThan(environment.queue.consumed.get(1), NOTIFICATION_HIDE_RESPONSE);
        assertEquals(0f, environment.queue.consumed.get(1).targets.get(TRANS_Y.getName()), 0f);
    }

    @Test public void queuedCarrierConfigRetainsTargetsMetadataAndCompletionOutsideTheHookScope() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        environment.visible(carrier, true, true, false);
        Pending queued = environment.queue.pending.peek();
        assertNotNull(queued);
        assertSame(carrier.token, queued.config.tag);
        assertEquals(37L, queued.config.flags);
        assertEquals(42f, queued.config.fromSpeed, 0f);
        assertSame(carrier.completion, queued.config.listeners.get(0));
        assertEquals("Completion must not run synchronously in the animation hook", 0, carrier.completions);
        // A config constructed outside the holder scope must not inherit carrier timing.
        AnimConfig unrelated = new AnimConfig();
        environment.ease(unrelated, NATIVE_SHOW);
        environment.delay(unrelated, 90L);
        environment.queue.to(unrelated, Map.of(ALPHA.getName(), .5f));
        environment.queue.drain();
        assertEquals(1, carrier.completions);
        environment.queue.drain();
        assertEquals("The original native completion listener must run once", 1, carrier.completions);
        assertNoSlowerThan(environment.queue.consumed.get(0), NOTIFICATION_FOLLOW.factors[1]);
        assertNativeShow(environment.queue.consumed.get(1));
        assertArrayEquals("The static OEM profile must not be mutated", new float[] {.95f, .6f}, NATIVE_SHOW.factors, 0f);
    }

    @Test public void nativeFailurePropagatesOnceAndRestoresTimingScopeForTheNextHolder() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        IllegalStateException nativeFailure = new IllegalStateException("native_failure");
        carrier.failure = nativeFailure;
        try {
            environment.visible(carrier, true, true, false);
            fail("The original failure must propagate");
        } catch (IllegalStateException failure) { assertSame(nativeFailure, failure); }
        assertEquals(1, carrier.nativeCalls);
        environment.visible(environment.holder(false), true, true, false);
        environment.queue.drain();
        assertNativeShow(environment.queue.consumed.get(1));
    }

    @Test public void nestedOrdinaryHolderDoesNotInheritTheCarriersTimingScope() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        carrier.nested = environment.holder(false);
        environment.visible(carrier, true, true, false);
        environment.queue.drain();
        assertNativeShow(environment.queue.consumed.get(0));
        Consumed outer = environment.queue.consumed.get(1);
        assertEquals(0L, outer.delay);
        assertNoSlowerThan(outer, NOTIFICATION_FOLLOW.factors[1]);
    }

    @Test public void alreadyFasterOemProfilesRemainIntactIncludingTheirDampingAndExtraFactors() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        EaseStyle faster = new EaseStyle(-2, .7f, .16f, 9f);
        carrier.showEase = faster;
        environment.visible(carrier, true, true, false);
        environment.queue.drain();
        Consumed queued = environment.queue.consumed.get(0);
        assertSame("A faster OEM profile must not be rebuilt or have its damping changed", faster, queued.ease);
        for (EaseStyle special : queued.specials.values()) assertSame(faster, special);
        assertEquals("Removing tile stagger is independent of preserving a faster profile", 0L, queued.delay);
        assertArrayEquals(new float[] {.7f, .16f, 9f}, faster.factors, 0f);
    }

    @Test public void aRetimedReleasePreservesExtraFactorsAndOnlyRaisesInsufficientDamping() throws Throwable {
        Environment environment = new Environment();
        Holder carrier = environment.holder(true);
        EaseStyle slower = new EaseStyle(-2, .7f, .45f, 9f);
        carrier.releaseTransYEase = slower;
        environment.expand(carrier, 10f, 20f, 35f, true, true);
        environment.queue.drain();
        Consumed queued = environment.queue.consumed.get(0);
        assertNoSlowerThan(queued, NOTIFICATION_FOLLOW.factors[1]);
        assertEquals(.9f, queued.ease.factors[0], .00001f);
        assertEquals(9f, queued.ease.factors[2], 0f);
        assertArrayEquals("Retiming must not mutate the OEM's shared ease object",
                new float[] {.7f, .45f, 9f}, slower.factors, 0f);
    }

    private static void assertNoSlowerThan(Consumed queued, float response) {
        assertEquals(-2, queued.ease.style);
        assertTrue("The consumed spring response must not retain the slower tile profile: " + queued.ease.factors[1],
                queued.ease.factors[1] <= response + .00001f);
        for (Map.Entry<String, Float> property : queued.targets.entrySet()) {
            EaseStyle effective = queued.specials.getOrDefault(property.getKey(), queued.ease);
            assertTrue("A native special must not override carrier timing for " + property.getKey()
                            + ": " + effective.factors[1], effective.factors[1] <= response + .00001f);
        }
    }

    private static void assertNativeShow(Consumed queued) {
        assertEquals("Unrelated native animation retains its stagger", 90L, queued.delay);
        assertArrayEquals(NATIVE_SHOW.factors, queued.ease.factors, 0f);
        for (EaseStyle special : queued.specials.values()) assertArrayEquals(NATIVE_SHOW.factors, special.factors, 0f);
    }

    private static ControlCenterConfig customSettings() {
        ControlCenterLayoutPlan.Mode regular = new ControlCenterLayoutPlan.Mode(4,
                List.of(ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)));
        return ControlCenterConfig.defaults().withEnabled(true).withLayoutPlan(ControlCenterLayoutPlan.of(
                regular, new ControlCenterLayoutPlan.Mode(4, List.of())).encode());
    }

    public static final class EaseStyle {
        public final int style;
        public final float[] factors;
        public EaseStyle(int style, float... factors) { this.style = style; this.factors = factors.clone(); }
    }

    public static final class EaseManager {
        public static EaseStyle getStyle(int style, float... factors) { return new EaseStyle(style, factors); }
    }

    public static final class FloatProperty {
        private final String name;
        public FloatProperty(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static final class AnimConfig {
        public EaseStyle ease;
        public long delay, flags = 37L;
        public float fromSpeed = 42f;
        public Object tag;
        public final List<Runnable> listeners = new ArrayList<>();
        public final Map<String, EaseStyle> specials = new LinkedHashMap<>();
        public AnimConfig setEase(EaseStyle ease) { this.ease = ease; return this; }
        public AnimConfig setEase(int style, float... factors) { return setEase(new EaseStyle(style, factors)); }
        public AnimConfig setDelay(long delay) { this.delay = delay; return this; }
        public AnimConfig setSpecial(FloatProperty property, EaseStyle ease, float... values) {
            specials.put(property.getName(), ease);
            return this;
        }
    }

    /** Host-like method bodies retain the OEM new-config/setters/to-versus-setTo chain. */
    public static final class Holder {
        public final View itemView = new View(RuntimeEnvironment.getApplication());
        public boolean mirrorShowing;
        public final Object token = new Object();
        public final Runnable completion = () -> this.completions++;
        public EaseStyle showEase = NATIVE_SHOW, hideEase = NATIVE_HIDE, releaseTransYEase = NATIVE_RELEASE;
        public EaseStyle followEase = NOTIFICATION_FOLLOW;
        private final Environment environment;
        int nativeCalls, completions;
        Holder nested;
        RuntimeException failure;
        Holder(Environment environment) { this.environment = environment; }
        public boolean getMirrorStarted() { return mirrorShowing; }
        public boolean getMirrorShowing() { return mirrorShowing; }
        public void changeVisible(boolean shown, boolean animated, boolean mirror) throws Throwable {
            nativeCalls++;
            mirrorShowing = mirror;
            Map<String, Float> targets = Map.of(ALPHA.getName(), shown ? 1f : 0f, SCALE.getName(), shown ? 1f : 0f,
                    SHRINK_X.getName(), shown ? 1f : 0f, SHRINK_Y.getName(), shown ? 1f : 0f);
            if (!animated) { environment.queue.setTo(targets); return; }
            if (nested != null) environment.visible(nested, shown, true, false);
            AnimConfig config = config();
            environment.delay(config, mirror ? 100L : shown ? 90L : 0L);
            EaseStyle nativeEase = mirror ? NATIVE_MIRROR : shown ? showEase : hideEase;
            environment.ease(config, nativeEase);
            environment.special(config, SCALE, nativeEase);
            environment.special(config, SHRINK_X, nativeEase);
            environment.special(config, SHRINK_Y, nativeEase);
            environment.queue.to(config, targets);
            if (failure != null) throw failure;
        }
        public void changeExpand(float translation, float shrinkTranslation, float speed,
                boolean released, boolean animated) throws Throwable {
            nativeCalls++;
            Map<String, Float> targets = Map.of(TRANS_Y.getName(), translation, SHRINK_TRANS_Y.getName(), shrinkTranslation);
            if (!animated) { environment.queue.setTo(targets); return; }
            AnimConfig config = config();
            config.fromSpeed = speed;
            environment.ease(config, released ? releaseTransYEase : followEase);
            environment.queue.to(config, targets);
            if (failure != null) throw failure;
        }
        private AnimConfig config() {
            AnimConfig config = new AnimConfig();
            config.tag = token;
            config.listeners.add(completion);
            return config;
        }
    }

    private record Pending(AnimConfig config, Map<String, Float> targets) { }
    private record Consumed(long delay, EaseStyle ease, Map<String, EaseStyle> specials, Map<String, Float> targets) { }

    private static final class QueuedFolme {
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        final List<Consumed> consumed = new ArrayList<>();
        final List<Map<String, Float>> direct = new ArrayList<>();
        void to(AnimConfig config, Map<String, Float> targets) { pending.add(new Pending(config, Map.copyOf(targets))); }
        void setTo(Map<String, Float> targets) { direct.add(Map.copyOf(targets)); }
        void drain() {
            while (!pending.isEmpty()) {
                Pending request = pending.remove();
                AnimConfig config = request.config;
                consumed.add(new Consumed(config.delay, config.ease, Map.copyOf(config.specials), request.targets));
                for (Runnable listener : config.listeners) listener.run();
            }
        }
    }

    private static final class Environment implements ControlCenterPluginHookInstaller.Registrar {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final QueuedFolme queue = new QueuedFolme();
        ControlCenterConfig current = customSettings();
        Environment() {
            ControlCenterAnimationHooks.install(getClass().getClassLoader(), (name, loader) -> switch (name) {
                case HOLDER_CLASS -> Holder.class;
                case "miuix.animation.base.AnimConfig" -> AnimConfig.class;
                case "miuix.animation.utils.EaseManager" -> EaseManager.class;
                case "miuix.animation.utils.EaseManager$EaseStyle" -> EaseStyle.class;
                case "miuix.animation.property.FloatProperty" -> FloatProperty.class;
                case ControlCenterAnimationHooks.EXPANSION_CLASS -> Expansion.class;
                case ControlCenterAnimationHooks.BLUR_CLASS -> Blur.class;
                default -> null;
            }, this, () -> current);
        }
        Holder holder(boolean carrier) {
            Holder holder = new Holder(this);
            ControlCenterMaterials.setGridCarrier(holder.itemView, carrier);
            return holder;
        }
        @Override public boolean install(String id, Method method, XposedInterface.Hooker hook) {
            hooks.put(method, hook);
            return true;
        }
        void visible(Holder holder, boolean shown, boolean animated, boolean mirror) throws Throwable {
            Method method = Holder.class.getDeclaredMethod("changeVisible", boolean.class, boolean.class, boolean.class);
            call(method, holder, List.of(shown, animated, mirror), args -> {
                holder.changeVisible((Boolean) args[0], (Boolean) args[1], (Boolean) args[2]);
                return null;
            });
        }
        void expand(Holder holder, float translation, float shrinkTranslation, float speed,
                boolean released, boolean animated) throws Throwable {
            Method method = Holder.class.getDeclaredMethod("changeExpand", float.class, float.class, float.class,
                    boolean.class, boolean.class);
            call(method, holder, List.of(translation, shrinkTranslation, speed, released, animated), args -> {
                holder.changeExpand((Float) args[0], (Float) args[1], (Float) args[2], (Boolean) args[3], (Boolean) args[4]);
                return null;
            });
        }
        void ease(AnimConfig config, EaseStyle ease) throws Throwable {
            Method method = AnimConfig.class.getDeclaredMethod("setEase", EaseStyle.class);
            call(method, config, List.of(ease), args -> config.setEase((EaseStyle) args[0]));
        }
        void delay(AnimConfig config, long delay) throws Throwable {
            Method method = AnimConfig.class.getDeclaredMethod("setDelay", long.class);
            call(method, config, List.of(delay), args -> config.setDelay((Long) args[0]));
        }
        void special(AnimConfig config, FloatProperty property, EaseStyle ease) throws Throwable {
            Method method = AnimConfig.class.getDeclaredMethod("setSpecial", FloatProperty.class, EaseStyle.class, float[].class);
            call(method, config, List.of(property, ease, new float[0]), args ->
                    config.setSpecial((FloatProperty) args[0], (EaseStyle) args[1], (float[]) args[2]));
        }
        Object call(Method method, Object owner, List<?> arguments, NativeCall proceed) throws Throwable {
            XposedInterface.Hooker hook = hooks.get(method);
            if (hook == null) return proceed.run(arguments.toArray());
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {XposedInterface.Chain.class}, (proxy, called, args) -> switch (called.getName()) {
                        case "getThisObject" -> owner;
                        case "getArgs" -> arguments;
                        case "proceed" -> proceed.run(args != null && args.length > 0 && args[0] instanceof Object[] override
                                ? override : arguments.toArray());
                        default -> null;
                    });
            return hook.intercept(chain);
        }
    }

    public static final class Expansion {
        public void setExpandProgress(boolean expanded, float value, boolean animated) { }
    }
    public static final class Blur {
        public void showBlur(boolean shown, boolean animated, boolean withBlur, boolean mirror) { }
    }
    @FunctionalInterface private interface NativeCall { Object run(Object[] args) throws Throwable; }
}
