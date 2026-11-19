package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Native fields and setters consumed by the notification geometry hooks. */
public class NotificationBackgroundView extends View {
    public int mActualHeight = 64, mActualWidth = -1;
    public int mExpandAnimationWidth = -1, mExpandAnimationHeight = -1;
    public int mClipTopAmount, mClipBottomAmount, mDrawableAlpha = 255;
    public boolean mExpandAnimationRunning, mBottomIsRounded, mBottomAmountClips = true;
    public Drawable mBackground;
    public int geometryCalls;
    public int nativeDrawCalls;
    private int radius = 120, backgroundMode = 1, viewMode = 1;
    private float scaleRatio = 1f;

    public NotificationBackgroundView(Context context) { super(context); }
    @Override public void onDraw(Canvas canvas) {
        nativeDrawCalls++;
        if (mBackground == null) {
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            canvas.drawRect(0, 0, getWidth(), mActualHeight, paint);
        }
    }
    public void setActualHeight(int value) { geometryCalls++; mActualHeight = value; invalidate(); }
    public void setActualWidth(int value) { geometryCalls++; mActualWidth = value; invalidate(); }
    public void setExpandAnimationRunning(boolean value) {
        geometryCalls++; mExpandAnimationRunning = value; invalidate();
    }
    public void setExpandAnimationSize(int width, int height) {
        geometryCalls++; mExpandAnimationWidth = width; mExpandAnimationHeight = height; invalidate();
    }
    public void setClipTopAmount(int value) { geometryCalls++; mClipTopAmount = value; invalidate(); }
    public void setClipBottomAmount(int value) { geometryCalls++; mClipBottomAmount = value; invalidate(); }
    public void setCustomBackground(Drawable value) { mBackground = value; setBackground(value); }
    public void setTint(int value) { if (mBackground != null) mBackground.setTint(value); }
    public void setRadius(float value) { invalidate(); }
    public int getMiViewBlurMode() { return viewMode; }
    public int getMiBackgroundBlurMode() { return backgroundMode; }
    public int getMiBackgroundBlurRadius() { return radius; }
    public float getMiBackgroundBlurScaleRatio() { return scaleRatio; }
    public void setMiViewBlurMode(int value) { viewMode = value; }
    public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
    public void setMiBackgroundBlurRadius(int value) { radius = value; }
    public void setMiBackgroundBlurScaleRatio(float value) { scaleRatio = value; }
}
