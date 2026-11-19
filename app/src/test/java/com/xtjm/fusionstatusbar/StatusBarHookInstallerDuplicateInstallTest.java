package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StatusBarHookInstallerDuplicateInstallTest {
    @Test
    public void repeatedInstallDoesNotReportAnExistingMethodAsMissing() {
        Registrar registrar = new Registrar();
        StatusBarHookInstaller installer = new StatusBarHookInstaller(
                (name, loader) -> StatusBarHookInstaller.STATUS_WINDOW_CONTROLLER_CLASS.equals(name)
                        ? StatusWindowFixture.class : null,
                registrar,
                callback -> chain -> null);
        StatusBarHookInstaller.Targets targets = new StatusBarHookInstaller.Targets(
                null, null, null, null, null, StatusWindowFixture.class, null, List.of());

        assertEquals(3, installer.install(targets).layoutHooks);
        StatusBarHookInstaller.Result repeated = installer.install(targets);

        assertEquals(0, repeated.layoutHooks);
        assertFalse(repeated.missingMethods.contains("StatusWindowFixture#apply"));
    }

    private static final class Registrar implements StatusBarHookInstaller.Registrar {
        private final Set<String> installed = new HashSet<>();

        @Override
        public int install(String id, Method method, XposedInterface.Hooker hooker) {
            return installed.add(id + "@" + method.toGenericString()) ? 1 : 0;
        }
    }

    public static final class StatusWindowFixture {
        public Object getBarLayoutParamsForRotation(int rotation) { return null; }
        public void apply(Object state) { }
        public void refreshStatusBarHeight() { }
    }
}
