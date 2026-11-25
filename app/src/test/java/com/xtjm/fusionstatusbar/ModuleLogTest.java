package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ModuleLogTest {
    @After public void resetGate() throws ReflectiveOperationException {
        var injected = ModuleLog.class.getDeclaredField("injected");
        injected.setAccessible(true);
        injected.setBoolean(null, false);
        ModuleLog.awaitConfiguration();
        ModuleLog.configure(true);
    }

    @Test public void startupBeforeThePreferenceIsKnownDoesNotEmitModuleDiagnostics() {
        new FusionModule().onModuleLoaded(new XposedModuleInterface.ModuleLoadedParam() {
            @Override public boolean isSystemServer() { return false; }
            @Override public String getProcessName() { return "com.android.systemui"; }
        });
        int before = ShadowLog.getLogsForTag(FusionModule.TAG).size();
        SystemUiHooks.runtimeGridLog(Log.WARN, "unconfirmed startup", new IllegalStateException());
        assertEquals(before, ShadowLog.getLogsForTag(FusionModule.TAG).size());
    }

    @Test public void providerReturnsTheExplicitDefaultInsteadOfAnUnavailableSnapshot() {
        FusionConfigProvider provider = Robolectric.buildContentProvider(FusionConfigProvider.class)
                .create("fusion.test.config").get();
        shadowOf(provider).setCallingPackage(provider.getContext().getPackageName());
        Bundle snapshot = provider.call("get_module_logging", null, null);
        assertNotNull(snapshot);
        assertTrue(snapshot.getBoolean("enabled"));
    }

    @Test public void committedSettingSurvivesReloadAndTheProviderOnlyAcceptsScopedCallers() {
        Context app = RuntimeEnvironment.getApplication();
        FusionConfigProvider provider = Robolectric.buildContentProvider(FusionConfigProvider.class)
                .create("fusion.test.config").get();
        assertTrue(ModuleLoggingConfig.save(app, false));
        ModuleLog.configure(true);
        for (String caller : new String[] {app.getPackageName(), "com.android.systemui", "android",
                "com.miui.home", "com.miui.securitycenter", "miui.systemui.plugin"}) {
            shadowOf(provider).setCallingPackage(caller);
            Bundle snapshot = provider.call("get_module_logging", null, null);
            assertTrue(snapshot.containsKey("enabled"));
            assertFalse(snapshot.getBoolean("enabled"));
            assertTrue(ModuleLoggingConfig.applySnapshot(snapshot));
            assertFalse(ModuleLog.acceptsDiagnostics());
        }
        shadowOf(provider).setCallingPackage("unrelated.app");
        assertThrows(SecurityException.class, () -> provider.call("get_module_logging", null, null));
        assertTrue(ModuleLoggingConfig.save(app, true));
        assertEquals(Boolean.TRUE, ModuleLoggingConfig.read(app));
        assertTrue(ModuleLog.acceptsDiagnostics());
    }

    @Test public void logcatAndBothFrameworkOverloadsStopAndResumeWithoutOffPeriodReplay() {
        List<List<Object>> framework = new ArrayList<>();
        FusionModule module = moduleRecordingFrameworkLogs(framework);
        assertTrue(ModuleLoggingConfig.applySnapshot(snapshot(true)));
        writeAllOutputs(module, "first");
        assertTrue(ModuleLoggingConfig.applySnapshot(snapshot(false)));
        assertFalse(ModuleLoggingConfig.applySnapshot(null));
        assertFalse(ModuleLoggingConfig.applySnapshot(new Bundle()));
        writeAllOutputs(module, "muted");
        assertTrue(ModuleLoggingConfig.applySnapshot(snapshot(true)));
        writeAllOutputs(module, "last");

        List<ShadowLog.LogItem> output = ShadowLog.getLogsForTag("FusionLogTest");
        assertEquals(16, output.size());
        assertEquals(List.of(Log.DEBUG, Log.INFO, Log.WARN, Log.WARN, Log.ERROR, Log.ERROR,
                Log.VERBOSE, Log.INFO), output.subList(0, 8).stream().map(item -> item.type).toList());
        assertFalse(output.stream().anyMatch(item -> item.msg.contains("muted")));
        assertEquals(4, framework.size());
        assertEquals(List.of(Log.INFO, "FusionLogTest", "first"), framework.get(0));
        assertTrue(framework.get(1).get(3) instanceof IllegalStateException);
        assertEquals(List.of(Log.INFO, "FusionLogTest", "last"), framework.get(2));
    }

    @Test public void onlyAnEnabledFirstSnapshotReplaysTheBoundedStartupBuffer() {
        List<List<Object>> framework = new ArrayList<>();
        FusionModule module = moduleRecordingFrameworkLogs(framework);
        ModuleLog.awaitConfiguration();
        writeAllOutputs(module, "disabled startup");
        ModuleLoggingConfig.applySnapshot(snapshot(false));
        ModuleLoggingConfig.applySnapshot(snapshot(true));
        assertTrue(framework.isEmpty());
        assertTrue(ShadowLog.getLogsForTag("FusionLogTest").isEmpty());

        ModuleLog.awaitConfiguration();
        for (int i = 0; i < 60; i++)
            FusionModule.logModule(module, Log.INFO, "FusionLogTest", "startup " + i);
        assertTrue(framework.isEmpty());
        ModuleLoggingConfig.applySnapshot(snapshot(true));
        assertEquals(48, framework.size());
        assertEquals("startup 0", framework.get(0).get(2));
        assertEquals("startup 47", framework.get(47).get(2));
    }

    @Test public void configNotificationRefreshesAnAlreadyAttachedProcess() {
        Context app = RuntimeEnvironment.getApplication();
        FusionConfigProvider provider = Robolectric.buildContentProvider(FusionConfigProvider.class)
                .create(ModuleLoggingConfig.URI.getAuthority()).get();
        shadowOf(provider).setCallingPackage("com.android.systemui");
        var handler = new Handler(Looper.getMainLooper());
        ModuleLog.awaitConfiguration();
        new ModuleLoggingConfig.Subscription(app, handler).subscribe();
        assertTrue(ModuleLog.acceptsDiagnostics());

        assertTrue(ModuleLoggingConfig.save(app, false));
        // Simulate the other process's gate still holding its previous value.
        ModuleLog.configure(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(ModuleLog.acceptsDiagnostics());

        assertTrue(ModuleLoggingConfig.save(app, true));
        ModuleLog.configure(false);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ModuleLog.acceptsDiagnostics());
    }

    private static Bundle snapshot(boolean enabled) {
        Bundle bundle = new Bundle();
        bundle.putBoolean("enabled", enabled);
        return bundle;
    }

    private static FusionModule moduleRecordingFrameworkLogs(List<List<Object>> output) {
        FusionModule module = new FusionModule();
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(), new Class<?>[] { XposedInterface.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("log")) output.add(List.of(arguments));
                    return null;
                });
        module.attachFramework(framework, () -> { });
        return module;
    }

    private static void writeAllOutputs(FusionModule module, String message) {
        String tag = "FusionLogTest";
        Throwable error = new IllegalStateException("diagnostic");
        ModuleLog.d(tag, message, error);
        ModuleLog.i(tag, message);
        ModuleLog.w(tag, message);
        ModuleLog.w(tag, message, error);
        ModuleLog.e(tag, message);
        ModuleLog.e(tag, message, error);
        ModuleLog.println(Log.VERBOSE, tag, message);
        ModuleLog.println(Log.INFO, tag, message);
        FusionModule.logModule(module, Log.INFO, tag, message);
        FusionModule.logModule(module, Log.ERROR, tag, message, error);
    }
}
