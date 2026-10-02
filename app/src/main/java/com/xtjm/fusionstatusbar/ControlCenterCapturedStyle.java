package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.json.JSONObject;

/** Last captured visual state, kept separate from the user's editable layout and live device state. */
final class ControlCenterCapturedStyle {
    final ControlCenterLayoutPlan.Shape shape;
    final int radiusDp, state;
    final String label, subtitle;
    final float level;
    final Bitmap icon;

    ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape shape, int radiusDp, int state,
            String label, String subtitle, float level, Bitmap icon) {
        this.shape = shape;
        this.radiusDp = Math.max(0, Math.min(64, radiusDp));
        this.state = state;
        this.label = label == null ? "" : label;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.level = Float.isFinite(level) && level >= 0 ? Math.min(1, level) : -1;
        this.icon = icon;
    }

    ControlCenterCapturedStyle withFallbackIcon(Bitmap fallback) {
        if (ControlCenterIcons.artworkKind(icon) != ControlCenterIcons.ArtworkKind.INVALID
                || ControlCenterIcons.artworkKind(fallback) == ControlCenterIcons.ArtworkKind.INVALID) return this;
        return new ControlCenterCapturedStyle(shape, radiusDp, state, label, subtitle, level, fallback);
    }

    JSONObject toJson() throws org.json.JSONException {
        JSONObject json = new JSONObject().put("shape", shape.name()).put("radius", radiusDp)
                .put("state", state).put("label", label).put("subtitle", subtitle).put("level", level);
        if (icon != null) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            json.put("icon", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
        }
        return json;
    }

    static ControlCenterCapturedStyle parse(JSONObject json) {
        return parse(json, true);
    }

    static ControlCenterCapturedStyle parse(JSONObject json, boolean includeIcon) {
        if (json == null) return null;
        try {
            Bitmap icon = null;
            String encoded = json.optString("icon", "");
            if (includeIcon && !encoded.isEmpty() && encoded.length() < 96_000) {
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth <= 128 && bounds.outHeight <= 128) {
                    icon = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            }
            return new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.valueOf(json.getString("shape")),
                    json.optInt("radius", 24), json.optInt("state", -1), json.optString("label", ""),
                    json.optString("subtitle", ""), (float) json.optDouble("level", -1), icon);
        } catch (IllegalArgumentException | org.json.JSONException ignored) { return null; }
    }
}
