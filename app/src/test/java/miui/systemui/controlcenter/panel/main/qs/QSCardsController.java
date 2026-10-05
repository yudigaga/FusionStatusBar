package miui.systemui.controlcenter.panel.main.qs;

import java.util.ArrayList;
import java.util.List;

/** Native card inventory, independent of the adapter's custom content map. */
public final class QSCardsController {
    public final List<QSListController.QSRecord> cards = new ArrayList<>();
    public int createdHolders;
    public List<QSListController.QSRecord> getListItems() { return cards; }
    public boolean getRightOrLeft() { return true; }
    public Object createViewHolder(android.view.ViewGroup parent, int type) {
        createdHolders++;
        return new QSListController.Holder(parent.getContext());
    }
}
