package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
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
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsResponsiveTest {
    private ActivityController<Activity> activity;
    private SettingsDialogController dialogs;
    private SettingsPageController pages;
    private FusionConfig config;

    @Before public void setup() {
        RuntimeEnvironment.setFontScale(1.5f);
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        config = FusionConfig.defaults();
        dialogs = new SettingsDialogController(activity.get());
        pages = new SettingsPageController(activity.get(), new SettingsPageController.Model() {
            public FusionConfig current() { return config; }
            public void update(FusionConfig next) { config = next; }
        }, dialogs);
    }

    @After public void cleanup() {
        dialogs.close();
        activity.pause().stop().destroy();
    }

    @Test public void smallScreenLargeFontSettingsRemainReadable() throws Exception { checkAll("light"); }

    @Test public void expandedLayoutChoiceFitsSmallScreenAtLargeFontScale() throws Exception {
        checkExpandedChoice("settings-inline-choice-expanded.png");
    }

    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void darkExpandedLayoutChoiceFitsSmallScreenAtLargeFontScale() throws Exception {
        checkExpandedChoice("settings-inline-choice-expanded-dark.png");
    }

    private void checkExpandedChoice(String screenshotName) throws Exception {
        config = config.withDoubleRow(true);
        pages.showLayout();
        Dialog sheet = ShadowDialog.getLatestDialog();
        DisplayMetrics metrics = activity.get().getResources().getDisplayMetrics();
        measureDialog(sheet, metrics.heightPixels);
        View fusionChoice = findDescription(sheet.getWindow().getDecorView(), "融合图标");
        assertNotNull(fusionChoice);
        fusionChoice.performClick();
        ShadowLooper.idleMainLooper();

        assertSame("The inline selector stays in the existing settings window", sheet, ShadowDialog.getLatestDialog());
        View selector = fusionChoice;
        View lastOption = findDescriptionContaining(selector, "右侧 · 第二排");
        assertNotNull(lastOption);
        assertEquals(View.VISIBLE, lastOption.getVisibility());

        View decor = measureDialog(sheet, metrics.heightPixels);
        verifyText(decor);
        save(decor, screenshotName);
        assertTrue("Choice rows keep a usable touch height",
                lastOption.getHeight() >= Math.round(48 * metrics.density));
        ScrollView scroll = findScroll(decor);
        assertNotNull(scroll);
        assertTrue(scroll.getWidth() <= decor.getWidth());
        assertTrue("Opening a lower choice scrolls the content", scroll.getScrollY() > 0);
        Rect selectorBounds = new Rect(0, 0, selector.getWidth(), selector.getHeight());
        scroll.offsetDescendantRectToMyCoords(selector, selectorBounds);
        assertTrue("Expanded options start inside the viewport", selectorBounds.top >= scroll.getScrollY());
        assertTrue("Expanded options finish inside the viewport",
                selectorBounds.bottom <= scroll.getScrollY() + scroll.getHeight());
    }

    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void darkSmallScreenLargeFontSettingsRemainReadable() throws Exception { checkAll("dark"); }

    @Test @Config(sdk = 33)
    public void wrappedLargeFontHeaderPreservesMaximumSheetHeight() {
        DisplayMetrics metrics = activity.get().getResources().getDisplayMetrics();
        dialogs.show("通知中心日期与时间", content -> {
            View tallContent = new View(activity.get());
            content.addView(tallContent, new android.widget.LinearLayout.LayoutParams(-1,
                    Math.round(1000 * metrics.density)));
        }, 0xffffffff, 0xff202124, 0xff3482ff);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View decor = measureDialog(dialog, metrics.heightPixels);
        int maxHeight = Math.round(metrics.heightPixels * .88f);
        assertTrue("Wrapped header breaks maximum sheet height: " + decor.getHeight() + " > " + maxHeight,
                decor.getHeight() <= maxHeight);
        verifyText(decor);
    }

    @Test public void keyboardConstraintKeepsClockInputsScrollableAndVisible() {
        config = config.withCustomClock(true)
                .withNotificationClock(config.notificationClock.withEnabled(true));
        Runnable[] opens = {pages::showClock, pages::showNotificationClock};
        for (Runnable open : opens) {
            open.run();
            Dialog dialog = ShadowDialog.getLatestDialog();
            DisplayMetrics metrics = activity.get().getResources().getDisplayMetrics();
            int availableHeight = Math.round(240 * metrics.density);
            View decor = measureDialog(dialog, availableHeight);
            assertTrue("Sheet exceeds keyboard-constrained space", decor.getHeight() <= availableHeight);
            ScrollView scroll = findScroll(decor);
            assertNotNull(scroll);
            assertTrue("Keyboard leaves no usable scroll viewport", scroll.getHeight() >= Math.round(48 * metrics.density));
            EditText input = findInput(decor);
            assertNotNull(input);
            assertTrue(input.requestFocus());
            input.requestRectangleOnScreen(new Rect(0, 0, input.getWidth(), input.getHeight()), true);
            Rect bounds = new Rect();
            input.getDrawingRect(bounds);
            scroll.offsetDescendantRectToMyCoords(input, bounds);
            assertTrue("Input remains above viewport", bounds.top >= scroll.getScrollY());
            assertTrue("Input remains below viewport", bounds.bottom <= scroll.getScrollY() + scroll.getHeight());
            dialog.dismiss();
        }
    }

    private void checkAll(String theme) throws Exception {
        Runnable[] opens = {pages::showFusion, pages::showClock, pages::showNotificationClock,
                pages::showNotificationDate, pages::showNotificationList,
                pages::showTelemetry, pages::showLayout};
        for (int i = 0; i < opens.length; i++) {
            opens[i].run();
            Dialog dialog = ShadowDialog.getLatestDialog();
            DisplayMetrics metrics = activity.get().getResources().getDisplayMetrics();
            View decor = measureDialog(dialog, metrics.heightPixels);
            assertTrue("Sheet exceeds its maximum height", decor.getHeight() <= Math.round(metrics.heightPixels * .88f));
            assertTrue("Dialog contains settings", countLabels(decor) > 2);
            verifyText(decor);
            ScrollView scroll = findScroll(decor);
            assertNotNull(scroll);
            View content = scroll.getChildAt(0);
            assertTrue("Scrollable content has a stable width", content.getWidth() > 0 && content.getWidth() <= decor.getWidth());
            save(decor, "settings-" + theme + "-" + i + ".png");
            if (i == 5) save(content, "telemetry-content-" + theme + ".png");
            dialog.dismiss();
        }
    }

    private static View measureDialog(Dialog dialog, int availableHeight) {
        View decor = dialog.getWindow().getDecorView();
        int width = dialog.getWindow().getAttributes().width;
        assertTrue("Dialog must declare a concrete width", width > 0);
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        return decor;
    }

    private static EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText input = findInput(group.getChildAt(i));
                if (input != null) return input;
            }
        }
        return null;
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findDescriptionContaining(View view, String description) {
        CharSequence current = view.getContentDescription();
        if (current != null && current.toString().contains(description)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescriptionContaining(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void verifyText(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getText().length() > 0 && text.getLayout() != null) {
                int available = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertTrue("Text clipped: " + text.getText(), text.getLayout().getLineWidth(line) <= available + 1);
                    assertEquals("Text ellipsized: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
                }
                assertTrue("Label height clips text: " + text.getText(),
                        text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop()
                                - text.getCompoundPaddingBottom() + 1);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) verifyText(group.getChildAt(i));
        }
    }

    private static int countLabels(View view) {
        int count = view instanceof TextView ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countLabels(group.getChildAt(i));
        }
        return count;
    }

    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView result = findScroll(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void save(View view, String name) throws Exception {
        File directory = new File("build/reports/phase2-ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
