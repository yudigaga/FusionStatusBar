package com.xtjm.fusionstatusbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.HapticFeedbackConstants;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** Editable, grid-aligned rendering of a resolved control-center layout. */
@SuppressLint("ViewConstructor")
final class ControlCenterGridEditor extends ViewGroup {
    enum EditAction { REMOVE, TOGGLE_LOCK, TOGGLE_HIDDEN, TOGGLE_DIRECTION, UNPAIR }

    interface Listener {
        void onItemClicked(String id);

        void onItemMoved(String id, int x, int y);

        default void onItemResized(String id, int width, int height) { }
        default void onItemAction(String id, EditAction action) { }
        default void onSelectionChanged(String id) { }
    }

    static final class Cell {
        final String id;
        final String firstSpec;
        final String secondSpec;
        final String firstLabel;
        final String secondLabel;
        final int x;
        final int y;
        final int width;
        final int height;
        final boolean horizontalPair;
        final Bitmap firstBitmap;
        final Bitmap secondBitmap;
        final ControlCenterLayoutPlan.Shape shape;
        final int cornerRadius;
        final boolean locked;
        final boolean hidden;
        final int zIndex;
        final int tileScale;
        ControlCenterCapturedStyle firstStyle, secondStyle;
        ControlCenterLayoutPlan.Item groupItem;
        java.util.Map<String, ControlCenterCapturedStyle> groupStyles = java.util.Collections.emptyMap();
        java.util.Map<String, String> groupLabels = java.util.Collections.emptyMap();

        Cell(String id, String firstLabel, String secondLabel, int x, int y,
                int width, int height, boolean horizontalPair) {
            this(id, "", "", firstLabel, secondLabel, x, y, width, height, horizontalPair,
                    null, null, ControlCenterLayoutPlan.Shape.RECTANGLE, 0,
                    false, false, 0, 100);
        }

        Cell(String id, String firstSpec, String secondSpec, String firstLabel,
                String secondLabel, int x, int y,
                int width, int height, boolean horizontalPair,
                Bitmap firstBitmap, Bitmap secondBitmap,
                ControlCenterLayoutPlan.Shape shape, int cornerRadius,
                boolean locked, boolean hidden, int zIndex, int tileScale) {
            this.id = id;
            this.firstSpec = firstSpec == null ? "" : firstSpec;
            this.secondSpec = secondSpec == null ? "" : secondSpec;
            this.firstLabel = firstLabel;
            this.secondLabel = secondLabel;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.horizontalPair = horizontalPair;
            this.firstBitmap = firstBitmap;
            this.secondBitmap = secondBitmap;
            this.shape = shape == null ? ControlCenterLayoutPlan.Shape.RECTANGLE : shape;
            this.cornerRadius = Math.max(0, cornerRadius);
            this.locked = locked;
            this.hidden = hidden;
            this.zIndex = zIndex;
            this.tileScale = tileScale;
        }

        Cell withCapturedStyles(ControlCenterCapturedStyle first, ControlCenterCapturedStyle second) {
            this.firstStyle = first;
            this.secondStyle = second;
            return this;
        }

        Cell withGroup(ControlCenterLayoutPlan.Item item, java.util.Map<String, ControlCenterCapturedStyle> styles,
                java.util.Map<String, String> labels) {
            groupItem = item; groupStyles = styles; groupLabels = labels; return this;
        }
    }

