package com.xtjm.fusionstatusbar;

import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class DiagnosticLogExporterTest {
    @Test
    public void archiveContainsMetadataAndSanitizedCapturedLogcat() throws Exception {
        File logcat = File.createTempFile("fusion-export-test-", ".txt");
        try {
            try (FileOutputStream fileOutput = new FileOutputStream(logcat)) {
                fileOutput.write(("I/FusionStatusBar: hooks installed email=alice@example.com "
                        + "token=private-value\n").getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DiagnosticLogExporter.writeArchive(output, logcat, "{\"model\":\"test\"}");

            try (ZipInputStream archive = new ZipInputStream(
                    new ByteArrayInputStream(output.toByteArray()))) {
                ZipEntry metadata = archive.getNextEntry();
                assertEquals("metadata.json", metadata.getName());
                assertEquals("{\"model\":\"test\"}", readEntry(archive));

                ZipEntry logs = archive.getNextEntry();
                assertEquals("logcat.txt", logs.getName());
                String captured = readEntry(archive);
                assertTrue(captured.contains("hooks installed"));
                assertTrue(captured.contains("email=<redacted>"));
                assertTrue(captured.contains("token=<redacted>"));
                assertFalse(captured.contains("alice@example.com"));
                assertFalse(captured.contains("private-value"));
                assertNull(archive.getNextEntry());
            }
        } finally {
            logcat.delete();
        }
    }

    @Test
    public void archiveContainsEveryExplicitlySelectedSource() throws Exception {
        File logcat = File.createTempFile("fusion-export-logcat-", ".txt");
        File lsposed = File.createTempFile("fusion-export-lsposed-", ".txt");
        try {
            write(logcat, "systemui log\n");
            write(lsposed, "plugin hook log\n");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DiagnosticLogExporter.writeArchive(output, Arrays.asList(
                    new DiagnosticLogExporter.ArchiveFile("logcat-all.txt", logcat),
                    new DiagnosticLogExporter.ArchiveFile("lsposed.txt", lsposed)), "{}");

            try (ZipInputStream archive = new ZipInputStream(
                    new ByteArrayInputStream(output.toByteArray()))) {
                assertEquals("metadata.json", archive.getNextEntry().getName());
                assertEquals("{}", readEntry(archive));
                assertEquals("logcat-all.txt", archive.getNextEntry().getName());
                assertEquals("systemui log\n", readEntry(archive));
                assertEquals("lsposed.txt", archive.getNextEntry().getName());
                assertEquals("plugin hook log\n", readEntry(archive));
                assertNull(archive.getNextEntry());
            }
        } finally {
            logcat.delete();
            lsposed.delete();
        }
    }

    @Test
    public void defaultOptionsRequestOnlyFilteredLogs() throws Exception {
        org.json.JSONArray defaults = DiagnosticLogExporter.ExportOptions.defaults().requestedSources();
        assertEquals("filtered-logcat", defaults.getString(0));
        assertEquals(1, defaults.length());

        DiagnosticLogExporter.ExportOptions selected = new DiagnosticLogExporter.ExportOptions(
                true, true, false);
        assertEquals("full-logcat", selected.requestedSources().getString(1));
        assertEquals("dmesg", selected.requestedSources().getString(2));
        assertEquals(3, selected.requestedSources().length());
    }

    @Test
    public void runtimeSnapshotCarriesRevisionFeatureVersionAndPseudonymousSession() throws Exception {
        Bundle status = new Bundle();
        status.putLong(FusionActivationStatus.KEY_READ_REVISION, 17L);
        status.putLong(FusionActivationStatus.KEY_APPLIED_REVISION, 16L);
        status.putString(FusionActivationStatus.KEY_RUNTIME_SESSION, "1000:4321:private-session");
        status.putString(FusionActivationStatus.KEY_MODULE_VERSION, "0.3.174");
        status.putInt(FusionActivationStatus.KEY_MODULE_CODE, 188);
        Bundle features = new Bundle();
        features.putString(FusionActivationStatus.FEATURE_CONTROL_CENTER,
                FusionActivationStatus.DEGRADED + ":missing_shape_hook");
        features.putString("unexpected_feature", "private-value");
        status.putBundle(FusionActivationStatus.KEY_FEATURES, features);

        JSONObject snapshot = DiagnosticLogExporter.runtimeSnapshot(status);
        assertTrue(snapshot.getBoolean("loadedThisBoot"));
        assertEquals(17L, snapshot.getLong("configReadRevision"));
        assertEquals(16L, snapshot.getLong("configAppliedRevision"));
        assertEquals(4321, snapshot.getInt("systemUiPid"));
        assertNotEquals("1000:4321:private-session", snapshot.getString("runtimeSessionHash"));
        assertEquals("0.3.174", snapshot.getString("moduleVersion"));
        assertTrue(snapshot.getJSONObject("features").getString(
                FusionActivationStatus.FEATURE_CONTROL_CENTER).contains("missing_shape_hook"));
        assertFalse(snapshot.toString().contains("private-session"));
        assertFalse(snapshot.toString().contains("private-value"));
    }

    @Test
    public void safeSettingsExcludeUserAuthoredPatternsAndTileIdentifiers() throws Exception {
        Bundle config = new Bundle();
        config.putLong(FusionConfig.KEY_REVISION, 12L);
        config.putBoolean(FusionConfig.KEY_CUSTOM_CLOCK, true);
        config.putString(FusionConfig.KEY_CLOCK_PATTERN, "email alice@example.com");
        config.putBoolean(ControlCenterConfig.KEY_ENABLED, true);
        config.putString(ControlCenterConfig.KEY_ORDER,
                "custom(com.example.private/.SecretTile)");

        JSONObject safe = DiagnosticLogExporter.safeSettings(config);
        assertEquals(12L, safe.getLong(FusionConfig.KEY_REVISION));
        assertTrue(safe.getBoolean(FusionConfig.KEY_CUSTOM_CLOCK));
        assertTrue(safe.getBoolean(ControlCenterConfig.KEY_ENABLED));
        assertFalse(safe.has(FusionConfig.KEY_CLOCK_PATTERN));
        assertFalse(safe.has(ControlCenterConfig.KEY_ORDER));
        assertFalse(safe.toString().contains("alice@example.com"));
        assertFalse(safe.toString().contains("com.example.private"));
    }

    @Test
    public void redactionRemovesIdentifiersAndUserStoragePaths() {
        String result = DiagnosticLogExporter.redactText(
                "email=alice@example.com android_id=abc123 Bearer eyJ.secret "
                        + "file=/storage/emulated/0/Documents/private.zip /home/alice/work");
        assertFalse(result.contains("alice@example.com"));
        assertFalse(result.contains("abc123"));
        assertFalse(result.contains("eyJ.secret"));
        assertFalse(result.contains("/storage/emulated/0"));
        assertFalse(result.contains("/home/alice"));
        assertTrue(result.contains("<redacted>"));
        assertTrue(result.contains("<user-path>"));
    }

    @Test
    public void oversizedEntryIsTailLimitedAndMarked() throws Exception {
        File log = File.createTempFile("fusion-export-large-", ".txt");
        try {
            try (FileOutputStream output = new FileOutputStream(log)) {
                output.write("EARLY_SECRET\n".getBytes(StandardCharsets.UTF_8));
                byte[] line = new byte[8192];
                Arrays.fill(line, (byte) 'x');
                for (int i = 0; i < (DiagnosticLogExporter.MAX_ENTRY_BYTES / line.length) + 32; i++) {
                    output.write(line);
                    output.write('\n');
                }
                output.write("LATEST_LINE\n".getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DiagnosticLogExporter.writeArchive(output, log, "{}");
            try (ZipInputStream archive = new ZipInputStream(
                    new ByteArrayInputStream(output.toByteArray()))) {
                assertEquals("metadata.json", archive.getNextEntry().getName());
                readEntry(archive);
                assertEquals("logcat.txt", archive.getNextEntry().getName());
                String tail = readEntry(archive);
                assertTrue(tail.startsWith("[truncated; last "));
                assertTrue(tail.contains("LATEST_LINE"));
                assertFalse(tail.contains("EARLY_SECRET"));
            }
        } finally {
            log.delete();
        }
    }

    @Test
    public void archiveCancellationStopsBeforeWritingEntries() throws Exception {
        File log = File.createTempFile("fusion-export-cancel-", ".txt");
        try {
            write(log, "log\n");
            AtomicBoolean cancelled = new AtomicBoolean(true);
            try {
                DiagnosticLogExporter.writeArchive(new ByteArrayOutputStream(), Arrays.asList(
                        new DiagnosticLogExporter.ArchiveFile("logcat.txt", log)), "{}",
                        cancelled::get);
                fail("expected cancellation");
            } catch (DiagnosticLogExporter.ExportCancelledException expected) {
                assertEquals("cancelled", expected.getMessage());
            }
        } finally {
            log.delete();
        }
    }

    @Test
    public void archiveChecksTheTotalTimeBudgetBeforeReadingSources() throws Exception {
        File log = File.createTempFile("fusion-export-deadline-", ".txt");
        try {
            write(log, "log\n");
            try {
                DiagnosticLogExporter.writeArchive(new ByteArrayOutputStream(), Arrays.asList(
                        new DiagnosticLogExporter.ArchiveFile("logcat.txt", log)), "{}",
                        () -> false, System.nanoTime() - 1L);
                fail("expected expired time budget");
            } catch (IOException expected) {
                assertEquals("total_time_budget_exceeded", expected.getMessage());
            }
        } finally {
            log.delete();
        }
    }

    @Test
    public void archiveRejectsOutputPastItsTotalByteBudget() throws Exception {
        File log = File.createTempFile("fusion-export-budget-", ".txt");
        try {
            write(log, "a log large enough to exceed the small test archive limit\n".repeat(8));
            try {
                DiagnosticLogExporter.writeArchive(new ByteArrayOutputStream(), Arrays.asList(
                        new DiagnosticLogExporter.ArchiveFile("logcat.txt", log)), "{}",
                        () -> false, Long.MAX_VALUE, 96);
                fail("expected archive byte budget failure");
            } catch (IOException expected) {
                assertEquals("archive_size_limit_exceeded", expected.getMessage());
            }
        } finally {
            log.delete();
        }
    }

    @Test
    public void unsafeArchiveEntryNameIsRejected() throws Exception {
        File log = File.createTempFile("fusion-export-name-", ".txt");
        try {
            write(log, "log\n");
            try {
                DiagnosticLogExporter.writeArchive(new ByteArrayOutputStream(), Arrays.asList(
                        new DiagnosticLogExporter.ArchiveFile("../outside.txt", log)), "{}");
                fail("expected invalid entry name");
            } catch (IOException expected) {
                assertEquals("invalid_archive_entry_name", expected.getMessage());
            }
        } finally {
            log.delete();
        }
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readEntry(ZipInputStream archive) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = archive.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
