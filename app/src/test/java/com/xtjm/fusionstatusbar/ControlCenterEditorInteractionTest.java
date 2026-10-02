package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlCenterEditorInteractionTest {
    private ActivityController<Activity> activity;
    private ControlCenterGridEditor editor;
    private final ArrayList<ControlCenterGridEditor.Cell> cells = new ArrayList<>();
    private int moves, resizes, clicks, actions;
    private ControlCenterGridEditor.EditAction lastAction;
    private long downTime;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        editor = new ControlCenterGridEditor(activity.get(), new ControlCenterGridEditor.Listener() {
            public void onItemClicked(String id) { clicks++; }
            public void onItemMoved(String id, int x, int y) {
                moves++;
                ControlCenterGridEditor.Cell current = cells.get(0);
                cells.set(0, copy(current, x, y, current.width, current.height, current.locked));
                editor.setCells(4, cells); // Deliberate synchronous listener reentrancy.
            }
            public void onItemResized(String id, int width, int height) {
                resizes++;
                ControlCenterGridEditor.Cell current = cells.get(0);
                cells.set(0, copy(current, current.x, current.y, width, height, current.locked));
                editor.setCells(4, cells);
            }
            public void onItemAction(String id, ControlCenterGridEditor.EditAction action) {
                actions++;
                lastAction = action;
                if (action == ControlCenterGridEditor.EditAction.TOGGLE_LOCK) {
                    ControlCenterGridEditor.Cell current = cells.get(0);
                    cells.set(0, copy(current, current.x, current.y, current.width, current.height, !current.locked));
                    editor.setCells(4, cells);
                }
            }
        });
        activity.get().setContentView(editor, new ViewGroup.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT));
        editor.setGeometry(4, 24);
        cells.add(cell("wifi", 0, 1, 1, 1, false, false, null, false));
        editor.setCells(4, cells);
        settle();
    }
    @After public void tearDown() { activity.pause().stop().destroy(); }
    private ControlCenterGridEditor.Cell cell(String id, int x, int y, int width, int height,
            boolean locked, boolean hidden, String second, boolean horizontal) {
        return new ControlCenterGridEditor.Cell(id, id, "bt", id, second, x, y, width, height,
                horizontal, null, null, ControlCenterLayoutPlan.Shape.RECTANGLE, 24, locked, hidden, 0, 100);
    }
    private ControlCenterGridEditor.Cell copy(ControlCenterGridEditor.Cell c,
            int x, int y, int width, int height, boolean locked) {
        return cell(c.id, x, y, width, height, locked, c.hidden, c.secondLabel, c.horizontalPair);
    }
    private void layout() {
        editor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        editor.layout(0, 0, 400, editor.getMeasuredHeight());
    }
    private void settle() {
        layout();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        layout();
    }
    private void event(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        editor.dispatchTouchEvent(event);
        event.recycle();
    }
    private int pitchX() { return ControlCenterGridGeometry.columnUnitPx(400, 4, 4) + 4; }
    private int pitchY() { return ControlCenterGridGeometry.rowHeightPx(activity.get()) + 4; }
    private void longPress(float x, float y) {
        event(MotionEvent.ACTION_DOWN, x, y);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 160));
        layout();
    }
    private View find(String description) { return find(editor, description); }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
    private void tapControl(String description) {
        View view = find(description);
        assertNotNull(description, view);
        float x = view.getLeft() + view.getWidth() / 2f;
        float y = view.getTop() + view.getHeight() / 2f;
        event(MotionEvent.ACTION_DOWN, x, y);
        event(MotionEvent.ACTION_UP, x, y);
        settle();
    }
    @Test public void shortTapOpensPropertiesWithoutEditingDraft() {
        event(MotionEvent.ACTION_DOWN, 25, pitchY() + 25);
        event(MotionEvent.ACTION_UP, 25, pitchY() + 25);
        assertEquals(1, clicks);
        assertEquals(0, moves + resizes + actions);
        assertNull(find("完成画布编辑"));
    }
    @Test public void longPressRaisesCardAndShowsChromeWithoutDraftChanges() {
        assertNull(find("移除项目"));
        longPress(25, pitchY() + 25);
        assertNotNull(find("移除项目"));
        View renderer = ((ViewGroup) editor.getChildAt(0)).getChildAt(0);
        assertEquals(1.05f, renderer.getScaleX(), 0.001f);
        event(MotionEvent.ACTION_UP, 25, pitchY() + 25);
        assertEquals(0, moves + resizes + actions + clicks);
    }
    @Test public void scrollingBeforeLongPressDoesNotStartDragOrOpenProperties() {
        event(MotionEvent.ACTION_DOWN, 25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25, pitchY() + 80);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        event(MotionEvent.ACTION_UP, 25, pitchY() + 80);
        assertNull(find("移除项目"));
        assertEquals(0, moves + resizes + clicks);
    }
    @Test public void dragPreservesGrabOffsetAndCommitsExactlyOnceOnRelease() {
        float x = 73, y = pitchY() + 32;
        longPress(x, y);
        event(MotionEvent.ACTION_MOVE, x + pitchX(), y);
        assertEquals(pitchX(), editor.getChildAt(0).getTranslationX(), 0.01f);
        assertEquals(0, moves);
        event(MotionEvent.ACTION_UP, x + pitchX(), y);
        settle();
        assertEquals(1, moves);
        assertEquals(1, cells.get(0).x);
        assertEquals(1, cells.get(0).y);
        assertEquals(0f, editor.getChildAt(0).getTranslationX(), 0.01f);
        assertNotNull(find("完成画布编辑"));
    }
    @Test public void cancellingDragRestoresGeometryWithoutCallback() {
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        event(MotionEvent.ACTION_CANCEL, 25 + pitchX(), pitchY() + 25);
        settle();
        assertEquals(0, moves + resizes + clicks);
        assertEquals(0f, editor.getChildAt(0).getTranslationX(), 0.01f);
        assertEquals(0, cells.get(0).x);
    }
    @Test public void secondPointerCancelsInsteadOfJumpingToAnotherFinger() {
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        event(MotionEvent.ACTION_POINTER_DOWN, 50, pitchY() + 25);
        event(MotionEvent.ACTION_UP, 25 + pitchX(), pitchY() + 25);
        settle();
        assertEquals(0, moves + resizes);
        assertEquals(0f, editor.getChildAt(0).getTranslationX(), 0.01f);
    }
    @Test public void unlockedOccupiedDropReachesTheSamePlacementTransactionAsProperties() {
        cells.add(cell("bt", 1, 1, 1, 1, false, false, null, false));
        editor.setCells(4, cells);
        settle();
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        event(MotionEvent.ACTION_UP, 25 + pitchX(), pitchY() + 25);
        settle();
        assertEquals(1, moves);
        assertEquals(1, cells.get(0).x);
    }

    @Test public void occupiedDropIsRejectedWithoutMovingOtherCards() {
        cells.add(cell("bt", 1, 1, 1, 1, true, false, null, false));
        editor.setCells(4, cells);
        settle();
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        event(MotionEvent.ACTION_UP, 25 + pitchX(), pitchY() + 25);
        settle();
        assertEquals(0, moves);
        assertEquals(0, cells.get(0).x);
        assertEquals(1, cells.get(1).x);
    }
    @Test public void selectedGripResizesContinuouslyAndCommitsOneChange() {
        editor.getChildAt(0).performLongClick();
        settle();
        View grip = find("拖动调整尺寸");
        float x = grip.getLeft() + 24, y = grip.getTop() + 24;
        int before = editor.getChildAt(0).getWidth();
        event(MotionEvent.ACTION_DOWN, x, y);
        event(MotionEvent.ACTION_MOVE, x + pitchX(), y + pitchY());
        layout();
        assertTrue(editor.getChildAt(0).getWidth() > before);
        assertEquals(0, resizes);
        event(MotionEvent.ACTION_UP, x + pitchX(), y + pitchY());
        settle();
        assertEquals(1, resizes);
        assertEquals(2, cells.get(0).width);
        assertEquals(2, cells.get(0).height);
    }
    @Test public void cancelledResizeRestoresOriginalSize() {
        editor.getChildAt(0).performLongClick();
        settle();
        View grip = find("拖动调整尺寸");
        float x = grip.getLeft() + 24, y = grip.getTop() + 24;
        int before = editor.getChildAt(0).getWidth();
        event(MotionEvent.ACTION_DOWN, x, y);
        event(MotionEvent.ACTION_MOVE, x + pitchX(), y);
        layout();
        event(MotionEvent.ACTION_CANCEL, x + pitchX(), y);
        settle();
        assertEquals(0, resizes);
        assertEquals(before, editor.getChildAt(0).getWidth());
        assertEquals(1f, editor.getChildAt(0).getScaleX(), 0.001f);
    }
    @Test public void lockedCardCanExposeUnlockButCannotMoveOrResize() {
        cells.set(0, copy(cells.get(0), 0, 1, 1, 1, true));
        editor.setCells(4, cells);
        settle();
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        event(MotionEvent.ACTION_UP, 25 + pitchX(), pitchY() + 25);
        assertFalse(find("移除项目").isEnabled());
        assertEquals(View.GONE, find("拖动调整尺寸").getVisibility());
        assertEquals(0, moves + resizes);
        tapControl("解锁项目");
        assertEquals(1, actions);
        assertEquals(ControlCenterGridEditor.EditAction.TOGGLE_LOCK, lastAction);
        assertFalse(cells.get(0).locked);
        assertEquals(View.VISIBLE, find("拖动调整尺寸").getVisibility());
    }
    @Test public void externalRefreshCancelsStaleGestureEvenWhenIdIsReused() {
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 25 + pitchX(), pitchY() + 25);
        editor.setCells(4, cells);
        event(MotionEvent.ACTION_UP, 25 + pitchX(), pitchY() + 25);
        settle();
        assertEquals(0, moves + resizes);
    }
    @Test public void doneCancelsPendingGestureAndRemovesChrome() {
        editor.getChildAt(0).performLongClick();
        settle();
        tapControl("完成画布编辑");
        assertNull(find("完成画布编辑"));
        assertEquals(1, editor.getChildCount());
        assertEquals(0, moves + resizes + actions);
    }
    @Test public void overlayRemovalActionIsNotMistakenForACardDrag() {
        editor.getChildAt(0).performLongClick();
        settle();
        tapControl("移除项目");
        assertEquals(ControlCenterGridEditor.EditAction.REMOVE, lastAction);
        assertEquals(1, actions);
        assertEquals(0, moves + resizes + clicks);
    }
    @Test public void noOpDropDoesNotEmitAMutation() {
        longPress(25, pitchY() + 25);
        event(MotionEvent.ACTION_MOVE, 45, pitchY() + 25);
        event(MotionEvent.ACTION_UP, 45, pitchY() + 25);
        settle();
        assertEquals(0, moves + resizes);
    }
    @Test public void hiddenCardDoesNotDimItsRecoveryControls() {
        cells.set(0, cell("wifi", 0, 1, 1, 1, false, true, null, false));
        editor.setCells(4, cells);
        editor.getChildAt(0).performLongClick();
        settle();
        assertEquals(0.36f, editor.getChildAt(0).getAlpha(), 0f);
        assertEquals(1f, find("显示项目").getAlpha(), 0f);
        tapControl("显示项目");
        assertEquals(ControlCenterGridEditor.EditAction.TOGGLE_HIDDEN, lastAction);
    }
    @Test public void finishingBeforeLongPressTimeoutPreventsLateSelection() {
        event(MotionEvent.ACTION_DOWN, 25, pitchY() + 25);
        editor.finishEditing();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        event(MotionEvent.ACTION_UP, 25, pitchY() + 25);
        assertNull(find("移除项目"));
        assertEquals(0, moves + resizes + clicks);
    }
}
