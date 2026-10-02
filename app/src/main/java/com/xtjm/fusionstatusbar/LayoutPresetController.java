package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.Toast;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Preset commands operate on the editor through one undoable host action. */
final class LayoutPresetController implements AutoCloseable {
    interface Choices {
        Dialog show(String title, String[] labels, java.util.function.IntConsumer selected);
    }
    interface Host {
        ControlCenterConfig draft();
        boolean compact();
        boolean canEdit();
        void loadPreset(ControlCenterConfig config, boolean compact);
    }
    private interface Action { void run() throws IOException; }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "FusionLayoutPresets"); thread.setDaemon(true); return thread;
    });
    private final Activity activity;
    private final Host host;
    private final Choices choices;
    private final LayoutPresetStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;
    private boolean busy;
    private Dialog dialog;

    LayoutPresetController(Activity activity, Host host, Choices choices) {
        this.activity = activity; this.host = host; this.choices = choices;
        this.store = new LayoutPresetStore(activity.getApplicationContext());
    }

    void show() {
        if (!available()) return;
        dialog = choices.show(activity.getString(R.string.preset_title),
                new String[] {activity.getString(R.string.preset_save_current), activity.getString(R.string.preset_manage)},
                position -> { if (position == 0) saveCurrent(); else showList(); });
    }

    void saveCurrent() {
        if (!available()) return;
        ControlCenterConfig draft = host.draft();
        boolean compact = host.compact();
        if (draft == null) return;
        nameDialog(R.string.preset_save_current, "", name -> mutate(() -> store.save(name, draft, compact), R.string.preset_saved));
    }

    private void showList() {
        if (!available()) return;
        busy = true;
        IO.execute(() -> {
            List<LayoutPresetStore.Preset> values;
            try { values = store.list(); }
            catch (IOException | RuntimeException error) {
                deliver(() -> { busy = false; message(R.string.preset_read_failed); }); return;
            }
            deliver(() -> {
                busy = false;
                if (values.isEmpty()) { message(R.string.preset_empty); return; }
                String[] names = new String[values.size()];
                for (int i = 0; i < names.length; i++) names[i] = values.get(i).name;
                dialog = choices.show(activity.getString(R.string.preset_manage), names,
                        index -> showActions(values.get(index)));
            });
        });
    }

    private void showActions(LayoutPresetStore.Preset preset) {
        if (!available()) return;
        dialog = choices.show(preset.name,
                new String[] {activity.getString(R.string.preset_load), activity.getString(R.string.preset_rename),
                        activity.getString(R.string.preset_copy), activity.getString(R.string.preset_delete)}, index -> {
                    switch (index) {
                        case 0:
                            if (host.canEdit()) { host.loadPreset(preset.config, preset.compact); message(R.string.preset_loaded); }
                            else message(R.string.backup_busy);
                            break;
                        case 1: nameDialog(R.string.preset_rename, preset.name,
                                name -> mutate(() -> store.rename(preset.id, name), R.string.preset_saved)); break;
                        case 2: nameDialog(R.string.preset_copy, "",
                                name -> mutate(() -> store.copy(preset.id, name), R.string.preset_saved)); break;
                        default:
                            dialog = new AlertDialog.Builder(activity).setTitle(R.string.preset_delete)
                                    .setMessage(activity.getString(R.string.preset_delete_confirmation, preset.name))
                                    .setNegativeButton(R.string.backup_cancel, null)
                                    .setPositiveButton(R.string.preset_delete, (confirmation, button) ->
                                            mutate(() -> store.delete(preset.id), R.string.preset_deleted)).create();
                            dialog.show();
                    }
                });
    }

    private void nameDialog(int title, String current, java.util.function.Consumer<String> submit) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.preset_name);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(LayoutPresetStore.MAX_NAME_LENGTH)});
        input.setText(current);
        input.setSelection(input.length());
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        frame.setPadding(padding, 0, padding, 0);
        frame.addView(input);
        AlertDialog currentDialog = new AlertDialog.Builder(activity).setTitle(title).setView(frame)
                .setNegativeButton(R.string.backup_cancel, null).setPositiveButton(R.string.preset_save, null).create();
        dialog = currentDialog;
        currentDialog.show();
        currentDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError(activity.getString(R.string.preset_invalid_name)); return; }
            currentDialog.dismiss();
            submit.accept(name);
        });
    }

    private void mutate(Action action, int success) {
        if (!available()) return;
        busy = true;
        IO.execute(() -> {
            int result = success;
            try { action.run(); }
            catch (IOException | RuntimeException error) {
                String reason = error.getMessage();
                result = "duplicate_preset_name".equals(reason) ? R.string.preset_duplicate_name
                        : "invalid_preset_name".equals(reason) ? R.string.preset_invalid_name
                        : "preset_limit".equals(reason) ? R.string.preset_limit : R.string.preset_write_failed;
            }
            int message = result;
            deliver(() -> { busy = false; message(message); });
        });
    }

    private boolean available() {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return false;
        if (!host.canEdit()) { message(R.string.backup_busy); return false; }
        if (busy) { message(R.string.backup_busy); return false; }
        return true;
    }
    private void message(int value) { if (!closed) Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private void deliver(Runnable task) {
        main.post(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) task.run(); });
    }
    @Override public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }
}
