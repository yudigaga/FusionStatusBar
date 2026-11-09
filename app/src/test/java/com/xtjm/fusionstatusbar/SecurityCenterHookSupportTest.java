package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityCenterHookSupportTest {
    @Test public void enabledValueDoesNotRunTheOriginalAndDisabledValueDoes() throws Throwable {
        int[] calls = {0};
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> {
                    if ("proceed".equals(method.getName())) { calls[0]++; return "native"; }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(Boolean.FALSE, SecurityCenterHookSupport.interceptValue(chain, true, Boolean.FALSE));
        assertEquals(0, calls[0]);
        assertEquals("native", SecurityCenterHookSupport.interceptValue(chain, false, Boolean.TRUE));
        assertEquals(1, calls[0]);
        assertNull(SecurityCenterHookSupport.interceptValue(chain, true, null));
        assertEquals(1, calls[0]);
    }

    @Test public void sharedGateRemainsOnWhileEitherIndependentFeatureNeedsIt() {
        MiscConfig defaults = MiscConfig.defaults();
        SecurityCenterFeature[] shared = {SecurityCenterFeature.SMART_CHARGING,
                SecurityCenterFeature.BYPASS_CHARGING_PLUS};
        assertFalse(SecurityCenterHookSupport.anyEnabled(defaults, shared));
        assertTrue(SecurityCenterHookSupport.anyEnabled(defaults.withSecurityFeature(shared[0], true), shared));
        assertTrue(SecurityCenterHookSupport.anyEnabled(defaults.withSecurityFeature(shared[1], true), shared));
        assertFalse(SecurityCenterHookSupport.anyEnabled(defaults.withSecurityFeature(
                SecurityCenterFeature.CINEMA_MODE, true), shared));
    }

    @Test public void replacementTypesCannotCoerceAnUnrelatedHostReturnType() {
        assertTrue(SecurityCenterHookSupport.valueMatchesType(boolean.class, Boolean.TRUE));
        assertTrue(SecurityCenterHookSupport.valueMatchesType(int.class, 666));
        assertTrue(SecurityCenterHookSupport.valueMatchesType(void.class, null));
        assertFalse(SecurityCenterHookSupport.valueMatchesType(boolean.class, 1));
        assertFalse(SecurityCenterHookSupport.valueMatchesType(int.class, 666L));
        assertFalse(SecurityCenterHookSupport.valueMatchesType(void.class, Boolean.TRUE));
        assertFalse(SecurityCenterHookSupport.valueMatchesType(boolean.class, null));
    }
}
