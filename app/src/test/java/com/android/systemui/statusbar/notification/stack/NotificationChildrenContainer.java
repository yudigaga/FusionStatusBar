package com.android.systemui.statusbar.notification.stack;

import android.content.Context;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import java.util.ArrayList;
import java.util.List;

public final class NotificationChildrenContainer extends FrameLayout {
    public final NotificationChildrenContainerInjectorImpl mInjector =
            new NotificationChildrenContainerInjectorImpl();
    public boolean mChildrenExpanded;
    private final List<ExpandableNotificationRow> attachedChildren = new ArrayList<>();
    private Object visibleWrapper;

    public NotificationChildrenContainer(Context context) {
        super(context);
    }

    public List<ExpandableNotificationRow> getAttachedChildren() { return attachedChildren; }

    public void addNotification(ExpandableNotificationRow row) {
        attachedChildren.add(row);
        addView(row, new LayoutParams(-1, -1));
    }

    public Object getVisibleWrapper() {
        return visibleWrapper;
    }

    public void setVisibleWrapper(Object wrapper) {
        visibleWrapper = wrapper;
    }
}
