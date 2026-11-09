package com.xtjm.fusionstatusbar;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityCenterBatteryHooksTest {
    @Test public void realMicrosecondsAreRoundedDownWithoutInventingMissingData() {
        assertEquals(-1L, SecurityCenterBatteryHooks.screenOnMinutes(-1L, 1));
        assertEquals(0L, SecurityCenterBatteryHooks.screenOnMinutes(0L, 1));
        assertEquals(62L, SecurityCenterBatteryHooks.screenOnMinutes(3_750_000_000L, 1));
        assertEquals(60L, SecurityCenterBatteryHooks.screenOnMinutes(3_750_000_000L, 5));
        assertEquals(60L, SecurityCenterBatteryHooks.screenOnMinutes(3_750_000_000L, 60));
        assertEquals(62L, SecurityCenterBatteryHooks.screenOnMinutes(3_750_000_000L, 0));
    }

    @Test public void referenceTimeKeepsHoursMinutesAndUnknownSeparate() {
        assertEquals("--", SecurityCenterBatteryHooks.screenOnText(-1));
        assertEquals("0分钟", SecurityCenterBatteryHooks.screenOnText(0));
        assertEquals("5分钟", SecurityCenterBatteryHooks.screenOnText(5));
        assertEquals("2小时5分钟", SecurityCenterBatteryHooks.screenOnText(125));
    }

    @Test public void onlyBatteryInformationRowsAreRetainedNotProtectionOrWarningRows() {
        String group = "preference_key_category_battery_info";
        assertTrue(SecurityCenterBatteryHooks.keepBatteryPreference(group, "reference_cycle_count"));
        assertTrue(SecurityCenterBatteryHooks.keepBatteryPreference(group, "reference_current_temp"));
        assertFalse(SecurityCenterBatteryHooks.keepBatteryPreference("other_group", "reference_cycle_count"));
        assertFalse(SecurityCenterBatteryHooks.keepBatteryPreference(group, "reference_battery_health_low_tip"));
        assertFalse(SecurityCenterBatteryHooks.keepBatteryPreference(group, "cb_always_charge_protect"));
        assertFalse(SecurityCenterBatteryHooks.keepBatteryPreference(group, null));
    }

    @Test public void skippingWirelessHintRunsTheKeepActionInsteadOfTheCloseAction() {
        ChargingDialog dialog = new ChargingDialog();
        assertTrue(SecurityCenterBatteryHooks.keepWirelessCharge(dialog));
        assertEquals(1, dialog.kept);
        assertEquals(0, dialog.closed);
        assertFalse(SecurityCenterBatteryHooks.keepWirelessCharge(new Object()));
        assertFalse(SecurityCenterBatteryHooks.keepWirelessCharge(null));
    }

    @Test public void malformedOrMissingBatteryDatesRemainUnknown() {
        assertEquals("2024-02-29", SecurityCenterBatteryHooks.batteryDate("20240229"));
        assertEquals("--", SecurityCenterBatteryHooks.batteryDate("20260230"));
        assertEquals("--", SecurityCenterBatteryHooks.batteryDate("00000000"));
        assertEquals("--", SecurityCenterBatteryHooks.batteryDate(null));
    }

    @Test public void rawMicroampHoursBecomeMahWithoutTurningUnknownValuesIntoZero() {
        assertEquals("5000 mAh", SecurityCenterBatteryHooks.capacityText(5_000_000));
        assertEquals("--", SecurityCenterBatteryHooks.capacityText(-1));
        assertEquals("--", SecurityCenterBatteryHooks.capacityText(null));
        assertEquals("--", SecurityCenterBatteryHooks.capacityText("unknown"));
    }

    @Test public void disablingChargeOptionsRestoresTheSameFragmentsNativeCapabilities() {
        var state = new SecurityCenterBatteryHooks.ChargeOptionsState();
        boolean[] enabled = state.apply(true, false, true);
        assertArrayEquals(new boolean[] {true, true}, enabled);
        assertArrayEquals(new boolean[] {false, true}, state.apply(false, enabled[0], enabled[1]));
    }

    @Test public void rebuildingChargePreferencesReplacesThePreviousNativeSnapshot() {
        var state = new SecurityCenterBatteryHooks.ChargeOptionsState();
        state.apply(true, false, true);
        state.resetForNativeInitialization();
        boolean[] enabled = state.apply(true, true, false);
        assertArrayEquals(new boolean[] {true, false}, state.apply(false, enabled[0], enabled[1]));
    }

    @Test public void referenceResultsAreRejectedAfterThePreferenceTreeChanges() {
        Object owner = new Object();
        Object group = new Object();
        var request = new SecurityCenterBatteryHooks.ReferenceRequest(owner, group, 1L, 5);
        assertTrue(request.accepts(owner, group, 1L, 5));
        assertFalse(request.accepts(new Object(), group, 1L, 5));
        assertFalse(request.accepts(owner, new Object(), 1L, 5));
    }

    @Test public void referenceResultsAreRejectedAfterANewerRefreshRequest() {
        Object owner = new Object();
        Object group = new Object();
        var request = new SecurityCenterBatteryHooks.ReferenceRequest(owner, group, 1L, 5);
        assertFalse(request.accepts(owner, group, 2L, 5));
    }

    @Test public void referenceResultsAreRejectedAfterTheDisplayPrecisionChanges() {
        Object owner = new Object();
        Object group = new Object();
        var request = new SecurityCenterBatteryHooks.ReferenceRequest(owner, group, 1L, 5);
        assertFalse(request.accepts(owner, group, 1L, 10));
    }

    @Test public void chargeLayoutTransitionsRestoreHiddenRowsAndRemoveStaleNativeIndices() throws Exception {
        ChargeFragment fragment = new ChargeFragment();
        fragment.p = false;
        fragment.q = true;
        fragment.category.visible = false;
        fragment.dropdown.visible = false;
        fragment.B.put(8, "stale-native-entry");
        var state = new SecurityCenterBatteryHooks.ChargeOptionsState();
        SecurityCenterBatteryHooks.prepareChargeOptions(fragment, state, true);
        assertTrue(fragment.p);
        assertTrue(fragment.q);
        assertTrue(fragment.category.visible);
        assertTrue(fragment.dropdown.visible);
        assertTrue(fragment.B.isEmpty());
        fragment.B.put(0, "WIRED_STANDARD");
        fragment.B.put(1, "WIRED_LOW_FAST");
        fragment.B.put(2, "WIRED_TOP_SPEED_FAST");
        SecurityCenterBatteryHooks.prepareChargeOptions(fragment, state, false);
        assertFalse(fragment.p);
        assertTrue(fragment.q);
        assertTrue(fragment.B.isEmpty());
    }

    @Test public void disabledChargeLayoutLeavesNativeFieldsRowsAndIndicesUntouched() throws Exception {
        ChargeFragment fragment = new ChargeFragment();
        fragment.p = false;
        fragment.q = true;
        fragment.category.visible = false;
        fragment.dropdown.visible = false;
        fragment.B.put(1, "WIRED_LOW_FAST");
        SecurityCenterBatteryHooks.prepareChargeOptions(fragment,
                new SecurityCenterBatteryHooks.ChargeOptionsState(), false);
        assertFalse(fragment.p);
        assertTrue(fragment.q);
        assertFalse(fragment.category.visible);
        assertFalse(fragment.dropdown.visible);
        assertEquals(Map.of(1, "WIRED_LOW_FAST"), fragment.B);
    }

    public static final class ChargeFragment {
        boolean p;
        boolean q;
        final Map<Integer, String> B = new HashMap<>();
        final ChargeRow category = new ChargeRow();
        final ChargeRow dropdown = new ChargeRow();
        public ChargeRow findPreference(CharSequence key) {
            return "pref_key_charge_protection_wired_charge_category".contentEquals(key)
                    ? category : dropdown;
        }
    }

    public static final class ChargeRow {
        boolean visible;
        public void setVisible(boolean value) { visible = value; }
    }

    public static final class ChargingDialog {
        int kept;
        int closed;
        public Action getButton(int which) {
            return new Action(which == -2 ? () -> kept++ : () -> closed++);
        }
    }

    public static final class Action {
        final Runnable action;
        Action(Runnable action) { this.action = action; }
        public boolean performClick() { action.run(); return true; }
    }
}
