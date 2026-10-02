package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SAF document handling and restore confirmation without keeping storage work on the UI thread. */
final class ConfigurationBackupController implements AutoCloseable {
    static final int REQUEST_EXPORT = 7101;
    static final int REQUEST_IMPORT = 7102;
    interface Host {
        boolean hasDraft();
        boolean beginRestore();
        void finishRestore(FusionConfigStore.WriteResult result);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "FusionConfigurationBackup"); thread.setDaemon(true); return thread;
    });
    private final Activity activity;
    private final Host host;
    private final android.content.Context app;
    private final FusionConfigRepository.Session session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;
    private boolean busy;
    private AlertDialog dialog;

    ConfigurationBackupController(Activity activity, Host host) {
        this.activity = activity; this.host = host; app = activity.getApplicationContext();
        session = FusionConfigRepository.forApplication(activity).newSession();
    }

    void exportConfiguration() {
        if (!available()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "fusion-statusbar-config.json");
        launch(intent, REQUEST_EXPORT);
    }

    void importConfiguration() {
        if (!available()) return;
        launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/plain"}), REQUEST_IMPORT);
    }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_EXPORT && requestCode != REQUEST_IMPORT) return false;
        if (closed || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        if (busy) { message(R.string.backup_busy); return true; }
        busy = true;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            session.read(value -> IO.execute(() -> {
                int message = R.string.backup_exported;
                try (OutputStream output = app.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("missing_document");
                    output.write(ConfigurationBackupCodec.encode(value));
                    output.flush();
                } catch (IOException | RuntimeException error) { message = R.string.backup_export_failed; }
                int result = message;
                deliver(() -> { busy = false; message(result); });
            }));
        } else {
            IO.execute(() -> {
                FusionConfig imported;
                try (InputStream input = app.getContentResolver().openInputStream(uri)) { imported = ConfigurationBackupCodec.read(input); }
                catch (IOException | RuntimeException error) {
                    deliver(() -> { busy = false; message(R.string.backup_import_invalid); });
                    return;
                }
                deliver(() -> { busy = false; confirmRestore(imported); });
            });
        }
        return true;
    }

    void undoRestore() {
        if (!available()) return;
        busy = true;
        IO.execute(() -> {
            boolean available = FusionConfigStore.canUndoRestore(app);
            deliver(() -> {
                busy = false;
                if (!available) { message(R.string.backup_undo_unavailable); return; }
                dialog = new AlertDialog.Builder(activity).setTitle(R.string.backup_undo)
                        .setMessage(R.string.backup_undo_confirmation)
                        .setNegativeButton(R.string.backup_cancel, null)
                        .setPositiveButton(R.string.backup_restore, (which, button) -> performRestore(null))
                        .create();
                dialog.show();
            });
        });
    }

    private void confirmRestore(FusionConfig imported) {
        String summary = activity.getString(R.string.backup_preview, imported.iconScale, imported.strokeScale,
                imported.statusBarHeight, imported.controlCenter.columns,
                activity.getString(imported.controlCenter.enabled ? R.string.backup_enabled : R.string.backup_disabled));
        if (host.hasDraft()) summary += "\n\n" + activity.getString(R.string.backup_keep_draft);
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.backup_restore_confirmation)
                .setMessage(summary).setNegativeButton(R.string.backup_cancel, null)
                .setPositiveButton(R.string.backup_restore, (which, button) -> performRestore(imported)).create();
        dialog.show();
    }

    private void performRestore(FusionConfig imported) {
        if (!available()) return;
        if (!host.beginRestore()) { message(R.string.backup_wait_for_save); return; }
        busy = true;
        java.util.function.Consumer<FusionConfigStore.WriteResult> complete = result -> {
            busy = false;
            host.finishRestore(result);
            message(result.success ? R.string.backup_restored : R.string.backup_restore_failed);
        };
        if (imported == null) session.undoRestore(complete);
        else session.restore(imported, complete);
    }

    private boolean available() {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return false;
        if (busy) { message(R.string.backup_busy); return false; }
        return true;
    }

    private void launch(Intent intent, int requestCode) {
        try { activity.startActivityForResult(intent, requestCode); }
        catch (ActivityNotFoundException | SecurityException error) { message(R.string.backup_picker_unavailable); }
    }

    private void deliver(Runnable task) {
        main.post(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) task.run(); });
    }

    private void message(int text) { if (!closed) Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }

    @Override public void close() {
        closed = true;
        session.close();
        main.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }
}
