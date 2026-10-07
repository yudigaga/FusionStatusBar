package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w320dp-h640dp-mdpi")
public class DesktopAppListAdapterTest {
    private ActivityController<Activity> owner;
    private Activity activity;
    private ListView parent;
    private List<DesktopAppCatalog.App> apps;

    @Before public void launch() {
        owner = Robolectric.buildActivity(Activity.class);
        owner.get().setTheme(R.style.AppTheme);
        owner.setup().visible();
        activity = owner.get();
        parent = new ListView(activity);
        apps = List.of(new DesktopAppCatalog.App("com.example.Alpha", "Alpha Tool", false, false),
                new DesktopAppCatalog.App("com.example.beta", "Beta Utility", true, false),
                new DesktopAppCatalog.App("com.Android.Settings", "\u7cfb\u7edf\u8bbe\u7f6e", false, false));
    }

    @After public void close() { owner.pause().stop().destroy(); }

    @Test public void searchMatchesNamesAndPackagesIgnoringCaseAndOutsideWhitespace() {
        DesktopAppListAdapter adapter = adapter(null, -1);
        adapter.setQuery("  aLPHa tOOL  ");
        assertEquals(1, adapter.getCount());
        assertEquals(0, adapter.sourceIndex(0));
        assertEquals(apps.get(0).displayLabel(activity), adapter.getItem(0));
        adapter.setQuery("COM.ANDROID.SET");
        assertEquals(1, adapter.getCount());
        assertEquals(2, adapter.sourceIndex(0));
        adapter.setQuery("\u8bbe\u7f6e");
        assertEquals(1, adapter.getCount());
        assertEquals(2, adapter.sourceIndex(0));
    }

    @Test public void filterKeepsStableSourceIdsAndClearingRestoresOriginalOrder() {
        DesktopAppListAdapter adapter = adapter(null, -1);
        assertTrue(adapter.hasStableIds());
        adapter.setQuery("example");
        assertEquals(2, adapter.getCount());
        assertEquals(0L, adapter.getItemId(0));
        assertEquals(1L, adapter.getItemId(1));
        adapter.setQuery("no-such-app");
        assertEquals(0, adapter.getCount());
        adapter.setQuery("   ");
        assertEquals(3, adapter.getCount());
        for (int i = 0; i < apps.size(); i++) {
            assertEquals(i, adapter.sourceIndex(i));
            assertEquals(i, adapter.getItemId(i));
        }
        adapter.setQuery(null);
        assertEquals(3, adapter.getCount());
    }

    @Test public void searchCaseFoldingDoesNotDependOnTheDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DesktopAppListAdapter adapter = adapter(null, -1);
            adapter.setQuery("UTILITY");
            assertEquals(1, adapter.getCount());
            assertEquals(1, adapter.sourceIndex(0));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void checkedSelectionSurvivesFiltersAndUpdatesTheSharedDraft() {
        boolean[] checked = {true, false, false};
        DesktopAppListAdapter adapter = adapter(checked, -1);
        adapter.setQuery("beta");
        adapter.toggle(0);
        assertArrayEquals(new boolean[] {true, true, false}, checked);
        adapter.setQuery("no-such-app");
        adapter.setQuery("");
        assertTrue(choice(adapter.getView(0, null, parent)).isChecked());
        assertTrue(choice(adapter.getView(1, null, parent)).isChecked());
        assertFalse(choice(adapter.getView(2, null, parent)).isChecked());
        adapter.setQuery("alpha");
        adapter.toggle(0);
        assertArrayEquals(new boolean[] {false, true, false}, checked);
    }

    @Test public void radioSelectionUsesTheSourceIndexAfterFilteringAndRecycling() {
        DesktopAppListAdapter adapter = adapter(null, 2);
        adapter.setQuery("settings");
        View selected = adapter.getView(0, null, parent);
        assertTrue(choice(selected) instanceof RadioButton);
        assertTrue(choice(selected).isChecked());
        adapter.setQuery("beta");
        adapter.toggle(0);
        assertSame(selected, adapter.getView(0, selected, parent));
        assertTrue(choice(selected).isChecked());
        adapter.setQuery("");
        assertFalse(choice(adapter.getView(2, selected, parent)).isChecked());
        assertTrue(choice(adapter.getView(1, selected, parent)).isChecked());
    }

