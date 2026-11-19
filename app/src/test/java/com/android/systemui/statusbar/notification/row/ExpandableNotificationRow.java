package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.widget.FrameLayout;

/** Minimal host fixture whose real names let NotificationSurfaceHooks install normally. */
public class ExpandableNotificationRow extends FrameLayout {
    public final NotificationBackgroundView mBackgroundNormal;
    public boolean mIsHeadsUp, mHeadsUpAnimatingAway, pinned;
    public int geometryCalls;

    public ExpandableNotificationRow(Context context, NotificationBackgroundView background) {
        super(context);
        mBackgroundNormal = background;
        addView(background, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override public void onFinishInflate() { super.onFinishInflate(); }
    public void setActualHeight(int height, boolean notifyListeners) {
        geometryCalls++;
        mBackgroundNormal.mActualHeight = height;
        mBackgroundNormal.invalidate();
    }
    public void setHeadsUp(boolean value) { mIsHeadsUp = value; }
    public void setHeadsUpAnimatingAway(boolean value) { mHeadsUpAnimatingAway = value; }
    public void setPinned(boolean value) { pinned = value; }
    public boolean isHeadsUpState() { return mIsHeadsUp; }
    public boolean isPinned() { return pinned; }
}
