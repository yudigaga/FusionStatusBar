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
    public android.content.Context getContext() { return org.robolectric.RuntimeEnvironment.getApplication(); }
    public List<QSRecord> getAddedTiles() { return addedTiles; }
    public Object createViewHolder(ViewGroup parent, int type) { throw new UnsupportedOperationException("Binding not exercised here"); }
    public void onBindViewHolder(Object holder, Object record) { }

    public static final class Factory {
        public QSRecord create(Object tile, boolean card) { return new QSRecord(String.valueOf(tile)); }
    }
    public static final class QSRecord implements MainPanelListItem {
        private final String spec;
        public QSRecord(String spec) { this.spec = spec; }
        public String getSpec() { return spec; }
        public Object getTile() { return spec; }
        public void setAdded(boolean value) { }
        public int getType() { return 8453; }
        public void setListening(boolean value) { }
        public void removeCallback() { }
    }
}
