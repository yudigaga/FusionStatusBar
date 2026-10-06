package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterSettingsPageTest {
    @Test public void editSelectionAndHistoryStayOnTheOwnedSession() {
        ControlCenterConfig baseline = baseline();
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(session);
        page.select("wifi");

        ControlCenterConfig edited = baseline.withEnabled(true).withSpacing(15);
        assertTrue(page.edit(edited));
        page.select("bt");
        assertTrue(page.canUndo());
        assertTrue(page.undo());

        assertSame(session, page.session());
        assertSame(baseline, page.draft());
        assertEquals("wifi", page.selection());
        assertTrue(page.canRedo());
        assertTrue(page.redo());
        assertSame(edited, page.draft());
        assertEquals("bt", page.selection());
        assertTrue(page.isDirty());
    }

    @Test public void modeSwitchClearsSelectionWithoutCreatingAnEditTransaction() {
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline()));
        page.select("wifi");
        int history = page.undoCount();

        page.selectMode(true);

        assertTrue(page.compact());
        assertEquals("", page.selection());
        assertEquals(history, page.undoCount());
        assertFalse(page.canUndo());
    }

    @Test public void appearancePanelEditsOwnedSessionAndOnlyTheSelectedCanvas() {
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.emptyList()),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.emptyList())).forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        page.selectMode(true);
        FakeHost host = new FakeHost(page);

        page.showAppearanceDialog(host);
        host.sliders.get("画布列数").accept(4);

        ControlCenterLayoutPlan edited = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        assertEquals(5, edited.regular.columns);
        assertEquals(4, edited.compact.columns);
        assertTrue(page.isDirty());
        assertEquals(1, host.draftChanges);

        assertTrue(page.undo());
        ControlCenterLayoutPlan undone = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        assertEquals(5, undone.regular.columns);
        assertEquals(3, undone.compact.columns);
        assertTrue(page.compact());

        host.toggles.get("接管电源菜单").accept(false);
        assertFalse(page.draft().systemSurfaces.powerMenu);
        assertEquals(2, host.draftChanges);
    }

    @Test public void headerVisibilityTogglesPersistAcrossOtherControlCenterEdits() {
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline()));
        FakeHost host = new FakeHost(page);

        page.showAppearanceDialog(host);
        host.toggles.get("隐藏日期").accept(true);
        host.toggles.get("隐藏运营商").accept(true);
        ControlCenterConfig edited = page.draft().withSpacing(9)
                .withSystemSurfaces(page.draft().systemSurfaces.withVolumeDialog(true))
                .withSurface(ControlCenterSurface.SOFT_GLASS)
                .withGlobalBorder(true, 2).withHideDate(true);
        assertTrue(edited.hideDate);
        assertTrue(edited.hideCarrier);

        FusionConfig restoredConfig = FusionConfig.fromBundle(
                FusionConfig.defaults().withControlCenter(edited).toBundle());
        ControlCenterConfig restored = restoredConfig.controlCenter;

        assertTrue(restored.hideDate);
        assertTrue(restored.hideCarrier);
        assertEquals(9, restored.spacing);
        assertTrue(restored.systemSurfaces.volumeDialog);
        assertEquals(ControlCenterSurface.SOFT_GLASS, restored.surface);
        assertTrue(restored.borderEnabled);
        assertEquals(2, restored.borderWidthDp);
        assertEquals(2, host.draftChanges);
    }

    @Test public void itemAppearanceEditsOnlyTheActiveCanvasAndCreatesOneUndoStep() {
        ControlCenterLayoutPlan.Item regularTile = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item compactTile = ControlCenterLayoutPlan.Item.tile("wifi", 1, 0, 1, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(regularTile)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.singletonList(compactTile)))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        page.selectMode(true);
        FakeHost host = new FakeHost(page);
        ControlCenterLayoutPlan.Item proposed = compactTile.withShape(
                ControlCenterLayoutPlan.Shape.CAPSULE, 0);

        assertTrue(page.editItemAppearance(compactTile.id, proposed, host));

        ControlCenterLayoutPlan edited = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE, edited.regular.items.get(0).shape);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, edited.compact.items.get(0).shape);
        assertEquals(1, host.draftChanges);
        assertTrue(page.undo());
        ControlCenterLayoutPlan undone = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE, undone.regular.items.get(0).shape);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE, undone.compact.items.get(0).shape);
        assertTrue(page.compact());
        assertEquals(1, host.draftChanges);
    }

    @Test public void itemAppearanceRejectsLockedItemsWithoutCreatingAnEdit() {
        ControlCenterLayoutPlan.Item locked = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withLocked(true);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(locked)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.emptyList())).forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        FakeHost host = new FakeHost(page);

        assertFalse(page.editItemAppearance(locked.id,
                locked.withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), host));
        assertFalse(page.isDirty());
        assertFalse(page.canUndo());
        assertEquals(1, host.rejectedEdits);
        assertEquals("项目已锁定，请先解锁", host.rejectionReason);
    }

    @Test public void itemAppearanceResolvesCoordinatesFromLegacyUnplacedPlans() {
        ControlCenterLayoutPlan rawPlan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(
                        ControlCenterLayoutPlan.Item.tile("wifi", -1, -1, 1, 1))),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.singletonList(
                        ControlCenterLayoutPlan.Item.tile("wifi", -1, -1, 1, 1))))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(rawPlan.encode())));
        FakeHost host = new FakeHost(page);
        ControlCenterLayoutPlan.Item visibleItem = rawPlan.resolve(false, null).mode.items.get(0);

        assertEquals(-1, rawPlan.regular.items.get(0).x);
        assertTrue(page.editItemAppearance(visibleItem.id,
                visibleItem.withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), host));

        ControlCenterLayoutPlan saved = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        ControlCenterLayoutPlan.Item edited = saved.regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, edited.shape);
        assertTrue(edited.x >= 0);
        assertTrue(edited.y >= 0);
        assertEquals(1, host.draftChanges);
    }

    @Test public void itemAppearanceMapsAvailablePairSurvivorBackToStoredPair() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(pair)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.emptyList()))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        FakeHost host = new FakeHost(page);
        host.availableSpecs = java.util.Collections.singletonList("bt");
        ControlCenterLayoutPlan.Item displayed = host.resolvedGridMode(false).items.get(0);

        assertEquals(ControlCenterLayoutPlan.Type.TILE, displayed.type);
        assertEquals("bt", displayed.firstSpec);
        assertTrue(page.editItemAppearance(displayed.id,
                displayed.withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), host));

        ControlCenterLayoutPlan.Item stored = ControlCenterLayoutPlan.decode(page.draft().layoutPlan)
                .regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Type.PAIR, stored.type);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, stored.shape);
        assertEquals(1, host.draftChanges);
    }

    @Test public void hiddenPairMemberSurvivorEditsAndRemovesTheStoredPair() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(pair)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.emptyList()))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        FakeHost host = new FakeHost(page);
        // wifi is hidden/unavailable, so the resolver exposes only the bt survivor tile.
        host.availableSpecs = java.util.Collections.singletonList("bt");
        ControlCenterLayoutPlan.Item survivor = host.resolvedGridMode(false).items.get(0);

        assertEquals(ControlCenterLayoutPlan.Type.TILE, survivor.type);
        assertTrue(page.editGridItem(survivor.id,
                current -> current.withZIndex(7), false, host));
        ControlCenterLayoutPlan.Item edited = ControlCenterLayoutPlan.decode(page.draft().layoutPlan)
                .regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Type.PAIR, edited.type);
        assertEquals(7, edited.zIndex);
        assertEquals(pair.id, edited.id);

        assertTrue(page.applyGridAction(survivor.id,
                ControlCenterGridEditor.EditAction.REMOVE, host));
        assertTrue(ControlCenterLayoutPlan.decode(page.draft().layoutPlan)
                .regular.items.isEmpty());
        assertEquals(2, host.draftChanges);
    }

    @Test public void hiddenPairMemberSurvivorCanToggleDirectionAndSplitStoredPair() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(pair)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.emptyList()))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        FakeHost host = new FakeHost(page);
        host.availableSpecs = java.util.Collections.singletonList("bt");
        String survivorId = host.resolvedGridMode(false).items.get(0).id;

        assertTrue(page.applyGridAction(survivorId,
                ControlCenterGridEditor.EditAction.TOGGLE_DIRECTION, host));
        ControlCenterLayoutPlan.Item rotated = ControlCenterLayoutPlan.decode(page.draft().layoutPlan)
                .regular.items.get(0);
        assertEquals(pair.id, rotated.id);
        assertEquals(ControlCenterLayoutPlan.Direction.VERTICAL, rotated.direction);
        assertEquals(1, rotated.width);
        assertEquals(2, rotated.height);

        assertTrue(page.applyGridAction(survivorId,
                ControlCenterGridEditor.EditAction.UNPAIR, host));
        List<ControlCenterLayoutPlan.Item> split = ControlCenterLayoutPlan.decode(page.draft().layoutPlan)
                .regular.items;
        assertEquals(2, split.size());
        assertEquals(ControlCenterLayoutPlan.Type.TILE, split.get(0).type);
        assertEquals(ControlCenterLayoutPlan.Type.TILE, split.get(1).type);
    }

    @Test public void gridGestureUsesOneSessionEditAndPreservesTheOtherCanvas() {
        ControlCenterLayoutPlan.Item regular = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item compact = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, java.util.Collections.singletonList(regular)),
                new ControlCenterLayoutPlan.Mode(3, java.util.Collections.singletonList(compact)))
                .forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        page.selectMode(true);
        FakeHost host = new FakeHost(page);

        assertTrue(page.commitGridGesture(compact.id, 1, 0, 2, 1, host));

        ControlCenterLayoutPlan edited = ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        assertEquals(0, edited.regular.items.get(0).x);
        assertEquals(1, edited.compact.items.get(0).x);
        assertEquals(2, edited.compact.items.get(0).width);
        assertEquals(1, page.undoCount());
        assertEquals(1, host.draftChanges);
    }

    @Test public void gridItemEditRejectsLockedCollisionWithoutChangingHistory() {
        ControlCenterLayoutPlan.Item moving = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item locked = ControlCenterLayoutPlan.Item.tile("bt", 1, 0, 1, 1)
                .withLocked(true);
        ControlCenterLayoutPlan.Mode mode = new ControlCenterLayoutPlan.Mode(4,
                java.util.Arrays.asList(moving, locked));
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(mode, mode).forPublication(false);
        ControlCenterSettingsPage page = new ControlCenterSettingsPage(
                new ControlCenterEditorSession(baseline().withLayoutPlan(plan.encode())));
        FakeHost host = new FakeHost(page);

        assertFalse(page.editGridItem(moving.id, item -> item.withPosition(1, 0), false, host));
        assertFalse(page.isDirty());
        assertEquals(0, page.undoCount());
        assertEquals(0, host.draftChanges);
        assertEquals(1, host.rejectedEdits);
    }

    private static final class FakeHost implements ControlCenterSettingsPage.Host {
        private final ControlCenterSettingsPage page;
        private final Context context = RuntimeEnvironment.getApplication();
        private final Map<String, Consumer<Boolean>> toggles = new HashMap<>();
        private final Map<String, IntConsumer> sliders = new HashMap<>();
        private final Map<String, IntConsumer> choices = new HashMap<>();
        private List<String> availableSpecs;
        private int draftChanges;
        private int rejectedEdits;
        private String rejectionReason;

        FakeHost(ControlCenterSettingsPage page) { this.page = page; }

        @Override public void showPageDialog(String title, ControlCenterSettingsPage.ContentBuilder builder) {
            assertEquals("布局与外观", title);
            builder.build(new LinearLayout(context));
        }

        @Override public ControlCenterLayoutPlan currentGridPlan() {
            return ControlCenterLayoutPlan.decode(page.draft().layoutPlan);
        }

        @Override public ControlCenterLayoutPlan.Mode resolvedGridMode(boolean compact) {
            return currentGridPlan().resolve(compact, availableSpecs).mode;
        }

        @Override public ControlCenterGridEditor createPageGridEditor(
                ControlCenterGridEditor.Listener listener) {
            return new ControlCenterGridEditor(context, listener);
        }

        @Override public View addPageGroupEditor(LinearLayout content, String id) {
            View editor = new View(context);
            content.addView(editor);
            return editor;
        }

        @Override public void refreshPageGroupEditor(View editor) { }

        @Override public ControlCenterCardStylePicker addPageCardStylePicker(LinearLayout content,
                ControlCenterLayoutPlan.Item selected,
                java.util.function.UnaryOperator<ControlCenterLayoutPlan.Item> update) {
            ControlCenterCardStylePicker picker = new ControlCenterCardStylePicker(context, selected,
                    android.graphics.Color.BLACK, android.graphics.Color.WHITE, update);
            content.addView(picker);
            return picker;
        }

        @Override public Button addPageButton(LinearLayout content, String title, boolean enabled,
                Runnable action) {
            Button button = new Button(context);
            button.setText(title);
            button.setEnabled(enabled);
            button.setOnClickListener(view -> action.run());
            content.addView(button);
            return button;
        }

        @Override public void showPairCreation() { }
        @Override public String tileLabel(String spec) { return spec; }
        @Override public boolean suppressPageSelection() { return false; }
        @Override public void persistPageSelection() { }

        @Override public void onPageDraftChanged() { draftChanges++; }
        @Override public void rejectPageEdit(String reason) {
            rejectedEdits++;
            rejectionReason = reason;
        }
        @Override public void addPageSectionLabel(LinearLayout content, String title) { }
        @Override public void addPageHint(LinearLayout content, String text) { }

        @Override public Switch addPageToggle(LinearLayout content, String title, String summary,
                boolean checked, Consumer<Boolean> update) {
            toggles.put(title, update);
            return new Switch(context);
        }

        @Override public View addPageSlider(LinearLayout content, String title, String summary,
                int minimum, int maximum, int value, String suffix, IntConsumer update) {
            sliders.put(title, update);
            return new View(context);
        }

        @Override public View addPageChoice(LinearLayout content, String title, String[] options,
                int selected, IntConsumer update) {
            choices.put(title, update);
            return new View(context);
        }

        @Override public View addPageBlurSlider(LinearLayout content, String title, int value,
                IntConsumer update) {
            sliders.put(title, update);
            return new View(context);
        }

        @Override public void setPageSliderEnabled(View row, boolean enabled) { }
    }

    private static ControlCenterConfig baseline() {
        return ControlCenterConfig.defaults().withLayoutPlan(
                ControlCenterLayoutPlan.blank(4).forPublication(false).encode());
    }
}
