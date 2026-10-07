package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LauncherGestureCompatibilityTest {
    @Test public void blankWorkspaceDetectionConvertsBothQueriesToScreenCoordinates() {
        WorkspaceTarget workspace = new WorkspaceTarget(RuntimeEnvironment.getApplication());
        assertTrue(LauncherHooks.isBlankWorkspaceAt(workspace, 32, 48));
        assertEquals(62f, workspace.areaX, 0f);
        assertEquals(138f, workspace.areaY, 0f);
        assertEquals(workspace.areaX, workspace.childX, 0f);
        assertEquals(workspace.areaY, workspace.childY, 0f);
        workspace.iconAtPoint = true;
        assertFalse(LauncherHooks.isBlankWorkspaceAt(workspace, 32, 48));
    }

    @Test public void editingWorkspaceNeverCountsAsBlank() {
        WorkspaceTarget workspace = new WorkspaceTarget(RuntimeEnvironment.getApplication());
        workspace.editing = true;
        assertFalse(LauncherHooks.isBlankWorkspaceAt(workspace, 32, 48));
        assertEquals(0f, workspace.areaX, 0f);
    }

    @Test public void customVerticalActionsSuppressOnlyTheirOwnFingerCountAndDirection() {
        DesktopConfig source = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_DOUBLE_DOWN, DesktopConfig.ACTION_SCREENSHOT)
                .withGesture(DesktopConfig.GESTURE_SINGLE_UP, DesktopConfig.ACTION_RECENTS);
        assertTrue(LauncherHooks.hasCustomVerticalAction(source, 11, 2));
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 11, 1));
        assertTrue(LauncherHooks.hasCustomVerticalAction(source, 10, 1));
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 10, 2));
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 11, 3));
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 9, 1));
    }

    @Test public void noActionRetainsNativeSwipeHandling() {
        DesktopConfig source = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_SINGLE_UP, DesktopConfig.ACTION_NONE)
                .withGesture(DesktopConfig.GESTURE_DOUBLE_DOWN, DesktopConfig.ACTION_NONE);
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 10, 1));
        assertFalse(LauncherHooks.hasCustomVerticalAction(source, 11, 2));
    }

    @Test public void normalStateIsFoundOnItsBaseType() {
        LauncherStateTarget launcher = new LauncherStateTarget();
        assertTrue(LauncherHooks.isNormalDesktopLauncher(launcher));
        launcher.manager.state = new StateTarget();
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
    }

    @Test public void editingFoldersAndDraggingBlockDesktopActions() {
        LauncherStateTarget launcher = new LauncherStateTarget();
        launcher.editing = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
        launcher.editing = false;
        launcher.folder = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
        launcher.folder = false;
        launcher.drag.dragging = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
    }

    @Test public void previewSearchAndTransientDialogsBlockDesktopActions() {
        LauncherStateTarget launcher = new LauncherStateTarget();
        launcher.preview = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
        launcher.preview = false;
        launcher.search = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
        launcher.search = false;
        launcher.dialog = true;
        assertFalse(LauncherHooks.isNormalDesktopLauncher(launcher));
    }

    @Test public void unknownLauncherStateFailsClosed() {
        assertFalse(LauncherHooks.isNormalDesktopLauncher(new Object()));
        assertFalse(LauncherHooks.isNormalDesktopLauncher(null));
    }

    public static final class WorkspaceTarget extends View {
        boolean editing;
        boolean iconAtPoint;
        float areaX;
        float areaY;
        float childX;
        float childY;

        WorkspaceTarget(Context context) { super(context); }
        @Override public void getLocationOnScreen(int[] location) {
            location[0] = 30;
            location[1] = 90;
        }
        public boolean isInNormalEditingMode() { return editing; }
        public boolean hitViewArea(float x, float y) {
            areaX = x;
            areaY = y;
            return x >= 30 && y >= 90;
        }
        public boolean pointOnChildViewRect(float x, float y) {
            childX = x;
            childY = y;
            return iconAtPoint;
        }
    }

    public static class StateTarget {
        public static final StateTarget NORMAL = new NormalStateTarget();
    }
    public static final class NormalStateTarget extends StateTarget { }
    public static final class StateManagerTarget {
        StateTarget state = StateTarget.NORMAL;
        public StateTarget getState() { return state; }
    }
    public static final class DragTarget {
        boolean dragging;
        public boolean isDragging() { return dragging; }
    }
    public static final class LauncherStateTarget {
        boolean editing;
        boolean folder;
        boolean preview;
        boolean search;
        boolean dialog;
        final StateManagerTarget manager = new StateManagerTarget();
        final DragTarget drag = new DragTarget();
        public boolean isInEditing() { return editing; }
        public boolean isFolderOpened() { return folder; }
        public boolean isPreviewShowing() { return preview; }
        public boolean isSearchEdgeShowing() { return search; }
        public boolean isUninstallDialogShowing() { return dialog; }
        public StateManagerTarget getStateManager() { return manager; }
        public DragTarget getDragController() { return drag; }
    }
}
