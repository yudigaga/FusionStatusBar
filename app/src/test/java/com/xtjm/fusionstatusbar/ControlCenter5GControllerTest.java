package com.xtjm.fusionstatusbar;

import android.telephony.TelephonyManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCenter5GControllerTest {
    @Test public void toggleOnlyChangesTheNrBitAndPreservesOtherAllowedModes() {
        FakeRadio radio = new FakeRadio(TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE, TelephonyManager.NETWORK_TYPE_BITMASK_LTE);

        ControlCenter5GController.State enabled = ControlCenter5GController.toggle(radio);
        assertTrue(enabled.available);
        assertTrue(enabled.enabled);
        assertEquals(TelephonyManager.NETWORK_TYPE_BITMASK_NR
                        | TelephonyManager.NETWORK_TYPE_BITMASK_LTE,
                radio.allowed);

        ControlCenter5GController.State disabled = ControlCenter5GController.toggle(radio);
        assertTrue(disabled.available);
        assertFalse(disabled.enabled);
        assertEquals(TelephonyManager.NETWORK_TYPE_BITMASK_LTE, radio.allowed);
    }

    @Test public void unsupportedNrAndDeniedRadioAccessAreReportedUnavailable() {
        FakeRadio unsupported = new FakeRadio(TelephonyManager.NETWORK_TYPE_BITMASK_LTE,
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE);
        assertFalse(ControlCenter5GController.read(unsupported).available);
        assertFalse(ControlCenter5GController.toggle(unsupported).available);
        assertEquals(0, unsupported.writes);

        FakeRadio denied = new FakeRadio(0, 0);
        denied.deny = true;
        assertFalse(ControlCenter5GController.read(denied).available);
        assertFalse(ControlCenter5GController.toggle(denied).available);
        assertEquals(0, denied.writes);
    }

    @Test public void disablingNrFallsBackToOtherSupportedTypesWhenNrWasTheOnlyAllowedMode() {
        FakeRadio radio = new FakeRadio(TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE, TelephonyManager.NETWORK_TYPE_BITMASK_NR);

        ControlCenter5GController.State disabled = ControlCenter5GController.toggle(radio);

        assertTrue(disabled.available);
        assertFalse(disabled.enabled);
        assertEquals(TelephonyManager.NETWORK_TYPE_BITMASK_LTE, radio.allowed);
    }

    private static final class FakeRadio implements ControlCenter5GController.RadioAccess {
        final long supported;
        long allowed;
        int writes;
        boolean deny;

        FakeRadio(long supported, long allowed) {
            this.supported = supported;
            this.allowed = allowed;
        }

        @Override public long supportedNetworkTypes() {
            if (deny) throw new SecurityException("denied");
            return supported;
        }

        @Override public long allowedNetworkTypes() {
            if (deny) throw new SecurityException("denied");
            return allowed;
        }

        @Override public void setAllowedNetworkTypes(long value) {
            if (deny) throw new SecurityException("denied");
            writes++;
            allowed = value;
        }
    }
}
