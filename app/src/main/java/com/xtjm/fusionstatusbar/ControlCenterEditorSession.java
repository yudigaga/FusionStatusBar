package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Owns an unpublished edit and its history independently of views and configuration storage. */
final class ControlCenterEditorSession {
    static final int MAX_HISTORY = 24;
    static final int MAX_SERIALIZED_CHARS = 240 * 1024;
    static final int MAX_RESTORED_CHARS = 8 * 1024 * 1024;
    private static final java.util.concurrent.atomic.AtomicLong SNAPSHOT_SEQUENCE = new java.util.concurrent.atomic.AtomicLong();
    private long snapshotTime = nextSnapshotTime();
    private ControlCenterConfig baseline;
    private ControlCenterConfig draft;
    private boolean compact;
    private String selection = "";
    private final ArrayDeque<State> undo = new ArrayDeque<>();
    private final ArrayDeque<State> redo = new ArrayDeque<>();

    private static final class State {
        final ControlCenterConfig config;
        final boolean compact;
        final String selection;
        State(ControlCenterConfig config, boolean compact, String selection) {
            this.config = config; this.compact = compact; this.selection = selection;
        }
    }

    ControlCenterEditorSession(ControlCenterConfig saved) {
        baseline = saved;
        draft = saved;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(saved.layoutPlan);
        compact = plan != null && plan.runtimeUsesCompact(false);
    }

    ControlCenterConfig draft() { return draft; }
    ControlCenterConfig baseline() { return baseline; }
    boolean compact() { return compact; }
    String selection() { return selection; }
    int undoCount() { return undo.size(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    boolean isDirty() {
        if (!sameConfig(baseline, draft)) return true;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(draft.layoutPlan);
        return plan != null && plan.runtimeLayout != (compact
                ? ControlCenterLayoutPlan.RuntimeLayout.COMPACT
                : ControlCenterLayoutPlan.RuntimeLayout.REGULAR);
    }

    void selectMode(boolean value) {
        if (compact != value) { compact = value; selection = ""; touch(); }
    }

    void select(String id) {
        String next = id == null ? "" : id;
        if (!selection.equals(next)) { selection = next; touch(); }
    }

    boolean edit(ControlCenterConfig next) {
        if (next == null || sameConfig(draft, next)) return false;
        undo.push(state());
        while (undo.size() > MAX_HISTORY) undo.removeLast();
        redo.clear();
        draft = next;
        touch();
        return true;
    }

    boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(state());
        restore(undo.pop());
        touch();
        return true;
    }

    boolean loadPreset(ControlCenterConfig next, boolean compactMode) {
        if (next == null || (sameConfig(draft, next) && compact == compactMode)) return false;
        undo.push(state());
        while (undo.size() > MAX_HISTORY) undo.removeLast();
        redo.clear();
        draft = next;
        compact = compactMode;
        selection = "";
        touch();
        return true;
    }

    boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(state());
        restore(redo.pop());
        touch();
        return true;
    }

    /** A successful older save advances the baseline without discarding subsequent edits. */
    void saved(ControlCenterConfig submitted, ControlCenterConfig committed) {
        touch();
        boolean unchanged = sameConfig(draft, submitted);
        baseline = committed;
        if (unchanged) {
            draft = committed;
            undo.clear();
            redo.clear();
        }
    }

    void rebase(ControlCenterConfig published) {
        baseline = published;
        touch();
    }

    private State state() { return new State(draft, compact, selection); }
    private void restore(State value) {
        draft = value.config; compact = value.compact; selection = value.selection;
    }

    String serialize() {
        try {
            JSONObject result = new JSONObject();
            result.put("version", 1);
            result.put("saved_at", snapshotTime);
            result.put("baseline", configJson(baseline));
            result.put("current", stateJson(state()));
            JSONArray previous = historyJson(undo);
            JSONArray following = historyJson(redo);
            result.put("undo", previous);
            result.put("redo", following);
            String encoded = result.toString();
            // Bound history on disk; the coordinator keeps large payloads out of Activity state.
            while (encoded.length() > MAX_SERIALIZED_CHARS && (previous.length() > 0 || following.length() > 0)) {
                if (previous.length() >= following.length()) previous.remove(previous.length() - 1);
                else following.remove(following.length() - 1);
                encoded = result.toString();
            }
            return encoded;
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to encode editor session", error);
        }
    }

