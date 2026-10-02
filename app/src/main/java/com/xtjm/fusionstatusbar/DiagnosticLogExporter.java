package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticLogExporter {
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final String FILTERED_LOGCAT_COMMAND = "logcat -d -v threadtime -b all "
            + "-s FusionStatusBar:V LSPosedFramework:V "
            + "AndroidRuntime:E Choreographer:W 'MIUIScout App:W'";
    private static final String ALL_LOGCAT_COMMAND = "logcat -d -v threadtime -b all";
    private static final String DMESG_COMMAND = "dmesg";
    private static final String LSPOSED_LOG_COMMAND =
            "for d in /data/adb/lspd/log /data/adb/lspd/log.old "
                    + "/data/adb/lsposed/log /data/adb/lsposed/log.old; do "
                    + "[ -d \"$d\" ] || continue; "
                    + "for f in \"$d\"/verbose_*.log \"$d\"/modules_*.log; do "
                    + "[ -f \"$f\" ] || continue; "
                    + "printf '\\n===== %s =====\\n' \"$f\"; "
                    + "tail -c 16777216 \"$f\"; "
                    + "done; done";

    private DiagnosticLogExporter() {
    }

    static void export(Context context, Uri destination) throws IOException {
        ArrayList<ArchiveFile> files = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        try {
            captureRootFile(context, "logcat.txt", FILTERED_LOGCAT_COMMAND, files, errors);
            captureRootFile(context, "logcat-all.txt", ALL_LOGCAT_COMMAND, files, errors);
            captureRootFile(context, "dmesg.txt", DMESG_COMMAND, files, errors);
            captureRootFile(context, "lsposed.txt", LSPOSED_LOG_COMMAND, files, errors);
            if (files.isEmpty()) {
                if (errors.length() > 0) {
                    throw new RootUnavailableException(
                            "All root diagnostic sources failed: " + errors);
                }
                throw new NoLogsException();
            }
            if (errors.length() > 0) {
                File errorFile = File.createTempFile("fusion-log-errors-", ".txt",
                        context.getCacheDir());
                writeText(errorFile, errors.toString());
                files.add(new ArchiveFile("collection-errors.txt", errorFile));
            }
            String metadata = createMetadata(context, files, errors.toString());
            OutputStream output = context.getContentResolver().openOutputStream(destination, "w");
            if (output == null) {
                throw new IOException("Document provider did not open the destination");
            }
            try (OutputStream closeableOutput = output) {
                writeArchive(closeableOutput, files, metadata);
            }
        } finally {
            for (ArchiveFile file : files) {
                file.file.delete();
            }
        }
    }

    private static boolean captureRootFile(Context context, String name, String command,
            List<ArchiveFile> files, StringBuilder errors) throws IOException {
        File destination = File.createTempFile("fusion-log-", ".txt", context.getCacheDir());
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .redirectOutput(destination)
                    .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("command timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("exit code " + process.exitValue());
            }
            if (destination.length() == 0) {
                destination.delete();
                return false;
            }
            files.add(new ArchiveFile(name, destination));
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            appendError(errors, name, "interrupted");
            destination.delete();
            throw new IOException("Log collection interrupted", error);
        } catch (IOException error) {
            appendError(errors, name, error.getMessage());
            destination.delete();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static void writeArchive(OutputStream destination, File logcatFile, String metadata)
            throws IOException {
        writeArchive(destination,
                Collections.singletonList(new ArchiveFile("logcat.txt", logcatFile)), metadata);
    }

    static void writeArchive(OutputStream destination, List<ArchiveFile> files, String metadata)
            throws IOException {
        try (ZipOutputStream archive = new ZipOutputStream(destination)) {
            archive.putNextEntry(new ZipEntry("metadata.json"));
            archive.write(metadata.getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();

            for (ArchiveFile file : files) {
                if (file == null || file.file == null || !file.file.isFile()) continue;
                archive.putNextEntry(new ZipEntry(file.name));
                writeTail(archive, file.file);
                archive.closeEntry();
            }
        }
    }

    private static void writeTail(ZipOutputStream archive, File source) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            long start = Math.max(0, input.length() - MAX_ENTRY_BYTES);
            input.seek(start);
            if (start > 0) {
                archive.write(("[truncated; last " + MAX_ENTRY_BYTES
                        + " bytes retained]\n").getBytes(StandardCharsets.UTF_8));
                input.readLine();
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                archive.write(buffer, 0, count);
            }
        }
    }

    private static void writeText(File destination, String value) throws IOException {
        try (OutputStream output = new java.io.FileOutputStream(destination)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void appendError(StringBuilder errors, String name, String message) {
        errors.append(name).append(": ")
                .append(message == null ? "unknown error" : message).append('\n');
    }

    private static String createMetadata(Context context, List<ArchiveFile> files,
            String collectionErrors) throws IOException {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("capturedAtMillis", System.currentTimeMillis());
            metadata.put("manufacturer", Build.MANUFACTURER);
            metadata.put("model", Build.MODEL);
            metadata.put("androidApi", Build.VERSION.SDK_INT);
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            metadata.put("moduleVersion", packageInfo.versionName);
            metadata.put("moduleVersionCode", packageInfo.getLongVersionCode());
            metadata.put("logcatCommand", FILTERED_LOGCAT_COMMAND);
            metadata.put("allLogcatCommand", ALL_LOGCAT_COMMAND);
            metadata.put("dmesgCommand", DMESG_COMMAND);
            metadata.put("lsposedLogCommand", LSPOSED_LOG_COMMAND);
            ArrayList<String> entries = new ArrayList<>();
            for (ArchiveFile file : files) {
                entries.add(file.name);
            }
            metadata.put("archiveEntries", entries);
            metadata.put("collectionErrors", collectionErrors);

            Bundle config = FusionConfigStore.read(context).toBundle();
            JSONObject settings = new JSONObject();
            ArrayList<String> keys = new ArrayList<>(config.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                settings.put(key, config.get(key));
            }
            metadata.put("settings", settings);
            return metadata.toString(2);
        } catch (Exception error) {
            throw new IOException("Could not create diagnostic metadata", error);
        }
    }

    static final class ArchiveFile {
        final String name;
        final File file;

        ArchiveFile(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }

    static final class RootUnavailableException extends IOException {
        RootUnavailableException(String message) {
            super(message);
        }

        RootUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class NoLogsException extends IOException {
        NoLogsException() {
            super("No matching logcat entries");
        }
    }
}
