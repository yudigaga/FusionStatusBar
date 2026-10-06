package miui.systemui.controlcenter.panel.main.qs;

import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem;

/** Matches the OEM class name checked by ControlCenterRuntimeGrid.apply, not production code. */
public final class QSListController {
    public final Factory recordFactory = new Factory();
    public final List<QSRecord> addedTiles = new ArrayList<>();
    public Object qsController;
    public Object qsCardsController;
    public boolean bindViews;
    public android.content.Context getContext() { return org.robolectric.RuntimeEnvironment.getApplication(); }
    public List<QSRecord> getAddedTiles() { return addedTiles; }
    public Object createViewHolder(ViewGroup parent, int type) {
        if (!bindViews) throw new UnsupportedOperationException("Binding not exercised here");
        return new Holder(parent.getContext());
    }
    public void onBindViewHolder(Object holder, Object record) { }

    public static final class Factory {
        public QSRecord create(Object tile, boolean card) { return new QSRecord(String.valueOf(tile)); }
    }
    public static final class QSRecord implements MainPanelListItem {
        private final String spec;
        private Object holder;
        public boolean added;
        public boolean listening;
        public int removedCallbacks;
        public QSRecord(String spec) { this.spec = spec; }
        public String getSpec() { return spec; }
        public Object getTile() { return spec; }
        public void setAdded(boolean value) { added = value; }
        public int getType() { return 8453; }
        public void setListening(boolean value) { listening = value; }
        public void removeCallback() { removedCallbacks++; }
        public Object getHolder() { return holder; }
        public void setHolder(Object holder) { this.holder = holder; }
        public void onBindViewHolder() { }
        public void onUnbindViewHolder() { }
        public void onViewAttachedToWindow() { }
        public void onViewDetachedFromWindow() { }
    }

    public static final class Holder {
        public final android.widget.FrameLayout itemView;
        public Object owner, item;
        public boolean recycled;
        public Holder(android.content.Context context) {
            itemView = new android.widget.FrameLayout(context);
            itemView.setBackgroundColor(android.graphics.Color.WHITE);
        }
        public void init() { }
        public void setOwner(Object owner) { this.owner = owner; }
        public void setItem$miui_controlcenter_release(Object item) { this.item = item; }
        public boolean getAttached$miui_controlcenter_release() { return false; }
        public void setAttached$miui_controlcenter_release(boolean attached) { }
        public void onViewAttachedToWindow(Object adapter, Object callback) { }
        public void onViewDetachedFromWindow() { }
        public void recycle() { recycled = true; }
    }
}
