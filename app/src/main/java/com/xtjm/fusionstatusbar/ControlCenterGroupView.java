package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.function.Function;

/** One surface, one normalized layout algorithm, different preview/native member factories. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterGroupView extends ViewGroup {
    private final ControlCenterLayoutPlan.Item item;
    private final GradientDrawable surface = new GradientDrawable();
    private final Path clip = new Path();
    private final int defaultRadius;

    ControlCenterLayoutPlan.Item item() { return item; }

    void visitMemberViews(java.util.function.BiConsumer<String, View> visitor) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view instanceof ControlCenterGroupEntry entry) visitor.accept(item.group.members.get(i).spec, entry.iconView());
        }
    }

    ControlCenterGroupView(Context context, ControlCenterLayoutPlan.Item item,
            Function<ControlCenterGroupData.Member, View> factory) {
        this(context, item, 24, factory);
    }

    ControlCenterGroupView(Context context, ControlCenterLayoutPlan.Item item, int defaultRadius,
            Function<ControlCenterGroupData.Member, View> factory) {
        super(context);
        this.item = item;
        this.defaultRadius = defaultRadius;
        surface.setColor(item.group.surface.color);
        if (item.group.border) surface.setStroke(Math.max(1, dp(0.7f)), 0x70ffffff);
        setBackground(surface);
        setClipChildren(true);
        for (ControlCenterGroupData.Member member : item.group.members) addView(factory.apply(member));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            android.graphics.Rect r = bounds(i, width, height);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(r.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(r.height(), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            android.graphics.Rect r = bounds(i, getWidth(), getHeight());
            getChildAt(i).layout(r.left, r.top, r.right, r.bottom);
        }
        float radius = item.shape == ControlCenterLayoutPlan.Shape.CIRCLE || item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                ? Math.min(getWidth(), getHeight()) / 2f : Math.min(dp(item.cornerRadius > 0 ? item.cornerRadius : defaultRadius),
                Math.min(getWidth(), getHeight()) / 2f);
        surface.setShape(item.shape == ControlCenterLayoutPlan.Shape.CIRCLE ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        surface.setCornerRadius(radius);
        clip.reset();
        if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) clip.addOval(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        else clip.addRoundRect(0, 0, getWidth(), getHeight(), radius, radius, Path.Direction.CW);
    }

    private android.graphics.Rect bounds(int index, int width, int height) {
        ControlCenterGroupData.Member member = item.group.members.get(index);
        int padding = Math.min(dp(10), Math.min(width, height) / 8);
        int w = Math.max(1, width - padding * 2), h = Math.max(1, height - padding * 2);
        int left = padding + member.x * w / 12, top = padding + member.y * h / 12;
        int right = padding + (member.x + member.width) * w / 12, bottom = padding + (member.y + member.height) * h / 12;
        int inset = Math.min(dp(3), Math.min(right - left, bottom - top) / 6);
        return new android.graphics.Rect(left + inset, top + inset, Math.max(left + inset + 1, right - inset),
                Math.max(top + inset + 1, bottom - inset));
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
