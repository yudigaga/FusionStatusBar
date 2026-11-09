package com.xtjm.fusionstatusbar;

import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityCenterGameVideoHooksTest {
    private static final String KING = "com.tencent.tmgp.sgame";

    @Test public void startupBoostIsOnlyAppliedToKingAndCleanupRestoresItsPriorValue() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        assertFalse(boost.intercept(true, "other.game", true));
        assertEquals("false", property.value);
        assertTrue(boost.intercept(true, KING, true));
        assertEquals("true", property.value);
        assertTrue(boost.intercept(true, KING, false));
        assertEquals("false", property.value);
    }

    @Test public void disablingTheFeatureImmediatelyRevokesOnlyTheOwnedBoost() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        boost.intercept(true, KING, true);
        boost.revoke();
        assertEquals("false", property.value);
        assertFalse(boost.intercept(false, KING, true));
        assertEquals("false", property.value);
    }

    @Test public void nativeCleanupStillRestoresWhenTheSwitchWasTurnedOff() throws Exception {
        MemoryProperty property = new MemoryProperty("old-value");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        boost.intercept(true, KING, true);
        assertTrue(boost.intercept(false, KING, false));
        assertEquals("old-value", property.value);
    }

    @Test public void switchingToAnotherGameRevokesTheOldOwnershipBeforeNativeHandling() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        boost.intercept(true, KING, true);
        assertFalse(boost.intercept(true, "other.game", true));
        assertEquals("false", property.value);
    }

    @Test public void laterHostChangesAreNotOverwrittenByModuleCleanup() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        boost.intercept(true, KING, true);
        property.value = "changed-by-host";
        boost.revoke();
        assertEquals("changed-by-host", property.value);
    }

    @Test public void repeatedStartsDoNotReplaceTheOriginalSnapshotWithTheOwnedValue() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        boost.intercept(true, KING, true);
        boost.intercept(true, KING, true);
        boost.revoke();
        assertEquals("false", property.value);
    }

    @Test public void aPreexistingTrueValueIsNotOwnedAndDoesNotSuppressTheHostsStop() throws Exception {
        MemoryProperty property = new MemoryProperty("true");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        assertTrue(boost.intercept(true, KING, true));
        assertFalse("without changing the property the module must let the native stop run",
                boost.intercept(true, KING, false));
        assertEquals("true", property.value);
    }

    @Test public void failedStartupWritesDoNotClaimOwnership() throws Exception {
        MemoryProperty property = new MemoryProperty("false");
        var boost = new SecurityCenterGameVideoHooks.LaunchBoost(property);
        property.failWrites = true;
        assertThrows(ReflectiveOperationException.class, () -> boost.intercept(true, KING, true));
        property.failWrites = false;
        property.value = "new-host-value";
        boost.revoke();
        assertEquals("new-host-value", property.value);
    }

    @Test public void packageQualificationRequiresAnActualCurrentPackage() {
        assertTrue(SecurityCenterGameVideoHooks.unlockPackage(true, "com.example.video"));
        assertFalse(SecurityCenterGameVideoHooks.unlockPackage(false, "com.example.video"));
        assertFalse(SecurityCenterGameVideoHooks.unlockPackage(true, null));
        assertFalse(SecurityCenterGameVideoHooks.unlockPackage(true, ""));
        assertFalse(SecurityCenterGameVideoHooks.unlockPackage(true, "  \t\n"));
    }

    private static final class MemoryProperty implements SecurityCenterGameVideoHooks.BootProperty {
        String value;
        boolean failWrites;
        MemoryProperty(String initial) { value = initial; }
        public String read() { return value; }
        public void write(String next) throws ReflectiveOperationException {
            if (failWrites) throw new ReflectiveOperationException("host rejected property write");
            value = next;
        }
    }
}
