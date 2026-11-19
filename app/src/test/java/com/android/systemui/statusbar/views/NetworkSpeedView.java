package com.android.systemui.statusbar.views;

import android.content.Context;
import android.view.View;

/** Narrow fixture retaining the controller no-op from the local 16.03.251211.r host sample. */
public class NetworkSpeedView extends View {
    private boolean visibleByController;

    public NetworkSpeedView(Context context) {
        super(context);
    }

    public void setVisibilityByController(boolean visible) {
        if (visibleByController == visible) return;
        visibleByController = visible;
        setVisibility(visible ? VISIBLE : GONE);
    }
}
