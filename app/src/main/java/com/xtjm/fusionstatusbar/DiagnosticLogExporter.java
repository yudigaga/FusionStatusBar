package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticLogExporter {
    static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    static final int MAX_ARCHIVE_BYTES = 34 * 1024 * 1024;
    static final long MAX_EXPORT_MILLIS = 90_000L;
    private static final long MAX_COMMAND_MILLIS = 25_000L;
    private static final int FILTERED_LOGCAT_BYTES = 4 * 1024 * 1024;
    private static final int FULL_LOGCAT_BYTES = 12 * 1024 * 1024;
    private static final int DMESG_BYTES = 4 * 1024 * 1024;
    private static final int LSPOSED_BYTES = 12 * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 64 * 1024;
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
                    + "tail -c 12582912 \"$f\"; "
                    + "done; done";
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:password|passwd|token|access_token|refresh_token|authorization|"
                    + "cookie|set-cookie|secret|api[_-]?key|account|email|phone|imei|imsi|"
                    + "iccid|android[_-]?id|serial(?:number)?|mac(?:[_-]?address)?)"
                    + "\\b\\s*[=:]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern USER_PATH = Pattern.compile(
            "(?i)(?:/storage/emulated|/data/media|/mnt/user)/\\d+|"
                    + "/(?:data/(?:user|user_de|data|adb)|sdcard|storage)/[^\\s\"')]+|"
                    + "/home/[^/\\s]+|[A-Z]:\\\\Users\\\\[^\\\\\\s]+");
    private static final List<String> SAFE_SETTING_KEYS = Collections.unmodifiableList(Arrays.asList(
            FusionConfig.KEY_REVISION, FusionConfig.KEY_SCHEMA_VERSION,
            FusionConfig.KEY_ICON_SCALE, FusionConfig.KEY_STROKE_SCALE,
            FusionConfig.KEY_DOUBLE_ROW, FusionConfig.KEY_DOUBLE_ROW_SIDE,
            FusionConfig.KEY_SPAN_ROWS, FusionConfig.KEY_WIFI_ICON,
            FusionConfig.KEY_STATUSBAR_HEIGHT, FusionConfig.KEY_CLOCK_SIDE,
            FusionConfig.KEY_CLOCK_ROW, FusionConfig.KEY_NOTIFICATION_SIDE,
            FusionConfig.KEY_NOTIFICATION_ROW, FusionConfig.KEY_SYSTEM_SIDE,
            FusionConfig.KEY_SYSTEM_ROW, FusionConfig.KEY_FUSION_SIDE,
            FusionConfig.KEY_FUSION_ROW, FusionConfig.KEY_SYSTEM_ICON_SCALE,
            FusionConfig.KEY_NOTIFICATION_ICON_SCALE, FusionConfig.KEY_SPAN_OFFSET_X,
            FusionConfig.KEY_SPAN_OFFSET_Y, FusionConfig.KEY_CUSTOM_CLOCK,
            FusionConfig.KEY_SHOW_WEATHER, NotificationClockConfig.KEY_ENABLED,
            NotificationClockConfig.KEY_SIZE, NotificationClockConfig.KEY_OFFSET_Y,
            NotificationClockConfig.KEY_DATE_ENABLED, NotificationClockConfig.KEY_DATE_SIZE,
            NotificationClockConfig.KEY_DATE_OFFSET_Y, NotificationClockConfig.KEY_LIST_OFFSET_Y,
            NotificationClockConfig.KEY_TIME_CENTERED, NotificationClockConfig.KEY_DATE_CENTERED,
            NotificationClockConfig.KEY_HIDE_SETTINGS, ControlCenterConfig.KEY_ENABLED,
            ControlCenterConfig.KEY_COLUMNS, ControlCenterConfig.KEY_TILE_SCALE,
            ControlCenterConfig.KEY_CORNER_RADIUS, ControlCenterConfig.KEY_SPACING,
            ControlCenterConfig.KEY_STYLE, ControlCenterConfig.KEY_BACKGROUND_BLUR,
            ControlCenterConfig.KEY_CARD_BLUR, ControlCenterConfig.KEY_TILE_BLUR,
            ControlCenterConfig.KEY_SURFACE, ControlCenterConfig.KEY_BORDER_ENABLED,
            ControlCenterConfig.KEY_BORDER_WIDTH, SystemSurfaceConfig.KEY_POWER_MENU,
            SystemSurfaceConfig.KEY_VOLUME_DIALOG, SystemSurfaceConfig.KEY_NOTIFICATIONS,
            SystemSurfaceConfig.KEY_HEADS_UP, SystemSurfaceConfig.KEY_EXTENDED_POWER_MENU,
            SystemSurfaceConfig.KEY_COMPACT_VOLUME, "telemetry_group_enabled_0",
            "telemetry_group_enabled_1", "telemetry_group_enabled_2",
            "telemetry_dual_speed", "telemetry_current_ma", "telemetry_current_positive"));
    private static final String[] FEATURE_KEYS = {
            FusionActivationStatus.FEATURE_STATUS_BAR,
            FusionActivationStatus.FEATURE_CONTROL_CENTER,
            FusionActivationStatus.FEATURE_NOTIFICATION_CLOCK,
            FusionActivationStatus.FEATURE_POWER_MENU,
            FusionActivationStatus.FEATURE_VOLUME_DIALOG,
            FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE,
            FusionActivationStatus.FEATURE_HEADS_UP
    };

    private DiagnosticLogExporter() {
    }

    static void export(Context context, Uri destination) throws IOException {
        export(context, destination, ExportOptions.defaults(), () -> false);
    }

    static void export(Context context, Uri destination, ExportOptions options,
            Cancellation cancellation) throws IOException {
        if (context == null || destination == null) throw new IOException("missing_export_target");
        ExportOptions selected = options == null ? ExportOptions.defaults() : options;
        Cancellation cancelled = cancellation == null ? () -> false : cancellation;
        long startedAt = System.currentTimeMillis();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_EXPORT_MILLIS);
        Bundle activationBefore = FusionActivationStatus.read(context);
        ArrayList<ArchiveFile> files = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        try {
            checkCancelled(cancelled);
            captureRootFile(context, "logcat.txt", FILTERED_LOGCAT_COMMAND,
                    FILTERED_LOGCAT_BYTES, files, errors, deadline, cancelled);
            if (selected.includeFullLogcat) captureRootFile(context, "logcat-all.txt",
                    ALL_LOGCAT_COMMAND, FULL_LOGCAT_BYTES, files, errors, deadline, cancelled);
            if (selected.includeDmesg) captureRootFile(context, "dmesg.txt", DMESG_COMMAND,
                    DMESG_BYTES, files, errors, deadline, cancelled);
            if (selected.includeLsposed) captureRootFile(context, "lsposed.txt",
                    LSPOSED_LOG_COMMAND, LSPOSED_BYTES, files, errors, deadline, cancelled);
            if (files.isEmpty()) {
                if (errors.length() > 0) {
                    throw new RootUnavailableException("All selected diagnostic sources failed: "
                            + errors);
                }
                throw new NoLogsException();
            }
            if (errors.length() > 0) {
                File errorFile = File.createTempFile("fusion-log-errors-", ".txt",
                        context.getCacheDir());
                writeText(errorFile, redactText(errors.toString()));
                files.add(new ArchiveFile("collection-errors.txt", errorFile, false));
            }
            Bundle activationAfter = FusionActivationStatus.read(context);
            long finishedAt = System.currentTimeMillis();
            String metadata = createMetadata(context, files, errors.toString(), selected,
                    activationBefore, activationAfter, startedAt, finishedAt);
            checkCancelled(cancelled);
            OutputStream output = context.getContentResolver().openOutputStream(destination, "w");
            if (output == null) throw new IOException("Document provider did not open the destination");
            try (OutputStream closeableOutput = output) {
                writeArchive(closeableOutput, files, metadata, cancelled, deadline);
            }
        } finally {
            for (ArchiveFile file : files) {
                if (file != null && file.file != null) file.file.delete();
            }
        }
    }

    private static boolean captureRootFile(Context context, String name, String command,
            int maxBytes, List<ArchiveFile> files, StringBuilder errors, long exportDeadline,
            Cancellation cancellation) throws IOException {
        checkCancelled(cancellation);
        File destination = File.createTempFile("fusion-log-", ".txt", context.getCacheDir());
        Process process = null;
        Thread copier = null;
        AtomicBoolean truncated = new AtomicBoolean();
        AtomicReference<IOException> copyError = new AtomicReference<>();
        try {
            if (remainingMillis(exportDeadline) <= 0) throw new IOException("total_time_budget_exceeded");
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            Process activeProcess = process;
            copier = new Thread(() -> copyBounded(activeProcess.getInputStream(), destination,
                    maxBytes, truncated, copyError, cancellation), "fusion-log-capture");
            copier.setDaemon(true);
            copier.start();

            long commandDeadline = Math.min(exportDeadline,
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_COMMAND_MILLIS));
            while (true) {
                checkCancelled(cancellation);
                long remaining = remainingMillis(commandDeadline);
                if (remaining <= 0) throw new IOException("command_timeout");
                if (process.waitFor(Math.min(remaining, 200L), TimeUnit.MILLISECONDS)) break;
            }
            joinCopier(copier, commandDeadline, cancellation);
            IOException readFailure = copyError.get();
            if (readFailure != null) throw new IOException("source_read_failed");
            if (process.exitValue() != 0) throw new IOException("command_failed_exit_" + process.exitValue());
            if (destination.length() == 0) {
                destination.delete();
                return false;
            }
            files.add(new ArchiveFile(name, destination, truncated.get()));
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            appendError(errors, name, "interrupted");
            throw new ExportCancelledException("interrupted", error);
        } catch (ExportCancelledException error) {
            throw error;
        } catch (IOException error) {
            appendError(errors, name, error.getMessage());
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                }
            }
            if (copier != null && copier.isAlive()) {
                copier.interrupt();
                try {
                    copier.join(1_000L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            if (files.stream().noneMatch(file -> file.file == destination)) destination.delete();
        }
    }

    private static void copyBounded(InputStream input, File destination, int maxBytes,
            AtomicBoolean truncated, AtomicReference<IOException> failure,
            Cancellation cancellation) {
        long copied = 0L;
        byte[] buffer = new byte[8192];
        try (InputStream source = input; OutputStream output = new FileOutputStream(destination)) {
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (cancellation.isCancelled()) throw new IOException("cancelled");
                long remaining = maxBytes - copied;
                if (remaining > 0) {
                    int accepted = (int) Math.min(count, remaining);
                    output.write(buffer, 0, accepted);
                    copied += accepted;
                }
                if (count > remaining) truncated.set(true);
            }
        } catch (IOException error) {
            failure.set(error);
        }
    }

    private static void joinCopier(Thread copier, long deadline, Cancellation cancellation)
            throws IOException, InterruptedException {
        while (copier.isAlive()) {
            checkCancelled(cancellation);
            long remaining = remainingMillis(deadline);
            if (remaining <= 0) throw new IOException("command_timeout");
            copier.join(Math.min(remaining, 200L));
        }
    }

    private static long remainingMillis(long deadlineNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private static void checkCancelled(Cancellation cancellation) throws ExportCancelledException {
        if (cancellation.isCancelled()) throw new ExportCancelledException("cancelled");
    }

    private static void checkBudget(Cancellation cancellation, long deadlineNanos)
            throws IOException {
        checkCancelled(cancellation);
        if (System.nanoTime() >= deadlineNanos) throw new IOException("total_time_budget_exceeded");
    }

    static void writeArchive(OutputStream destination, File logcatFile, String metadata)
            throws IOException {
        writeArchive(destination,
                Collections.singletonList(new ArchiveFile("logcat.txt", logcatFile)), metadata,
                () -> false);
    }

    static void writeArchive(OutputStream destination, List<ArchiveFile> files, String metadata)
            throws IOException {
        writeArchive(destination, files, metadata, () -> false);
    }

    static void writeArchive(OutputStream destination, List<ArchiveFile> files, String metadata,
            Cancellation cancellation) throws IOException {
        writeArchive(destination, files, metadata, cancellation, Long.MAX_VALUE);
    }

    static void writeArchive(OutputStream destination, List<ArchiveFile> files,
            String metadata, Cancellation cancellation, long deadlineNanos) throws IOException {
        writeArchive(destination, files, metadata, cancellation, deadlineNanos,
                MAX_ARCHIVE_BYTES);
    }

    static void writeArchive(OutputStream destination, List<ArchiveFile> files,
            String metadata, Cancellation cancellation, long deadlineNanos,
            int maximumArchiveBytes) throws IOException {
        if (maximumArchiveBytes < 1) throw new IOException("invalid_archive_limit");
        byte[] metadataBytes = redactText(metadata == null ? "{}" : metadata)
                .getBytes(StandardCharsets.UTF_8);
        if (metadataBytes.length > MAX_METADATA_BYTES) throw new IOException("metadata_too_large");
        CountingOutputStream bounded = new CountingOutputStream(destination, maximumArchiveBytes);
        try (ZipOutputStream archive = new ZipOutputStream(bounded)) {
            archive.putNextEntry(new ZipEntry("metadata.json"));
            archive.write(metadataBytes);
            archive.closeEntry();

            int entries = 0;
            for (ArchiveFile file : files) {
                checkBudget(cancellation, deadlineNanos);
                if (file == null || file.file == null || !file.file.isFile()) continue;
                if (++entries > 8) throw new IOException("too_many_archive_entries");
                if (!validEntryName(file.name)) throw new IOException("invalid_archive_entry_name");
                archive.putNextEntry(new ZipEntry(file.name));
                if (file.truncated) archive.write("[truncated; byte limit reached]\n"
                        .getBytes(StandardCharsets.UTF_8));
                writeTail(archive, file.file, cancellation, deadlineNanos);
                archive.closeEntry();
            }
        }
    }

    private static boolean validEntryName(String name) {
        return name != null && name.matches("[A-Za-z0-9._-]{1,64}")
                && !".".equals(name) && !"..".equals(name);
    }

    private static void writeTail(ZipOutputStream archive, File source,
            Cancellation cancellation, long deadlineNanos) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            long start = Math.max(0, input.length() - MAX_ENTRY_BYTES);
            input.seek(start);
            ByteArrayOutputStream text = new ByteArrayOutputStream(
                    (int) Math.min(input.length() - start, MAX_ENTRY_BYTES));
            if (start > 0) {
                text.write(("[truncated; last " + MAX_ENTRY_BYTES
                        + " bytes retained]\n").getBytes(StandardCharsets.UTF_8));
                input.readLine();
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkBudget(cancellation, deadlineNanos);
                text.write(buffer, 0, count);
            }
            archive.write(redactText(new String(text.toByteArray(), StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeText(File destination, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(destination)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void appendError(StringBuilder errors, String name, String message) {
        errors.append(name).append(": ")
                .append(message == null ? "unknown_error" : message.replaceAll("[^A-Za-z0-9_-]", "_"))
                .append('\n');
    }

    private static String createMetadata(Context context, List<ArchiveFile> files,
            String collectionErrors, ExportOptions options, Bundle activationBefore,
            Bundle activationAfter, long startedAtMillis, long finishedAtMillis) throws IOException {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("formatVersion", 2);
            metadata.put("capturedAtMillis", finishedAtMillis);
            metadata.put("captureStartedAtMillis", startedAtMillis);
            metadata.put("captureDurationMillis", Math.max(0L, finishedAtMillis - startedAtMillis));
            metadata.put("manufacturer", redactText(Build.MANUFACTURER));
            metadata.put("model", redactText(Build.MODEL));
            metadata.put("androidApi", Build.VERSION.SDK_INT);
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            metadata.put("moduleVersion", packageInfo.versionName);
            metadata.put("moduleVersionCode", packageInfo.getLongVersionCode());
            metadata.put("requestedSources", options.requestedSources());
            JSONArray entries = new JSONArray();
            for (ArchiveFile file : files) {
                JSONObject item = new JSONObject();
                item.put("name", file.name);
                item.put("bytes", file.file.length());
                item.put("truncated", file.truncated || file.file.length() > MAX_ENTRY_BYTES);
                entries.put(item);
            }
            metadata.put("archiveEntries", entries);
            metadata.put("collectionErrors", redactText(collectionErrors));
            metadata.put("runtimeStateChangedDuringCapture",
                    !sameRuntimeState(activationBefore, activationAfter));
            metadata.put("runtimeBefore", runtimeSnapshot(activationBefore));
            metadata.put("runtimeAfter", runtimeSnapshot(activationAfter));
            metadata.put("settings", safeSettings(FusionConfig.read(context).toBundle()));
            return metadata.toString(2);
        } catch (Exception error) {
            throw new IOException("Could not create diagnostic metadata", error);
        }
    }

    static JSONObject runtimeSnapshot(Bundle status) throws Exception {
        JSONObject runtime = new JSONObject();
        Bundle value = status == null ? Bundle.EMPTY : status;
        long readRevision = value.getLong(FusionActivationStatus.KEY_READ_REVISION, -1L);
        long appliedRevision = value.getLong(FusionActivationStatus.KEY_APPLIED_REVISION, -1L);
        String session = value.getString(FusionActivationStatus.KEY_RUNTIME_SESSION, "");
        runtime.put("loadedThisBoot", value.containsKey(FusionActivationStatus.KEY_READ_REVISION));
        runtime.put("moduleVersion", redactText(value.getString(
                FusionActivationStatus.KEY_MODULE_VERSION, "")));
        runtime.put("moduleVersionCode", value.getInt(FusionActivationStatus.KEY_MODULE_CODE, 0));
        runtime.put("configReadRevision", readRevision);
        runtime.put("configAppliedRevision", appliedRevision);
        runtime.put("systemUiPid", systemUiPid(session));
        runtime.put("runtimeSessionHash", hashSession(session));
        Bundle features = value.getBundle(FusionActivationStatus.KEY_FEATURES);
        JSONObject states = new JSONObject();
        for (String feature : FEATURE_KEYS) {
            String state = features == null ? null : features.getString(feature);
            if (state != null) states.put(feature, redactText(state));
        }
        runtime.put("features", states);
        return runtime;
    }

    static JSONObject safeSettings(Bundle config) throws Exception {
        JSONObject settings = new JSONObject();
        if (config == null) return settings;
        for (String key : SAFE_SETTING_KEYS) {
            Object value = config.get(key);
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double) {
                settings.put(key, value);
            }
        }
        return settings;
    }

    static String redactText(String source) {
        if (source == null || source.isEmpty()) return source == null ? "" : source;
        String value = SECRET_ASSIGNMENT.matcher(source).replaceAll("$1<redacted>");
        value = BEARER.matcher(value).replaceAll("Bearer <redacted>");
        value = EMAIL.matcher(value).replaceAll("<email>");
        value = USER_PATH.matcher(value).replaceAll("<user-path>");
        return value;
    }

    private static int systemUiPid(String session) {
        if (session == null || session.isEmpty()) return -1;
        int first = session.indexOf(':');
        int second = first < 0 ? -1 : session.indexOf(':', first + 1);
        if (first < 0 || second < 0) return -1;
        try {
            int pid = Integer.parseInt(session.substring(first + 1, second));
            return pid > 0 ? pid : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String hashSession(String session) {
        if (session == null || session.isEmpty()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(session.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) result.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return "unavailable";
        }
    }

    private static boolean sameRuntimeState(Bundle before, Bundle after) {
        try {
            return runtimeSnapshot(before).toString().equals(runtimeSnapshot(after).toString());
        } catch (Exception ignored) {
            return false;
        }
    }

    static final class ExportOptions {
        final boolean includeFullLogcat;
        final boolean includeDmesg;
        final boolean includeLsposed;

        ExportOptions(boolean includeFullLogcat, boolean includeDmesg, boolean includeLsposed) {
            this.includeFullLogcat = includeFullLogcat;
            this.includeDmesg = includeDmesg;
            this.includeLsposed = includeLsposed;
        }

        static ExportOptions defaults() { return new ExportOptions(false, false, false); }

        JSONArray requestedSources() {
            JSONArray sources = new JSONArray();
            sources.put("filtered-logcat");
            if (includeFullLogcat) sources.put("full-logcat");
            if (includeDmesg) sources.put("dmesg");
            if (includeLsposed) sources.put("lsposed");
            return sources;
        }
    }

    interface Cancellation { boolean isCancelled(); }

    static final class ArchiveFile {
        final String name;
        final File file;
        final boolean truncated;

        ArchiveFile(String name, File file) { this(name, file, false); }

        ArchiveFile(String name, File file, boolean truncated) {
            this.name = name;
            this.file = file;
            this.truncated = truncated;
        }
    }

    static final class ExportCancelledException extends IOException {
        ExportCancelledException(String message) { super(message); }
        ExportCancelledException(String message, Throwable cause) { super(message, cause); }
    }

    static final class RootUnavailableException extends IOException {
        RootUnavailableException(String message) { super(message); }
        RootUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    static final class NoLogsException extends IOException {
        NoLogsException() { super("No matching logcat entries"); }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long maximum;
        private long written;

        CountingOutputStream(OutputStream delegate, long maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override public void write(int value) throws IOException {
            ensure(1);
            delegate.write(value);
            written++;
        }

        @Override public void write(byte[] buffer, int offset, int length) throws IOException {
            ensure(length);
            delegate.write(buffer, offset, length);
            written += length;
        }

        private void ensure(int count) throws IOException {
            if (count < 0 || written + count > maximum) throw new IOException("archive_size_limit_exceeded");
        }

        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }
}
