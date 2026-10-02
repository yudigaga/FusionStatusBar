package com.xtjm.fusionstatusbar;

import java.lang.reflect.Method;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCenterHookPolicyTest {
    private static final class Fixture {
        private void notifyChanged(boolean first, boolean second) {
        }

        private void notifyChanged(boolean first) {
        }
    }

    @Test
    public void exactParameterMatchingDoesNotHookPartialOverload() throws Exception {
        Method exact = Fixture.class.getDeclaredMethod("notifyChanged", boolean.class,
                boolean.class);
        Method partial = Fixture.class.getDeclaredMethod("notifyChanged", boolean.class);

        assertTrue(ControlCenterHookPolicy.matchesMethod(exact, "notifyChanged",
                new Class<?>[] {boolean.class, boolean.class}, void.class));
        assertFalse(ControlCenterHookPolicy.matchesMethod(partial, "notifyChanged",
                new Class<?>[] {boolean.class, boolean.class}, void.class));
    }
}
