package com.xtjm.fusionstatusbar;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The snapshot's atomic token is also the commit marker for one-shot durable restore undo. */
final class ConfigurationRecoveryStore {
    static final int MAX_BYTES = 600 * 1024;

    private ConfigurationRecoveryStore() { }

    static FusionConfigStore.SnapshotFile file(Context context) {
        return new BoundedAtomicDocument(new File(context.createDeviceProtectedStorageContext().getFilesDir(),
                "configuration_recovery_v1.json"), MAX_BYTES);
    }

    static FusionConfigStore.WriteResult restore(FusionConfigStore.SnapshotFile configFile,
            FusionConfigStore.SnapshotFile recoveryFile, FusionConfig imported, FusionConfig fallback) {
        FusionConfig before = FusionConfigStore.read(configFile, fallback);
        try {
            String epoch = UUID.randomUUID().toString();
            JSONObject backups = new JSONObject();
            JSONObject previous = read(recoveryFile);
            String active = FusionConfigStore.restoreToken(configFile);
            // Retain the active undo if writing the new configuration fails or the process stops.
            if (!active.isEmpty() && previous.has(active)) backups.put(active, previous.getJSONObject(active));
            backups.put(epoch, new JSONObject(FusionConfigStore.encode(before)));
            recoveryFile.write(new JSONObject().put("version", 1).put("backups", backups)
                    .toString().getBytes(StandardCharsets.UTF_8));
            return FusionConfigStore.write(configFile, imported, fallback, epoch);
        } catch (IOException | JSONException | RuntimeException error) {
            return new FusionConfigStore.WriteResult(false, before, "restore_backup_failed");
        }
    }

    static FusionConfigStore.WriteResult undo(FusionConfigStore.SnapshotFile configFile,
            FusionConfigStore.SnapshotFile recoveryFile, FusionConfig fallback) {
        FusionConfig current = FusionConfigStore.read(configFile, fallback);
        try {
            JSONObject backups = read(recoveryFile);
            String active = FusionConfigStore.restoreToken(configFile);
            if (active.isEmpty() || !backups.has(active)) return new FusionConfigStore.WriteResult(false, current, "no_restore_backup");
            FusionConfig before = ConfigurationBackupCodec.decode(backups.getJSONObject(active).toString().getBytes(StandardCharsets.UTF_8));
            // No backup uses the new epoch, so retries after a successful undo cannot toggle back.
            return FusionConfigStore.write(configFile, before, fallback, UUID.randomUUID().toString());
        } catch (IOException | JSONException | RuntimeException error) {
            return new FusionConfigStore.WriteResult(false, current, "restore_backup_unavailable");
        }
    }

    static boolean canUndo(FusionConfigStore.SnapshotFile configFile, FusionConfigStore.SnapshotFile recoveryFile) {
        try {
            String active = FusionConfigStore.restoreToken(configFile);
            JSONObject backups = read(recoveryFile);
            if (active.isEmpty() || !backups.has(active)) return false;
            ConfigurationBackupCodec.decode(backups.getJSONObject(active).toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException | RuntimeException error) { return false; }
    }

    private static JSONObject read(FusionConfigStore.SnapshotFile file) throws IOException, JSONException {
        try {
            byte[] bytes = file.read();
            if (bytes.length > MAX_BYTES) throw new IOException("recovery_too_large");
            JSONObject result = ConfigurationBackupCodec.parseObject(ConfigurationBackupCodec.strictText(bytes));
            if (result.length() != 2 || result.getInt("version") != 1) throw new IOException("unsupported_recovery_format");
            JSONObject backups = result.getJSONObject("backups");
            if (backups.length() > 2) throw new IOException("too_many_recovery_entries");
            return backups;
        } catch (FileNotFoundException missing) { return new JSONObject(); }
    }
}
