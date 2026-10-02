package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/** Strict, bounded interchange format; the permissive on-device migration reader is separate. */
final class ConfigurationBackupCodec {
    static final int MAX_BYTES = 288 * 1024;
    private static final String FORMAT = "fusion-statusbar-config";

    private ConfigurationBackupCodec() { }

    static byte[] encode(FusionConfig config) throws IOException {
        try {
            JSONObject document = new JSONObject().put("format", FORMAT).put("version", 1)
                    .put("config", new JSONObject(FusionConfigStore.encode(config)));
            byte[] bytes = document.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("backup_too_large");
            return bytes;
        } catch (JSONException error) { throw new IOException("backup_encoding_failed", error); }
    }

    static FusionConfig read(InputStream input) throws IOException {
        if (input == null) throw new IOException("missing_document");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) {
            if (buffer.size() + count > MAX_BYTES) throw new IOException("backup_too_large");
            buffer.write(chunk, 0, count);
        }
        return decode(buffer.toByteArray());
    }

    static FusionConfig decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("invalid_backup_size");
        try {
            JSONObject document = parseObject(strictText(bytes));
            JSONObject config;
            if (document.has("format")) {
                if (document.length() != 3 || !FORMAT.equals(document.get("format"))
                        || !Integer.valueOf(1).equals(document.get("version"))) throw new IOException("unsupported_backup_format");
                config = document.getJSONObject("config");
            } else {
                // Earlier releases stored a bare configuration object. Its schema controls migration.
                config = document;
            }
            return configFromJson(config, false);
        } catch (JSONException | IllegalArgumentException error) { throw new IOException("invalid_backup", error); }
    }

    static JSONObject configJson(ControlCenterConfig config) throws JSONException {
        Bundle values = new Bundle();
        config.writeTo(values);
        JSONObject result = new JSONObject();
        for (String key : new java.util.TreeSet<>(values.keySet())) result.put(key, values.get(key));
        return result;
    }

    static ControlCenterConfig controlCenterFromJson(JSONObject object) throws IOException {
        try { return configFromJson(object, true).controlCenter; }
        catch (JSONException error) { throw new IOException("invalid_preset", error); }
    }

    private static FusionConfig configFromJson(JSONObject object, boolean controlCenterOnly) throws IOException, JSONException {
        Bundle expected = FusionConfig.defaults().toBundle();
        if (controlCenterOnly) {
            expected.clear();
            ControlCenterConfig.defaults().writeTo(expected);
        }
        int schema = 0;
        if (!controlCenterOnly && object.has(FusionConfig.KEY_SCHEMA_VERSION)) {
            Object value = object.get(FusionConfig.KEY_SCHEMA_VERSION);
            if (!(value instanceof Integer)) throw new IOException("invalid_config_schema");
            schema = (Integer) value;
            if (schema < 0 || schema > FusionConfig.CONFIG_SCHEMA_VERSION) throw new IOException("unsupported_config_schema");
        }
        boolean current = controlCenterOnly || schema == FusionConfig.CONFIG_SCHEMA_VERSION;
        if (current && object.length() != expected.size()) throw new IOException("incomplete_configuration");
        if (!current) {
            expected.putInt(FusionConfig.KEY_LEGACY_BATTERY_SCALE, 100);
            for (int i = 0; i < 5; i++) {
                expected.putBoolean("telemetry_enabled_" + i, false);
                expected.putInt("telemetry_position_" + i, 0);
            }
        }
        Bundle values = new Bundle();
        int settings = 0;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!expected.containsKey(key)) throw new IOException("unknown_configuration_field");
            Object type = expected.get(key), value = object.get(key);
            if (type instanceof Boolean && value instanceof Boolean) values.putBoolean(key, (Boolean) value);
            else if (type instanceof Integer && value instanceof Integer) values.putInt(key, (Integer) value);
            else if (type instanceof Long && (value instanceof Integer || value instanceof Long)) values.putLong(key, ((Number) value).longValue());
            else if (type instanceof String && value instanceof String && ((String) value).length() <= 128 * 1024) values.putString(key, (String) value);
            else throw new IOException("invalid_configuration_type");
            if (!key.equals(FusionConfig.KEY_SCHEMA_VERSION) && !key.equals(FusionConfig.KEY_REVISION)) settings++;
        }
        if (settings == 0) throw new IOException("empty_configuration");
        String layout = values.getString(ControlCenterConfig.KEY_LAYOUT_PLAN, "");
        if (!layout.isEmpty() && ControlCenterLayoutPlan.decode(layout) == null) throw new IOException("invalid_layout_plan");
        FusionConfig normalized = FusionConfig.fromBundle(values);
        Bundle canonical = normalized.toBundle();
        for (String key : values.keySet()) {
            if (key.equals(FusionConfig.KEY_SCHEMA_VERSION) || key.equals(ControlCenterConfig.KEY_LAYOUT_PLAN)
                    || key.equals(FusionConfig.KEY_LEGACY_BATTERY_SCALE) || key.startsWith("telemetry_enabled_")
                    || key.startsWith("telemetry_position_")) continue;
            if (!Objects.equals(values.get(key), canonical.get(key))) throw new IOException("invalid_configuration_value");
        }
        if (values.containsKey(FusionConfig.KEY_LEGACY_BATTERY_SCALE)) {
            int scale = values.getInt(FusionConfig.KEY_LEGACY_BATTERY_SCALE);
            if (scale != FusionConfig.defaults().withIconScale(scale).iconScale) throw new IOException("invalid_configuration_value");
        }
        return normalized;
    }

    static String strictText(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) { throw new IOException("invalid_utf8", error); }
    }

    static JSONObject parseObject(String text) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JSONObject result = readObject(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing_json");
            return result;
        } catch (JSONException | IllegalStateException error) { throw new IOException("invalid_json", error); }
    }

    private static JSONObject readObject(JsonReader reader, int depth) throws IOException, JSONException {
        if (depth > 4) throw new IOException("json_too_deep");
        JSONObject result = new JSONObject();
        Set<String> seen = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.length() > 128 || !seen.add(key) || seen.size() > 256) throw new IOException("invalid_json_field");
            result.put(key, readValue(reader, depth + 1));
        }
        reader.endObject();
        return result;
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException, JSONException {
        if (depth > 4) throw new IOException("json_too_deep");
        switch (reader.peek()) {
            case BEGIN_OBJECT: return readObject(reader, depth);
            case BEGIN_ARRAY:
                JSONArray array = new JSONArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.length() >= 256) throw new IOException("invalid_json_array");
                    array.put(readValue(reader, depth + 1));
                }
                reader.endArray();
                return array;
            case BOOLEAN: return reader.nextBoolean();
            case STRING: return reader.nextString();
            case NUMBER:
                String number = reader.nextString();
                if (!number.matches("-?(0|[1-9][0-9]*)")) throw new IOException("invalid_integer");
                try {
                    long value = Long.parseLong(number);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return Integer.valueOf((int) value);
                    return Long.valueOf(value);
                } catch (NumberFormatException error) { throw new IOException("integer_out_of_range", error); }
            default: throw new IOException("invalid_json_value");
        }
    }
}
