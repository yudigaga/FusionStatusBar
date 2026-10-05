package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Additional power-menu actions beside the original shutdown/restart slider. */
final class PowerMenuSideSlider extends FrameLayout {
    static final int ACTION_TOP = android.R.id.accessibilityActionScrollUp;
    static final int ACTION_BOTTOM = android.R.id.accessibilityActionScrollDown;
    private static final float COMMIT_FRACTION = .78f;
    private static final int ACCENT = 0xff3482ff;

    private final View track;
    private final ImageView topIcon, bottomIcon, thumb;
    private final TextView topLabel, bottomLabel;
    private final IntConsumer action;
    private final int touchSlop;
    private boolean topEnabled = true, bottomEnabled = true;
    private boolean captured, moved, consuming;
    private int pointerId = -1, armedDirection;
    private float startY, travel, dragOffset;
    private int thumbSize, iconSize;
    private float contentScale = 1f;

    PowerMenuSideSlider(Context context, String topLabel, int topDrawableResource,
            String bottomLabel, int bottomDrawableResource, IntConsumer action) {
        super(context);
        this.action = Objects.requireNonNull(action);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(topLabel + "、" + bottomLabel);

        track = new View(context);
        GradientDrawable nativeTrack = new GradientDrawable();
        nativeTrack.setColor(0x509ea6b1);
        nativeTrack.setCornerRadius(dp(100));
        track.setBackground(nativeTrack);
        addView(track);
        topIcon = icon(context, topDrawableResource, Color.WHITE);
        bottomIcon = icon(context, bottomDrawableResource, Color.WHITE);
        this.topLabel = label(context, topLabel);
        this.bottomLabel = label(context, bottomLabel);
        thumb = icon(context, R.drawable.cc_icon_swap_vert, ACCENT);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        thumb.setBackground(circle);
        applyAppearance(ControlCenterConfig.defaults());
    }

