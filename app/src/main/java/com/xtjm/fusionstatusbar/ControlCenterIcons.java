package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.util.Locale;

/** App-side preview assets. Native SystemUI tile icons are never replaced. */
final class ControlCenterIcons {
    private static final int ARTWORK_SIZE = 128, CONTENT_SIZE = 112;
    private ControlCenterIcons() { }

    static int resource(String spec) {
        String value = spec == null ? "" : spec.toLowerCase(Locale.ROOT);
        if (value.contains("screenrecord")) return R.drawable.cc_icon_videocam;
        switch (value) {
            case "wifi": return R.drawable.cc_icon_wifi;
            case "bt": return R.drawable.cc_icon_bluetooth;
            case "cell": case "internet": return R.drawable.cc_icon_swap_vert;
            case "airplane": return R.drawable.cc_icon_airplanemode_active;
            case "mute": case "quietmode": return R.drawable.cc_icon_notifications_off;
            case "flashlight": return R.drawable.cc_icon_flashlight_on;
            case "screenshot": return R.drawable.cc_icon_content_cut;
            case "batterysaver": case "battery": return R.drawable.cc_icon_battery_saver;
            case "rotation": return R.drawable.cc_icon_screen_lock_rotation;
            case "cast": case "screenmirroring": return R.drawable.cc_icon_cast;
            case "hotspot": case "link": return R.drawable.cc_icon_link;
            case "search": return R.drawable.cc_icon_search;
            case "gps": case "location": return R.drawable.cc_icon_gps_fixed;
            case "nfc": return R.drawable.cc_icon_nfc;
            case "controls": case "control:device-center": case "control:device-controls": return R.drawable.cc_preview_devices;
            case "satellite": return R.drawable.cc_preview_satellite;
            case "autobrightness": return R.drawable.cc_preview_brightness_auto;
            case "screenlock": return R.drawable.cc_preview_lock;
            case "night": return R.drawable.cc_preview_night;
            case "sync": return R.drawable.cc_preview_sync;
            case "vibrate": return R.drawable.cc_preview_vibrate;
            case "edit": return R.drawable.cc_preview_edit;
            case "settings": return R.drawable.cc_preview_settings;
            case "wallet": return R.drawable.cc_preview_wallet;
            case "control:brightness": case "control:slider": return R.drawable.cc_icon_wb_sunny;
            case "control:volume": return R.drawable.cc_icon_volume_up;
            case "control:media": return R.drawable.cc_icon_play_arrow;
            // Missing artwork is represented as a generic service, never as a "more" action.
            default: return R.drawable.cc_preview_app_service;
        }
    }

    static int color(String spec, boolean active) {
        if (!active) return Color.rgb(248, 249, 251);
        if ("cell".equalsIgnoreCase(spec) || "internet".equalsIgnoreCase(spec)) return Color.rgb(36, 200, 66);
        if ("mute".equalsIgnoreCase(spec) || "quietmode".equalsIgnoreCase(spec)) return Color.rgb(255, 79, 70);
        return Color.rgb(48, 126, 255);
    }

    static Drawable load(Context context, int resource, int color) {
        Drawable drawable = context.getDrawable(resource).mutate();
        drawable.setTint(color);
        return drawable;
    }

    enum ArtworkKind { INVALID, MONOCHROME, COLOR }

