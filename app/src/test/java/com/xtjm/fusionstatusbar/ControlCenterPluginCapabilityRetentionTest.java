package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
public class ControlCenterPluginCapabilityRetentionTest {
    private static final XposedInterface.Hooker NO_OP = chain -> chain.proceed();
    private final Map<Field, Object> values = new HashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> maps = new java.util.IdentityHashMap<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private ControlCenterPluginCapabilities capabilities;
    private final ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
            .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);

    @Before public void isolateRuntimeState() throws Exception {
        capabilities = (ControlCenterPluginCapabilities) field(SystemUiHooks.class,
                "CONTROL_CENTER_PLUGIN_CAPABILITIES").get(null);
        preserveValue(capabilities, "active");
        preserveMap(capabilities, "entries");
        Field scheduler = preserveValue(capabilities, "scheduler");
        scheduler.set(capabilities, (BoundedHookRetry.Scheduler) (task, delay) -> scheduled.add(task));
        for (String name : new String[] {"module", "loader", "currentConfig", "configLifecycle"}) {
            Field target = preserveValue(SystemUiHooks.class, name);
            target.set(null, name.equals("currentConfig")
                    ? FusionConfig.defaults().withControlCenter(settings) : null);
        }
        preserveMap(SystemUiHooks.class, "FEATURE_STATES");
        preserveMap(SystemUiHooks.class, "RECENT_DIAGNOSTICS");
        Object handles = field(SystemUiHooks.class, "HANDLES").get(null);
        preserveMap(handles, "handles");
        preserveMap(handles, "failures");
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
        scheduled.clear();
    }

    @Test public void failedBackgroundProbeMustNotReplaceTheWorkingPanelsCapability() throws Exception {
        ClassLoader workingLoader = new ClassLoader(getClass().getClassLoader()) { };
        ControlCenterPluginHookInstaller.Result working = installedCapability(workingLoader, true);
        assertEquals("The setup must install every capability required by the glass settings", "",
                working.missingRequiredCapability(settings));
        capabilities.record(workingLoader, working);
        capabilities.activate(workingLoader);

        // This is the actual installation method used by the production retry callback,
        // not a test reimplementation of its activation policy.
        ClassLoader unrelatedLoader = new ClassLoader(null) { };
        Method probe = SystemUiHooks.class.getDeclaredMethod("hookControlCenterPlugin", ClassLoader.class);
        probe.setAccessible(true);
        assertEquals("The unrelated loader installs no plugin hooks", 0,
                ((Integer) probe.invoke(null, unrelatedLoader)).intValue());
        assertFalse("The failed probe must retain its bounded retry evidence", scheduled.isEmpty());

        assertSame("An unsuccessful background probe must not steal the mounted panel's capability",
                working, capabilities.activeResult());
        Method missing = SystemUiHooks.class.getDeclaredMethod("reportMissingControlCenterCapability");
        missing.setAccessible(true);
        assertFalse("A failed unrelated probe must not report the working panel as degraded",
                (Boolean) missing.invoke(null));
    }

    @Test public void explicitReplacementWithAnUnavailableHostMustExposeItsOwnMissingHooks() throws Exception {
        ClassLoader previousLoader = new ClassLoader(getClass().getClassLoader()) { };
        ControlCenterPluginHookInstaller.Result previous = installedCapability(previousLoader, true);
        capabilities.record(previousLoader, previous);
        capabilities.activate(previousLoader);

        ClassLoader replacementLoader = new ClassLoader(null) { };
        ControlCenterPluginHookInstaller.Result replacement = installedCapability(replacementLoader, false);
        assertFalse(replacement.layoutAvailable);
        capabilities.record(replacementLoader, replacement);
        capabilities.activate(replacementLoader);

        assertSame("An actual host replacement must not borrow stale previous-host capability",
                replacement, capabilities.activeResult());
        assertEquals("control_center_layout_hooks_unavailable",
                capabilities.activeResult().missingRequiredCapability(settings));
    }

    @Test public void firstCapableEmbeddedHostCanClaimOwnershipBeforeAnyPluginActivation() {
        ControlCenterPluginCapabilities registry = new ControlCenterPluginCapabilities(
                (task, delay) -> false);
        ClassLoader unavailable = new ClassLoader(null) { };
        registry.record(unavailable, installedCapability(unavailable, false));
        registry.activateIfUnclaimed(unavailable);
        assertNull("An unsuccessful initial probe must not claim the active host", registry.activeResult());

        ClassLoader embedded = new ClassLoader(getClass().getClassLoader()) { };
        ControlCenterPluginHookInstaller.Result builtIn = installedCapability(embedded, true);
        registry.record(embedded, builtIn);
        registry.activateIfUnclaimed(embedded);
        assertSame("An embedded host must not remain pending while waiting for a plugin callback",
                builtIn, registry.activeResult());

        ClassLoader anotherCapableLoader = new ClassLoader(getClass().getClassLoader()) { };
        registry.record(anotherCapableLoader, installedCapability(anotherCapableLoader, true));
        registry.activateIfUnclaimed(anotherCapableLoader);
        assertSame("A later probe does not establish actual replacement-host ownership",
                builtIn, registry.activeResult());
    }

    private static ControlCenterPluginHookInstaller.Result installedCapability(ClassLoader owner,
            boolean available) {
        RuntimeHookRegistry<Object> handles = new RuntimeHookRegistry<>();
        Map<String, Class<?>> classes = available ? Map.of(
                "miui.systemui.controlcenter.panel.main.qs.QSListController", ListFixture.class,
                "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", AdapterFixture.class,
                "miui.systemui.controlcenter.panel.main.qs.QSRecord", SpanFixture.class,
                "miui.systemui.controlcenter.windowview.ControlCenterWindowViewController", WindowFixture.class,
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerController", SpanFixture.class) : Map.of();
        ControlCenterPluginHookInstaller installer = new ControlCenterPluginHookInstaller(
                (name, loader) -> loader == owner ? classes.get(name) : null,
                (id, method, hooker) -> {
                    try { return handles.install(method, id, Object::new); }
                    catch (Throwable error) { throw new AssertionError(error); }
                }, callback -> NO_OP, () -> { });
        ControlCenterPluginHookInstaller.Result result = installer.install(owner);
        assertEquals("The installer counts actual retained method registrations",
                available, handles.hasHook(AdapterFixture.class, "distributeContent"));
        return result;
    }

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
}