    @Test public void recycledRowsRebindColoredIconsTextAndStatusWithDefaultFallback() {
        Shadows.shadowOf(activity.getPackageManager()).setApplicationIcon(apps.get(0).packageName,
                new ColorDrawable(Color.RED));
        Shadows.shadowOf(activity.getPackageManager()).setApplicationIcon(apps.get(1).packageName,
                new ColorDrawable(Color.GREEN));
        DesktopAppListAdapter adapter = adapter(new boolean[] {false, true, false}, -1);
        View row = adapter.getView(0, null, parent);
        ImageView icon = row.findViewById(android.R.id.icon);
        assertEquals(Color.RED, ((ColorDrawable) icon.getDrawable()).getColor());
        assertNull("Application icons retain their own colors", icon.getImageTintList());
        assertEquals(36, icon.getLayoutParams().width);
        assertEquals(36, icon.getLayoutParams().height);
        assertSame(row, adapter.getView(1, row, parent));
        assertEquals(Color.GREEN, ((ColorDrawable) icon.getDrawable()).getColor());
        TextView title = row.findViewById(android.R.id.text1);
        TextView subtitle = row.findViewById(android.R.id.text2);
        assertEquals("Beta Utility", title.getText().toString());
        assertTrue(subtitle.getText().toString().contains("com.example.beta"));
        assertTrue(subtitle.getText().toString().contains(activity.getString(R.string.desktop_app_disabled)));
        assertTrue(choice(row).isChecked());
        assertSame(row, adapter.getView(2, row, parent));
        Drawable fallback = activity.getPackageManager().getDefaultActivityIcon();
        assertNotNull(icon.getDrawable());
        assertEquals(fallback.getClass(), icon.getDrawable().getClass());
        assertEquals("com.Android.Settings", subtitle.getText().toString());
        assertFalse(choice(row).isChecked());
        assertEquals(apps.get(2).displayLabel(activity), row.getContentDescription().toString());
    }

    @Test public void iconsLoadLazilyAndAreCachedWithinThePicker() {
        var manager = Shadows.shadowOf(activity.getPackageManager());
        manager.setApplicationIcon(apps.get(0).packageName, new ColorDrawable(Color.RED));
        DesktopAppListAdapter adapter = adapter(null, -1);
        manager.setApplicationIcon(apps.get(0).packageName, new ColorDrawable(Color.GREEN));
        View row = adapter.getView(0, null, parent);
        ImageView icon = row.findViewById(android.R.id.icon);
        assertEquals(Color.GREEN, ((ColorDrawable) icon.getDrawable()).getColor());
        manager.setApplicationIcon(apps.get(0).packageName, new ColorDrawable(Color.BLUE));
        adapter.getView(0, row, parent);
        assertEquals(Color.GREEN, ((ColorDrawable) icon.getDrawable()).getColor());
    }

    @Test public void iconCacheEvictsOldEntriesWhenBrowsingLargeCatalogs() {
        var manager = Shadows.shadowOf(activity.getPackageManager());
        List<DesktopAppCatalog.App> manyApps = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            String packageName = "test.cache.app" + i;
            manyApps.add(new DesktopAppCatalog.App(packageName, "App " + i, false, false));
            manager.setApplicationIcon(packageName, new ColorDrawable(Color.RED));
        }
        DesktopAppListAdapter adapter = new DesktopAppListAdapter(activity, manyApps, null, -1,
                Color.WHITE, Color.BLACK, Color.GRAY, Color.BLUE);
        View row = null;
        for (int i = 0; i < manyApps.size(); i++) row = adapter.getView(i, row, parent);
        manager.setApplicationIcon(manyApps.get(0).packageName, new ColorDrawable(Color.GREEN));
        adapter.getView(0, row, parent);
        ImageView icon = row.findViewById(android.R.id.icon);
        assertEquals(Color.GREEN, ((ColorDrawable) icon.getDrawable()).getColor());
    }

    @Test public void rowsKeepTextThemeAndExposeSelectionWithoutFocusableControls() {
        DesktopAppListAdapter adapter = adapter(new boolean[] {true, false, false}, -1);
        View row = adapter.getView(0, null, parent);
        assertTrue(choice(row) instanceof CheckBox);
        assertFalse(row.isFocusable());
        assertFalse(choice(row).isFocusable());
        assertFalse(choice(row).isClickable());
        assertTrue(row.getMinimumHeight() >= 64);
        assertEquals("\u5df2\u9009\u62e9", row.getStateDescription());
        TextView title = row.findViewById(android.R.id.text1);
        TextView subtitle = row.findViewById(android.R.id.text2);
        assertEquals(Typeface.NORMAL, title.getTypeface().getStyle());
        assertEquals(16f, title.getTextSize(), .01f);
        assertEquals(12f, subtitle.getTextSize(), .01f);
        assertEquals(Color.BLACK, title.getCurrentTextColor());
        assertEquals(Color.GRAY, subtitle.getCurrentTextColor());
        assertTrue(title.getMaxLines() > 1);
        assertTrue(subtitle.getMaxLines() > 1);
        adapter.toggle(0);
        adapter.getView(0, row, parent);
        assertEquals(activity.getString(R.string.desktop_not_selected), row.getStateDescription());
    }

    private DesktopAppListAdapter adapter(boolean[] checked, int selected) {
        return new DesktopAppListAdapter(activity, apps, checked, selected,
                Color.WHITE, Color.BLACK, Color.GRAY, Color.BLUE);
    }

    private CompoundButton choice(View row) {
        ViewGroup group = (ViewGroup) row;
        return (CompoundButton) group.getChildAt(group.getChildCount() - 1);
    }
}
