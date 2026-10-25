package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterSplitHeaderCapabilityRegressionTest {
    private static final XposedInterface.Hooker NO_OP = chain -> chain.proceed();
    private final Map<Field, Object> values = new HashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> maps = new IdentityHashMap<>();
    private final ControlCenterConfig appearance = ControlCenterConfig.defaults().withEnabled(true)
            .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
    private ControlCenterPluginCapabilities capabilities;

    @Before public void isolateRuntimeState() throws Exception {
        capabilities = (ControlCenterPluginCapabilities) field(SystemUiHooks.class,
                "CONTROL_CENTER_PLUGIN_CAPABILITIES").get(null);
        preserveValue(capabilities, "active");
        preserveMap(capabilities, "entries");
        for (String name : new String[] {"module", "loader", "currentConfig", "configLifecycle"}) {
            Field target = preserveValue(SystemUiHooks.class, name);
            target.set(null, name.equals("currentConfig")
                    ? FusionConfig.defaults().withControlCenter(appearance) : null);
        }
        preserveMap(SystemUiHooks.class, "FEATURE_STATES");
        preserveMap(SystemUiHooks.class, "RECENT_DIAGNOSTICS");
    }

    @After public void restoreRuntimeState() throws Exception {
        for (Map.Entry<Map<Object, Object>, Map<Object, Object>> item : maps.entrySet()) {
            item.getKey().clear();
            item.getKey().putAll(item.getValue());
        }
        for (Map.Entry<Field, Object> item : values.entrySet()) {
            Field target = item.getKey();
            target.set(target.getDeclaringClass() == SystemUiHooks.class ? null : capabilities,
                    item.getValue());
        }
    }

    @Test public void installedCoreDateHookServesTheActivePluginLayoutWithoutFalseDegradation() throws Exception {
        ClassLoader core = loader(), plugin = loader();
        ControlCenterPluginHookInstaller.Result coreResult = installed(core, false, DateFixture.class);
        ControlCenterPluginHookInstaller.Result pluginResult = installed(plugin, true, null);
        assertEquals(1, coreResult.count(ControlCenterPluginHookInstaller.Group.HEADER_DATE));
        assertFalse(coreResult.layoutAvailable);
        assertTrue(pluginResult.layoutAvailable);
        assertEquals(0, pluginResult.count(ControlCenterPluginHookInstaller.Group.HEADER_DATE));
        activate(core, coreResult, plugin, pluginResult, appearance.withHideDate(true));
        assertFalse("The actual SystemUI header hook and active plugin layout jointly satisfy date hiding",
                reportMissing());
    }

    @Test public void installedCoreCarrierHookServesTheActivePluginLayoutWithoutFalseDegradation() throws Exception {
        ClassLoader core = loader(), plugin = loader();
        ControlCenterPluginHookInstaller.Result coreResult = installed(core, false, CarrierFixture.class);
        ControlCenterPluginHookInstaller.Result pluginResult = installed(plugin, true, null);
        assertEquals(1, coreResult.count(ControlCenterPluginHookInstaller.Group.HEADER_CARRIER));
        assertEquals(0, coreResult.count(ControlCenterPluginHookInstaller.Group.HEADER_DATE));
        activate(core, coreResult, plugin, pluginResult, appearance.withHideCarrier(true));
        assertFalse("Carrier visibility can be owned by the known SystemUI loader independently of the plugin",
                reportMissing());
    }

    @Test public void unrelatedHeaderSuccessCannotHideAGenuinelyMissingCoreDateHook() throws Exception {
        ClassLoader core = loader(), plugin = loader(), unrelated = loader();
        capabilities.record(unrelated, installed(unrelated, false, DateFixture.class));
        activate(core, installed(core, false, null), plugin, installed(plugin, true, null),
                appearance.withHideDate(true));
        assertTrue("Only the known core loader may supply a plugin's missing header capability", reportMissing());
        assertReason("control_center_date_visibility_hook_unavailable");
    }

    @Test public void failedActivePluginCannotBorrowLayoutFromTheCoreOrAnOldPlugin() throws Exception {
        ClassLoader core = loader(), plugin = loader(), oldPlugin = loader();
        capabilities.record(oldPlugin, installed(oldPlugin, true, DateFixture.class));
        activate(core, installed(core, true, DateFixture.class), plugin, installed(plugin, false, null),
                appearance.withHideDate(true));
        assertTrue("Layout availability must still belong to the active plugin", reportMissing());
        assertReason("control_center_layout_hooks_unavailable");
    }

    private void activate(ClassLoader core, ControlCenterPluginHookInstaller.Result coreResult,
            ClassLoader plugin, ControlCenterPluginHookInstaller.Result pluginResult,
            ControlCenterConfig settings) throws Exception {
        field(SystemUiHooks.class, "loader").set(null, core);
        field(SystemUiHooks.class, "currentConfig").set(null, FusionConfig.defaults().withControlCenter(settings));
        capabilities.record(core, coreResult);
        capabilities.record(plugin, pluginResult);
        capabilities.activate(plugin);
        assertSame(pluginResult, capabilities.activeResult());
    }

    private static ControlCenterPluginHookInstaller.Result installed(ClassLoader owner, boolean layout,
            Class<?> header) {
        Map<String, Class<?>> classes = new HashMap<>();
        if (layout) {
            classes.put("miui.systemui.controlcenter.panel.main.qs.QSListController", ListFixture.class);
            classes.put("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", AdapterFixture.class);
            classes.put("miui.systemui.controlcenter.panel.main.qs.QSRecord", SpanFixture.class);
            classes.put("miui.systemui.controlcenter.windowview.ControlCenterWindowViewController", WindowFixture.class);
            classes.put("miui.systemui.controlcenter.panel.main.media.MediaPlayerController", SpanFixture.class);
        }
        if (header != null) classes.put("com.android.systemui.controlcenter.shade.ControlCenterHeaderController", header);
        RuntimeHookRegistry<Object> handles = new RuntimeHookRegistry<>();
        ControlCenterPluginHookInstaller installer = new ControlCenterPluginHookInstaller(
                (name, target) -> target == owner ? classes.get(name) : null,
                (id, method, hooker) -> {
                    try { return handles.install(method, id, Object::new); }
                    catch (Throwable error) { throw new AssertionError(error); }
                }, callback -> NO_OP, () -> { });
        ControlCenterPluginHookInstaller.Result result = installer.install(owner);
        assertEquals("Layout evidence must come from real retained method registrations", layout,
                handles.hasHook(AdapterFixture.class, "distributeContent"));
        if (header == DateFixture.class) assertTrue(handles.hasHook(DateFixture.class, "updateDateVisibility"));
        if (header == CarrierFixture.class) assertTrue(handles.hasHook(CarrierFixture.class, "updateCarrierAndPrivacyVisible"));
        return result;
    }

    private static boolean reportMissing() throws Exception {
        Method method = SystemUiHooks.class.getDeclaredMethod("reportMissingControlCenterCapability");
        method.setAccessible(true);
        return (Boolean) method.invoke(null);
    }

    private static void assertReason(String reason) throws Exception {
        Map<?, ?> states = (Map<?, ?>) field(SystemUiHooks.class, "FEATURE_STATES").get(null);
        RuntimeApplicationPolicy.Result state = (RuntimeApplicationPolicy.Result) states.get(
                FusionActivationStatus.FEATURE_CONTROL_CENTER);
        assertNotNull(state);
        assertEquals(FusionActivationStatus.DEGRADED, state.state());
        assertEquals(reason, state.reason());
    }

    private ClassLoader loader() { return new ClassLoader(getClass().getClassLoader()) { }; }

    private Field preserveValue(Object owner, String name) throws Exception {
        Class<?> type = owner instanceof Class<?> value ? value : owner.getClass();
        Field target = field(type, name);
        values.put(target, target.get(owner instanceof Class<?> ? null : owner));
        return target;
    }

    @SuppressWarnings("unchecked")
    private void preserveMap(Object owner, String name) throws Exception {
        Class<?> type = owner instanceof Class<?> value ? value : owner.getClass();
        Map<Object, Object> map = (Map<Object, Object>) field(type, name)
                .get(owner instanceof Class<?> ? null : owner);
        maps.put(map, new HashMap<>(map));
        map.clear();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static final class ListFixture {
        void distributeTiles(boolean value) { }
        void onBindViewHolder(Object holder, Object item) { }
    }
    public static final class AdapterFixture {
        void distributeContent(boolean value) { }
        void onBindViewHolder(Object holder, Object item) { }
    }
    public static final class SpanFixture { int getSpanSize() { return 1; } }
    public static final class WindowFixture { void setBlurRatio(float ratio) { } }
    public static final class DateFixture { void updateDateVisibility() { } }
    public static final class CarrierFixture { void updateCarrierAndPrivacyVisible(Object miui, Object android) { } }
}
