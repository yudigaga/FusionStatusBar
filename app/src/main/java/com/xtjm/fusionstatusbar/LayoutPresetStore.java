package com.xtjm.fusionstatusbar;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Named snapshots include both layout modes and every control-center appearance setting. */
final class LayoutPresetStore {
    static final int MAX_PRESETS = 24;
    static final int MAX_NAME_LENGTH = 40;
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private final FusionConfigStore.SnapshotFile file;

    static final class Preset {
        final String id;
        final String name;
        final ControlCenterConfig config;
        final boolean compact;
        Preset(String id, String name, ControlCenterConfig config, boolean compact) {
            this.id = id; this.name = name; this.config = config; this.compact = compact;
        }
    }

    LayoutPresetStore(Context context) {
        this(new BoundedAtomicDocument(new File(context.getFilesDir(), "control_center_presets_v1.json"), MAX_BYTES));
    }

    LayoutPresetStore(FusionConfigStore.SnapshotFile file) { this.file = file; }

    List<Preset> list() throws IOException { synchronized (LOCK) { return Collections.unmodifiableList(read()); } }

    Preset save(String name, ControlCenterConfig config, boolean compact) throws IOException {
        synchronized (LOCK) {
            List<Preset> values = read();
            if (values.size() >= MAX_PRESETS) throw new IOException("preset_limit");
            String validName = validateName(name, values, null);
            // Store a normalized copy and validate it before publishing the new preset list.
            try { config = ConfigurationBackupCodec.controlCenterFromJson(ConfigurationBackupCodec.configJson(config)); }
            catch (JSONException error) { throw new IOException("invalid_preset", error); }
            Preset preset = new Preset(UUID.randomUUID().toString(), validName, config, compact);
            values.add(preset);
            write(values);
            return preset;
        }
    }

    void rename(String id, String name) throws IOException {
        synchronized (LOCK) {
            List<Preset> values = read();
            int index = index(values, id);
            Preset before = values.get(index);
            values.set(index, new Preset(id, validateName(name, values, id), before.config, before.compact));
            write(values);
        }
    }

    Preset copy(String id, String name) throws IOException {
        synchronized (LOCK) {
            List<Preset> values = read();
            Preset original = values.get(index(values, id));
            return save(name, original.config, original.compact);
        }
    }

    void delete(String id) throws IOException {
        synchronized (LOCK) {
            List<Preset> values = read();
            values.remove(index(values, id));
            write(values);
        }
    }

    private List<Preset> read() throws IOException {
        byte[] bytes;
        try { bytes = file.read(); }
        catch (FileNotFoundException missing) { return new ArrayList<>(); }
        if (bytes.length > MAX_BYTES) throw new IOException("preset_file_too_large");
        try {
            JSONObject document = ConfigurationBackupCodec.parseObject(ConfigurationBackupCodec.strictText(bytes));
            int version = document.getInt("version");
            if (document.length() != 2 || (version != 1 && version != 2)) throw new IOException("unsupported_preset_format");
            Object entries = document.get("presets");
            int count = version == 2 && entries instanceof JSONArray ? ((JSONArray) entries).length()
                    : version == 1 && entries instanceof JSONObject ? ((JSONObject) entries).length() : -1;
            if (count < 0) throw new IOException("invalid_preset_file");
            if (count > MAX_PRESETS) throw new IOException("preset_limit");
            List<Preset> result = new ArrayList<>();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < count; i++) {
                String id;
                JSONObject item;
                if (version == 2) {
                    item = ((JSONArray) entries).getJSONObject(i);
                    id = item.getString("id");
                    if (item.length() != 4) throw new IOException("invalid_preset");
                } else {
                    JSONObject object = (JSONObject) entries;
                    id = object.names().getString(i);
                    item = object.getJSONObject(id);
                    if (item.length() != 3) throw new IOException("invalid_preset");
                }
                if (!UUID.fromString(id).toString().equals(id)) throw new IOException("invalid_preset_id");
                if (!ids.add(id) || !(item.get("compact") instanceof Boolean) || !(item.get("name") instanceof String))
                    throw new IOException("invalid_preset");
                String name = validateName(item.getString("name"), result, null);
                result.add(new Preset(id, name, ConfigurationBackupCodec.controlCenterFromJson(item.getJSONObject("config")),
                        item.getBoolean("compact")));
            }
            return result;
        } catch (JSONException | IllegalArgumentException error) { throw new IOException("invalid_preset_file", error); }
    }

    private void write(List<Preset> values) throws IOException {
        try {
            JSONArray entries = new JSONArray();
            for (Preset preset : values) entries.put(new JSONObject().put("id", preset.id).put("name", preset.name)
                    .put("compact", preset.compact).put("config", ConfigurationBackupCodec.configJson(preset.config)));
            byte[] bytes = new JSONObject().put("version", 2).put("presets", entries).toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("preset_file_too_large");
            file.write(bytes);
        } catch (JSONException error) { throw new IOException("preset_encoding_failed", error); }
    }

    private static String validateName(String name, List<Preset> existing, String excluded) throws IOException {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) throw new IOException("invalid_preset_name");
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IOException("invalid_preset_name");
        for (Preset preset : existing) if (!preset.id.equals(excluded) && preset.name.equalsIgnoreCase(value)) throw new IOException("duplicate_preset_name");
        return value;
    }

    private static int index(List<Preset> values, String id) throws IOException {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id.equals(id)) return i;
        throw new IOException("preset_not_found");
    }
}