    static ControlCenterEditorSession restore(ControlCenterConfig saved, String encoded) {
        ControlCenterEditorSession session = new ControlCenterEditorSession(saved);
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_RESTORED_CHARS) return session;
        try {
            JSONObject value = new JSONObject(encoded);
            if (value.optInt("version") != 1) return session;
            ControlCenterConfig previousBaseline = configFromJson(value.getJSONObject("baseline"));
            State current = stateFromJson(value.getJSONObject("current"));
            ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(current.config.layoutPlan);
            ControlCenterConfig publication = plan == null ? current.config
                    : current.config.withLayoutPlan(plan.forPublication(current.compact).encode());
            if (!sameConfig(previousBaseline, saved) && sameConfig(publication, saved)) {
                session.selectMode(current.compact);
                session.select(current.selection);
                return session;
            }
            // An external publication must not turn an old clean session into a stale draft.
            if (!sameConfig(previousBaseline, saved) && sameConfig(previousBaseline, current.config)) return session;
            session.restore(current);
            readHistory(value.optJSONArray("undo"), session.undo);
            readHistory(value.optJSONArray("redo"), session.redo);
            session.snapshotTime = value.optLong("saved_at", 0);
            SNAPSHOT_SEQUENCE.accumulateAndGet(session.snapshotTime, Math::max);
            return session;
        } catch (JSONException | RuntimeException ignored) {
            return new ControlCenterEditorSession(saved);
        }
    }

    static boolean sameConfig(ControlCenterConfig first, ControlCenterConfig second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        Bundle a = new Bundle(), b = new Bundle();
        first.writeTo(a); second.writeTo(b);
        for (String key : a.keySet()) if (!java.util.Objects.equals(a.get(key), b.get(key))) return false;
        return true;
    }

    private static long nextSnapshotTime() {
        return SNAPSHOT_SEQUENCE.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
    }

    private void touch() { snapshotTime = nextSnapshotTime(); }

    static String latestSnapshot(String first, String second) {
        return snapshotTime(second) > snapshotTime(first) ? second : first;
    }

    private static long snapshotTime(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_RESTORED_CHARS) return -1;
        try { return new JSONObject(encoded).optLong("saved_at", 0); }
        catch (JSONException error) { return -1; }
    }

    private static JSONObject configJson(ControlCenterConfig config) throws JSONException {
        Bundle bundle = new Bundle();
        config.writeTo(bundle);
        JSONObject result = new JSONObject();
        for (String key : new java.util.TreeSet<>(bundle.keySet())) result.put(key, bundle.get(key));
        return result;
    }

    private static ControlCenterConfig configFromJson(JSONObject value) throws JSONException {
        Bundle bundle = new Bundle();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object field = value.get(key);
            if (field instanceof Boolean) bundle.putBoolean(key, (Boolean) field);
            else if (field instanceof Number) bundle.putInt(key, ((Number) field).intValue());
            else if (field instanceof String) bundle.putString(key, (String) field);
            else throw new JSONException("Invalid configuration field");
        }
        return ControlCenterConfig.fromBundle(bundle);
    }

    private static JSONObject stateJson(State value) throws JSONException {
        return new JSONObject().put("config", configJson(value.config))
                .put("compact", value.compact).put("selection", value.selection);
    }

    private static State stateFromJson(JSONObject value) throws JSONException {
        String selected = value.optString("selection", "");
        if (selected.length() > 256) selected = "";
        return new State(configFromJson(value.getJSONObject("config")), value.optBoolean("compact"), selected);
    }

    private static JSONArray historyJson(ArrayDeque<State> values) throws JSONException {
        JSONArray result = new JSONArray();
        for (State value : values) result.put(stateJson(value));
        return result;
    }

    private static void readHistory(JSONArray values, ArrayDeque<State> target) throws JSONException {
        if (values == null) return;
        for (int i = 0; i < Math.min(MAX_HISTORY, values.length()); i++) target.addLast(stateFromJson(values.getJSONObject(i)));
    }
}
