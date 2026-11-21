package com.android.systemui.statusbar.views;

import android.content.Context;
import android.widget.LinearLayout;

/** Native container identity required by the real two-column installation path. */
public class MiuiStatusBatteryContainer extends LinearLayout {
    public enum PrivacyState { COMPLETE_HIDE_PRIVACY, COMPLETE_SHOW_PRIVACY }

    public PrivacyState mPrivacyState = PrivacyState.COMPLETE_HIDE_PRIVACY;

    public MiuiStatusBatteryContainer(Context context) {
        super(context);
    }
}
