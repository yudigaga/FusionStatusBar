package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DiagnosticLogExporterTest {
    @Test
    public void archiveContainsMetadataAndCapturedLogcat() throws Exception {
        File logcat = File.createTempFile("fusion-export-test-", ".txt");
        try {
            try (FileOutputStream fileOutput = new FileOutputStream(logcat)) {
                fileOutput.write("I/FusionStatusBar: hooks installed\n"
                        .getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DiagnosticLogExporter.writeArchive(output, logcat, "{\"model\":\"test\"}");

            try (ZipInputStream archive = new ZipInputStream(
                    new ByteArrayInputStream(output.toByteArray()))) {
                ZipEntry metadata = archive.getNextEntry();
                assertEquals("metadata.json", metadata.getName());
                assertEquals("{\"model\":\"test\"}",
                        readEntry(archive));

                ZipEntry logs = archive.getNextEntry();
                assertEquals("logcat.txt", logs.getName());
                assertEquals("I/FusionStatusBar: hooks installed\n",
                        readEntry(archive));
                assertNull(archive.getNextEntry());
            }
        } finally {
            logcat.delete();
        }
    }

    @Test
    public void archiveContainsEveryCapturedSource() throws Exception {
        File logcat = File.createTempFile("fusion-export-logcat-", ".txt");
        File lsposed = File.createTempFile("fusion-export-lsposed-", ".txt");
        try {
            try (FileOutputStream output = new FileOutputStream(logcat)) {
                output.write("systemui log\n".getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream output = new FileOutputStream(lsposed)) {
                output.write("plugin hook log\n".getBytes(StandardCharsets.UTF_8));
            }
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

    private static String readEntry(ZipInputStream archive) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = archive.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
