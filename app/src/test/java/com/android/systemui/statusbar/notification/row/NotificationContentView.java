package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** OEM host fixture that exposes whichever template is actually on screen. */
public final class NotificationContentView extends FrameLayout {
    public ExpandableNotificationRow mContainingNotification;
    public int mVisibleType;
    private Object contractedWrapper, expandedWrapper;
    private View contractedView, expandedView;
    private boolean expanded;

    public NotificationContentView(Context context) {
        super(context);
    }

    public Object getVisibleWrapper() {
        return mVisibleType == 1 ? expandedWrapper : contractedWrapper;
    }

    public void setContractedWrapper(Object wrapper, View view) {
        contractedWrapper = wrapper;
        contractedView = view;
    }

    public void setExpandedWrapper(Object wrapper, View view) {
        expandedWrapper = wrapper;
        expandedView = view;
        view.setVisibility(INVISIBLE);
    }

    public void setExpanded(boolean value) { expanded = value; }

    public void selectLayout(boolean force, boolean animate) {
        mVisibleType = expanded && expandedWrapper != null ? 1 : 0;
        if (contractedView != null) contractedView.setVisibility(mVisibleType == 0 ? VISIBLE : INVISIBLE);
        if (expandedView != null) expandedView.setVisibility(mVisibleType == 1 ? VISIBLE : INVISIBLE);
    }
}
