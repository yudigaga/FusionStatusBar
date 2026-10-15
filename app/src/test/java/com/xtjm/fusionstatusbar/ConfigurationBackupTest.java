package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class ConfigurationBackupTest {
    @Test public void networkVerticalOffsetsRoundTripAndPreviousSchemaSixBackupDefaultsToZero() throws Exception {
        FusionConfig source = FusionConfig.defaults().withFusionWifiOffsetYPercent(-6)
                .withFusionMobileOffsetYPercent(11).withFusionContentScale(FusionContentConfig.NETWORK, 125);
        byte[] bytes = ConfigurationBackupCodec.encode(source);
        FusionConfig restored = ConfigurationBackupCodec.decode(bytes);
        assertEquals(-6, restored.fusionContent.wifiOffsetYPercent);
        assertEquals(11, restored.fusionContent.mobileOffsetYPercent);
        JSONObject backup = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONObject previous = backup.getJSONObject("config");
        previous.remove(FusionContentConfig.KEY_WIFI_OFFSET_Y_PERCENT);
        previous.remove(FusionContentConfig.KEY_MOBILE_OFFSET_Y_PERCENT);
        restored = decode(backup.toString());
        assertEquals(6, restored.toBundle().getInt(FusionConfig.KEY_SCHEMA_VERSION));
        assertEquals(0, restored.fusionContent.wifiOffsetYPercent);
        assertEquals(0, restored.fusionContent.mobileOffsetYPercent);
        assertEquals(125, restored.fusionContent.networkScale);
        previous.remove(FusionContentConfig.KEY_NETWORK_SCALE);
        assertThrows(IOException.class, () -> decode(backup.toString()));
    }

    @Test public void backupRejectsOutOfRangeOrWrongTypeNetworkOffsets() throws Exception {
        JSONObject backup = new JSONObject(new String(ConfigurationBackupCodec.encode(FusionConfig.defaults()),
                StandardCharsets.UTF_8));
        JSONObject config = backup.getJSONObject("config");
        config.put(FusionContentConfig.KEY_WIFI_OFFSET_Y_PERCENT, -21);
        assertThrows(IOException.class, () -> decode(backup.toString()));
        config.put(FusionContentConfig.KEY_WIFI_OFFSET_Y_PERCENT, 0);
        config.put(FusionContentConfig.KEY_MOBILE_OFFSET_Y_PERCENT, 21);
        assertThrows(IOException.class, () -> decode(backup.toString()));
        config.put(FusionContentConfig.KEY_MOBILE_OFFSET_Y_PERCENT, "3");
        assertThrows(IOException.class, () -> decode(backup.toString()));
    }

    @Test public void fusionContentOrderAndIndependentSizesSurviveBackupAndSchemaFiveMigration() throws Exception {
        FusionConfig source = FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.NETWORK, FusionContentConfig.BOTTOM)
                .withFusionContentScale(FusionContentConfig.BATTERY, 75)
                .withFusionContentScale(FusionContentConfig.NETWORK, 125)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 90).withIconScale(115);
        byte[] backup = ConfigurationBackupCodec.encode(source);
        FusionConfig restored = ConfigurationBackupCodec.decode(backup);
        assertTrue(source.fusionContent.sameAs(restored.fusionContent));
        assertEquals(115, restored.iconScale);
        JSONObject document = new JSONObject(new String(backup, StandardCharsets.UTF_8));
        JSONObject previous = document.getJSONObject("config");
        previous.put(FusionConfig.KEY_SCHEMA_VERSION, 5);
        for (String key : new String[] {FusionContentConfig.KEY_BATTERY_POSITION,
                FusionContentConfig.KEY_NETWORK_POSITION, FusionContentConfig.KEY_SIGNAL_POSITION,
                FusionContentConfig.KEY_BATTERY_SCALE, FusionContentConfig.KEY_NETWORK_SCALE,
                FusionContentConfig.KEY_SIGNAL_SCALE}) previous.remove(key);
        assertTrue(FusionContentConfig.defaults().sameAs(decode(document.toString()).fusionContent));
        assertEquals(115, decode(document.toString()).iconScale);
    }

    @Test public void currentBackupRejectsConflictingContentSlotsAndIncompleteContentSettings() throws Exception {
        JSONObject backup = new JSONObject(new String(ConfigurationBackupCodec.encode(FusionConfig.defaults()),
                StandardCharsets.UTF_8));
        JSONObject config = backup.getJSONObject("config");
        config.put(FusionContentConfig.KEY_NETWORK_POSITION, FusionContentConfig.TOP);
        assertThrows(IOException.class, () -> decode(backup.toString()));
        config.put(FusionContentConfig.KEY_NETWORK_POSITION, FusionContentConfig.MIDDLE);
        config.remove(FusionContentConfig.KEY_NETWORK_SCALE);
        assertThrows(IOException.class, () -> decode(backup.toString()));
    }

    @Test public void exportedFullConfigurationRoundTripsAppearanceLayoutsAndRevision() throws Exception {
        ControlCenterConfig center = ControlCenterConfig.defaults().withEnabled(true).withColumns(5)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(5).forPublication(true).encode());
        FusionConfig source = FusionConfig.defaults().withIconScale(115).withStrokeScale(125)
                .withStatusClockSizeSp(21).withControlCenter(center).withRevision(87);
        FusionConfig restored = ConfigurationBackupCodec.read(new ByteArrayInputStream(ConfigurationBackupCodec.encode(source)));
        Bundle expected = source.toBundle(), actual = restored.toBundle();
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) assertEquals(key, expected.get(key), actual.get(key));
    }

    @Test public void earlierBareSchemaMigratesLegacyBatteryAndTelemetry() throws Exception {
        FusionConfig restored = decode("{\"config_schema_version\":1,\"battery_scale\":125,\"telemetry_enabled_0\":true}");
        assertEquals(125, restored.iconScale);
        assertTrue(restored.telemetry.enabled(TelemetryConfig.TEMPERATURES));
        assertEquals(FusionConfig.CONFIG_SCHEMA_VERSION, restored.toBundle().getInt(FusionConfig.KEY_SCHEMA_VERSION));
    }

    @Test public void previousCompleteBackupsAndPresetsDefaultTheNewGlobalSurface() throws Exception {
        JSONObject full = new JSONObject(new String(ConfigurationBackupCodec.encode(FusionConfig.defaults()), StandardCharsets.UTF_8));
        JSONObject priorConfig = full.getJSONObject("config");
        priorConfig.put(FusionConfig.KEY_SCHEMA_VERSION, 4);
        priorConfig.remove(FusionConfig.KEY_STATUS_CLOCK_SIZE);
        priorConfig.remove(ControlCenterConfig.KEY_HIDE_DATE);
        priorConfig.remove(ControlCenterConfig.KEY_HIDE_CARRIER);
        priorConfig.remove(ControlCenterConfig.KEY_SURFACE);
        priorConfig.remove(ControlCenterConfig.KEY_BORDER_ENABLED);
        priorConfig.remove(ControlCenterConfig.KEY_BORDER_WIDTH);
        assertEquals(ControlCenterSurface.DEFAULT, decode(full.toString()).controlCenter.surface);
        assertFalse(decode(full.toString()).controlCenter.borderEnabled);
        assertEquals(1, decode(full.toString()).controlCenter.borderWidthDp);
        assertEquals(0, decode(full.toString()).statusClockSizeSp);
        JSONObject preset = ConfigurationBackupCodec.configJson(ControlCenterConfig.defaults().withTileScale(115));
        preset.remove(ControlCenterConfig.KEY_SURFACE);
        preset.remove(ControlCenterConfig.KEY_BORDER_ENABLED);
        preset.remove(ControlCenterConfig.KEY_BORDER_WIDTH);
        var restored = ConfigurationBackupCodec.controlCenterFromJson(preset);
        assertEquals(115, restored.tileScale);
        assertEquals(ControlCenterSurface.DEFAULT, restored.surface);
        assertFalse(restored.borderEnabled);
        preset.remove(ControlCenterConfig.KEY_COLUMNS);
        assertThrows(IOException.class, () -> ConfigurationBackupCodec.controlCenterFromJson(preset));
    }

    @Test public void globalSurfaceAndMemberSizeSurviveFullBackupAndPreset() throws Exception {
        var member = new ControlCenterGroupData.Member("bt", 0, 0, 12, 12, false, 155);
        var group = ControlCenterGroupData.empty().withMembers(java.util.List.of(member));
        var plan = ControlCenterLayoutPlan.blank(4).withItem(false,
                ControlCenterLayoutPlan.Item.group("group:tools", group, 0, 0, 1, 3));
        var center = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.FROSTED).withGlobalBorder(true, 2)
                .withLayoutPlan(plan.encode());
        var restored = ConfigurationBackupCodec.read(new ByteArrayInputStream(
                ConfigurationBackupCodec.encode(FusionConfig.defaults().withControlCenter(center))));
        assertEquals(ControlCenterSurface.FROSTED, restored.controlCenter.surface);
        assertTrue(restored.controlCenter.borderEnabled);
        assertEquals(2, restored.controlCenter.borderWidthDp);
        assertEquals(155, ControlCenterLayoutPlan.decode(restored.controlCenter.layoutPlan)
                .regular.items.get(0).group.members.get(0).iconScale);
        var preset = ConfigurationBackupCodec.controlCenterFromJson(ConfigurationBackupCodec.configJson(center));
        assertEquals(center.layoutPlan, preset.layoutPlan);
        assertEquals(center.surface, preset.surface);
    }

    @Test public void malformedUnknownAndFutureValuesAreRejectedBeforeRestore() throws Exception {
        String[] invalid = {"{}", "[]", "{\"icon_scale\":\"125\"}", "{\"icon_scale\":null}",
                "{\"icon_scale\":100,\"icon_scale\":120}", "{icon_scale:100}", "{\"icon_scale\":100} trailing",
                "{\"config_schema_version\":999,\"icon_scale\":100}", "{\"config_schema_version\":-1,\"icon_scale\":100}",
                "{\"config_schema_version\":" + FusionConfig.CONFIG_SCHEMA_VERSION + ",\"icon_scale\":100}",
                "{\"icon_scale\":999}", "{\"icon_scale\":100.5}", "{\"icon_scale\":1e2}",
                "{\"new_option\":true}", "{\"icon_scale\":100,\"control_center_layout_plan\":\"broken\"}"};
        for (String value : invalid) assertThrows(value, IOException.class, () -> decode(value));
        JSONObject future = new JSONObject(new String(ConfigurationBackupCodec.encode(FusionConfig.defaults()), StandardCharsets.UTF_8));
        future.put("version", 2);
        assertThrows(IOException.class, () -> decode(future.toString()));
    }

    @Test public void overlargeAndMalformedUtf8DocumentsAreRejected() {
        byte[] bytes = new byte[ConfigurationBackupCodec.MAX_BYTES + 1];
        Arrays.fill(bytes, (byte) ' ');
        assertThrows(IOException.class, () -> ConfigurationBackupCodec.read(new ByteArrayInputStream(bytes)));
        assertThrows(IOException.class, () -> ConfigurationBackupCodec.decode(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }

    @Test public void automaticBackupPrecedesRestoreAndUndoSurvivesNewReaderWithoutToggling() {
        MemoryFile config = new MemoryFile(), backup = new MemoryFile();
        FusionConfig initial = FusionConfig.defaults().withIconScale(115);
        assertTrue(FusionConfigStore.write(config, initial, FusionConfig.defaults()).success);
        FusionConfigStore.WriteResult restored = ConfigurationRecoveryStore.restore(config, backup,
                FusionConfig.defaults().withIconScale(140).withRevision(999), FusionConfig.defaults());
        assertTrue(restored.success);
        assertEquals(2, restored.revision);
        MemoryFile reopened = new MemoryFile(); reopened.bytes = backup.bytes.clone();
        assertTrue(ConfigurationRecoveryStore.canUndo(config, reopened));
        FusionConfigStore.WriteResult undone = ConfigurationRecoveryStore.undo(config, reopened, FusionConfig.defaults());
        assertTrue(undone.success);
        assertEquals(115, undone.config.iconScale);
        assertEquals(3, undone.revision);
        assertFalse(ConfigurationRecoveryStore.canUndo(config, reopened));
        assertFalse(ConfigurationRecoveryStore.undo(config, reopened, FusionConfig.defaults()).success);
        assertEquals(115, FusionConfigStore.read(config, FusionConfig.defaults()).iconScale);
    }

    @Test public void failedBackupCannotModifyPublishedConfiguration() {
        MemoryFile config = new MemoryFile(), backup = new MemoryFile();
        FusionConfigStore.write(config, FusionConfig.defaults().withIconScale(110), FusionConfig.defaults());
        byte[] before = config.bytes.clone(); backup.fail = true;
        assertFalse(ConfigurationRecoveryStore.restore(config, backup, FusionConfig.defaults().withIconScale(140), FusionConfig.defaults()).success);
        assertArrayEquals(before, config.bytes);
        assertFalse(ConfigurationRecoveryStore.canUndo(config, backup));
    }

    @Test public void failedSubsequentRestoreKeepsEarlierUndoAndFailedUndoCanBeRetried() {
        MemoryFile config = new MemoryFile(), backup = new MemoryFile();
        FusionConfigStore.write(config, FusionConfig.defaults().withIconScale(110), FusionConfig.defaults());
        assertTrue(ConfigurationRecoveryStore.restore(config, backup, FusionConfig.defaults().withIconScale(120), FusionConfig.defaults()).success);
        config.fail = true;
        assertFalse(ConfigurationRecoveryStore.restore(config, backup, FusionConfig.defaults().withIconScale(140), FusionConfig.defaults()).success);
        assertTrue(ConfigurationRecoveryStore.canUndo(config, backup));
        assertFalse(ConfigurationRecoveryStore.undo(config, backup, FusionConfig.defaults()).success);
        assertTrue(ConfigurationRecoveryStore.canUndo(config, backup));
        config.fail = false;
        FusionConfigStore.WriteResult result = ConfigurationRecoveryStore.undo(config, backup, FusionConfig.defaults());
        assertTrue(result.success);
        assertEquals(110, result.config.iconScale);
    }

    @Test public void ordinaryEditsKeepRecoveryTokenAndExportDoesNotContainPrivateToken() throws Exception {
        MemoryFile config = new MemoryFile(), backup = new MemoryFile();
        FusionConfigStore.write(config, FusionConfig.defaults(), FusionConfig.defaults());
        ConfigurationRecoveryStore.restore(config, backup, FusionConfig.defaults().withIconScale(120), FusionConfig.defaults());
        String token = FusionConfigStore.restoreToken(config);
        FusionConfigStore.WriteResult edit = FusionConfigStore.write(config, FusionConfig.defaults().withIconScale(130), FusionConfig.defaults());
        assertEquals(token, FusionConfigStore.restoreToken(config));
        assertTrue(ConfigurationRecoveryStore.canUndo(config, backup));
        assertFalse(new String(ConfigurationBackupCodec.encode(edit.config), StandardCharsets.UTF_8).contains(token));
    }

    @Test public void corruptRecoveryFileDoesNotOverwriteCurrentSnapshot() {
        MemoryFile config = new MemoryFile(), backup = new MemoryFile();
        FusionConfigStore.write(config, FusionConfig.defaults().withIconScale(115), FusionConfig.defaults());
        byte[] before = config.bytes.clone(); backup.bytes = "broken".getBytes(StandardCharsets.UTF_8);
        assertFalse(ConfigurationRecoveryStore.undo(config, backup, FusionConfig.defaults()).success);
        assertFalse(ConfigurationRecoveryStore.restore(config, backup, FusionConfig.defaults(), FusionConfig.defaults()).success);
        assertArrayEquals(before, config.bytes);
    }

    private static FusionConfig decode(String value) throws IOException {
        return ConfigurationBackupCodec.decode(value.getBytes(StandardCharsets.UTF_8));
    }

    static final class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        boolean fail;
        public byte[] read() throws IOException { if (bytes == null) throw new FileNotFoundException(); return bytes.clone(); }
        public void write(byte[] value) throws IOException { if (fail) throw new IOException("disk_full"); bytes = value.clone(); }
    }
}
