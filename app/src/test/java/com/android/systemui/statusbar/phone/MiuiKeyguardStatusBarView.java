package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.widget.FrameLayout;

/** Host fixture retaining the actual SystemUI receiver name for hook filtering. */
public class MiuiKeyguardStatusBarView extends FrameLayout {
    public MiuiKeyguardStatusBarView(Context context) {
        super(context);
    }
}
