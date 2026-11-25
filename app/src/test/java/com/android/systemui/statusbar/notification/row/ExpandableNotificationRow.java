package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer;

/** Minimal host fixture whose real names let NotificationSurfaceHooks install normally. */
public class ExpandableNotificationRow extends FrameLayout {
    public final NotificationBackgroundView mBackgroundNormal;
    public boolean mIsHeadsUp, mHeadsUpAnimatingAway, pinned;
    public int geometryCalls;
    public NotificationContentView mPrivateLayout, mPublicLayout;
    public NotificationChildrenContainer mChildrenContainer;
    public ExpandableNotificationRow mNotificationParent;
    public boolean mShowingPublic, mIsSummaryWithChildren, mChildrenExpanded;

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
    public void onNotificationUpdated() { }
    public void setHideSensitive(boolean hide, boolean animate, long delay, long duration) {
        mShowingPublic = hide;
        if (mPrivateLayout != null) mPrivateLayout.setVisibility(hide ? GONE : VISIBLE);
        if (mPublicLayout != null) mPublicLayout.setVisibility(hide ? VISIBLE : GONE);
    }
    public NotificationContentView getShowingLayout() {
        return mShowingPublic ? mPublicLayout : mPrivateLayout;
    }
    public ExpandableNotificationRow getNotificationParent() { return mNotificationParent; }
    public boolean isChildInGroup() { return mNotificationParent != null; }
    public View getContentView() {
        return mIsSummaryWithChildren && !mShowingPublic ? mChildrenContainer : getShowingLayout();
    }
    public void setHeadsUp(boolean value) { mIsHeadsUp = value; }
    public void setHeadsUpAnimatingAway(boolean value) { mHeadsUpAnimatingAway = value; }
    public void setPinned(boolean value) { pinned = value; }
    public boolean isHeadsUpState() { return mIsHeadsUp; }
    public boolean isPinned() { return pinned; }
}
