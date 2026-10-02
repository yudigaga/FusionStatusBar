package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Editor chrome lives outside the renderer's clipping and hidden-state alpha. */
// Programmatically constructed with mandatory editor actions; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterEditOverlay extends ViewGroup {
    interface Actions {
        void run(ControlCenterGridEditor.EditAction action);
        void properties();
        void done();
    }
    private final List<TextView> primary = new ArrayList<>();
    private final TextView remove, lock, visibility, direction, split, status;
    private final View resize;
    private final RectF anchor = new RectF();
    private boolean paired;

    ControlCenterEditOverlay(Context context, Actions actions) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setElevation(dp(32));
        remove = button("−", "移除项目", () -> actions.run(ControlCenterGridEditor.EditAction.REMOVE));
        remove.setTextSize(24);
        lock = button("锁定", "锁定项目", () -> actions.run(ControlCenterGridEditor.EditAction.TOGGLE_LOCK));
        visibility = button("隐藏", "隐藏项目", () -> actions.run(ControlCenterGridEditor.EditAction.TOGGLE_HIDDEN));
        primary.add(remove);
        primary.add(lock);
        primary.add(visibility);
        primary.add(button("属性", "调整项目属性", actions::properties));
        primary.add(button("完成", "完成画布编辑", actions::done));
        direction = button("换排列", "切换组合排列", () -> actions.run(ControlCenterGridEditor.EditAction.TOGGLE_DIRECTION));
        split = button("拆分", "拆分组合卡片", () -> actions.run(ControlCenterGridEditor.EditAction.UNPAIR));
        status = new TextView(context);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        status.setBackground(material());
        status.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(status);
        resize = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.ROUND);
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                canvas.drawLine(cx - dp(7), cy + dp(5), cx + dp(5), cy - dp(7), paint);
                canvas.drawLine(cx, cy + dp(7), cx + dp(7), cy, paint);
            }
        };
        resize.setBackground(material());
        resize.setContentDescription("拖动调整尺寸");
        // Keyboard/accessibility users retain the numeric size controls.
        resize.setOnClickListener(view -> actions.properties());
        addView(resize);
    }

    void bind(ControlCenterGridEditor.Cell cell) {
        paired = cell.secondLabel != null || cell.groupItem != null;
        lock.setText(cell.locked ? "解锁" : "锁定");
        lock.setContentDescription(cell.locked ? "解锁项目" : "锁定项目");
        visibility.setText(cell.hidden ? "显示" : "隐藏");
        visibility.setContentDescription(cell.hidden ? "显示项目" : "隐藏项目");
        for (View view : new View[] {remove, visibility, direction, split, resize}) {
            view.setEnabled(!cell.locked);
            view.setAlpha(cell.locked ? 0.4f : 1f);
        }
        direction.setText(cell.horizontalPair ? "上下排" : "左右排");
        direction.setVisibility(cell.secondLabel != null ? VISIBLE : GONE);
        split.setVisibility(paired ? VISIBLE : GONE);
        resize.setVisibility(cell.locked ? GONE : VISIBLE);
        status.setVisibility(GONE);
        requestLayout();
    }

    void placement(int width, int height, boolean valid, boolean active) {
        status.setVisibility(active ? VISIBLE : GONE);
        status.setText(valid ? width + " × " + height : "位置不可用");
        status.setTextColor(valid ? Color.WHITE : Color.rgb(255, 143, 133));
    }

    void anchor(float left, float top, float right, float bottom) {
        anchor.set(left, top, right, bottom);
        positionChildren();
    }

    boolean hitsControl(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View view = getChildAt(i);
            if (view != status && hit(view, x, y)) return true;
        }
        return false;
    }

    boolean hitsResize(float x, float y) { return resize.isEnabled() && hit(resize, x, y); }

    private boolean hit(View view, float x, float y) {
        return view.getVisibility() == VISIBLE && x >= view.getLeft() && x < view.getRight()
                && y >= view.getTop() && y < view.getBottom();
    }

    private TextView button(String text, String description, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setBackground(material());
        view.setFocusable(true);
        view.setOnClickListener(v -> click.run());
        addView(view);
        return view;
    }

    private GradientDrawable material() {
        GradientDrawable result = new GradientDrawable();
        result.setColor(Color.argb(242, 43, 49, 59));
        result.setCornerRadius(dp(20));
        return result;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        int size = dp(48);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(child == status ? dp(96) : size, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) { positionChildren(); }

    private void positionChildren() {
        if (getWidth() == 0) return;
        int size = dp(48), gap = dp(3), pitch = size + gap;
        int columns = Math.max(1, Math.min(primary.size(), (getWidth() + gap) / pitch));
        int primaryRows = (primary.size() + columns - 1) / columns;
        int rows = primaryRows + (paired ? 1 : 0);
        int toolbarWidth = Math.min(primary.size(), columns) * pitch - gap;
        int left = clamp(Math.round(anchor.left), 0, Math.max(0, getWidth() - toolbarWidth));
        int toolbarHeight = rows * pitch - gap;
        int above = Math.round(anchor.top) - toolbarHeight - dp(4);
        // Top-row cards keep their content visible: dock below instead of over the surface.
        int top = above >= 0 ? above : Math.round(anchor.bottom) + dp(4);
        top = clamp(top, 0, Math.max(0, getHeight() - toolbarHeight));
        for (int i = 0; i < primary.size(); i++) {
            place(primary.get(i), left + (i % columns) * pitch, top + (i / columns) * pitch, size, size);
        }
        place(direction, left, top + primaryRows * pitch, size, size);
        place(split, left + pitch, top + primaryRows * pitch, size, size);
        int gripX = clamp(Math.round(anchor.right) - size, 0, Math.max(0, getWidth() - size));
        int gripY = clamp(Math.round(anchor.bottom) - size, 0, Math.max(0, getHeight() - size));
        // Prefer the free area below the card if its short height would collide with the dock.
        if (gripY < top + toolbarHeight && gripY + size > top
                && gripX < left + toolbarWidth && gripX + size > left) {
            gripY = clamp(Math.round(anchor.bottom) + dp(4), 0, Math.max(0, getHeight() - size));
        }
        place(resize, gripX, gripY, size, size);
        place(status, clamp(gripX - dp(100), 0, Math.max(0, getWidth() - dp(96))),
                gripY, dp(96), size);
    }

    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + width, y + height); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