    void applyAppearance(ControlCenterConfig config) {
        contentScale = config.tileScale / 100f;
        SystemSurfaceStyle.applyTrack(track, config, true);
        int foreground = config.surface == ControlCenterSurface.DEFAULT
                ? Color.WHITE : config.surface.neutralForeground();
        topIcon.setImageTintList(android.content.res.ColorStateList.valueOf(foreground));
        bottomIcon.setImageTintList(android.content.res.ColorStateList.valueOf(foreground));
        for (TextView label : new TextView[] {topLabel, bottomLabel}) {
            label.setTextColor(foreground);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * contentScale);
        }
        cancelGesture();
        requestLayout();
    }

    void setActionEnabled(boolean top, boolean bottom) {
        if (topEnabled == top && bottomEnabled == bottom) return;
        topEnabled = top;
        bottomEnabled = bottom;
        topIcon.setAlpha(top ? 1f : .35f);
        topLabel.setAlpha(top ? 1f : .35f);
        bottomIcon.setAlpha(bottom ? 1f : .35f);
        bottomLabel.setAlpha(bottom ? 1f : .35f);
        thumb.setAlpha(top || bottom ? 1f : .35f);
        cancelGesture();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(dp(64), widthMeasureSpec);
        int height = resolveSize(dp(360), heightMeasureSpec);
        int trackHeight = Math.max(0, height - dp(72));
        setMeasuredDimension(width, height);
        thumbSize = Math.max(0, Math.min(width - dp(4), trackHeight / 3));
        iconSize = Math.max(0, Math.min(Math.round(dp(28) * contentScale), thumbSize / 2));
        measureChildExactly(track, width, trackHeight);
        measureChildExactly(thumb, thumbSize, thumbSize);
        measureChildExactly(topIcon, iconSize, iconSize);
        measureChildExactly(bottomIcon, iconSize, iconSize);
        int labelWidth = Math.max(width, Math.min(dp(96), width + dp(16)));
        measureLabel(topLabel, labelWidth);
        measureLabel(bottomLabel, labelWidth);
        int inset = Math.max(0, (thumbSize - iconSize) / 2);
        thumb.setPadding(inset, inset, inset, inset);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        int labelSpace = dp(36);
        int trackHeight = Math.max(0, height - 2 * labelSpace);
        track.layout(0, labelSpace, width, labelSpace + trackHeight);
        int thumbLeft = (width - thumbSize) / 2;
        int thumbTop = (height - thumbSize) / 2;
        thumb.layout(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize);
        int iconLeft = (width - iconSize) / 2;
        int capCenter = labelSpace + Math.max(thumbSize / 2 + dp(4), trackHeight / 8);
        topIcon.layout(iconLeft, capCenter - iconSize / 2,
                iconLeft + iconSize, capCenter - iconSize / 2 + iconSize);
        bottomIcon.layout(iconLeft, height - capCenter - iconSize / 2,
                iconLeft + iconSize, height - capCenter - iconSize / 2 + iconSize);
        int labelLeft = (width - topLabel.getMeasuredWidth()) / 2;
        topLabel.layout(labelLeft, 0, labelLeft + topLabel.getMeasuredWidth(), dp(28));
        bottomLabel.layout(labelLeft, height - dp(28),
                labelLeft + bottomLabel.getMeasuredWidth(), height);
        float nextTravel = Math.max(0, (trackHeight - thumbSize) / 2f - dp(3));
        if (travel != nextTravel) cancelGesture();
        travel = nextTravel;
        thumb.setTranslationY(dragOffset);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int eventAction = event.getActionMasked();
        if (eventAction == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            consuming = true;
            captured = isEnabled() && (topEnabled || bottomEnabled)
                    && event.getPointerCount() == 1 && onThumb(event.getX(), event.getY());
            if (!captured) return true;
            pointerId = event.getPointerId(0);
            startY = event.getY();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (!captured) {
            boolean handled = consuming;
            if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) consuming = false;
            return handled;
        }
        if (eventAction == MotionEvent.ACTION_CANCEL || eventAction == MotionEvent.ACTION_POINTER_DOWN
                || eventAction == MotionEvent.ACTION_POINTER_UP || event.getPointerCount() != 1) {
            cancelGesture();
            return true;
        }
        int index = event.findPointerIndex(pointerId);
        if (index < 0 || !isEnabled()) {
            cancelGesture();
            return true;
        }
        if (eventAction == MotionEvent.ACTION_MOVE) {
            updateDrag(event.getX(index), event.getY(index));
        } else if (eventAction == MotionEvent.ACTION_UP) {
            if (moved) updateDrag(event.getX(index), event.getY(index));
            int direction = moved ? armedDirection : 0;
            cancelGesture();
            if (direction != 0 && directionEnabled(direction)) dispatchAction(direction);
            else performClick();
        }
        return true;
    }

    private boolean onThumb(float x, float y) {
        float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
        return dx * dx + dy * dy <= thumbSize * thumbSize / 4f;
    }

    private void updateDrag(float x, float y) {
        float delta = y - startY;
        moved |= Math.abs(delta) > touchSlop;
        dragOffset = Math.max(topEnabled ? -travel : 0, Math.min(bottomEnabled ? travel : 0, delta));
        thumb.setTranslationY(dragOffset);
        float threshold = Math.max(travel * COMMIT_FRACTION, touchSlop * 2f);
        int direction = moved && travel > 0 && Math.abs(dragOffset) >= threshold
                && x >= -touchSlop && x <= getWidth() + touchSlop ? (dragOffset < 0 ? -1 : 1) : 0;
        if (direction != 0 && direction != armedDirection)
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        armedDirection = direction;
        thumb.setActivated(direction != 0);
    }

    private boolean directionEnabled(int direction) {
        return isEnabled() && (direction < 0 ? topEnabled : bottomEnabled);
    }

    private void dispatchAction(int direction) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        action.accept(direction);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void cancelGesture() {
        captured = false;
        moved = false;
        pointerId = -1;
        armedDirection = 0;
        dragOffset = 0;
        if (thumb != null) {
            thumb.setTranslationY(0);
            thumb.setActivated(false);
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override protected void onDetachedFromWindow() {
        cancelGesture();
        consuming = false;
        super.onDetachedFromWindow();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(PowerMenuSideSlider.class.getName());
        if (directionEnabled(-1)) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_TOP, topLabel.getText()));
        if (directionEnabled(1)) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_BOTTOM, bottomLabel.getText()));
    }

    @Override public boolean performAccessibilityAction(int actionId, Bundle arguments) {
        int direction = actionId == ACTION_TOP ? -1 : actionId == ACTION_BOTTOM ? 1 : 0;
        if (direction == 0) return super.performAccessibilityAction(actionId, arguments);
        if (!directionEnabled(direction)) return false;
        cancelGesture();
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        dispatchAction(direction);
        return true;
    }

    private ImageView icon(Context context, int resource, int color) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(loadIcon(context, resource, color));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(view);
        return view;
    }

    private TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setIncludeFontPadding(false);
        view.setLetterSpacing(0);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(view);
        return view;
    }

    private static Drawable loadIcon(Context context, int resource, int color) {
        Drawable drawable;
        try {
            Context module = BuildConfig.APPLICATION_ID.equals(context.getPackageName()) ? context
                    : context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            drawable = module.getDrawable(resource);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            int fallback = resource == R.drawable.cc_icon_usb ? android.R.drawable.stat_sys_download
                    : resource == R.drawable.cc_icon_settings_backup_restore ? android.R.drawable.ic_popup_sync
                    : resource == R.drawable.cc_icon_airplanemode_active ? android.R.drawable.ic_menu_compass
                    : resource == R.drawable.cc_icon_notifications_off ? android.R.drawable.ic_lock_silent_mode
                    : android.R.drawable.arrow_up_float;
            drawable = context.getDrawable(fallback);
        }
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
        }
        return drawable;
    }

    private static void measureChildExactly(View child, int width, int height) {
        child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height), MeasureSpec.EXACTLY));
    }

    private void measureLabel(TextView label, int width) {
        float desired = 14 * contentScale * getResources().getDisplayMetrics().scaledDensity;
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, desired);
        float measured = label.getPaint().measureText(label.getText().toString());
        if (measured > width - dp(4) && measured > 0)
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, desired * Math.max(0, width - dp(4)) / measured);
        measureChildExactly(label, width, dp(28));
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
