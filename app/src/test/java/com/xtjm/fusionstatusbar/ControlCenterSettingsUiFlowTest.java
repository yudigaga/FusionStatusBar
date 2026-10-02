package com.xtjm.fusionstatusbar;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h800dp-mdpi")
public class ControlCenterSettingsUiFlowTest {
    @Test public void aboutSectionShowsRepositoryAndMiuixCreditLinks() throws Exception {
        launchOverview();
        assertEquals(1, texts(activity.getWindow().getDecorView(), "项目仓库").size());
        assertEquals(1, texts(activity.getWindow().getDecorView(), "FusionStatusBar").size());
        assertEquals(1, texts(activity.getWindow().getDecorView(),
                activity.getString(R.string.about_project_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)).size());
        assertEquals(1, texts(activity.getWindow().getDecorView(), "UI 参考与感谢").size());
        assertEquals(1, texts(activity.getWindow().getDecorView(), "https://github.com/yudigaga/FusionStatusBar").size());
        assertEquals(1, texts(activity.getWindow().getDecorView(), "感谢 Miuix 提供 UI 设计参考").size());
    }

    private void launchOverview() throws Exception {
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults());
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue(activity.settingsLoaded());
        assertEquals(View.VISIBLE, ((LinearLayout[]) field("pages"))[0].getVisibility());
    }


    @Test public void addPopupUsesTheExistingRoundedBottomSheet() throws Exception {
        launch();
        String before = activity.editorSession().draft().layoutPlan;
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertEquals(android.view.Gravity.BOTTOM, dialog.getWindow().getAttributes().gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK);
        assertEquals(R.style.SettingsSheetWindowAnimation, dialog.getWindow().getAttributes().windowAnimations);
        assertFalse("Tile choices use the same shell as the existing settings pages", dialog instanceof AlertDialog);
        dialog.cancel();
        assertEquals(before, activity.editorSession().draft().layoutPlan);
    }

    @Test public void morePopupUsesTheExistingSettingsSheet() throws Exception {
        LinearLayout page = launch();
        texts(page, "更多 ···").get(0).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("More actions must use the settings sheet instead of an OEM popup", dialog);
        assertEquals(android.view.Gravity.BOTTOM, dialog.getWindow().getAttributes().gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK);
        assertEquals(R.style.SettingsSheetWindowAnimation, dialog.getWindow().getAttributes().windowAnimations);
        dialog.cancel();
        assertFalse(activity.editorSession().isDirty());
    }

    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int fixtureEdits;

    @Before public void isolateApplicationRepository() throws Exception {
        // Robolectric replaces Application between methods while ordinary static fields survive.
        Field repository = FusionConfigRepository.class.getDeclaredField("application");
        repository.setAccessible(true);
        repository.set(null, null);
    }

    private LinearLayout launch() throws Exception {
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, Collections.emptyList()),
                new ControlCenterLayoutPlan.Mode(3, Collections.emptyList())).forPublication(false);
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults()
                .withControlCenter(ControlCenterConfig.defaults().withLayoutPlan(plan.encode())));
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while ((!activity.settingsLoaded() || (boolean) field("controlCenterAppTilesLoading")) && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertFalse((boolean) field("controlCenterAppTilesLoading"));
        assertTrue(activity.settingsLoaded());
        LinearLayout page = ((LinearLayout[]) field("pages"))[3];
        // Exercise the visible navigation entry rather than only the private page field.
        findDescription(activity.getWindow().getDecorView(), "控制中心").performClick();
        assertEquals(View.VISIBLE, page.getVisibility());
        return page;
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void coldStartKeepsLegacyLayoutCoveredUntilOverviewIsReady() throws Exception {
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults());
        controller = Robolectric.buildActivity(MainActivity.class).create();
        activity = controller.get();

        View content = activity.findViewById(android.R.id.content);
        assertEquals(View.VISIBLE, content.getVisibility());
        assertFalse(activity.settingsLoaded());
        View startup = (View) field("startupOverlay");
        assertEquals(View.VISIBLE, startup.getVisibility());
        assertNotNull(findDescription(startup, "正在加载设置"));
        assertSame(startup, ((ViewGroup) content).getChildAt(((ViewGroup) content).getChildCount() - 1));

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue(activity.settingsLoaded());
        assertEquals(View.VISIBLE, content.getVisibility());
        assertNull(startup.getParent());
        assertEquals(0, field("selectedPage"));
        assertEquals(View.VISIBLE, ((LinearLayout[]) field("pages"))[0].getVisibility());
        assertEquals(View.GONE, ((LinearLayout[]) field("pages"))[1].getVisibility());
    }

    @Test public void activityRestoredFromBackgroundStartsOnOverview() throws Exception {
        launch();
        findDescription(activity.getWindow().getDecorView(), "状态栏").performClick();
        assertEquals(View.VISIBLE, ((LinearLayout[]) field("pages"))[1].getVisibility());

        android.os.Bundle state = new android.os.Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue(activity.settingsLoaded());
        LinearLayout[] pages = (LinearLayout[]) field("pages");
        assertEquals(View.VISIBLE, pages[0].getVisibility());
        assertEquals(View.GONE, pages[1].getVisibility());
        assertEquals(0, field("selectedPage"));
    }

    @Test public void selectedCanvasSummaryUsesItsOwnColumns() throws Exception {
        launch();
        TextView summary = (TextView) field("controlCenterSummary");
        assertTrue(summary.getText().toString().startsWith("5 列"));
        ((Button) field("controlCenterCompactModeButton")).performClick();
        assertTrue(summary.getText().toString().startsWith("3 列"));
        assertTrue(((TextView) field("controlCenterCanvasSummary")).getText()
                .toString().startsWith("紧凑布局 · 3 列"));
        ((Button) field("controlCenterRegularModeButton")).performClick();
        assertTrue(summary.getText().toString().startsWith("5 列"));
    }

    @Test public void enableSwitchEditsDraftUntilPush() throws Exception {
        launch();
        Switch toggle = (Switch) field("controlCenterEnabledSwitch");
        Button push = (Button) field("controlCenterPushButton");
        assertFalse(toggle.isChecked());
        assertFalse(push.isEnabled());
        toggle.performClick();
        assertTrue(((ControlCenterConfig) field("controlCenterDraft")).enabled);
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
        assertTrue(push.isEnabled());
        assertEquals("推送修改", push.getText().toString());
    }

    @Test public void consolidatedAppearanceDialogHasOnlyEffectiveSettings() throws Exception {
        LinearLayout page = launch();
        assertEquals(1, texts(page, "布局与外观").size());
        findDescription(page, "布局与外观").performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        View dialogContent = dialog.getWindow().getDecorView();
        assertTrue(texts(dialogContent, "磁贴风格").isEmpty());
        for (String label : new String[] {"画布列数", "内容缩放", "默认圆角", "项目间距", "背景模糊", "卡片模糊", "磁贴模糊"}) {
            assertEquals(label, 1, texts(dialogContent, label).size());
        }
        assertEquals(0, texts(page, "控制中心设置").size());
    }

    @Test public void columnAdjustmentEditsOnlySelectedCanvasDraft() throws Exception {
        LinearLayout page = launch();
        ((Button) field("controlCenterCompactModeButton")).performClick();
        findDescription(page, "布局与外观").performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        View columnRow = (View) texts(dialog.getWindow().getDecorView(), "画布列数")
                .get(0).getParent().getParent();
        SeekBar columns = findSeekBar(columnRow);
        assertNotNull(columns);
        // Dispatch a user-origin progress change through the widget's installed listener.
        columns.setProgress(1);
        Shadows.shadowOf(columns).getOnSeekBarChangeListener().onProgressChanged(columns, 1, true);
        ControlCenterLayoutPlan draft = ControlCenterLayoutPlan.decode(
                ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(4, draft.compact.columns);
        assertEquals(5, draft.regular.columns);
        assertEquals(3, ControlCenterLayoutPlan.decode(
                FusionConfigStore.read(activity).controlCenter.layoutPlan).compact.columns);
        assertTrue(((TextView) field("controlCenterSummary")).getText().toString().startsWith("4 列"));
        assertTrue(((Button) field("controlCenterPushButton")).isEnabled());
    }

    @Test public void addAndMoreEntriesExposeDistinctActions() throws Exception {
        LinearLayout page = launch();
        texts(page, activity.getString(R.string.editor_add)).get(0).performClick();
        Dialog add = ShadowDialog.getLatestDialog();
        assertNotNull(add);
        assertTrue(choiceList(add).getAdapter().getCount() > 2);
        assertEquals("组合卡片 · 将两个磁贴合并", choiceList(add).getAdapter().getItem(0));
        assertEquals("空白组合卡", choiceList(add).getAdapter().getItem(1));
        assertFalse(choiceList(add).getAdapter().getItem(2).toString().startsWith("系统快捷开关 · "));
        assertFalse(choiceList(add).getAdapter().getItem(2).toString().startsWith("应用快捷开关 · "));
        add.dismiss();
        texts(page, "更多 ···").get(0).performClick();
        Dialog more = ShadowDialog.getLatestDialog();
        assertNotNull(more);
        android.widget.ListAdapter actions = choiceList(more).getAdapter();
        assertEquals(4, actions.getCount());
        assertEquals("全局磁贴开关", actions.getItem(0));
        assertEquals("清空当前画布", actions.getItem(1));
        assertEquals("恢复原生布局", actions.getItem(2));
        assertEquals(activity.getString(R.string.editor_layout_presets), actions.getItem(3));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void addTileRowsRenderReadableGlyphsInLightMode() throws Exception {
        assertAddTileGlyphContrast(android.graphics.Color.WHITE, android.graphics.Color.WHITE);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    public void addTileRowsRenderReadableGlyphsInDarkMode() throws Exception {
        assertAddTileGlyphContrast(android.graphics.Color.BLACK, 0xff1c1c1e);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void addTileRowsPreferCapturedArtworkOverApplicationFallback() throws Exception {
        launch();
        String spec = installTileChoiceFixture(android.graphics.Color.BLUE);
        ControlCenterCapturedStyle style = new ControlCenterCapturedStyle(
                ControlCenterLayoutPlan.Shape.CIRCLE, 0, 1, "测试磁贴", "", -1,
                tileChoiceGlyph(android.graphics.Color.RED));
        String encoded = android.util.Base64.encodeToString(spec.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
        String capture = new org.json.JSONObject().put("version", 3).put("iconSampling", 2)
                .put("bounds", "v1|360|800|" + encoded + ",0,0,80,80")
                .put("styles", new org.json.JSONObject().put(spec, style.toJson())).toString();
        ((ControlCenterActualEditor) field("controlCenterActualPreview")).setPreview(null, capture);
        Dialog choices = openTileChoices();
        LinearLayout row = (LinearLayout) choiceRow(choices, "测试磁贴 · 测试应用");
        android.graphics.drawable.Drawable icon = ((android.widget.ImageView) row.getChildAt(0)).getDrawable();
        assertTrue(icon instanceof android.graphics.drawable.BitmapDrawable);
        Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) icon).getBitmap();
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2));
    }

    @Test public void pairTileChoicesKeepApplicationAndSystemIconsAtTheSameFixedSize() throws Exception {
        launch();
        installTileChoiceFixture(android.graphics.Color.WHITE);
        Dialog add = openTileChoices();
        choiceList(add).performItemClick(null, 0, 0);
        Dialog first = ShadowDialog.getLatestDialog();
        assertChoiceIconSize(choiceRow(first, "测试磁贴 · 测试应用"));
        assertChoiceIconSize(choiceRow(first, "Wi-Fi"));
        int selected = choiceIndex(first, "测试磁贴 · 测试应用");
        choiceList(first).performItemClick(null, selected, selected);
        assertChoiceIconSize(choiceRow(ShadowDialog.getLatestDialog(), "Wi-Fi"));
    }

    private void assertAddTileGlyphContrast(int sourceColor, int surface) throws Exception {
        launch();
        installTileChoiceFixture(sourceColor);
        Dialog choices = openTileChoices();
        for (String label : new String[] {"Wi-Fi", "测试磁贴 · 测试应用"}) {
            LinearLayout row = (LinearLayout) choiceRow(choices, label);
            assertNotNull(row.getBackground());

            row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, 320, row.getMeasuredHeight());
            android.widget.ImageView icon = (android.widget.ImageView) row.getChildAt(0);
            int expectedSize = Math.round(28 * activity.getResources().getDisplayMetrics().density);
            assertEquals(expectedSize, icon.getWidth());
            assertEquals(expectedSize, icon.getHeight());
            Bitmap rendered = Bitmap.createBitmap(320, row.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(rendered);
            canvas.drawColor(surface);
            row.draw(canvas);
            assertEquals(surface, rendered.getPixel(row.getWidth() - 1, row.getHeight() / 2));
            int visible = 0;
            for (int y = icon.getTop(); y < icon.getBottom(); y++) {
                for (int x = icon.getLeft(); x < icon.getRight(); x++) {
                    int pixel = rendered.getPixel(x, y);
                    if (Math.abs(android.graphics.Color.luminance(pixel)
                            - android.graphics.Color.luminance(surface)) > 0.25f) visible++;
                }
            }
            assertTrue(label + " must show a readable glyph", visible > 20);
            assertEquals(label, ((TextView) row.getChildAt(1)).getText().toString());
            rendered.recycle();
        }
    }

    private String installTileChoiceFixture(int color) throws Exception {
        String spec = "custom(com.example/.tile)";
        Field entries = MainActivity.class.getDeclaredField("controlCenterAppTiles");
        entries.setAccessible(true);
        entries.set(activity, java.util.List.of(new ControlCenterAppTiles.Entry(
                spec, "测试磁贴", "测试应用", tileChoiceGlyph(color))));
        return spec;
    }

    private static Bitmap tileChoiceGlyph(int color) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(color);
        new Canvas(bitmap).drawRect(24, 24, 72, 72, paint);
        return bitmap;
    }

    private Dialog openTileChoices() {
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        return ShadowDialog.getLatestDialog();
    }

    private static int choiceIndex(Dialog dialog, String label) {
        android.widget.ListAdapter adapter = choiceList(dialog).getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) if (label.equals(adapter.getItem(i).toString())) return i;
        throw new AssertionError("Missing tile choice: " + label);
    }

    private static View choiceRow(Dialog dialog, String label) {
        return choiceList(dialog).getAdapter().getView(choiceIndex(dialog, label), null, choiceList(dialog));
    }

    private static android.widget.ListView choiceList(Dialog dialog) {
        android.widget.ListView list = dialog.findViewById(android.R.id.list);
        assertNotNull("A choice sheet exposes one scrolling list", list);
        return list;
    }

    private void assertChoiceIconSize(View row) {
        row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST));
        row.layout(0, 0, 320, row.getMeasuredHeight());
        android.widget.ImageView icon = (android.widget.ImageView) ((ViewGroup) row).getChildAt(0);
        assertNotNull(icon.getDrawable());
        int size = Math.round(28 * activity.getResources().getDisplayMetrics().density);
        assertEquals(size, icon.getWidth());
        assertEquals(size, icon.getHeight());
    }

    @Test public void importingCapturedLayoutCanUndoToCompletePreviousDraft() throws Exception {
        launch();
        ControlCenterLayoutPlan previous = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, Collections.singletonList(
                        ControlCenterLayoutPlan.Item.tile("bt", 2, 1, 1, 1))),
                new ControlCenterLayoutPlan.Mode(3, Collections.singletonList(
                        ControlCenterLayoutPlan.Item.tile("flashlight", 1, 2, 1, 1))))
                .forPublication(false);
        java.lang.reflect.Method apply = MainActivity.class.getDeclaredMethod(
                "applyControlGridPlan", ControlCenterLayoutPlan.class);
        apply.setAccessible(true);
        apply.invoke(activity, previous);
        String beforeImport = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        ControlCenterActualEditor reference = (ControlCenterActualEditor) field("controlCenterActualPreview");
        reference.setPreview(null, "v1|360|800|d2lmaQ==,0,0,100,100");
        java.lang.reflect.Method importLayout = MainActivity.class.getDeclaredMethod("importActualControlCenterDraft");
        importLayout.setAccessible(true);
        importLayout.invoke(activity);
        assertNotEquals(beforeImport, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(2, historyCount());
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(beforeImport, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(1, historyCount());
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void exportNarrowLightPage() throws Exception { exportPage("light"); }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    public void exportNarrowDarkPage() throws Exception { exportPage("dark"); }

    private void exportPage(String name) throws Exception {
        LinearLayout page = launch();
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Arrays.asList(
                        ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1),
                        ControlCenterLayoutPlan.Item.tile("bt", 2, 0, 1, 1)
                                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0),
                        ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.VOLUME,
                                3, 0, 1, 2))),
                new ControlCenterLayoutPlan.Mode(3, Collections.emptyList())).forPublication(false);
        activity.updateControlCenterDraft(((ControlCenterConfig) field("controlCenterDraft"))
                .withEnabled(true).withLayoutPlan(plan.encode()));
        int width = 328;
        page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        page.layout(0, 0, width, page.getMeasuredHeight());
        assertTrue(page.getHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(width, page.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(name.equals("dark") ? 0xff101113 : 0xfff5f6f8);
        page.draw(canvas);
        File directory = new File("build/reports/control-center-ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + "-360dp.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }

    private ControlCenterGridEditor prepareOverlayPlan(boolean paired) throws Exception {
        launch();
        ControlCenterLayoutPlan.Item item = paired
                ? ControlCenterLayoutPlan.Item.pair("wifi", "bt", ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 1, 2, 1)
                : ControlCenterLayoutPlan.Item.tile("wifi", 0, 1, 1, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, Collections.singletonList(item)),
                new ControlCenterLayoutPlan.Mode(3, Collections.singletonList(
                        ControlCenterLayoutPlan.Item.tile("wifi", 1, 0, 1, 1))));
        activity.updateControlCenterDraft(((ControlCenterConfig) field("controlCenterDraft"))
                .withEnabled(true).withLayoutPlan(plan.encode()));
        ControlCenterGridEditor editor = (ControlCenterGridEditor) field("controlCenterGridEditor");
        layoutEditor(editor);
        fixtureEdits = activity.editorSession().undoCount();
        return editor;
    }
    private int historyCount() { return activity.editorSession().undoCount() - fixtureEdits; }
    private void layoutEditor(ControlCenterGridEditor editor) {
        editor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        editor.layout(0, 0, 360, editor.getMeasuredHeight());
    }
    private ControlCenterLayoutPlan draftPlan() throws Exception {
        return ControlCenterLayoutPlan.decode(((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
    }
    @Test public void overlayRemoveIsOneUndoableDraftEditAndDoesNotSave() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        editor.getChildAt(0).performLongClick();
        layoutEditor(editor);
        findDescription(editor, "移除项目").performClick();
        assertEquals(0, draftPlan().regular.items.size());
        assertEquals(1, draftPlan().compact.items.size());
        assertEquals(1, historyCount());
        assertNull(findDescription(editor, "完成画布编辑"));
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
    }
    @Test public void overlayLockHideAndRestoreUseTheExistingDraftOnly() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        editor.getChildAt(0).performLongClick();
        layoutEditor(editor);
        findDescription(editor, "锁定项目").performClick();
        assertTrue(draftPlan().regular.items.get(0).locked);
        assertFalse(findDescription(editor, "移除项目").isEnabled());
        findDescription(editor, "解锁项目").performClick();
        assertFalse(draftPlan().regular.items.get(0).locked);
        findDescription(editor, "隐藏项目").performClick();
        assertTrue(draftPlan().regular.items.get(0).hidden);
        findDescription(editor, "显示项目").performClick();
        assertFalse(draftPlan().regular.items.get(0).hidden);
        assertEquals(4, historyCount());
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
    }
    @Test public void pairOverlayRotationAndSplitRemainValidAndUndoable() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(true);
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        editor.getChildAt(0).performLongClick();
        layoutEditor(editor);
        findDescription(editor, "切换组合排列").performClick();
        ControlCenterLayoutPlan.Item pair = draftPlan().regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Direction.VERTICAL, pair.direction);
        assertEquals(1, pair.width);
        assertEquals(2, pair.height);
        assertTrue(draftPlan().regular.validate().isEmpty());
        findDescription(editor, "拆分组合卡片").performClick();
        assertEquals(2, draftPlan().regular.items.size());
        assertTrue(draftPlan().regular.validate().isEmpty());
        assertEquals(2, historyCount());
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(ControlCenterLayoutPlan.Type.PAIR, draftPlan().regular.items.get(0).type);
    }
    @Test public void canvasSwitchClearsSelectionEvenForTheSameItemId() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        editor.getChildAt(0).performLongClick();
        layoutEditor(editor);
        assertNotNull(findDescription(editor, "移除项目"));
        ((Button) field("controlCenterCompactModeButton")).performClick();
        layoutEditor(editor);
        assertNull(findDescription(editor, "移除项目"));
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(0, historyCount());
    }
    @Test public void invalidPlacementCommitDoesNotDirtyUndoOrPersist() throws Exception {
        prepareOverlayPlan(false);
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        java.lang.reflect.Method commit = MainActivity.class.getDeclaredMethod("commitControlGridGesture",
                String.class, int.class, int.class, int.class, int.class);
        commit.setAccessible(true);
        commit.invoke(activity, "wifi", 5, 0, 1, 1);
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(0, historyCount());
        commit.invoke(activity, "wifi", 1, 1, 1, 1);
        assertEquals(1, draftPlan().regular.items.get(0).x);
        assertEquals(1, historyCount());
        assertTrue(draftPlan().regular.validate().isEmpty());
        assertEquals(1, draftPlan().compact.items.get(0).x);
    }

    @Test public void wallpaperWorkspaceBackRestoresEditorAndDoesNotSave() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        Object parent = editor.getParent();
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        texts(activity.getWindow().getDecorView(), "壁纸画布").get(0).performClick();
        ControlCenterWallpaperWorkspace workspace = (ControlCenterWallpaperWorkspace) field("controlCenterWallpaperWorkspace");
        assertTrue(workspace.isOpen());
        assertNotSame(parent, editor.getParent());
        activity.onBackPressed();
        assertFalse(workspace.isOpen());
        assertFalse(activity.isFinishing());
        assertSame(parent, editor.getParent());
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
        assertEquals(0, historyCount());
    }
    @Test public void wallpaperWorkspaceReusesDraftActionsAndUndo() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        texts(activity.getWindow().getDecorView(), "壁纸画布").get(0).performClick();
        layoutEditor(editor);
        editor.getChildAt(0).performLongClick();
        layoutEditor(editor);
        findDescription(editor, "移除项目").performClick();
        assertTrue(draftPlan().regular.items.isEmpty());
        View workspace = findDescription(activity.getWindow().getDecorView(), "控制中心编辑画布");
        texts(workspace, "撤销").get(0).performClick();
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
        texts(workspace, "返回设置").get(0).performClick();
        assertSame(field("controlCenterGridPanel"), editor.getParent());
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
    }
    @Test public void wallpaperWorkspacePauseRestoresPageAndClearsSelection() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        texts(activity.getWindow().getDecorView(), "壁纸画布").get(0).performClick();
        layoutEditor(editor);
        editor.getChildAt(0).performLongClick();
        controller.pause();
        assertFalse(((ControlCenterWallpaperWorkspace) field("controlCenterWallpaperWorkspace")).isOpen());
        assertSame(field("controlCenterGridPanel"), editor.getParent());
        assertNull(findDescription(editor, "移除项目"));
        controller.resume();
    }
    @Test public void wallpaperWorkspaceStateSaveRestoresOriginalHierarchy() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        texts(activity.getWindow().getDecorView(), "壁纸画布").get(0).performClick();
        activity.onSaveInstanceState(new android.os.Bundle());
        assertFalse(((ControlCenterWallpaperWorkspace) field("controlCenterWallpaperWorkspace")).isOpen());
        assertSame(field("controlCenterGridPanel"), editor.getParent());
    }

    @Test public void wallpaperWorkspaceRemovesActualSettingsRootEvenAfterDraftRefresh() throws Exception {
        prepareOverlayPlan(false);
        ViewGroup content = activity.findViewById(android.R.id.content);
        View settingsRoot = content.getChildAt(0);
        texts(activity.getWindow().getDecorView(), "壁纸画布").get(0).performClick();
        assertNull(settingsRoot.getParent());
        assertEquals(1, content.getChildCount());
        // Refreshing settings while editing must not put their root behind the canvas.
        activity.updateControlCenterDraft((ControlCenterConfig) field("controlCenterDraft"));
        settingsRoot.setVisibility(View.VISIBLE);
        assertNull(settingsRoot.getParent());
        assertTrue(texts(content, "布局与外观").isEmpty());
        activity.onBackPressed();
        assertSame(settingsRoot, content.getChildAt(0));
        assertEquals(1, content.getChildCount());
        assertEquals(View.VISIBLE, settingsRoot.getVisibility());
    }

    @Test public void importUsesCapturedBoundsInsteadOfPackingAllTilesBeforeComponents() throws Exception {
        launch();
        int spacingBefore = ((ControlCenterConfig) field("controlCenterDraft")).spacing;
        String compactBefore = draftPlan().compact.items.toString();
        ControlCenterActualEditor reference = (ControlCenterActualEditor) field("controlCenterActualPreview");
        reference.setPreview(null, "v2|440|900|86|188|290|392"
                + "|d2lmaQ==,24,120,188,86|Y2VsbA==,228,120,188,86"
                + "|Y29udHJvbDptZWRpYQ==,24,222,188,188,2,2"
                + "|Y29udHJvbDpicmlnaHRuZXNz,228,222,86,188,1,2"
                + "|Y29udHJvbDp2b2x1bWU=,330,222,86,188,1,2"
                + "|Y29udHJvbDpkZXZpY2UtY2VudGVy,24,426,392,86,4,1"
                + "|YnQ=,24,528,86,86|YWlycGxhbmU=,126,528,86,86"
                + "|bXV0ZQ==,228,528,86,86|Zmxhc2hsaWdodA==,330,528,86,86");
        java.lang.reflect.Method importer = MainActivity.class.getDeclaredMethod("importActualControlCenterDraft");
        importer.setAccessible(true);
        importer.invoke(activity);
        ControlCenterLayoutPlan.Mode mode = draftPlan().regular;
        ControlCenterLayoutPlan.Item wifi = mode.items.stream().filter(i -> i.firstSpec.equals("wifi")).findFirst().get();
        ControlCenterLayoutPlan.Item media = mode.items.stream().filter(i -> i.firstSpec.equals("control:media")).findFirst().get();
        ControlCenterLayoutPlan.Item bt = mode.items.stream().filter(i -> i.firstSpec.equals("bt")).findFirst().get();
        assertEquals(4, mode.columns);
        assertEquals(2, wifi.width);
        assertEquals(0, wifi.y);
        assertEquals(1, media.y);
        assertEquals(4, bt.y);
        assertEquals(ControlCenterLayoutPlan.Shape.CIRCLE, bt.shape);
        assertTrue(wifi.cornerRadius > 0);
        assertTrue(mode.validate().isEmpty());
        assertEquals(compactBefore, draftPlan().compact.items.toString());
        assertEquals(16, ((ControlCenterConfig) field("controlCenterDraft")).spacing);
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(spacingBefore, ((ControlCenterConfig) field("controlCenterDraft")).spacing);
    }

    @Test public void draggingIntoAnUnlockedSlotReflowsLikePropertyEditing() throws Exception {
        prepareOverlayPlan(false);
        ControlCenterLayoutPlan.Mode mode = new ControlCenterLayoutPlan.Mode(4, java.util.Arrays.asList(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 1, 0, 1, 1)));
        activity.updateControlCenterDraft(((ControlCenterConfig) field("controlCenterDraft"))
                .withLayoutPlan(ControlCenterLayoutPlan.of(mode, mode).encode()));
        fixtureEdits = activity.editorSession().undoCount();
        java.lang.reflect.Method commit = MainActivity.class.getDeclaredMethod("commitControlGridGesture",
                String.class, int.class, int.class, int.class, int.class);
        commit.setAccessible(true);
        commit.invoke(activity, "wifi", 1, 0, -1, -1);
        ControlCenterLayoutPlan.Item wifi = draftPlan().regular.items.stream().filter(i -> i.firstSpec.equals("wifi")).findFirst().get();
        assertEquals(1, wifi.x);
        assertTrue(draftPlan().regular.validate().isEmpty());
        assertEquals(1, historyCount());
    }

    @Test public void cardStylePresetIsOneUndoableDraftEditAndDoesNotPublish() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        String before = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        editor.getChildAt(0).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        SettingsChoiceSelector presets = (SettingsChoiceSelector) findDescription(
                dialog.getWindow().getDecorView(), "卡片样式");
        assertEquals(8, ControlCenterCardStyle.values().length);
        assertEquals(0, historyCount());
        presets.performClick();
        presets.optionView(ControlCenterCardStyle.SOFT.ordinal()).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(28, draftPlan().regular.items.get(0).cornerRadius);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE, draftPlan().regular.items.get(0).shape);
        assertEquals(1, historyCount());
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
        assertEquals(0, draftPlan().compact.items.get(0).cornerRadius);
        SeekBar radius = (SeekBar) findDescription(dialog.getWindow().getDecorView(), "独立圆角");
        assertEquals(28, radius.getProgress());
        dialog.dismiss();
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(before, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
    }

    @Test public void legacyTelemetryPositionsUseInlineChoicesWithoutOpeningAnotherWindow() throws Exception {
        launch();
        SettingsChoiceSelector[] positions = (SettingsChoiceSelector[]) field("telemetryPositions");
        assertEquals(3, positions.length);
        for (int index = 0; index < positions.length; index++) {
            SettingsChoiceSelector position = positions[index];
            assertNotNull(position);
            assertEquals(0, countSpinners(position));
            assertEquals(position, findDescription(position, position.getContentDescription().toString()));
            Switch toggle = ((Switch[]) field("telemetrySwitches"))[index];
            if (!toggle.isChecked()) toggle.performClick();
            int before = ((FusionConfig) field("config")).telemetry.position(index);
            position.performClick();
            position.optionView(1).performClick();
            assertEquals(1, ((FusionConfig) field("config")).telemetry.position(index));
            if (before == 1) assertEquals(before, ((FusionConfig) field("config")).telemetry.position(index));
        }
    }

    @Test public void manualRadiusAndLockKeepStyleControlsInSync() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        editor.getChildAt(0).performClick();
        View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
        SettingsChoiceSelector presets = (SettingsChoiceSelector) findDescription(root, "卡片样式");
        SeekBar radius = (SeekBar) findDescription(root, "独立圆角");
        Shadows.shadowOf(radius).getOnSeekBarChangeListener().onProgressChanged(radius, 17, true);
        assertEquals(ControlCenterCardStyle.CUSTOM.ordinal(), presets.selectedIndex());
        assertEquals(17, draftPlan().regular.items.get(0).cornerRadius);
        presets.performClick();
        presets.optionView(ControlCenterCardStyle.CAPSULE.ordinal()).performClick();
        assertFalse(radius.isEnabled());
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, draftPlan().regular.items.get(0).shape);
        texts(root, "锁定此项目").get(0).performClick();
        assertFalse(presets.isEnabled());
        texts(root, "解锁此项目").get(0).performClick();
        assertTrue(presets.isEnabled());
        presets.performClick();
        presets.optionView(ControlCenterCardStyle.DEFAULT.ordinal()).performClick();
        assertTrue(radius.isEnabled());
        assertEquals(0, radius.getProgress());
    }

    @Test public void blankGroupMembersArrangementAndSplitAreDraftOnlyAndUndoable() throws Exception {
        launch();
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        String compactBefore = draftPlan().compact.items.toString();
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        Dialog add = ShadowDialog.getLatestDialog();
        choiceList(add).performItemClick(null, 1, 1);
        Dialog properties = ShadowDialog.getLatestDialog();
        assertEquals(1, draftPlan().regular.items.size());
        assertEquals(ControlCenterLayoutPlan.Type.GROUP, draftPlan().regular.items.get(0).type);
        String empty = ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan;
        for (int i = 0; i < 3; i++) {
            texts(properties.getWindow().getDecorView(), "添加成员").get(0).performClick();
            Dialog members = ShadowDialog.getLatestDialog();
            choiceList(members).performItemClick(null, 0, 0);
        }
        assertEquals(3, draftPlan().regular.items.get(0).group.members.size());
        SettingsChoiceSelector mode = (SettingsChoiceSelector) findDescription(properties.getWindow().getDecorView(), "内部排列");
        mode.performClick();
        mode.optionView(ControlCenterGroupData.Arrangement.MIXED.ordinal()).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(ControlCenterGroupData.Arrangement.MIXED, draftPlan().regular.items.get(0).group.arrangement);
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
        assertEquals(compactBefore, draftPlan().compact.items.toString());
        texts(properties.getWindow().getDecorView(), "拆分卡片").get(0).performClick();
        assertEquals(3, draftPlan().regular.items.size());
        assertTrue(draftPlan().regular.items.stream().allMatch(item -> item.type == ControlCenterLayoutPlan.Type.TILE));
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(ControlCenterLayoutPlan.Type.GROUP, draftPlan().regular.items.get(0).type);
        for (int i = 0; i < 4; i++) ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(empty, ((ControlCenterConfig) field("controlCenterDraft")).layoutPlan);
    }

    @Test public void groupCannotClaimLockedOrGroupedTilesButCanMoveStandaloneTiles() throws Exception {
        launch();
        var group = ControlCenterLayoutPlan.Item.group("group:test", ControlCenterGroupData.empty(), 0, 0, 2, 2);
        var mode = new ControlCenterLayoutPlan.Mode(4, java.util.Arrays.asList(group,
                ControlCenterLayoutPlan.Item.tile("wifi", 3, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 3, 1, 1, 1).withLocked(true)));
        activity.updateControlCenterDraft(((ControlCenterConfig) field("controlCenterDraft"))
                .withLayoutPlan(ControlCenterLayoutPlan.of(mode, mode).encode()));
        java.lang.reflect.Method change = MainActivity.class.getDeclaredMethod("changeControlGroup", String.class, ControlCenterGroupData.class);
        change.setAccessible(true);
        change.invoke(activity, "group:test", ControlCenterGroupData.empty().add("bt"));
        assertEquals(3, draftPlan().regular.items.size());
        change.invoke(activity, "group:test", ControlCenterGroupData.empty().add("wifi"));
        assertEquals(2, draftPlan().regular.items.size());
        assertEquals(java.util.List.of("wifi"), draftPlan().regular.items.get(0).specs());
        ((Button) field("controlCenterUndoButton")).performClick();
        assertEquals(3, draftPlan().regular.items.size());
    }

    @Test public void groupPublishesOnlyOnExplicitPushAndLockedPropertiesCannotRemoveIt() throws Exception {
        launch();
        var group = ControlCenterLayoutPlan.Item.group("group:test", ControlCenterGroupData.empty().add("wifi"), 0, 0, 2, 2).withLocked(true);
        activity.updateControlCenterDraft(((ControlCenterConfig) field("controlCenterDraft")).withEnabled(true)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(4).withItem(false, group).encode()));
        ControlCenterGridEditor editor = (ControlCenterGridEditor) field("controlCenterGridEditor");
        layoutEditor(editor);
        editor.getChildAt(0).performClick();
        Dialog properties = ShadowDialog.getLatestDialog();
        View root = properties.getWindow().getDecorView();
        assertFalse(texts(root, "添加成员").get(0).isEnabled());
        assertFalse(texts(root, "从网格移除").get(0).isEnabled());
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
        properties.dismiss();
        ((Button) field("controlCenterPushButton")).performClick();
        awaitPublication();
        ControlCenterConfig persisted = FusionConfigStore.read(activity).controlCenter;
        assertTrue(persisted.enabled);
        var plan = ControlCenterLayoutPlan.decode(persisted.layoutPlan);
        assertNotNull(plan);
        assertEquals(ControlCenterLayoutPlan.RuntimeLayout.REGULAR, plan.runtimeLayout);
        assertEquals(group.group, plan.regular.items.get(0).group);
        assertTrue(plan.regular.items.get(0).locked);
    }

    @Test public void optionalComponentsShowCaptureAvailabilityWithoutDeletingDraftItems() throws Exception {
        launch();
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        Dialog choices = ShadowDialog.getLatestDialog();
        List<String> initial = new ArrayList<>();
        for (int i = 0; i < choiceList(choices).getAdapter().getCount(); i++) {
            initial.add(choiceList(choices).getAdapter().getItem(i).toString());
        }
        assertTrue(initial.contains("组件 · 设备控制 · 未验证"));
        choices.dismiss();
        ControlCenterActualEditor reference = (ControlCenterActualEditor) field("controlCenterActualPreview");
        reference.setPreview(null, "v1|360|800|Y29udHJvbDptZWRpYQ==,0,0,180,180");
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        choices = ShadowDialog.getLatestDialog();
        List<String> captured = new ArrayList<>();
        for (int i = 0; i < choiceList(choices).getAdapter().getCount(); i++) {
            captured.add(choiceList(choices).getAdapter().getItem(i).toString());
        }
        assertTrue(captured.contains("组件 · 音乐"));
        String absent = "组件 · 设备控制 · 本次未检测到";
        assertTrue(captured.contains(absent));
        choiceList(choices).performItemClick(null, captured.indexOf(absent), captured.indexOf(absent));
        assertEquals(ControlCenterComponentSpec.DEVICE_CONTROLS, draftPlan().regular.items.get(0).firstSpec);
        assertTrue(ControlCenterLayoutPlan.decode(FusionConfigStore.read(activity).controlCenter.layoutPlan).regular.items.isEmpty());
        ((Button) field("controlCenterUndoButton")).performClick();
        assertTrue(draftPlan().regular.items.isEmpty());
    }

    @Test public void applicationShortcutAppearsWithoutCaptureAndCanBeAddedToDraft() throws Exception {
        android.content.pm.ResolveInfo info = new android.content.pm.ResolveInfo();
        info.serviceInfo = new android.content.pm.ServiceInfo();
        info.serviceInfo.packageName = "com.example.shortcut"; info.serviceInfo.name = "com.example.shortcut.AppTile";
        info.serviceInfo.permission = "android.permission.BIND_QUICK_SETTINGS_TILE";
        info.serviceInfo.exported = true; info.serviceInfo.enabled = true;
        info.serviceInfo.nonLocalizedLabel = "应用测试开关";
        info.serviceInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        info.serviceInfo.applicationInfo.packageName = info.serviceInfo.packageName;
        info.serviceInfo.applicationInfo.enabled = true; info.serviceInfo.applicationInfo.nonLocalizedLabel = "测试应用";
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addResolveInfoForIntent(
                new android.content.Intent(android.service.quicksettings.TileService.ACTION_QS_TILE), info);
        launch();
        String persisted = FusionConfigStore.read(activity).controlCenter.layoutPlan;
        texts(activity.getWindow().getDecorView(), activity.getString(R.string.editor_add)).get(0).performClick();
        Dialog choices = ShadowDialog.getLatestDialog();
        int target = -1;
        for (int i = 0; i < choiceList(choices).getAdapter().getCount(); i++) {
            String label = choiceList(choices).getAdapter().getItem(i).toString();
            if (label.equals("应用测试开关 · 测试应用")) target = i;
        }
        assertTrue(target >= 0);
        choiceList(choices).performItemClick(null, target, target);
        assertEquals("custom(com.example.shortcut/.apptile)", draftPlan().regular.items.get(0).firstSpec);
        assertEquals(persisted, FusionConfigStore.read(activity).controlCenter.layoutPlan);
    }

    @Test public void threeBlurSlidersEditIndependentDraftFieldsUntilPush() throws Exception {
        launch();
        findDescription(activity.getWindow().getDecorView(), "布局与外观").performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        String[] titles = {"背景模糊", "卡片模糊", "磁贴模糊"};
        int[] values = {0, 40, 75};
        for (int i = 0; i < titles.length; i++) {
            View row = (View) texts(root, titles[i]).get(0).getParent().getParent();
            SeekBar slider = findSeekBar(row);
            assertEquals(100, slider.getMax()); assertEquals(100, slider.getProgress());
            slider.setProgress(values[i]);
            Shadows.shadowOf(slider).getOnSeekBarChangeListener().onProgressChanged(slider, values[i], true);
        }
        ControlCenterConfig draft = (ControlCenterConfig) field("controlCenterDraft");
        assertEquals(0, draft.backgroundBlur); assertEquals(40, draft.cardBlur); assertEquals(75, draft.tileBlur);
        assertEquals(100, FusionConfigStore.read(activity).controlCenter.cardBlur);
        dialog.dismiss();
        ((Button) field("controlCenterPushButton")).performClick();
        awaitPublication();
        ControlCenterConfig saved = FusionConfigStore.read(activity).controlCenter;
        assertEquals(0, saved.backgroundBlur); assertEquals(40, saved.cardBlur); assertEquals(75, saved.tileBlur);
    }

    private void awaitPublication() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (activity.savingControlCenter() && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertFalse(activity.savingControlCenter());
    }

    private void awaitSettings() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue(activity.settingsLoaded());
    }

    @Test public void activityRecreationKeepsDraftModeAndRedoWithoutPublishing() throws Exception {
        launch();
        ((Button) field("controlCenterCompactModeButton")).performClick();
        ((Switch) field("controlCenterEnabledSwitch")).performClick();
        activity.updateControlCenterDraft(activity.editorSession().draft().withSpacing(17));
        ((Button) field("controlCenterUndoButton")).performClick();
        android.os.Bundle state = new android.os.Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();
        activity = controller.get();
        awaitSettings();
        assertTrue(activity.editorSession().compact());
        assertTrue(activity.editorSession().draft().enabled);
        assertTrue(((Button) field("controlCenterRedoButton")).isEnabled());
        ((Button) field("controlCenterRedoButton")).performClick();
        assertEquals(17, activity.editorSession().draft().spacing);
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
    }

    @Test public void newActivityRestoresIndependentDraftAfterPreviousInstanceIsDestroyed() throws Exception {
        launch();
        activity.updateControlCenterDraft(activity.editorSession().draft().withEnabled(true).withBlur(0, 45, 70));
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        awaitSettings();
        assertTrue(activity.editorSession().isDirty());
        assertEquals(45, activity.editorSession().draft().cardBlur);
        assertTrue(activity.editorSession().canUndo());
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
    }

    @Test public void selectedItemReturnsAfterActivityRecreation() throws Exception {
        ControlCenterGridEditor editor = prepareOverlayPlan(false);
        editor.getChildAt(0).performLongClick();
        assertEquals("wifi", activity.editorSession().selection());
        android.os.Bundle state = new android.os.Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();
        activity = controller.get();
        awaitSettings();
        editor = (ControlCenterGridEditor) field("controlCenterGridEditor");
        layoutEditor(editor);
        assertEquals("wifi", activity.editorSession().selection());
        assertNotNull(findDescription(editor, "完成画布编辑"));
    }

    @Test public void failedPublicationKeepsUndoAndRetryCommitsTheSameDraft() throws Exception {
        launch();
        activity.updateControlCenterDraft(activity.editorSession().draft().withEnabled(true).withSpacing(17));
        final int[] attempts = {0};
        FusionConfigRepository repository = new FusionConfigRepository(new FusionConfigRepository.Storage() {
            @Override public FusionConfig read() { return FusionConfigStore.read(activity); }
            @Override public FusionConfigStore.WriteResult write(FusionConfig value) {
                return attempts[0]++ == 0
                        ? new FusionConfigStore.WriteResult(false, read(), "disk_failure")
                        : FusionConfigStore.write(activity, value);
            }
        }, Runnable::run, Runnable::run);
        Field storage = MainActivity.class.getDeclaredField("configSession");
        storage.setAccessible(true);
        ((FusionConfigRepository.Session) storage.get(activity)).close();
        storage.set(activity, repository.newSession());
        int history = activity.editorSession().undoCount();
        ((Button) field("controlCenterPushButton")).performClick();
        assertTrue(activity.editorSession().isDirty());
        assertEquals(history, activity.editorSession().undoCount());
        assertEquals(activity.getString(R.string.editor_save_failed),
                ((TextView) field("controlCenterPublishHint")).getText().toString());
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
        ((Button) field("controlCenterPushButton")).performClick();
        assertFalse(activity.editorSession().isDirty());
        assertEquals(17, FusionConfigStore.read(activity).controlCenter.spacing);
        assertEquals(activity.getString(R.string.editor_saved_waiting),
                ((TextView) field("controlCenterPublishHint")).getText().toString());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void exportBlurSettingsAtNarrowWidth() throws Exception {
        launch();
        findDescription(activity.getWindow().getDecorView(), "布局与外观").performClick();
        View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
        View row = (View) texts(root, "背景模糊").get(0).getParent().getParent();
        ViewGroup content = (ViewGroup) row.getParent();
        content.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, 300, content.getMeasuredHeight());
        assertEquals(3, texts(content, "透明").size());
        assertEquals(3, texts(content, "模糊").size());
        for (String title : new String[] {"背景模糊", "卡片模糊", "磁贴模糊"}) {
            ViewGroup sliderRow = (ViewGroup) texts(content, title).get(0).getParent().getParent();
            SeekBar slider = findSeekBar(sliderRow);
            assertTrue(slider.getWidth() > 100);
            assertTrue(sliderRow.getBottom() <= content.getHeight());
        }
        Bitmap bitmap = Bitmap.createBitmap(300, content.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE); content.draw(canvas);
        File directory = new File("build/reports/control-center-materials");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, "settings-300.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }

    private Object field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
                ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findDescription(group.getChildAt(i), description);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static SeekBar findSeekBar(View view) {
        if (view instanceof SeekBar) return (SeekBar) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SeekBar found = findSeekBar(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int countSpinners(View view) {
        int count = view instanceof android.widget.Spinner ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countSpinners(group.getChildAt(i));
        }
        return count;
    }

    private static List<TextView> texts(View view, String text) {
        List<TextView> result = new ArrayList<>();
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            result.add((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                result.addAll(texts(group.getChildAt(i), text));
            }
        }
        return result;
    }
}
