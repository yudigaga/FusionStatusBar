package com.android.systemui.statusbar.views;

import android.content.Context;
import android.widget.FrameLayout;

/** The native status icon rail boundary, with manually positioned test icons. */
public final class MiuiStatusIconContainer extends FrameLayout {
    public int islandX = -1;

    public MiuiStatusIconContainer(Context context) {
        super(context);
    }

    public int getIslandTranslationX() {
        return islandX;
    }
}
