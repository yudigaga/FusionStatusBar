package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import java.util.ArrayList;

/** Built-in backdrop for the same editor, never a second draft owner. */
final class ControlCenterWallpaperWorkspace {
    private static final int BACKGROUND_COLOR = Color.rgb(8, 8, 8);
    private final Activity activity;
    private final ControlCenterGridEditor editor;
    private final OnBackInvokedCallback back = this::close;
    private final ArrayList<View> pageViews = new ArrayList<>();
    private final ArrayList<Integer> pageVisibility = new ArrayList<>();
    private ViewGroup originalParent;
    private ViewGroup.LayoutParams originalParams;
    private int originalIndex;
    private ViewGroup content;
    private LinearLayout workspace;
    private Drawable originalBackground;
    private boolean hadWallpaperFlag;
    private int originalStatusBarColor;
    private int originalNavigationBarColor;
    private int originalSystemUiVisibility;
    private android.view.ViewTreeObserver.OnPreDrawListener firstDrawProbe;
    private android.view.ViewTreeObserver probeObserver;
    private long openedAt;


    ControlCenterWallpaperWorkspace(Activity activity, ControlCenterGridEditor editor) {
        this.activity = activity;
        this.editor = editor;
    }

    boolean isOpen() { return workspace != null; }

    void setBackgroundStrength(int value) {
        if (workspace != null && workspace.getBackground() != null) {
            workspace.getBackground().mutate().setAlpha(Math.round(255f * Math.max(0, Math.min(100, value)) / 100));
        }
    }

    void open(String title, Runnable undo) {
        if (isOpen() || !(editor.getParent() instanceof ViewGroup)) return;
        openedAt = android.os.SystemClock.uptimeMillis();
        editor.finishEditing();
        originalParent = (ViewGroup) editor.getParent();
        originalIndex = originalParent.indexOfChild(editor);
        originalParams = editor.getLayoutParams();
        content = activity.findViewById(android.R.id.content);
        Window window = activity.getWindow();
        originalBackground = window.getDecorView().getBackground();
        originalStatusBarColor = window.getStatusBarColor();
        originalNavigationBarColor = window.getNavigationBarColor();
        originalSystemUiVisibility = window.getDecorView().getSystemUiVisibility();
        hadWallpaperFlag = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            pageViews.add(child);
            pageVisibility.add(child.getVisibility());
            child.setVisibility(View.GONE);
        }
        // Remove the old page from the render tree, rather than retaining a hidden sibling.
        // Its view state, layout params and editor callbacks remain owned by the Activity.
        content.removeAllViews();
        originalParent.removeView(editor);
        workspace = new LinearLayout(activity);
        workspace.setOrientation(LinearLayout.VERTICAL);
        workspace.setPadding(dp(16), dp(12), dp(16), dp(12));
        workspace.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(dp(16) + safe.left, dp(12) + safe.top,
                    dp(16) + safe.right, dp(12) + safe.bottom);
            return insets;
        });
        workspace.setBackgroundResource(R.drawable.control_center_canvas_background);
        workspace.setContentDescription("控制中心编辑画布");
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 0, 0, dp(16));
        workspace.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.addView(editor, new ScrollView.LayoutParams(-1, -2));
        workspace.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(activity);
        addButton(actions, "撤销", undo);
        addButton(actions, "返回设置", this::close);
        workspace.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        content.addView(workspace, new ViewGroup.LayoutParams(-1, -1));
        workspace.requestApplyInsets();
        // The bundled image is already blurred and opaque. No live-wallpaper surface
        // or transparent-window format transition is needed to display it.
        window.setBackgroundDrawable(new ColorDrawable(BACKGROUND_COLOR));
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setStatusBarColor(BACKGROUND_COLOR);
        window.setNavigationBarColor(BACKGROUND_COLOR);
        window.getDecorView().setSystemUiVisibility(originalSystemUiVisibility
                & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        logEntryTiming("setup");
        probeObserver = workspace.getViewTreeObserver();
        firstDrawProbe = () -> {
            logEntryTiming("pre_draw");
            removeFirstDrawProbe();
            return true;
        };
        probeObserver.addOnPreDrawListener(firstDrawProbe);
    }

    boolean close() {
        if (!isOpen()) return false;
        removeFirstDrawProbe();
        editor.finishEditing();
        activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
        ((ViewGroup) editor.getParent()).removeView(editor);
        content.removeView(workspace);
        originalParent.addView(editor, Math.min(originalIndex, originalParent.getChildCount()), originalParams);
        for (int i = 0; i < pageViews.size(); i++) {
            View page = pageViews.get(i);
            content.addView(page, i);
            page.setVisibility(pageVisibility.get(i));
        }
        Window window = activity.getWindow();
        window.setBackgroundDrawable(originalBackground);
        window.setStatusBarColor(originalStatusBarColor);
        window.setNavigationBarColor(originalNavigationBarColor);
        window.getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        if (hadWallpaperFlag) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        pageViews.clear();
        pageVisibility.clear();
        workspace = null;
        originalParent = null;
        originalParams = null;
        originalBackground = null;
        content = null;
        return true;
    }

    // Low-volume support diagnostics: setup vs first pre-draw, NOT wallpaper readiness
    // or compositor presentation latency. Included by the existing log exporter.
    private void logEntryTiming(String phase) {
        WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
        android.util.Log.i("FusionStatusBar", "WallpaperWorkspace phase=" + phase
                + " background=builtin"
                + " elapsedMs=" + (android.os.SystemClock.uptimeMillis() - openedAt)
                + " format=" + attrs.format
                + " showWallpaper=" + ((attrs.flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0)
                + " contentChildren=" + content.getChildCount()
                + " size=" + workspace.getWidth() + "x" + workspace.getHeight());
    }

    private void removeFirstDrawProbe() {
        if (firstDrawProbe != null) {
            if (probeObserver != null && probeObserver.isAlive()) {
                probeObserver.removeOnPreDrawListener(firstDrawProbe);
            }
            if (workspace != null && workspace.getViewTreeObserver().isAlive()) {
                workspace.getViewTreeObserver().removeOnPreDrawListener(firstDrawProbe);
            }
            firstDrawProbe = null;
            probeObserver = null;
        }
    }

    private void addButton(LinearLayout row, String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(32, 34, 37)));
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
