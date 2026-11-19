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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * The local 16.03.251211.r host inserts network_speed into statusIcons, not next to it.
 * Its icon container measures eligible slots even when the root View is GONE. This suite
 * isolates that contract; it does not identify the cause of any screenshot's leading gap.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class NativeSpeedSlotWidthRegressionTest {
    private static final String QUERY_ID = "NetworkSpeedView#isIconVisible";
    private final Map<Field, Object> previousFields = new LinkedHashMap<>();
    private final Map<String, Map<?, ?>> previousMaps = new LinkedHashMap<>();
    private ActivityController<Activity> activity;
    private FrameLayout window;
    private FusionConfig disabled;
    private InstalledHooks hooks;

    @Before public void isolateConfigRootsAndNativeOwnership() throws Exception {
        replaceField("configLifecycle", isolatedConfigLifecycle());
        disabled = FusionConfig.defaults().withDoubleRow(false)
                .withControlCenter(FusionConfig.defaults().controlCenter.withEnabled(false));
        replaceField("currentConfig", disabled);
        replaceField("currentPlan", StatusBarLayoutPlan.from(disabled));
        replaceField("configApplied", true);
        replaceField("currentStatusRoot", new WeakReference<ViewGroup>(null));
        replaceField("currentKeyguardStatusRoot", new WeakReference<ViewGroup>(null));
        saveAndClearMap("NATIVE_SPEED_VISIBILITY");
        saveAndClearMap("NATIVE_SPEED_ROOTS");
        saveAndClearMap("FEATURE_STATES");
        Field menuConfig = SystemMenuHooks.class.getDeclaredField("config");
        menuConfig.setAccessible(true);
        previousFields.put(menuConfig, menuConfig.get(null));
        activity = Robolectric.buildActivity(Activity.class).setup();
        shadowOf(activity.get().getSystemService(PowerManager.class)).setIsInteractive(true);
        window = new FrameLayout(activity.get());
        activity.get().setContentView(window);
        hooks = install(SlotSpeedView.class);
    }

    @After public void restoreConfigAndNativeOwnership() throws Exception {
        ShadowLooper.idleMainLooper();
        if (activity != null) activity.pause().stop().destroy();
        TelemetrySampler.configure(RuntimeEnvironment.getApplication(), TelemetryConfig.defaults());
        for (Map.Entry<Field, Object> saved : previousFields.entrySet()) {
            saved.getKey().set(null, saved.getValue());
        }
        for (Map.Entry<String, Map<?, ?>> saved : previousMaps.entrySet()) {
            restoreMap(saved.getKey(), saved.getValue());
        }
    }

    @Test public void phoneReplacementReclaimsGoneNativeSpeedWidthAndTheFirstVisibleIconPosition()
            throws Throwable {
        assertReplacementWidth(fixture(false));
    }

    @Test public void keyguardReplacementReclaimsGoneNativeSpeedWidthAndTheFirstVisibleIconPosition()
            throws Throwable {
        assertReplacementWidth(fixture(true));
    }

    @Test public void exactParentRetainsItsConstraintButDropsTheReplacedSlotContribution()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);

        phone.icons.measureAndLayout(View.MeasureSpec.EXACTLY, 120);

        assertEquals("EXACT width belongs to the host constraint", 120,
                phone.icons.getMeasuredWidth());
        assertEquals(phone.icons.describe(), 20, phone.icons.eligibleWidth);
        assertEquals("The rightmost ordinary icon remains END aligned", 100f,
                phone.ordinary.getX(), 0f);
    }

    @Test public void zeroHostSpacingStillRequiresReclaimingTheGoneFortyPixelSlot()
            throws Throwable {
        Fixture phone = fixture(false);
        phone.icons.iconSpacing = 0;
        phone.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals("The sampled host defaults to zero icon spacing", 60,
                phone.icons.getMeasuredWidth());
        assertEquals(40f, phone.ordinary.getX(), 0f);

        enableAndUpdate(phone);

        assertCompact(phone);
    }

    @Test public void disablingAndReenablingRestoresTheRawHostSlotWithoutChangingOrdinaryIcons()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        assertCompact(phone);

        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertRawWidth(phone);

        applyConfig(true);
        updateNativeVisibility(phone.speed);
        assertEquals(View.GONE, phone.speed.getVisibility());
        assertCompact(phone);
        assertTrue(phone.ordinary.visible);
        assertFalse(phone.ordinary.blocked);
        assertTrue(phone.icons.ignoredSlots.isEmpty());
    }

    @Test public void phoneStockNetworkTogglesKeepHolderIndicesAndMuteIdentityAcrossModuleCycles()
            throws Throwable {
        assertStockToggleSequence(fixture(false));
    }

    @Test public void keyguardStockNetworkTogglesKeepHolderIndicesAndMuteIdentityAcrossModuleCycles()
            throws Throwable {
        assertStockToggleSequence(fixture(true));
    }

    @Test public void moduleReadoutAndNativeSlotRespectThreeIndependentSwitches() throws Throwable {
        for (boolean keyguard : new boolean[] {false, true}) {
            Fixture fixture = fixture(keyguard);
            fixture.icons.iconSpacing = 0;
            StockIconOwner owner = new StockIconOwner(fixture.icons, fixture.speed, fixture.ordinary);
            for (boolean monitorEnabled : new boolean[] {true, false}) {
                for (boolean fusionAllowsNative : new boolean[] {true, false}) {
                    int mask = fusionAllowsNative ? FusionConfig.SYSTEM_ICON_ALL
                            : FusionConfig.SYSTEM_ICON_ALL & ~FusionConfig.ICON_NETWORK_SPEED;
                    applyConfig(monitorEnabled, mask);
                    // Model the host's delivery of the composed block list to the native View.
                    String blockList = SystemIconVisibilityPolicy.apply("", mask);
                    fixture.speed.blocked = List.of(blockList.split(",")).contains("network_speed");
                    updateNativeVisibility(fixture.speed);
                    for (boolean systemSpeedEnabled : new boolean[] {true, false}) {
                        owner.setStockNetworkVisible(systemSpeedEnabled);
                        DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                                null, null, null, null, 4, 7, true));
                        View readout = DualRowStatusBarLayout.networkReadout(fixture.root);
                        boolean nativeEligible = !monitorEnabled && fusionAllowsNative && systemSpeedEnabled;
                        String phase = "keyguard=" + keyguard + ",monitor=" + monitorEnabled
                                + ",fusionNative=" + fusionAllowsNative + ",system=" + systemSpeedEnabled;

                        assertEquals(phase, monitorEnabled, readout != null);
                        if (readout != null) {
                            assertEquals(phase, "\u21914B/s \u21937B/s", readout.getContentDescription());
                        }
                        assertEquals(phase, systemSpeedEnabled, fixture.speed.controllerVisible);
                        assertEquals(phase, nativeEligible, hooks.query(fixture.speed));
                        fixture.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
                        assertEquals(phase, nativeEligible ? 60 : 20, fixture.icons.getMeasuredWidth());
                        owner.assertMetadataAndPhysicalChildren();
                    }
                }
            }
        }
    }

    @Test public void anUnmanagedRootKeepsItsRawSpeedEligibilityWhileAnotherRootIsReplaced()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        HostIconContainer unrelated = new HostIconContainer(phone.root.getContext(), hooks);
        SlotSpeedView speed = new SlotSpeedView(phone.root.getContext());
        unrelated.addView(speed, new ViewGroup.LayoutParams(40, 24));
        unrelated.addView(new OrdinaryIconView(phone.root.getContext()),
                new ViewGroup.LayoutParams(20, 24));
        window.addView(unrelated);
        updateNativeVisibility(speed);

        unrelated.measureAndLayout(View.MeasureSpec.AT_MOST, 120);

        assertEquals(View.VISIBLE, speed.getVisibility());
        assertEquals(unrelated.describe(), 64, unrelated.getMeasuredWidth());
        assertTrue(hooks.query(speed));
        assertCompact(phone);
    }

    @Test public void aKeyguardWithoutAMountedReadoutKeepsItsNativeSlot()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        MiuiKeyguardStatusBarView unsupported =
                new MiuiKeyguardStatusBarView(phone.root.getContext());
        HostIconContainer icons = new HostIconContainer(phone.root.getContext(), hooks);
        SlotSpeedView speed = new SlotSpeedView(phone.root.getContext());
        icons.addView(speed, new ViewGroup.LayoutParams(40, 24));
        icons.addView(new OrdinaryIconView(phone.root.getContext()),
                new ViewGroup.LayoutParams(20, 24));
        unsupported.addView(icons);
        window.addView(unsupported);
        invokeHook("keyguardAttach", unsupported, () -> null);
        updateNativeVisibility(speed);

        assertFalse(DualRowStatusBarLayout.showsTelemetry(unsupported, TelemetryConfig.NET_SPEED));
        icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals(icons.describe(), 64, icons.getMeasuredWidth());
        assertEquals(View.VISIBLE, speed.getVisibility());
        assertTrue(hooks.query(speed));
    }

    @Test public void eligibilityOverrideLeavesRawControllerBlockedAndRemovalStateUntouched()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        boolean[][] states = {{true, false, false}, {false, false, false},
                {true, true, false}, {false, false, true}};
        for (boolean[] state : states) {
            phone.speed.controllerVisible = state[0];
            phone.speed.blocked = state[1];
            phone.speed.removeFlag = state[2];
            boolean raw = phone.speed.isIconVisible();

            assertFalse("Mounted replacement must exclude the slot for raw=" + raw,
                    hooks.query(phone.speed));
            assertEquals(state[0], phone.speed.controllerVisible);
            assertEquals(state[1], phone.speed.blocked);
            assertEquals(state[2], phone.speed.removeFlag);
            assertEquals(raw, phone.speed.isIconVisible());
        }
    }

    @Test public void alreadyIneligibleNativeSpeedSkipsReplacementLookup() throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        int[] lookups = {0};
        replaceField("currentStatusRoot", new WeakReference<ViewGroup>(phone.root) {
            @Override public ViewGroup get() {
                lookups[0]++;
                return super.get();
            }
        });
        phone.speed.controllerVisible = false;
        assertFalse(hooks.query(phone.speed));
        assertEquals("An ineligible OEM icon needs no override", 0, lookups[0]);

        phone.speed.controllerVisible = true;
        assertFalse("An eligible OEM icon is still suppressed in the replacement root",
                hooks.query(phone.speed));
        assertTrue(lookups[0] > 0);
    }

    @Test public void refreshingRestoresASpeedMovedOutsideTheManagedRoot()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        phone.icons.removeView(phone.speed);
        HostIconContainer outside = new HostIconContainer(phone.root.getContext(), hooks);
        outside.addView(phone.speed, new ViewGroup.LayoutParams(40, 24));
        outside.addView(new OrdinaryIconView(phone.root.getContext()),
                new ViewGroup.LayoutParams(20, 24));
        window.addView(outside);

        refresh(phone);

        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertTrue(hooks.query(phone.speed));
        outside.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals(outside.describe(), 64, outside.getMeasuredWidth());
        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
    }

    @Test public void refreshingRestoresAParentlessSpeedAndItsRawEligibility()
            throws Throwable {
        Fixture phone = fixture(false);
        enableAndUpdate(phone);
        phone.icons.removeView(phone.speed);

        refresh(phone);

        assertNull(phone.speed.getParent());
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertTrue(hooks.query(phone.speed));
        assertFalse(nativeVisibility().containsKey(phone.speed));
    }

    @Test public void refreshingOldRootsKeepsTransferredSpeedsSuppressedUntilDisable()
            throws Throwable {
        Fixture phone = fixture(false);
        Fixture keyguard = fixture(true);
        enableAndUpdate(phone, keyguard);
        phone.icons.removeView(phone.speed);
        keyguard.icons.removeView(keyguard.speed);
        phone.icons.addView(keyguard.speed, 0, new ViewGroup.LayoutParams(40, 24));
        keyguard.icons.addView(phone.speed, 0, new ViewGroup.LayoutParams(40, 24));

        refresh(phone);
        refresh(keyguard);

        assertEquals(View.GONE, phone.speed.getVisibility());
        assertEquals(View.GONE, keyguard.speed.getVisibility());
        assertFalse(hooks.query(phone.speed));
        assertFalse(hooks.query(keyguard.speed));
        applyConfig(false);
        assertEquals(View.VISIBLE, phone.speed.getVisibility());
        assertEquals(View.VISIBLE, keyguard.speed.getVisibility());
        assertTrue(hooks.query(phone.speed));
        assertTrue(hooks.query(keyguard.speed));
    }

    @Test public void installerRegistersOnlyTheZeroArgumentBooleanSpeedQuery()
            throws Exception {
        assertTrue("The slot-width contract requires the optional query hook",
                hooks.methods.containsKey(QUERY_ID));
        assertEquals(SlotSpeedView.class.getDeclaredMethod("isIconVisible"),
                hooks.methods.get(QUERY_ID));
        assertFalse(hooks.methods.containsValue(
                SlotSpeedView.class.getDeclaredMethod("isIconVisible", int.class)));
    }

    @Test public void missingQueryCapabilityIsReportedWhilePreservingTheExistingVisibilityHook()
            throws Exception {
        InstalledHooks missing = install(MissingQuerySpeed.class);
        assertTrue(missing.methods.containsKey("NetworkSpeedView#updateVisibility"));
        assertFalse(missing.methods.containsKey(QUERY_ID));
        assertTrue(missing.result.missingMembers.toString(), missing.result.missingMembers.contains(
                NotificationHookInstaller.NETWORK_SPEED_CLASS + "#isIconVisible"));
    }

    @Test public void wrongQueryReturnTypeIsRejectedWhilePreservingTheExistingVisibilityHook()
            throws Exception {
        InstalledHooks wrong = install(WrongReturnQuerySpeed.class);
        assertTrue(wrong.methods.containsKey("NetworkSpeedView#updateVisibility"));
        assertFalse(wrong.methods.containsKey(QUERY_ID));
        assertTrue(wrong.result.missingMembers.toString(), wrong.result.missingMembers.contains(
                NotificationHookInstaller.NETWORK_SPEED_CLASS + "#isIconVisible"));
    }

    private void assertReplacementWidth(Fixture fixture) throws Throwable {
        enableAndUpdate(fixture);
        assertSame(fixture.icons, fixture.speed.getParent());
        assertEquals("Existing visibility override is active before checking slot width",
                View.GONE, fixture.speed.getVisibility());
        assertTrue("Raw controller/blocked/removeFlag still say the host slot is visible",
                fixture.speed.isIconVisible());
        assertCompact(fixture);
    }

    private void assertStockToggleSequence(Fixture fixture) throws Throwable {
        fixture.icons.iconSpacing = 0;
        StockIconOwner owner = new StockIconOwner(fixture.icons, fixture.speed, fixture.ordinary);
        owner.assertMetadataAndPhysicalChildren();
        // Model NET on/off/on independently of the system's live-speed controller requests.
        // This does not claim that a real settings label or either reported symptom is mapped.
        for (boolean moduleEnabled : new boolean[] {true, false, true}) {
            applyConfig(moduleEnabled);
            assertEquals(moduleEnabled,
                    DualRowStatusBarLayout.showsTelemetry(fixture.root, TelemetryConfig.NET_SPEED));
            // The dedicated subclass is discovered through the existing production callback.
            updateNativeVisibility(fixture.speed);
            for (boolean stockVisible : new boolean[] {true, false, true, false}) {
                owner.setStockNetworkVisible(stockVisible);
                String phase = "root=" + (fixture.keyguard ? "keyguard" : "phone")
                        + " module=" + moduleEnabled + " stock=" + stockVisible;
                owner.assertMetadataAndPhysicalChildren();
                assertSame(phase + " controller must address the metadata-selected network view",
                        fixture.speed, owner.lastNetworkTarget);
                assertEquals(phase, stockVisible, owner.network.visible);
                assertEquals(phase, stockVisible, fixture.speed.controllerVisible);
                assertFalse(phase, fixture.speed.blocked);
                assertFalse(phase, fixture.speed.removeFlag);
                assertEquals(phase, moduleEnabled || !stockVisible ? View.GONE : View.VISIBLE,
                        fixture.speed.getVisibility());
                boolean eligibility = hooks.query(fixture.speed);
                assertEquals(phase, !moduleEnabled && stockVisible, eligibility);
                assertEquals(phase + " query must preserve the raw controller request",
                        stockVisible, fixture.speed.controllerVisible);
                assertFalse(phase + " query must preserve blocked", fixture.speed.blocked);
                assertFalse(phase + " query must preserve removeFlag", fixture.speed.removeFlag);
                fixture.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
                assertEquals(phase + " " + fixture.icons.describe(),
                        eligibility ? 60 : 20, fixture.icons.getMeasuredWidth());
                assertEquals(phase + " ordinary icon must retain its visible position",
                        eligibility ? 40f : 0f, fixture.ordinary.getX(), 0f);
                owner.assertMetadataAndPhysicalChildren();
            }
        }
        // The last live-speed request was false. Disabling the module must retain it, then
        // a new true request must reach the same native speed object and leave mute visible.
        applyConfig(false);
        assertEquals(View.GONE, fixture.speed.getVisibility());
        assertFalse(hooks.query(fixture.speed));
        owner.setStockNetworkVisible(true);
        assertSame(fixture.speed, owner.lastNetworkTarget);
        assertTrue(hooks.query(fixture.speed));
        assertEquals(View.VISIBLE, fixture.speed.getVisibility());
        assertTrue(fixture.speed.controllerVisible);
        assertFalse(fixture.speed.blocked);
        assertFalse(fixture.speed.removeFlag);
        fixture.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals(fixture.icons.describe(), 60, fixture.icons.getMeasuredWidth());
        owner.assertMetadataAndPhysicalChildren();
    }

    private static void assertCompact(Fixture fixture) throws Throwable {
        fixture.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals(fixture.icons.describe(), 20, fixture.icons.getMeasuredWidth());
        assertEquals(fixture.icons.describe(), 0f, fixture.ordinary.getX(), 0f);
    }

    private static void assertRawWidth(Fixture fixture) throws Throwable {
        fixture.icons.measureAndLayout(View.MeasureSpec.AT_MOST, 120);
        assertEquals(fixture.icons.describe(), 64, fixture.icons.getMeasuredWidth());
        assertEquals(fixture.icons.describe(), 44f, fixture.ordinary.getX(), 0f);
    }

    private Fixture fixture(boolean keyguard) throws Throwable {
        Resources resources = new NamedSystemUiResources(window.getResources());
        Context context = new ContextWrapper(window.getContext()) {
            @Override public Resources getResources() { return resources; }
        };
        ViewGroup root = keyguard ? new MiuiKeyguardStatusBarView(context) : new FrameLayout(context);
        LinearLayout common = new LinearLayout(context);
        FrameLayout left = new FrameLayout(context);
        left.setId(resources.getIdentifier("phone_status_bar_left_container", "id", "com.android.systemui"));
        LinearLayout system = new LinearLayout(context);
        system.setId(resources.getIdentifier("system_icon_area", "id", "com.android.systemui"));
        MiuiStatusBatteryContainer batteryContainer = new MiuiStatusBatteryContainer(context);
        batteryContainer.setId(resources.getIdentifier("system_icons", "id", "com.android.systemui"));
        HostIconContainer icons = new HostIconContainer(context, hooks);
        icons.setId(resources.getIdentifier("statusIcons", "id", "com.android.systemui"));
        SlotSpeedView speed = new SlotSpeedView(context);
        OrdinaryIconView ordinary = new OrdinaryIconView(context);
        icons.addView(speed, new ViewGroup.LayoutParams(40, 24));
        icons.addView(ordinary, new ViewGroup.LayoutParams(20, 24));
        batteryContainer.addView(icons, new LinearLayout.LayoutParams(64, 24));
        system.addView(batteryContainer);
        common.addView(left, new LinearLayout.LayoutParams(200, 48));
        common.addView(system, new LinearLayout.LayoutParams(200, 48));
        root.addView(common, new FrameLayout.LayoutParams(400, 48));
        window.addView(root, new FrameLayout.LayoutParams(400, 48));
        invokeHook(keyguard ? "keyguardAttach" : "phoneAttached", root, () -> null);
        return new Fixture(root, icons, speed, ordinary, keyguard);
    }

    private void enableAndUpdate(Fixture... fixtures) throws Throwable {
        applyConfig(true);
        for (Fixture fixture : fixtures) {
            assertTrue("The production layout must mount a module readout",
                    DualRowStatusBarLayout.showsTelemetry(fixture.root, TelemetryConfig.NET_SPEED));
            // This dedicated subclass supplies the slot contract missing from the shared fixture.
            // Seed ownership through the real visibility hook, not the exact-class root scan.
            updateNativeVisibility(fixture.speed);
        }
    }

    private void updateNativeVisibility(SlotSpeedView speed) throws Throwable {
        hooks.invoke("NetworkSpeedView#updateVisibility", speed, () -> {
            speed.updateVisibility();
            return null;
        });
    }

    private void applyConfig(boolean enabled) throws Throwable {
        applyConfig(enabled, disabled.systemIconVisibilityMask);
    }

    private void applyConfig(boolean enabled, int iconMask) throws Throwable {
        Method apply = SystemUiHooks.class.getDeclaredMethod("applyLoadedConfig", FusionConfig.class);
        apply.setAccessible(true);
        call(apply, null, disabled.withTelemetry(disabled.telemetry
                .withEnabled(TelemetryConfig.NET_SPEED, enabled)).withSystemIconVisibilityMask(iconMask));
        ShadowLooper.idleMainLooper();
    }

    private static void refresh(Fixture fixture) throws Throwable {
        Method apply = SystemUiHooks.class.getDeclaredMethod(
                fixture.keyguard ? "applyKeyguardStatusBar" : "applyRootPlacement", ViewGroup.class);
        apply.setAccessible(true);
        call(apply, null, fixture.root);
    }

    private static InstalledHooks install(Class<?> speedType) throws Exception {
        InstalledHooks installed = new InstalledHooks();
        Method hookerFactory = SystemUiHooks.class.getDeclaredMethod(
                "notificationHooker", NotificationHookInstaller.Callback.class);
        hookerFactory.setAccessible(true);
        NotificationHookInstaller installer = new NotificationHookInstaller(
                (name, loader) -> NotificationHookInstaller.NETWORK_SPEED_CLASS.equals(name)
                        ? speedType : null,
                new NotificationHookInstaller.MemberResolver() {
                    @Override public Field field(Class<?> type, String name) { return null; }
                    @Override public Method exact(Class<?> type, String name, Class<?>[] parameters,
                            boolean searchSuper) {
                        for (Class<?> current = type; current != null;
                                current = searchSuper ? current.getSuperclass() : null) {
                            try {
                                Method method = current.getDeclaredMethod(name, parameters);
                                method.setAccessible(true);
                                return method;
                            } catch (NoSuchMethodException ignored) { }
                        }
                        return null;
                    }
                    @Override public Method[] declaredMethods(Class<?> type) {
                        return type.getDeclaredMethods();
                    }
                },
                (id, method, hooker) -> {
                    installed.methods.put(id, method);
                    installed.hookers.put(id, hooker);
                    return true;
                },
                callback -> {
                    try { return (XposedInterface.Hooker) hookerFactory.invoke(null, callback); }
                    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
        installed.result = installer.install(NativeSpeedSlotWidthRegressionTest.class.getClassLoader());
        return installed;
    }

    private static Object invokeHook(String name, View receiver, NativeCall nativeCall)
            throws Throwable {
        Method hook = SystemUiHooks.class.getDeclaredMethod(name, XposedInterface.Chain.class);
        hook.setAccessible(true);
        return call(hook, null, chain(receiver, nativeCall));
    }

    private static XposedInterface.Chain chain(View receiver, NativeCall nativeCall) {
        return (XposedInterface.Chain) Proxy.newProxyInstance(
                NativeSpeedSlotWidthRegressionTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getThisObject")) return receiver;
                    if (method.getName().equals("proceed")) return nativeCall.run();
                    return null;
                });
    }

    private static Object call(Method method, Object receiver, Object... args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
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

    private void saveAndClearMap(String name) throws Exception {
        Map<?, ?> map = (Map<?, ?>) field(name).get(null);
        previousMaps.put(name, new LinkedHashMap<>(map));
        map.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreMap(String name, Map<?, ?> previous) throws Exception {
        Map map = (Map) field(name).get(null);
        map.clear();
        map.putAll(previous);
    }

    @SuppressWarnings("unchecked")
    private static Map<View, Integer> nativeVisibility() throws Exception {
        return (Map<View, Integer>) field("NATIVE_SPEED_VISIBILITY").get(null);
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
                    Handler main, Runnable reload, Runnable preview) { return () -> { }; }
        }, new RuntimeConfigLifecycle.Host() {
            @Override public void apply(FusionConfig config) { }
            @Override public void onApplyFailure(FusionConfig config, Throwable error) { }
            @Override public void onRecovered() { }
            @Override public void requestPreview(Context context) { }
        });
    }

    @FunctionalInterface private interface NativeCall { Object run() throws Throwable; }

    private static final class InstalledHooks {
        final Map<String, Method> methods = new LinkedHashMap<>();
        final Map<String, XposedInterface.Hooker> hookers = new LinkedHashMap<>();
        NotificationHookInstaller.Result result;

        Object invoke(String id, View receiver, NativeCall nativeCall) throws Throwable {
            XposedInterface.Hooker hooker = hookers.get(id);
            return hooker == null ? nativeCall.run() : hooker.intercept(chain(receiver, nativeCall));
        }

        boolean query(SlotSpeedView speed) throws Throwable {
            // An absent query hook means the original host method runs. Baseline RED must be
            // a width/eligibility assertion, never reflection failure for an unimplemented hook.
            return (Boolean) invoke(QUERY_ID, speed, speed::isIconVisible);
        }
    }

    /** Dedicated contract fixture; the existing shared NetworkSpeedView fixture stays unchanged. */
    public static final class SlotSpeedView extends NetworkSpeedView {
        boolean controllerVisible = true;
        boolean blocked;
        boolean removeFlag;

        SlotSpeedView(Context context) { super(context); }
        public String getSlot() { return "network_speed"; }
        public boolean isIconVisible() { return (controllerVisible && !blocked) || removeFlag; }
        public boolean isIconVisible(int ignored) { return false; }
        public boolean isIconBlocked() { return false; }
        public int getNetworkSpeedWidth() { return 40; }
        public void updateVisibility() { setVisibility(controllerVisible && !blocked ? VISIBLE : GONE); }
    }

    public static final class MissingQuerySpeed { public void updateVisibility() { } }
    public static final class WrongReturnQuerySpeed {
        public void updateVisibility() { }
        public int isIconVisible() { return 1; }
    }

    private static final class OrdinaryIconView extends View {
        boolean visible = true;
        boolean blocked;
        OrdinaryIconView(Context context) { super(context); }
        public String getSlot() { return "mute"; }
    }

    /**
     * Two primary-holder subset of StatusBarIconList.getViewIndex:119-148 and controller
     * handleSet:215-238. Every holder remains in the owner model regardless of visibility.
     * No subSlots, real settings service, manager reconstruction or ROM animation is modeled.
     */
    private final class StockIconOwner {
        final HostIconContainer group;
        final SlotSpeedView speedIdentity;
        final OrdinaryIconView muteIdentity;
        final PrimaryHolder network = new PrimaryHolder("network_speed", 0, 9, true);
        final PrimaryHolder mute = new PrimaryHolder("mute", 0, 0, true);
        final List<PrimaryHolder> holders = List.of(network, mute);
        View lastNetworkTarget;

        StockIconOwner(HostIconContainer group, SlotSpeedView speed, OrdinaryIconView ordinary) {
            this.group = group;
            speedIdentity = speed;
            muteIdentity = ordinary;
        }

        int getViewIndex(int tag, String slot) {
            int index = 0;
            for (PrimaryHolder holder : holders) {
                if (holder.tag == tag && holder.slot.equals(slot)) return index;
                // Match the host's holder-presence counting, never its current visible state.
                index++;
            }
            throw new IllegalArgumentException("No fixture holder for " + slot + "/" + tag);
        }

        void setStockNetworkVisible(boolean visible) throws Throwable {
            network.visible = visible;
            int physicalIndex = getViewIndex(network.tag, network.slot);
            lastNetworkTarget = group.getChildAt(physicalIndex);
            assertEquals("Stock network metadata must remain type 9", 9, network.type);
            assertTrue("type 9 must target NetworkSpeedView, not an adjacent mute icon",
                    lastNetworkTarget instanceof SlotSpeedView);
            SlotSpeedView target = (SlotSpeedView) lastNetworkTarget;
            // NetworkSpeedView.setVisibilityByController:196-200 returns early for unchanged
            // requests. The query must not alter this raw controller state or this no-op gate.
            if (target.controllerVisible != visible) {
                target.controllerVisible = visible;
                updateNativeVisibility(target);
            }
        }

        void assertMetadataAndPhysicalChildren() {
            assertEquals(2, holders.size());
            assertSame(network, holders.get(0));
            assertSame(mute, holders.get(1));
            assertEquals("network_speed", network.slot);
            assertEquals(0, network.tag);
            assertEquals(9, network.type);
            assertEquals("mute", mute.slot);
            assertEquals(0, mute.tag);
            assertEquals(0, mute.type);
            assertTrue("Network toggles must not change the mute holder", mute.visible);
            assertEquals(0, getViewIndex(0, "network_speed"));
            assertEquals(1, getViewIndex(0, "mute"));
            assertEquals("No module readout may enter the manager-owned child list", 2,
                    group.getChildCount());
            assertSame(speedIdentity, group.getChildAt(getViewIndex(0, "network_speed")));
            assertSame(muteIdentity, group.getChildAt(getViewIndex(0, "mute")));
            assertEquals(network.slot, speedIdentity.getSlot());
            assertEquals(mute.slot, muteIdentity.getSlot());
            assertSame(group, speedIdentity.getParent());
            assertSame(group, muteIdentity.getParent());
            assertEquals("A width fix must not hide the mute object", View.VISIBLE,
                    muteIdentity.getVisibility());
            assertEquals(1f, muteIdentity.getAlpha(), 0f);
            assertEquals(1f, muteIdentity.getScaleX(), 0f);
            assertEquals(1f, muteIdentity.getScaleY(), 0f);
            assertTrue(muteIdentity.visible);
            assertFalse(muteIdentity.blocked);
            assertTrue("No temporary ignored-slot mutation is needed", group.ignoredSlots.isEmpty());
        }
    }

    private static final class PrimaryHolder {
        final String slot;
        final int tag;
        final int type;
        boolean visible;

        PrimaryHolder(String slot, int tag, int type, boolean visible) {
            this.slot = slot;
            this.tag = tag;
            this.type = type;
            this.visible = visible;
        }
    }

    /**
     * Eligibility/width contract from MiuiStatusIconContainer:1083-1130,345-381.
     * Four pixels is a spacing stress case, not the sampled host's zero-spacing default.
     * Like the host, children retain origin frames and receive X translations during layout.
     */
    private static final class HostIconContainer extends ViewGroup {
        final InstalledHooks hooks;
        final List<String> ignoredSlots = new ArrayList<>();
        final List<View> eligible = new ArrayList<>();
        int eligibleWidth;
        int iconSpacing = 4;

        HostIconContainer(Context context, InstalledHooks hooks) { super(context); this.hooks = hooks; }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            eligible.clear();
            eligibleWidth = 0;
            try {
                for (int index = 0; index < getChildCount(); index++) {
                    View child = getChildAt(index);
                    boolean visible;
                    String slot;
                    boolean blocked;
                    if (child instanceof SlotSpeedView speed) {
                        visible = hooks.query(speed);
                        slot = speed.getSlot();
                        blocked = speed.isIconBlocked();
                    } else {
                        OrdinaryIconView icon = (OrdinaryIconView) child;
                        visible = icon.visible;
                        blocked = icon.blocked;
                        slot = "mute";
                    }
                    if (visible && !blocked && !ignoredSlots.contains(slot)) eligible.add(child);
                }
            } catch (Throwable error) { throw new AssertionError(error); }
            for (View child : eligible) {
                // Deliberately does not filter getVisibility(): the host calls measureChild too.
                measureChild(child, MeasureSpec.makeMeasureSpec(120, MeasureSpec.UNSPECIFIED), heightSpec);
                eligibleWidth += child.getPaddingStart() + child.getMeasuredWidth() + child.getPaddingEnd();
            }
            eligibleWidth += Math.max(0, eligible.size() - 1) * iconSpacing;
            int mode = MeasureSpec.getMode(widthSpec);
            int limit = MeasureSpec.getSize(widthSpec);
            int width = mode == MeasureSpec.EXACTLY ? limit
                    : mode == MeasureSpec.AT_MOST ? Math.min(limit, eligibleWidth) : eligibleWidth;
            setMeasuredDimension(width, 24);
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                child.layout(0, 0, child.getMeasuredWidth(), 24);
            }
            int cursor = getWidth();
            for (int index = eligible.size() - 1; index >= 0; index--) {
                View child = eligible.get(index);
                int width = child instanceof SlotSpeedView speed
                        ? speed.getNetworkSpeedWidth() : child.getMeasuredWidth();
                cursor -= width;
                child.setTranslationX(cursor);
                if (index > 0) cursor -= iconSpacing;
            }
        }

        void measureAndLayout(int mode, int width) {
            measure(MeasureSpec.makeMeasureSpec(width, mode), MeasureSpec.makeMeasureSpec(24, MeasureSpec.EXACTLY));
            layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }

        String describe() {
            StringBuilder value = new StringBuilder("eligibleWidth=").append(eligibleWidth)
                    .append(" measured=").append(getMeasuredWidth()).append(" ignored=").append(ignoredSlots);
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                value.append(" child=").append(child.getClass().getSimpleName())
                        .append(" visibility=").append(child.getVisibility())
                        .append(" measured=").append(child.getMeasuredWidth())
                        .append(" bounds=").append(child.getLeft()).append("..").append(child.getRight())
                        .append(" translationX=").append(child.getTranslationX());
            }
            return value.toString();
        }
    }

    private record Fixture(ViewGroup root, HostIconContainer icons, SlotSpeedView speed,
            OrdinaryIconView ordinary, boolean keyguard) { }

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