    /** Inspect only at bind time, never during drawing or dragging. Dense artwork with
     * detail is preserved; a nearly solid neutral plate is not a recognizable icon. */
    static ArtworkKind artworkKind(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return ArtworkKind.INVALID;
        int count = 0, neutral = 0, samples = 0, coreCount = 0;
        int coreMin = 255, coreMax = 0;
        int minR = 255, minG = 255, minB = 255, maxR = 0, maxG = 0, maxB = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 128), stepY = Math.max(1, bitmap.getHeight() / 128);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) for (int x = 0; x < bitmap.getWidth(); x += stepX) {
            samples++;
            int pixel = bitmap.getPixel(x, y);
            if (Color.alpha(pixel) < 32) continue;
            count++;
            int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
            minR = Math.min(minR, r); minG = Math.min(minG, g); minB = Math.min(minB, b);
            maxR = Math.max(maxR, r); maxG = Math.max(maxG, g); maxB = Math.max(maxB, b);
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 24) neutral++;
            if (Color.alpha(pixel) >= 240) {
                coreCount++;
                coreMin = Math.min(coreMin, Math.min(r, Math.min(g, b)));
                coreMax = Math.max(coreMax, Math.max(r, Math.max(g, b)));
            }
        }
        if (count < 4) return ArtworkKind.INVALID;
        boolean dense = count >= samples * 0.65f;
        boolean neutralArtwork = neutral >= count * 0.98f;
        boolean singleTone = maxR - minR <= 8 && maxG - minG <= 8 && maxB - minB <= 8;
        // Themed empty plates can have dark, translucent edge shadows. Their
        // near-opaque interior is still flat; a real interior logo adds range.
        boolean flatCore = coreCount >= count * 0.8f && coreMax - coreMin <= 8;
        if (dense && neutralArtwork && (singleTone || flatCore)) {
            return ArtworkKind.INVALID;
        }
        // Neutral multi-tone artwork must retain its gray details instead of
        // becoming an opaque white/black silhouette when the backdrop changes.
        return neutralArtwork && singleTone && !dense ? ArtworkKind.MONOCHROME : ArtworkKind.COLOR;
    }

    static final class Artwork {
        final Drawable drawable;
        final boolean captured, monochrome;
        private final boolean tintForBackdrop;
        Artwork(Context context, String spec, boolean active, Bitmap bitmap) {
            ArtworkKind kind = artworkKind(bitmap);
            captured = kind != ArtworkKind.INVALID;
            Bitmap source = captured ? bitmap : rasterize(load(context, resource(spec), color(spec, active)));
            BitmapDrawable prepared = new BitmapDrawable(context.getResources(), normalize(source));
            prepared.setFilterBitmap(true);
            drawable = prepared;
            if (!captured) source.recycle(); // Only our temporary raster, never caller-owned artwork.
            monochrome = kind == ArtworkKind.MONOCHROME;
            tintForBackdrop = monochrome || !captured && !active;
        }
        void setBackdrop(int color) {
            if (tintForBackdrop) drawable.setTint(foregroundColor(color));
        }
    }

    private static Bitmap rasterize(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(ARTWORK_SIZE, ARTWORK_SIZE, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, ARTWORK_SIZE, ARTWORK_SIZE);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** Resolve visible bounds once at bind time. Every source gets the same output
     * padding; source-edge clipping cannot enlarge one icon relative to another. */
    private static Bitmap normalize(Bitmap source) {
        int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            if (Color.alpha(source.getPixel(x, y)) < 32) continue;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        Bitmap result = Bitmap.createBitmap(ARTWORK_SIZE, ARTWORK_SIZE, Bitmap.Config.ARGB_8888);
        if (right < left || bottom < top) return result;
        Rect content = new Rect(left, top, right + 1, bottom + 1);
        float scale = CONTENT_SIZE / (float) Math.max(content.width(), content.height());
        float width = content.width() * scale, height = content.height() * scale;
        RectF target = new RectF((ARTWORK_SIZE - width) / 2, (ARTWORK_SIZE - height) / 2,
                (ARTWORK_SIZE + width) / 2, (ARTWORK_SIZE + height) / 2);
        new Canvas(result).drawBitmap(source, content, target, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return result;
    }

    static int foregroundColor(int backdrop) {
        return Color.luminance(backdrop) > 0.179f ? 0xff181a1d : 0xfff8f9fb;
    }

    static int composite(int surface, int strength, int backdrop) {
        float alpha = Color.alpha(surface) / 255f * Math.max(0, Math.min(100, strength)) / 100f;
        return Color.rgb(Math.round(Color.red(surface) * alpha + Color.red(backdrop) * (1 - alpha)),
                Math.round(Color.green(surface) * alpha + Color.green(backdrop) * (1 - alpha)),
                Math.round(Color.blue(surface) * alpha + Color.blue(backdrop) * (1 - alpha)));
    }

    static void draw(Canvas canvas, Drawable drawable, float cx, float cy, float size) {
        drawable.setBounds(Math.round(cx - size / 2), Math.round(cy - size / 2),
                Math.round(cx + size / 2), Math.round(cy + size / 2));
        drawable.draw(canvas);
    }
}
