package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Renders the small status-bar frame behind the live icon preview. */
public final class FusionPreviewBackdrop extends View {
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FusionConfig config = FusionConfig.defaults();
    private int systemStatusHeight;

    public FusionPreviewBackdrop(Context context) {
        this(context, null);
    }

    public FusionPreviewBackdrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        background.setColor(Color.rgb(28, 36, 44));
        divider.setColor(Color.argb(70, 220, 230, 240));
        side.setColor(Color.argb(28, 120, 180, 230));
    }

    public void setDisplayConfig(FusionConfig next) {
        config = next == null ? FusionConfig.defaults() : next;
        systemStatusHeight = Math.round(config.statusBarHeight
                * getResources().getDisplayMetrics().density);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        canvas.drawRect(0f, 0f, width, height, background);
        float statusHeight = config.doubleRow
                ? Math.min(height, systemStatusHeight > 0 ? systemStatusHeight : height)
                : height;
        if (config.doubleRow) {
            float dividerY = statusHeight / 2f;
            canvas.drawRect(0f, dividerY - 0.5f, width, dividerY + 0.5f, divider);
            drawElementBand(canvas, width, statusHeight, config.clockSide, config.clockRow, width * 0.25f);
            drawElementBand(canvas, width, statusHeight, config.notificationSide,
                    config.notificationRow, width * 0.3f);
            drawElementBand(canvas, width, statusHeight, config.systemSide, config.systemRow, width * 0.3f);
            drawElementBand(canvas, width, statusHeight, config.fusionSide, config.fusionRow,
                    width * 0.32f);
        }
    }

    private void drawElementBand(Canvas canvas, float width, float height,
            int positionSide, int positionRow, float bandWidth) {
        float y = positionRow == 0 ? 0f : height / 2f;
        float x = positionSide == FusionConfig.SIDE_LEFT ? 0f : width - bandWidth;
        canvas.drawRect(x, y, x + bandWidth, y + height / 2f, side);
    }

}
