package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityCenterConfigTest {
    private static final String PREFIX = "security_center_";
    private static final String PRECISION = PREFIX + "screen_on_time_precision_minutes";
    private static final String[] FEATURES = {"smart_privacy", "battery_details", "score_666",
            "skip_ten_seconds", "game_three_second_launch", "game_fill_display", "video_more",
            "video_temperature_limit", "motion_sickness", "cinema_mode", "smart_charging",
            "bypass_charging_plus", "smart_refresh_rate", "image_enhancement", "charge_features",
            "aim_assist", "gyroscope_acceleration", "cooling_fan_ui", "game_sound_ui",
            "independent_display_ui", "super_resolution", "frame_interpolation", "game_hdr",
            "car_crash_detection", "skip_charge_dialogs", "screen_on_time", "hide_home_warnings"};

    @Test public void homeWarningsDefaultOffAndRemainIndependentOfTheCosmeticScore() {
        String homeWarnings = PREFIX + "hide_home_warnings";
        Bundle values = MiscConfig.defaults().toBundle();
        assertTrue("serialize the independent home display option", values.containsKey(homeWarnings));
        assertFalse(values.getBoolean(homeWarnings, true));
        values.putBoolean(homeWarnings, true);
        MiscConfig hidden = MiscConfig.fromBundle(values);
        assertTrue(hidden.toBundle().getBoolean(homeWarnings));
        assertFalse(hidden.isEnabled(SecurityCenterFeature.SCORE_666));
        Bundle changed = hidden.withSecurityFeature(SecurityCenterFeature.SCORE_666, true).toBundle();
        changed.putBoolean(homeWarnings, false);
        MiscConfig restored = MiscConfig.fromBundle(changed);
        assertFalse(restored.toBundle().getBoolean(homeWarnings, true));
        assertTrue(restored.isEnabled(SecurityCenterFeature.SCORE_666));
    }

    @Test public void homeWarningsSurviveTheAtomicStoreAndRejectNonBooleanValues() {
        String homeWarnings = PREFIX + "hide_home_warnings";
        Bundle values = MiscConfig.defaults().withScreenshotClipboardEnabled(true).toBundle();
        values.putBoolean(homeWarnings, true);
        MemoryFile file = new MemoryFile();
        assertTrue(MiscConfigStore.write(file, MiscConfig.fromBundle(values)));
        Bundle stored = MiscConfigStore.read(file).toBundle();
        assertTrue(stored.getBoolean(homeWarnings));
        assertTrue(stored.getBoolean(MiscConfig.KEY_SCREENSHOT_CLIPBOARD_ENABLED));
        stored.putString(homeWarnings, "true");
        assertFalse(MiscConfig.validUpdate(stored));
        assertFalse(MiscConfig.fromBundle(stored).toBundle().getBoolean(homeWarnings, true));
    }

    @Test public void allRequestedOptionsDefaultOffAndRoundTripWithoutLosingExistingOptions() {
        Bundle defaults = MiscConfig.defaults().toBundle();
        for (String name : FEATURES) {
            assertTrue("serialize the default for " + name, defaults.containsKey(PREFIX + name));
            assertFalse(defaults.getBoolean(PREFIX + name, true));
            defaults.putBoolean(PREFIX + name, true);
        }
        MiscConfig enabled = MiscConfig.fromBundle(defaults).withRemoveChainStartDialog(true)
                .withScreenshotClipboardEnabled(true);
        Bundle changed = enabled.toBundle();
        for (String name : FEATURES) assertTrue(name, changed.getBoolean(PREFIX + name));
        assertTrue(enabled.removeChainStartDialog);
        assertTrue(enabled.screenshotClipboardEnabled);
        changed.putBoolean(PREFIX + "score_666", false);
        changed.putString(PREFIX + "cinema_mode", "true");
        Bundle restored = MiscConfig.fromBundle(changed).toBundle();
        assertFalse(restored.getBoolean(PREFIX + "score_666"));
        assertFalse(restored.getBoolean(PREFIX + "cinema_mode"));
        assertTrue(restored.getBoolean(PREFIX + "smart_privacy"));
    }

    @Test public void displayGranularityDefaultsToOneMinuteAndClampsToOneThroughSixty() {
        Bundle values = MiscConfig.defaults().toBundle();
        assertEquals(1, values.getInt(PRECISION, -1));
        values.putInt(PRECISION, 0);
        assertEquals(1, MiscConfig.fromBundle(values).toBundle().getInt(PRECISION));
        values.putInt(PRECISION, 123);
        assertEquals(60, MiscConfig.fromBundle(values).toBundle().getInt(PRECISION));
        values.putInt(PRECISION, 7);
        assertEquals(7, MiscConfig.fromBundle(values).withScreenshotClipboardEnabled(true)
                .toBundle().getInt(PRECISION));
        values.putString(PRECISION, "7");
        assertEquals(1, MiscConfig.fromBundle(values).toBundle().getInt(PRECISION));
    }

    @Test public void hostOptionsAndPrecisionSurviveTheAtomicConfigurationStore() {
        Bundle values = MiscConfig.defaults().withRemoveChainStartDialog(true).toBundle();
        values.putBoolean(PREFIX + "smart_privacy", true);
        values.putBoolean(PREFIX + "screen_on_time", true);
        values.putInt(PRECISION, 5);
        MemoryFile file = new MemoryFile();
        assertTrue(MiscConfigStore.write(file, MiscConfig.fromBundle(values)));
        Bundle stored = MiscConfigStore.read(file).toBundle();
        assertTrue(stored.getBoolean(PREFIX + "smart_privacy"));
        assertTrue(stored.getBoolean(PREFIX + "screen_on_time"));
        assertEquals(5, stored.getInt(PRECISION));
        assertTrue(stored.getBoolean(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG));
    }

    @Test public void securityCenterMayReadConfigurationButMayNotWriteIt() {
        String own = BuildConfig.APPLICATION_ID;
        boolean allowed;
        try { FusionConfigProvider.enforceMiscReadCaller("com.miui.securitycenter", own); allowed = true; }
        catch (SecurityException denied) { allowed = false; }
        assertTrue("the scoped host needs read access", allowed);
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceCaller("com.miui.securitycenter", own, true));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceMiscReadCaller("other.application", own));
    }

    private static final class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        @Override public byte[] read() throws IOException {
            if (bytes == null) throw new FileNotFoundException();
            return bytes.clone();
        }
        @Override public void write(byte[] data) { bytes = data.clone(); }
    }
}
