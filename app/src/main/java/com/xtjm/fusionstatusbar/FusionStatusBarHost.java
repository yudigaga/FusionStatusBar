package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

/** Holds a fusion icon in one row or across both rows of the custom status area. */
public final class FusionStatusBarHost extends FrameLayout {
    private final FusionIconView icon;
    private FusionConfig config = FusionConfig.defaults();

    public FusionStatusBarHost(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        icon = new FusionIconView(context);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(icon, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    public void setDisplayConfig(FusionConfig next) {
        config = next == null ? FusionConfig.defaults() : next;
        icon.setDisplayConfig(config);
        requestLayout();
    }

    public void setIconTint(int color) {
        icon.setIconTint(Color.alpha(color) == 0 ? Color.WHITE : color);
    }

    public FusionIconView getIcon() {
        return icon;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        if (totalHeight <= 0) {
            totalHeight = icon.getResources().getDisplayMetrics().heightPixels > 0
                    ? Math.round(40f * icon.getResources().getDisplayMetrics().density) : 40;
        }
        boolean exactWidth = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY;
        int availableWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int iconHeight = config.doubleRow && config.spanRows
                ? totalHeight : Math.max(1, totalHeight / 2);
        int iconHeightSpec = View.MeasureSpec.makeMeasureSpec(iconHeight, View.MeasureSpec.EXACTLY);
        int iconWidthSpec = exactWidth
                ? View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST)
                : widthMeasureSpec;
        icon.measure(iconWidthSpec, iconHeightSpec);
        int width = icon.getMeasuredWidth();
        if (exactWidth) {
            width = Math.min(width, availableWidth);
        }
        setMeasuredDimension(Math.max(1, width), Math.max(1, totalHeight));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int iconWidth = icon.getMeasuredWidth();
        int iconHeight = icon.getMeasuredHeight();
        int iconLeft = Math.max(0, (width - iconWidth) / 2);
        int iconTop;
        if (config.doubleRow && config.spanRows) {
            iconTop = Math.max(0, (height - iconHeight) / 2);
        } else {
            iconTop = 0;
        }
        icon.layout(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
    }
}