    private final Listener listener;
    private final ArrayList<Cell> cells = new ArrayList<>();
    private final ArrayList<EditorItemHost> hosts = new ArrayList<>();
    private int columns = 4, rows = 1, gap, rowHeight, radius, cellWidth;
    private int cardStrength = 100, tileStrength = 100;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int touchSlop;
    private final DecelerateInterpolator settleInterpolator = new DecelerateInterpolator();
    private final android.animation.ValueAnimator.AnimatorUpdateListener settleUpdate = animation -> positionOverlay();
    private ControlCenterEditOverlay overlay;
    private EditorItemHost selected, touching;
    private enum Phase { IDLE, PENDING, DRAG, RESIZE, LOCKED, CANCELLED }
    private Phase phase = Phase.IDLE;
    private int pointerId = -1;
    private float downX, downY;
    private int targetX, targetY, targetWidth, targetHeight;
    private int previewWidth, previewHeight;
    private boolean moved, validPlacement;
    private final Runnable longPress = () -> {
        if (phase == Phase.PENDING && touching != null) {
            touching.performLongClick();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    ControlCenterGridEditor(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gap = ControlCenterGridGeometry.gapPx(context, 4);
        rowHeight = ControlCenterGridGeometry.rowHeightPx(context);
        radius = ControlCenterGridGeometry.radiusPx(context, 24);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        setBackgroundColor(Color.argb(10, 0, 0, 0));
    }

    void setGeometry(int spacingDp, int radiusDp) {
        int nextGap = ControlCenterGridGeometry.gapPx(getContext(), spacingDp);
        int nextRowHeight = ControlCenterGridGeometry.rowHeightPx(getContext());
        int nextRadius = ControlCenterGridGeometry.radiusPx(getContext(), radiusDp);
        if (gap != nextGap || rowHeight != nextRowHeight || radius != nextRadius) cancelGesture();
        gap = nextGap;
        rowHeight = nextRowHeight;
        radius = nextRadius;
        for (EditorItemHost host : hosts) host.bind(host.cell);
        requestLayout();
    }

    void setMaterialStrength(int card, int tile) {
        cardStrength = card; tileStrength = tile;
        for (EditorItemHost host : hosts) host.card.setMaterialStrength(card, tile);
    }

    void setCells(int columnCount, List<Cell> next) {
        // Any external update (undo, import, lock, refresh) invalidates the in-flight gesture.
        for (EditorItemHost host : hosts) host.rememberVisualBounds();
        resetTouch();
        columns = Math.max(3, Math.min(6, columnCount));
        java.util.Map<String, EditorItemHost> retained = new java.util.HashMap<>();
        for (EditorItemHost host : hosts) retained.put(host.cell.id, host);
        hosts.clear();
        cells.clear();
        rows = 1;
        if (next != null) for (Cell cell : next) if (cell != null) cells.add(cell);
        cells.sort(java.util.Comparator.comparingInt(cell -> cell.zIndex));
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            rows = Math.max(rows, cell.y + cell.height);
            EditorItemHost host = retained.remove(cell.id);
            if (host == null) host = new EditorItemHost();
            host.bind(cell);
            hosts.add(host);
            if (indexOfChild(host) != i) {
                if (host.getParent() == this) removeView(host);
                addView(host, i);
            }
        }
        for (EditorItemHost obsolete : retained.values()) {
            obsolete.stopAnimations();
            removeView(obsolete);
        }
        if (selected != null && !hosts.contains(selected)) finishEditing();
        if (selected != null) {
            selected.card.setEditing(!selected.cell.locked, true);
            overlay.bind(selected.cell);
        }
        requestLayout();
        invalidate();
    }

    /** Ends transient editing only; never mutates the draft. Also used on canvas/lifecycle changes. */
    void finishEditing() {
        cancelGesture();
        if (selected != null) {
            selected.card.setEditing(false, true);
            selected.setTranslationZ(0);
            selected = null;
            listener.onSelectionChanged("");
        }
        if (overlay != null) {
            removeView(overlay);
            overlay = null;
        }
        requestLayout();
    }

    private void select(EditorItemHost host) {
        if (selected != host) {
            if (selected != null) {
                selected.card.setEditing(false, true);
                selected.setTranslationZ(0);
            }
            selected = host;
            listener.onSelectionChanged(host.cell.id);
        }
        host.card.setEditing(!host.cell.locked, true);
        host.setTranslationZ(dp(12));
        if (overlay == null) {
            overlay = new ControlCenterEditOverlay(getContext(), new ControlCenterEditOverlay.Actions() {
                public void run(EditAction action) {
                    EditorItemHost current = selected;
                    if (current == null || (current.cell.locked && action != EditAction.TOGGLE_LOCK)) return;
                    cancelGesture();
                    listener.onItemAction(current.cell.id, action);
                }
                public void properties() {
                    if (selected != null) listener.onItemClicked(selected.cell.id);
                }
                public void done() { finishEditing(); }
            });
            addView(overlay);
        }
        overlay.bind(host.cell);
        requestLayout();
        if (touching == host) {
            phase = host.cell.locked ? Phase.LOCKED : Phase.DRAG;
            disallowParent(true);
        }
    }

    void restoreSelection(String id) {
        if (id == null || id.isEmpty() || (selected != null && selected.cell.id.equals(id))) return;
        for (EditorItemHost host : hosts) {
            if (host.cell.id.equals(id)) { select(host); return; }
        }
    }

    /** Transparent interaction shell. Renderer scaling never changes logical hit coordinates. */
    private final class EditorItemHost extends FrameLayout {
        private Cell cell;
        private final ControlCenterCardView card;
        private float fromX = Float.NaN, fromY, fromWidth, fromHeight;
        EditorItemHost() {
            super(ControlCenterGridEditor.this.getContext());
            setClipChildren(false);
            setClipToPadding(false);
            setPivotX(0);
            setPivotY(0);
            card = new ControlCenterCardView(getContext());
            addView(card, new FrameLayout.LayoutParams(-1, -1));
            setOnClickListener(view -> listener.onItemClicked(cell.id));
            setOnLongClickListener(view -> { select(this); return true; });
        }
        void rememberVisualBounds() {
            if (isLaidOut()) {
                fromX = getX();
                fromY = getY();
                fromWidth = getWidth() * getScaleX();
                fromHeight = getHeight() * getScaleY();
            }
            animate().cancel();
        }
        void stopAnimations() {
            animate().cancel();
            animate().setUpdateListener(null);
            card.setEditing(false, false);
        }
        void bind(Cell next) {
            cell = next;
            setTag(next.id);
            setAlpha(next.hidden ? 0.36f : 1f);
            setContentDescription((next.secondLabel == null ? next.firstLabel
                    : next.firstLabel + "、" + next.secondLabel + "组合卡片")
                    + (next.locked ? "、已锁定" : "") + (next.hidden ? "、已隐藏" : "")
                    + (next.firstStyle == null || next.firstStyle.state < 0 ? "、示意预览" : "、拉取时状态"));
            if (next.groupItem != null) {
                card.bindGroup(next.groupItem, next.groupStyles, next.groupLabels,
                        Math.round(radius / getResources().getDisplayMetrics().density));
                card.setMaterialStrength(cardStrength, tileStrength);
                return;
            }
            card.bind(new ControlCenterCardView.Model(next.firstSpec, next.secondSpec,
                    next.firstLabel, next.secondLabel, next.horizontalPair,
                    capturedActive(next.firstStyle, next.firstSpec), capturedActive(next.secondStyle, next.secondSpec),
                    next.width > next.height, next.shape,
                    next.cornerRadius > 0 ? dp(next.cornerRadius) : radius, next.tileScale,
                    next.firstStyle, next.secondStyle));
            card.setMaterialStrength(cardStrength, tileStrength);
        }
    }

    private static boolean sampleActive(String spec) {
        return "cell".equalsIgnoreCase(spec) || "bt".equalsIgnoreCase(spec)
                || "rotation".equalsIgnoreCase(spec) || "mute".equalsIgnoreCase(spec)
                || "quietmode".equalsIgnoreCase(spec);
    }

    private static boolean capturedActive(ControlCenterCapturedStyle style, String spec) {
        return style != null && style.state >= 0 ? style.state == 2 : sampleActive(spec);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (overlay != null && overlay.hitsResize(event.getX(), event.getY())) return true;
            if (overlay != null && overlay.hitsControl(event.getX(), event.getY())) return false;
            return findHost(event.getX(), event.getY()) != null || selected != null;
        }
        return phase != Phase.IDLE;
    }

