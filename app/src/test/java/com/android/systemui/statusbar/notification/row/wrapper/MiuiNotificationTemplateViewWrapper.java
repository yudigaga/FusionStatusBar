package com.android.systemui.statusbar.notification.row.wrapper;

import android.view.View;
import android.widget.TextView;

/** The named OEM template boundary used by the notification host. */
public final class MiuiNotificationTemplateViewWrapper {
    public final View mView;
    public final TextView mTitle;
    public final TextView mText;
    public final TextView mTime;
    public final TextView mSubText;

    public MiuiNotificationTemplateViewWrapper(View view, TextView title, TextView text,
                                                TextView time, TextView subText) {
        mView = view;
        mTitle = title;
        mText = text;
        mTime = time;
        mSubText = subText;
    }
}
