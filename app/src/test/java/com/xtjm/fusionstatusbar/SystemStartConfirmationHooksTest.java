package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class SystemStartConfirmationHooksTest {
    @Test public void enablingSkipsTheNativeConfirmationGateAndAllowsStart() throws Throwable {
        int[] originalCalls = {0};
        Object result = SystemStartConfirmationHooks.interceptCheck(chain(false, originalCalls),
                MiscConfig.defaults().withRemoveChainStartDialog(true));
        assertEquals(Boolean.TRUE, result);
        assertEquals(0, originalCalls[0]);
    }

    @Test public void disablingPreservesBothNativeAllowAndDenyResults() throws Throwable {
        int[] originalCalls = {0};
        assertEquals(Boolean.FALSE, SystemStartConfirmationHooks.interceptCheck(
                chain(false, originalCalls), MiscConfig.defaults()));
        assertEquals(Boolean.TRUE, SystemStartConfirmationHooks.interceptCheck(
                chain(true, originalCalls), MiscConfig.defaults()));
        assertEquals(2, originalCalls[0]);
    }

    @Test public void unsupportedOemReturnTypesAndOtherPolicyMethodsAreNotHooked() {
        var selected = SystemStartConfirmationHooks.supportedMethods(OemLocalService.class);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().allMatch(method -> method.getReturnType() == boolean.class
                && method.getName().equals("checkAllowStartActivity")));
    }

    private static XposedInterface.Chain chain(boolean result, int[] originalCalls) {
        return (XposedInterface.Chain) Proxy.newProxyInstance(SystemStartConfirmationHooksTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("proceed")) { originalCalls[0]++; return result; }
                    return null;
                });
    }

    private static class OemLocalService {
        boolean checkAllowStartActivity(String source, String target) { return false; }
        boolean checkAllowStartActivity(String source, String target, int user) { return false; }
        int checkAllowStartActivity(int unrelated) { return 0; }
        boolean checkWakePath() { return false; }
    }
}