    // Clicks belong to the individual accessible hosts; ACTION_UP delegates to host.performClick().
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            boolean resize = overlay != null && overlay.hitsResize(event.getX(), event.getY());
            touching = resize ? selected : findHost(event.getX(), event.getY());
            if (touching == null) { finishEditing(); return true; }
            pointerId = event.getPointerId(0);
            downX = event.getX();
            downY = event.getY();
            Cell cell = touching.cell;
            touching.animate().cancel();
            // Start from committed geometry, not an intermediate settling animation.
            touching.setTranslationX(0);
            touching.setTranslationY(0);
            touching.setScaleX(1);
            touching.setScaleY(1);
            touching.fromX = Float.NaN;
            targetX = cell.x;
            targetY = cell.y;
            targetWidth = cell.width;
            targetHeight = cell.height;
            previewWidth = touching.getWidth();
            previewHeight = touching.getHeight();
            validPlacement = true;
            moved = false;
            if (resize && !cell.locked) {
                phase = Phase.RESIZE;
                disallowParent(true);
            } else if (touching == selected && !cell.locked) {
                phase = Phase.DRAG;
                disallowParent(true);
            } else {
                phase = Phase.PENDING;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) {
            cancelGesture();
            return true;
        }
        if (touching == null || phase == Phase.IDLE) return true;
        int index = event.findPointerIndex(pointerId);
        if (index < 0) { cancelGesture(); return true; }
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            updateGesture(event.getX(index) - downX, event.getY(index) - downY);
        }
        if (action == MotionEvent.ACTION_UP) {
            EditorItemHost host = touching;
            Phase completed = phase;
            boolean commit = moved && validPlacement;
            int x = targetX, y = targetY, width = targetWidth, height = targetHeight;
            host.rememberVisualBounds();
            resetTouch(); // Clear before listener reentrancy: setCells may run synchronously.
            if (completed == Phase.PENDING) {
                host.performClick();
            } else if (commit && completed == Phase.DRAG && (x != host.cell.x || y != host.cell.y)) {
                listener.onItemMoved(host.cell.id, x, y);
            } else if (commit && completed == Phase.RESIZE
                    && (width != host.cell.width || height != host.cell.height)) {
                listener.onItemResized(host.cell.id, width, height);
            } else if (!validPlacement && moved) {
                announceForAccessibility("位置不可用，布局未修改");
            }
            requestLayout();
            invalidate();
        }
        return true;
    }

    private void updateGesture(float dx, float dy) {
        if (phase == Phase.PENDING) {
            if (Math.hypot(dx, dy) > touchSlop) {
                removeCallbacks(longPress);
                phase = Phase.CANCELLED; // Let the outer ScrollView take ordinary scrolling.
            }
            return;
        }
        if (phase != Phase.DRAG && phase != Phase.RESIZE) return;
        if (!moved && Math.hypot(dx, dy) <= touchSlop) return;
        moved = true;
        Cell cell = touching.cell;
        int pitchX = Math.max(1, cellWidth + gap), pitchY = Math.max(1, rowHeight + gap);
        if (phase == Phase.DRAG) {
            float tx = clamp(dx, -cell.x * pitchX, (columns - cell.x - cell.width) * pitchX);
            float ty = clamp(dy, -cell.y * pitchY,
                    (ControlCenterLayoutPlan.MAX_ROWS - cell.y - cell.height) * pitchY);
            touching.setTranslationX(tx);
            touching.setTranslationY(ty);
            targetX = Math.round(cell.x + tx / pitchX);
            targetY = Math.round(cell.y + ty / pitchY);
        } else {
            int minWidth = cell.secondLabel != null && cell.horizontalPair ? 2 : 1;
            int minHeight = cell.secondLabel != null && !cell.horizontalPair ? 2 : 1;
            int maxHeight = Math.min(ControlCenterLayoutPlan.MAX_HEIGHT,
                    ControlCenterLayoutPlan.MAX_ROWS - cell.y);
            targetWidth = Math.round(clamp(cell.width + dx / pitchX, minWidth, columns - cell.x));
            targetHeight = Math.round(clamp(cell.height + dy / pitchY, minHeight, maxHeight));
            previewWidth = Math.round(clamp(spanWidth(cell.width) + dx, spanWidth(minWidth), spanWidth(columns - cell.x)));
            previewHeight = Math.round(clamp(spanHeight(cell.height) + dy, spanHeight(minHeight), spanHeight(maxHeight)));
            requestLayout();
        }
        validPlacement = canPlace(cell, targetX, targetY, targetWidth, targetHeight);
        if (overlay != null) overlay.placement(targetWidth, targetHeight, validPlacement, true);
        positionOverlay();
        invalidate();
    }

    private boolean canPlace(Cell source, int x, int y, int width, int height) {
        ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
        ControlCenterLayoutPlan.Item candidate = null;
        for (Cell other : cells) {
            // Geometry-only identities avoid depending on optional spec/label data in the View.
            ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("slot:" + items.size(),
                    other.x, other.y, other.width, other.height).withLocked(other.locked);
            items.add(item);
            if (other.id.equals(source.id)) candidate = item.withPosition(x, y).withSize(width, height);
        }
        return candidate != null && ControlCenterGridPlacement.place(
                new ControlCenterLayoutPlan.Mode(columns, items), candidate) != null;
    }

    private EditorItemHost findHost(float x, float y) {
        if (selected != null && hits(selected, x, y)) return selected;
        for (int i = hosts.size() - 1; i >= 0; i--) if (hits(hosts.get(i), x, y)) return hosts.get(i);
        return null;
    }
    private boolean hits(EditorItemHost host, float x, float y) {
        return x >= host.getX() && x < host.getX() + host.getWidth() * host.getScaleX()
                && y >= host.getY() && y < host.getY() + host.getHeight() * host.getScaleY();
    }
    private void disallowParent(boolean disallow) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
    }
    private void resetTouch() {
        removeCallbacks(longPress);
        touching = null;
        phase = Phase.IDLE;
        pointerId = -1;
        disallowParent(false);
        if (overlay != null) overlay.placement(0, 0, true, false);
    }
    private void cancelGesture() {
        if (touching != null) touching.rememberVisualBounds();
        resetTouch();
        requestLayout();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        finishEditing();
        for (EditorItemHost host : hosts) host.stopAnimations();
        super.onDetachedFromWindow();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE && hosts != null) finishEditing();
    }

    private int spanWidth(int span) { return ControlCenterGridGeometry.spanPx(cellWidth, gap, span); }
    private int spanHeight(int span) { return ControlCenterGridGeometry.spanPx(rowHeight, gap, span); }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) width = dp(320);
        cellWidth = ControlCenterGridGeometry.columnUnitPx(width, columns, gap);
        for (EditorItemHost host : hosts) {
            boolean resizing = host == touching && phase == Phase.RESIZE;
            host.measure(MeasureSpec.makeMeasureSpec(resizing ? previewWidth : spanWidth(host.cell.width), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(resizing ? previewHeight : spanHeight(host.cell.height), MeasureSpec.EXACTLY));
        }
        int extraRows = selected == null ? 0 : 1;
        int desiredHeight = ControlCenterGridGeometry.contentHeightPx(
                Math.min(ControlCenterLayoutPlan.MAX_ROWS, rows + extraRows), rowHeight, gap);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
        if (overlay != null) overlay.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cells.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(150, 154, 162));
            paint.setTextSize(dp(13));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("空白画布 · 点击添加资源", Math.max(1, getWidth()) / 2f,
                    Math.max(dp(24), getHeight() / 2f), paint);
        }
        if (moved && touching != null && (phase == Phase.DRAG || phase == Phase.RESIZE)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(validPlacement ? Color.argb(200, 128, 195, 255) : Color.argb(220, 255, 115, 105));
            float x = targetX * (cellWidth + gap), y = targetY * (rowHeight + gap);
            canvas.drawRoundRect(x + dp(1), y + dp(1), x + spanWidth(targetWidth) - dp(1),
                    y + spanHeight(targetHeight) - dp(1), dp(18), dp(18), paint);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (EditorItemHost host : hosts) {
            int x = host.cell.x * (cellWidth + gap), y = host.cell.y * (rowHeight + gap);
            host.layout(x, y, x + host.getMeasuredWidth(), y + host.getMeasuredHeight());
            if (!Float.isNaN(host.fromX)) {
                host.setTranslationX(host.fromX - x);
                host.setTranslationY(host.fromY - y);
                host.setScaleX(host.fromWidth / Math.max(1, host.getWidth()));
                host.setScaleY(host.fromHeight / Math.max(1, host.getHeight()));
                host.fromX = Float.NaN;
                if (Math.abs(host.getTranslationX()) < 0.01f && Math.abs(host.getTranslationY()) < 0.01f
                        && Math.abs(host.getScaleX() - 1f) < 0.001f && Math.abs(host.getScaleY() - 1f) < 0.001f) {
                    host.animate().setUpdateListener(null);
                    continue;
                }
                host.animate().translationX(0).translationY(0).scaleX(1).scaleY(1)
                        .setDuration(180).setInterpolator(settleInterpolator)
                        .setUpdateListener(settleUpdate).start();
            }
        }
        if (overlay != null) overlay.layout(0, 0, getWidth(), getHeight());
        positionOverlay();
    }

    private void positionOverlay() {
        if (overlay != null && selected != null) {
            overlay.anchor(selected.getX(), selected.getY(),
                    selected.getX() + selected.getWidth() * selected.getScaleX(),
                    selected.getY() + selected.getHeight() * selected.getScaleY());
        }
    }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
