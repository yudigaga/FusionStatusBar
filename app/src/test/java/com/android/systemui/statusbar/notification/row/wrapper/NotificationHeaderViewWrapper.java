package com.android.systemui.statusbar.notification.row.wrapper;

import android.view.View;
import android.widget.TextView;

public final class NotificationHeaderViewWrapper {
    public final View mView;
    public final TextView mAppNameText;
    public final TextView mHeaderText;

    public NotificationHeaderViewWrapper(View view, TextView appNameText, TextView headerText) {
        mView = view;
        mAppNameText = appNameText;
        mHeaderText = headerText;
    }
}
