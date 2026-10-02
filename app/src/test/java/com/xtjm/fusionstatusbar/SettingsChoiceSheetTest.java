package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsChoiceSheetTest {
    private ActivityController<Activity> activityController;
    private Activity activity;
    private SettingsDialogController dialogs;

    @Before public void setup() {
        RuntimeEnvironment.setFontScale(1.5f);
        activityController = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = activityController.get();
        dialogs = new SettingsDialogController(activity);
    }

    @After public void cleanup() {
        dialogs.close();
        activityController.pause().stop().destroy();
    }

    @Test public void shortAndLongLightChoicesKeepHeaderVisibleAndExportNativeScreenshots() throws Exception {
        verifyResponsiveChoices(0xffffffff, 0xff202124, "light");
    }

    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void shortAndLongDarkChoicesKeepHeaderVisibleAndExportNativeScreenshots() throws Exception {
        verifyResponsiveChoices(0xff252527, 0xfff4f4f7, "dark");
    }

    @Test public void cancelAndBackDoNotSelectOrCloseTheParentPropertiesSheet() {
        Dialog parent = showParent();
        AtomicInteger edits = new AtomicInteger();
        Dialog choices = showChoices(new String[] {"蓝牙", "自动亮度"}, edits);
        TextView cancel = findText(choices.getWindow().getDecorView(), activity.getString(R.string.backup_cancel));
        assertNotNull(cancel);
        cancel.performClick();
        assertFalse(choices.isShowing());
        assertTrue(parent.isShowing());
        assertEquals(0, edits.get());

        choices = showChoices(new String[] {"蓝牙", "自动亮度"}, edits);
        choices.cancel();
        assertFalse(choices.isShowing());
        assertTrue(parent.isShowing());
        assertEquals(0, edits.get());
    }

    @Test public void selectingDismissesOnlyTheChoiceBeforeCallingBackExactlyOnce() {
        Dialog parent = showParent();
        AtomicInteger edits = new AtomicInteger();
        Dialog[] choice = new Dialog[1];
        choice[0] = dialogs.showList("选择磁贴", adapter(new String[] {"蓝牙", "自动亮度"},
                0xffffffff, 0xff202124), position -> {
            assertFalse("The selected list is dismissed before its callback", choice[0].isShowing());
            assertTrue("The owning properties sheet remains open", parent.isShowing());
            assertEquals(1, position);
            edits.incrementAndGet();
        }, 0xffffffff, 0xff202124, 0xff3482ff);
        ListView list = choice[0].findViewById(android.R.id.list);
        list.performItemClick(null, 1, 1);
        list.performItemClick(null, 1, 1);
        assertEquals(1, edits.get());
        assertFalse(choice[0].isShowing());
        assertTrue(parent.isShowing());
    }

    @Test public void closeDismissesOwnedListsAndPropertiesWithoutSelection() {
        Dialog parent = showParent();
        AtomicInteger edits = new AtomicInteger();
        Dialog first = showChoices(new String[] {"蓝牙"}, edits);
        Dialog second = showChoices(new String[] {"自动亮度"}, edits);
        dialogs.close();
        dialogs.close();
        assertFalse(first.isShowing());
        assertFalse(second.isShowing());
        assertFalse(parent.isShowing());
        assertEquals(0, edits.get());
    }

    @Test public void recycledRowsKeepIconSlotOriginalLabelsAndAccessibleTwoLineText() {
        String original = "应用快捷方式带完整名称与来源信息，需要两行显示但无障碍仍能读出全部内容";
        SettingsListAdapter adapter = new SettingsListAdapter(activity, new String[] {original, "蓝牙"},
                0xff252527, 0xfff4f4f7, 0xff3482ff, index -> index == 0 ? new ColorDrawable(0xff3482ff) : null);
        ListView list = new ListView(activity);
        ViewGroup row = (ViewGroup) adapter.getView(0, null, list);
        assertEquals(original, adapter.getItem(0));
        assertEquals(original, row.getContentDescription());
        assertTrue(row.getBackground() instanceof RippleDrawable);
        assertTrue(row.getMinimumHeight() >= dp(52));
        ImageView icon = (ImageView) row.getChildAt(0);
        assertEquals(dp(28), icon.getLayoutParams().width);
        assertEquals(dp(28), icon.getLayoutParams().height);
        assertNotNull(icon.getDrawable());
        TextView label = (TextView) row.getChildAt(1);
        assertEquals(2, label.getMaxLines());
        // Android 14 scales sp nonlinearly at large font sizes.
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                activity.getResources().getDisplayMetrics()), label.getTextSize(), .01f);
        assertEquals(0xfff4f4f7, label.getCurrentTextColor());
        assertSame(row, adapter.getView(1, row, list));
        assertNull(icon.getDrawable());
        assertEquals(dp(28), icon.getLayoutParams().width);
        assertEquals("蓝牙", row.getContentDescription());
        assertEquals("蓝牙", label.getText());
    }

    @Test @Config(qualifiers = "w800dp-h1000dp-mdpi")
    public void tabletWidthRemainsCappedAt420Dp() {
        Dialog choices = showChoices(new String[] {"蓝牙"}, new AtomicInteger());
        assertEquals(dp(420), choices.getWindow().getAttributes().width);
    }

    private void verifyResponsiveChoices(int background, int primary, String theme) throws Exception {
        String[] shortLabels = {"蓝牙", "自动亮度"};
        Dialog shortList = dialogs.showList("添加磁贴", adapter(shortLabels, background, primary),
                ignored -> fail("Measuring a sheet must not select anything"), background, primary, 0xff3482ff);
        View shortDecor = measure(shortList);
        verifyShell(shortList, background, primary);
        int shortHeight = shortDecor.getHeight();
        ListView shortView = shortList.findViewById(android.R.id.list);
        assertEquals(shortLabels.length, shortView.getChildCount());
        assertFalse(shortView.canScrollVertically(1));
        save(shortDecor, "choices-short-" + theme + ".png");
        shortList.dismiss();

        String[] labels = new String[40];
        for (int i = 0; i < labels.length; i++) labels[i] = "应用磁贴 " + (i + 1) + " · 较长名称与来源";
        Dialog longList = dialogs.showList("添加应用与系统磁贴", adapter(labels, background, primary),
                ignored -> fail("Scrolling a sheet must not select anything"), background, primary, 0xff3482ff);
        View longDecor = measure(longList);
        verifyShell(longList, background, primary);
        assertTrue("Short choices hug their content", shortHeight < longDecor.getHeight());
        ListView list = longList.findViewById(android.R.id.list);
        assertTrue("Long choices remain scrollable", list.canScrollVertically(1));
        assertTrue(list.getHeight() >= dp(52));
        TextView cancel = findText(longDecor, activity.getString(R.string.backup_cancel));
        assertNotNull(cancel);
        assertTrue(cancel.isShown());
        assertTrue(cancel.getParent() != list);
        int headerTop = ((View) cancel.getParent()).getTop();
        save(longDecor, "choices-long-" + theme + ".png");
        // Move the real viewport, not keyboard selection (which touch-mode
        // ListView may defer until a later ViewRoot traversal in Robolectric).
        for (int page = 0; page < labels.length && list.canScrollVertically(1); page++) {
            list.scrollListBy(list.getHeight() - dp(52));
        }
        assertEquals(labels.length - 1, list.getLastVisiblePosition());
        assertFalse("All rows can be reached by scrolling", list.canScrollVertically(1));
        assertTrue(cancel.isShown());
        assertEquals(headerTop, ((View) cancel.getParent()).getTop());
        longList.dismiss();
    }

    private void verifyShell(Dialog dialog, int background, int primary) {
        assertFalse(dialog instanceof android.app.AlertDialog);
        var attributes = dialog.getWindow().getAttributes();
        assertEquals(R.style.SettingsSheetWindowAnimation, attributes.windowAnimations);
        assertEquals(Gravity.BOTTOM, attributes.gravity);
        assertEquals(Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(24), dp(420)), attributes.width);
        View decor = dialog.getWindow().getDecorView();
        assertTrue(decor.getHeight() <= Math.round(activity.getResources().getDisplayMetrics().heightPixels * .88f));
        ViewGroup shell = (ViewGroup) ((ViewGroup) dialog.findViewById(android.R.id.content)).getChildAt(0);
        GradientDrawable surface = (GradientDrawable) shell.getBackground();
        assertEquals(background, surface.getColor().getDefaultColor());
        assertEquals(dp(28), surface.getCornerRadius(), .01f);
        assertEquals(2, shell.getChildCount());
        assertTrue(shell.getChildAt(1) instanceof ListView);
        assertFalse(hasScrollView(shell));
        ViewGroup header = (ViewGroup) shell.getChildAt(0);
        assertEquals(primary, ((TextView) header.getChildAt(0)).getCurrentTextColor());
        assertTrue(header.getBottom() <= shell.getHeight());
    }

    private Dialog showParent() {
        dialogs.show("磁贴属性", content -> content.addView(new TextView(activity)),
                0xffffffff, 0xff202124, 0xff3482ff);
        return ShadowDialog.getLatestDialog();
    }

    private Dialog showChoices(String[] labels, AtomicInteger edits) {
        return dialogs.showList("添加磁贴", adapter(labels, 0xffffffff, 0xff202124),
                ignored -> edits.incrementAndGet(), 0xffffffff, 0xff202124, 0xff3482ff);
    }

    private SettingsListAdapter adapter(String[] labels, int background, int primary) {
        return new SettingsListAdapter(activity, labels, background, primary, 0xff3482ff, index -> {
            GradientDrawable icon = new GradientDrawable();
            icon.setShape(GradientDrawable.OVAL);
            icon.setColor(0xff3482ff);
            return icon;
        });
    }

    private View measure(Dialog dialog) {
        View decor = dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        return decor;
    }

    private static TextView findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView match = findText(((ViewGroup) view).getChildAt(i), value);
            if (match != null) return match;
        }
        return null;
    }

    private static boolean hasScrollView(View view) {
        if (view instanceof ScrollView) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            if (hasScrollView(((ViewGroup) view).getChildAt(i))) return true;
        }
        return false;
    }

    private static void save(View view, String name) throws Exception {
        File directory = new File("build/reports/settings-choice-sheets");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
