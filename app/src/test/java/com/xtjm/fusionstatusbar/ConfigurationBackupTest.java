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
    @Test public void exportedFullConfigurationRoundTripsAppearanceLayoutsAndRevision() throws Exception {
        ControlCenterConfig center = ControlCenterConfig.defaults().withEnabled(true).withColumns(5)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(5).forPublication(true).encode());
        FusionConfig source = FusionConfig.defaults().withIconScale(115).withStrokeScale(125)
                .withControlCenter(center).withRevision(87);
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

    @Test public void malformedUnknownAndFutureValuesAreRejectedBeforeRestore() throws Exception {
        String[] invalid = {"{}", "[]", "{\"icon_scale\":\"125\"}", "{\"icon_scale\":null}",
                "{\"icon_scale\":100,\"icon_scale\":120}", "{icon_scale:100}", "{\"icon_scale\":100} trailing",
                "{\"config_schema_version\":999,\"icon_scale\":100}", "{\"config_schema_version\":-1,\"icon_scale\":100}",
                "{\"icon_scale\":999}", "{\"icon_scale\":100.5}", "{\"icon_scale\":1e2}",
                "{\"new_option\":true}", "{\"icon_scale\":100,\"control_center_layout_plan\":\"broken\"}",
                "{\"config_schema_version\":4,\"icon_scale\":100}"};
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
