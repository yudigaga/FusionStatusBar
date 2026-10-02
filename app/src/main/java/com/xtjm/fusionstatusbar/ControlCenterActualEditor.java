package com.xtjm.fusionstatusbar;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Base64;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.ViewParent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Displays a SystemUI capture and keeps its real tile bounds interactive. */
final class ControlCenterActualEditor extends FrameLayout {
    interface Listener {
        void onTileClicked(String spec);

        void onTileMoved(String source, String destination);

        void onTileMovedToEnd(String source);

        default void onTileMovedToPosition(String source, float x, float y) {
        }
    }

    private final ImageView image;
    private final FrameLayout tileLayer;
    private final Listener listener;
    private Bitmap previewBitmap;
    private LayoutData layout;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private int bitmapWidth;
    private int bitmapHeight;
    private List<Tile> displayedTiles = new ArrayList<>();
    private InteractionOverlay interactionOverlay;
    private String draggingSpec;
    private float dragDownRawX;
    private float dragDownRawY;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragging;
    private boolean touchCaptured;
    private boolean editingEnabled = true;
    private final Runnable beginLongPress = () -> {
        if (draggingSpec == null) return;
        dragging = true;
        if (interactionOverlay != null) interactionOverlay.invalidate();
    };

    ControlCenterActualEditor(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClipChildren(false);
        setClipToPadding(false);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        addView(image, new FrameLayout.LayoutParams(-1, -1));
        tileLayer = new FrameLayout(context);
        tileLayer.setClipChildren(false);
        tileLayer.setClipToPadding(false);
        tileLayer.setOnTouchListener(this::onTileLayerTouch);
        addView(tileLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    void setEditingEnabled(boolean enabled) {
        editingEnabled = enabled;
        tileLayer.setVisibility(enabled && layout != null ? VISIBLE : GONE);
        if (!enabled) {
            removeCallbacks(beginLongPress);
            resetTouchDrag();
        }
    }

    void setPreview(Bitmap bitmap, String serializedLayout) {
        previewBitmap = bitmap;
        image.setImageBitmap(bitmap);
        bitmapWidth = bitmap == null ? 0 : bitmap.getWidth();
        bitmapHeight = bitmap == null ? 0 : bitmap.getHeight();
        image.setVisibility(VISIBLE);
        tileLayer.setVisibility(editingEnabled ? VISIBLE : GONE);
        layout = LayoutData.parse(serializedLayout);
        displayedTiles = layout == null ? new ArrayList<>() : new ArrayList<>(layout.tiles);
        rebuildTiles();
        setVisibility(VISIBLE);
        requestLayout();
    }

    void clearPreview() {
        previewBitmap = null;
        image.setImageDrawable(null);
        bitmapWidth = 0;
        bitmapHeight = 0;
        image.setVisibility(GONE);
        layout = null;
        displayedTiles = new ArrayList<>();
        tileLayer.removeAllViews();
        interactionOverlay = null;
        tileLayer.setVisibility(GONE);
        setVisibility(GONE);
    }

    boolean hasTileLayout() {
        return layout != null && !layout.tiles.isEmpty();
    }

    LayoutData capturedLayout() { return layout; }

    ControlCenterCapturedStyle capturedStyle(String spec) {
        return layout == null || spec == null ? null : layout.styles.get(ControlCenterConfig.canonicalSpec(spec));
    }

    /** Returns a screenshot crop for the current native tile, or null when it is unavailable. */
    Bitmap capturedThumbnail(String spec) {
        if (previewBitmap == null || layout == null || spec == null
                || layout.width <= 0 || layout.height <= 0) {
            return null;
        }
        Tile tile = findDisplayed(spec);
        if (tile == null) return null;
        float scaleX = previewBitmap.getWidth() / (float) layout.width;
        float scaleY = previewBitmap.getHeight() / (float) layout.height;
        int left = clamp(Math.round(tile.left * scaleX), 0, previewBitmap.getWidth() - 1);
        int top = clamp(Math.round(tile.top * scaleY), 0, previewBitmap.getHeight() - 1);
        int right = clamp(Math.round((tile.left + tile.width) * scaleX), left + 1,
                previewBitmap.getWidth());
        int bottom = clamp(Math.round((tile.top + tile.height) * scaleY), top + 1,
                previewBitmap.getHeight());
        try {
            return Bitmap.createBitmap(previewBitmap, left, top,
                    Math.max(1, right - left), Math.max(1, bottom - top));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    List<String> capturedSpecs() {
        if (layout == null) return new ArrayList<>();
        if (layout.editableMode != null) {
            LinkedHashSet<String> specs = new LinkedHashSet<>();
            for (ControlCenterLayoutPlan.Item item : layout.editableMode.items) for (String spec : item.specs()) {
                if (!ControlCenterComponentSpec.isSpecial(spec)) specs.add(spec);
            }
            return new ArrayList<>(specs);
        }
        ArrayList<Tile> sorted = new ArrayList<>(layout.tiles);
        sorted.sort(Comparator.comparingInt((Tile tile) -> tile.top)
                .thenComparingInt(tile -> tile.left));
        LinkedHashSet<String> specs = new LinkedHashSet<>();
        for (Tile tile : sorted) {
            if (!ControlCenterComponentSpec.isSpecial(tile.spec)) {
                specs.add(tile.spec.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(specs);
    }

    List<String> capturedComponents() {
        if (layout == null) return new ArrayList<>();
        ArrayList<Tile> sorted = new ArrayList<>(layout.tiles);
        sorted.sort(Comparator.comparingInt((Tile tile) -> tile.top)
                .thenComparingInt(tile -> tile.left));
        LinkedHashSet<String> specs = new LinkedHashSet<>();
        for (Tile tile : sorted) {
            if (ControlCenterComponentSpec.isSpecial(tile.spec)) {
                specs.add(tile.spec.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(specs);
    }

    int capturedComponentSpan(String spec) {
        Tile tile = findComponent(spec);
        return tile == null ? ControlCenterComponentSpec.defaultSpan(spec) : tile.nativeSpan;
    }

    int capturedComponentRows(String spec) {
        Tile tile = findComponent(spec);
        return tile == null ? ControlCenterComponentSpec.defaultRows(spec) : tile.nativeRows;
    }

    boolean isRightOf(String spec) {
        Tile tile = findDisplayed(spec);
        return tile != null && isRightOf(tile);
    }

    String componentDropTarget(String source, float x, float y) {
        if (layout == null) return null;
        boolean right = isRightPosition(x);
        Tile best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Tile tile : displayedTiles) {
            if (!ControlCenterComponentSpec.isSpecial(tile.spec)
                    || tile.spec.equalsIgnoreCase(source)
                    || isRightOf(tile) != right) continue;
            float centerX = tile.left + tile.width / 2f;
            float centerY = tile.top + tile.height / 2f;
            float distance = (centerX - x) * (centerX - x)
                    + (centerY - y) * (centerY - y);
            if (x >= tile.left && x <= tile.left + tile.width
                    && y >= tile.top && y <= tile.top + tile.height) {
                return tile.spec;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tile;
            }
        }
        return best == null ? null : best.spec;
    }

    boolean isRightPosition(float x) {
        return layout != null && x >= layout.width / 2f;
    }

    private boolean isRightOf(Tile tile) {
        return layout != null && tile.left + tile.width / 2f >= layout.width / 2f;
    }

    private Tile findDisplayed(String spec) {
        if (spec == null) return null;
        for (Tile tile : displayedTiles) {
            if (ControlCenterConfig.canonicalSpec(spec).equals(ControlCenterConfig.canonicalSpec(tile.spec))) return tile;
        }
        return null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private Tile findComponent(String spec) {
        if (layout == null || spec == null) return null;
        String normalized = spec.toLowerCase(Locale.ROOT);
        for (Tile tile : layout.tiles) {
            if (ControlCenterComponentSpec.isSpecial(tile.spec)
                    && normalized.equals(tile.spec.toLowerCase(Locale.ROOT))) {
                return tile;
            }
        }
        return null;
    }

    static boolean hasSerializedTiles(String serializedLayout) {
        LayoutData parsed = LayoutData.parse(serializedLayout);
        return parsed != null && !parsed.tiles.isEmpty();
    }

    void setDraft(List<String> specs, ControlCenterConfig settings) {
        if (layout == null || specs == null || settings == null) return;
        ArrayList<Tile> slots = new ArrayList<>(layout.tiles);
        slots.removeIf(tile -> ControlCenterComponentSpec.isSpecial(tile.spec));
        slots.sort(Comparator.comparingInt((Tile tile) -> tile.top)
                .thenComparingInt(tile -> tile.left));
        Map<String, Tile> originals = new HashMap<>();
        for (Tile tile : layout.tiles) {
            originals.put(ControlCenterConfig.canonicalSpec(tile.spec), tile);
        }
        ArrayList<Tile> next = new ArrayList<>();
        ArrayList<Tile> componentSlots = new ArrayList<>();
        for (Tile tile : layout.tiles) {
            if (ControlCenterComponentSpec.isSpecial(tile.spec)) componentSlots.add(tile);
        }
        componentSlots.sort(Comparator.comparingInt((Tile tile) -> tile.top)
                .thenComparingInt(tile -> tile.left));
        Map<String, Tile> componentsBySpec = new HashMap<>();
        for (Tile tile : componentSlots) {
            componentsBySpec.put(tile.spec.toLowerCase(Locale.ROOT), tile);
        }
        ArrayList<String> componentOrder = new ArrayList<>();
        if (!settings.componentOrder.isEmpty()) {
            for (String spec : settings.componentOrder.split(",")) {
                if (componentsBySpec.containsKey(spec) && !componentOrder.contains(spec)) {
                    componentOrder.add(spec);
                }
            }
        }
        for (Tile tile : componentSlots) {
            String spec = tile.spec.toLowerCase(Locale.ROOT);
            if (!componentOrder.contains(spec)) componentOrder.add(spec);
        }
        ArrayList<Tile> unusedComponentSlots = new ArrayList<>(componentSlots);
        for (String componentSpec : componentOrder) {
            Tile tile = componentsBySpec.get(componentSpec);
            if (tile == null) continue;
            if (!ControlCenterComponentSpec.isSpecial(tile.spec)) continue;
            String configuredSide = settings.componentSide(componentSpec);
            boolean right = "right".equals(configuredSide)
                    || (configuredSide.isEmpty() && isRightOf(tile));
            Tile slot = null;
            for (Tile candidate : unusedComponentSlots) {
                if (isRightOf(candidate) == right) {
                    slot = candidate;
                    break;
                }
            }
            if (slot == null && !unusedComponentSlots.isEmpty()) {
                slot = unusedComponentSlots.get(0);
            }
            if (slot == null) continue;
            unusedComponentSlots.remove(slot);
            int width = tile.width;
            int height = tile.height;
            if (ControlCenterTileLayout.hasShape(settings.layout, tile.spec)) {
                width = Math.max(1, Math.round(tile.width * settings.tileWidth(tile.spec)
                        / (float) Math.max(1, tile.nativeSpan)));
                height = componentHeight(tile, settings.tileHeight(tile.spec));
            }
            next.add(new Tile(tile.spec, slot.left, slot.top, width, height,
                    tile.nativeSpan, tile.nativeRows));
        }
        for (int index = 0; index < specs.size() && !slots.isEmpty(); index++) {
            String spec = specs.get(index);
            if (spec == null || spec.isEmpty()) continue;
            Tile original = originals.get(ControlCenterConfig.canonicalSpec(spec));
            if (original == null && index >= slots.size()) continue;
            Tile slot = slots.get(Math.min(index, slots.size() - 1));
            Tile base = original == null ? slot : original;
            int width = Math.max(1, base.width * settings.tileWidth(spec));
            int height = Math.max(1, base.height * settings.tileHeight(spec));
            next.add(new Tile(spec, slot.left, slot.top, width, height, 1, 1));
        }
        displayedTiles = next;
        rebuildTiles();
        requestLayout();
    }

    private int componentHeight(Tile tile, int desiredRows) {
        int nativeHeight = layout == null ? 0 : layout.rowHeight(tile.nativeRows);
        int desiredHeight = layout == null ? 0 : layout.rowHeight(desiredRows);
        if (nativeHeight > 0 && desiredHeight > 0) {
            return Math.max(1, Math.round(tile.height * desiredHeight
                    / (float) nativeHeight));
        }
        return Math.max(1, Math.round(tile.height * desiredRows
                / (float) Math.max(1, tile.nativeRows)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (layout != null && layout.width > 0 && layout.height > 0
                && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int desiredHeight = Math.max(1, Math.round(width * layout.height
                    / (float) layout.width));
            int height = resolveSize(desiredHeight, heightMeasureSpec);
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            setMeasuredDimension(getMeasuredWidth(), height);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (layout == null || layout.width <= 0 || layout.height <= 0) return;
        float widthScale = getWidth() / (float) layout.width;
        scale = widthScale;
        offsetX = (getWidth() - layout.width * scale) / 2f;
        offsetY = 0f;
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            Matrix matrix = new Matrix();
            matrix.setScale(scale * layout.width / (float) bitmapWidth,
                    scale * layout.height / (float) bitmapHeight);
            matrix.postTranslate(offsetX, offsetY);
            image.setImageMatrix(matrix);
        }
    }

    private void rebuildTiles() {
        tileLayer.removeAllViews();
        interactionOverlay = null;
        if (layout == null) return;
        interactionOverlay = new InteractionOverlay(getContext());
        interactionOverlay.setOnTouchListener(this::onTileLayerTouch);
        tileLayer.addView(interactionOverlay,
                new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.START));
        tileLayer.requestLayout();
    }

    private boolean onTileLayerTouch(View ignored, MotionEvent event) {
        if (!editingEnabled) return false;
        float localX = event.getRawX() - screenLeft();
        float localY = event.getRawY() - screenTop();
        float layoutX = scale <= 0f ? 0f : (localX - offsetX) / scale;
        float layoutY = scale <= 0f ? 0f : (localY - offsetY) / scale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchCaptured = true;
                requestParentDisallowIntercept(ignored, true);
                Tile hit = tileAt(layoutX, layoutY);
                draggingSpec = hit == null ? null : hit.spec;
                // The editor lives inside the settings ScrollView. Keep the
                // pointer stream here or the ScrollView turns a long drag into
                // a page fling and later tiles stop receiving the gesture.
                dragging = false;
                dragDownRawX = event.getRawX();
                dragDownRawY = event.getRawY();
                Log.i("FusionStatusBar", "control-center editor touch down hit="
                        + (hit == null ? "<none>" : hit.spec)
                        + " x=" + layoutX + " y=" + layoutY
                        + " tiles=" + displayedTiles.size() + " scale=" + scale);
                if (hit == null) return true;
                removeCallbacks(beginLongPress);
                postDelayed(beginLongPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingSpec == null) return true;
                if (dragging) {
                    dragOffsetX = event.getRawX() - dragDownRawX;
                    dragOffsetY = event.getRawY() - dragDownRawY;
                    if (interactionOverlay != null) interactionOverlay.invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!touchCaptured) return true;
                removeCallbacks(beginLongPress);
                if (dragging && draggingSpec != null) {
                    finishTouchDrag(event);
                } else if (draggingSpec != null) {
                    listener.onTileClicked(draggingSpec);
                    resetTouchDrag();
                } else {
                    resetTouchDrag();
                }
                requestParentDisallowIntercept(ignored, false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (touchCaptured) {
                    removeCallbacks(beginLongPress);
                    resetTouchDrag();
                }
                requestParentDisallowIntercept(ignored, false);
                return true;
            default:
                return true;
        }
    }

    private void requestParentDisallowIntercept(View source, boolean disallow) {
        ViewParent parent = source == null ? getParent() : source.getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    private Tile tileAt(float x, float y) {
        Tile hit = null;
        long smallestArea = Long.MAX_VALUE;
        float nearestCenter = Float.MAX_VALUE;
        for (Tile tile : displayedTiles) {
            if (x < tile.left || x > tile.left + tile.width
                    || y < tile.top || y > tile.top + tile.height) continue;
            long area = (long) tile.width * tile.height;
            float centerX = tile.left + tile.width / 2f;
            float centerY = tile.top + tile.height / 2f;
            float distance = (centerX - x) * (centerX - x)
                    + (centerY - y) * (centerY - y);
            if (area < smallestArea || (area == smallestArea && distance < nearestCenter)) {
                smallestArea = area;
                nearestCenter = distance;
                hit = tile;
            }
        }
        if (hit != null) return hit;

        // Captured SystemUI bounds and the rendered bitmap can differ by a
        // few pixels after density/window scaling. Accept a small distance to
        // the nearest rectangle, but never turn a distant blank area into a hit.
        float density = getResources().getDisplayMetrics().density;
        float slop = Math.max(24f, 24f * density / Math.max(0.01f, scale));
        float maxDistance = slop * slop;
        float bestDistance = maxDistance;
        for (Tile tile : displayedTiles) {
            float dx = x < tile.left ? tile.left - x
                    : x > tile.left + tile.width ? x - (tile.left + tile.width) : 0f;
            float dy = y < tile.top ? tile.top - y
                    : y > tile.top + tile.height ? y - (tile.top + tile.height) : 0f;
            float distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                hit = tile;
            }
        }
        return hit;
    }

    private void finishTouchDrag(MotionEvent event) {
        String source = draggingSpec;
        float x = scale <= 0f ? 0f : (event.getRawX() - screenLeft() - offsetX) / scale;
        float y = scale <= 0f ? 0f : (event.getRawY() - screenTop() - offsetY) / scale;
        resetTouchDrag();
        if (source == null) return;
        if (ControlCenterComponentSpec.isSpecial(source)) {
            Log.i("FusionStatusBar", "control-center editor component drop spec=" + source
                    + " x=" + x + " y=" + y);
            listener.onTileMovedToPosition(source, x, y);
            return;
        }
        String destination = ordinaryDropTarget(source, x, y);
        Log.i("FusionStatusBar", "control-center editor tile drop source=" + source
                + " destination=" + destination);
        if (destination == null) listener.onTileMovedToEnd(source);
        else listener.onTileMoved(source, destination);
    }

    private int screenLeft() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return location[0];
    }

    private int screenTop() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return location[1];
    }

    private String ordinaryDropTarget(String source, float x, float y) {
        for (Tile tile : displayedTiles) {
            if (ControlCenterComponentSpec.isSpecial(tile.spec)
                    || tile.spec.equalsIgnoreCase(source)) continue;
            if (x >= tile.left && x <= tile.left + tile.width
                    && y >= tile.top && y <= tile.top + tile.height) {
                return tile.spec;
            }
        }
        return null;
    }

    private void resetTouchDrag() {
        draggingSpec = null;
        dragOffsetX = 0f;
        dragOffsetY = 0f;
        dragging = false;
        touchCaptured = false;
        if (interactionOverlay != null) interactionOverlay.invalidate();
    }

    private boolean onTileDrag(View target, DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) return true;
        if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED
                || event.getAction() == DragEvent.ACTION_DRAG_LOCATION
                || event.getAction() == DragEvent.ACTION_DRAG_EXITED) return true;
        if (event.getAction() != DragEvent.ACTION_DROP) return false;
        String source = sourceSpec(event);
        String destination = target == null || target.getTag() == null
                ? null : String.valueOf(target.getTag());
        if (source == null || source.equals(destination)) return true;
        if (destination == null) return true;
        if (!ControlCenterComponentSpec.isSpecial(source)
                && ControlCenterComponentSpec.isSpecial(destination)) return true;
        listener.onTileMoved(source, destination);
        return true;
    }

    private boolean onEditorDrag(View target, DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) return true;
        if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED
                || event.getAction() == DragEvent.ACTION_DRAG_LOCATION
                || event.getAction() == DragEvent.ACTION_DRAG_EXITED) return true;
        if (event.getAction() != DragEvent.ACTION_DROP) return false;
        String source = sourceSpec(event);
        if (source != null) {
            if (ControlCenterComponentSpec.isSpecial(source)) {
                float x = scale <= 0f ? 0f : (event.getX() - offsetX) / scale;
                float y = scale <= 0f ? 0f : (event.getY() - offsetY) / scale;
                listener.onTileMovedToPosition(source, x, y);
            } else {
                listener.onTileMovedToEnd(source);
            }
        }
        return true;
    }

    private String sourceSpec(DragEvent event) {
        ClipData data = event.getClipData();
        if (data == null || data.getItemCount() == 0 || data.getItemAt(0) == null) return null;
        CharSequence value = data.getItemAt(0).getText();
        return value == null ? null : value.toString();
    }

    private final class InteractionOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        InteractionOverlay(Context context) {
            super(context);
            setContentDescription("control-center-editor");
            setFocusable(false);
            setClickable(false);
            setLongClickable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            for (Tile tile : displayedTiles) {
                float left = offsetX + tile.left * scale;
                float top = offsetY + tile.top * scale;
                float right = offsetX + (tile.left + tile.width) * scale;
                float bottom = offsetY + (tile.top + tile.height) * scale;
                boolean selected = tile.spec.equals(draggingSpec);
                if (selected && dragging) {
                    left += dragOffsetX;
                    right += dragOffsetX;
                    top += dragOffsetY;
                    bottom += dragOffsetY;
                }
                paint.setColor(selected
                        ? Color.argb(220, 76, 147, 255)
                        : Color.argb(90, 76, 147, 255));
                canvas.drawRoundRect(left + 1f, top + 1f,
                        Math.max(left + 2f, right - 1f),
                        Math.max(top + 2f, bottom - 1f),
                        12f, 12f, paint);
            }
        }
    }

    static final class LayoutData {
        final int width;
        final int height;
        final int[] rowHeights;
        final List<Tile> tiles;
        final Map<String, ControlCenterCapturedStyle> styles = new HashMap<>();
        float density;
        ControlCenterLayoutPlan.Mode editableMode;
        int spacingDp;

        LayoutData(int width, int height, int[] rowHeights, List<Tile> tiles) {
            this.width = width;
            this.height = height;
            this.rowHeights = rowHeights;
            this.tiles = tiles;
        }

        int rowHeight(int rows) {
            if (rows < 1 || rows > rowHeights.length) return 0;
            return rowHeights[rows - 1];
        }

        static LayoutData parse(String value) {
            if (value == null || value.isEmpty() || value.length() > 2_000_000) return null;
            if (value.startsWith("{")) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(value);
                    if (json.getInt("version") != 3) return null;
                    String bounds = json.getString("bounds");
                    if (!bounds.startsWith("v1|") && !bounds.startsWith("v2|")) return null;
                    LayoutData data = parse(bounds);
                    if (data == null) return null;
                    data.density = (float) json.optDouble("density", 0);
                    ControlCenterLayoutPlan editable = ControlCenterLayoutPlan.decode(json.optString("editablePlan", ""));
                    if (editable != null) {
                        data.editableMode = editable.regular;
                        data.spacingDp = Math.max(0, Math.min(24, json.optInt("spacingDp", 0)));
                    }
                    org.json.JSONObject styles = json.optJSONObject("styles");
                    Map<String, org.json.JSONObject> styleSources = new HashMap<>();
                    if (styles != null) {
                        java.util.Iterator<String> keys = styles.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            styleSources.put(ControlCenterConfig.canonicalSpec(key), styles.optJSONObject(key));
                        }
                    }
                    if (styles != null) for (Tile tile : data.tiles) {
                        String key = ControlCenterConfig.canonicalSpec(tile.spec);
                        ControlCenterCapturedStyle style = ControlCenterCapturedStyle.parse(styleSources.get(key),
                                json.optInt("iconSampling", 1) >= 2);
                        if (style != null) data.styles.put(key, style);
                    }
                    if (styles != null && data.editableMode != null) for (ControlCenterLayoutPlan.Item item : data.editableMode.items) {
                        for (String spec : item.specs()) if (!data.styles.containsKey(spec)) {
                            String key = ControlCenterConfig.canonicalSpec(spec);
                            ControlCenterCapturedStyle style = ControlCenterCapturedStyle.parse(styleSources.get(key),
                                    json.optInt("iconSampling", 1) >= 2);
                            if (style != null) data.styles.put(key, style);
                        }
                    }
                    return data;
                } catch (org.json.JSONException ignored) { return null; }
            }
            String[] entries = value.split("\\|");
            if (entries.length < 3 || (!"v1".equals(entries[0])
                    && !"v2".equals(entries[0]))) return null;
            try {
                int width = Integer.parseInt(entries[1]);
                int height = Integer.parseInt(entries[2]);
                if (width <= 0 || height <= 0 || width > 16384 || height > 32768 || entries.length > 263) return null;
                int[] rowHeights = new int[4];
                int tileStart = 3;
                if ("v2".equals(entries[0])) {
                    if (entries.length < 7) return null;
                    for (int row = 0; row < rowHeights.length; row++) {
                        rowHeights[row] = Math.max(0,
                                Integer.parseInt(entries[3 + row]));
                    }
                    tileStart = 7;
                }
                ArrayList<Tile> tiles = new ArrayList<>();
                for (int index = tileStart; index < entries.length; index++) {
                    String[] fields = entries[index].split(",", 7);
                    if (fields.length < 5) continue;
                    String spec = new String(Base64.decode(fields[0], Base64.DEFAULT),
                            StandardCharsets.UTF_8);
                    int left = Integer.parseInt(fields[1]);
                    int top = Integer.parseInt(fields[2]);
                    int tileWidth = Integer.parseInt(fields[3]);
                    int tileHeight = Integer.parseInt(fields[4]);
                    int nativeSpan = fields.length > 5
                            ? Integer.parseInt(fields[5])
                            : ControlCenterComponentSpec.defaultSpan(spec);
                    int nativeRows = fields.length > 6
                            ? Integer.parseInt(fields[6])
                            : ControlCenterComponentSpec.defaultRows(spec);
                    if (!spec.isEmpty() && spec.length() <= 128 && tileWidth > 0 && tileHeight > 0
                            && tileWidth <= width && tileHeight <= height) {
                        tiles.add(new Tile(spec, left, top, tileWidth, tileHeight,
                                Math.max(1, nativeSpan), Math.max(1, nativeRows)));
                    }
                }
                return new LayoutData(width, height, rowHeights, tiles);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    static final class Tile {
        final String spec;
        final int left;
        final int top;
        final int width;
        final int height;
        final int nativeSpan;
        final int nativeRows;

        Tile(String spec, int left, int top, int width, int height) {
            this(spec, left, top, width, height,
                    ControlCenterComponentSpec.isSpecial(spec)
                            ? ControlCenterComponentSpec.defaultSpan(spec) : 1,
                    ControlCenterComponentSpec.isSpecial(spec)
                            ? ControlCenterComponentSpec.defaultRows(spec) : 1);
        }

        Tile(String spec, int left, int top, int width, int height,
                int nativeSpan, int nativeRows) {
            this.spec = spec;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.nativeSpan = Math.max(1, nativeSpan);
            this.nativeRows = Math.max(1, nativeRows);
        }
    }
}
