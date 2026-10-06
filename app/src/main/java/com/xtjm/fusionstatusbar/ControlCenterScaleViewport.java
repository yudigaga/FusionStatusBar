package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import java.util.function.IntSupplier;

/** Reserves the transformed region's dimensions and keeps enlarged content reachable. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterScaleViewport extends HorizontalScrollView {
    private final View content;
    private final IntSupplier scalePercent;
    private final Region region;
    private int viewportWidth;
    private int previousRange = -1;

    ControlCenterScaleViewport(Context context, View content, IntSupplier scalePercent) {
        super(context);
        this.content = content;
        this.scalePercent = scalePercent;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(false);
        region = new Region(context);
        region.setClipChildren(false);
        region.setClipToPadding(false);
        region.addView(content, new FrameLayout.LayoutParams(-1, -2));
        addView(region, new LayoutParams(-2, -2));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        viewportWidth = Math.max(1, MeasureSpec.getSize(widthSpec));
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int range = Math.max(0, region.getWidth() - getWidth());
        if (range != previousRange) scrollTo(range / 2, 0);
        previousRange = range;
    }

    private final class Region extends FrameLayout {
        Region(Context context) { super(context); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            content.measure(MeasureSpec.makeMeasureSpec(viewportWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            float scale = scalePercent.getAsInt() / 100f;
            content.setPivotX(0f);
            content.setPivotY(0f);
            content.setScaleX(scale);
            content.setScaleY(scale);
            setMeasuredDimension(Math.max(viewportWidth, Math.round(viewportWidth * scale)),
                    Math.max(1, Math.round(content.getMeasuredHeight() * scale)));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int transformedWidth = Math.round(content.getMeasuredWidth() * content.getScaleX());
            int x = Math.max(0, (getWidth() - transformedWidth) / 2);
            content.layout(x, 0, x + content.getMeasuredWidth(), content.getMeasuredHeight());
        }
    }
}
