package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Module-owned circular NR network-mode tile. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenter5GTileView extends FrameLayout {
    private final ControlCenter5GController controller;
    private final GradientDrawable surface = new GradientDrawable();
    private final TextView label;
    private ControlCenter5GController.State state = new ControlCenter5GController.State(false, false);
    private ControlCenterLayoutPlan.Shape shape = ControlCenterLayoutPlan.Shape.CIRCLE;
    private int cornerRadiusDp;
    private boolean released;

    ControlCenter5GTileView(Context context) {
        this(context, new ControlCenter5GController(context));
    }

    ControlCenter5GTileView(Context context, ControlCenter5GController controller) {
        super(context);
        this.controller = controller;
        surface.setShape(GradientDrawable.OVAL);
        setBackground(surface);
        label = new TextView(context);
        label.setText("5G");
        label.setTextSize(24);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setClickable(true);
        setFocusable(true);
        setOnClickListener(view -> {
            if (released || !state.available) return;
            state = controller.toggle();
            updateAppearance();
        });
        updateAppearance();
    }

    void setShape(ControlCenterLayoutPlan.Shape shape, int cornerRadiusDp) {
        this.shape = shape == null ? ControlCenterLayoutPlan.Shape.CIRCLE : shape;
        this.cornerRadiusDp = Math.max(0, cornerRadiusDp);
        updateShape();
    }

    boolean networkEnabled() { return state.available && state.enabled; }

    private void updateShape() {
        if (shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
            surface.setShape(GradientDrawable.OVAL);
        } else {
            surface.setShape(GradientDrawable.RECTANGLE);
            float radius = shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? Math.min(getWidth(), getHeight()) / 2f
                    : cornerRadiusDp > 0
                    ? cornerRadiusDp * getResources().getDisplayMetrics().density : 0f;
            surface.setCornerRadius(radius);
        }
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateShape();
    }

    void release() {
        released = true;
        setOnClickListener(null);
    }

    private void refreshState() {
        if (!released) {
            state = controller.read();
            updateAppearance();
        }
    }

    private void updateAppearance() {
        boolean enabled = state.available && state.enabled;
        surface.setColor(!state.available ? 0xff43474e
                : enabled ? 0xfff8f9fb : 0xff5e636b);
        label.setTextColor(!state.available ? 0xffaeb3bc
                : enabled ? 0xff3482f6 : Color.WHITE);
        setAlpha(state.available ? 1f : 0.7f);
        setEnabled(state.available && !released);
        setContentDescription(state.available
                ? "5G " + (state.enabled ? "已开启" : "已关闭") : "5G 不可用");
        setStateDescription(state.available ? (state.enabled ? "已开启" : "已关闭") : "不可用");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshState();
    }
}
