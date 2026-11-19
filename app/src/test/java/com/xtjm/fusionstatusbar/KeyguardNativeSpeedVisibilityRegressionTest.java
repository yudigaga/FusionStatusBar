package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView;
import com.android.systemui.statusbar.views.MiuiStatusBatteryContainer;
import com.android.systemui.statusbar.views.NetworkSpeedView;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises real hook entry points; the fixture does not identify any screenshot's text. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class KeyguardNativeSpeedVisibilityRegressionTest {
    private final Map<Field, Object> previousFields = new LinkedHashMap<>();
    private Map<View, Integer> previousNativeVisibility;
    private Map<String, RuntimeApplicationPolicy.Result> previousFeatureStates;
    private ActivityController<Activity> activityController;
    private FrameLayout window;
    private FusionConfig disabled;

    @Before public void isolateRuntimeConfigAndRoots() throws Exception {
        // Isolate provider IO while retaining production config diffing and both refresh queues.
        replaceField("configLifecycle", isolatedConfigLifecycle());
        disabled = FusionConfig.defaults().withDoubleRow(false)
                .withControlCenter(FusionConfig.defaults().controlCenter.withEnabled(false));
        replaceField("currentConfig", disabled);
        replaceField("currentPlan", StatusBarLayoutPlan.from(disabled));
        replaceField("configApplied", true);
        replaceField("currentStatusRoot", new WeakReference<ViewGroup>(null));
        replaceField("currentKeyguardStatusRoot", new WeakReference<ViewGroup>(null));
        previousNativeVisibility = new LinkedHashMap<>(nativeVisibility());
        nativeVisibility().clear();
        previousFeatureStates = new LinkedHashMap<>(featureStates());
        Field menuConfig = SystemMenuHooks.class.getDeclaredField("config");
        menuConfig.setAccessible(true);
        previousFields.put(menuConfig, menuConfig.get(null));
        activityController = Robolectric.buildActivity(Activity.class).setup();
        shadowOf(activityController.get().getSystemService(PowerManager.class)).setIsInteractive(true);
        window = new FrameLayout(activityController.get());
        activityController.get().setContentView(window);
    }

    @After public void restoreRuntimeState() throws Exception {
        ShadowLooper.idleMainLooper();
        if (activityController != null) activityController.pause().stop().destroy();
        TelemetrySampler.configure(RuntimeEnvironment.getApplication(), TelemetryConfig.defaults());
        for (Map.Entry<Field, Object> saved : previousFields.entrySet()) {
            saved.getKey().set(null, saved.getValue());
        }
        nativeVisibility().clear();
        nativeVisibility().putAll(previousNativeVisibility);
        featureStates().clear();
        featureStates().putAll(previousFeatureStates);
    }

    @Test public void enablingHidesBothRootsAndDisablingRestoresTheirDistinctHostStates()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);

        applyConfig(true);
        assertNotNull("The phone fixture must mount the real module readout", readout(phone.root));
        assertNotNull("The keyguard fixture must mount the real module readout", readout(keyguard.root));
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertSame(phone.root, root("currentStatusRoot"));
        assertSame(keyguard.root, root("currentKeyguardStatusRoot"));

        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void attachingWhileEnabledSuppressesKeyguardWithoutReplacingPhoneRoot()
            throws Exception {
        applyConfig(true);
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.VISIBLE);
        attach(phone);
        attach(keyguard);

        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertSame(phone.root, root("currentStatusRoot"));
        assertSame(keyguard.root, root("currentKeyguardStatusRoot"));
    }

    @Test public void controllerShowingKeyguardSpeedIsSuppressedUntilTheOverrideIsDisabled()
            throws Exception {
        Fixture keyguard = fixture(true, View.GONE);
        attach(keyguard);
        applyConfig(true);

        controllerVisibility(keyguard.speed, true);
        assertEquals(View.GONE, keyguard.speed.getVisibility());

        applyConfig(false);
        assertEquals("Restore the latest host controller request", View.VISIBLE,
                keyguard.speed.getVisibility());
    }

    @Test public void controllerHidingPhoneSpeedRemainsHiddenAfterTheOverrideIsDisabled()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        attach(phone);
        applyConfig(true);

        controllerVisibility(phone.speed, false);
        applyConfig(false);

        assertEquals(View.GONE, phone.speed.getVisibility());
    }

    @Test public void repeatedControllerShowDoesNotReplaceTheSavedHostVisibility()
            throws Exception {
        Fixture keyguard = fixture(true, View.VISIBLE);
        attach(keyguard);
        applyConfig(true);

        controllerVisibility(keyguard.speed, true);
        controllerVisibility(keyguard.speed, true);
        assertEquals(View.GONE, keyguard.speed.getVisibility());

        applyConfig(false);

        assertEquals("A host no-op must not save the module's GONE as the host state",
                View.VISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void repeatedKeyguardLayoutDoesNotReplaceTheSavedHostVisibility()
            throws Exception {
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(keyguard);
        applyConfig(true);
        invokeHook("keyguardLayout", keyguard.root, null);
        invokeHook("keyguardLayout", keyguard.root, null);

        applyConfig(false);

        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void reattachingKeyguardFindsItsReplacementSpeedView() throws Exception {
        Fixture keyguard = fixture(true, View.VISIBLE);
        attach(keyguard);
        applyConfig(true);
        NetworkSpeedView previous = keyguard.speed;
        window.removeView(keyguard.root);
        removeFromParent(keyguard.speed);
        keyguard.speed = new NetworkSpeedView(window.getContext());
        keyguard.speed.setVisibility(View.VISIBLE);
        keyguard.root.addView(keyguard.speed);
        window.addView(keyguard.root);

        attach(keyguard);

        assertNull("A replaced native speed must not be reattached by the old layout",
                previous.getParent());
        assertEquals(View.VISIBLE, previous.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        applyConfig(false);
        assertNull(previous.getParent());
        assertEquals(View.VISIBLE, previous.getVisibility());
        assertEquals(View.VISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void refreshingRestoresEveryNativeSpeedViewThatHasLeftTheRoot()
            throws Exception {
        Fixture keyguard = fixture(true, View.VISIBLE);
        NetworkSpeedView other = new NetworkSpeedView(window.getContext());
        other.setVisibilityByController(true);
        other.setVisibility(View.INVISIBLE);
        keyguard.root.addView(other);
        attach(keyguard);
        applyConfig(true);
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertEquals(View.GONE, other.getVisibility());
        removeFromParent(keyguard.speed);
        removeFromParent(other);
        window.addView(keyguard.speed);
        window.addView(other);

        invokeHook("keyguardLayout", keyguard.root, null);

        assertEquals(View.VISIBLE, keyguard.speed.getVisibility());
        assertEquals(View.INVISIBLE, other.getVisibility());
        assertSame(window, keyguard.speed.getParent());
        assertSame(window, other.getParent());
    }

    @Test public void refreshingPhoneRestoresAManagedSpeedMovedOutsideItsRoot()
            throws Exception {
        Fixture phone = fixture(false, View.INVISIBLE);
        attach(phone);
        applyConfig(true);
        assertEquals(View.GONE, phone.speed.getVisibility());
        removeFromParent(phone.speed);
        window.addView(phone.speed);

        attach(phone);

        assertSame(window, phone.speed.getParent());
        assertEquals(View.INVISIBLE, phone.speed.getVisibility());
        applyConfig(false);
        assertSame(window, phone.speed.getParent());
        assertEquals(View.INVISIBLE, phone.speed.getVisibility());
    }

    @Test public void refreshingRestoresParentlessManagedSpeedsInBothRoots()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);
        applyConfig(true);
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        removeFromParent(phone.speed);
        removeFromParent(keyguard.speed);

        attach(phone);
        invokeHook("keyguardLayout", keyguard.root, null);

        assertNull(phone.speed.getParent());
        assertNull(keyguard.speed.getParent());
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void movingManagedSpeedsWithinTheirRootsRetainsLayoutOwnership()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);
        applyConfig(true);
        ViewGroup phoneSlot = (ViewGroup) phone.speed.getParent();
        ViewGroup keyguardSlot = (ViewGroup) keyguard.speed.getParent();
        removeFromParent(phone.speed);
        removeFromParent(keyguard.speed);
        phone.root.addView(phone.speed);
        keyguard.root.addView(keyguard.speed);

        attach(phone);
        invokeHook("keyguardLayout", keyguard.root, null);

        assertSame(phoneSlot, phone.speed.getParent());
        assertSame(keyguardSlot, keyguard.speed.getParent());
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void refreshingDetachedRootsRetainsTheirManagedSpeedsAcrossReattach()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);
        applyConfig(true);
        ViewGroup phoneSlot = (ViewGroup) phone.speed.getParent();
        ViewGroup keyguardSlot = (ViewGroup) keyguard.speed.getParent();
        window.removeView(phone.root);
        window.removeView(keyguard.root);

        FusionConfig network = disabled.withTelemetry(disabled.telemetry
                .withEnabled(TelemetryConfig.NET_SPEED, true));
        invokeConfig(network.withSystemIconScale(90));

        assertSame(phoneSlot, phone.speed.getParent());
        assertSame(keyguardSlot, keyguard.speed.getParent());
        removeFromParent(phone.speed);
        removeFromParent(keyguard.speed);
        phone.root.addView(phone.speed);
        keyguard.root.addView(keyguard.speed);
        window.addView(phone.root);
        window.addView(keyguard.root);
        attach(phone);
        attach(keyguard);
        assertSame(phoneSlot, phone.speed.getParent());
        assertSame(keyguardSlot, keyguard.speed.getParent());
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void refreshingTheOldRootDoesNotRevealASpeedTransferredToKeyguard()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);
        applyConfig(true);
        removeFromParent(phone.speed);
        keyguard.root.addView(phone.speed);
        controllerVisibility(phone.speed, true);
        assertEquals(View.GONE, phone.speed.getVisibility());

        attach(phone);

        assertSame(keyguard.root, phone.speed.getParent());
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        applyConfig(false);
        assertSame(keyguard.root, phone.speed.getParent());
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void releasingTheOldRootDoesNotRevealASpeedTransferredToPhone()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.INVISIBLE);
        attach(phone);
        attach(keyguard);
        applyConfig(true);
        removeFromParent(keyguard.speed);
        phone.root.addView(keyguard.speed);
        attach(phone);
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        window.removeView(keyguard.root);
        Fixture replacement = fixture(true, View.VISIBLE);

        attach(replacement);

        assertSame(phone.root, keyguard.speed.getParent());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, replacement.speed.getVisibility());
        applyConfig(false);
        assertSame(phone.root, keyguard.speed.getParent());
        assertEquals(View.INVISIBLE, keyguard.speed.getVisibility());
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.VISIBLE, replacement.speed.getVisibility());
    }

    @Test public void disablingRestoresEveryNativeSpeedViewThatHasLeftTheRoot()
            throws Exception {
        Fixture keyguard = fixture(true, View.VISIBLE);
        NetworkSpeedView other = new NetworkSpeedView(window.getContext());
        other.setVisibilityByController(true);
        other.setVisibility(View.INVISIBLE);
        keyguard.root.addView(other);
        attach(keyguard);
        applyConfig(true);
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertEquals(View.GONE, other.getVisibility());
        removeFromParent(keyguard.speed);
        removeFromParent(other);
        window.addView(keyguard.speed);
        window.addView(other);

        applyConfig(false);

        assertEquals(View.VISIBLE, keyguard.speed.getVisibility());
        assertEquals(View.INVISIBLE, other.getVisibility());
        assertSame(window, keyguard.speed.getParent());
        assertSame(window, other.getParent());
    }

    @Test public void rootsAndControllerCallbacksDoNotHideAnUnrelatedSpeedView()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.VISIBLE);
        NetworkSpeedView unrelated = new NetworkSpeedView(window.getContext());
        window.addView(unrelated);
        attach(phone);
        attach(keyguard);
        applyConfig(true);

        controllerVisibility(unrelated, true);

        assertEquals(View.VISIBLE, unrelated.getVisibility());
        applyConfig(false);
        assertEquals(View.VISIBLE, unrelated.getVisibility());
    }

    @Test public void reattachingAfterAnInitialDiscoveryMissSuppressesTheNewViewImmediately()
            throws Exception {
        applyConfig(true);
        Fixture phone = fixture(false, View.VISIBLE);
        removeFromParent(phone.speed);
        attach(phone);
        phone.root.addView(phone.speed);

        attach(phone);

        assertEquals("A reattach cannot retain the earlier missing-view cache", View.GONE,
                phone.speed.getVisibility());
    }

    @Test public void aReplacedKeyguardRootIsNoLongerSuppressedByControllerCallbacks()
            throws Exception {
        Fixture previous = fixture(true, View.VISIBLE);
        attach(previous);
        applyConfig(true);
        window.removeView(previous.root);
        Fixture replacement = fixture(true, View.VISIBLE);
        attach(replacement);

        controllerVisibility(previous.speed, true);

        assertEquals(View.VISIBLE, previous.speed.getVisibility());
        assertEquals(View.GONE, replacement.speed.getVisibility());
    }

    @Test public void otherTelemetryDoesNotSuppressNativeSpeedWhenNetworkReadoutIsDisabled()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Fixture keyguard = fixture(true, View.VISIBLE);
        attach(phone);
        attach(keyguard);
        FusionConfig temperatures = disabled.withTelemetry(disabled.telemetry
                .withEnabled(TelemetryConfig.TEMPERATURES, true));
        invokeConfig(temperatures);

        controllerVisibility(phone.speed, true);
        controllerVisibility(keyguard.speed, true);

        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.VISIBLE, keyguard.speed.getVisibility());
    }

    @Test public void anUnsupportedKeyguardWithoutAModuleReadoutKeepsItsNativeSpeed()
            throws Exception {
        Fixture phone = fixture(false, View.VISIBLE);
        Context context = window.getContext();
        MiuiKeyguardStatusBarView unsupported = new MiuiKeyguardStatusBarView(context);
        NetworkSpeedView speed = new NetworkSpeedView(context);
        unsupported.addView(speed);
        window.addView(unsupported);
        attach(phone);
        invokeHook("keyguardAttach", unsupported, null);

        applyConfig(true);
        controllerVisibility(speed, true);

        assertEquals("No mounted module readout means there is no native replacement to suppress",
                View.VISIBLE, speed.getVisibility());
        assertEquals(View.GONE, phone.speed.getVisibility());
    }

    private Fixture fixture(boolean keyguard, int visibility) {
        Context base = window.getContext();
        Resources resources = new NamedSystemUiResources(base.getResources());
        Context context = new ContextWrapper(base) {
            @Override public Resources getResources() { return resources; }
        };
        ViewGroup root = keyguard ? new MiuiKeyguardStatusBarView(context)
                : new FrameLayout(context);
        LinearLayout common = new LinearLayout(context);
        FrameLayout left = new FrameLayout(context);
        left.setId(resources.getIdentifier("phone_status_bar_left_container", "id",
                "com.android.systemui"));
        LinearLayout system = new LinearLayout(context);
        system.setId(resources.getIdentifier("system_icon_area", "id", "com.android.systemui"));
        MiuiStatusBatteryContainer battery = new MiuiStatusBatteryContainer(context);
        battery.setId(resources.getIdentifier("system_icons", "id", "com.android.systemui"));
        FrameLayout statusIcons = new FrameLayout(context);
        statusIcons.setId(resources.getIdentifier("statusIcons", "id", "com.android.systemui"));
        battery.addView(statusIcons, new LinearLayout.LayoutParams(24, 24));
        system.addView(battery);
        NetworkSpeedView speed = new NetworkSpeedView(context);
        speed.setVisibilityByController(visibility != View.GONE);
        speed.setVisibility(visibility);
        system.addView(speed, new LinearLayout.LayoutParams(40, 24));
        common.addView(left, new LinearLayout.LayoutParams(200, 48));
        common.addView(system, new LinearLayout.LayoutParams(200, 48));
        root.addView(common, new FrameLayout.LayoutParams(400, 48));
        window.addView(root, new FrameLayout.LayoutParams(400, 48));
        return new Fixture(root, speed, keyguard);
    }

    private static void removeFromParent(View view) {
        if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
    }

    private static StatusPairView readout(View view) {
        if (view instanceof StatusPairView pair) return pair;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                StatusPairView pair = readout(group.getChildAt(i));
                if (pair != null) return pair;
            }
        }
        return null;
    }

    private static void attach(Fixture fixture) throws Exception {
        invokeHook(fixture.keyguard ? "keyguardAttach" : "phoneAttached", fixture.root, null);
    }

    private void applyConfig(boolean enabled) throws Exception {
        invokeConfig(disabled.withTelemetry(disabled.telemetry
                .withEnabled(TelemetryConfig.NET_SPEED, enabled)));
    }

    private static void invokeConfig(FusionConfig config) throws Exception {
        Method apply = SystemUiHooks.class.getDeclaredMethod("applyLoadedConfig", FusionConfig.class);
        apply.setAccessible(true);
        apply.invoke(null, config);
        ShadowLooper.idleMainLooper();
    }

    private static void controllerVisibility(NetworkSpeedView speed, boolean visible)
            throws Exception {
        invokeHook("afterNativeSpeedVisibility", speed,
                () -> speed.setVisibilityByController(visible));
    }

    private static void invokeHook(String name, View receiver, Runnable nativeCall)
            throws Exception {
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                KeyguardNativeSpeedVisibilityRegressionTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getThisObject")) return receiver;
                    if (method.getName().equals("proceed") && nativeCall != null) nativeCall.run();
                    return null;
                });
        Method hook = SystemUiHooks.class.getDeclaredMethod(name, XposedInterface.Chain.class);
        hook.setAccessible(true);
        hook.invoke(null, chain);
    }

    private void replaceField(String name, Object value) throws Exception {
        Field field = field(name);
        previousFields.putIfAbsent(field, field.get(null));
        field.set(null, value);
    }

    private static Field field(String name) throws Exception {
        Field field = SystemUiHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ViewGroup root(String name) throws Exception {
        return (ViewGroup) ((WeakReference<?>) field(name).get(null)).get();
    }

    @SuppressWarnings("unchecked")
    private static Map<View, Integer> nativeVisibility() throws Exception {
        return (Map<View, Integer>) field("NATIVE_SPEED_VISIBILITY").get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, RuntimeApplicationPolicy.Result> featureStates() throws Exception {
        return (Map<String, RuntimeApplicationPolicy.Result>) field("FEATURE_STATES").get(null);
    }

    private static RuntimeConfigLifecycle isolatedConfigLifecycle() {
        return new RuntimeConfigLifecycle(new Handler(Looper.getMainLooper()),
                (task, delay) -> false, new RuntimeConfigLifecycle.Factory() {
            @Override public RuntimeConfigLifecycle.ConfigAccess createConfig(Context context,
                    RuntimeConfigCoordinator.Listener listener) {
                return new RuntimeConfigLifecycle.ConfigAccess() {
                    @Override public void reload() { }
                    @Override public void report(Context source, long revision, String feature,
                            String state, String reason) { }
                };
            }
            @Override public RuntimeConfigLifecycle.ObserverAccess createObservers(Context context,
                    Handler main, Runnable reload, Runnable preview) {
                return () -> { };
            }
        }, new RuntimeConfigLifecycle.Host() {
            @Override public void apply(FusionConfig config) { }
            @Override public void onApplyFailure(FusionConfig config, Throwable error) { }
            @Override public void onRecovered() { }
            @Override public void requestPreview(Context context) { }
        });
    }

    private static final class Fixture {
        final ViewGroup root;
        NetworkSpeedView speed;
        final boolean keyguard;

        Fixture(ViewGroup root, NetworkSpeedView speed, boolean keyguard) {
            this.root = root;
            this.speed = speed;
            this.keyguard = keyguard;
        }
    }

    private static final class NamedSystemUiResources extends Resources {
        @SuppressWarnings("deprecation") NamedSystemUiResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }

        @Override public int getIdentifier(String name, String type, String packageName) {
            if ("id".equals(type) && "com.android.systemui".equals(packageName)) {
                return switch (name) {
                    case "phone_status_bar_left_container" -> 0x7f0e0101;
                    case "system_icon_area" -> 0x7f0e0102;
                    case "system_icons" -> 0x7f0e0103;
                    case "statusIcons" -> 0x7f0e0104;
                    default -> 0;
                };
            }
            return super.getIdentifier(name, type, packageName);
        }
    }
}
