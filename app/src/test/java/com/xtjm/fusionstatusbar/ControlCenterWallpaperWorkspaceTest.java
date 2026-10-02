package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterWallpaperWorkspaceTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private LinearLayout page;
    private ControlCenterGridEditor editor;
    private ControlCenterWallpaperWorkspace workspace;
    private ColorDrawable background;
    private LinearLayout.LayoutParams params;
    private int mutations;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(new View(activity));
        editor = new ControlCenterGridEditor(activity, new ControlCenterGridEditor.Listener() {
            public void onItemClicked(String id) { mutations++; }
            public void onItemMoved(String id, int x, int y) { mutations++; }
            public void onItemResized(String id, int width, int height) { mutations++; }
        });
        params = new LinearLayout.LayoutParams(-1, -2);
        page.addView(editor, params);
        page.addView(new View(activity));
        activity.setContentView(page);
        background = new ColorDrawable(Color.RED);
        activity.getWindow().setBackgroundDrawable(background);
        workspace = new ControlCenterWallpaperWorkspace(activity, editor);
    }
    @After public void cleanup() {
        workspace.close();
        controller.pause().stop().destroy();
    }
    @Test public void entryReusesEditorWithoutRequestingWallpaperOrMutatingDraft() {
        workspace.open("标准布局", () -> {});
        assertTrue(workspace.isOpen());
        assertEquals(View.GONE, page.getVisibility());
        assertNotSame(page, editor.getParent());
        assertSame(editor, ((android.view.ViewGroup) editor.getParent()).getChildAt(0));
        assertFalse(showsWallpaper());
        assertEquals(0, mutations);
    }
    @Test public void exitRestoresExactParentIndexParamsBackgroundAndVisibility() {
        workspace.open("标准布局", () -> {});
        assertTrue(workspace.close());
        assertSame(page, editor.getParent());
        assertEquals(1, page.indexOfChild(editor));
        assertSame(params, editor.getLayoutParams());
        assertSame(background, activity.getWindow().getDecorView().getBackground());
        assertEquals(View.VISIBLE, page.getVisibility());
        assertFalse(showsWallpaper());
        assertFalse(workspace.isOpen());
        assertEquals(0, mutations);
    }
    @Test public void repeatedEntryAndExitAreIdempotent() {
        workspace.open("标准布局", () -> {});
        Object parent = editor.getParent();
        workspace.open("标准布局", () -> {});
        assertSame(parent, editor.getParent());
        assertTrue(workspace.close());
        assertFalse(workspace.close());
        workspace.open("紧凑布局", () -> {});
        assertTrue(workspace.close());
        assertEquals(3, page.getChildCount());
    }
    @Test public void preexistingWallpaperFlagAndHiddenPageArePreserved() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        page.setVisibility(View.INVISIBLE);
        workspace.open("标准布局", () -> {});
        assertFalse("The built-in backdrop must not wake a wallpaper service", showsWallpaper());
        workspace.close();
        assertTrue(showsWallpaper());
        assertEquals(View.INVISIBLE, page.getVisibility());
    }
    @Test public void workspaceInsetsProtectHeaderAndFooterWithoutAccumulating() {
        workspace.open("标准布局", () -> {});
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        View surface = content.getChildAt(content.getChildCount() - 1);
        int top = surface.getPaddingTop(), bottom = surface.getPaddingBottom();
        android.view.WindowInsets insets = new android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 28, 0, 24))
                .build();
        surface.dispatchApplyWindowInsets(insets);
        assertEquals(top + 28, surface.getPaddingTop());
        assertEquals(bottom + 24, surface.getPaddingBottom());
        surface.dispatchApplyWindowInsets(insets);
        assertEquals(top + 28, surface.getPaddingTop());
        assertEquals(bottom + 24, surface.getPaddingBottom());
    }
    @Test public void workspaceDetachesSettingsInsteadOfLeavingAnUnderlyingPage() {
        workspace.open("标准布局", () -> {});
        assertNull("Settings must not remain in the wallpaper rendering tree", page.getParent());
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        assertEquals(1, content.getChildCount());
        workspace.close();
        assertSame(content, page.getParent());
    }
    @Test public void entryDoesNotSwitchTheWindowToATransparentSurface() {
        activity.getWindow().setFormat(android.graphics.PixelFormat.OPAQUE);
        workspace.open("标准布局", () -> {});
        assertEquals(android.graphics.PixelFormat.OPAQUE, activity.getWindow().getAttributes().format);
        workspace.close();
        assertEquals(android.graphics.PixelFormat.OPAQUE, activity.getWindow().getAttributes().format);
    }
    @Test public void builtInBackgroundIsAnOpaqueSmallBitmap() {
        workspace.open("标准布局", () -> {});
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        android.graphics.drawable.Drawable backdrop = content.getChildAt(0).getBackground();
        assertTrue(backdrop instanceof android.graphics.drawable.BitmapDrawable);
        android.graphics.drawable.BitmapDrawable drawable = (android.graphics.drawable.BitmapDrawable) backdrop;
        assertFalse(drawable.getBitmap().hasAlpha());
        assertTrue(drawable.getBitmap().getAllocationByteCount() <= 2 * 1024 * 1024);
        assertEquals(android.view.Gravity.FILL, drawable.getGravity());
        assertEquals(android.graphics.PixelFormat.OPAQUE,
                activity.getWindow().getDecorView().getBackground().getOpacity());
        android.view.ViewGroup surface = (android.view.ViewGroup) content.getChildAt(0);
        android.view.ViewGroup actions = (android.view.ViewGroup) surface.getChildAt(2);
        for (int i = 0; i < actions.getChildCount(); i++) {
            assertEquals(Color.WHITE, ((android.widget.Button) actions.getChildAt(i)).getCurrentTextColor());
        }
    }
    @Test public void darkSystemBarsAreRestoredWhenReturningToSettings() {
        android.view.Window window = activity.getWindow();
        window.setStatusBarColor(Color.GREEN);
        window.setNavigationBarColor(Color.BLUE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        window.getDecorView().setSystemUiVisibility(flags);
        workspace.open("标准布局", () -> {});
        assertEquals(View.SYSTEM_UI_FLAG_LAYOUT_STABLE, window.getDecorView().getSystemUiVisibility());
        assertEquals(Color.rgb(8, 8, 8), window.getStatusBarColor());
        assertEquals(Color.rgb(8, 8, 8), window.getNavigationBarColor());
        workspace.close();
        assertEquals(flags, window.getDecorView().getSystemUiVisibility());
        assertEquals(Color.GREEN, window.getStatusBarColor());
        assertEquals(Color.BLUE, window.getNavigationBarColor());
    }
    @Test public void explicitTranslucentHostFormatIsNotChangedEither() {
        activity.getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        workspace.open("标准布局", () -> {});
        assertEquals(android.graphics.PixelFormat.TRANSLUCENT, activity.getWindow().getAttributes().format);
        assertFalse(showsWallpaper());
        workspace.close();
        assertEquals(android.graphics.PixelFormat.TRANSLUCENT, activity.getWindow().getAttributes().format);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    public void bundledBackgroundFillsNarrowTallAndLandscapeWorkspaces() throws Exception {
        editor.setGeometry(8, 24);
        editor.setCells(4, java.util.Arrays.asList(
                new ControlCenterGridEditor.Cell("wifi", "wifi", "", "WLAN", null, 0, 0, 2, 1,
                        false, null, null, ControlCenterLayoutPlan.Shape.RECTANGLE, 24, false, false, 0, 100),
                new ControlCenterGridEditor.Cell("bt", "bt", "", "蓝牙", null, 2, 0, 1, 1,
                        false, null, null, ControlCenterLayoutPlan.Shape.CIRCLE, 0, false, false, 0, 100),
                new ControlCenterGridEditor.Cell("light", "flashlight", "", "手电筒", null, 0, 1, 2, 1,
                        false, null, null, ControlCenterLayoutPlan.Shape.CAPSULE, 0, false, false, 0, 100)));
        workspace.open("标准布局 · 壁纸画布", () -> {});
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        android.view.ViewGroup surface = (android.view.ViewGroup) content.getChildAt(0);
        for (int[] size : new int[][] {{320, 640}, {412, 892}, {800, 360}}) {
            int width = size[0], height = size[1];
            surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            surface.layout(0, 0, width, height);
            assertEquals(3, surface.getChildCount());
            assertTrue(surface.getChildAt(0).getBottom() <= surface.getChildAt(1).getTop());
            assertTrue(surface.getChildAt(1).getBottom() <= surface.getChildAt(2).getTop());
            assertTrue(surface.getChildAt(1).getHeight() > 0);
            assertTrue(surface.getChildAt(2).getBottom() <= height - surface.getPaddingBottom());
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height,
                    android.graphics.Bitmap.Config.ARGB_8888);
            surface.draw(new android.graphics.Canvas(bitmap));
            java.util.Set<Integer> tones = new java.util.HashSet<>();
            for (int y = 0; y < height; y += 8) {
                for (int x : new int[] {0, width - 1}) {
                    int pixel = bitmap.getPixel(x, y);
                    assertEquals(255, Color.alpha(pixel));
                    assertTrue(Color.red(pixel) < 40);
                    assertEquals(Color.red(pixel), Color.green(pixel));
                    assertEquals(Color.red(pixel), Color.blue(pixel));
                    tones.add(pixel);
                }
            }
            assertTrue("Backdrop must render its soft texture, not a blank fill", tones.size() > 4);
            java.io.File directory = new java.io.File("build/reports/control-center-wallpaper");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(directory, "builtin-" + width + "x" + height + ".png"))) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
            }
            bitmap.recycle();
        }
    }
    @Test public void entryDiagnosticIsOneShotAndCancelledWhenClosedBeforeDrawing() {
        org.robolectric.shadows.ShadowLog.clear();
        workspace.open("标准布局", () -> {});
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        View surface = content.getChildAt(0);
        surface.getViewTreeObserver().dispatchOnPreDraw();
        surface.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(1, timingCount("pre_draw"));
        workspace.close();
        workspace.open("标准布局", () -> {});
        android.view.ViewTreeObserver observer = content.getChildAt(0).getViewTreeObserver();
        workspace.close();
        if (observer.isAlive()) observer.dispatchOnPreDraw();
        assertEquals(1, timingCount("pre_draw"));
        assertEquals(2, timingCount("setup"));
    }
    private long timingCount(String phase) {
        return org.robolectric.shadows.ShadowLog.getLogsForTag("FusionStatusBar").stream()
                .filter(item -> item.msg.contains("WallpaperWorkspace phase=" + phase)).count();
    }
    private boolean showsWallpaper() {
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0;
    }
}
